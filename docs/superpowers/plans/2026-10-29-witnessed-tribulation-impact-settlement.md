# 具名灾劫与冲击失败结算 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 让服务器仅在明确选择的太白、黑楼兰、方正具名 profile 中推进有来源证明的外劫压力，并以独立、可恢复的一次性事务结算灾劫冲击导致的致命失败。

**Architecture:** 普通 production profile 默认 UNRESOLVED_ORDINARY，具名 profile 在会话首次纳气步进时冻结并持久化。外劫压力/序号与三气失衡风险分开；灾劫终态保存 profile、载体和冲击序号来源证明。TRIBULATION_IMPACT 走独立策略、玩家回执和服务端结算分支，不能进入三个 qi-overload 原因的白名单。所有新副作用仍受全路线 readiness 约束。

**Tech Stack:** Minecraft 1.21.1、NeoForge 21.1.263、Java 21、现有 FrameworkTest、FileSessionRepository schema 迁移、玩家 NBT 离线 verifier。

## Global Constraints

- 普通通用 profile 必须继续默认为 UNRESOLVED_ORDINARY，并且不生成灾劫。
- 只有 TAI_BAI_WITNESSED、BLACK_LOU_LAN_WITNESSED、FANG_ZHENG_WITNESSED 可通过服务器配置选择；LEGACY_TEST_FIXTURE 不允许进入生产配置。
- TRIBULATION_IMPACT 不得加入 hasCanonicalFatalQiCause 或三气过盛 DamageSource 映射。
- 来源缺失、旧数据含糊、写盘失败、存档冲突或死亡结果不明时必须隔离，不能自动再次致死。
- AscensionReadiness.implementedCapabilities() 继续返回空集；本计划不得开放任何升仙入口。
- 不启动 Minecraft 客户端、服务器、集成世界或 DSH；不修改真实存档。
- 项目没有 Git 仓库；完成后更新计划/进度文档，不执行或声称 git commit。

---

### Task 1: 分离外劫压力与三气风险

**Files:**
- Modify: src/main/java/net/guzhenren/shengxian/core/AscensionProgress.java
- Modify: src/main/java/net/guzhenren/shengxian/core/TribulationParameters.java
- Test: src/test/java/net/guzhenren/shengxian/FrameworkTest.java

**Interfaces:**
- Profile gains NOT_FROZEN as an internal sentinel; production configuration cannot select it.
- AscensionProgress gains frozen tribulation profile, bounded tribulation pressure, monotonic impact sequence, and optional TribulationImpactFailure(profile, carrierType, carrierId, impactSequence).
- TribulationImpactFailure accepts only the three witnessed profiles, a non-NONE carrier type, a valid bounded carrier ID, and a positive impact sequence.

- [x] **Step 1: Write failing tests**
  Add FrameworkTest cases proving a carrier impact increases only tribulation pressure, leaves the Qi Risk triple unchanged, advances one stable impact sequence per impact, and creates a terminal context that names profile/type/carrier/sequence.

- [x] **Step 2: Run the core tests and confirm RED**
  Run: .\build.ps1 -Action Test
  Expected: new behavioral assertions fail because impact pressure and a persisted impact context do not exist; existing 176 checks remain green.

- [x] **Step 3: Implement the bounded progress model**
  Add validated fields and transition methods. Keep old constructor signatures as delegating overloads with UNRESOLVED_ORDINARY, zero pressure, zero sequence, and no impact context so existing call sites remain source compatible.

- [x] **Step 4: Run the core tests and confirm GREEN**
  Run: .\build.ps1 -Action Test
  Expected: all existing and new FrameworkTest cases pass; malformed profile/context and backwards impact sequence are rejected.

### Task 2: Freeze named profiles and migrate session schema

**Files:**
- Modify: src/main/java/net/guzhenren/shengxian/core/FileSessionRepository.java
- Modify: src/main/java/net/guzhenren/shengxian/core/AscensionProgress.java
- Test: src/test/java/net/guzhenren/shengxian/FrameworkTest.java

