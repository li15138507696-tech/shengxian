# 升仙失败结算持久回执 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans in this session. Execute one task at a time; each task includes its test-first cycle. The project has no Git repository, so do not create commits.

**Goal:** 将已提交的三气致命失败结算改为一次尝试、玩家持久回执、世界会话确认和不确定状态隔离，并继续保持普通升仙 readiness 门禁关闭。

**Architecture:** 在既有 FAILED 会话的 appliedEffects 幂等账本中表达 PREPARED、STARTED、UNCERTAIN、RESPAWN_RECEIPT 和 ACKNOWLEDGED；稳定操作 ID 从 session UUID 与失败 phaseRevision 派生。玩家侧回执保存在 NeoForgeData/PlayerPersisted。服务器仅在 PREPARED 时运行一次失败结算；死亡克隆必须由当前进程中的操作关联确认，PlayerRespawnEvent 保存并核对实际玩家 NBT 后才确认世界记录。无法证明结果时持久隔离，不自动重试。

**Tech Stack:** Minecraft 1.21.1、NeoForge 21.1.263、Java 21、现有 FrameworkTest、现有 VerifyRankPersistence NBT 检查模式、现有协议回环。

## Global Constraints

- 三种唯一原因仅为 HUMAN_OVERLOAD、HEAVEN_OVERLOAD、EARTH_OVERLOAD；TRIBULATION_IMPACT、UNCLASSIFIED、空集和混合原因拒绝。
- PlayerList.saveAll() 的正常返回不能单独视为玩家数据已持久化。
- LivingDeathEvent 是可取消前置事件，不能标记完成；仅死亡 PlayerEvent.Clone 与当前操作关联匹配时产生回执。
- Clone 回调中不能调用 saveAll() 证明克隆实体已保存；等待 PlayerRespawnEvent。
- STARTED、旧版缺少操作 ID、来源冲突、写盘或事件结果未知时禁止自动再次致死。
- 玩家完成回执必须先持久化并核对，世界 ack 才可提交；ack 失败只能重试 ack。
- implementedCapabilities() 继续返回空集，CANONICAL_FAILURE 与普通路线保持关闭。
- 不启动 Minecraft、客户端、集成世界、专用服务器或 DSH。
- 保留 0.3.16 及更早产物；原计划目标 0.3.17。最终审阅发现死亡事件取消状态必须在整个同步 hurt 分发返回后读取，因此最终产物提升为 0.3.18；0.3.17 中间产物也保留，不作为当前版本。

---

## 文件职责与接口

- Modify: src/main/java/net/guzhenren/shengxian/core/AscensionProgress.java
  - 管理失败结算标记、状态优先级、失败原因不变量及旧记录缺失标记时的保守隔离。
- Modify: src/main/java/net/guzhenren/shengxian/core/AscensionService.java
  - 在提交 FAILED 与原因时原子加入 PREPARED；提供限定的 STARTED、UNCERTAIN、RESPAWN_RECEIPT、ACKNOWLEDGED 单阶段写入。
- Modify: src/main/java/net/guzhenren/shengxian/core/SessionStore.java
  - 对服务方法提供同步持久 API，更新 owner 缓存。
- Modify: src/main/java/net/guzhenren/shengxian/core/FileSessionRepository.java
  - 仅允许 FAILED 会话中的合法单步 settlement 变化；既有无 settlement 标记的失败记录按 UNCERTAIN 处理；无需改 schema 8。
- Create: src/main/java/net/guzhenren/shengxian/compat/SavedFailureReceipt.java
  - 编码/解码 NeoForgeData/PlayerPersisted 的单操作收据，写入与校验在线玩家、UUID 文件及单人房主加载源。
- Create: src/main/java/net/guzhenren/shengxian/server/FailureSettlementService.java
  - 登录恢复、一次伤害调用、LivingDeathEvent 操作关联、PlayerEvent.Clone 死亡回执与 PlayerRespawnEvent 核验/ack。
- Modify: src/main/java/net/guzhenren/shengxian/server/AscensionServer.java and ShengxianMod.java
  - 将登录/重生/克隆/死亡事件接入统一 FailureSettlementService，移除会重复致死的旧 reconcile 路径。
- Modify: src/main/java/net/guzhenren/shengxian/compat/GuzhenrenPlayerBridge.java
  - 将直接副作用拆成写 shiqiao 和执行一次伤害调用；不得在 bridge 内 ack。
- Modify: src/main/java/net/guzhenren/shengxian/command/ShengxianCommands.java
  - 状态输出包含 settlement state 与 operation ID；不添加自动重试或管理员清除命令。
- Modify: src/test/java/net/guzhenren/shengxian/FrameworkTest.java
  - 状态机、旧数据迁移、合法与非法写入、重复/乱序事件、UNCERTAIN 不重试。
- Create: tools/VerifyFailureReceipt.java
  - 用本地 Minecraft NBT 类离线生成、保存、读取并校验 player receipt。
- Modify: build.ps1
  - 在 Test/Build 的真实 NBT 辅助检查阶段调用 VerifyFailureReceipt。
