package net.guzhenren.shengxian.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

/** Small, resource-pack-aware loader for our numeric Bedrock cube/box-UV assets. */
public final class CalamityArtModel {
    private static final float RAD = (float) (Math.PI / 180.0);
    private final ModelPart root;
    private final Map<String, ModelPart> parts;
    private final Map<String, Clip> clips;
    private final ResourceLocation texture;
    private final int cubes;
    private final int textureWidth, textureHeight;

    private CalamityArtModel(ModelPart root, Map<String, ModelPart> parts, Map<String, Clip> clips,
                             ResourceLocation texture, int cubes, int width, int height) {
        this.root = root;
        this.parts = parts;
        this.clips = clips;
        this.texture = texture;
        this.cubes = cubes;
        this.textureWidth = width;
        this.textureHeight = height;
    }

    public ResourceLocation texture() { return texture; }
    public String diagnostics() {
        return parts.size() + " bones / " + cubes + " cubes / " + textureWidth + "x" + textureHeight
                + " texture=" + texture + " / animations=" + clips.keySet();
    }
    public boolean hasAnimation(String name) { return clips.containsKey(name); }

    public static CalamityArtModel load(ResourceManager resources, String id) throws IOException {
        JsonObject geo = json(resources, "geo/" + id + ".geo.json");
        JsonObject geometry = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        int width = description.get("texture_width").getAsInt();
        int height = description.get("texture_height").getAsInt();
        if (width <= 0 || height <= 0) throw new IOException("Invalid texture dimensions");
        JsonArray bones = geometry.getAsJsonArray("bones");
        if (bones.size() > 512) throw new IOException("Too many bones");
        Map<String, JsonObject> definitions = new LinkedHashMap<>();
        Map<String, float[]> pivots = new HashMap<>();
        Map<String, ModelPart> parts = new LinkedHashMap<>();
        Map<String, Map<String, ModelPart>> children = new HashMap<>();
        int cubeCount = 0;
        for (JsonElement element : bones) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            if (definitions.put(name, bone) != null) throw new IOException("Duplicate bone " + name);
            pivots.put(name, vector(bone.get("pivot"), 0));
            children.put(name, new LinkedHashMap<>());
        }
        for (var entry : definitions.entrySet()) {
            String name = entry.getKey();
            JsonObject bone = entry.getValue();
            float[] pivot = pivots.get(name);
            List<ModelPart.Cube> boxes = new ArrayList<>();
            if (bone.has("cubes")) for (JsonElement element : bone.getAsJsonArray("cubes")) {
                if (++cubeCount > 4096) throw new IOException("Too many cubes");
                JsonObject cube = element.getAsJsonObject();
                // Cube-local rotations/per-face UV require a different mesh; fail this asset explicitly.
                if (cube.has("rotation") || !cube.get("uv").isJsonArray())
                    throw new IOException("Only unrotated box-UV cubes are supported");
                float[] o = vector(cube.get("origin"), 0), s = vector(cube.get("size"), 0);
                JsonArray uv = cube.getAsJsonArray("uv");
                float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0;
                boolean mirror = cube.has("mirror") ? cube.get("mirror").getAsBoolean()
                        : bone.has("mirror") && bone.get("mirror").getAsBoolean();
                if (s[0] < 0 || s[1] < 0 || s[2] < 0) throw new IOException("Negative cube size");
                boxes.add(new ModelPart.Cube(uv.get(0).getAsInt(), uv.get(1).getAsInt(),
                        o[0] - pivot[0], -(o[1] + s[1] - pivot[1]), o[2] - pivot[2],
                        s[0], s[1], s[2], inflate, inflate, inflate, mirror, width, height,
                        EnumSet.allOf(Direction.class)));
            }
            parts.put(name, new ModelPart(boxes, children.get(name)));
        }
        Map<String, ModelPart> roots = new LinkedHashMap<>();
        for (var entry : definitions.entrySet()) {
            String name = entry.getKey();
            JsonObject bone = entry.getValue();
            String parent = bone.has("parent") ? bone.get("parent").getAsString() : null;
            // Validate ancestry before making the render tree (including self-parent/cycles).
            Set<String> ancestry = new HashSet<>();
            String ancestor = name;
            while (ancestor != null) {
                if (!ancestry.add(ancestor)) throw new IOException("Bone cycle: " + name);
                JsonObject def = definitions.get(ancestor);
                if (def == null) throw new IOException("Unknown parent " + ancestor);
                ancestor = def.has("parent") ? def.get("parent").getAsString() : null;
            }
            float[] p = pivots.get(name), pp = parent == null ? new float[3] : pivots.get(parent);
            float[] r = vector(bone.get("rotation"), 0);
            ModelPart part = parts.get(name);
            // Basis change S=diag(1,-1,1): positions Y invert, axial rotations X/Z invert.
            // Pivots are absolute in Bedrock, relative to the parent in ModelPart.
            PartPose rest = PartPose.offsetAndRotation(p[0]-pp[0], -(p[1]-pp[1]), p[2]-pp[2],
                    -r[0]*RAD, r[1]*RAD, -r[2]*RAD);
            part.setInitialPose(rest);
            part.loadPose(rest);
            if (parent == null) roots.put(name, part); else children.get(parent).put(name, part);
        }
        Map<String, Clip> clips = new LinkedHashMap<>();
        JsonObject animations = json(resources, "animations/" + id + ".animation.json").getAsJsonObject("animations");
        for (var entry : animations.entrySet()) {
            String key = entry.getKey();
            String shortName = key.substring(key.lastIndexOf('.') + 1);
            JsonObject animation = entry.getValue().getAsJsonObject();
            double length = animation.get("animation_length").getAsDouble();
            if (!Double.isFinite(length) || length <= 0) throw new IOException("Invalid animation length");
            boolean loop = animation.has("loop") && animation.get("loop").getAsBoolean();
            Map<String, Channels> channels = new LinkedHashMap<>();
            for (var b : animation.getAsJsonObject("bones").entrySet()) {
                if (!parts.containsKey(b.getKey())) throw new IOException("Animation references unknown bone " + b.getKey());
                JsonObject v = b.getValue().getAsJsonObject();
                channels.put(b.getKey(), new Channels(track(v.get("rotation")), track(v.get("position")), track(v.get("scale"))));
            }
            clips.put(shortName, new Clip(length, loop, channels));
        }
        ResourceLocation texture = location("textures/entity/" + id + ".png");
        // Resolve once while loading, so a missing texture isolates this asset rather than spamming frames.
        resources.getResourceOrThrow(texture);
        return new CalamityArtModel(new ModelPart(List.of(), roots), parts, clips, texture, cubeCount, width, height);
    }

    public void render(PoseStack pose, VertexConsumer vertices, int light, int overlay, String animation, double seconds) {
        for (ModelPart part : parts.values()) {
            part.resetPose();
            part.xScale = part.yScale = part.zScale = 1;
        }
        Clip clip = clips.getOrDefault(animation, clips.get("idle"));
        if (clip != null) {
            double t = clip.loop ? Math.max(0, seconds) % clip.length : Math.min(Math.max(0, seconds), clip.length);
            for (var entry : clip.channels.entrySet()) {
                ModelPart p = parts.get(entry.getKey());
                Channels c = entry.getValue();
                if (c.rotation != null) {
                    p.xRot -= c.rotation.sample(t, 0)*RAD;
                    p.yRot += c.rotation.sample(t, 1)*RAD;
                    p.zRot -= c.rotation.sample(t, 2)*RAD;
                }
                if (c.position != null) {
                    p.x += c.position.sample(t, 0);
                    p.y -= c.position.sample(t, 1);
                    p.z += c.position.sample(t, 2);
                }
                if (c.scale != null) {
                    p.xScale *= c.scale.sample(t, 0);
                    p.yScale *= c.scale.sample(t, 1);
                    p.zScale *= c.scale.sample(t, 2);
                }
            }
        }
        root.render(pose, vertices, light, overlay);
    }

    private static JsonObject json(ResourceManager resources, String path) throws IOException {
        try (Reader reader = resources.getResourceOrThrow(location(path)).openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static ResourceLocation location(String path) { return ResourceLocation.fromNamespaceAndPath("shengxian", path); }
    private static float[] vector(JsonElement value, float fallback) throws IOException {
        if (value == null) return new float[]{fallback, fallback, fallback};
        JsonArray a = value.getAsJsonArray();
        if (a.size() != 3) throw new IOException("Expected numeric XYZ vector");
        float[] v = {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
        for (float n : v) if (!Float.isFinite(n)) throw new IOException("Nonfinite vector");
        return v;
    }
    private static Track track(JsonElement element) throws IOException {
        if (element == null) return null;
        if (element.isJsonArray()) return new Track(new double[]{0}, new float[][]{vector(element, 0)});
        TreeMap<Double, float[]> keys = new TreeMap<>();
        for (var entry : element.getAsJsonObject().entrySet()) {
            double t = Double.parseDouble(entry.getKey());
            if (!Double.isFinite(t) || t < 0) throw new IOException("Invalid keyframe time");
            keys.put(t, vector(entry.getValue(), 0));
        }
        if (keys.isEmpty()) throw new IOException("Empty animation track");
        return new Track(keys.keySet().stream().mapToDouble(Double::doubleValue).toArray(), keys.values().toArray(float[][]::new));
    }
    private record Clip(double length, boolean loop, Map<String, Channels> channels) {}
    private record Channels(Track rotation, Track position, Track scale) {}
    private record Track(double[] times, float[][] values) {
        float sample(double time, int axis) {
            int found = Arrays.binarySearch(times, time);
            if (found >= 0) return values[found][axis];
            int right = -found - 1;
            if (right == 0) return values[0][axis];
            if (right == times.length) return values[right - 1][axis];
            double a = (time - times[right-1]) / (times[right] - times[right-1]);
            return (float) (values[right-1][axis] + a * (values[right][axis] - values[right-1][axis]));
        }
    }
}
