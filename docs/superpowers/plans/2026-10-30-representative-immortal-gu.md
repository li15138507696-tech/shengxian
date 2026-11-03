# 增强版代表仙蛊 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for the shared foundation; independent ability services may use superpowers:subagent-driven-development after their shared interfaces exist. Steps use checkbox (`- [ ]`) syntax for tracking. This checkout has no Git repository; reuse it and do not create commits.

**Goal:** 按已批准的增强版规格交付定仙游、人如故、江山如故的真实物品、能力、仙元扣费、恢复回执和独立反馈。

**Architecture:** schema 11 在原会话内保存有界仙蛊运行状态，与仙元原子扣费；可重复催动不膨胀旧追加式 effect ledger。服务器共用物品绑定、回执与扣费入口，三种效果独立；短时伤势及无掉落灾毁历史有严格容量和寿命。客户端只展示物品与反馈。

**Tech Stack:** Java 21、Minecraft 1.21.1、NeoForge 21.1.263、当前离线 build.ps1、领域回归与压缩 NBT verifier。

## Global Constraints

- 规格：`docs/superpowers/specs/2026-10-30-representative-immortal-gu-design.md`，增强版已由用户批准并要求开始开发。
- 定仙游：8 落点、60 ticks 蓄力、移动/受伤不打断、3 仙元/200 ticks 冷却。
- 人如故：100 ticks 历史、24 格可见玩家目标、自身及他人、近期燃烧/原版中毒/凋零、2 仙元/60 ticks 冷却；同一目标受治间隔 40 ticks。
- 江山如故：仅本人仙窍地块，32 格、单次 1024 格、每操作 64 格/tick、全服 512 格/tick；历史 12000 ticks、每人 8192/全服 65536 条；5 仙元/200 ticks 冷却。
- 留景默认关闭；只有确认无掉落的白名单爆炸损毁可复原。不可覆盖新方块或复制材料。
- 本体字段只读；readiness 空集不变；原排难路径保持兼容。
- 未确认效果不自动重放/退款/二次扣费；管理员按精确 operation ID 接受现状解除隔离。
- 不打开真实存档；不升版本、不发行、不提交。

## Shared Interfaces

```java
// Immutable core types; opaque effect payload is a bounded server-generated JSON string.
XianGuRuntime.empty();
XianGuRuntime.grant(String id);
XianGuRuntime.begin(UUID operationId, String id, long tick, double beforeEssence,
                   double cost, long cooldownTicks, String payload);
XianGuRuntime.finish(UUID operationId, XianGuRuntime.Stage stage);
XianGuRuntime.withMark(int slot, XianGuRuntime.Mark mark);
XianGuRuntime.selectSlot(int slot);
XianGuRuntime.withRecording(boolean enabled);
AscensionProgress.withXianGu(XianGuRuntime runtime);
XianGuStateCodec.write(Properties properties, XianGuRuntime state);
XianGuStateCodec.read(Properties properties);
```

`Mark` 保存维度、位置、朝向与景象摘要；`Use` 保存 operation/session 外的固定身份、单调序号、gu ID、冻结费用/扣费前后余额、冷却终点、payload、CHARGED/COMMITTED/UNCERTAIN/ACKNOWLEDGED 阶段。服务器项目会话本身提供 session UUID。

```java
// Server entry points; the service owns held-item/phase/rank/session validation.
XianGuUseService.check(ServerPlayer caster, String id);
XianGuUseService.execute(ServerPlayer caster, String id, List<ServerPlayer> subjects,
                        String payload, Runnable effect, BooleanSupplier verify);
RenRuGuService.use(ServerPlayer caster, boolean targetOther);
DingXianYouService.recordMark(ServerPlayer caster);
DingXianYouService.cycleMark(ServerPlayer caster);
DingXianYouService.start(ServerPlayer caster, InteractionHand hand);
JiangShanRuGuService.toggle(ServerPlayer caster);
JiangShanRuGuService.use(ServerPlayer caster);
```

## Task 1: 可重复仙蛊、运行状态与 schema 11

**Files:** `core/XianGu.java`, `core/XianGuCatalog.java`, `core/XianGuLedger.java`, new `core/XianGuRuntime.java`, new `core/XianGuStateCodec.java`, `core/AscensionProgress.java`, `core/FileSessionRepository.java`, constructors in `core/QiEngine.java`, `core/ApertureEngine.java`, `core/TribulationEngine.java`, `src/test/java/net/guzhenren/shengxian/FrameworkTest.java`.

