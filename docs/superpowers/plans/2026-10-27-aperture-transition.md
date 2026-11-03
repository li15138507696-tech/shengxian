# 仙窍水墨过渡接入 Implementation Plan

> **For agentic workers:** This plan is executed inline in the current session. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 把 V5.4 的仙窍水墨过渡适配为 Veil 4.5.2 效果，在目标玩家进入/离开仙窍时显示 16 tick，并生成不覆盖 0.2.5 的 0.2.6 产物。

**Architecture:** 新增 Veil post pipeline 与 `VeilApertureTransitionRenderer` 反射桥接；现有 `FxPayload.entityId` 标记传送玩家，客户端只对本地目标玩家启动全屏过渡。`ClientFx` 管理 16 tick 生命周期与回退蒙层，`ApertureSpace` 继续广播附近空间音画。游戏规则、仙窍存档和三气特效保持原样。

**Tech Stack:** Minecraft 1.21.1、NeoForge 21.1.263、Java 21、Veil 4.5.2、GLSL 1.50 语义迁移到 Veil Pinwheel pipeline JSON。

## Global Constraints

- 新版本为 `0.2.6`；`shengxian-0.2.5.jar` 和其 SHA-256 保持不变。
- 保留用户确认的 16 tick、只对传送玩家显示、附近玩家仍保留粒子与音效。
- 运行资源 namespace 固定为 `shengxian`；pipeline ID 固定为 `shengxian:aperture_transit`。
- 仅迁移 `aperture_transit` 仙窍过渡 shader；不导入其他 V5.4 模型、物品图标、LSS 页面或无关效果。
- Veil 运行 API 通过反射访问，保持离线 javac classpath 不依赖 Veil 类。
- 不改升仙规则入口、传送权限、本体状态和存档格式。
- 不新增或运行测试；仅运行 `Compile` 生成/编译产物，以及 `Verify` 检查元数据与 JAR 结构。
- 项目无 `.git` 仓库，不执行提交。

---

## File Structure

- **Create** `src/main/resources/assets/shengxian/pinwheel/post/aperture_transit.json`：Veil pipeline，在 `after_level` 阶段把效果应用到 `minecraft:main`。
- **Create** `src/main/resources/assets/shengxian/pinwheel/shaders/program/aperture_transit.json`：使用 `veil:blit_screen` 全屏顶点程序并引用本项目 fragment shader。
- **Create** `src/main/resources/assets/shengxian/pinwheel/shaders/program/aperture_transit.fsh`：从用户素材迁移水墨水流分支；使用 `texCoord` 和 `DiffuseSampler`，与场景颜色按 shader alpha 混合。
- **Create** `src/main/java/net/guzhenren/shengxian/client/VeilApertureTransitionRenderer.java`：反射取得 Veil renderer/post manager，临时启停 pipeline，注册 pre-post-processing 代理并设置 uniforms。
- **Modify** `src/main/java/net/guzhenren/shengxian/client/ClientFx.java`：仅目标本地玩家启动过渡；推进 16 tick、到期移除、世界卸载清理；shader 不可用时显示 16 tick 原版颜色回退。
- **Modify** `src/main/java/net/guzhenren/shengxian/server/ApertureSpace.java`：open/close 的 `FxPayload.entityId` 填入当前传送玩家 ID，附近接收者继续收到原音画。
- **Modify** `build.gradle`、`src/main/resources/META-INF/neoforge.mods.toml`、`build.ps1`：将产物、元数据和 verifier 版本升到 0.2.6，旧产物不覆盖。
- **Modify** `README.md`；**Create** `docs/verification-0.2.6.md`：记录新效果、编译与静态产物检查范围。

## Task 1: 版本与 shader 资源

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/META-INF/neoforge.mods.toml`
- Modify: `build.ps1`
- Create: `src/main/resources/assets/shengxian/pinwheel/post/aperture_transit.json`
- Create: `src/main/resources/assets/shengxian/pinwheel/shaders/program/aperture_transit.json`
- Create: `src/main/resources/assets/shengxian/pinwheel/shaders/program/aperture_transit.fsh`

- [x] **Step 1: Set version pins to 0.2.6.** Change `build.gradle` `version = '0.2.5'` to `version = '0.2.6'`; change `neoforge.mods.toml` `version = "0.2.5"` to `version = "0.2.6"`; change `build.ps1` expected artifact metadata string and output file from `shengxian-0.2.5.jar` to `shengxian-0.2.6.jar`.
- [x] **Step 2: Add the pipeline definition.** Write:

```json
{
  "stages": [
    {
      "type": "veil:blit",
      "shader": "shengxian:aperture_transit",
      "in": "minecraft:main"
    }
  ],
  "renderStage": "after_level"
}
```

- [x] **Step 3: Add the program binding.** Write:

```json
{
  "vertex": "veil:blit_screen",
  "fragment": "shengxian:aperture_transit"
}
```

- [x] **Step 4: Adapt the V5.4 fragment shader.** Start from `素材/guzrlevelupdemoV5.4-特效与贴图/特效/shaders/core/aperture_transit.fsh`; remove `#version 150`; use `in vec2 texCoord`, `uniform sampler2D DiffuseSampler`; preserve procedural water flow, caustics and reveal mask; calculate the transition effect color/alpha and assign `fragColor = vec4(mix(scene.rgb, effectColor, effectAlpha), 1.0)` where `scene = texture(DiffuseSampler, texCoord)`. Do not copy the old vertex shader or change the source material library.
- [x] **Step 5: Confirm version strings and resource names statically.** Run `rg -n '0\.2\.5|0\.2\.6|aperture_transit' build.gradle build.ps1 src/main/resources/META-INF/neoforge.mods.toml src/main/resources/assets/shengxian/pinwheel` and verify 0.2.5 appears only in historical text/old paths, while the target artifact metadata uses 0.2.6.

