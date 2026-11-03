# SDD ledger — plan: docs/superpowers/plans/2026-10-27-ordinary-ascension-completion.md

## Setup rulings
- Scope: ordinary five-to-six route only; special variants remain closed wherever `canon-rules.md` lists an unresolved GAP.
- Existing evidence docs are binding; no new canon rules will be invented. The upstream 10.20 JAR is user supplied and can be inspected locally.
- Project has no Git repository. User authorized current-session implementation; use in-place edits and do not claim a commit.
- Testing: the user explicitly asked for tests earlier and wants work continued until complete; run the existing Test/Compile/Verify checks and add focused tests as needed.
- Previous 0.2.6 visual-transition work is complete and remains separate; do not alter 0.2.5/0.2.6 artifacts or the user's installed world.

## Interface preflight
- Formation commits `RANK_SIX_SYNC_RECOVERY_EFFECT` into the session before the base `zhuanshu/jieduan` projection. Current code has an admin sync path but no automatic player-login replay or ack/clear step. Task 1 owns this pending boundary and Task 5 consumes its confirmed semantics.
- Existing `BenMingGuStore` records fingerprints/consumed flags but not full item escrow. Task 2 must resolve before Task 5 enables `offerVitalGu`.
- `TribulationEngine` stores sound-chime/beast state but there are no Minecraft carriers. Task 4 owns runtime carrier linkage before Task 5 can advertise the full ordinary route.

## Task results
- Task 1: implemented. Rank projection now has a durable acknowledgement marker, safe 5→6 / already-6 recovery policy, login replay and shared admin/formation path. At the earlier checkpoint, core tests passed 108; the current 0.3.1 suite passes 111, with artifact verification in docs/verification-0.3.1.md.
- Task 2: partial. Full `ItemStack` escrow, crash reconciliation, an `EMBEDDED` formation state and compatibility with already-started legacy returns are implemented. Upgrade-to-Immortal-Gu mapping and real player inventory playthrough remain absent because no verified universal conversion table exists.
- Task 3: partial. Three-qi simulation, bounded server intents, persistent insight and active-online ticking exist; the route remains gated and there is no client playthrough or upstream environment sampling.
- Task 4: partial. Three canon-backed qi failure causes are allowlisted, but an audit found the lethal player effect precedes durable acknowledgement and may replay after crash; CANONICAL_FAILURE remains gated. Real Minecraft calamity carriers, combat/guardian interaction, chunk lifecycle handling and client acceptance remain incomplete.
- Task 5: partial. Formation, mod-owned aperture/essence records, plot reservations and rank-six replay/ack are implemented. The upstream mod has no aperture or essence fields; no unsupported projection is fabricated. Actual player commit/restart acceptance remains outstanding.
- Task 6: partial. Version 0.3.1 passed Test (111), SourceCheck, Build/Verify, and isolated empty-world server startup/clean stop. There is no full real-client or multiplayer ascension run.

## Execution constraints
- Keep the readiness gate closed until the Task 5 capability audit is complete.
- No live user save mutation. Use separate test worlds/fixtures for actual game validation.
- 2026-10-27 regressions: found `AscensionLifecycleService.onServerTick` advanced legacy BREAKING/BROKEN records while global readiness was closed; added a policy test first and implemented `mayAdvanceSession`. Then added a failing test for preserving the original Gu after formation without inventing return/upgrade; implemented `EMBEDDED` and retained already-started legacy return transactions. Full core suite now passes (108/0).
- 0.3.0 artifact and isolation-server findings remain in `docs/verification-0.3.0.md`; the Gu embed correction is packaged and verified in 0.3.1. Keep the overall objective active: the ordinary route has not been opened or accepted.

## 2026-10-27 continuation

- Current source target is 0.3.1. Re-ran Test: 111 passed / 0 failed; SourceCheck passed on Minecraft 1.21.1 / NeoForge 21.1.263 / 110 local jars after the status-command edit.
- Split disaster parameters into explicit Taibai-witnessed, Hei-Loulan-witnessed, unresolved ordinary and legacy-test profiles. The standard profile is inert; no named example is generalized into production.
- Added multi-carrier schema 7 persistence, with schema 6 single-carrier migration. Updated runtime close/form checks and diagnostics to inspect all active carriers.
- Six-tab client screen and server-owned snapshot exist; client acceptance is still open.
- README and parameter evidence state the unresolved default; 0.3.1 build and isolated server verification are complete. Artifact 0.3.0 remains unchanged. Artifact 0.3.1 was built and passed Verify; isolated empty-world server startup and clean stop are recorded in docs/verification-0.3.1.md.
- Outstanding completion blockers remain real world carrier entities/guardian combat/chunk semantics; a verified Gu-use backlash call path and protection/loss rules; a canon-supported ordinary calamity profile; and isolated real-client/multiplayer recovery acceptance. Keep all route capabilities closed.
## 2026-10-27 safety and carrier continuation

