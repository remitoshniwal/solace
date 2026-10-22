package com.solacemod.solace.client;

import com.solacemod.solace.Solace;
import com.solacemod.solace.config.ClientConfig;

import java.lang.reflect.Method;

/**
 * Asks Iris (the shader pack mod) two questions, if it's installed: is a shader pack on, and is it drawing shadows right now?
 * Solace never needs Iris: without it (or if Iris changes), both answers are simply "no" and nothing changes.
 * Uses Iris's small public API by name, so the mod has no dependency on it.
 */
public final class Shaders {
    private static boolean looked;
    private static Object api;
    private static Method packInUse;
    private static Method shadowPass;

    private Shaders() {
    }

    private static void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = c.getMethod("getInstance").invoke(null);
            packInUse = c.getMethod("isShaderPackInUse");
            shadowPass = c.getMethod("isRenderingShadowPass");
            Solace.LOGGER.info("Iris found: Solace's glow switches to its shader-friendly version while a shader pack is on");
        } catch (ReflectiveOperationException | LinkageError e) {
            api = null; // no Iris: nothing to do
        }
    }

    private static boolean ask(Method m) {
        if (api == null || m == null) return false;
        try {
            return (Boolean) m.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            api = null; // Iris changed: stop asking
            return false;
        }
    }

    /** A shader pack is on and the shader-friendly glow is allowed (client setting). */
    public static boolean packOn() {
        if (!ClientConfig.SHADER_FRIENDLY_GLOW.get()) return false;
        look();
        return ask(packInUse);
    }

    /** The shader pack is drawing its shadow map right now (glowing things shouldn't cast shadows). */
    public static boolean shadowPass() {
        look();
        return ask(shadowPass);
    }
}
