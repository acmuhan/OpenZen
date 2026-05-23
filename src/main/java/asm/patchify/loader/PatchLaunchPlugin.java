package asm.patchify.loader;

import asm.patchify.annotation.Patch;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 * ModLauncher plugin path for normal Forge {@code mods/} loading.
 *
 * <p>This is the preferred non-javaagent fallback: Forge/ModLauncher discovers launch plugins from
 * {@code META-INF/services/cpw.mods.modlauncher.serviceapi.ILaunchPluginService} before Minecraft
 * classes are defined, so we can apply the same PatchTransformer without requiring
 * {@link java.lang.instrument.Instrumentation}. The Java-agent path remains supported for dev runs
 * and external injectors.</p>
 */
public final class PatchLaunchPlugin implements ILaunchPluginService {
    private static final Logger LOGGER = LogManager.getLogger("PatchLaunchPlugin");
    private static final EnumSet<Phase> BEFORE_ONLY = EnumSet.of(Phase.BEFORE);
    private static final EnumSet<Phase> NONE = EnumSet.noneOf(Phase.class);

    private final Map<String, List<Class<?>>> patchesByTarget = new HashMap<>();

    public PatchLaunchPlugin() {
        System.setProperty("openzen.patch.launchPluginActive", "true");
        PatchRegistry.registerBuiltins();
        rebuildIndex();
        LOGGER.info("OpenZen PatchLaunchPlugin loaded with {} target(s)", patchesByTarget.size());
    }

    @Override
    public String name() {
        return "openzen_patchify";
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        if (isEmpty || classType == null) {
            return NONE;
        }
        return patchesByTarget.containsKey(classType.getInternalName()) ? BEFORE_ONLY : NONE;
    }

    @Override
    public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
        if (phase != Phase.BEFORE || classType == null || classNode == null) {
            return ComputeFlags.NO_REWRITE;
        }

        List<Class<?>> patches = patchesByTarget.get(classType.getInternalName());
        if (patches == null || patches.isEmpty()) {
            return ComputeFlags.NO_REWRITE;
        }

        for (Class<?> patch : patches) {
            try {
                LOGGER.debug("Applying launch patch {} -> {}", patch.getName(), classType.getInternalName());
                PatchTransformer.apply(patch, classNode);
            } catch (Throwable t) {
                LOGGER.error("Failed to apply launch patch {} -> {}", patch.getName(), classType.getInternalName(), t);
            }
        }
        return ComputeFlags.COMPUTE_FRAMES;
    }

    private void rebuildIndex() {
        patchesByTarget.clear();
        for (Class<?> patchClass : PatchRegistry.getPatches()) {
            Patch patch = patchClass.getAnnotation(Patch.class);
            if (patch == null) continue;
            String internalName = patch.value().getName().replace('.', '/');
            patchesByTarget.computeIfAbsent(internalName, k -> new ArrayList<>()).add(patchClass);
        }
    }
}