- Added capability GU_BACKLASH; readiness remains closed. reconcileFailure now quarantines historical/unverified failures while closed, retaining the record and leaving upstream shiqiao/death untouched.
- CANON-001 review removed BREAKING -> PREPARING, disabled the cancel UI, refused second attempts, and changed the lifecycle message. Login now idempotently reapplies the already committed absolute essence target before rebasing the online timer.
- Added a red/green regression for medium aperture time-ratio coverage: current game mapping is 5..16 so the verified Fang Yuan 1:16 medium example is representable; this remains a game mapping.
- Added CarrierAnchor to active tribulations and bumped FileSessionRepository to schema 8. Schema 7 multi-carrier records migrate without losing carriers but with unplaced anchors.
- AscensionService.stepSimulation persists deterministic UUIDs and world anchors before any entity spawn. SessionStore exposes carrier-ID strike. A server-side invisible ArmorStand hit target uses built-in particles/name, no new images. Attack, right-click, damage, login/server tick reconciliation are connected; only loaded chunks are queried, never forced. Carrier states remain authoritative, so chunk absence is not death.
- Verification so far: FrameworkTest 114 passed/0 failed; SourceCheck passed after runtime event wiring. Next: final Test/SourceCheck/Build/Verify for 0.3.2, then isolated empty-world startup/clean stop. Keep readiness closed.
- Still open: no full general ordinary calamity profile; no real Gu-use backlash hook or protection/loss model; no end-to-end real-client/multiplayer test. This work does not complete the original full-route objective.


## 2026-10-27 0.3.4 实施与实机边界

- 护道人邀请按主人 UUID 持久化，命令只在服务端配置加载且启用时可写；击退还检查邀请名单和 readiness。写盘失败回滚 owner/plot 两个权限集合；撤销同一次 `writeAll()` 完成。
- 形成前邀请在成窍提交、恢复以及登录对账时同步到形成后地块名单；离线玩家资料缓存为空时来访/撤销返回可读失败，不触发 NPE。
- 增加写盘失败回滚、形成前邀请晋升及形成后权限重启测试。最终核心测试 120/120、SourceCheck、Build、Verify 通过；产物 `0.3.4` SHA256 `55006655fe46f4e85f4ee4669c1ea1327f216f7cad62c38ae081eca8011d6445`。
- 代理只读复审对 owner 范围、配置 fail-closed、双集合原子回滚、离线缓存和登录补偿未发现必须修项。
- `build/test-server-0.3.4` 新建隔离空世界：本体和升仙 0.3.4 均加载、仓库打开、`Done` 后 `stop`，全部维度保存。日志有本体缺失注册/配方/进阶资源错误；升仙无启动异常。没有连接玩家。
- 修复了构建脚本版本 staging；更新本体静态审计文档（原文件内容被重复同一表行覆盖，已重建为可复核的 13 条代码事实）。
- 尚未完成原始目标：一般灾劫证据、用蛊反噬保护/损失、余气升炼、完整客户端/多人流程及恢复验收均缺；因此门禁继续关闭，不能报告全路线完成。

## 2026-10-27 continuation — 0.3.5 canonical grade correction

- Ruling: CANON-006 says 福地档次 follows the amount of fused 天地人三气; the planner counted only heaven+earth, so grade resolution now counts `qi.heaven + qi.earth + qi.human` and excludes `humanReserve` because it has not entered the qi cluster.
- TDD: added balanced inputs (20/20/20, 30/30/30, 70/70/70). Before the implementation, 120 existing checks passed and the new test failed on the expected medium threshold. After the change, 121/121 passed.
- Fresh verification: `build.ps1 -Action SourceCheck` passed with Minecraft 1.21.1 / NeoForge 21.1.263 / 110 local jars; `Build -ModVersion 0.3.5` passed tests, full source compilation, packaging and artifact verification; separate `Verify -ModVersion 0.3.5` passed.
- Artifact: `build/libs/shengxian-0.3.5.jar`, SHA256 `8596579b1b62bc060021231f5f7c5764c935c7b1cbeb805b6cee9dd9d445fa6b`.
- Isolated server: new `build/test-server-0.3.5` world loaded base mod + Shengxian 0.3.5 + embedded Veil 4.5.2, reached `Done`, opened session store, and stopped with all dimensions saved. No player connected. Upstream registry/recipe/advancement errors remain documented.
- Canon evidence: CANON-014 records the second-aperture failure exception from chapter 222; GAP-006 remains open for the ordinary single-aperture failure branch.
- Readiness remains closed; no live player world was used or changed.
## 2026-10-27 continuation — 0.3.6 canonical initial essence correction

