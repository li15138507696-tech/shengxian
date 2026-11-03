import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.JumpInsnNode;

/** Static validation of exact accepted Gu activation injection points; loads no game classes. */
public final class VerifyGuActivationHooks {
    private static final String PLAYER_VARS = "net/guzhenren/network/GuzhenrenModVariables$PlayerVariables";
    private static final String ESSENCE_FIELD = "Lnet/guzhenren/network/GuzhenrenModVariables$PlayerVariables;zhenyuan:D";
    private static final String INNER_HASH = "b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc";
    private static final List<Target> TARGETS = List.of(
            new Target("YueGuangGuProcedureMixin", "net/guzhenren/procedures/YueGuangGuDangYouJiKongQiShiShiTiDeWeiZhiProcedure",
                    "(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"),
            new Target("BaiXiangSheGuProcedureMixin", "net/guzhenren/procedures/BaiXiangSheGuYouJiKongQiShiShiTiDeWeiZhiProcedure",
                    "(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"),
            new Target("AiBieChiProcedureMixin", "net/guzhenren/procedures/AiBieChiDangShengWuShiTiBeiGaiWuPinJiZhongProcedure",
                    "(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"),
            new Target("DingGu2ProcedureMixin", "net/guzhenren/procedures/DingGu2YouJiFangKuaiShiFangKuaiDeWeiZhiProcedure",
                    "(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"));

    private static final String PROCEDURE_DESCRIPTOR = "(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V";
    private static final List<KillerMoveTarget> KILLER_MOVE_TARGETS = List.of(
            new KillerMoveTarget("SanZhaoAnJianCProcedureMixin", "net/guzhenren/procedures/SanZhaoAnJianCAnXiaAnJianShiProcedure", "sanzhao_CD2", 'C'),
            new KillerMoveTarget("SanZhaoAnJianVProcedureMixin", "net/guzhenren/procedures/SanZhaoAnJianVAnXiaAnJianShiProcedure", "sanzhao_CD3", 'V'),
            new KillerMoveTarget("SanZhaoAnJianXProcedureMixin", "net/guzhenren/procedures/SanZhaoAnJianXAnXiaAnJianShiProcedure", "sanzhao_CD1", 'X'),
            new KillerMoveTarget("SanZhaoAnJianZProcedureMixin", "net/guzhenren/procedures/SanZhaoAnJianZAnXiaAnJianShiProcedure", "sanzhao_CD", 'Z'));

