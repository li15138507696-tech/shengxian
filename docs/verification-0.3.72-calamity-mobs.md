# 0.3.70–0.3.72 灾劫做成生物实体

日期：2026-11-02。用户要求：把灾劫做成生物。

## 已实现

- `entity/CalamityCarrierEntity`：继承 `PathfinderMob` 的真实生物，自带碰撞箱、变体同步、存档字段；体型按灾劫类型区分（虎 4.6×7，荒兽 7.5×12，音磬 3×6，血雨 3.2×3.3，柳风 4.5×3.8，单位格）。
- `entity/ShengxianEntities`：注册 `shengxian:calamity_carrier`。
- `entity/ShengxianEntityAttributes`：注册属性，MAX_HEALTH 取血池上限。
- `client/CalamityCarrierRenderer` + `CalamityClientSetup`：用现有原创 geo/atlas/animation 渲染生物本体，不再依附盔甲架。
- `command` 新增 `/shengxiantest mob <tiger|beast|chimes|blood|jade_willow>`。
- `CalamityCombatService` 泛化为接受任意 `Entity`，盔甲架与生物共用攻击循环与受击/摧毁逻辑。

## 实机验证（真实证据）

- 生成生物成功，服务端返回“已生成灾劫生物 独角峥虎地劫（1000000.0 生命）”。
- `/data get entity` 读到 Health = 1000000.0，名牌显示 **1.00M HP**。
- `/damage ... 250000 minecraft:magic` 后 Health = 750000.0 —— 是真正可受伤的实体，不是视觉贴图。
- 截图 `docs/art/ingame-mob-tiger.png`：生物本体在游戏中渲染（镜头位于虎腿之间，可见体型与名牌）。

## 关键限制（重要）

基础模组的 AttributeFix 把 `generic.max_health` 上限锁在 **1,000,000**。我曾把血量设为 150 万，实际被静默压到 100 万且名牌仍写 1.50M；已改为统一 1,000,000 并把原因写进代码注释。若确实需要百万以上，需要改 AttributeFix 或换用独立血量池（不经过原版属性）。

## 未完成 / 未验证

- **生物的攻击循环未观察到实际攻击**：已把生物接入 `CalamityCombatService.tick`，但实机只看到生物存在、可被伤害，没有出现 `Calamity strike` 日志，需再排查（疑似实体 tick 分支未触发）。
- **正式灾劫仍是盔甲架**：`/shengxiantest mob` 是测试入口；`TribulationWorldService` 的正式载体生成仍走 ArmorStand，切换需要同时改身份标签、reconcile、客户端识别与销毁提交。
- 生物的死亡目前走原版死亡，没有接升仙记录的销毁提交，也没有骨骼 death 动画。
- 存档重启后血池（内存）会重置。

产物：`build/libs/shengxian-0.3.72.jar`，SHA256 `adadab748c769ec392e93dce3c0ed884546f1584b6246098740ca1b253266f97`，Framework 209 通过。
