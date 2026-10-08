package dev.skypilot.mod.platform;

/** Optional Iris API, looked up once; querying it does not introduce a required dependency. */
final class ShaderCompatibility {
    private static boolean checked, active, failed;
    private static Object api;
    private static java.lang.reflect.Method inUse;
    private static long nextCheck;
    static boolean externalShaderActive() {
        if (failed) return true;
        long now = System.nanoTime();
        if (now < nextCheck) return active;
        nextCheck = now + 1_000_000_000L;
        try {
            if (!checked) {
                checked = true;
                if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) return false;
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
            }
            active = inUse != null && Boolean.TRUE.equals(inUse.invoke(api));
        } catch (ReflectiveOperationException | LinkageError e) {
            // With an incompatible API, leave the external renderer in control.
            active = true; failed = true;
        }
        return active;
    }
}
