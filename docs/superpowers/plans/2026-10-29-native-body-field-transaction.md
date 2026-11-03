# 本体字段事务与恢复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This project has no Git repository; execute inline in the current checkout and do not create commits.

**Goal:** 让碎窍 `zhenyuan` 扣费在玩家存档和单人 host 加载源中具有可恢复的一次性回执，并复核现有 `zhuanshu/jieduan/shiqiao` 专用事务不覆盖合法本体变化。

**Architecture:** 世界 schema 10 的 `BREAKING` 意图同时保存扣费前值与目标；玩家 `PlayerPersisted/shengxian:shatter_essence` 记录 PREPARED/COMMITTED/UNCERTAIN。提交前后核验 UUID 玩家文件，单人 owner 还核验 `level.dat/Data/Player`；恢复只在当前字段仍等于已记录原值时写目标，目标值只补确认，其他值隔离。现有 rank/failure 专用回执保留，不新增通用字段框架；没有上游原生字段的进度继续只读。

**Tech Stack:** Java 21、NeoForge 21.1.263、本地 Minecraft NBT、`FileSessionRepository`、`FrameworkTest`、压缩 NBT verifier。

## Global Constraints

- 事务身份绑定 `sessionId + phaseRevision + before + target`；一次碎窍目标不可替换。
- 只在 `zhenyuan` 当前值与持久化 PREPARED 的 `before` 精确相同或已等于 `target` 时恢复；任何第三值不扣、不补、不回滚。
- schema 9 与更早的 target-only 活动碎窍必须保留并隔离；无匹配玩家回执时不得自动扣费。
- COMMITTED 后，碎窍 server tick 与重登恢复均不得再次钳制 `zhenyuan`。
- 玩家文件和单人 owner 加载源各自核验；一侧缺失、格式错误或身份不符时不确认世界计时。
- `zhuanshu/jieduan` 与 `shiqiao` 继续使用现有专用回执；`kongqiao`、`renqi`、`shouyuan`、仙窍和仙元不存在经核验的写入适配，本阶段保持只读。
- `AscensionReadiness.implementedCapabilities()` 保持空集，不开放普通路线或失败后果。
- 只运行领域、压缩 NBT 和源码校验；不启动 Minecraft，不改真实存档，不升版本、不打包、不提交。

## Interfaces

- `AscensionProgress.withShatterEssenceIntent(long phaseRevision, double before, double target)` 保存新的同阶段世界意图；`shatterEssenceIntent(long phaseRevision)` 读取新版意图。旧 `shatter-zhenyuan-target:` key 仍可读，但没有 baseline。
- `AscensionService.beginShatter(..., double before, double target)` 与 `SessionStore.beginShatter(..., double before, double target)` 原子保存 BREAKING、固定意图和计时器。
- `ShatterEssenceTransaction.Receipt(UUID sessionId, long phaseRevision, Stage stage, Double before, double target, Double observed)` 纯领域类型保存玩家回执；`before`/`observed` 只在合法阶段存在。`Stage` 为 `PREPARED`, `COMMITTED`, `UNCERTAIN`。
- `ShatterEssenceTransaction.decide(Optional<Receipt> receipt, UUID sessionId, long phaseRevision, Double worldBefore, double target, double current)` 返回 `WRITE_PREPARED`, `APPLY_TARGET`, `COMMIT_ALREADY_APPLIED`, `ALREADY_COMMITTED`, `QUARANTINE` 之一。无 receipt 时只有 `current == worldBefore` 才能准备；PREPARED 时只有当前值等于 before 或 target 才能继续。
- `SavedShatterEssenceReceipt.record/read/verify/verifyHost` 管理玩家 NBT 回执；`verify` 对事务提交阶段检查精确 `zhenyuan` 值，`verifyState` 在后续正常玩法可能已改动真元时只核回执身份。

---

### Task 1: 世界碎窍意图与 schema 10 迁移

**Files:**
- Create: `src/main/java/net/guzhenren/shengxian/core/ShatterEssenceTransaction.java`
- Modify: `src/main/java/net/guzhenren/shengxian/core/AscensionProgress.java`
- Modify: `src/main/java/net/guzhenren/shengxian/core/AscensionService.java`
- Modify: `src/main/java/net/guzhenren/shengxian/core/SessionStore.java`
- Modify: `src/main/java/net/guzhenren/shengxian/core/FileSessionRepository.java`
- Test: `src/test/java/net/guzhenren/shengxian/FrameworkTest.java`

**Steps:**

- [x] 写回归：新意图保存 phaseRevision、before、target；拒绝非有限数、负数、target 不低于 before；旧 schema 9 只读 target、baseline 缺失；新 schema 10 可重启读回；未知未来 schema 不覆盖。
- [x] RED：`pwsh -NoProfile -File .\build.ps1 -Action Test`，新 API 缺失时按预期编译失败。
- [x] 添加 `ShatterEssenceTransaction` 纯模型与决策测试：无 receipt 先写 PREPARED；PREPARED + 当前值等于 before 得到 APPLY_TARGET；当前值等于 target 得到 COMMIT_ALREADY_APPLIED；COMMITTED 匹配得到 ALREADY_COMMITTED；旧 baseline 缺失、第三值、非法值、身份不符或 UNCERTAIN 得到 QUARANTINE。
- [x] GREEN：将所有新碎窍调用改为同时传 before/target；`FileSessionRepository` 新写 schema 10，schema 9 继续按旧字段读取并保留 target-only key；FrameworkTest 全通过。