**Interfaces:**
- Current repository schema advances from 8 to 9.
- Schema 9 stores profile, external pressure, impact sequence, and optional terminal impact source fields.
- Schema 8 or older active ABSORBING sessions migrate with frozen profile UNRESOLVED_ORDINARY; prior qi, active carriers, anchors and applied effects remain intact.

- [x] **Step 1: Write failing schema tests**
  Add a schema 8 ABSORBING fixture with an active anchored carrier and verify migration keeps the carrier but freezes the profile to UNRESOLVED_ORDINARY. Add a schema 9 roundtrip test for pressure, sequence, profile, and terminal source context.

- [x] **Step 2: Run tests and confirm RED**
  Run: .\build.ps1 -Action Test
  Expected: the schema 8 fixture does not yet expose a frozen profile and schema 9 roundtrip rejects or loses the new fields.

- [x] **Step 3: Implement schema 9 read/write and conservative migration**
  Extend the repository reader/writer. Reject malformed enum, nonfinite/negative pressure, invalid sequence, incomplete source context, and mismatched failure context without overwriting the original session file.

- [x] **Step 4: Run tests and confirm GREEN**
  Run: .\build.ps1 -Action Test
  Expected: schema 8 retains existing carrier state, schema 9 roundtrips all impact fields, and malformed fixtures fail closed.

### Task 3: Make named profiles an explicit server setting

**Files:**
- Modify: src/main/java/net/guzhenren/shengxian/ShengxianConfig.java
- Modify: src/main/java/net/guzhenren/shengxian/core/TribulationParameters.java
- Modify: src/main/java/net/guzhenren/shengxian/core/TribulationEngine.java
- Modify: src/main/java/net/guzhenren/shengxian/core/AscensionService.java
- Modify: src/main/java/net/guzhenren/shengxian/server/AscensionControlService.java
- Test: src/test/java/net/guzhenren/shengxian/FrameworkTest.java

**Interfaces:**
- ShengxianConfig.tribulationProfile(): TribulationParameters.Profile
- TribulationParameters.forProfile(Profile): validated parameters for one profile
- TribulationEngine.startIfDue freezes an unfrozen session profile once; subsequent ticks use the stored profile, not a changed config value.
- TribulationEngine.Result carries the impact sequence and optional fatal TribulationImpactFailure context needed by the caller.

- [x] **Step 1: Write failing profile-selection tests**
  Verify default configuration resolves to UNRESOLVED_ORDINARY, each named profile starts only its witnessed carriers, and a frozen profile ignores a later different profile parameter.

- [x] **Step 2: Run tests and confirm RED**
  Run: .\build.ps1 -Action Test
  Expected: config profile selection and session freeze assertions fail while current named domain factory tests remain green.

- [x] **Step 3: Implement profile selection and freeze**
  Add a constrained server config value; map only the four production choices. Feed the selected profile into simulation. Preserve UNRESOLVED_ORDINARY as the default and use the persisted session profile after first freeze.

- [x] **Step 4: Run tests and confirm GREEN**
  Run: .\build.ps1 -Action Test
  Expected: default remains inert, explicit profiles behave independently, and session restart cannot change an already frozen profile.

### Task 4: Persist isolated impact pressure and terminal provenance

**Files:**
- Modify: src/main/java/net/guzhenren/shengxian/core/TribulationEngine.java
- Modify: src/main/java/net/guzhenren/shengxian/core/AscensionService.java
- Modify: src/main/java/net/guzhenren/shengxian/core/SessionStore.java
- Modify: src/main/java/net/guzhenren/shengxian/core/AscensionReadiness.java
- Modify: src/main/java/net/guzhenren/shengxian/core/AscensionProgress.java
- Test: src/test/java/net/guzhenren/shengxian/FrameworkTest.java