- Review found upper-grade initial essence used a range that included 30 despite the canonical wording requiring more than 30. Changed the game-mapping range to 31–40 and added a seeded regression; kept unabsorbed human reserve excluded from grade.
- The suite reached 123/123; `SourceCheck`, Build and Verify passed for 0.3.6. Artifact SHA256: `b6ad0c00778be63c5f07da0a0dc8658699c0437a559b8633153a80c78c27cc68`.
- Real NeoForge client FlowBot connected to the isolated 0.3.6 server. `prepare`, `shatter`, `open` and `offer` reached readiness and were rejected; `form` with no session returned `Session missing` before the readiness check. No `first` session file was created. This observation motivated 0.3.7.

## 2026-10-27 continuation — 0.3.7 formation readiness correction

- Added a pure readiness policy: new formation cannot run while the route is closed; replay of a committed IMMORTAL formation remains allowed for recovery. `FormApertureCommand.form` now checks an existing committed record before choosing the recovery branch, otherwise checks readiness before reporting/loading a missing session.
- TDD regression first failed compilation because `mayAttemptFormation` did not exist; after implementation, 124/124 tests passed. `SourceCheck`, Build and Verify passed on Minecraft 1.21.1 / NeoForge 21.1.263 / Java 21 / 110 local jars.
- Artifact `build/libs/shengxian-0.3.7.jar`, 3,477,305 bytes, SHA256 `0476c89e1bcf311dd52a89c343ca812306224e3f0c545cdbec686c8b4d20f4ed`; embedded Veil 4.5.2 SHA256 `e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66`.
- Isolated server and NeoForge client both loaded 0.3.7. FlowBot joined; `/shengxian form` and `/shengxianform` returned the closed readiness message, status showed no `first` session, and the session directory remained empty. Client output confirmed Veil shader compilation/upload. These were console-dispatched commands against a real online ServerPlayer, not GUI-click acceptance. The user then asked to pause server calls; no clean stop is claimed for this run.
- The ordinary ascension objective remains incomplete and the readiness capability set remains empty. General ordinary tribulations, verified Gu backlash/protection/loss rules, single-aperture failure, real upstream aperture mapping, and full multiplayer/UI/recovery acceptance remain open.
## 2026-10-28 原著依据补核与停止

- CANON-008/GAP-004 已补入第 625 章对升仙期间催动蛊虫风险及成功升仙者常有蛊虫损失的依据；证据不支持每次催动必毁蛊，精确窗口、概率、被动持有与本命蛊保护仍待核。
- GAP-006 已补入第三卷第二百零一章：碎窍不可逆，三气失衡可导致自爆、消散、石化等致命结局；双空窍失败存活仍为特殊例外。
- 0.3.7 隔离测试服进程已不存在，但日志没有正常停服或全部维度已保存标记；按证据边界不声称正常保存。用户要求停止后，本轮不再启动或调用服务器。

## 2026-10-28 continuation — rank durability, legacy safety, Gu API audit

- 0.3.11 corrected rank-six recovery durability: player projection receipt is stored beside upstream rank; vanilla save return is not trusted without reading/forcing the actual UUID player file; singleplayer owner also verifies level.dat/Data/Player, the data source chosen by PlayerList. No real world was loaded or edited.
- 0.3.12 adds rank-six-sync-projection-v1 to new IMMORTAL commits. A legacy pending record without the marker and with current rank 5 is deferred (never automatically raised); current rank >=6 is only persisted/verified and acknowledged; current protocol records retain first 5→6 projection; a previously receipted then downgraded player remains deferred. Core tests 146/0; 22 compressed NBT checks pass.
- Build/Verify 0.3.12 passed, with 122 common-side classes free of client references. Protocol roundtrip passed 8 phase cases and 23 outcome checks. Artifact SHA256 aa0f42ffbf7d77bc91228ab8102a6bd8cfa76f03c63656242c5741dfc844158a; 0.3.11 artifact hash unchanged.
- Read-only code review found no new important issue. No Minecraft client, integrated world, dedicated server, DSH or live save was used.
- Static Gu activation audit now maps right-click, attack, passive inventory maintenance, block-use, and keybind routes. It identified no universal success API; exact backlash/loss/protection semantics remain unverified. Evidence is docs/evidence/gu-activation-api.md.
- Ordinary readiness stays empty/closed. Remaining goal blockers: canonical ordinary calamity profiles, evidence-backed Gu backlash and vital-Gu rules, upstream aperture mapping, and full real client/multiplayer/restart acceptance. Continue; do not claim the complete route is finished.
## 2026-10-28 continuation — CANON-008 backlash window refinement

