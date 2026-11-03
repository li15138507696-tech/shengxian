# 0.3.23 验证记录：0.3.22 启动崩溃修复与整批验收

日期：2026-10-30。基线 0.3.21。本版只修一个会让专用服务器起不来的缺陷，并把 0.3.22 那批未发版改动第一次真正跑起来。

## 0.3.22 的问题（未交付）

0.3.22 通过了 199 项回归、8 个钩子静态校验、6 类回执 NBT 检查和归档 Verify，但**在隔离专用服务器启动时崩溃**：

```
java.lang.NullPointerException
  at java.util.ImmutableCollections$ListN.indexOf(ImmutableCollections.java:723)
  at net.neoforged.neoforge.common.ModConfigSpec$ValueSpec.test(ModConfigSpec.java:1120)
  at net.neoforged.neoforge.common.ModConfigSpec.correct(...)
  at net.neoforged.fml.config.ConfigTracker.createDefaultConfig(ConfigTracker.java:224)
```

根因：`tribulationProfile` 用 `defineInList` 定义，校验器是 `acceptableValues.contains(value)`。NeoForge 在“键不在已有配置文件里”和“生成默认配置”这两条路径上会用 `null` 探测校验器，而 `List.of(...)` 是 `ImmutableCollections.ListN`，`contains(null)` 直接抛 NPE。旧配置文件缺少该键时崩溃，删掉配置重新生成同样崩溃。

0.3.22 产物保留但**不可用**，SHA256 `0a6f03413694a4fd8c03e5dedf48d8712f9f51c245440b20a0888571df8f7e9b`。

## 0.3.23 修复

- `ShengxianConfig.tribulationProfile` 改为普通 `define`，合法值写进注释；读取端 `TribulationParameters.configuredProfile` 增加 null/空值兜底，未知值仍解析为 `UNRESOLVED_ORDINARY`。
- 顺带修了构建脚本的陈旧守卫：`build.ps1` 的 mixin 校验仍写死 4 个钩子，0.3.22 起配置里有 10 个（4 蛊 + 4 三招 + 2 地形），会导致打包阶段报 `Gu activation mixin config mismatch`。现在 0.3.22+ 期望 10 个，历史版本仍按 4 个校验。

## 验证

```
& ./build.ps1 -Action Test                     → 199 passed, 0 failed
& ./build.ps1 -Action SourceCheck -ModVersion 0.3.23
  → 8 个蛊激活钩子、70 项爆炸/资源检查、6 类回执（rank 22 / failure 22 / tribulation impact 22 /
    gu backlash 31 / shatter essence 30 / immortal gu 19）、237 个 common-side 类
& ./build.ps1 -Action Build -ModVersion 0.3.23
  → SHA256 dadf2889905fcbce0480cf243f4e6748d65260704ebcdf3a5c214eb1fc9c5d9f
```

产物：`build/libs/shengxian-0.3.23.jar`，3,819,862 字节。

## 隔离专用服务器（首次真正启动 0.3.22 那一批）

| 项目 | 结果 |
| --- | --- |
| 启动 | `shengxian (version 0.3.22 -> 0.3.23)`，`Done (2.398s)!` |
| 配置文件 | 缺失的 `shengxian-server.toml` 自动重新生成，含 `guBacklashThreshold`、`tribulationProfile`、`[ascension.immortalGu]` 全部键 |
| Mixin | `Preparing shengxian.common.mixins.json (10)`；`LandscapeExplosionMixin` 与 `LandscapeBlockChangeMixin` 已实际注入 |
| 物品注册 | `/give FlowBot shengxian:ding_xian_you` → `[Fixed Immortal Travel]`；`ren_ru_gu_immortal` → `[Man as Before Immortal Gu]`；`jiang_shan_ru_gu` → `[Landscape as Before]` |
| 目录命令 | `/shengxiangu list` 列出 4 条：pai_nan、ding_xian_you、ren_ru_gu_immortal、jiang_shan_ru_gu |
| shengxian 异常 | 0 |

## 仍未验证

- 反噬的实机语义（纳气期、结果点、阈值触发）没有在游戏里跑过。
- 三只仙蛊的实际效果（定仙游传送、人如蛊治疗、江山如蛊地形还原）只有单元测试与静态检查，未实机使用。
- 爆炸 reserve→publish 的掉落复原未在实机触发。
- 碎窍真元事务、多人并发、崩溃注入均未实机验收。
- 本轮未改 `implementedCapabilities()`，普通升仙入口继续关闭。
