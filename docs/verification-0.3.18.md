# 0.3.18 离线验证记录：失败结算持久回执

日期：2026-10-28。当前 JAR 是 `0.3.18`。本轮只做离线测试、编译、打包和产物/协议校验；没有启动 Minecraft、客户端、集成世界、专用服务器或 DSH。

## 实现范围

- 失败世界状态：`PREPARED → STARTED → UNCERTAIN` 或 `RESPAWN_RECEIPT → ACKNOWLEDGED`。旧失败记录缺少状态标记时按 `UNCERTAIN` 处理。仓库拒绝跳步、修改失败原因和非法附加 effect。
- 操作 ID 由会话 UUID 和失败终态 `phaseRevision` 稳定派生；重复登录不会创建新的伤害操作。
- 玩家 `NeoForgeData/PlayerPersisted/shengxian:failure` 收据包含操作 ID、唯一白名单原因和 `STARTED`/`RESPAWN_RECEIPT` 阶段。保存后读取并核对 UUID 玩家文件；单人房主额外核验 `level.dat/Data/Player`，并 force 文件。
- 伤害前必须先核验玩家 `STARTED` 收据，再持久化世界 `STARTED`。只调用一次原有原因映射伤害源；同步死亡事件完整分发后再检查取消状态。
- 仅匹配当前内存操作、同一 `DamageSource` 的死亡观察、`wasDeath` Clone 和复制的 `STARTED` 收据，才会将克隆玩家收据升级。Clone 内不保存；Respawn 后保存核验玩家回执，再确认世界记录。完成回执恢复只补 ack。
- 写盘/事件不明、死亡取消、原因/操作 ID 冲突时进入或保持 `UNCERTAIN`，不自动重试。`/shengxian status` 显示世界状态、稳定操作 ID 与玩家收据状态。
- `AscensionReadiness.implementedCapabilities()` 继续为空；`CANONICAL_FAILURE` 仍关闭。协议没有把多份 Minecraft 存档、掉落和统计变成原子事务，不宣称 exactly-once。

## 验证结果

- `pwsh -NoProfile -File .\build.ps1 -Action Test`：164 passed，0 failed。
- `pwsh -NoProfile -File .\build.ps1 -Action SourceCheck -ModVersion 0.3.18`：Java 21 / NeoForge 21.1.263 完整源码编译通过；四个本体催蛊 hook 静态目标检查通过；静态公共侧边界检查通过，151 个 common-side 类未引用客户端渲染类。
- `pwsh -NoProfile -File .\build.ps1 -Action NbtCheck -ModVersion 0.3.18`：保存修为投影 22 项、失败收据 22 项压缩 NBT 检查通过。覆盖身份、操作 ID、原因、阶段、`shiqiao`、损坏输入及单人房主加载源。
- `pwsh -NoProfile -File .\build.ps1 -Action Build -ModVersion 0.3.18`：测试、源码编译、hook/NBT 检查、JAR 创建与内嵌依赖校验通过。
- `pwsh -NoProfile -File .\build.ps1 -Action Verify -ModVersion 0.3.18`：产物元数据、公共侧类边界和 JAR 指纹通过。
- `pwsh -NoProfile -File .\tools\verify-protocol.ps1`：8 阶段视觉协议回环通过，升仙结果校验 23 项通过；修为与失败回执各 22 项压缩 NBT 检查通过。

## 产物

- 当前产物：`build/libs/shengxian-0.3.18.jar`
- 大小：3,611,090 bytes
- SHA256：`048b317e214b84b7b20d0300ce995feecbe7e0c5f22332b91ee905beecd27599`
- 内嵌 Veil：4.5.2；SHA256 `e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66`
- 保留的旧产物：0.3.16（3,582,334 bytes，SHA256 `6814e50a25bd7a942857972e50ae94ed98a8e1652e30dde5e4ee53a936820e26`）和中间版 0.3.17（3,610,832 bytes，SHA256 `796f59e60ccd511df158b9df35cc221b8bb2a6a613edd763f2814578faa87284`）。0.3.18 是当前版本。

## 尚未验证

死亡取消、Clone/Respawn 的真实事件顺序、进程在每个保存边界退出、单人世界与 UUID 玩家文件冲突、多人登录恢复和实际掉落/统计行为尚未在隔离游戏中运行。按用户指示，本轮没有启动任何 Minecraft 服务器或客户端。完成这些运行验收前，失败后果能力和普通升仙入口继续关闭。

## 2026-10-29 运行时复核：四个 Mixin 是否真的应用

0.3.14 的记录只证明了静态描述符匹配，没有证明运行时会注入。本次用一次性探针构建补齐这一步。

- 方法：从 `build/libs/shengxian-0.3.18.jar` 重打包一个探针 jar，只在 `ShengxianMod` 构造器末尾加一行 `Class.forName(name, false, loader)`，强制加载四个目标 Procedure；装入隔离专用服务器启动后读日志。探针 jar 与服务器 mods 已还原为发布产物，探针构建已删除。
- 结果：四个类全部加载成功，`debug.log` 出现四条实际注入记录：

```
Mixing YueGuangGuProcedureMixin from shengxian.common.mixins.json into net.guzhenren.procedures.YueGuangGuDangYouJiKongQiShiShiTiDeWeiZhiProcedure
Mixing BaiXiangSheGuProcedureMixin from shengxian.common.mixins.json into net.guzhenren.procedures.BaiXiangSheGuYouJiKongQiShiShiTiDeWeiZhiProcedure
Mixing AiBieChiProcedureMixin from shengxian.common.mixins.json into net.guzhenren.procedures.AiBieChiDangShengWuShiTiBeiGaiWuPinJiZhongProcedure
Mixing DingGu2ProcedureMixin from shengxian.common.mixins.json into net.guzhenren.procedures.DingGu2YouJiFangKuaiShiFangKuaiDeWeiZhiProcedure
```

- 意义：注入点存在、描述符匹配（`require=1` 下缺失会直接抛错），类转换成功。
- **仍未证明**：回调在真实用蛊时的触发时机、`ACCEPTED` 判定是否符合“分支已扣费”，以及多人/重连下的行为。这需要真实用蛊操作，仍未做。
- 环境：服务器 `Done (2.177s)`，启动无异常；未连接玩家。
