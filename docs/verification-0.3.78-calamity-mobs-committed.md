# 0.3.73–0.3.78 生物 tick 修复 + 正式载体切换为生物

日期：2026-11-02。接续 `verification-0.3.72-calamity-mobs.md`。

## 一、生物 tick 不触发：根因与修复

现象：生物能生成、能被 `/damage` 打、能渲染，但**从不出招**（没有 `Calamity strike` 日志）。

根因：攻击循环只挂在 `EntityTickEvent.Post` 的分支上，该分支对这批生物没有实际推进（此前的实体 tick 路径不可靠）。

修复：
1. 新增 `TribulationWorldService.tickMobileCarriers`，在 `ServerTickEvent.Post` 里按玩家周边 96 格扫描灾劫生物并推进，不再依赖实体自身 tick 事件。
2. `CalamityCombatService.tick` 增加同一 game tick 幂等保护（`lastTick == now` 直接返回），避免两条驱动路径重复推进。
3. 保留每 100 tick 的诊断日志，便于以后确认。

验证日志（0.3.74 实机）：
```
Calamity strike CHIME_SINGLE from 483120ce... hit 0 target(s) for 3000.0 damage
Calamity strike GROUND_ROAR   from 16ac2783... hit 0 target(s) for 3000.0 damage
Calamity strike CHIME_DOUBLE  from 483120ce... hit 0 target(s) for 3000.0 damage
Calamity strike CLAW_POUNCE   from 16ac2783... hit 0 target(s) for 6000.0 damage
```

## 二、正式载体切换为生物

- `TribulationWorldService.spawn` 现在创建 `shengxian:calamity_carrier` 生物，写入变体、身份标签与带血量的名牌，替换原来的隐形盔甲架。
- 所有载体相关判定泛化为 `Entity`：`isCarrier`、`matches`、`carrierTypeOf`、`carrierOwner`、`carrierTypeFromTags`、`emitAura`、`strikeInnerByOwner`、`onAttackEntity`、`onIncomingDamage`、`onEntityInteract`、`synchronize`。
- 旧存档里的盔甲架载体仍然兼容（`reconcile` 接受两种形态），不做强制迁移。
- 灾劫生物免疫原版伤害：所有伤害走血池结算，摧毁时提交一次 `strikeTribulation(Integer.MAX_VALUE)`，避免重复致死。
- 血池与实体属性血量同步（`damageCarrier` 同时写 `LivingEntity#setHealth`），名牌与实体血量一致。
- 新增测试入口 `/shengxiantest committed <tiger|chimes|beast>`，走**正式生成路径**（含身份标签）。

## 三、实机验证

- `/shengxiantest committed tiger` → `已按正式路径生成载体 独角峥虎地劫，实体类型 calamity_carrier，生命 1000000.0`。
- 该载体按节奏出招并**命中渡劫者**：`GROUND_ROAR ... hit 1 target(s) for 3000 damage`。
- `/shengxiantest hit 400000` → 全部在范围内的载体被摧毁，日志 `Calamity carrier ... was destroyed`，随后 `execute if entity @e[type=shengxian:calamity_carrier]` 返回 Test failed（已无载体）。
- 最终构建 0.3.78 启动无异常，Framework 209 通过。

## 四、仍未完成

- 摧毁时的死亡表现仍是爆炸粒子 + 音节，未接模型自带的 death 动画。
- 血池只在内存中：服务器重启会恢复满血（持久单位不变）。
- 旧盔甲架载体没有自动迁移成生物，只在新建/重建时使用生物。
- 客户端 `CalamityArtRenderer` 仍会为旧盔甲架渲染模型；生物由自己的 `EntityRenderer` 渲染，两者不会重复。
- 预警环仍未取得清晰截图（服务端尘埃粒子，代码在跑）。

产物：`build/libs/shengxian-0.3.78.jar`，SHA256 `00b82e7c16a439af333da83b3b4e62fd6e54aa1d1569fda203673d6e6a39bd10`。
