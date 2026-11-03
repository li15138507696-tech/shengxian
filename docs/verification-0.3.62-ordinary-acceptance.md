# 0.3.62 普通升仙实机验收

日期：2026-11-02。验收对象是固定产物 build/libs/shengxian-0.3.62.jar（SHA256 cf886f8dfee135a1dd47679a3b3153c84e7364f79725f2394338060464247db8），不是工作区后续可能变化的源码。

## 环境

- 全新隔离世界 ordinary-c3-world，NeoForge 21.1.263，Minecraft 1.21.1，Java 21。
- 上游蛊真人本体、天意 3.14.0、LDLib2、Photon；真实月光蛊物品 guzhenren:yue_guang_gu，custom_data.gc=guzhenren:yue_dao。
- 服务端仅在隔离世界显式设置 apertureLedgerOnly=true，因为 UPSTREAM-010 没有本体仙窍/仙元字段；普通灾劫 profile 保持 UNRESOLVED_ORDINARY。
- 测试角色只在升仙前写入五转、阶段四、真元 500000/500000 和本命蛊镜像；没有改写任何升仙阶段、仙窍结果或成功回执。
- 客户端为匹配 0.3.62 的真实客户端，命令由服务端 RCON 以该在线玩家身份执行；不是离线模拟器。

## 成功链路（实际命令）

| 阶段 | 结果 |
| --- | --- |
| 资格 | 转数=5.0，阶段=4.0，资格=可发起，真元=500000/500000 |
| prepare | PREPARING，绑定真实月光蛊镜像 |
| shatter | ABSORBING，真元 500000→450000，消耗 50000（最大值的 10%） |
| 纳气 | 立即设置 0.375/0.375/0.5，求知=false；三气同步 1.5/1.5/1.5/步，风险始终 0 |
| 目标 | 27.0/27.0/27.0，外部=3.0/3.0，储备=63.0，无灾劫 |
| open | OPENING |
| offer | FORMING；背包真实月光蛊被消耗，托管状态 STORED |
| form | IMMORTAL；仙窍面积=5,694,459 亩，时间比=15.0，仙元=26.0 |
| 本体同步 | zhuanshu=6.0，jieduan=1.0，真元=∞/∞ |
| 仙窍进出 | enter 后 Dimension=shengxian:aperture，Pos=0.5,65,0.5；leave 回到 minecraft:overworld |
| 本命蛊持久化 | benminggu.properties：fingerprint 匹配，state=EMBEDDED，物品为真实 guzhenren:yue_guang_gu + gc=guzhenren:yue_dao |
| 重登 | IMMORTAL、面积 5,694,459、仙元 51.2（内部时钟 4200）、本命蛊 EMBEDDED 保持；补全测试角色 created 标记后 zhuanshu=6.0、jieduan=1.0 保持 |

## 失败路径（真实发生，未美化）

第一次运行在碎窍后为检查数据停留了约两分钟。环境三气从 3/3 回生到 51/51、120/120，普通灾劫按当前规则生成 JADE_WILLOW_WIND 和 SINGLE_HORNED_TIGER，冲击把角色打到 Health=0.0，随后 open 被挡住。该世界保留为 failure 证据。

结论：普通路线不是“碎窍后可以无限挂机”。及时纳气并在达标后立刻开窍可以避开这次外劫；若故意保留环境气，灾劫会真实生成并造成致命伤害。

## 测试脚手架缺陷与修复

首次重登后本体转数回到 0.0、阶段 0.0，而仙窍会话仍是 IMMORTAL。检查发现测试角色 guzhenren_library:character.created=0，是离线前置写入只设置了转数、没有把上游角色标记为已创建，重登时被上游按未初始化角色重置。

把该测试角色的 created 标记补为 true，并只恢复流程已经产生的 6.0/1.0 后再次重登，六转、阶段、仙窍、仙元与本命蛊均保持。这个修复只用于修正测试前置数据，不能作为“未初始化真实玩家也会保留六转”的证明；真正的玩家创建流程仍需单独验收。

## 视觉验收

0.3.60 之后的实机截图确认：原版方块云不再把新云壁切成大块缺口；默认主路径已移除硬质三色管、悬空圆环和盆状山脊。实机画面见 build/visual-review-20261102/screenshots/storm-fixed-wide.png 和 storm-fixed-upward.png。

但云流细节仍偏软、偏糊，未达到用户此前的“有形状、有体积、有质感、宏大”最终要求。该美术方向仍是评审版，不能因为编译、着色器或流程通过就宣称视觉定稿。

## 未覆盖

- 未由真实玩家创建流程从零养成五转巅峰；前置五转和本命蛊由隔离测试写入。
- 未验证多人同场、真实存档迁移、第三方光影、Fabulous 模式和低端显卡性能。
- 一般普通灾劫目录仍不是完整原著规则；本轮只验证了实际生成的两种普通灾劫与致命失败路径。
- 未修复首次成仙后角色在一次外劫中死亡的问题；正确节奏可避开，但“成仙后立即无敌”不是本模组承诺。
