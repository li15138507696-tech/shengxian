# 0.3.24 验证记录：三处缺陷修复

日期：2026-10-30。基线 0.3.23。普通升仙入口继续关闭。

## 修复

| # | 缺陷 | 位置 | 处理 |
| --- | --- | --- | --- |
| 1 | 已过时 API：`LevelReader#hasChunkAt(BlockPos)` 在 1.21.1 已弃用，构建时产生 deprecation 警告 | `JiangShanRuGuService:176` | 改为 `level.isLoaded(pos)`，语义相同（区块是否已加载） |
| 2 | 催动失败时玩家会收到**两条**消息：一条具体原因，一条通用的“仙蛊操作失败，记录已保留” | `XianGuService.use` + `XianGuCommand` | 新增 `Outcome.REJECTED`（空消息）；命令遇到空消息不再输出，只保留具体原因 |
| 3 | 目标从未被采样过时，人如故误报“跨维度后需要重新记录伤势” | `RenRuGuService.use` | 先判空：无记录时报“尚无近期伤势记录”，只有维度真的变了才报跨维度 |

第 2 条的实测依据：用 `/execute as FlowBot run shengxiangu use ding_xian_you` 触发一次前置条件失败，玩家先收到“仙蛊：仙蛊要求已成仙窍且本体至少六转”，紧接着又收到“仙蛊操作失败，记录已保留。”——两条消息互相矛盾。

第 3 条的实测依据：`DIMENSIONS.get(uuid)` 为 null 时，`"...".equals(null)` 恒为 false，代码会走进跨维度分支并清空历史。

## 验证

```
& ./build.ps1 -Action Test                     → 199 passed, 0 failed
& ./build.ps1 -Action Build -ModVersion 0.3.24
  → 8 个蛊钩子、70 项爆炸/资源、6 类回执、237 个 common-side 类
  → SHA256 2dddccd0b63b826e8110b6d336d3700a98414fab28c96613da765ba87fd76fd5
```

编译输出里的 deprecation 警告已消失（仅剩测试夹具的日志行）。

产物：`build/libs/shengxian-0.3.24.jar`。

## 隔离专用服务器

`shengxian (version 0.3.23 -> 0.3.24)`，`Done (2.102s)!`，shengxian 相关 ERROR/异常 0 条。

## 未验证 / 未修复

- 三条修复中只有第 1、3 条可静态确认；第 2 条的实际观感需要在客户端里再触发一次失败才能肉眼确认（本轮用命令触发了失败，但改后未再跑客户端）。
- 之前发现但未处理的测试数据问题：FlowBot 的会话是 IMMORTAL，本体转数仍为 5，登录时 `reconcileRankSix` 会保留待办意图。这是夹具漂移，不是产品缺陷；要让三只仙蛊真正可催动，需要先把该角色的本体转数恢复为 6。
- 三只仙蛊的实际效果（传送/治疗/地形还原）、反噬语义、爆炸掉落复原仍未实机验收。

## 夹具修复尝试（未成功，记录原因）

目标：把 FlowBot 的本体修为补到六转，好让三只仙蛊的前置条件成立。

实测结果：

- `/shengxian status` 显示 **本体转数=0.0，阶段编码=0.0**——该角色的本体变量从来没被写过，不是“5 转”，而是空。
- `/shengxian sync FlowBot` 拒绝：`六转修复失败：本体修为与待恢复记录不符，或已执行后发生降转；保留现状，不重复升转`。这是设计正确的行为（不会替玩家伪造修为）。
- `/data merge entity FlowBot {"neoforge:attachments":{"guzhenren:player_variables":{zhuanshu:6.0d,jieduan:5.0d}}}` 返回 `Unable to modify player data`——原版禁止对在线玩家做 `/data` 修改。
- 本体没有注册任何修为命令（`guzhenren`/`gzr`/`xiuwei`/`jieyan` 全部 Unknown command）。

结论：要让仙蛊真正可催动，只能**停服后编辑离线 `world/playerdata/<uuid>.dat`**（gzip NBT），或让角色走一遍本体自己的修炼流程。本轮没有做这件事：它属于测试夹具工作，不是产品缺陷，且直接改玩家存档有风险。

因此三只仙蛊的效果仍未实机验收。
