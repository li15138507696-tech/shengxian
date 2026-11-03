# 纳气期蛊反噬与蛊损失实施计划

> **实施记录：** 规格经用户审阅通过后，按 RED/GREEN 完成。勾选项记录本轮已完成步骤。

**Goal:** 在 `ABSORBING` 阶段只对已确认成功的精确蛊催动累计服务器阈值，并在阈值到达时安全损失触发该次的非本命蛊一只。

**Architecture:** 纯领域模型负责 session 绑定、压力阈值、序号、幂等和结果分类；服务端适配层负责解析普通背包或上游杀招槽中的确切目标、核对本命蛊指纹、执行一次损失并写入玩家持久化回执。Mixin 只在上游成功结果点提交已完成事件；不确定目标、效果、保存或双存档来源时消耗阈值并隔离，不改动替代蛊。readiness 始终关闭。

**Tech Stack:** Java 21、NeoForge 1.21.1、Mixin/ASM、Minecraft ItemStack/CompoundTag、现有 `FrameworkTest`、压缩 NBT 离线验证器。

## Global Constraints

- 本规则仅作用于 `Phase.ABSORBING`；每次确认催动增加 1 压力；默认阈值为 8，服务器参数范围 1–64。
- 达阈值时只尝试损失触发事件所用的一只蛊；阈值消费但目标缺失/不明确或受保护时，不改损其他蛊。
- 本命蛊保护同时核对会话绑定指纹和 `PlayerVariables.BenMingGu` 镜像；计数和 DAMAGE 不进入规范化内容指纹；同内容副本一并受保护。
- 接受分支若不能确认效果已产生，不累计压力；按键包、普通 wrapper、冷却写入及被动 tick 不作为确认。
- 玩家压力、事务回执、目标物品变化保存在同一玩家 NBT；多人玩家文件与单人房主 `level.dat/Data/Player` 都要读回核验；失败时不重放损失。
- 未核实来源保持无惩罚；`AscensionReadiness.implementedCapabilities()` 保持空集，`GU_BACKLASH` readiness 不开放。
- 本轮只跑离线领域、NBT、源码及 hook 校验；不启动客户端、世界或服务器，不改版本、不打包、不提交。

---

### Task 1: 纯领域压力与阈值状态

**Files:**
- Create: `src/main/java/net/guzhenren/shengxian/core/GuBacklashState.java`
- Modify: `src/main/java/net/guzhenren/shengxian/core/GuActivationPolicy.java`
- Test: `src/test/java/net/guzhenren/shengxian/FrameworkTest.java`

**Interfaces:**
- `GuBacklashState(UUID sessionId, int pressure, int threshold, long sequence, String lastEventKey, Receipt lastReceipt, boolean quarantined)` 保存 session 绑定状态。
- `GuBacklashState.observe(UUID sessionId, Phase phase, int configuredThreshold, GuActivation activation, boolean targetConfirmed, boolean vitalProtected)` 返回 `Transition(state, Decision)`；`Decision` 为 `IGNORED_PHASE`, `IGNORED_UNCONFIRMED`, `DUPLICATE`, `PRESSURE_ONLY`, `LOSS_DUE`, `VITAL_PROTECTED`, `TARGET_UNAVAILABLE`, `QUARANTINED`。
- 新 session 初始化为 0 压力并冻结当前阈值；同 session 后续忽略配置变化；阈值到达时扣去一个阈值并递增序号。仅 `COMPLETED` 且来源符合策略的事件可累计。

- [x] **Step 1: 写失败回归**：在 FrameworkTest 增加仅 ABSORBING 累计、拒绝/未完成不累计、阈值恰好触发、压力余数保留、新 session 重置、阈值冻结、同事件幂等、受保护/目标不明只消费阈值、隔离后无后续损失的断言。
- [x] **Step 2: 运行 RED**：`pwsh -NoProfile -File .\build.ps1 -Action Test`；新增 API 未定义时应编译失败。
- [x] **Step 3: 实现最小纯 Java 状态转移和策略门禁**，不引入 Minecraft 类型，不改变 readiness。
- [x] **Step 4: 运行 GREEN**：重复 `pwsh -NoProfile -File .\build.ps1 -Action Test`，所有既有和新增 FrameworkTest 通过。

