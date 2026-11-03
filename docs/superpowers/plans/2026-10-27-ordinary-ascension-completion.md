# 普通升仙闭环完成 Implementation Plan

> **For agentic workers:** Execute inline in the current session. Steps use checkbox syntax and are tracked in the SDD ledger.

**Goal:** 完成有正文依据的普通五转至六转升仙流程，从备妥、碎窍、纳气、并发灾劫、投入本命蛊、成窍，到六转/仙元结算与重启恢复；特殊路线在证据不足时继续关闭。

**Architecture:** 世界会话侧车保存阶段、进度与待结算副作用；蛊真人字段仅由 `GuzhenrenPlayerBridge` 读写。所有不可逆的玩家字段/物品效果均先记录可恢复意图，再由登录/服务器生命周期重放并确认；`AscensionReadiness` 只在全链路验证后开放。

**Tech Stack:** Minecraft 1.21.1、NeoForge 21.1.263、Java 21、项目现有纯 Java `FrameworkTest`、本地蛊真人 10.20 JAR。

**Spec / Evidence:** `docs/ascension-implementation-plan.md`, `docs/evidence/canon-rules.md`, `docs/evidence/upstream-audit.md`.

## Global Constraints

- 仅开放普通五转路线；十绝体、方正、神不知、仙僵、至尊仙胎等保持关闭，除非相应 GAP 被原著证据补齐。
- 不用 `renqi` 推算原著人气；不把 `kongqiao` 作为空窍是否破碎的标志。
- 仅实现 `canon-rules.md` 中 CANON-001..013 已核内容；各例证不推广到未核 profile，GAP-001..008 不猜测、不硬编码。
- 碎窍彻底完成后不可回退；动作重放、崩溃恢复不得重复扣真元、消费物品、发放仙元或生成灾劫。
- 既有 0.2.5 / 0.2.6 JAR 和用户真实存档保持不变；集成验证使用隔离存档。
- 项目无 Git；在 `.superpowers/sdd/2026-10-27-ordinary-ascension-completion/progress.md` 记录进度与无提交说明。

## Review Focus

- 写入会话后、写入本体前崩溃：重登只重放一次；本体已写入后、确认标记前崩溃：不重复扣费/扣物。
- 登录玩家境界已低于五转或发生合法降转：拒绝覆盖，保留待处理记录并记录告警。
- 三气或外劫跨出界、区块卸载/重载、玩家离线：不凭客户端状态结算失败/胜利。
- 本命蛊指纹重复、物品复制/移动、背包满格或物品来源不符：不开始投入，也不丢失原物。
- 坏档、未知 schema、多人同地块和重复命令：保留证据，不覆盖、不重复副作用。

### Task 1: 成窍后六转提交恢复

**Files:** `AscensionProgress.java`, `AscensionService.java`, `SessionStore.java`, `AscensionLifecycleService.java`, `AscensionServer.java`, `FrameworkTest.java`.

**Interfaces:** 现有 `RANK_SIX_SYNC_RECOVERY_EFFECT` 作为 pending 标记；追加独立确认键，不删除既有账本标记；玩家登录时自动恢复，管理员命令复用同一恢复逻辑。

- [x] 为 pending/ack 状态和六转恢复决策写失败测试。
- [x] 实现幂等确认：`phaseRevision` 不变、`stateRevision` 单调增加；重复确认不再次写入。
- [x] 登录时恢复六转；只接受当前五转或已达六转，其他境界保留待处理并拒绝覆盖。
- [x] 0.3.1 Test（111 passed）、SourceCheck、Build 和 Verify 已通过；隔离空世界服务端启动、仓库打开与正常停服通过。

### Task 2: 本命蛊真实来源与投入托管

**Files:** `BenMingGuStore.java`, `BenMingGuCustody.java`, `AscensionProgress.java`, `AscensionLifecycleService.java`, codec / `FrameworkTest.java`.

