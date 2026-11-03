# 纳气期蛊反噬离线验证

日期：2026-10-29。源码增量未升版、未打包。

## 实现范围

- 只在服务端 `ABSORBING` 会话内，对已核实并确认完成的催动加压力；默认阈值 8，配置范围 1–64，阈值按 session 冻结。
- 达到阈值只处理触发当次的蛊一只。普通背包按 `ItemStack` 对象身份定位；杀招蛊使用核实的 `ShaZhao1..4_GuChong1..10` 来源槽。缺失、重复、已被上游消耗或槽位被替换时不选择其他目标。
- 本命蛊保护要求绑定内容指纹与上游 `PlayerVariables.BenMingGu` 镜像一致；内容指纹相同的副本一并受保护。
- 玩家压力、目标数量、事务收据和事务阶段位于同一玩家持久 NBT。损失前写入并核验 `PREPARED`，减 1 后写入并核验 `COMMITTED`；未完成事务、保存不确定、双存档不一致在登录时隔离，不补做损失。
- 只有 8 个已核实观察目标参与：月光蛊、白象蛇蛊、哀别持、定蛊二号、C/V/X/Z 四个杀招过程。普通升仙 readiness 与 `GU_BACKLASH` 能力门禁保持关闭。

## 执行结果

在 `D:\daima\mcmod\升仙` 执行：

| 命令 | 结果 |
| --- | --- |
| `pwsh -NoProfile -File .\build.ps1 -Action Test` | FrameworkTest 188 项通过，0 失败 |
| `pwsh -NoProfile -File .\build.ps1 -Action NbtCheck` | 通过：rank、failure、tribulation impact、Gu backlash 分别 22、22、22、31 项压缩 NBT 检查；Gu 钩子 8 个静态目标通过 |
| `pwsh -NoProfile -File .\build.ps1 -Action SourceCheck` | 完整源码编译通过；相同的 8 个精确 hook 与 97 项压缩 NBT 检查通过 |

Gu 回执回归另外验证玩家 UUID、session/sequence、PREPARED→COMMITTED 只向前推进、同序号状态不可替换、UNCERTAIN 事务不可被新 session 覆盖、事务与最新事件关联、精确背包和杀招槽计数、受保护目标计数不变、玩家文件与单人 host `level.dat/Data/Player` 的一致性，以及缺失/损坏存档拒绝确认。新增的同序号替换与未决事务替换测试先观察到 RED，再在写入单调性收紧后通过。

hook 验证绑定的上游内层 JAR SHA256：

`b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc`

## 验证边界

本轮没有启动 Minecraft 客户端、单人世界、集成服或专用服务器。源码与压缩 NBT 回归不能证明真实右键、攻击、方块互动、杀招效果、客户端背包同步、多玩家并发、重登或崩溃过程。8 个静态 hook 只覆盖已审计入口；其余本体催蛊入口仍不累计、不处罚。`AscensionReadiness.implementedCapabilities()` 仍为空集。
