// Run from project root in PowerShell, after compiling CalamityArtModel:
// $cp=(((Get-Content build/offline-classpath.txt)-join ';') -split ';' | Where-Object { Test-Path $_ }) -join ';'
// $cp+=';build/art-render-check' # or replace with the unified build's classes directory
// & 'C:/Users/ADMIN/.jdks/temurin-21/bin/jshell.exe' --class-path $cp tools/VerifyCalamityArtLoader.jsh
// Require exit code 0 AND the ART_ALL_OK marker. JShell compilation errors may otherwise return 0.
// Use the default remote JShell engine; --execution local cannot resolve this game's resource classes.
// No GPU, window, native rendering context, gameplay entity, or server is started.
// Scope: real geometry hierarchy / UV+normal finiteness / keyframes; NOT visual texture orientation.

import net.guzhenren.shengxian.client.CalamityArtModel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

class ArtVertexSink implements InvocationHandler {
    final List<float[]> positions = new ArrayList<>();
    long finiteFloatCount;
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (args != null) for (Object value : args) {
            if (value instanceof Float f) {
                if (!Float.isFinite(f)) throw new AssertionError("Nonfinite vertex float in " + method.getName());
                finiteFloatCount++;
            }
            if (value instanceof Double d && !Double.isFinite(d)) throw new AssertionError("Nonfinite vertex double");
        }
        if (method.getName().equals("addVertex")) {
            // Exact MC 1.21.1 ModelPart.Cube.compile emits addVertex(FFFIFFIIFFF):
            // transformed XYZ, color, UV, overlay, light, normal XYZ. These are final mesh coordinates.
            if (args == null || args.length != 11 || !(args[0] instanceof Float))
                throw new AssertionError("Unexpected addVertex overload; update this check against javap");
            positions.add(new float[]{(Float) args[0], (Float) args[1], (Float) args[2]});
        }
        return method.getReturnType() == VertexConsumer.class ? proxy : null;
    }
    VertexConsumer consumer() {
        return (VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class[]{VertexConsumer.class}, this);
    }
}

class ArtSmoke {
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static ArtVertexSink sample(CalamityArtModel model, String animation, double seconds) {
        ArtVertexSink sink = new ArtVertexSink();
        PoseStack pose = new PoseStack();
        pose.scale(1, -1, 1); // Same ModelPart Y-down -> world Y-up conversion as the actual renderer.
        model.render(pose, sink.consumer(), 0xF000F0, 0, animation, seconds);
        require(!sink.positions.isEmpty(), "No vertices: " + animation + " at " + seconds);
        require(sink.finiteFloatCount == sink.positions.size() * 8L, "Missing XYZ/UV/normal validation");
        return sink;
    }
    static boolean changed(ArtVertexSink a, ArtVertexSink b) {
        require(a.positions.size() == b.positions.size(), "Vertex topology changed unexpectedly");
        for (int i=0; i<a.positions.size(); i++) for (int axis=0; axis<3; axis++)
            if (Math.abs(a.positions.get(i)[axis] - b.positions.get(i)[axis]) > 0.000001f) return true;
        return false;
    }
    static void verify(ResourceManager resources, String id) throws Exception {
        CalamityArtModel model = CalamityArtModel.load(resources, id);
        require(model.hasAnimation("idle"), id + " has no idle animation");
        long vertices = 0;
        for (String animation : List.of("idle", "walk", "charge", "attack", "hurt", "death"))
            for (double t : new double[]{0, 0.375, 30}) vertices += sample(model, animation, t).positions.size();
        ArtVertexSink start = sample(model, "idle", 0);
        int changedSamples = 0;
        for (double t : new double[]{0.375, 0.9, 1.4})
            if (changed(start, sample(model, "idle", t))) changedSamples++;
        require(changedSamples >= 2, id + " idle does not change mesh at multiple sampled times");
        // Sampling other states/time must not accumulate animation offsets into the shared rest pose.
        require(!changed(start, sample(model, "idle", 0)), id + " rest/reset is nondeterministic");
        System.out.println("ART_OK " + id + " / " + model.diagnostics() + " / vertices=" + vertices
                + " / finiteXYZ_UV_normals=true / changedIdleSamples=" + changedSamples + "/3 / reset=true");
    }
}

ResourceManager artResources = (ResourceManager) java.lang.reflect.Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class[]{ResourceManager.class}, (proxy, method, args) -> {
    if (!method.getName().equals("getResource") && !method.getName().equals("getResourceOrThrow"))
        throw new UnsupportedOperationException("Smoke ResourceManager: " + method.getName());
    ResourceLocation location = (ResourceLocation) args[0];
    Path path = Path.of("src/main/resources/assets", location.getNamespace(), location.getPath());
    if (!Files.isRegularFile(path)) throw new java.io.FileNotFoundException(path.toString());
    Resource resource = new Resource(null, () -> Files.newInputStream(path));
    return method.getName().equals("getResource") ? Optional.of(resource) : resource;
});

int artFailures = 0;
for (String id : List.of("jade_willow", "horned_tiger", "eight_chimes", "ancient_beast", "blood_core")) {
    try { ArtSmoke.verify(artResources, id); }
    catch (Throwable failure) { artFailures++; System.err.println("ART_FAIL " + id); failure.printStackTrace(); }
}
if (artFailures == 0) System.out.println("ART_ALL_OK 5/5 assets; numeric mesh, finite values, idle motion and pose reset verified");
/exit artFailures
