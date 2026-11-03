# 灾劫领域参数与原著边界

更新日期：2026-10-27。所有数值都是游戏映射，不是原著常数。

## 生产默认值

TribulationParameters.standard() 使用 UNRESOLVED_ORDINARY：当前不生成任何灾劫。原著记录的几个具体升仙案例不能推成所有普通升仙的固定灾劫目录。生产能力门禁也保持关闭。

代码保留两个明确标注的原著个例 profile，用于领域层验证：

| Profile | 依据 | 领域原型 |
| --- | --- | --- |
| TAI_BAI_WITNESSED | CANON-012 | 天气来源满足阈值时出现柳风；地气来源满足阈值时出现独角峥虎；两者可并发 |
| BLACK_LOU_LAN_WITNESSED | CANON-009 | 先记录八音磬灾劫；该载体结束后，才允许地气荒兽灾劫出现 |
| FANG_ZHENG_WITNESSED | CANON-013 | 天地二气同时达阈值才转化出血气与血雨；同一场血雨被击退后不再重启 |
| UNRESOLVED_ORDINARY | GAP-001/002 | 不推测灾劫形态或条件，不生成载体 |
| LEGACY_TEST_FIXTURE | 仅旧核心测试 | 保留旧夹具行为，不用于生产 |

## 数值映射

TAI_BAI_WITNESSED 与 BLACK_LOU_LAN_WITNESSED 使用同一组可调领域参数。阈值 60、载体单位 8/3、冲击间隔 20/40 个模拟步、每次冲击风险 +2 都是测试与游戏节奏参数。它们不表示原著灾劫的数值、伤害或时长。默认每 20 个服务端 tick 进行一个模拟步；真实路线当前被门禁阻止。

- 黑楼兰个例中的“八音磬”以八个剩余载体单位表达，不能据此推断每个单位的原著发声次数。
- 太白云生个例中的柳风和独角峥虎分别读取天气/地气来源；不是按二气大小二选一。
- TribulationEngine 可同时保存多个领域载体；schema 8 持久化其世界锚点。0.3.4 运行时用不可见 ArmorStand 命中体、名称和原版粒子呈现已核个例原型；按 owner-scoped 邀请进行击退，不强制加载锚点区块。它不是荒兽 AI，也未完成玩家/多人实机验收。

## 已完成与缺项

已实现：按来源分开的领域 profile、并发状态、模拟步风险、按 ID 处理领域载体、schema 8 锚点持久化和 schema 7 多载体迁移。
0.3.13 起方正个例（CANON-013）成为第三个可运行的领域 profile：`Type.BLOOD_RAIN` 此前在枚举、粒子、显示名和冲击间隔里都已存在，但没有 profile 能产生它，属于"不可达能力"。逐条依据与载体表达力见 `tribulation-matrix.md`。

尚未实现：

- 一般普通路线灾劫目录及依据现场条件选择 profile。
- 荒兽 AI、载体真实攻击行为、灾劫击退完整客户端反馈；区域型血雨载体（见 tribulation-matrix.md 的 TRIB-CARRIER-01/02）。
- 灾劫自行结束的条件无原著依据；当前载体不会自行消失（TRIB-END-01）。
- 玩家/多人护道、区块卸载重载、崩溃重启恢复和战利品边界的真实客户端验收。
- 接入有依据的具体升仙情境、现场天气/地形采样及服务器验收。
- CANON-008 所述真实施蛊调用点反噬；GAP-004 的本命蛊保护、仙蛊损失规则仍未核清。

所以这些领域原型与世界锚点不表示一般路线灾劫已按原著完整还原，也不构成已完成的升仙玩法。

## 2026-10-29 具名 profile 接线与冲击失败

- 服务端配置 `ascension.tribulationProfile` 只允许 `UNRESOLVED_ORDINARY`、`TAI_BAI_WITNESSED`、`BLACK_LOU_LAN_WITNESSED`、`FANG_ZHENG_WITNESSED`。缺省、无效值和内部测试 profile 都回退到 `UNRESOLVED_ORDINARY`；会话首次纳气模拟时冻结 profile，旧 schema 活动纳气会话也迁移到惰性 profile。
- 具名灾劫每次冲击只增加独立外劫压力和序号，不修改天地人三气失衡风险。当前冲击增加量为 2.0；阈值沿用当前 `QiParameters.riskLimit()`（默认 12.0）。两者都是游戏映射参数，不是原著常数。
- 达到阈值时，FAILED 会话保存 profile、载体类别、载体 ID 和冲击序号。独立冲击结算用 `magic()` 伤害源和 2048.0F 固定游戏映射伤害；它不由天地人某一气过盛推导，也不属于原著给出的伤害数值。
- 结算仍要求完整升仙 readiness；当前 `implementedCapabilities()` 为空，所以配置具名 profile 不会开放生产升仙或本体伤害。无来源旧失败只隔离，不自动致死。离线实现和限制见 [verification-witnessed-tribulation-impact.md](../verification-witnessed-tribulation-impact.md)。