- Modify: README.md, docs/ascension-implementation-plan.md, docs/evidence/README.md, .superpowers/sdd/2026-10-27-ordinary-ascension-completion/progress.md
  - 记录最终版本离线验证、能力门禁与未做服务器验收边界。
- Create: docs/verification-0.3.18.md
  - 记录新的离线验证和产物指纹。

---

### Task 1: 纯 Java 失败结算状态机

**Files:** AscensionProgress.java, FrameworkTest.java.

- [x] Add a regression for a newly committed unique qi failure: it is PREPARED; no cause is NONE; old pending failure without a PREPARED marker is UNCERTAIN; ACKNOWLEDGED outranks all other markers.
- [x] Run the focused FrameworkTest suite and confirm those new checks fail on the old implementation.
- [x] Add enum FailureSettlementState { NONE, PREPARED, STARTED, RESPAWN_RECEIPT, UNCERTAIN, ACKNOWLEDGED } and methods:
  - failureSettlementState(): ACKNOWLEDGED > RESPAWN_RECEIPT > UNCERTAIN > STARTED > PREPARED; causes absent => NONE; causes present without any recognized settlement marker => UNCERTAIN.
  - withFailureStarted(): only PREPARED may transition to STARTED.
  - withFailureUncertain(): PREPARED or STARTED may transition to UNCERTAIN; it cannot change ACKNOWLEDGED.
  - withFailureRespawnReceipt(): STARTED or UNCERTAIN may receive a verified receipt; operationId and the unique cause must match.
  - withFailureAcknowledged(): require RESPAWN_RECEIPT or be idempotent if already ACKNOWLEDGED.
- [x] Update withFailurePending(causes) to atomically append cause key(s) and PREPARED for new terminal failures. Preserve old records with missing status marker as UNCERTAIN.
- [x] Add operation ID API:
  
      public static UUID failureOperationId(UUID sessionId, long phaseRevision) {
          if (sessionId == null || phaseRevision < 0) throw new IllegalArgumentException();
          return UUID.nameUUIDFromBytes(
              (sessionId + ":failure:" + phaseRevision).getBytes(StandardCharsets.UTF_8));
      }
  
  Put the method in a focused pure Java FailureSettlementIdentity class if adding it to AscensionProgress would mix concerns.
- [x] Re-run FrameworkTest; expect all tests plus new state/identity cases to pass.
- [x] Refactor duplicated marker parsing only after green; retain all behavior tests.

### Task 2: FAILED 会话持久化迁移与事务校验

**Files:** AscensionService.java, SessionStore.java, FileSessionRepository.java, FrameworkTest.java.

- [x] Add failing repository tests for PREPARED→STARTED, STARTED→UNCERTAIN, UNCERTAIN→RESPAWN_RECEIPT→ACKNOWLEDGED; reject STARTED→ACKNOWLEDGED, PREPARED→ACKNOWLEDGED, state skipping, duplicate actions, altered cause, and stale revision.
- [x] Run tests first and verify the failure is caused by repository rejection/absent method, not compilation mistakes.
- [x] Implement these SessionStore operations with exact signatures:
  
      AscensionRecord startFailure(ApertureKey key, long expectedStateRevision,
                                   long sequence, long serverTick);
      AscensionRecord markFailureUncertain(ApertureKey key, long expectedStateRevision,
                                           long sequence, long serverTick);
      AscensionRecord recordFailureRespawnReceipt(ApertureKey key, long expectedStateRevision,
                                                  long sequence, long serverTick, UUID operationId);
      AscensionRecord acknowledgeFailure(ApertureKey key, long expectedStateRevision,
                                         long sequence, long serverTick, UUID operationId);
  
  Each call performs one same-phase FAILED state update, increments stateRevision once, preserves phaseRevision, failure cause, aperture and all unrelated progress.
- [x] Modify FileSessionRepository.validateUpdate to permit only those legal monotonic effect additions on a FAILED session; ack must include the receipt marker for the deterministic operation ID. Unknown status, skipped steps, marker deletion or cause mutation throws IOException.
- [x] Keep repository schema at 8: status is represented by bounded existing appliedEffects; old pending failure with only cause keys derives UNCERTAIN and is never replayed.
- [x] Verify file repository restart between each transition; duplicate acknowledged calls return the same record without another write.
- [x] Run FrameworkTest and confirm all new repository transition cases pass.

### Task 3: 玩家失败收据与实际 NBT 核对

**Files:** SavedFailureReceipt.java, VerifyFailureReceipt.java, build.ps1.

- [x] Add failing NBT verifier cases: matching UUID/op/cause/stage succeeds; wrong owner/op/cause, missing NeoForgeData/PlayerPersisted, wrong stage, malformed NBT and mismatched shiqiao fail.
- [x] Run the NBT verifier through the same local runtime classpath used by VerifyRankPersistence; all corrupt identity, receipt, stage, flag, and host-source cases are rejected.
- [x] Implement PlayerReceipt { UUID operationId; FailureCause cause; ReceiptStage stage } with stages STARTED and RESPAWN_RECEIPT, stored under NeoForgeData/PlayerPersisted with namespaced key shengxian:failure.
- [x] Add methods to read/write the live player persistent CompoundTag and to verify:
  - UUID in the actual player file;
  - operation ID, unique cause, receipt stage, and shiqiao=true;
  - NeoForgeData/PlayerPersisted path;
  - singleplayer host's level.dat/Data/Player load source as well as UUID.dat where applicable.