- [x] 对照 CANON-005、GAP-004 与参考包，确认投入时点；仍无普通路线通用的逐蛊余气升炼映射，未知时保留原物。
- [x] 为实际 inventory stack 建立持久化托管/恢复状态；先保存托管意图再移除原物，登录恢复时消除重复或复原未提交事务。
- [x] 成窍后新增 `EMBEDDED` 状态，保留完整原物，不伪造通用返还或仙蛊升炼；旧版已启动的返还事务仍安全完成。
- [ ] 真实客户端 inventory、满背包、断线和形成结果仍需隔离玩家验收。
- [x] 覆盖账本重启与状态幂等；[ ] 实际 inventory 移动、满背包、失败和形成成功的隔离客户端验收。

### Task 3: 三气与师法自然闭环

**Files:** existing Qi engine/service/payload/screen classes and `FrameworkTest.java`.

- [x] 将环境二气、有限人气、已纳气、风险、窗口与领悟绑定同一服务端模拟步和原子会话写入。
- [ ] 确保玩家只能提交有界意图；关闭界面不暂停；重连不重复领悟；CANON-002/003/004 的损益可实测。
- [ ] 只有有证据的知识结果可发放；未核额度不设置原著硬上限。

### Task 4: 灾劫载体、并发和失败后果

**Files:** `TribulationEngine.java`, server entity/registry classes, player lifecycle hooks, `FrameworkTest.java` and isolated game fixtures.

- [ ] 依 CANON-009/011 实现可见、可交互的音磬天灾与荒兽地劫载体、来源、影响、应对、结束；与纳气并发，区块卸载不算载体死亡。
- [x] schema 8 持久化灾劫维度、位置和实体 UUID；schema 7 并发载体迁移为未放置；确定性 UUID 使存档提交后崩溃可恢复而不重复生成。
- [x] 服务端交互锚点、按 ID 护道击退、本人禁击、右键/伤害拦截和区块未加载保护已接线；完整多人实机验收仍待完成，普通路线载体目录仍因 GAP-001 关闭。
- [ ] 依 CANON-002 限定三种失衡失败原因；失败记录账本具幂等确认，但本体死亡/失窍副作用先于确认，不能保证崩溃后一致且不重放，能力保持关闭。
- [ ] 依 CANON-008 把反噬接入真实用蛊调用点；不每 tick 删除蛊，不猜测本命蛊保护规则。
- [ ] 验证载体存活恢复、双人护道、重复击杀/掉落和失败恢复。

### Task 5: 成窍、仙元、六转统一结算并开放门禁

**Files:** formation / aperture stores, `FormApertureCommand.java`, `GuzhenrenPlayerBridge.java`, `AscensionReadiness.java`, matching tests.

- [ ] 成窍规划按 CANON-006 范围生成档次、面积、初生仙元与时间比；游戏缩尺明确标注。
- [x] 同一恢复协议完成仙窍形成、地块认领、六转字段同步确认、仙元账本；核心阶段崩溃可从记录恢复且不重复生成/扣除。 [ ] 真人服务端提交/重连验收。
- [ ] 只有所有八项 `RuleProfile.Capability` 都已实现并有验收覆盖后，才将其加入能力集合、打开普通路线。
- [ ] 对账保留本体之后合法发生的降转/重置；不自动补寿元。

### Task 6: UI、联机和隔离实机验收

**Files:** client screens/payloads, test fixtures, `build.ps1`, `README.md`, verification record.

- [x] K 键六页界面覆盖总览、三气、求知、灾劫、本命蛊、仙窍；只显示服务端快照。实际客户端渲染与玩家流程仍待验收。
- [ ] 独立测试存档验证完整普通路线、退出重连、停服恢复、多人护道、维度进入/离开、失败后果和重复动作。
- [x] 0.3.1 Test（111）、SourceCheck、Build、Verify；只在新建隔离空世界加载并正常关闭；0.2.5/0.2.6/0.3.0 均保留。
- [ ] 记录实际验证边界；未通过的路线不宣称完成。


### 2026-10-27 0.3.4 执行补记

- 已实现：世界灾劫锚点、owner-scoped 护道人授权与原子持久化、成窍/登录权限对账、离线主人 profile 缓存保护。
- 已验证：120 项核心测试、完整 NeoForge SourceCheck、Build/JAR Verify、隔离空世界服务端 `Done` 与正常停服。
- 未验收：真实客户端、双人攻击/护道、区块卸载/重启恢复、原著一般灾劫目录和用蛊反噬结算。生产门禁仍关闭。
- 该版本验证证据：`docs/verification-0.3.4.md`。