### Task 2: 玩家持久数据编码与存档回读验证

**Files:**
- Create: `src/main/java/net/guzhenren/shengxian/compat/SavedGuBacklash.java`
- Create: `tools/VerifyGuBacklashReceipt.java`
- Modify: `build.ps1`
- Test: `tools/VerifyGuBacklashReceipt.java`

**Interfaces:**
- NBT 键为 `PlayerPersisted/shengxian:gu_backlash`；编码 session UUID、pressure、冻结 threshold、sequence、quarantine、最近 event/loss receipt 和事务阶段。
- `SavedGuBacklash.read(CompoundTag)`、`record(CompoundTag, GuBacklashState, Transaction)`、`verify(Path, UUID, GuBacklashState, Transaction)`、`verifyHost(Path, UUID, GuBacklashState, Transaction)` 拒绝格式错误、玩家 UUID 不符、receipt 回退或不匹配，并保留其他 `PlayerPersisted` 项。
- verifier 必须在玩家文件/host NBT 内核对目标物品计数变化与回执位于同一 player compound；保存未完成、只写入事务意图或只出现部分文件时拒绝确认。

- [x] **Step 1: 写 NBT RED**：verifier 覆盖正常读写、不同 owner/session、错误阈值/序号、损失回执回退、未完成事务、错误目标槽、玩家文件与 host NBT、存档缺失/损坏。
- [x] **Step 2: 运行 RED**：`pwsh -NoProfile -File .\build.ps1 -Action NbtCheck`；缺少 codec/verifier 时应失败。
- [x] **Step 3: 编码状态和两阶段事务回执**：`PREPARED` 先于物品变化保存，`COMMITTED` 随变化保存；登录时遗留 `PREPARED` 或不一致来源进入 quarantine，不自动补做损失。
- [x] **Step 4: 将 verifier 加入 `Test-PlayerProjection`**，然后运行 `pwsh -NoProfile -File .\build.ps1 -Action NbtCheck`；期望所有 player projection 与 Gu 回执压缩 NBT 校验通过。

### Task 3: 精确目标解析、指纹保护和损失事务

**Files:**
- Create: `src/main/java/net/guzhenren/shengxian/server/GuBacklashService.java`
- Modify: `src/main/java/net/guzhenren/shengxian/server/GuActivationObserver.java`
- Modify: `src/main/java/net/guzhenren/shengxian/compat/GuzhenrenPlayerBridge.java`
- Modify: `src/main/java/net/guzhenren/shengxian/ShengxianConfig.java`
- Modify: `src/main/java/net/guzhenren/shengxian/server/AscensionServer.java`
- Test: `src/test/java/net/guzhenren/shengxian/FrameworkTest.java`

**Interfaces:**
- `ShengxianConfig.GU_BACKLASH_THRESHOLD` 默认 8、范围 1–64；`guBacklashThreshold()` 对未加载配置返回 8。
- `GuBacklashService.completed(ServerPlayer, UUID, GuActivation, ItemStack, TargetRef)` 只服务端调用，验证当前 first session 为 ABSORBING、精确存储槽和堆栈指纹、普通背包对象唯一性、会话本命蛊 binding 与上游镜像一致后，再按 Task 1 状态提交。
- `TargetRef.inventory()` 通过当前 ItemStack 对象身份在玩家 Inventory 中唯一定位；`TargetRef.killerMove(group, slot)` 只定位对应 `PlayerVariables.ShaZhao*_GuChong*` 槽。定位不到或重复时不得猜槽。
- 损失触发时使用 PREPARED 保存→精确减 1→COMMITTED 保存→player NBT 与必要的 host NBT 回读；任何异常设置内存 quarantine、保留诊断并停止后续处罚，不尝试另一个目标。
- `GuActivationObserver` 只有在新 ledger 记录首次出现时转发完成事件；同内容本命蛊副本不扣除。

