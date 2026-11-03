package net.guzhenren.shengxian.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/** Veil 4.5.2 post-processing bridge that keeps compile-time linkage optional. */
final class VeilApertureTransitionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("shengxian");
    private static final ResourceLocation PIPELINE = ResourceLocation.fromNamespaceAndPath(
            "shengxian", "aperture_transit");
    private static final int DURATION_TICKS = 16;

    private static Object manager;
    private static Object callback;
    private static Method addMethod;
    private static Method removeMethod;
    private static Method isActiveMethod;
    private static boolean callbackRegistered;
    private static boolean active;
    private static boolean opening;
    private static boolean warned;
    private static long startedAt;
    private static long currentTick;

    private VeilApertureTransitionRenderer() {}

    static boolean start(long clientTick, boolean entering) {
        try {
            Object currentManager = resolveManager();
            if (currentManager == null) throw new IllegalStateException("Veil post-processing manager is unavailable");
            cacheManagerMethods(currentManager.getClass());
            registerCallback();
            if (!Boolean.TRUE.equals(isActiveMethod.invoke(currentManager, PIPELINE))) {
                addMethod.invoke(currentManager, PIPELINE);
            }
            manager = currentManager;
            startedAt = clientTick;
            currentTick = clientTick;
            opening = entering;
            active = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce(exception);
            active = false;
            return false;
        }
    }

    static void tick(long clientTick) {
        currentTick = clientTick;
        if (active && clientTick - startedAt >= DURATION_TICKS) removePipeline();
    }

    static boolean isRunning() { return active; }

    static void clear() {
        if (active) removePipeline();
        active = false;
        startedAt = 0L;
        currentTick = 0L;
    }

    private static Object resolveManager() throws ReflectiveOperationException {
        Class<?> renderSystem = Class.forName("foundry.veil.api.client.render.VeilRenderSystem");
        Object renderer = invoke(findPublicMethod(renderSystem, "renderer"), null);
        if (renderer == null) return null;
        return invoke(findPublicMethod(renderer.getClass(), "getPostProcessingManager"), renderer);
    }

    private static void cacheManagerMethods(Class<?> managerClass) throws NoSuchMethodException {
        if (addMethod != null && addMethod.getDeclaringClass().isAssignableFrom(managerClass)) return;
        addMethod = findPublicMethod(managerClass, "add", ResourceLocation.class);
        removeMethod = findPublicMethod(managerClass, "remove", ResourceLocation.class);
        isActiveMethod = findPublicMethod(managerClass, "isActive", ResourceLocation.class);
    }

    private static void registerCallback() throws ReflectiveOperationException {
        if (callbackRegistered) return;
        Class<?> platformClass = Class.forName("foundry.veil.platform.VeilEventPlatform");
        Object platform = platformClass.getField("INSTANCE").get(null);
        Class<?> preEvent = Class.forName("foundry.veil.api.event.VeilPostProcessingEvent$Pre");
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "shengxian-aperture-post-listener";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
                    default -> null;
                };
            }
            if (active && arguments != null && arguments.length >= 2
                    && PIPELINE.toString().equals(String.valueOf(arguments[0]))) {
                try {
                    applyUniforms(arguments[1]);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    warnOnce(exception);
                }
            }
            return null;
        };
        callback = Proxy.newProxyInstance(preEvent.getClassLoader(), new Class<?>[]{preEvent}, handler);
        Method register = findPublicMethod(platformClass, "preVeilPostProcessing", preEvent);
        invoke(register, platform, callback);
        callbackRegistered = true;
    }

    private static void applyUniforms(Object pipeline) throws ReflectiveOperationException {
        float progress = Math.max(0.0f, Math.min(1.0f, (currentTick - startedAt) / (float) DURATION_TICKS));
        setFloat(pipeline, "Time", currentTick / 20.0f);
        setFloat(pipeline, "Progress", progress);
        setFloat(pipeline, "Reveal", progress);
        setVector2(pipeline, "Resolution", Math.max(1, Minecraft.getInstance().getWindow().getWidth()),
                Math.max(1, Minecraft.getInstance().getWindow().getHeight()));
        if (opening) setVector3(pipeline, "Accent", 0.58f, 0.82f, 0.74f);
        else setVector3(pipeline, "Accent", 0.50f, 0.74f, 0.88f);
        setFloat(pipeline, "Grade", 0.65f);
        setFloat(pipeline, "Opacity", 0.92f);
        setFloat(pipeline, "Style", 1.0f);
    }

    private static void setFloat(Object pipeline, String name, float value) throws ReflectiveOperationException {
        Object uniform = uniform(pipeline, name);
        if (uniform == null || !isValid(uniform)) return;
        invoke(findPublicMethod(uniform.getClass(), "setFloat", float.class), uniform, value);
    }

    private static void setVector2(Object pipeline, String name, float x, float y) throws ReflectiveOperationException {
        Object uniform = uniform(pipeline, name);
        if (uniform == null || !isValid(uniform)) return;
        invoke(findPublicMethod(uniform.getClass(), "setVector", float.class, float.class), uniform, x, y);
    }

    private static void setVector3(Object pipeline, String name, float x, float y, float z)
            throws ReflectiveOperationException {
        Object uniform = uniform(pipeline, name);
        if (uniform == null || !isValid(uniform)) return;
        invoke(findPublicMethod(uniform.getClass(), "setVector", float.class, float.class, float.class), uniform, x, y, z);
    }

    private static Object uniform(Object pipeline, String name) throws ReflectiveOperationException {
        return invoke(findPublicMethod(pipeline.getClass(), "getUniform", CharSequence.class), pipeline, name);
    }

    private static boolean isValid(Object uniform) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(invoke(findPublicMethod(uniform.getClass(), "isValid"), uniform));
    }

    private static void removePipeline() {
        try {
            Object currentManager = manager != null ? manager : resolveManager();
            if (currentManager != null) {
                cacheManagerMethods(currentManager.getClass());
                removeMethod.invoke(currentManager, PIPELINE);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce(exception);
        } finally {
            active = false;
        }
    }

    private static Method findPublicMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method candidate = null;
        try {
            candidate = type.getMethod(name, parameterTypes);
            if (Modifier.isPublic(candidate.getDeclaringClass().getModifiers())) return candidate;
        } catch (NoSuchMethodException ignored) {
            // Search public contracts and parents below.
        }
        for (Class<?> contract : type.getInterfaces()) {
            try {
                return findPublicMethod(contract, name, parameterTypes);
            } catch (NoSuchMethodException ignoredInterface) {
                // Continue through the remaining public contracts.
            }
        }
        Class<?> parent = type.getSuperclass();
        if (parent != null) {
            try {
                return findPublicMethod(parent, name, parameterTypes);
            } catch (NoSuchMethodException ignoredParent) {
                // Fall back to a public method declared by this implementation class.
            }
        }
        if (candidate != null) return candidate;
        throw new NoSuchMethodException(type.getName() + '.' + name);
    }

    private static Object invoke(Method method, Object receiver, Object... arguments)
            throws ReflectiveOperationException {
        return method.invoke(receiver, arguments);
    }

    private static void warnOnce(Throwable exception) {
        if (warned) return;
        warned = true;
        LOGGER.warn("Veil aperture transition could not be initialized or updated; using the client overlay fallback",
                exception);
    }
}