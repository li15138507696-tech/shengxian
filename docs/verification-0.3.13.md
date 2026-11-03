# 0.3.13 离线验证记录（阶段 1：灾劫规则与真实载体）

日期：2026-10-28。本文件随阶段推进追加；当前只覆盖阶段 1，**不是**完整交付记录。

## 本阶段范围

只处理灾劫证据与载体：CANON-007/009/011/012/013 与 GAP-001/002。不触碰用蛊反噬、空窍映射、失败后果、仙窍空间。

## 代码变更

| 文件 | 变更 |
| --- | --- |
| `core/TribulationParameters.java` | 新增 `Profile.FANG_ZHENG_WITNESSED` 与 `fangZhengWitnessed()`；`standard()` 仍是 `UNRESOLVED_ORDINARY` |
| `core/TribulationEngine.java` | 新增 `EventType.STARTED_BLOOD_RAIN`；新增 `startFangZheng`（双源阈值、击退后不重启、消耗两源各一个阈值） |
| `server/AscensionControlService.java` | 新增 `STARTED_BLOOD_RAIN` 的服务端 FX 广播 |
| `client/ClientFx.java` | 新增 `tribulation_blood_rain_start` 表现（红色遮罩、REVERSE_PORTAL 粒子、雨声） |
| `src/test/.../FrameworkTest.java` | 新增 3 项回归 |

## 变更理由（可复核）

- 变更前用探针实测：`standard()`、`taiBaiWitnessed()`、`heiLoulanWitnessed()` 三者在 `ambient=(500,500)` 下都不会产生 `BLOOD_RAIN`，而 `Type.BLOOD_RAIN` 已在枚举、`emitAura`、`displayName`、`impactInterval`、`AscensionVisuals` 中实现——属于"有实现、无来源"的不可达能力。CANON-013 与 GAP-001 明确允许实现独立的方正型 profile，因此补齐来源而不是新增机制。

## 新增回归

1. `production tribulation profile stays unresolved and never invents a carrier`
   —— `standard()` 的 profile 必须是 `UNRESOLVED_ORDINARY`；在 `(500,500)` 气源下 `startIfDue` 与 `tick` 都不产生载体、事件或气机变化。
2. `Fang Zheng witnessed blood rain needs both qi sources and never restarts`
   —— 仅天气或仅地气不启动；两者同时达阈值才出 `BLOOD_RAIN`，两源各消耗一个阈值；重复调用不新增；击退（含超额单位）后清空且不改气机；再次满足阈值也不重启。
3. `Fang Zheng blood rain is weakened by guardian strikes without touching qi or risk`
   —— 每次击退减 1 单位；气机与风险不变；未知载体 ID 是无操作。

## 执行结果

```
& ./build.ps1 -Action Test
Framework tests: 149 passed, 0 failed      (0.3.12 基线为 146)

& ./build.ps1 -Action SourceCheck
Dependencies: Minecraft 1.21.1 / NeoForge 21.1.263 / 110 local jars
Source compile verified.
```

## 发布构建与归档校验

```
& ./build.ps1 -Action Build -ModVersion 0.3.13
Framework tests: 149 passed, 0 failed
PASS saved rank projection: 22 compressed-NBT checks
Static common-side dependency boundary verified: 122 class files
Artifact verified: build/runs/.../shengxian-0.3.13.jar
Embedded Veil: 4.5.2 (SHA256 e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66)
SHA256: a298672f8ea0fed5f5a7033eb6fb996f28e4c23e7152403d603d189087ef20d7
Installed immutable artifact: build/libs/shengxian-0.3.13.jar
```

## 实机验证 A：发布构建在隔离专用服务器

- 环境：`build/test-server`，NeoForge 21.1.263，Java 21，本体 10.20 外层 jar，天意 3.14.0。
- 启动：`Done (3.120s)!`；日志 `shengxian (version 0.2.4 -> 0.3.13)`、`session store opened ... formed plots=3, restored reservations=0`。
- RCON `/shengxian status`：路线仍关闭，并列出全部 8 项缺失机制（碎窍、三气、成窍、仙元、本体写入、灾劫、失败后果、蛊反噬）。
- `/execute in shengxian:aperture run time query gametime` 返回 `The time is 99030`，维度仍在。
- `/stop` 干净退出，`All dimensions are saved`，进程退出码 0。
- 日志中的 ERROR 全部来自本体（缺失注册表键、`guzhenren:fangju` 标签、`guzhenren:nilvhuafenjie` 配方、`guzhenren:zijingsheligu` 进度），与本模组无关。

## 实机验证 B：世界侧灾劫载体（一次性验证构建，未发布）

发布构建的门禁与 `UNRESOLVED_ORDINARY` 生产 profile 会同时阻止载体出现，因此世界侧代码在发布态下不可达。为取得证据，临时构建了 `shengxian-0.3.13-smoke.jar`，只改两处并明确标注 `SMOKE BUILD ONLY`：

