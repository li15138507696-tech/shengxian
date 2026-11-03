> 历史设计稿：本文记录 0.2.0 前的安全关闭架构。0.2.4 曾接入普通五转流程；0.2.5 已重新关闭入口并补强仙窍地块隔离。当前事实见 `docs/ascension-implementation-plan.md` 和 `docs/verification-0.2.5.md`。

# 升仙独立框架

2026-10-25。用户在已交付升仙设计后要求“单开个项目，做一个框架”，本轮据此建立独立 `shengxian` 工程，目标位置 `D:/daima/mcmod/升仙`。

采用 Java 21、Minecraft 1.21.1、NeoForge，框架不依赖天意附属。只实现可验证的领域状态、规则能力检查、存档接口与只读本体诊断。完整原著方案保留为后续路线，不把尚未考据的条件变成硬编码。事件接入使用当前本地运行库中的签名。

范围：三步状态流程、按玩家与空窍区分的会话、不可逆碎窍标记、revision 与动作序号检查、未知 schema 拒绝覆盖、原子文件写入、明确的规则未核实状态、只读玩家快照、`/shengxian status`。状态流转是领域框架，不自动完成三气模拟、渡劫或世界副作用。

真实升仙入口保持关闭：当前适配器没有修改空窍、形成真实仙窍、仙元收支等能力，不修改本体修为/寿元/背包，不装入真实游戏目录。框架不会假造已完成的仙窍或仙蛊。存档按单文件原子替换保存框架会话，不声称跨玩家/区块文件事务。

设计选择：新工程独立编译与打包，依赖由指定本体 JAR 与本地启动器 profile 只读取得，不引用天意源码或其 `_pack`。使用 PowerShell 构建避免现有终端缺 Python；提供 Gradle 入口供后续扩展，基础脚本不需要联网。

现有目录无 Git 仓库，不自动初始化或创建 worktree。先在授权工作区暂存并完成验证，再申请文件访问权限复制至同级新目录。源计划中的“天意项目内实施”和“只规划”是上一轮的范围，已由本轮新项目框架请求取代；原著事实和待核规则保留。

## 2026-10-25 阶段 2 补充：五身份与 schema 2

原模型把 `revision` 同时当阶段转换次数和保存版本，并用奇偶性约束可达阶段，导致纳气阶段无法每刻保存。阶段 2 拆分为：

- `phaseRevision`：只随阶段转换 +1；奇偶性只描述 PREPARING↔BREAKING 循环。
- `stateRevision`：每次持久化变更 +1（含同阶段更新），作为仓库 CAS 令牌，且不小于 `phaseRevision`。
- `lastActionSequence`：客户端动作序号，严格递增，和 revision 解耦。
- `serverTick`：服务端模拟时钟，非递减；允许同一 tick 内多个不同序号的动作。
- `effectKey`：`AscensionProgress.appliedEffects` 中的幂等编号；重复键拒绝，账本不能缩小。

保存语义：同阶段更新不改 phase/broken/phaseRevision，只增 stateRevision；阶段转换同时增两个 revision。仓库仍只做单文件原子替换，不冒充跨文件事务。

schema 2 字段：`phaseRevision/stateRevision/sequence/serverTick`、三气容器、领悟额度、`effect.count/effect.N`、`apertureDraft`。schema 1 映射为 `phaseRevision=stateRevision=revision`、`sequence=sequence`、`serverTick=0`、进度为空；坏记录和未来 schema 保留原字节并拒绝覆盖。

三气/领悟/effectKey 目前只是有界容器，没有成功公式、阈值或奖励规则；这些要等阶段 4 的证据与参数冻结后再填。

## 2026-10-25 schema 3 与三气引擎

- AscensionProgress 增加 Risk(heaven, earth, human) 与 Window(ticks, id)；窗口 id 用于 insight:<id> 幂等键。
- 仓库 schema 3 新增 riskHeaven/riskEarth/riskHuman/windowTicks/windowId；schema 2 读取时这两组取空值，保存时升级。
- QiEngine 是纯 Java 连续步进：外部二气再生与吸入、人气有限释放、三向超额风险、平衡衰减、窗口开启/关闭、每窗一次领悟、求知分心风险。没有总成功率或固定雷劫。
- AscensionService.stepQi 只允许 ABSORBING；致命失衡与 FAILED 在同一 stateRevision 保存中提交。
- 参数表见 docs/evidence/qi-parameters.md，全部标为游戏映射。

## 2026-10-25 schema 4 与灾劫领域

- AscensionProgress 增加 Tribulation(id, type, remaining, ticksUntilImpact)；type 为 NONE/SOUND_CHIMES/ANCIENT_BEAST。
- 仓库 schema 4 新增 tribulation* 字段；schema 3 读取时灾劫取空值，保存时升级。
- TribulationEngine：外部二气总量达到阈值后按主导二气选择音磬/荒兽；冲击给三向风险加固定值；护道人 strike 只减载体单位，不改气机、风险或窗口；载体清空只写一次 tribulation:<id>:survived，无掉落。
- AscensionService.stepSimulation 在同一 stateRevision 下依次执行 QiEngine 与 TribulationEngine，任一致命则提交 FAILED。
- 载体实体、区块语义、可见效果和多人同步仍未实现；参数见 docs/evidence/tribulation-parameters.md。

## 2026-10-25 schema 5 与仙窍账本

- AscensionProgress 增加 Aperture(areaMu, timeRatio, essence, internalTicks, plotIndex)。
- 仓库 schema 5 新增 aperture* 字段；schema 4 读取时仙窍取空值，保存时升级。
- ApertureEngine 只负责账本：一次性形成、内部时钟按时间倍率推进、仙元产出/支出；不补寿元。
- 尚未创建独立维度/空间、传送入口、主人权限或地形；不能把账本当作真实仙窍已完成。