- Re-read chapter 687 and 625 text. The former explicitly states mortal and immortal Gu suffer backlash during severe heaven/earth qi fluctuation and records Fang Yuan losing many mortal Gu; the latter locates Fang Zheng's activated-Gu backlash and destruction at the ascent-force/unguarded moment. Updated CANON-008, GAP-004 and Gu activation audit with source limits.
- This narrows the evidence for a real risk window but does not establish universal exact phase boundaries, loss rates, or vital-Gu immunity. Do not implement guessed probability or treat all uses during the whole session as guaranteed destruction. Actual accepted activation hook work must be paired with a still-open rule boundary; GU_BACKLASH remains unavailable until consequences are evidence-backed and testable.
- Evidence sources are non-official reposts; no source is represented as an official edited edition. No game or server launched.
## 2026-10-28 0.3.14：四条本体催蛊观察钩子（未启用反噬）

- 依据本体内层 JAR 的固定 SHA256 b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc，在月光蛊、白象蛇蛊、哀别持、定蛊二号四个 Procedure 的唯一 zhenyuan 写入后记录 ACCEPTED。仅服务端玩家、仅存在 first 升仙会话且处于 BREAKING 至 FORMING 时计入有界事件账本。
- 修复了 Mixin 回调必须完整匹配目标方法参数的签名问题，并清除 NeoForge TOML 中重复的四个注册表。构建器新增 ASM 静态检查：目标类/描述符、唯一真元写入、Inject 方法、完整回调参数、字段写入点、AFTER 位移和配置单次注册。
- FrameworkTest 157/157；真实压缩玩家 NBT 检查 22/22；Java 21 完整编译与四个目标校验通过；common-side 134 个类不含客户端渲染引用；Build/Verify 通过。0.3.13 不可变产物哈希保持不变。
- 反噬、销毁、概率、本命蛊豁免均未实现；被观察的只是四条接受分支，非全本体覆盖。普通升仙 readiness 仍关闭。没有启动 Minecraft、集成世界或服务器；运行时 Mixin 启动及多人行为未验证。见 docs/verification-0.3.14.md 与 docs/evidence/gu-activation-api.md。
## 2026-10-28 阶段 2 增量：0.3.14 四条接受催动观察钩子

0.3.14 将下列四个已审计 Procedure 接入 common Mixin。注入点均为目标 execute 方法中唯一的 PlayerVariables.zhenyuan PUTFIELD 之后；回调描述符与目标方法的全部参数逐项匹配：

| 入口 | Procedure 类 | 记录入口 |
| --- | --- | --- |
| 右键催动 | YueGuangGuDangYouJiKongQiShiShiTiDeWeiZhiProcedure | RIGHT_CLICK_USE |
| 右键催动 | BaiXiangSheGuYouJiKongQiShiShiTiDeWeiZhiProcedure | RIGHT_CLICK_USE |
| 攻击命中 | AiBieChiDangShengWuShiTiBeiGaiWuPinJiZhongProcedure | ATTACK_HURT_ENEMY；所有者按攻击者实体记录 |
| 方块使用 | DingGu2YouJiFangKuaiShiFangKuaiDeWeiZhiProcedure | BLOCK_USE_ON |

GuActivationObserver 只接受服务端 ServerPlayer，只在其 first 会话处于 BREAKING、BROKEN、ABSORBING、OPENING 或 FORMING 时记入 ACCEPTED。配置绑定上游内层 JAR 的精确指纹，构建时用 ASM 核对类、描述符、唯一真元写入、Mixin 注解目标、完整回调方法描述符与 PUTFIELD/AFTER 注入点；产物校验要求四个类和配置齐备、TOML 只注册一次。

这四个点只证明上游分支已接受催动并写入真元，不证明延迟弹道、召唤或方块效果最终完成。当前仅四种入口可观察，杀招按键、被动维护及其余 Procedure 未接线。事件留在服务器内存账本；没有反噬、扣除/销毁蛊虫、概率、本命蛊保护或任何升仙结果修改。GU_BACKLASH 与普通路线门禁继续关闭。游戏运行时 Mixin 引导、客户端/专用服务器加载和多人行为未验证。

离线验证详见 verification-0.3.14.md。
## 2026-10-28 阶段 2 增量：0.3.15 全局 owner 上限

