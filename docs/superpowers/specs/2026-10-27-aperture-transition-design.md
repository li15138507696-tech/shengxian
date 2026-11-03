# 仙窍水墨过渡着色器接入设计

日期：2026-10-27  
状态：待用户审阅

## 目标

将用户提供的 V5.4 素材中 `特效/shaders/core/aperture_transit` 水墨过渡效果，适配到 `shengxian` 当前 Veil 4.5.2 渲染环境。玩家进入或离开仙窍时，只给正在传送的玩家显示约 16 tick 的全屏过渡；周围玩家仍看到原有空间粒子和听到原有音效。

以新版本 `shengxian-0.2.6.jar` 交付，保留 `shengxian-0.2.5.jar` 不变。升仙规则入口继续关闭，不改本体状态、传送权限和仙窍存档逻辑。

## 现状依据

- `shengxian-0.2.5.jar` 已包含项目中 59 个 Java 类和 12 项运行资源；这次只补用户素材库中尚未进入运行包的仙窍过渡效果。
- `素材/guzrlevelupdemoV5.4-特效与贴图` 有 646 个特效/贴图文件。大部分是仙蛊模型、物品图标和其他效果，与当前框架没有对应实体、物品或渲染触发点。
- 当前客户端 `ClientFx` 对 `aperture_open` 和 `aperture_close` 使用长时间纯色蒙层；`ApertureSpace` 在传送后发送这两种服务端特效消息。
- 用户素材 shader 的水墨过渡分支提供 `Time`、`Progress`、`Resolution`、`Accent`、`Grade`、`Opacity`、`Reveal`、`Style` 等 uniform；当前项目将 Veil 4.5.2 以内嵌 Jar-in-Jar 交付。

## 方案

### 资源

只移植 `aperture_transit` 全屏水墨过渡效果，放到 `shengxian` 自有命名空间：

- `assets/shengxian/pinwheel/post/aperture_transit.json`
- `assets/shengxian/pinwheel/shaders/program/aperture_transit.json`
- `assets/shengxian/pinwheel/shaders/program/aperture_transit.fsh`

程序使用 Veil 全屏 blit 顶点着色器；适配后的 fragment shader 继续使用原素材的水流、焦散和环形揭幕逻辑，并删除旧核心着色器中不适用于 Veil post pipeline 的声明。运行资源 ID 统一为 `shengxian:aperture_transit`。用户提供的原始素材库保留不动。

### 客户端触发

- 收到 `aperture_open` 或 `aperture_close` 后，客户端为 16 tick 过渡记录开始 tick 与效果类型。
- `Progress` 和 `Reveal` 随进度从 0 递增到 1；`Time` 使用客户端时间；`Resolution` 取实际 framebuffer 尺寸；`Accent` 使用玉青色。`Style` 选择素材中的水流过渡分支。
- 通过 Veil post-processing manager 临时启用 `shengxian:aperture_transit`，并在 Veil 的 pre-post-processing 回调中为当前 pipeline 设置 uniform。计时结束、客户端世界卸载或资源不可用时移除活动 pipeline。
- 使用现有 `FxPayload.entityId` 标记传送玩家。服务器仍向附近玩家发送粒子/音效；客户端只在该实体 ID 等于本地玩家 ID 时启动全屏 shader，避免旁观玩家被蒙屏。
- shader 不可用时保留短时的原版颜色蒙层作为退路。现有三气流带、粒子、声音和灾劫蒙层保持原样。

### 版本与范围

- 版本提升到 0.2.6；同步更新 `build.gradle`、模组元数据、`build.ps1` 的产物路径和验证版本，以及 README/验证记录。
- 新产物写为 `build/libs/shengxian-0.2.6.jar`，不覆盖 0.2.5。
- 不导入其余 V5.4 仙蛊图标、GeckoLib 模型、GZSM 模型、LSS 页面脚本或无关 shader；不添加新玩法、物品或外部模组依赖。

## 验收条件

1. 0.2.6 构建产物包含上述三项 Veil 资源，资源路径和 namespace 均为 `shengxian`。
2. 0.2.5 JAR 的字节与 SHA-256 保持不变。
3. `aperture_open/close` 仍保留现有附近粒子和声音；只有正在传送的玩家显示约 16 tick 的 shader 过渡。
4. shader 到期或玩家离开世界后，pipeline 状态被清理，不影响之后的普通画面。
5. 不声称已完成完整玩家客户端画面验收；本轮实现验证范围为 Java 编译、资源 JSON/JAR 收录和 artifact 元数据校验。

## 风险与处理

- Veil pipeline 和 shader resource schema 对版本敏感：以本项目内嵌 Veil 4.5.2 API 为准，并使用项目现有反射桥接风格避免离线编译 classpath 依赖 Veil。
- 旧素材 shader 使用原模组 namespace：迁移时统一改为 `shengxian`，pipeline/program 互相引用也必须一致。
- 现有特效包发给附近玩家：继续用它呈现空间音画；本地全屏过渡根据 `entityId` 过滤，仅目标玩家触发。
- 若运行时反射或 shader 加载失败，记录一次警告并回退到原版短蒙层，不影响服务端传送和数据。