package asm.patchify.loader;

/**
 * Small out-of-process helper used when OpenZen is loaded as a normal Forge mod.
 *
 * <p>HotSpot often refuses self-attach from inside the same JVM unless
 * {@code -Djdk.attach.allowAttachSelf=true} was present at process startup. Spawning a second JVM
 * avoids that specific guard: the helper attaches to Minecraft's JVM and loads this jar as a Java
 * agent through {@code agentmain}.</p>
 */
public final class AttachHelper {
    private AttachHelper() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: AttachHelper <pid> <agent-jar>");
        }

        String pid = args[0];
        String agentJar = args[1];
        Object vm = null;
        try {
            Class<?> virtualMachineClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            vm = virtualMachineClass.getMethod("attach", String.class).invoke(null, pid);
            virtualMachineClass.getMethod("loadAgent", String.class).invoke(vm, agentJar);
        } finally {
            if (vm != null) {
                try {
                    vm.getClass().getMethod("detach").invoke(vm);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