    private record Target(String mixinSimpleName, String targetInternalName, String targetDescriptor) {}
    private record KillerMoveTarget(String mixinSimpleName, String targetInternalName,
                                    String cooldownField, char keybind) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("expected upstream JAR, config JSON, mods TOML, and compiled classes directory");
        Path upstream = Path.of(args[0]);
        Path configFile = Path.of(args[1]);
        Path modsFile = Path.of(args[2]);
        Path classes = Path.of(args[3]);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(upstream)));
        check(hash.equals(INNER_HASH), "unexpected upstream JAR fingerprint: " + hash);
        JsonObject config;
        try (var reader = Files.newBufferedReader(configFile)) { config = JsonParser.parseReader(reader).getAsJsonObject(); }
        check(config.get("required").getAsBoolean(), "Gu mixins must be required on the supported base-mod fingerprint");
        check(config.get("compatibilityLevel").getAsString().equals("JAVA_21"), "Mixin Java compatibility mismatch");
        List<String> configured = new ArrayList<>();
        config.getAsJsonArray("mixins").forEach(value -> configured.add(value.getAsString()));
        String packageName = config.get("package").getAsString();
        String prefix = packageName.replace('.', '/') + "/";
        String mods = Files.readString(modsFile);
        check(java.util.regex.Pattern.compile("\\[\\[mixins\\]\\]\\s*config = \"shengxian\\.common\\.mixins\\.json\"").matcher(mods).results().count() == 1, "NeoForge must register the common Gu mixin config exactly once");

        try (JarFile jar = new JarFile(upstream.toFile())) {
            for (Target target : TARGETS) {
                check(configured.contains(target.mixinSimpleName()), "mixin not listed in config: " + target.mixinSimpleName());
                ClassNode upstreamClass = read(jar.getInputStream(jar.getJarEntry(target.targetInternalName() + ".class")));
                MethodNode method = upstreamClass.methods.stream()
                        .filter(candidate -> candidate.name.equals("execute") && candidate.desc.equals(target.targetDescriptor()))
                        .findFirst().orElseThrow(() -> new AssertionError("missing target signature " + target.targetInternalName()));
                int writes = 0;
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field && instruction.getOpcode() == Opcodes.PUTFIELD
                            && field.owner.equals(PLAYER_VARS) && field.name.equals("zhenyuan") && field.desc.equals("D")) writes++;
                }
                check(writes == 1, target.mixinSimpleName() + " target must contain exactly one accepted-branch zhenyuan write, found " + writes);

                Path mixinFile = classes.resolve(prefix + target.mixinSimpleName() + ".class");
                ClassNode mixinClass;
                try (InputStream stream = Files.newInputStream(mixinFile)) { mixinClass = read(stream); }
                check(hasMixinTarget(mixinClass, target.targetInternalName()), "mixin targets the wrong class: " + target.mixinSimpleName());
                MethodNode handler = mixinClass.methods.stream().filter(candidate -> candidate.name.equals("shengxian$stageAcceptedGu"))
                        .findFirst().orElseThrow(() -> new AssertionError("mixin cost-stage callback missing: " + target.mixinSimpleName()));
                String expectedHandler = target.targetDescriptor().substring(0, target.targetDescriptor().indexOf(')')) + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
                check(handler.desc.equals(expectedHandler), "callback parameters must match the full target signature: " + handler.desc);
                verifyInject(handler, target.targetDescriptor(), ESSENCE_FIELD);
                String entry = target.mixinSimpleName().equals("AiBieChiProcedureMixin") ? "ATTACK_HURT_ENEMY"
                        : target.mixinSimpleName().equals("DingGu2ProcedureMixin") ? "BLOCK_USE_ON" : "RIGHT_CLICK_USE";
                String stagingMethod = target.mixinSimpleName().equals("YueGuangGuProcedureMixin") ? "stageDelayed" : "stageAccepted";
                verifyStageDispatch(handler, entry, stagingMethod);

                switch (target.mixinSimpleName()) {
                    case "YueGuangGuProcedureMixin" -> verifyYueGuangCompletion(jar, mixinClass, target);
                    case "BaiXiangSheGuProcedureMixin" -> {
                        MethodNode complete = findMethod(mixinClass, "shengxian$completeAfterSnakeSpawn");
                        verifyInjectPoint(complete, target.targetDescriptor(), "INVOKE",
                                "Lnet/minecraft/world/entity/Entity;setDeltaMovement(DDD)V", "AFTER");
                        verifyCompletionDispatch(complete, entry, true);
                        verifyBaiXiangResultEvidence(method);
                    }
                    case "AiBieChiProcedureMixin" -> {
                        MethodNode complete = findMethod(mixinClass, "shengxian$completeAfterHitEffects");
                        verifyInjectReturn(complete, target.targetDescriptor());
                        verifyCompletionDispatch(complete, entry, true);
                        check(hasMethodCallNamed(method, "net/minecraft/world/effect/MobEffectInstance", "<init>"),
                                "AiBieChi target has no synchronous hit effect evidence");
                    }
                    case "DingGu2ProcedureMixin" -> verifyDingGu2Completion(method, mixinClass, target);
                    default -> throw new AssertionError("unclassified accepted Gu target: " + target.mixinSimpleName());
                }
                System.out.println("PASS " + target.mixinSimpleName() + " -> " + target.targetInternalName() + " cost and result gates");
            }
            for (KillerMoveTarget target : KILLER_MOVE_TARGETS) {
                check(configured.contains(target.mixinSimpleName()), "mixin not listed in config: " + target.mixinSimpleName());
                var upstreamEntry = jar.getJarEntry(target.targetInternalName() + ".class");
                check(upstreamEntry != null, "missing killer-move target class: " + target.targetInternalName());
                ClassNode upstreamClass = read(jar.getInputStream(upstreamEntry));
                MethodNode method = upstreamClass.methods.stream()
                        .filter(candidate -> candidate.name.equals("execute") && candidate.desc.equals(PROCEDURE_DESCRIPTOR))
                        .findFirst().orElseThrow(() -> new AssertionError("missing killer-move target signature " + target.targetInternalName()));
                int markerWrites = 0;
                int durabilityWrites = 0;
                for (AbstractInsnNode instruction : method.instructions) {
                    if (!(instruction instanceof FieldInsnNode field) || instruction.getOpcode() != Opcodes.PUTFIELD
                            || !field.owner.equals(PLAYER_VARS)) continue;
                    if ((field.name + ":" + field.desc).equals(target.cooldownField() + ":D")) {
                        markerWrites++;
                    } else if (field.name.endsWith("_NaiJiu") && field.name.startsWith("ShaZhao")) {
                        durabilityWrites++;
                    }
                }
                check(markerWrites > 0, target.mixinSimpleName() + " target has no cooldown cost write");
                check(durabilityWrites > 0, target.mixinSimpleName() + " target has no Gu durability writes");

                Path mixinFile = classes.resolve(prefix + target.mixinSimpleName() + ".class");
                ClassNode mixinClass;
                try (InputStream stream = Files.newInputStream(mixinFile)) { mixinClass = read(stream); }
                check(hasMixinTarget(mixinClass, target.targetInternalName()), "mixin targets the wrong class: " + target.mixinSimpleName());
                String expectedHandler = PROCEDURE_DESCRIPTOR.substring(0, PROCEDURE_DESCRIPTOR.indexOf(')'))
                        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
                MethodNode begin = mixinClass.methods.stream().filter(candidate -> candidate.name.equals("shengxian$beginGuObservation"))
                        .findFirst().orElseThrow(() -> new AssertionError("mixin begin callback missing: " + target.mixinSimpleName()));
                MethodNode end = mixinClass.methods.stream().filter(candidate -> candidate.name.equals("shengxian$recordChangedGu"))
                        .findFirst().orElseThrow(() -> new AssertionError("mixin return callback missing: " + target.mixinSimpleName()));
                check(begin.desc.equals(expectedHandler), "killer-move begin callback parameters mismatch: " + begin.desc);
                check(end.desc.equals(expectedHandler), "killer-move return callback parameters mismatch: " + end.desc);
                verifyKillerMoveInjection(begin, PROCEDURE_DESCRIPTOR, "HEAD");
                verifyKillerMoveInjection(end, PROCEDURE_DESCRIPTOR, "RETURN");
                verifyKillerMoveDispatch(begin, target.keybind(), "beginKillerMoveObservation");
                verifyKillerMoveDispatch(end, target.keybind(), "endKillerMoveObservation");
                System.out.println("PASS " + target.mixinSimpleName() + " -> " + target.targetInternalName() + " entry/return durability observation");
            }
        }
        verifyBacklashService(classes, prefix);
        System.out.println("PASS exact Gu activation hooks: " + (TARGETS.size() + KILLER_MOVE_TARGETS.size()) + " verified targets");
    }

    private static ClassNode read(InputStream input) throws Exception {
        try (input) { ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG); return node; }
    }

    private static boolean hasMixinTarget(ClassNode node, String target) {
        for (AnnotationNode annotation : annotations(node.visibleAnnotations, node.invisibleAnnotations)) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
            Object value = value(annotation, "value");
            if (!(value instanceof List<?> types)) return false;
            return types.stream().anyMatch(type -> type instanceof Type classType && classType.getInternalName().equals(target));
        }
        return false;
    }

    private static void verifyInject(MethodNode handler, String descriptor, String expectedField) {
        AnnotationNode inject = annotations(handler.visibleAnnotations, handler.invisibleAnnotations).stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
                .findFirst().orElseThrow(() -> new AssertionError("callback lacks @Inject"));
        Object methodValue = value(inject, "method");
        check(methodValue instanceof List<?> methods && methods.contains("execute" + descriptor), "callback method descriptor mismatch");
        check(Integer.valueOf(1).equals(value(inject, "require")), "callback must require one injection");
        Object atValue = value(inject, "at");
        AnnotationNode at = null;
        if (atValue instanceof AnnotationNode node) at = node;
        else if (atValue instanceof List<?> points && points.size() == 1 && points.get(0) instanceof AnnotationNode node) at = node;
        check(at != null, "callback must define exactly one injection point");

        check("FIELD".equals(value(at, "value")), "callback is not attached to a field write");
        check(expectedField.equals(value(at, "target")), "callback field target mismatch");
        check(Integer.valueOf(Opcodes.PUTFIELD).equals(value(at, "opcode")), "callback must follow its accepted cost write");
        Object shift = value(at, "shift");
        check(shift instanceof String[] enumValue && enumValue[1].equals("AFTER"), "callback must run after the accepted write");
    }

    private static MethodNode findMethod(ClassNode node, String name) {
        return node.methods.stream().filter(candidate -> candidate.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("missing hook handler " + node.name + "." + name));
    }

    private static void verifyInjectPoint(MethodNode handler, String descriptor, String expectedAt,
                                         String expectedTarget, String expectedShift) {
        AnnotationNode inject = annotations(handler.visibleAnnotations, handler.invisibleAnnotations).stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
                .findFirst().orElseThrow(() -> new AssertionError("callback lacks @Inject"));
        Object methodValue = value(inject, "method");
        check(methodValue instanceof List<?> methods && methods.contains("execute" + descriptor),
                "completion callback target descriptor mismatch");
        check(Integer.valueOf(1).equals(value(inject, "require")), "completion callback must require one injection");
        AnnotationNode at = annotationValue(value(inject, "at"));
        check(at != null && expectedAt.equals(value(at, "value")), "completion callback has the wrong injection type");
        check(expectedTarget.equals(value(at, "target")), "completion callback target call mismatch");
        Object shift = value(at, "shift");
        check(shift instanceof String[] enumValue && enumValue[1].equals(expectedShift),
                "completion callback has the wrong invocation shift");
    }

    private static void verifyInjectReturn(MethodNode handler, String descriptor) {
        AnnotationNode inject = annotations(handler.visibleAnnotations, handler.invisibleAnnotations).stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
                .findFirst().orElseThrow(() -> new AssertionError("callback lacks @Inject"));
        Object methodValue = value(inject, "method");
        check(methodValue instanceof List<?> methods && methods.contains("execute" + descriptor),
                "return callback target descriptor mismatch");
        check(Integer.valueOf(1).equals(value(inject, "require")), "return callback must require one injection");
        AnnotationNode at = annotationValue(value(inject, "at"));
        check(at != null && "RETURN".equals(value(at, "value")), "synchronous completion must be observed at return");
    }

    private static AnnotationNode annotationValue(Object atValue) {
        if (atValue instanceof AnnotationNode node) return node;
        if (atValue instanceof List<?> points && points.size() == 1 && points.get(0) instanceof AnnotationNode node) return node;
        return null;
    }

    private static void verifyStageDispatch(MethodNode handler, String entry, String expectedMethod) {
        String observer = "net/guzhenren/shengxian/server/GuActivationObserver";
        String descriptor = "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/guzhenren/shengxian/core/GuActivation$Entry;)V";
        check(hasCall(handler, observer, expectedMethod, descriptor),
                "cost stage does not use the expected observer: " + expectedMethod);
        check(hasEnumEntry(handler, entry), "cost stage supplies the wrong Gu entry: " + entry);
    }

    private static void verifyCompletionDispatch(MethodNode handler, String entry, boolean fullTarget) {
        String observer = "net/guzhenren/shengxian/server/GuActivationObserver";
        String inventoryTarget = "net/guzhenren/shengxian/server/GuBacklashService$TargetRef";
        String targetFactory = "()Lnet/guzhenren/shengxian/server/GuBacklashService$TargetRef;";
        check(hasCall(handler, inventoryTarget, "inventory", targetFactory),
                "completed Gu use does not carry an inventory target identity");
        String descriptor = fullTarget
                ? "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/guzhenren/shengxian/core/GuActivation$Entry;Lnet/guzhenren/shengxian/server/GuBacklashService$TargetRef;)V"
                : "(Lnet/guzhenren/shengxian/core/GuActivation$Entry;"
                    + "Lnet/guzhenren/shengxian/server/GuBacklashService$TargetRef;)V";
        check(hasCall(handler, observer, "completeStaged", descriptor),
                "result point does not submit its staged activation");
        check(hasEnumEntry(handler, entry), "completion supplies the wrong Gu entry: " + entry);
    }

    private static void verifyYueGuangCompletion(JarFile jar, ClassNode mixinClass, Target target) throws Exception {
        MethodNode queuedSpawn = findMethod(read(jar.getInputStream(jar.getJarEntry(target.targetInternalName() + ".class"))),
                "lambda$execute$1");
        check("(Lnet/minecraft/world/entity/Entity;)V".equals(queuedSpawn.desc), "moonlight projectile lambda signature changed");
        check(countCalls(queuedSpawn, "net/minecraft/world/level/Level", "addFreshEntity",
                "(Lnet/minecraft/world/entity/Entity;)Z") == 1, "moonlight lambda must have one projectile-add result point");
        MethodNode redirect = findMethod(mixinClass, "shengxian$confirmProjectileSpawn");
        verifyRedirect(redirect, "lambda$execute$1(Lnet/minecraft/world/entity/Entity;)V",
                "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z");
        check(hasCall(redirect, "net/minecraft/world/level/Level", "addFreshEntity",
                "(Lnet/minecraft/world/entity/Entity;)Z"), "moonlight redirect must preserve the original spawn call");
        check(hasCall(redirect, "net/guzhenren/shengxian/server/GuActivationObserver", "completeDelayed",
                "(Lnet/minecraft/world/entity/Entity;Lnet/guzhenren/shengxian/core/GuActivation$Entry;)V"),
                "moonlight result does not complete only after accepted projectile insertion");
        check(hasCall(redirect, "net/guzhenren/shengxian/server/GuActivationObserver", "discardDelayed",
                "(Lnet/minecraft/world/entity/Entity;Lnet/guzhenren/shengxian/core/GuActivation$Entry;)V"),
                "rejected moonlight projectile leaves no pending activation");
        check(hasEnumEntry(redirect, "RIGHT_CLICK_USE"), "moonlight completion entry changed");
    }

    private static void verifyBaiXiangResultEvidence(MethodNode method) {
        int cost = indexOfField(method, Opcodes.PUTFIELD, PLAYER_VARS, "zhenyuan", "D");
        int spawn = indexOfCall(method, "net/minecraft/world/entity/EntityType", "spawn",
                "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;)Lnet/minecraft/world/entity/Entity;");
        int movement = indexOfCall(method, "net/minecraft/world/entity/Entity", "setDeltaMovement", "(DDD)V");
        check(cost >= 0 && spawn > cost && movement > spawn, "Bai Xiang spawn result must follow its accepted cost");
        boolean nullGuard = false;
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (index > spawn && index < movement && instruction instanceof JumpInsnNode jump
                    && jump.getOpcode() == Opcodes.IFNULL) nullGuard = true;
            index++;
        }
        check(nullGuard, "Bai Xiang success branch must guard the spawned entity before completion");
    }

    private static void verifyDingGu2Completion(MethodNode method, ClassNode mixinClass, Target target) {
        int cost = indexOfField(method, Opcodes.PUTFIELD, PLAYER_VARS, "zhenyuan", "D");
        String setBlock = "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z";
        check(countCalls(method, "net/minecraft/world/level/LevelAccessor", "setBlock", setBlock) == 1,
                "Ding Gu II must have one exact block-placement result point");
        check(cost >= 0 && indexOfCall(method, "net/minecraft/world/level/LevelAccessor", "setBlock", setBlock) > cost,
                "Ding Gu II block placement must follow its accepted cost");
        MethodNode redirect = findMethod(mixinClass, "shengxian$confirmBlockPlacement");
        verifyRedirect(redirect, "execute" + target.targetDescriptor(),
                "Lnet/minecraft/world/level/LevelAccessor;setBlock" + setBlock);
        check(hasCall(redirect, "net/minecraft/world/level/LevelAccessor", "setBlock", setBlock),
                "Ding Gu II redirect must preserve the upstream setBlock return value");
        verifyCompletionDispatch(redirect, "BLOCK_USE_ON", false);
        check(hasCall(redirect, "net/guzhenren/shengxian/server/GuActivationObserver", "discardStaged",
                "(Lnet/guzhenren/shengxian/core/GuActivation$Entry;)V"),
                "failed Ding Gu II placement must discard its staged activation");
    }

    private static void verifyRedirect(MethodNode handler, String methodName, String expectedTarget) {
        AnnotationNode redirect = annotations(handler.visibleAnnotations, handler.invisibleAnnotations).stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;"))
                .findFirst().orElseThrow(() -> new AssertionError("handler lacks @Redirect"));
        Object methodsValue = value(redirect, "method");
        check(methodsValue instanceof List<?> methods && methods.contains(methodName), "redirect targets the wrong method");
        check(Integer.valueOf(1).equals(value(redirect, "require")), "redirect must require exactly one source call");
        AnnotationNode at = annotationValue(value(redirect, "at"));
        check(at != null && "INVOKE".equals(value(at, "value"))
                && expectedTarget.equals(value(at, "target")), "redirect invocation target mismatch");
    }

    private static boolean hasCall(MethodNode method, String owner, String name, String descriptor) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner)
                    && call.name.equals(name) && call.desc.equals(descriptor)) return true;
        }
        return false;
    }

    private static int countCalls(MethodNode method, String owner, String name, String descriptor) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner)
                    && call.name.equals(name) && call.desc.equals(descriptor)) count++;
        }
        return count;
    }

    private static int indexOfCall(MethodNode method, String owner, String name, String descriptor) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner)
                    && call.name.equals(name) && call.desc.equals(descriptor)) return index;
            index++;
        }
        return -1;
    }

    private static int indexOfField(MethodNode method, int opcode, String owner, String name, String descriptor) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && instruction.getOpcode() == opcode
                    && field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(descriptor)) return index;
            index++;
        }
        return -1;
    }

    private static boolean hasEnumEntry(MethodNode method, String entry) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.owner.equals("net/guzhenren/shengxian/core/GuActivation$Entry")
                    && field.name.equals(entry)) return true;
        }
        return false;
    }

    private static void verifyBacklashService(Path classes, String prefix) throws Exception {
        ClassNode observer;
        try (InputStream input = Files.newInputStream(classes.resolve("net/guzhenren/shengxian/server/GuActivationObserver.class"))) {
            observer = read(input);
        }
        String targetRef = "net/guzhenren/shengxian/server/GuBacklashService$TargetRef";
        String service = "net/guzhenren/shengxian/server/GuBacklashService";
        check(observer.methods.stream().filter(method -> method.name.equals("endKillerMoveObservation"))
                        .anyMatch(method -> hasCall(method, targetRef, "killerMove", "(II)L" + targetRef + ";")),
                "killer-move changes must preserve their exact group and slot");
        check(observer.methods.stream().filter(method -> method.name.equals("recordCompleted"))
                        .anyMatch(method -> hasCall(method, service, "completed",
                                "(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/UUID;Lnet/guzhenren/shengxian/core/GuActivation;Lnet/minecraft/world/item/ItemStack;L" + targetRef + ";)V")),
                "completed observer events are not wired to the backlash transaction");

        ClassNode backlash;
        try (InputStream input = Files.newInputStream(classes.resolve("net/guzhenren/shengxian/server/GuBacklashService.class"))) {
            backlash = read(input);
        }
        MethodNode completed = findMethod(backlash, "completed");
        check(hasMethodCallNamed(completed, "net/guzhenren/shengxian/core/GuBacklashState", "observe"),
                "server transaction does not use the pressure state machine");
        check(hasCall(completed, "net/minecraft/world/item/ItemStack", "shrink", "(I)V"),
                "threshold loss does not decrement exactly the source stack");
        check(hasMethodCallNamed(backlash, "net/guzhenren/shengxian/compat/SavedGuBacklash", "record"),
                "threshold loss does not write a saved receipt");
        check(hasMethodCallNamed(backlash, "net/guzhenren/shengxian/compat/BenMingGuFingerprint", "of"),
                "threshold loss does not compare the bound Gu fingerprint");
        check(hasMethodCallNamed(backlash, "net/guzhenren/shengxian/compat/SavedGuBacklash", "verify"),
                "player file read-back verification is missing");
        check(hasMethodCallNamed(backlash, "net/guzhenren/shengxian/compat/SavedGuBacklash", "verifyState"),
                "saved pressure read-back verification is missing");
        check(hasMethodCallNamed(backlash, "net/guzhenren/shengxian/core/GuBacklashTargetLocator", "inventorySlot"),
                "ordinary inventory target is not resolved by exact stack identity");
        check(hasMethodCallNamed(backlash, "net/guzhenren/shengxian/compat/SavedGuBacklash", "verifyHost"),
                "singleplayer host read-back verification is missing");
        MethodNode login = findMethod(backlash, "reconcileOnLogin");
        check(hasMethodCallNamed(login, "net/guzhenren/shengxian/server/GuBacklashService", "verifyPlayerAndHostState"),
                "login must compare the singleplayer host source before accepting more pressure");
    }

    private static boolean hasMethodCallNamed(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        }
        return false;
    }

    private static boolean hasMethodCallNamed(ClassNode node, String owner, String name) {
        return node.methods.stream().anyMatch(method -> hasMethodCallNamed(method, owner, name));
    }

    private static void verifyKillerMoveInjection(MethodNode handler, String descriptor, String expectedAt) {
        AnnotationNode inject = annotations(handler.visibleAnnotations, handler.invisibleAnnotations).stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
                .findFirst().orElseThrow(() -> new AssertionError("callback lacks @Inject"));
        Object methodValue = value(inject, "method");
        check(methodValue instanceof List<?> methods && methods.contains("execute" + descriptor), "callback method descriptor mismatch");
        check(Integer.valueOf(1).equals(value(inject, "require")), "callback must require one injection");
        Object atValue = value(inject, "at");
        AnnotationNode at = null;
        if (atValue instanceof AnnotationNode node) at = node;
        else if (atValue instanceof List<?> points && points.size() == 1 && points.get(0) instanceof AnnotationNode node) at = node;
        check(at != null && expectedAt.equals(value(at, "value")), "killer-move callback must be attached at " + expectedAt);
    }

    private static void verifyKillerMoveDispatch(MethodNode handler, char keybind, String observerMethod) {
        String observer = "net/guzhenren/shengxian/server/GuActivationObserver";
        String descriptor = "(Lnet/minecraft/world/entity/Entity;C)V";
        boolean found = false;
        for (AbstractInsnNode instruction : handler.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(observer)
                    || !call.name.equals(observerMethod) || !call.desc.equals(descriptor)) continue;
            found = true;
            AbstractInsnNode previous = instruction.getPrevious();
            while (previous != null && (previous.getType() == AbstractInsnNode.LABEL
                    || previous.getType() == AbstractInsnNode.LINE || previous.getType() == AbstractInsnNode.FRAME)) previous = previous.getPrevious();
            int expected = keybind;
            boolean suppliesExpectedKey = previous instanceof IntInsnNode intInsn && intInsn.operand == expected
                    || previous instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value && value == expected;
            check(suppliesExpectedKey, "mixin passes the wrong killer-move keybind: " + handler.name);
        }
        check(found, "mixin does not call the killer-move observation phase: " + handler.name);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    @SafeVarargs
    private static List<AnnotationNode> annotations(List<AnnotationNode>... groups) {
        List<AnnotationNode> all = new ArrayList<>();
        for (List<AnnotationNode> group : groups) if (group != null) all.addAll(group);
        return all;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
