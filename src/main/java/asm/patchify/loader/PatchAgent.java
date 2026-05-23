package asm.patchify.loader;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Java agent entry point — loaded by the system class loader when the JVM is started with
 * {@code -javaagent:<this jar>}.
 *
 * <p>In a ForgeGradle dev environment the mod jar and the agent jar are the same file but get
 * loaded by different class loaders (system vs the Forge module layer). That means the static
 * fields on this class are NOT shared between agent-side and mod-side copies of
 * {@code PatchAgent}. To bridge the gap we stash {@link Instrumentation} in
 * {@link System#getProperties()} under {@link #INSTRUMENTATION_KEY} so the mod can retrieve it
 * regardless of which class loader it lives in.</p>
 */
public final class PatchAgent {
    public static final String INSTRUMENTATION_KEY = "asm.patchify.instrumentation";
    private static final Logger LOGGER = LogManager.getLogger("PatchAgent");

    private static volatile boolean transformerInstalled = false;
    private static volatile boolean selfAttachAttempted = false;

    private PatchAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        install(inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        install(inst);
    }

    public static synchronized void install(Instrumentation inst) {
        Object existing = System.getProperties().get(INSTRUMENTATION_KEY);
        if (existing == inst) {
            return;
        }
        System.getProperties().put(INSTRUMENTATION_KEY, inst);
        LOGGER.info("PatchAgent attached, retransform supported = {}", inst.isRetransformClassesSupported());
    }

    /**
     * Looks up the {@link Instrumentation} stashed by {@link #premain}. Works across class loaders.
     */
    public static Instrumentation getInstrumentation() {
        Object instObj = System.getProperties().get(INSTRUMENTATION_KEY);
        return instObj instanceof Instrumentation ? (Instrumentation) instObj : null;
    }

    /**
     * Best-effort fallback for normal Forge mod loading.
     *
     * <p>When OpenZen is only placed in {@code mods/}, Forge loads {@link shit.zen.ZenClient}
     * but the JVM never invokes this jar's {@code Premain-Class}. The preferred launch paths are
     * still {@code -javaagent:<jar>}, {@code runClient0}, or the native injector, but on runtimes
     * that include the JDK Attach API this method can attach the same jar to the current JVM and
     * make the plain {@code mods/} path work without manual JVM arguments.</p>
     */
    public static synchronized boolean trySelfAttach() {
        if (getInstrumentation() != null) {
            return true;
        }
        if (selfAttachAttempted) {
            return false;
        }
        selfAttachAttempted = true;

        Path jarPath = findOwnJar();
        if (jarPath == null) {
            LOGGER.warn("PatchAgent self-attach skipped: could not locate OpenZen jar");
            return false;
        }
        if (!Files.isRegularFile(jarPath)) {
            LOGGER.warn("PatchAgent self-attach skipped: {} is not a jar file", jarPath);
            return false;
        }

        // Some HotSpot builds allow enabling self-attach before VirtualMachine.attach is called.
        // If the runtime rejects this, the catch block below keeps the mod from crashing.
        System.setProperty("jdk.attach.allowAttachSelf", "true");

        Object vm = null;
        try {
            String pid = Long.toString(ProcessHandle.current().pid());
            Class<?> virtualMachineClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Method attach = virtualMachineClass.getMethod("attach", String.class);
            Method loadAgent = virtualMachineClass.getMethod("loadAgent", String.class);
            Method detach = virtualMachineClass.getMethod("detach");

            LOGGER.info("PatchAgent self-attaching {} to current JVM pid {}", jarPath, pid);
            vm = attach.invoke(null, pid);
            loadAgent.invoke(vm, jarPath.toAbsolutePath().toString());
            Instrumentation inst = getInstrumentation();
            if (inst != null) {
                LOGGER.info("PatchAgent self-attach succeeded");
                return true;
            }
            LOGGER.warn("PatchAgent self-attach completed but Instrumentation is still unavailable");
            return false;
        } catch (Throwable t) {
            Throwable cause = unwrap(t);
            LOGGER.warn("PatchAgent self-attach failed: {}. If plain mods/ loading does not work, launch with -javaagent:{} or use OpenZenLoader.exe.",
                    cause.toString(), jarPath.toAbsolutePath());
            return false;
        } finally {
            if (vm != null) {
                try {
                    Method detach = vm.getClass().getMethod("detach");
                    detach.invoke(vm);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Path findOwnJar() {
        try {
            if (PatchAgent.class.getProtectionDomain() == null
                    || PatchAgent.class.getProtectionDomain().getCodeSource() == null
                    || PatchAgent.class.getProtectionDomain().getCodeSource().getLocation() == null) {
                return null;
            }
            URI uri = PatchAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(uri).toAbsolutePath().normalize();
            return path.toString().endsWith(".jar") ? path : null;
        } catch (Throwable t) {
            LOGGER.warn("PatchAgent failed to resolve own jar path: {}", t.toString());
            return null;
        }
    }

    private static Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null
                && (current instanceof java.lang.reflect.InvocationTargetException
                || current instanceof java.lang.ExceptionInInitializerError)) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Install a transformer for the currently registered patches and retransform any patch target
     * that is already loaded. Called from mod code once {@link PatchRegistry} is populated.
     */
    public static synchronized void installPatchesAndRetransform() {
        if (transformerInstalled) {
            LOGGER.info("Patches already installed; skipping duplicate retransform request");
            return;
        }
        Instrumentation inst = getInstrumentation();
        if (inst == null) {
            LOGGER.warn("PatchAgent not attached; cannot install patches");
            return;
        }
        PatchClassFileTransformer transformer = new PatchClassFileTransformer();
        inst.addTransformer(transformer, true);
        transformerInstalled = true;
        List<Class<?>> retransform = new ArrayList<>();
        for (Class<?> patch : PatchRegistry.getPatches()) {
            asm.patchify.annotation.Patch ann = patch.getAnnotation(asm.patchify.annotation.Patch.class);
            if (ann == null) continue;
            Class<?> target;
            try {
                target = ann.value();
            } catch (Throwable t) {
                LOGGER.warn("Patch target unresolved for {}: {}", patch.getName(), t.toString());
                continue;
            }
            if (inst.isModifiableClass(target)) {
                retransform.add(target);
            } else {
                LOGGER.warn("Cannot retransform unmodifiable target {}", target.getName());
            }
        }
        if (retransform.isEmpty()) {
            return;
        }
        // Retransform one class at a time so we can pinpoint which patch produces invalid
        // bytecode if the JVM throws VerifyError / LinkageError.
        int success = 0;
        for (Class<?> target : retransform) {
            try {
                inst.retransformClasses(target);
                success++;
            } catch (Throwable t) {
                LOGGER.error("Retransform failed for {}: {}", target.getName(), t.toString());
            }
        }
        LOGGER.info("Retransformed {} / {} patch target(s)", success, retransform.size());
    }
}