- [x] **Step 1: 写领域 RED**：补充目标判定场景：背包唯一对象、对象缺失/重复、杀招组槽、指纹变化、本命蛊副本/镜像不一致、保存不确定不重试。
- [x] **Step 2: 运行 RED**：`pwsh -NoProfile -File .\build.ps1 -Action Test`，确认目标解析/状态迁移断言在实现前失败。
- [x] **Step 3: 实现配置、目标引用解析和服务端事务**；每个异常分支都只隔离，不找替代蛊。
- [x] **Step 4: 运行 GREEN**：运行 FrameworkTest；再运行 `pwsh -NoProfile -File .\build.ps1 -Action NbtCheck` 验证真实编译依赖和文件读回验证器。

### Task 4: 完成点钩子、精确 hook 审计和文档

**Files:**
- Modify: `src/main/java/net/guzhenren/shengxian/mixin/common/YueGuangGuProcedureMixin.java`
- Modify: `src/main/java/net/guzhenren/shengxian/mixin/common/BaiXiangSheGuProcedureMixin.java`
- Modify: `src/main/java/net/guzhenren/shengxian/mixin/common/AiBieChiProcedureMixin.java`
- Modify: `src/main/java/net/guzhenren/shengxian/mixin/common/DingGu2ProcedureMixin.java`
- Modify: `src/main/java/net/guzhenren/shengxian/mixin/common/SanZhaoAnJianCProcedureMixin.java`
- Modify: `src/main/java/net/guzhenren/shengxian/mixin/common/SanZhaoAnJianVProcedureMixin.java`
- Modify: `src/main/java/net/guzhenren/shengxian/mixin/common/SanZhaoAnJianXProcedureMixin.java`
- Modify: `src/main/java/net/guzhenren/shengxian/mixin/common/SanZhaoAnJianZProcedureMixin.java`
- Modify: `tools/VerifyGuActivationHooks.java`
- Modify: `docs/evidence/gu-activation-api.md`
- Modify: `docs/ascension-implementation-plan.md`
- Create: `docs/verification-gu-backlash.md`

**Interfaces:**
- 月光蛊仅在 `lambda$execute$1(Entity)` 的延迟弹道确实被服务端 `addFreshEntity` 接受后提交；白象蛇蛊仅在实际生成实体并进入其成功分支后提交；哀别持提交点必须晚于接受分支所有同步效果；定蛊二号必须确认目标方块实际成为铁炼鼎。
- C/V/X/Z 只提交方法返回时实际改变且 ItemStack 仍能以同一组槽定位的蛊；不能根据整段方法涉及过的字段集合处罚。
- Hook verifier 继续绑定内层 JAR SHA256 `b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc`，核对每个目标类/描述符/唯一结果调用或槽位变化注入/观察器签名，以及 Mixin 配置精确注册。

- [x] **Step 1: 扩展 verifier 的 RED 断言**：要求旧的“只在真元写入后观察”实现失败，并核对延迟结果点、召唤成功点、方块结果、四组槽位和物品位置来源。
- [x] **Step 2: 运行 hook RED**：`pwsh -NoProfile -File .\build.ps1 -Action SourceCheck`，预期旧注入位置被静态验证器拒绝。
- [x] **Step 3: 将每个来源移到确认完成点**，对不含可验证结果或目标定位的分支不提交反噬事件；不新增通用 wrapper 或未分类入口。
- [x] **Step 4: 更新证据、计划进度和验证边界**，明确审计覆盖仅为这 8 个已有目标，不表示全体本体入口覆盖；readiness 仍关闭。
- [x] **Step 5: 完整离线验证**：依次执行 `pwsh -NoProfile -File .\build.ps1 -Action Test`、`pwsh -NoProfile -File .\build.ps1 -Action NbtCheck`、`pwsh -NoProfile -File .\build.ps1 -Action SourceCheck`；三项均通过后记录准确测试数、编译类数和 NBT 检查数。

