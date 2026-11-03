# 0.2.6 仙窍水墨过渡验证记录

日期：2026-10-27

## 改动

- 从 V5.4 素材库迁移 `aperture_transit` 水流分支，接入 Veil `after_level` post pipeline；fragment shader 以 `texCoord` / `DiffuseSampler` 采样并混合真实场景画面。
- 新增 Veil 反射桥接；进入和离开仙窍时仅目标玩家启动 16 tick 过渡。周围玩家继续收到原有音效与粒子。Veil 反射接口不可用时，目标玩家使用 16 tick 原版颜色蒙层回退。
- 世界卸载清理过渡 pipeline；未改升仙门禁、仙窍规则、存档格式或三气效果。
- 版本与构建校验目标升至 0.2.6；0.2.5 JAR 未覆盖。

## 文件

- `build.gradle`、`build.ps1`、`src/main/resources/META-INF/neoforge.mods.toml`
- `src/main/java/net/guzhenren/shengxian/client/ClientFx.java`
- `src/main/java/net/guzhenren/shengxian/client/VeilApertureTransitionRenderer.java`
- `src/main/java/net/guzhenren/shengxian/server/ApertureSpace.java`
- `src/main/resources/assets/shengxian/pinwheel/post/aperture_transit.json`
- `src/main/resources/assets/shengxian/pinwheel/shaders/program/aperture_transit.json`
- `src/main/resources/assets/shengxian/pinwheel/shaders/program/aperture_transit.fsh`
- `README.md`、`docs/verification-0.2.6.md`
## 验证

- `& .\build.ps1 -Action Compile`：退出码 0；Java 21 全源码编译通过，构建从 Minecraft 1.21.1 / NeoForge 21.1.263 profile 读取 110 个本地依赖 JAR。构建内置的元数据与 Jar-in-Jar 校验通过。
- `& .\build.ps1 -Action Verify`：退出码 0；确认模组版本为 0.2.6、Veil 4.5.2 元数据与二进制指纹、pipeline/program JSON、fragment shader 场景混合资源齐全。
- `& .\build.ps1 -Action Test`：100 passed / 0 failed；这是纯 Java 核心行为回归，不加载 Minecraft 或 Veil。编译测试类时有 unchecked/unsafe operations 提示。
- 产物：`build/libs/shengxian-0.2.6.jar`，3,424,946 字节。
- 0.2.6 SHA256：`7258345b4fb46fa5bb40afbfcb16c39ad2958dc169948782ff43a82bd94f3b10`。
- 内嵌 Veil 4.5.2 SHA256：`e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66`。
- 原 `build/libs/shengxian-0.2.5.jar` 保持 3,415,893 字节，SHA256 仍为 `a4294b066271eb1e8cce924f5453c4bb8195d95977af77ca0384573e11cfce19`。

## 限制

- 核心行为测试通过，但没有启动真实客户端或服务端；不据此声称 shader 的游戏内视觉验收通过。
- 项目目录无 Git 仓库，本次未创建提交。