观察事件与递增序号现在存放在同一 owner 状态中，默认上限为 4096 位最近使用的 owner，每位最多保留 64 条事件。新 owner 到达上限时，最近最少访问的 owner 状态连同事件去重键与序号一起淘汰；后续重新出现时从序号 1 开始。服务器停止时整体清空。

这是有界、易失的观察数据，不是可用于灾劫结算的持久事实；淘汰不触发惩罚，也不能被解释为蛊虫损失。回归覆盖 owner 淘汰和序号重置。详见 verification-0.3.15.md。
## 2026-10-28 continuation — 0.3.16 failure settlement guard

- AscensionReadiness accepts only open readiness plus exactly one canonical qi-overload cause; the player bridge repeats the guard before upstream mutation.
- Added negative coverage for empty, mixed, tribulation-impact and unclassified causes, and positive coverage for each of the three cited imbalance causes.
- Independent audit found a crash window: shiqiao/lethal damage is applied before persistent acknowledgement, while login and respawn retry pending failures. Do not enable CANONICAL_FAILURE until a durable idempotent consequence protocol is designed and verified.
- Current artifact: 0.3.16. Offline build/protocol verification only; no game or server launched. Runtime Mixin, multiplayer, and crash recovery remain unverified.
## 2026-10-28 continuation — 0.3.17 failure settlement receipts

- Implemented the approved one-attempt protocol: stable session/phase operation ID, PREPARED → STARTED → UNCERTAIN/RESPAWN_RECEIPT → ACKNOWLEDGED world markers, and verified NeoForgeData/PlayerPersisted player receipts.
- Replaced login/respawn lethal replay hooks with fail-closed reconciliation. Damage can only follow verified player STARTED and world STARTED; only a matching in-process death observation plus wasDeath Clone can promote the new player receipt. PlayerRespawnEvent verifies the saved NBT source before world acknowledgement.
- Added pure recovery/diagnostic tests and compressed player NBT checks. Readiness remains empty and CANONICAL_FAILURE remains disabled. No game, client, integrated world, server, or DSH was started; runtime crash/login/respawn acceptance is still pending.
## 2026-10-28 final review — 0.3.18

- Finalized the damage-event correlation check by reading LivingDeathEvent cancellation after synchronous hurt dispatch returns; added service-layer canonical-cause guards in addition to the player bridge guard.
- 0.3.18 supersedes the intermediate 0.3.17 artifact. Both artifacts are retained; verification records identify the current artifact. Runtime isolation testing remains pending because the no-server instruction is still active.
## 2026-10-29 成窍地块事务恢复增量

- 发现的崩溃窗口：地块先落盘预留、成窍会话后落盘；若两次写入之间崩溃，旧实现重试会另分地块并遗留无主占位。
- 改为对新的升仙预留持久化 owner/aperture/session/committed。相同会话复用 pending 地块；成窍会话先提交后确认地块，若确认前崩溃，成窍命令重试或登录恢复会补确认。其他会话无法提交该 reservation。旧版匿名 reservation 仍保持占用。
- TDD：先新增重启复用/隔离/幂等回归，观察到预期编译失败，再实现。build.ps1 -Action Test 为 176/176；SourceCheck 通过，含全部源码编译、8 个精确蛊钩子检查及 44 项压缩 NBT 检查。
- 未打包、未升版本、未启动游戏或服务器。本体没有经核验的原生空窍/仙元字段，因此世界会话/账本仍是成窍和仙元的权威记录；普通路线 readiness 继续为空集。真实玩家端、重登与崩溃运行验收仍待做。

## 2026-10-29 具名灾劫冲击事务增量

- 为三个具名 profile 接入服务端配置与会话冻结；普通 profile、无效配置和 schema 1–8 活动纳气迁移继续惰性。发现 schema 1 ABSORBING 迁移未冻结的缺口，先加 RED 回归再修复；schema 9 残留来源字段也先以 RED 复现解析器误接受，再改为拒绝。
- 外劫压力与三气风险分开保存；致命冲击凭证绑定 profile、carrier type/ID 与 impact sequence。TRIBULATION_IMPACT 使用独立服务、DamageSource 与 PlayerPersisted 回执，三气白名单未扩展。
- FrameworkTest 183/183；Java 21 SourceCheck 与 NbtCheck 通过，8 个 Gu 目标和 rank、failure、tribulation impact 三套回执各 22 项压缩 NBT 检查通过。`AscensionReadiness.implementedCapabilities()` 仍为空集。
- 源码未升版、未打包；未启动客户端、集成世界、服务器或 DSH。死亡/重生、多玩家和崩溃恢复仍需隔离实机验收，不宣称 exactly-once。