- [x] After public saveAll(), read and validate the file; force the player file channel before reporting verified. Any exception, absent file or mismatch returns IOException and forbids damage/ack.
- [x] Add VerifyFailureReceipt.java to build.ps1 beside VerifyRankPersistence for Build and the explicit NBT verification action.
- [x] Re-run the NBT verifier; expect all valid/corrupt identity and transition cases to pass.

### Task 4: 一次尝试与 Clone/Respawn 恢复接线

**Files:** FailureSettlementService.java, AscensionServer.java, ShengxianMod.java, GuzhenrenPlayerBridge.java, FrameworkTest.java, SourceCheck.

- [x] Add and run a pure policy test: PREPARED with no player receipt may prepare once; STARTED without RESPawn receipt becomes UNCERTAIN; matching RESPAWN_RECEIPT only acks; UNCERTAIN never returns an automatic damage action.
- [x] Implement a per-server transient map keyed by owner UUID and operation ID. It exists only to correlate the synchronous cause-specific hurt call with LivingDeathEvent and is cleared on completion, logout, server stop, or uncertain transition.
- [x] Before attempting damage: commit PREPARED; write player STARTED and shiqiao=true; save and verify actual NBT; commit world STARTED. If any write/verification fails, do not call hurt/kill.
- [x] Execute one synchronous failure settlement sequence for the uniquely allowed cause. Do not ack in GuzhenrenPlayerBridge or immediately after hurt returns.
- [x] Register LivingDeathEvent only to mark the active in-memory attempt observation. If hurt/kill returns and the player is alive, event was canceled, or any error occurs, persist UNCERTAIN and never auto-retry.
- [x] Register PlayerEvent.Clone. Only when wasDeath, copied STARTED receipt, operation ID, unique cause and current in-memory death observation match, set the new player's receipt to RESPAWN_RECEIPT. Do not call saveAll from Clone.
- [x] In PlayerRespawnEvent, after the new player is in PlayerList, save, parse and verify the actual loaded NBT source. Only a matching durable receipt may call recordFailureRespawnReceipt and then acknowledgeFailure.
- [x] On login/restart: RESPAWN_RECEIPT verifies and retries ack only; matching STARTED without completion becomes UNCERTAIN; old markerless pending becomes UNCERTAIN; mismatches remain preserved and logged.
- [x] On logout/server stop, clear any transient active operation and persist UNCERTAIN if an attempt remains unresolved.
- [x] Extend FrameworkTest for lifecycle decision policy and compile SourceCheck to verify event signatures and server-only type placement. No live events are claimed validated.

### Task 5: 诊断、门禁和版本交付

**Files:** ShengxianCommands.java, AscensionReadiness.java, README.md, docs/ascension-implementation-plan.md, docs/evidence/README.md, SDD progress, docs/verification-0.3.18.md, build.gradle, mods.toml.

- [x] Add and run the pure diagnostic formatter test in FrameworkTest.
- [x] Add failure settlement state and stable operation ID to the existing owner status output. Do not add commands that retry, ack, or erase UNCERTAIN.
- [x] Keep AscensionReadiness.implementedCapabilities() empty and repeat the bridge guard so no live failure path becomes reachable accidentally.
- [x] Update project version to 0.3.18 and current documentation; retain all existing jars unchanged.
- [x] Add docs/verification-0.3.18.md with exact Test, SourceCheck, NBT, Verify and protocol outputs, exact JAR size/hash, Veil hash, and explicit no-server limit.
- [x] Run:
  
      pwsh -NoProfile -File .\build.ps1 -Action Test
      pwsh -NoProfile -File .\build.ps1 -Action SourceCheck
      pwsh -NoProfile -File .\build.ps1 -Action Build -ModVersion 0.3.18
      pwsh -NoProfile -File .\build.ps1 -Action Verify -ModVersion 0.3.18
      pwsh -NoProfile -File .\tools\verify-protocol.ps1
  
  Expected: all exit 0; JAR Verify reports 135+ common-side classes with no client renderer refs, four exact upstream Gu targets, embedded Veil 4.5.2 unchanged; protocol reports 8 phases, 23 outcomes, 22 NBT checks.
- [x] Do not launch Minecraft, DSH, an integrated world, or a server. Mark crash/rejoin/Clone/Respawn runtime acceptance as pending and keep route gated.

## Plan self-review

- Covers every design requirement: stable ID, persisted world states, PlayerPersisted receipt, death attempt correlation, Clone/Respawn ordering, loaded-file validation, uncertain quarantine, migration, diagnostics, and closed route gate.
- Old markerless pending failures become UNCERTAIN; this blocks the previous login/respawn replay path.
- Both death/receipt and world ack order are explicit. A receipt save failure cannot acknowledge; an ack failure never repeats damage.
- No runtime success claim is permitted because the user's no-server constraint remains.