1. `GuzhenrenPlayerBridge.CAPABILITIES` 改为全量能力（等价于开门禁）；
2. `AscensionControlService.TRIBULATION` 改为 `taiBaiWitnessed()`。

会话夹具：把 FlowBot（`9083676c-…`）的 IMMORTAL 会话临时改为 ABSORBING（先备份，验证后已还原）。客户端为 `build/launch-client.ps1` 启动的 NeoForge 21.1.263 真实客户端。

观察结果（服务端 RCON 与 schema 8 会话文件）：

- 客户端加入后服务端按 20 tick 推进，天地二气达阈值后**同一 tick 生成两个载体**：`heaven-107528`（柳风，`(-1,74,0)`）与 `earth-107528`（独角峥虎，`(1,74,0)`）。
- 实体是不可见 ArmorStand，标签为 `shengxian:calamity_carrier` 与 `shengxian:tribulation:<b64 owner>:<b64 aperture>:<b64 carrierId>`；显示名"柳风天灾 · 护道者可击退"。
- 实体 UUID 与领域锚点一致：`160d960b-c580-3c6b-b1e5-42da0d1e7b00`（柳风）、`7fddd2cf-15c3-39ab-b90f-8248a038a238`（峥虎）；会话文件写入 `tribulation.N.dimension/x/y/z/entity`。
- **停服重启后**（所有区块卸载），玩家重新登录，两具载体以**相同 UUID** 复原；`@e[type=armor_stand,x=-1,y=74,z=0,distance=..3]` 返回"选择器匹配到多个实体"，证明两具都回来了，且没有重复生成。
- 风险随冲击累积（`riskHeaven/riskEarth/riskHuman` 同步增长）。

验证后：smoke 构建已删除，服务器与客户端 mods 恢复为发布 jar，会话夹具已还原为 IMMORTAL。

## 未验证（仍按未验证记录）

- **护道者击退**：需要第二名真实玩家在授权后实际攻击命中载体，本轮没有第二客户端与人工操作，未验证。
- **区块卸载但不重启**：出生点区块常驻，载体始终处于已加载区块；无法在出生点制造"区块卸载"场景，只有整服重启这一等价路径。
- **客户端视觉**：`ClientFx` 的血雨/柳风表现未做截图或人工确认。
- 多人并发、死亡/换维度、真实存档迁移仍未验证。

## 仍未完成

- 一般普通路线灾劫目录：无原著依据，生产 profile 保持 `UNRESOLVED_ORDINARY`。
- TRIB-CARRIER-01/02（荒兽与峥虎的真实实体/AI、区域型血雨）、TRIB-END-01/02（灾劫自行结束条件、音磬发声节奏）：见 `docs/evidence/tribulation-matrix.md` 第 4 节。
- readiness 继续为空集；所有升仙新入口保持关闭。

## 阶段 2 增量（2026-10-28）：催蛊事件层

### 代码

| 文件 | 内容 |
| --- | --- |
| `core/GuActivation.java` | 一次催动观察：owner、guId、入口、接受/完成/拒绝、tick、sequence；`key()` 供幂等 |
| `core/GuActivationLedger.java` | 按 owner 有界（默认 64）存储，重复事件忽略，`REJECTED` 不入库 |
| `core/GuActivationPolicy.java` | 喂养/持有/纯 tick 不算催动；需要完成语义的入口单列；`mayApplyBacklash` 恒 false |
| `core/UpstreamFingerprint.java` | 外层 `3733c20f…`、内层 `b6d314d3…` 指纹常量与匹配 |
| `FrameworkTest` | 新增 7 项回归 |

### 回归

1. 只有服务端接受过的观察入库，`REJECTED` 被丢弃；
2. 同一事件重放不重复计数；
3. 事件不跨 owner；
4. 六种入口都能区分并分别计数，`REJECTED` 的 tick 不触发策略；
5. 账本按 owner 有界，最旧记录淘汰；
6. 反噬保持关闭（`mayApplyBacklash` 恒 false），且 `use`/`hurtEnemy` 需要完成语义、`inventoryTick` 不需要；
7. 上游指纹常量与匹配行为（大小写不敏感、空值拒绝）。

```
& ./build.ps1 -Action Test
Framework tests: 156 passed, 0 failed

& ./build.ps1 -Action SourceCheck
Source compile verified.
```

### 未做（明确）

- **没有安装任何钩子**：`mixin` 配置与四个入口的注入都未接线，游戏内不会产生事件，也不会改变玩家状态。
- 反噬、蛊虫损失、本命蛊保护未实现，`GU_BACKLASH` 仍关闭。
- 杀招按键入口未定位到具体类。
- 本轮未重新构建 JAR（0.3.13 产物未包含这些类）；如需交付新产物应递增版本号。
