# 灾劫证据矩阵（阶段 1）

日期：2026-10-28。基线：0.3.15。本页把原著条目逐条对到代码与载体，用于判断"哪些灾劫有依据、哪些只能保持关闭"。
所有数值仍是游戏映射（见 `tribulation-parameters.md`），不是原著常数。

## 0. 结论摘要

- **一般普通路线的灾劫目录仍无正文依据**：CANON-011 只说明成因（天地二气过浓蓄积）与"种类繁多、难以预判"，不构成目录。生产 profile 保持 `UNRESOLVED_ORDINARY`，不生成任何载体；`AscensionReadiness.implementedCapabilities()` 继续返回空集。
- 有正文依据、可作为**个例 profile** 实现的有三例：太白云生（CANON-012）、黑楼兰（CANON-009/011）、方正（CANON-013）。本阶段补齐了此前**不可达**的方正血雨 profile。
- 十绝体（CANON-007）与神不知（GAP-002）没有可实现的形态或数值依据，保持未启用。
- 现有"不可见 ArmorStand 命中体"只能表达**可被护道者击退的领域载体**；它不能表达真实战斗实体、AI 或区域型灾劫。为不发明数值，本轮**不**实现伪 AI，缺口登记在第 4 节。

## 1. 逐条矩阵

| 条目 | 出处 | 触发条件 | 外形 | 影响对象 | 应对 | 结束条件 | 个例/通则 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| CANON-009 天灾 | 译本第 688 节 | 天气过浓蓄积（CANON-011） | 八道音磬，每次两响，共四次 | 升仙者本人 | 黑楼兰以排难仙蛊"排难之光"逐一消灭，代价是双目爆裂、仙蛊自爆 | 载体被全部消灭 | 黑楼兰个例 | `TribulationEngine.startHeiLoulan`（`TribulationEngine.java:76`），`Type.SOUND_CHIMES` |
| CANON-009 地劫 | 译本第 688 节 | 天灾之后地气蓄积 | 上古荒兽劫，出现冰瀑神猿（七转地劫） | 升仙者与护道者 | 护道者（方源）击杀/击退 | 荒兽被击退 | 黑楼兰个例 | 同 profile 第二阶段，`Type.ANCIENT_BEAST`（`TribulationEngine.java:76`） |
| CANON-012 柳风 | 第三卷 202–205 章 | 天气来源达阈值 | 碧玉柳叶/柳风 | 升仙者 | 本人平衡三气；护道者处理外劫 | 载体被消灭 | 太白云生个例 | `TribulationEngine.startTaiBai`（`TribulationEngine.java:40`），`Type.JADE_WILLOW_WIND` |
| CANON-012 峥虎 | 第三卷 202–205 章 | 地气来源达阈值 | 独角峥虎 | 同上 | 同上 | 载体被消灭 | 太白云生个例 | 同上，`Type.SINGLE_HORNED_TIGER`；与柳风可并发 |
| CANON-013 血雨 | 第五卷第 625 章 | 天地二气**共同**转化为血气与血雨（天庭安排、血道背景） | 大规模血气、血雨 | 升仙者 | 护道者（樊西流）从外部干扰血云、削弱血雨；本人仍须平衡三气 | 载体被削弱至消失 | 方正个例 | **本轮新增** `TribulationEngine.startFangZheng`（`TribulationEngine.java:113`），`Type.BLOOD_RAIN` |
| CANON-011 一般成因 | 第 686/687 节 | 天地二气过浓、蓄积 | 种类繁多、难以预判 | — | 临场应对 | — | **通则（定性）** | `TribulationParameters.standard()` = `UNRESOLVED_ORDINARY`，不生成载体 |
| CANON-007 十绝体 | 第 686 节 | 十绝空窍自行碎裂 | "天灾地劫非常恐怖"；小家子气蛊可影响灾劫 | 十绝体升仙者 | 未核 | 未核 | 个例（黑楼兰） | 无 profile；十绝路线整体未启用 |
| GAP-002 神不知 | 未读到正文 | 未核 | 未核 | 天地灾劫不加身，但三气仍须本人调和 | 未核 | 未核 | 未核 | 无 profile |
| GAP-001 普通通则 | 第 687 节 | 未核 | 未核 | 未核 | 未核 | 未核 | **通则缺依据** | `UNRESOLVED_ORDINARY`，readiness 关闭 |

## 2. 服务端状态接线核对