## Task 2: Veil transition adapter

**Files:**
- Create: `src/main/java/net/guzhenren/shengxian/client/VeilApertureTransitionRenderer.java`

**Interface:**
- `static boolean start(long clientTick, boolean opening)` activates or resets `shengxian:aperture_transit`; returns `false` if the pipeline cannot be activated.
- `static void tick(long clientTick)` removes the pipeline when age reaches 16 ticks.
- `static void clear()` removes the pipeline and clears effect state on level unload.

- [x] **Step 1: Resolve Veil API through reflection.** Load `foundry.veil.api.client.render.VeilRenderSystem`, call static `renderer()`, then `VeilRenderer.getPostProcessingManager()`. Cache methods for manager `add(ResourceLocation)`, `remove(ResourceLocation)`, `isActive(ResourceLocation)` and pipeline `getUniform(CharSequence)`. Catch `ReflectiveOperationException`, `LinkageError` and runtime failures; log one warning per failed state transition.
- [x] **Step 2: Register the callback once.** Load `foundry.veil.platform.VeilEventPlatform`, read `INSTANCE`, load `foundry.veil.api.event.VeilPostProcessingEvent$Pre`, create a `Proxy` implementing that interface, and call `preVeilPostProcessing(proxy)`. In the proxy callback, set values only when the passed pipeline ID equals `shengxian:aperture_transit`.
- [x] **Step 3: Set each existing shader uniform safely.** For each uniform, call `pipeline.getUniform(name)` and skip null/invalid values. Set `Time = clientTick / 20.0f`, `Progress = age / 16.0f`, `Reveal = Progress`, `Resolution = window width/height`, `Accent = (0.58, 0.82, 0.74)` for entry or `(0.50, 0.74, 0.88)` for exit, `Grade = 0.65`, `Opacity = 0.92`, and `Style = 1.0` to select the water branch.
- [x] **Step 4: Make lifecycle idempotent.** `start` accepts an already active pipeline and resets its start tick; `tick` removes it at age 16; `clear` is safe when Veil or the renderer is unavailable and resets cached active state.

## Task 3: Wire targeted packet state into client FX

**Files:**
- Modify: `src/main/java/net/guzhenren/shengxian/server/ApertureSpace.java`
- Modify: `src/main/java/net/guzhenren/shengxian/client/ClientFx.java`

- [x] **Step 1: Tag the destination FX packet with the traveler ID.** For `aperture_open` and both `aperture_close` sends, use the existing overload `AscensionFx.send(level, pos, player.getId(), effect, intensity)` instead of the entityId-less overload. Keep `AscensionFx.send` as nearby distribution so other players still receive particles and sound.
- [x] **Step 2: Start the screen transition only for its target.** In `ClientFx.play`, compare `payload.entityId()` with `Minecraft.getInstance().player.getId()`. Only on equality call `VeilApertureTransitionRenderer.start(clientTicks, opening)`; other nearby clients keep sound and particles without full-screen transition.
- [x] **Step 3: Preserve a short fallback.** If `start` returns false for the local traveler, set `overlayTicks = 16` and preserve the existing entry/exit fallback colors. Keep tribulation overlays unchanged.
- [x] **Step 4: Advance and clear lifecycle.** In `ClientFx.tick`, call `VeilApertureTransitionRenderer.tick(clientTicks)` and, when `minecraft.level == null`, call `clear()` before returning.

## Task 4: Build and document 0.2.6

**Files:**
- Modify: `README.md`
- Create: `docs/verification-0.2.6.md`

- [x] **Step 1: Update README.** Describe the 16 tick client-only aperture transition, note 0.2.6 and its output filename, preserve the entry-gate notice, and record that full client visual acceptance is not claimed.
- [x] **Step 2: Compile/package without tests.** From `D:\daima\mcmod\升仙`, run `& .\build.ps1 -Action Compile`. Expected: Java 21 compilation succeeds, artifact is `build\libs\shengxian-0.2.6.jar`, and the script's metadata/Jar-in-Jar verification passes. This action does not run the core test suite.
- [x] **Step 3: Record artifact checks.** Run `& .\build.ps1 -Action Verify`; inspect the 0.2.6 JAR to confirm the three shader resources, 0.2.6 metadata, Veil 4.5.2 hash, and that `shengxian-0.2.5.jar` still has its recorded SHA-256. Do not launch a game client or server in this pass.
- [x] **Step 4: Write verification record.** Record changed files, artifact size/hash, successful Compile/Verify output, unchanged 0.2.5 hash, and the lack of in-game client visual verification.

---

## Self-Review

- Spec coverage: assets, namespace/Veil adaptation, 16 tick timing, target-player isolation, fallback, 0.2.6 output and 0.2.5 preservation each map to a task.
- Scope: no gameplay, save schema, ascension gate, item registration or unrelated V5.4 assets are changed.
- Verification respects the no-test constraint and does not claim in-game visual acceptance.
- The project has no Git repository, so no commit step is applicable.