### Task 2: 玩家 PREPARED/COMMITTED/UNCERTAIN 回执与压缩 NBT verifier

**Files:**
- Create: `src/main/java/net/guzhenren/shengxian/compat/SavedShatterEssenceReceipt.java`
- Create: `tools/VerifyShatterEssenceReceipt.java`
- Modify: `build.ps1`

**Steps:**

- [x] 先写 verifier：检查 unrelated `PlayerPersisted` 保留、session/phase/target/receipt、PREPARED/COMMITTED 单向迁移、UNCERTAIN 隔离、精确 `zhenyuan` 读回、玩家 UUID、单人 host `Data/Player`、坏档和两来源读回。
- [x] RED：`pwsh -NoProfile -File .\build.ps1 -Action NbtCheck`，缺失 codec 时按预期失败。
- [x] 实现 receipt NBT 键 `PlayerPersisted/shengxian:shatter_essence`；PREPARED 必须含 before/target 且未写后值，COMMITTED 必须含与 target 相等的确认值，UNCERTAIN 可表示 target-only 旧记录。身份或阶段不能回退/替换。
- [x] 实现即时 `verify`（核精确上游 `zhenyuan` 数值与 receipt）和恢复期 `verifyState`（核身份与 receipt，不把已提交后后来正常改变的 `zhenyuan` 误判为冲突）；单人 host 通过 `Data/Player` 使用相同规则。
- [x] 把 `VerifyShatterEssenceReceipt` 接入 `Test-PlayerProjection`；NbtCheck 中新 verifier 与现有 rank/failure/impact/Gu verifier 全部通过。

### Task 3: 本体桥接与碎窍生命周期接线

**Files:**
- Modify: `src/main/java/net/guzhenren/shengxian/compat/GuzhenrenPlayerBridge.java`
- Modify: `src/main/java/net/guzhenren/shengxian/server/AscensionLifecycleService.java`
- Test: `src/test/java/net/guzhenren/shengxian/FrameworkTest.java`
- Test: `tools/VerifyShatterEssenceReceipt.java`

**Steps:**

- [x] 写生命周期顺序回归：协调器 fake port 覆盖首扣前先保存 PREPARED、before 未写窗口、target 已写窗口、COMMITTED 后后续真元值不改写、旧 target-only 隔离和保存失败停步。
- [x] RED：`pwsh -NoProfile -File .\build.ps1 -Action Test`，协调器缺失时按预期编译失败。
- [x] 新增桥接事务：先保存并核验 PREPARED；仅 live `zhenyuan == before` 才写 target；保存并读回 COMMITTED；玩家文件及单人 host 均匹配后才返回成功。可安全标记时保存 UNCERTAIN；本体字段不回滚。
- [x] `shatter()` 在世界意图持久化后执行事务，未 COMMITTED 则不返回开始计时；`resumeShatterClock()` 先核对/完成事务再 rebase；`onServerTick()` 只要求已核实 COMMITTED，不重放目标写入。
- [x] 对 schema 9 target-only 且没有匹配 COMMITTED receipt 的 BREAKING，会话转 UNCERTAIN 并停止计时；没有安全双源写回时保留原记录，仍不推导 before 或自动补扣。
- [x] GREEN：FrameworkTest 和 NbtCheck 全通过。

### Task 4: 复核境界/失窍专用事务、文档与最终校验

**Files:**
- Inspect: `src/main/java/net/guzhenren/shengxian/compat/SavedRankProjection.java`
- Inspect: `src/main/java/net/guzhenren/shengxian/compat/SavedFailureReceipt.java`
- Inspect: `src/main/java/net/guzhenren/shengxian/compat/SavedTribulationImpactReceipt.java`
- Modify: `docs/ascension-implementation-plan.md`
- Modify: `docs/evidence/upstream-audit.md`
- Create: `docs/verification-native-body-transaction.md`

**Steps:**

- [x] 对照现有回归复核 `zhuanshu/jieduan` 与 `shiqiao` 的 receipt 顺序、玩家/host 文件核验、已确认后合法降转不被覆盖、未就绪失败门禁；已有专用回归覆盖，本阶段没有发现需改动的测试缺口。
- [x] 更新进度记录：schema 10、target-only 旧记录隔离、本阶段字段边界、readiness 未开放及未运行 Minecraft。
- [x] 运行 `pwsh -NoProfile -File .\build.ps1 -Action Test`，FrameworkTest 192 项通过、0 失败。
- [x] 运行 `pwsh -NoProfile -File .\build.ps1 -Action NbtCheck`，五类 receipt verifier 全通过。
- [x] 运行 `pwsh -NoProfile -File .\build.ps1 -Action SourceCheck`，Java 21 完整源码编译及静态检查通过。
- [x] 在 `docs/verification-native-body-transaction.md` 记录结果，并说明没有实机运行、没有打开真实存档、没有升版/打包/提交。