| 事件 | 提交位置 | 是否"先提交、后生效" | 备注 |
| --- | --- | --- | --- |
| 灾劫开始 | `AscensionService.stepSimulation` → `TribulationEngine.startIfDue` → 一次 `repository.save` | 是 | 开始与当步纳气同一次状态版本提交 |
| 冲击（外劫压力累积） | `TribulationEngine.tick` → 同一次 save | 是 | 具名 profile 每次增加独立压力与冲击序号；达 `QiParameters.riskLimit`（默认 12.0，游戏参数）时提交有来源证明的 TRIBULATION_IMPACT，不改三气失衡风险 |
| 护道击退 | `TribulationWorldService.onAttackEntity` → `SessionStore.strikeTribulation` | 是 | 先保存，后播粒子、后 `discard` 实体 |
| 载体结束 | `TribulationEngine.strike` 写 `tribulation:<id>:survived` | 是 | 幂等键；重复击打不再扣减 |
| 失败 | `stepSimulation` 提交 `Phase.FAILED` + `FailureCause.TRIBULATION_IMPACT` 及具名来源证明 | 是 | 独立冲击结算器核对 profile/载体/序号；三气 handler 白名单不包含该原因 |
| 区块卸载 | `TribulationWorldService.reconcile` 检查 `hasChunk` 后跳过 | 不适用 | **不强制加载区块，不把卸载当死亡**；重载后按确定性 UUID 复原 |
| 视觉 | `AscensionFx` / `ClientFx` | 否 | 只读已提交结果，不驱动状态 |

### 具名 profile 与冲击结算边界（2026-10-29）

- 服务端可显式选择三个已核个例；默认、无效配置和旧版活动纳气会话均采用 `UNRESOLVED_ORDINARY`。profile 随会话冻结，配置变化不会切换已有会话规则。
- 冲击压力和序号独立保存。致命冲击只允许 singleton `TRIBULATION_IMPACT`，并携带 profile、载体类别/ID、冲击序号；同一原子会话提交也保留三气风险不变。
- 冲击后果走独立事务和 `shengxian:tribulation_impact` 玩家回执。一次伤害尝试必须有玩家 STARTED 与世界 STARTED 持久证明；缺来源、回执冲突、存档失败或结果不明时进入 UNCERTAIN，不自动重试。
- 当前总 readiness 仍关闭。因此具名选择与领域状态实现不等于当前生产流程可达；离线结算代码不代表已在真实世界验收死亡/重生链路。

## 3. 载体表达力评估（ArmorStand 是否足够）

| 类型 | 需要的表现 | 现有载体能力 | 结论 |
| --- | --- | --- | --- |
| `SOUND_CHIMES` 音磬 | 非战斗；四轮发声、逐轮被消灭 | 命中体 + END_ROD 粒子 + 击退计数 | **状态层够用**；"两响一次"的节奏未表达（见 TRIB-END-01） |
| `JADE_WILLOW_WIND` 柳风 | 非战斗，环境型 | 命中体 + CLOUD 粒子 | 状态层够用 |
| `SINGLE_HORNED_TIGER` 峥虎 | 真实生物、会攻击 | 不可见命中体，无 AI、无攻击 | **不足**（TRIB-CARRIER-01） |
| `ANCIENT_BEAST` 荒兽 | 七转级荒兽，护道者交战 | 同上 | **不足**（TRIB-CARRIER-01） |
| `BLOOD_RAIN` 血雨 | 区域型天象，可被外部削弱 | 单点命中体 | **不足**（TRIB-CARRIER-02） |

## 4. 未解决项（不得写成已完成）

- **TRIB-CARRIER-01**：峥虎/荒兽需要真实生物实体与 AI。原著未给血量、攻击力、移动与持续时间，直接实现等于自造数值，故本轮不做。
- **TRIB-CARRIER-02**：血雨是区域型灾劫，需要范围载体（区域伤害/视野压制），现有单点命中体不足以表达。
- **TRIB-END-01**：灾劫"自行结束"的条件无原著依据。当前实现中载体**不会自行消失**，只有被击退（`strike`）或本人失败才结束；冲击按间隔重复。这是游戏映射，标为待核。
- **TRIB-END-02**：音磬"每次两响、共四次"与当前"8 个载体单位、每 20 步一次冲击"不是同一件事。单位数只决定击退所需次数，不表示原著发声次数。
- **TRIB-GATE-01**：三个具名 profile 已可由服务器配置选择，但普通升仙入口与冲击后果仍由 `AscensionReadiness` 关闭；配置 profile 不等于开放生产路线。

## 5. 本阶段实现与验证

- 新增 `Profile.FANG_ZHENG_WITNESSED` 与 `TribulationParameters.fangZhengWitnessed()`（`TribulationParameters.java:27`、`:74`）。
- 新增 `TribulationEngine.startFangZheng`：天地二气**同时**达阈值才启动；消耗两源各一个阈值；同一场血雨被击退后不再重启（幂等键 `tribulation:fang-zheng-blood-*:survived`）。
- 新增 `EventType.STARTED_BLOOD_RAIN`，并接入服务端 FX（`AscensionControlService`）与客户端表现（`ClientFx`）。
- 新增 3 项纯 Java 回归：生产 profile 惰性、方正血雨双源门槛与不重启、护道击退不改气机与风险。
- 结果：`FrameworkTest` 149 项通过 / 0 失败；`SourceCheck` 全源码编译通过；0.3.13 Build/Verify 通过。
- 世界侧实机（2026-10-28，一次性 smoke 构建，验证后删除）：真实客户端下两具载体按锚点生成、UUID 与会话记录一致、停服重启后同 UUID 复原。见 `verification-0.3.13.md`。护道击退与客户端视觉未验证。

## 6. 依据编号对照

CANON-007、CANON-009、CANON-011、CANON-012、CANON-013、GAP-001、GAP-002，详见 `canon-rules.md`。
