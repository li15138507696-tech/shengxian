# 具名灾劫机制与冲击失败结算设计

日期：2026-10-29  
适用项目：独立升仙模组。本文延续 ordinary-ascension-completion 的已批准路线；不开放普通升仙。

## 目标

把三个已有原著个例 profile 接入可明确选择、服务端权威的灾劫规则，并为累计灾劫冲击导致的致命失败增加独立、可恢复的一次性结算事务。

灾劫冲击不能进入天地人三气过盛失败白名单。灾劫选择、冲击阈值和伤害都属于游戏映射；普通通用 profile 默认不产生灾劫。

## 证据与边界

- CANON-009：黑楼兰个例有八音磬天灾、之后的荒兽地劫、护道者外部应对；音磬和排难仙蛊受损属于该个例。
- CANON-011：灾劫与天地二气过浓蓄积有关，形态种类繁多、难以预判；这不足以推出任意普通升仙的灾劫目录。
- CANON-012、CANON-013：太白云生与方正有不同的具名灾劫例证，可用作独立场景 profile，不能推广成通用固定规则。
- CANON-002、GAP-006：人气、天气、地气各自过盛是现有三气致命失败规则；单空窍失败不可复原，双空窍存活是特殊例外。
- 目前的灾劫外部冲击失败属于游戏映射，不声称是原著通则。未记录明确场景来源的旧失败不能自动按此规则致死。
- 网络章节转载仅用于定位；本设计以项目 canon-rules.md 的条目与限定为实施边界。

## 方案

### Profile 选择与冻结

- 增加服务器配置项选择 TAI_BAI_WITNESSED、BLACK_LOU_LAN_WITNESSED、FANG_ZHENG_WITNESSED 或 UNRESOLVED_ORDINARY。
- 默认值为 UNRESOLVED_ORDINARY；LEGACY_TEST_FIXTURE 不得通过生产配置选择。
- 服务器第一次推进该会话的纳气模拟时，把实际 profile 写入会话进度；后续 tick 与重启恢复都按会话内已保存的 profile，不因配置热改或重启后配置变化而切换规则。
- 旧 schema 中正在纳气但未保存 profile 的会话迁移到 UNRESOLVED_ORDINARY，防止升级后突然生成灾劫。
- 目前阈值、载体单位、冲击间隔和累计压力都是可辨认的游戏参数；不标为原著数值。

### 灾劫冲击

- 继续使用已有具名 profile 的来源触发与并发顺序。
- 外部灾劫压力与三气失衡风险分开记录。灾劫冲击不得同时给天地人三项失衡风险加值，避免一次外劫被误认成多种气机过盛。
- 每次冲击在同一份会话保存中推进外劫压力与稳定冲击序号。
- 达到该 profile 的游戏映射阈值时，提交唯一失败原因 TRIBULATION_IMPACT，并同时保存其来源证明：profile、载体类别与 ID、冲击序号。
- 只有三个具名 profile 可以产生上述来源证明；UNRESOLVED_ORDINARY 不生成载体、压力或冲击失败。
- 来源证明不可变并随 FAILED 记录保留；普通状态修改不能改变失败原因或冲击身份。

### 独立致命结算

- 三气过盛结算入口继续只接受唯一的 HUMAN_OVERLOAD、HEAVEN_OVERLOAD 或 EARTH_OVERLOAD。TRIBULATION_IMPACT 永远不加入其白名单。
- 新增独立的灾劫冲击结算处理器、玩家持久回执和伤害源。它要求：
  - 失败原因恰好是 TRIBULATION_IMPACT；
  - 会话内存在格式正确且属于三个具名 profile 之一的来源证明；
  - 稳定操作 ID 与本次会话及失败阶段修订相符；
  - 总 readiness 已开放。
- 来源证明无效、缺失、来自旧存档或与玩家回执冲突时，写入 UNCERTAIN 并保留两侧证据；绝不自动再次致死。
- 结算顺序采用现有持久回执原则：玩家 STARTED 回执先保存并核对；世界 STARTED 成功后只尝试一次伤害；只有匹配的死亡克隆与重生回执保存核对后才能确认世界 ACK。保存失败、服务端退出或结果不明都隔离，不能保证跨世界文件与玩家 NBT 的 exactly-once。
- 冲击结算不借用三气过盛的 cause-specific DamageSource 映射。不会按 TRIBULATION_IMPACT 推导某一类天地人气机失衡。
- readiness 的实现能力集合仍为空；本设计不开放碎窍、调气、灾劫或失败新入口。实现完成也不能替代全路线门禁与实机验收。

## 数据与兼容

- 会话 schema 增加 profile 快照、独立外劫压力/序号及失败来源证明；旧 schema 会话保留原 qi、灾劫和 failure data，不丢现有 carrier。
- 旧版无 profile 的活跃会话采用 UNRESOLVED_ORDINARY。旧版已有 TRIBULATION_IMPACT 失败但缺来源证明的记录按 UNCERTAIN 解释，不重放伤害。
- 玩家灾劫回执使用独立 PlayerPersisted 键，不与三气失败回执混用；写入、保存、UUID 玩家文件核对和单人房主加载源核对遵循现有 receipt 审计边界。
- 世界失败状态保留原有 FAILED 与 settlement markers；新增专用 API 验证冲击来源证明后才能推进冲击结算状态。三气 API 对 TRIBULATION_IMPACT 仍拒绝。

## 验证

- RED/GREEN 回归覆盖：配置默认空 profile；旧会话迁移不启动灾劫；具名 profile 独立触发；累计外劫压力不污染三气风险；只产生单一 TRIBULATION_IMPACT 与来源证明。
- 结算回归覆盖：普通三气 handler 拒绝 TRIBULATION_IMPACT；冲击 handler 拒绝缺来源/伪造 profile/重复载体/混合原因；匹配 receipt 只确认不重伤；STARTED、旧记录、保存失败和事件结果未知均不会自动重试。
- 验证 session restart 后 profile、压力、载体锚点、稳定冲击序号和两侧 receipt 关系一致。
- 运行 FrameworkTest、Java 21 NeoForge SourceCheck 及玩家 NBT 离线检查。
- 本阶段不启动 Minecraft 客户端、服务器、集成世界或 DSH；真实死亡、重生、多人护道与崩溃窗口留待单独授权的隔离世界验收。

## 自审

- 普通通用 profile 明确保持 inert；未把三个故事个例合成为概率表或统一灾劫目录。
- TRIBULATION_IMPACT 与三气过盛是不同失败来源，两个 handler、来源校验与 DamageSource 映射隔离。
- 所有不确定恢复均选择隔离而非再次造成死亡；未宣称跨存档 exactly-once。
- readiness 保持关闭；没有把具名 profile 实现误当成一般路线完成。