- [ ] RED：目录 4 项；新三只 `reusable()`；grant 后重复 begin/finish 不消耗持有；同一 operation 不二次扣费；冷却和不确定操作拒绝。
- [ ] RED：8 个 mark 持久化；未知槽/非法数据拒绝；schema 10 默认空状态；schema 11 状态读回；IMMORTAL 拒绝非指定字段变更。
- [ ] 运行 `pwsh -NoProfile -File .\build.ps1 -Action Test` 并记录失败原因。
- [ ] 实现有界模型与 codec，新增进度字段并逐个保留所有 wither/引擎构造调用中的 runtime，保留旧构造器兼容。
- [ ] 世界 charge 同次保存 essence 与 operation/cooldown；更新门禁单独校验 runtime 的合法转移及持有单调性，不放开其他进度。
- [ ] GREEN：FrameworkTest 全通过。

## Task 2: 实体物品、共用扣费与玩家回执

**Files:** new `item/ImmortalGuItem.java`, new `item/ShengxianItems.java`, new `server/XianGuUseService.java`, new `server/XianGuItemBinding.java`, new `compat/SavedXianGuUseReceipt.java`, new `tools/VerifyXianGuUseReceipt.java`; modify `ShengxianMod.java`, `ShengxianConfig.java`, `XianGuService.java`, `XianGuCommand.java`, `build.ps1`.

- [ ] 先写 NBT verifier：绑定 owner/session/op/gu、PREPARED/COMMITTED/UNCERTAIN/ABORTED、拒绝错身份和回退、玩家/host、健康/位置/伤害状态核验；运行 NbtCheck 观察 RED。
- [ ] 注册不可堆叠物品；绑定数据与账本同时校验，grant 先存账本，满背包可 recover，副本共享运行状态。
- [ ] 扣费入口先核验所有相关玩家存档旧来源并准备回执，再原子扣费，完成效果后分别保存/读回并确认世界 operation；异常隔离。
- [ ] status 与精确 operation ID acknowledge；登录只确认已提交结果，未完成效果不重放。
- [ ] NbtCheck 与 SourceCheck 全通过；原排难命令继续可用。

## Task 3: 人如故与定仙游

**Files:** new `core/RecentHealthHistory.java`, new `core/TravelChannel.java`, new `server/RenRuGuService.java`, new `server/DingXianYouService.java`; lifecycle hooks in `ShengxianMod.java`, `AscensionServer.java`.

- [ ] RED：五秒生命/伤害状态窗口、历史清理、目标受治间隔；八槽标记、60 ticks 蓄力、移动/受伤允许、取消条件、失败不扣费。
- [ ] GREEN：有界纯模型通过领域回归。
- [ ] 人如故服务器射线/遮挡/距离校验，效果前双人锁、目标复核，实际健康和新伤害状态还原后确认回执。
- [ ] 定仙游记录服务器落点和景象；保持使用蓄力，完成前安全/权限复核，传送后清除 fallDistance 并确认加载源。
- [ ] 服务事件连接后 SourceCheck 通过。

## Task 4: 江山如故留景与修复

**Files:** new `core/LandscapeHistory.java`, new `server/JiangShanRuGuService.java`, new `mixin/LandscapeExplosionMixin.java`, new `tools/VerifyLandscapeExplosionHook.java`; common mixin JSON and `build.ps1`.

- [ ] RED：白名单/容量/十分钟过期/边界、无掉落记录才可修复、记录只能消费一次、1024 计划、64/512 分批预算。
- [ ] GREEN：纯有界记录模型通过。
- [ ] 精确包装 `BlockBehaviour.onExplosionHit` 的已核白名单分支；仅有效留景与足够容量时抑制 consumer/经验，实际销毁后才发布可用记录；不能确认时退回原版并不登记可恢复条目。
- [ ] 修复仅原位置空气、本人地块、无碰撞、已加载区块；逐格消费，离线/卸载/停止服务失效；按共用扣费回执运行有界分批任务。
- [ ] 精确字节码 verifier 与领域回归全通过。

## Task 5: 模型、反馈与最终验收

**Files:** `assets/shengxian/models/item/{ding_xian_you,ren_ru_gu_immortal,jiang_shan_ru_gu}.json`, two lang JSON files; new `server/XianGuEffects.java`, new `docs/verification-representative-immortal-gu.md`, update `docs/ascension-implementation-plan.md`.

- [ ] 三个专属立体蛊模型、工具提示、动作栏与低亮度局部反馈；JSON/注册/语言键一致性检查。
- [ ] 完成 Test、NbtCheck、SourceCheck；只在新增变更/失败需要时重跑。
- [ ] 若隔离运行依赖可用，记录注册/加载或玩家交互的实际证据；不把静态编译称为客户端验收。
- [ ] 逐项对照增强版规格自审，修复发现的问题，记录准确测试数量、交互方法和剩余实机边界。
