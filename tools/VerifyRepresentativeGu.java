import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Audits the exact patched Minecraft explosion branch and physical item resources without bootstrapping the game. */
public final class VerifyRepresentativeGu {
    private static final String BLOCK_STATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String ARGS = "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;Ljava/util/function/BiConsumer;";
    private static int checks;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static ClassNode read(InputStream in) throws Exception {
        try (in) { ClassNode node = new ClassNode(); new ClassReader(in).accept(node, 0); return node; }
    }
    private static ClassNode read(JarFile jar, String type) throws Exception {
        var entry = jar.getJarEntry(type + ".class");
        InputStream input = entry != null ? jar.getInputStream(entry) : VerifyRepresentativeGu.class.getClassLoader().getResourceAsStream(type + ".class");
        if (input == null) throw new AssertionError("Missing class audit input " + type);
        return read(input);
    }
    private static Object value(AnnotationNode node, String key) {
        for (int i = 0; node.values != null && i < node.values.size(); i += 2) if (key.equals(node.values.get(i))) return node.values.get(i + 1);
        return null;
    }
    private static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
        return annotations == null ? null : annotations.stream().filter(a -> a.desc.endsWith(desc + ";")).findFirst().orElse(null);
    }
    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(() -> new AssertionError("Missing " + node.name + "." + name));
    }
    private static int call(MethodNode method, String owner, String name, String descriptor) {
        int count = 0;
        for (var insn : method.instructions) if (insn instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(descriptor)) count++;
        return count;
    }
    private static void injection(ClassNode node, String handler, String annotation, String method, String at, String target) {
        MethodNode callback = method(node, handler);
        AnnotationNode inject = annotation(callback.visibleAnnotations, annotation);
        if (inject == null) inject = annotation(callback.invisibleAnnotations, annotation);
        check(inject != null, "Missing injector " + handler);
        check(Integer.valueOf(1).equals(value(inject, "require")), "Injector must fail if its exact target is absent");
        check(((List<?>) value(inject, "method")).equals(List.of(method)), "Wrong injection method");
        Object points = value(inject, "at");
        AnnotationNode point = points instanceof List<?> list ? (AnnotationNode) list.getFirst() : (AnnotationNode) points;
        check(at.equals(value(point, "value")), "Wrong injection point");
        if (target != null) check(target.equals(value(point, "target")), "Wrong redirect target");
    }
    public static void main(String[] args) throws Exception {
        Path compiled = Path.of(args[1]), resources = Path.of(args[2]);
        try (JarFile jar = new JarFile(args[0])) {
            ClassNode behavior = read(jar, "net/minecraft/world/level/block/state/BlockBehaviour");
            MethodNode explode = method(behavior, "onExplosionHit");
            check(explode.desc.equals("(" + ARGS + ")V"), "Explosion signature drifted");
            String dropDesc = "(Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;";
            check(call(explode, BLOCK_STATE, "getDrops", dropDesc) == 1, "Drop invocation must be unique");
            check(call(explode, "java/util/List", "forEach", "(Ljava/util/function/Consumer;)V") == 1, "Explosion drops no longer use one consumer");
            int spawn = -1, drops = -1, destroy = -1, index = 0;
            for (var insn : explode.instructions) {
                if (insn instanceof MethodInsnNode c) {
                    if (c.name.equals("spawnAfterBreak")) spawn = index;
                    if (c.name.equals("getDrops")) drops = index;
                    if (c.name.equals("onBlockExploded")) destroy = index;
                }
                index++;
            }
            check(spawn >= 0 && spawn < drops && drops < destroy, "Explosion drop/destruction order changed");
            // Find the actual constructor used for each whitelisted vanilla block, then audit inherited break behavior.
            Set<String> terrain = Set.of("STONE", "DIRT", "GRASS_BLOCK", "COARSE_DIRT", "PODZOL", "DEEPSLATE", "TUFF", "MUD", "CLAY");
            String constructor = null; int audited = 0;
            for (var insn : method(read(jar, "net/minecraft/world/level/block/Blocks"), "<clinit>").instructions) {
                if (insn instanceof MethodInsnNode c && c.name.equals("<init>") && c.owner.startsWith("net/minecraft/world/level/block/")) constructor = c.owner;
                if (insn instanceof FieldInsnNode f && f.getOpcode() == 179 && terrain.contains(f.name)) {
                    check(constructor != null, "Missing terrain constructor " + f.name);
                    for (String type = constructor; type != null && !type.equals(behavior.name); ) {
                        ClassNode block = read(jar, type);
                        check(block.methods.stream().noneMatch(m -> m.name.equals("spawnAfterBreak") || m.name.equals("onExplosionHit")), "Unsafe custom explosion/XP branch in " + type);
                        type = block.superName;
                    }
                    audited++;
                }
            }
            check(audited == 9, "Whitelist audit incomplete");
            check(method(behavior, "spawnAfterBreak").instructions.size() <= 5, "Base after-break behavior is no longer empty");
            String prefix = "net/guzhenren/shengxian/mixin/common/";
            ClassNode explosionMixin = read(Files.newInputStream(compiled.resolve(prefix + "LandscapeExplosionMixin.class")));
            injection(explosionMixin, "shengxian$recordNoDrop", "Redirect", "onExplosionHit", "INVOKE", "L" + BLOCK_STATE + ";getDrops" + dropDesc);
            injection(explosionMixin, "shengxian$confirmDestroyed", "Inject", "onExplosionHit", "RETURN", null);
            check(method(explosionMixin, "shengxian$recordNoDrop").desc.equals("(L" + BLOCK_STATE + ";Lnet/minecraft/world/level/storage/loot/LootParams$Builder;" + ARGS + ")Ljava/util/List;"), "Redirect capture signature changed");
            ClassNode changes = read(Files.newInputStream(compiled.resolve(prefix + "LandscapeBlockChangeMixin.class")));
            injection(changes, "shengxian$invalidateReplaced", "Inject", "setBlockState", "RETURN", null);
            check(method(read(jar, "net/minecraft/world/level/chunk/LevelChunk"), "setBlockState").desc.equals("(Lnet/minecraft/core/BlockPos;L" + BLOCK_STATE + ";Z)L" + BLOCK_STATE + ";"), "Chunk mutation signature changed");
        }
        JsonObject mixins = JsonParser.parseString(Files.readString(resources.resolve("shengxian.common.mixins.json"))).getAsJsonObject();
        List<String> names = new ArrayList<>(); mixins.getAsJsonArray("mixins").forEach(n -> names.add(n.getAsString()));
        check(mixins.get("required").getAsBoolean() && names.containsAll(List.of("LandscapeExplosionMixin", "LandscapeBlockChangeMixin")), "Missing required landscape mixins");
        Path assets = resources.resolve("assets/shengxian");
        for (String id : List.of("ding_xian_you", "ren_ru_gu_immortal", "jiang_shan_ru_gu")) {
            JsonObject model = JsonParser.parseString(Files.readString(assets.resolve("models/item/" + id + ".json"))).getAsJsonObject();
            // Two legitimate shapes: legacy 3D element geometry, or a generated flat item model
            // backed by the art texture shipped under textures/item/<id>.png.
            boolean flat = model.has("parent") && model.get("parent").getAsString().equals("minecraft:item/generated")
                    && model.getAsJsonObject("textures").has("layer0")
                    && Files.isRegularFile(assets.resolve("textures/item/" + id + ".png"));
            boolean geometry = model.has("elements") && model.getAsJsonArray("elements").size() >= 5
                    && model.has("display") && model.getAsJsonObject("textures").has("particle");
            check(flat || geometry, "Missing Gu model (neither flat texture nor geometry): " + id);
            for (String locale : List.of("zh_cn", "en_us")) {
                JsonObject lang = JsonParser.parseString(Files.readString(assets.resolve("lang/" + locale + ".json"))).getAsJsonObject();
                for (String key : List.of("item.shengxian." + id, "tooltip.shengxian." + id, "tooltip.shengxian.immortal_gu_binding")) {
                    check(lang.has(key) && !lang.get(key).getAsString().isBlank(), "Missing " + locale + " " + key);
                }
            }
        }
        System.out.println("PASS representative Gu explosion hooks and assets: " + checks + " checks");
    }
}