**Interfaces:**
- TribulationEngine.tick advances external pressure and the sequence without changing Qi Risk.
- A fatal carrier impact commits exactly Set.of(TRIBULATION_IMPACT) plus the profile/type/carrier/sequence context in the same repository save as FAILED.
- AscensionReadiness.hasVerifiedTribulationImpactCause(causes, context) accepts only one cause, TRIBULATION_IMPACT, and a structurally valid named-profile context; it does not alter hasCanonicalFatalQiCause.

- [x] **Step 1: Write failing terminal-impact regressions**
  Verify cumulative external pressure reaches its own game-mapped limit, does not mutate heaven/earth/human qi risk, and commits one TRIBULATION_IMPACT with source context. Verify a qi fatal step still commits only its single qi cause and no impact context.

- [x] **Step 2: Run tests and confirm RED**
  Run: .\build.ps1 -Action Test
  Expected: existing impact tick changes Qi Risk and the terminal impact lacks durable source provenance.

- [x] **Step 3: Implement the isolated impact path**
  Keep external pressure bounded separately from Qi Risk. Generate the context from the active frozen profile and the impacting carrier before clearing active carriers. Commit context and cause atomically with FAILED.

- [x] **Step 4: Run tests and confirm GREEN**
  Run: .\build.ps1 -Action Test
  Expected: mixed causes, unresolved profiles, missing carrier IDs, and duplicate terminal impact context are rejected; existing qi failure tests remain unchanged.

### Task 5: Add independent impact receipts and settlement transitions

**Files:**
- Create: src/main/java/net/guzhenren/shengxian/compat/SavedTribulationImpactReceipt.java
- Create: src/main/java/net/guzhenren/shengxian/server/TribulationImpactSettlementService.java
- Modify: src/main/java/net/guzhenren/shengxian/core/AscensionService.java
- Modify: src/main/java/net/guzhenren/shengxian/core/SessionStore.java
- Modify: src/main/java/net/guzhenren/shengxian/core/FailureSettlementPolicy.java
- Modify: src/main/java/net/guzhenren/shengxian/server/FailureSettlementService.java
- Test: src/test/java/net/guzhenren/shengxian/FrameworkTest.java

**Interfaces:**
- SavedTribulationImpactReceipt.Receipt(operationId, profile, carrierType, carrierId, impactSequence, stage)
- SavedTribulationImpactReceipt stages are STARTED and RESPAWN_RECEIPT; it uses its own PlayerPersisted key.
- SessionStore adds startTribulationImpactFailure, markTribulationImpactUncertain, recordTribulationImpactRespawnReceipt, and acknowledgeTribulationImpact; each operation requires the exact impact context and stable session/phase operation ID.
- FailureSettlementService dispatches a verified TRIBULATION_IMPACT only to TribulationImpactSettlementService; the qi branch continues to accept only the three fatal qi causes.

- [x] **Step 1: Write policy and repository RED tests**
  Cover one valid witnessed impact, missing/legacy context, wrong profile, wrong carrier/sequence, mixed causes, duplicate STARTED, only-ack recovery from matching RESPAWN_RECEIPT, and UNCERTAIN refusing another lethal attempt.

- [x] **Step 2: Run tests and confirm RED**
  Run: .\build.ps1 -Action Test
  Expected: impact context and cause are currently rejected by the qi-only settlement API; the new independent policy tests fail for missing API/behavior.

- [x] **Step 3: Implement separate persisted transition guards**
  Add impact-specific SessionStore/AscensionService methods that validate singleton TRIBULATION_IMPACT and source context before any state transition. Do not broaden the existing qi-only requireCanonicalFailure guard.

- [x] **Step 4: Implement the separate settlement policy**
  Persist and verify the impact receipt. Reconcile only a matching operation/context. Apply the impact damage source once after the player STARTED receipt and world STARTED are verified. On logout/server stop, uncertain event, missing receipt, or save conflict, quarantine as UNCERTAIN.

