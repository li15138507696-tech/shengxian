# 失败结算证据与安全边界

日期：2026-10-28。适用于普通路线三气失衡失败分支。原著依据与章节引文见 [canon-rules.md](canon-rules.md) 的 CANON-001/002/014；来源为网络转载，不代表官方校订本。

## 可确认的失败范围

- 完全碎窍后不可恢复或撤退。
- 人气过盛、天气过盛、地气过盛分别对应致命结局；代码原因是 HUMAN_OVERLOAD、HEAVEN_OVERLOAD、EARTH_OVERLOAD。
- 第二空窍使方源在一次失败中损失一窍而存活是特殊个例；不能推成普通单空窍蛊师可存活、复原空窍或重试。
- 单有灾劫冲击不等于上述三种气机失衡。TRIBULATION_IMPACT 不能套用三种失衡的本体死亡映射。

## 0.3.16–0.3.18 策略与代码边界

AscensionReadiness.hasCanonicalFatalQiCause 只接受恰含一个三气失衡原因的集合；空集合、UNCLASSIFIED、TRIBULATION_IMPACT 与混合原因均拒绝。mayApplyFailure 还要求 readiness 已开放；GuzhenrenPlayerBridge.failureDamageSource 在创建伤害源前重复检查门禁和单一白名单原因。

readiness 的 implementedCapabilities() 仍返回空集，所以生产升仙失败伤害不可达。协议实现本身不等于普通升仙已开放。

## 0.3.17 持久回执实现

- 世界会话在失败终态提交时写 PREPARED；操作 ID 固定由 session UUID 与 phaseRevision 派生。玩家 NBT 在 NeoForgeData/PlayerPersisted/shengxian:failure 写入操作 ID、唯一失败原因和 STARTED/RESPAWN_RECEIPT 阶段。
- 玩家 STARTED 收据和 shiqiao=true 先写入，再保存并核验 UUID 玩家文件；单人房主也核验 level.dat/Data/Player。随后世界状态变为 STARTED，才允许一次同步伤害调用。
- LivingDeathEvent 只观察当前进程内与同一 DamageSource 对象关联的死亡，不视为完成。只有 wasDeath Clone 与内存操作、旧玩家 STARTED 收据匹配时，才在新玩家 PlayerPersisted 写 RESPawn 收据；Clone 回调不调用 saveAll()。
- PlayerRespawnEvent 保存新玩家、核验实际玩家数据源与 RESPawn 收据后，世界状态才依次推进到 RESPawn Receipt 与 ACKNOWLEDGED。已持久的 RESPawn 收据只会补 ack，不再次施加伤害。
- STARTED 后无匹配完成回执、旧版无状态标记、存档冲突、保存失败或事件结果不明时进入 UNCERTAIN；旧数据无显式状态标记时也按 UNCERTAIN 解释。登录、重生、退出和停服都不会自动再次致死。
- 0.3.18 在同步 hurt 返回后读取完整 LivingDeathEvent 分发后的最终取消状态；死亡取消、伤害未接受或玩家仍存活时隔离为 UNCERTAIN。服务 API 和 DamageSource 桥接都重复要求唯一白名单原因。
- /shengxian status 展示世界状态、玩家收据和稳定操作 ID。玩家、世界存档、死亡掉落和统计没有共同原子事务，因此不宣称 Minecraft 副作用 exactly-once。

## 运行时验收仍待完成

当前只有纯 Java 策略测试、压缩玩家 NBT 离线测试和静态源码编译。CANONICAL_FAILURE 仍关闭。解锁前需在隔离世界验证写盘失败、死亡取消、正常 Clone/Respawn、Clone 后崩溃、Respawn 保存失败、世界 ack 失败、重登补 ack、普通死亡误认和单人/UUID 文件冲突。上述离线证据不能代替真实实体副作用验收。
## 本地 NeoForge 21.1.263 API 审计

依据项目本地 Minecraft 1.21.1 / NeoForge 21.1.263 编译 classpath 字节码与映射源码行号：

- PlayerList.save(ServerPlayer) 为 protected。公开 saveAll() 只遍历当前在线列表，不能返回逐玩家保存成功状态。
- PlayerDataStorage.save(Player) 为 public void，但捕获异常后仅记录 warn；正常返回本身不是持久化证明。六转投影中读取并校验实际玩家 NBT 的模式可供参考。
- ServerPlayer.restoreFrom 会把原角色 NeoForgeData 下的 PlayerPersisted 子树复制到新角色，然后发 PlayerEvent.Clone。根级自定义数据不会自动跨死亡复制。
- Clone 触发时旧角色已经从玩家列表移除，新角色尚未加入；Clone 回调内 saveAll() 保存不到新角色。PlayerRespawnEvent 发生在新角色加入后，才能调用 saveAll() 并检查实际加载源。
- LivingDeathEvent 可取消，且在死亡掉落、统计与最后死亡位置处理之前触发；它不能充当最终死亡回执。LivingEntity.kill() 仍通过 genericKill 伤害路径，不能绕过事件。
- 本体 shiqiao 是普通布尔字段，会随 Clone 复制，也可由石窍蛊设置或其他 procedure 清除；它不带升仙会话 ID、失败原因或死亡完成语义，不能当收据。
## 后续验证要求

- 纯 Java 策略测试覆盖重复调用、失败写盘、空/混合/未分类原因。
- NeoForge 隔离世界覆盖登录、死亡、重生和服务端进程重启；不得使用真实玩家存档。
- 证明普通死亡不会被误认为升仙死亡、已完成后果不会重放、中断后也不会静默漏掉应有后果。
- 以上证据完成前保持普通路线门禁关闭。

## 2026-10-29 具名灾劫冲击的独立结算

- `TRIBULATION_IMPACT` 保持在三气致命原因白名单之外。冲击只能由三个具名 profile 产生，终态绑定冻结 profile、载体类别、载体 ID 和单调冲击序号；累计外劫压力不改三气失衡风险。
- schema 9 保存 profile 快照、外劫压力/序号和冲击来源。schema 1–8 的活动纳气会话迁移为 `UNRESOLVED_ORDINARY`；旧失败缺少冲击来源时保留并按 `UNCERTAIN` 隔离，不自动造成死亡。
- 冲击使用独立 `shengxian:tribulation_impact` 玩家回执和结算服务。玩家 STARTED 与世界 STARTED 均经持久化核验后，才调用一次 `magic()` 伤害，当前固定伤害映射为 2048.0F。只有匹配的死亡 Clone 与 Respawn 回执完成后才 ACK。
- 保存失败、注销/停服、死亡事件取消、来源或回执冲突及其他不确定情况均进入 `UNCERTAIN`，不会再次致死。世界会话与玩家 NBT 不是共同原子事务，因此不承诺跨存档 exactly-once。
- 总 readiness 的能力集合仍为空，三气与灾劫冲击后果都不可由当前普通升仙流程触达。离线检查和未完成的隔离世界验收列在 [verification-witnessed-tribulation-impact.md](../verification-witnessed-tribulation-impact.md)。
