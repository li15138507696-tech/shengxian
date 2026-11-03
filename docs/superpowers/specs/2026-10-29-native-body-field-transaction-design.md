# 本体字段事务与恢复设计

日期：2026-10-29  
适用项目：独立升仙模组。此阶段只收紧已经审计的本体字段写入；普通路线 readiness 继续关闭。

## 目标

让碎窍真元扣费具备与现有六转境界、失窍写入相同等级的持久回执和恢复核验。每个副作用都必须绑定升仙 session 与阶段身份，在玩家存档和单人房主加载源中读回确认；不确定时停止并保留证据，不重放扣费或覆盖本体后续合法变化。

## 当前代码证据

- `AscensionLifecycleService.shatter` 先把 `Phase.BREAKING` 和固定 `shatterEssenceTarget` 保存到世界会话，再调用 `GuzhenrenPlayerBridge.applyTrueEssenceTarget`。该写入只在内存里降低 `zhenyuan` 并标记同步；动作返回前没有保存或读回 UUID 玩家文件及单人 `level.dat/Data/Player`。世界记录目前不保留扣费前值。
- 登录恢复 `resumeShatterClock` 会再次按世界目标下调当前 `zhenyuan` 并调用 `saveAll()`，但没有玩家侧 shatter receipt，也没有读取保存后的本体字段核验；随后会重设碎窍计时基线。
- 六转投影已有 `SavedRankProjection` 与 session 回执；它保存 `zhuanshu/jieduan`，验证 UUID 玩家文件，并对单人房主验证 `level.dat/Data/Player`，再确认世界侧同步标记。
- 失败与具名灾劫结算已有独立 `SavedFailureReceipt` / `SavedTribulationImpactReceipt`，验证 `shiqiao` 和来源回执；readiness 关闭时不会应用这些失败后果。
- 上游审计确认没有独立的本体仙窍/仙元玩家字段；`kongqiao` 是本体分类编码，不能代表升仙进度；`renqi` 是派生值，`shouyuan` 的单位仍未核实。以上字段继续只读，不增加写入。

## 设计

### 字段边界

本阶段处理 `zhenyuan` 碎窍扣费事务。`zhuanshu/jieduan` 和 `shiqiao` 保留现有字段专用投影与来源回执；实现计划需要复核其调用顺序及恢复门禁，但不把它们改写成通用字段框架。不得写 `kongqiao`、`shouyuan`、`renqi`，也不得伪造本体仙窍或仙元字段。

### 碎窍真元事务

- 事务身份绑定 `sessionId + phaseRevision + shatterEssenceTarget`；同一碎窍尝试不得换目标。
- 世界侧 schema 10 意图同时保存扣费前值与固定目标，避免“世界已进 BREAKING、玩家 PREPARED 尚未落盘”这一崩溃窗口丢失原值。schema 9 及更早仅带目标的活动碎窍仍可读取和保留，但没有可证明的原值时不能补扣。
- 玩家侧回执存在 `PREPARED`、`COMMITTED`、`UNCERTAIN` 三态，保存原值、固定目标和实际确认值。该回执保存在 NeoForge `PlayerPersisted`，不修改上游字段格式。
- 首次写入顺序：世界会话先提交不可逆 BREAKING 意图与目标；玩家侧保存并核验 PREPARED；仅当本体当前值仍等于记录原值时将 `zhenyuan` 下调到固定目标；同一玩家 NBT 保存 COMMITTED 回执和实际值；读回玩家文件及单人房主加载源，确认 UUID、session/阶段/目标、回执、`zhenyuan` 值匹配；只有全部通过，才继续碎窍计时。
- 恢复时 PREPARED 且本体值等于原值，执行一次向下写入并提交；值已等于目标，表示扣费可能已落盘，只补 COMMITTED，不再次扣费；任何第三种值、非法数值、回执身份不符或玩家与房主来源不一致都转为 UNCERTAIN，保留本体现状，不猜测或补扣。
- 旧版只有目标、无 baseline 且没有匹配玩家回执的活动 BREAKING 会话转 UNCERTAIN 隔离，不自动扣费；若已有身份/目标匹配的 COMMITTED 玩家回执则仅确认，不回放。
- COMMITTED 回执与世界意图吻合时，恢复只确认已有扣费，不再次钳制本体；碎窍计时 tick 也不得重复应用目标，因此本体后续正常获得或消耗真元不会被旧碎窍目标覆盖。
- 保存失败、回读失败或事务身份冲突时，保持 BREAKING 记录待处理并隔离该 session；禁止启动计时、自动回滚本体字段或重复扣费。

### 六转与失窍写入复核

实现计划复核现有 `zhuanshu/jieduan` 与 `shiqiao` 回执都先核验来源、只允许预期阶段转换、保存后验证玩家及必要的单人加载源，并在恢复时不覆盖之后的合法境界变化。发现缺口时只增加对应字段专用回归/保护，不扩大失败白名单，不开放 readiness。

## 错误处理与兼容

- 缺失、损坏或未知版本的玩家回执不能被当作“未扣费”而自动重做；session 保持不可继续的待核状态。
- 不从单一存档来源推断另一来源；单人 owner 的 player `.dat` 与 `level.dat/Data/Player` 必须各自匹配同一提交。
- 不迁移或覆盖用户本体字段，不触碰真实存档。所有验证限定在离线 NBT fixture 和明确隔离的运行环境。
- `AscensionReadiness.implementedCapabilities()` 继续为空；此工作不使普通路线或失败后果开放。

## 验收标准

- RED/GREEN 覆盖：首次扣费、保存后崩溃、字段已下调但 COMMITTED 未保存、重登恢复、目标值不可变、BREAKING tick 不重复扣费、第三值隔离、NaN/Infinity 拒绝、玩家文件与单人 host NBT 一致、两个来源冲突隔离、schema 9 target-only 迁移隔离、旧回执/新 session 不串用。
- 六转与失窍已有回执路径具备针对调用顺序、错误恢复与后续合法改动不被覆盖的回归。
- 完成 `FrameworkTest`、NBT receipt check 与 `SourceCheck`；没有运行隔离服务器时明确不宣称实机完成，也不开放 readiness。