- [x] **Step 5: Run tests and confirm GREEN**
  Run: .\build.ps1 -Action Test
  Expected: replay never applies a second lethal effect; qi handler still rejects TRIBULATION_IMPACT; unknown old impact records never auto-kill.

### Task 6: Connect player persistence verification and lifecycle dispatch

**Files:**
- Modify: src/main/java/net/guzhenren/shengxian/compat/GuzhenrenPlayerBridge.java
- Modify: src/main/java/net/guzhenren/shengxian/server/FailureSettlementService.java
- Modify: src/main/java/net/guzhenren/shengxian/server/AscensionControlService.java
- Create: tools/VerifyTribulationImpactReceipt.java
- Modify: build.ps1
- Modify: src/test/java/net/guzhenren/shengxian/FrameworkTest.java

**Interfaces:**
- GuzhenrenPlayerBridge.persistTribulationImpactReceipt(ServerPlayer, Receipt) saves, reads back and verifies the UUID player file and singleplayer-host data source when applicable.
- GuzhenrenPlayerBridge.tribulationImpactDamageSource(ServerPlayer) is separate from failureDamageSource and returns the explicit impact damage type.
- Death, Clone, Respawn, login and server-stop correlation records one impact operation; an ambiguous correlation becomes UNCERTAIN.

- [x] **Step 1: Write failing NBT verifier and lifecycle policy tests**
  Verify the independent receipt preserves unrelated PlayerPersisted data, rejects malformed or mismatched operation/source, and roundtrips STARTED/RESPAWN_RECEIPT. Verify the NBT verifier refuses wrong UUID, wrong impact identity, missing source and host mismatch.

- [x] **Step 2: Run the focused verifier and confirm RED**
  Run: .\build.ps1 -Action NbtCheck
  Expected: build.ps1 does not yet invoke VerifyTribulationImpactReceipt and the new receipt verifier target is absent.

- [x] **Step 3: Implement upstream persistence and dispatch**
  Add bridge persistence/source methods and route matching lifecycle events to the impact receipt while leaving the qi receipt flow unchanged. Do not acknowledge on LivingDeathEvent alone; require verified Clone and Respawn receipt.

- [x] **Step 4: Run NBT and core checks**
  Run: .\build.ps1 -Action Test
  Run: .\build.ps1 -Action NbtCheck
  Expected: FrameworkTest passes; impact receipt compressed-NBT checks pass for UUID player data and singleplayer-host source.

### Task 7: Verify and document the implementation boundary

**Files:**
- Modify: docs/evidence/tribulation-parameters.md
- Modify: docs/evidence/tribulation-matrix.md
- Modify: docs/evidence/failure-settlement.md
- Modify: docs/ascension-implementation-plan.md
- Modify: .superpowers/sdd/2026-10-27-ordinary-ascension-completion/progress.md
- Create: docs/verification-witnessed-tribulation-impact.md

- [x] **Step 1: Run complete offline verification**
  Run: .\build.ps1 -Action Test
  Run: .\build.ps1 -Action SourceCheck
  Expected: all core regressions pass; full Java 21 NeoForge source compile, 8 audited Gu hooks and rank/failure/impact NBT verifiers pass.

- [x] **Step 2: Recheck the readiness gate**
  Verify AscensionReadiness.implementedCapabilities() still returns Set.of() and no new command bypasses readiness.

- [x] **Step 3: Record exact verification evidence**
  Document profile default, schema migration behavior, independent impact receipt, test totals, SourceCheck output and that no game/server was started. Do not claim real death/respawn/multiplayer or crash runtime acceptance.

- [x] **Step 4: Update this plan and SDD ledger**
  Mark only offline-complete steps checked. Leave isolated death/respawn/multiplayer/crash acceptance unchecked. Do not version-bump, package, or claim a Git commit.
