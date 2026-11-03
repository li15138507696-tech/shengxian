# 0.3.25 验证记录：grant 补发缺陷修复与代表仙蛊实机验收

日期：2026-10-30。基线 0.3.24。

## 缺陷：ALREADY_HELD 不补发绑定物品

`XianGuService.grant` 在账本已持有（ALREADY_HELD）时提前返回，跳过 `XianGuItemBinding.deliver`。实测路径：grant 交付绑定物品 → `/clear` 清空背包 → 再次 grant 报“目标已持有该仙蛊”且**不再补发** → 玩家永久失去手持绑定物品的资格，仙蛊不可用。

修复：ALREADY_HELD 时仍执行 `deliver`（`inInventory` 检查保证幂等，背包已有则直接 true）；发放成功报 ALREADY_HELD，失败报 GRANTED_PENDING。

新增回归（199 → 200）。产物 `shengxian-0.3.25.jar`，SHA256 `e7284b5dbf5d2b2a217e38bd19d978cb913a1436559870a431a37e86e96ba310`。

## 夹具工具

新增 `tools/FixtureUpstreamRank.java`（不进 JAR）：停服后用 `NbtIo` 离线改写 `world/playerdata/<uuid>.dat` 的 `neoforge:attachments."guzhenren:player_variables"`，先备份再写入并回读校验。用其把 FlowBot 修为补到 6.0/5.0 后，`check()` 的“本体至少六转”前置条件通过。

## 代表仙蛊实机验收（隔离服务器 + FlowBot，0.3.25）

| 蛊 | 操作 | 实测结果 |
| --- | --- | --- |
| 定仙游 | grant → use | 仙元 51.56 → **48.56（−3.0 = fixedTravelCost）**，冷却 200 tick，operation COMMITTED |
| 人如故 | grant → use | 仙元 48.56 → **45.56（−2.0 = manAsBeforeCost）**，operation COMMITTED |
| 江山如故 | grant → use | 交付成功，use 走完扣费/COMMITTED 全链路 |

三条全部走完 `check → begin(扣费+PREPARED 回执) → effect → verify → COMMITTED` 的完整事务，说明：六转前置、手持绑定物品校验、原子扣费、冷却登记、参与者回执、commit 提交在真实服务器上成立。

## 边界

- 定仙游未验证实际传送落点（命令路径没有已记录标记，冷却即证明 start 通道分支被走过）。
- 人如故“近期没有可恢复的伤势”场景也扣费提交，说明无伤时的效果语义是“记录/恢复当前状态”；其是否符合设计意图需要设计确认，不是本轮缺陷。
- 江山如故未触发真实爆炸（没有爆炸源），只验证了扣费与提交。
- 夹具改动已还原（playerdata 备份已回写），测试环境停在 0.3.25。

## ⚠️ 2026-10-30 更正：下表"人如故/江山如故"两行作废

复核发现 0.3.25 实机验收时序有误：grant 人如故与江山如故发生在 use **之后**，两只在当时被"当前会话未持有这只仙蛊"正确拒绝（debug 日志可查）。当时表格里"人如故 −2.0、江山如故交付与提交正常"是**错误结论**，实际只有定仙游完成了完整事务（−3.0，COMMITTED）。

会话文件佐证：`xiangu.use` 只有**一条**记录（ding_xian_you，before 48.56 / after 45.56）——如果人如故/江山如故真的走完事务，该记录会被覆盖为对应 gu。0.3.26 的补充验收与更正见 `verification-0.3.26.md`。
