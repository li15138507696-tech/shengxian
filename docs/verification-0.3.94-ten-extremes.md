# 0.3.94 十绝体路线（CANON-007）

日期：2026-11-02。首个特殊升仙路线落地。

## 原著依据

CANON-007（译本第 686 节《黑楼兰升仙（上）》，中文第四卷）：

- 普通空窍需要蛊师不断催动真元冲击窍壁；十绝空窍的窍壁早已难以承受，解开体内蛊虫对空窍的封印后**自行碎裂**。
- "十绝升仙，天灾地劫非常恐怖"。
- 小家子气蛊是上古气道五转蛊，能影响天灾地劫。

边界与验收（同条记录）：黑楼兰个例不能推广为所有十绝体都持有排难仙蛊或小家子气蛊；十绝体路线必须有独立 profile，不能套普通碎窍；**不能把个例蛊虫当固定配置发放**。

## 落地前的事实核对

- `tenExtremes` 来自本体 `kongqiao == 3.0`。该编码已审计：`kongqiao` 是**资质/体质编码（3=十绝体）**，`shiqiao` 才是失窍布尔——见 `ascension-implementation-plan.md` 第 196 行。UPSTREAM-006 的"升仙模块不应接入 `kongqiao`"指的是不得把它当**空窍状态或碎窍完整度**使用，不涉及体质分类；本改动沿用既有读法，未新增语义风险。

## 实现

### 1. 独立资格与 profile

- 新增 `TenExtremesQualification.fivePeak(...)`：五转、四至五阶段（含偏移）、**必须是十绝体**、未失窍。
- 新增 `RuleProfile.tenExtremes()`，id `ten-extremes-five-to-six`。
- 刻意做成**两个独立谓词**而不是给普通判定加布尔开关：普通路线必须继续拒绝十绝体（此前正是靠 `!tenExtremes` 把它们挡在门外），共用带开关的谓词太容易把某条路线悄悄放宽。

### 2. 自碎：不消耗真元，不需要"冲击"步骤

- 新增 `AscensionService.beginSelfRupture(...)`：`PREPARING → BREAKING`，只写 `ShatterClock`，**不写真元扣费意图**。
- 之所以能不写真元意图：真元意图本就是可选的——仓储只在意图存在时才校验（`FileSessionRepository` 的 shatter 目标一致性检查以 `!shatterEssenceTargetKeys().isEmpty()` 为前提），生命周服用 `.orElse(null)` 兜底。因此**无需改 schema**。
- 阶段图未改：`PREPARING` 仍只能到 `BREAKING`。区别在机制——普通路线是持续冲击并扣费，十绝体是解封后自行崩裂。碎裂时长沿用同一参数；原著只说"自行碎裂"，未规定时长，属游戏映射。

### 3. 灾劫强度不同

- 新增 `TribulationParameters.Profile.TEN_EXTREMES` 与 `TribulationParameters.tenExtremes()`：触发阈值更低（成形更早）、载体更多、间隔更短、单次冲击风险更高——**严格比普通档更凶**。
- 由**体质**决定，不由运维配置：`AscensionService.start` 新增可选 `frozenTribulationProfile`，`prepare()` 对十绝体在创建时即冻结为 `TEN_EXTREMES`。该值随会话持久化（`tribulationProfile` 属性），后续 `TribulationEngine` 会用冻结值重建参数，配置里的普通 profile 覆盖不了它。
- `configuredProfile("TEN_EXTREMES")` 显式回落 `UNRESOLVED_ORDINARY`：运维手写该名不会改变任何路线的难度。
- 仓储创建期不变量相应放开一格：新记录只额外允许携带 `TEN_EXTREMES`，其余预置字段仍一律拒绝。

## 刻意未实现：小家子气蛊影响灾劫

未实现，不是遗漏。理由：

- 原著只说它能影响天灾地劫，**没有给出本体侧对应物品 ID**；`docs/evidence/gu-activation-api.md` 的 8 个已核催动点里没有它。
- CANON-007 的验收明确要求"不能把个例蛊虫当固定配置发放"。在无法确认真实物品来源前，任何"检测某 ID 就减伤"的实现都是编造 ID 或伪造效果。
- 因此保持关闭，等待物品 ID 与催动点证据；这条不影响路线可玩性，只影响该个例的护道手段。

## 验证

| 项目 | 结果 |
| --- | --- |
| 核心回归 `-Action Test` | **213 项通过，0 失败**（原 211，+2） |
| `-Action Build -ModVersion 0.3.94` | 通过，exit 0 |
| `-Action Verify -ModVersion 0.3.94` | 通过，exit 0 |
| 静态依赖边界 | 276 个 class 文件（+1：`TenExtremesQualification`） |

产物：`build/libs/shengxian-0.3.94.jar`
SHA256：`650b7c0610e3dd2c9a1c9e6754f850eea104a730f2c62b8349751cd0627d02ad`

新增断言：

- 十绝体判定接受十绝体五转巅峰（含阶段偏移），拒绝普通体、失窍、错转数。
- **普通判定仍然拒绝十绝体**（`!OrdinaryQualification.fivePeak(5,4,false,true,false)`）——两条路线的隔离被钉住。
- 自碎后 `shatterEssenceTargetKeys()` 为空、`shatterEssenceTarget()` 为空、`shatterClock` 存在；重复自碎被拒；写入后重新读取仍为空（持久化一致）。
- 对照组：普通入口仍写入真元意图。
- 灾劫档次逐项严格更凶（阈值、载体数、间隔、风险四项都比普通档严）；`configuredProfile` 回落；冻结值可持久化读回。

## 边界

- **未实机验证**。十绝体路线的真实入口需要本体 `kongqiao == 3.0` 的角色，本次只做了域层与编译验证，没有启动游戏。
- 灾劫"恐怖"的具体数值是游戏映射，原著无数字；本实现只保证"严格比普通档更凶"这一可检验关系。
- 小家子气蛊影响灾劫未实现（见上）。
- 未改动普通路线、仙窍账本、失败结算、六转写入、视觉层；普通路线的行为完全不变（213 项中既有 211 项原样通过）。
