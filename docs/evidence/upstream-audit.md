# 上游本体 10.20 静态审计

审计日期：2026-10-27。范围：用户提供的 10.20 外层 JAR 及其内嵌蛊真人本体。此文件记录静态字节码/反编译核对，不把字段名当作原著规则。

## 输入与复核方法

- 外层 SHA256：`3733c20ffbd16d9068bb8188d45b24a0d61b279d7cb6fdc8ea341938a7f4cf04`（120,223,219 字节）。
- 内层条目：`META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar`。
- 内层 SHA256：`b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc`。
- 核对文件：`build/decompiled/net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.java`、`GuzhenrenModVariables.java`、`KE1Procedure.java`、`JingJieBaoCunProcedure.java` 和 `build/field-access2.tsv`。
- 可复核构建环境：Minecraft 1.21.1、NeoForge 21.1.263、Java 21、本地 110 个依赖 JAR。以上审计副产物不进入升仙发布包。

## UPSTREAM-001..014

| 编号 | 静态事实 | 实现边界 |
| --- | --- | --- |
| UPSTREAM-001 | 外层 JAR 以 Jar-in-Jar 方式携带 `guzhenren-0.1-neoforge-1.21.1.jar`；两个归档指纹见上。 | 只针对这两个已核对字节版本；不能把其他本体包视为兼容。 |
| UPSTREAM-002 | 内层本体声明 Minecraft 1.21.1 与 NeoForge 依赖；玩家数据由 `GuzhenrenModVariables` 的 NeoForge attachment 保存。 | 模组能加载不代表具有普通升仙业务流程。 |
| UPSTREAM-003 | `PlayerVariables` 使用 `double` 保存 `zhuanshu`、`jieduan`、`kongqiao`、`tizhi`、`renqi`、`zhenyuan`、`zuida_zhenyuan` 等字段；另有 `boolean shiqiao`。 | 字段名和类型不能单独证明语义或安全写入顺序。 |
| UPSTREAM-004 | `PlayerVariables.serializeNBT/deserializeNBT` 将这些字段写入、读回玩家 NBT；`clonePlayer` 也复制境界、阶段、空窍编码、失窍标记、人气字段及本命蛊相关字段。 | 死亡克隆会保留这些字段，不能假设死亡后本体会自动清空或校正升仙事务。 |
| UPSTREAM-005 | `KE1Procedure` 在有境界时按是否有“九重金莲真元”效果钳制 `jieduan`：无效果为 1..4，有效果为 2..5；`zhuanshu` 是独立字段。 | 阶段范围不是原著数字；无效果 4、有该效果 5 是本体代码映射，不应把转数和阶段混为一谈。 |
| UPSTREAM-006 | 同一 `KE1Procedure` 在 `kongqiao != 3` 时，根据 `zuida_zhenyuan` 的 400..599、600..799、800..999 区间写入不同 `kongqiao` 编码。 | `kongqiao` 是本体内部分类编码，不是空窍是否已破碎或仙窍状态。 |
| UPSTREAM-007 | `KE1Procedure` 每次执行都会重算 `renqi`：汇总多种流派熟练值、魂魄/精力上限、`smz` 记分、魅力和名声等字段。 | `renqi` 是本体派生值，不是独立的原著人气储备；不得直接当作升仙消耗或三气资源。 |
| UPSTREAM-008 | `shiqiao` 是独立布尔值，与数值型 `kongqiao` 分开序列化、复制。 | 不能用 `kongqiao` 替代失窍写入，也不能把 `shiqiao` 当作碎窍过程进度。 |
| UPSTREAM-009 | 玩家变量中有 `ItemStack BenMingGu`、`BenMingGu_1`、`LianGuBenMingGu` 等字段，且会保存/读回和克隆。 | 只证明存在物品槽及标记；不能证明放蛊、消耗、炼成仙蛊、唯一性或本命蛊保护结算。 |
| UPSTREAM-010 | 在 `PlayerVariables` 的已核字段中没有名为仙窍/仙元的独立玩家记录；项目另用世界侧账本记录面积、时间比与青提仙元。 | 这是字段/接口边界，不证明本体其他模组逻辑绝无相关概念；不能伪称已写入本体仙窍。 |
| UPSTREAM-011 | `JingJieBaoCunProcedure` 在修为规则开启、寿元归零、且未被复活效果排除的死亡处理中，重置转数、阶段、空窍编码、体质、部分真元及数值字段 `benminggu`。 | 此处重置不证明物品槽 `ItemStack BenMingGu` 被清空；升仙失败后的统一失窍/死亡结果不应由模组猜测，调用前必须有分类和幂等提交保护。 |
| UPSTREAM-012 | `zhuanshu` 是普通玩家变量；升级流程和多个交互/效果类过程都可能写入，不是由升仙附属模组独占的事务字段。 | 写六转前需记录预期旧值、确认服务端回写、处理重登与后续合法降转；不能仅凭变量名认定升级安全。 |
| UPSTREAM-013 | `KE1Procedure` 会持续修正阶段与若干属性；死亡、重生和玩家变量同步有本体自己的事件顺序。 | 侧车账本不能取代本体事件验收；必须用隔离客户端/服务器检查境界、重生、掉线与存档恢复。 |
| UPSTREAM-014 | `PlayerVariables.shouyuan` 是 `double`，随 attachment NBT 序列化/反序列化并在 `clonePlayer` 复制；`SshouyuanProcedure` 以两位小数格式显示该值。 | 桥接器只展示原值，不推断单位，也不将它用于升仙门禁或结果结算。 |

## 升仙侧事务实现（不属于本体接口证据）

- 2026-10-30：升仙仓库 schema 10 的碎窍意图保存已审计 `zhenyuan` 的扣费前值与目标；玩家侧 PREPARED/COMMITTED/UNCERTAIN 回执与 UUID 玩家文件、单人 host `Data/Player` 分别核验。该实现只消费本表确认的 `zhenyuan` 字段，不增加本体字段或改变本表的语义结论。
- schema 9 及更早的 target-only 活动碎窍没有可证明的扣费前值。除非存在匹配且已提交的玩家回执，否则升级后的代码会隔离会话，不从当前值推算或补扣。
- 以上是升仙侧源代码与离线 NBT 检查结果，不是隔离客户端/服务器上的本体运行验收。

## 当前结论

- 本体有凡人境界、真元、空窍分类、失窍标志、本命蛊相关槽位和玩家变量持久化，但这些事实不构成普通升仙接口。
- 没有从这些字段推出原著的三气阈值、灾劫目录、蛊虫反噬损失、仙窍面积/时间倍率、升仙后本命蛊归宿或完整特殊路线。
- 升仙侧车的实体锚点与交互是附属模组自己的实现。原著证据仍以 `canon-rules.md` 为准；待核项见 GAP-001..008。
- 0.3.4 隔离空世界服务端只证明模组被加载、世界仓库打开和服务器能正常停止；未连接玩家，不能证明境界同步、护道实战或完整路线。

## 可复现审计

```powershell
python build/scan_fields2.py
python build/keyword_scan2.py
```

本体 JAR 未修改；没有改动用户真实存档或客户端实例。
