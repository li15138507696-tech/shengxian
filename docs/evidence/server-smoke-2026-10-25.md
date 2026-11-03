# 隔离服务器冒烟记录（2026-10-25）

## 环境

- 目录：D:/daima/mcmod/升仙/build/test-server
- NeoForge：21.1.263，installer 安装，退出码 0
- Java：21.0.7
- mods：用户给定外层本体 10.20.jar（120,223,219 字节）与本项目 shengxian-0.2.0.jar
- 启动：java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.263/win_args.txt nogui

## 实际结果

- 模组列表包含 guzhenren 0.1、guzhenren_library 0.1、shengxian 0.2.0；外层 jarjar 的 Pehkui/GeckoLib/PlayerAnimator/Prickle/Bookshelf/AttributeFix 一并加载。
- 日志：Done (5.272s)! For help, type "help"
- 日志：shengxian session store opened at .\world\.\shengxian\sessions
- RCON 127.0.0.1:25575：
  - /shengxian status -> 框架已加载、世界会话仓库路径、门槛关闭
  - /execute in shengxian:aperture run time query gametime -> The time is 83
  - /execute in shengxian:aperture run forceload add 0 0 -> Marked chunk [0, 0] in shengxian:aperture to be force loaded
  - /execute in shengxian:aperture run setblock 0 64 0 minecraft:stone -> Changed the block at 0, 64, 0
  - /shengxian aperture status（控制台）-> 只能由玩家执行
  - /stop -> Stopping the server；保存日志含 shengxian:aperture 与 All dimensions are saved
- 服务器退出码 0。

## 边界

- 没有玩家连接：进入/离开传送、返回点、地块自动建造、在线内部时钟 tick、护道人来访、死亡/重连/区块卸载均未验证。
- build/test-server 是隔离测试产物，不进入发布 JAR，也不代表真实服务器部署。
- 本体仅在隔离测试 mods 中使用；未修改给定 JAR 或真实实例。
- 最终产物 22f6f87e 重启后 /shengxian status 与 /execute in shengxian:aperture time query 通过；/shengxianfixture 已移除（Unknown command）。
- 玩家连接：mineflayer 4.39.0 被 NeoForge 以 vanilla client not supported 拒绝；FakePlayer 传送 NullPointerException: Connection.channel() null。传送/进入未验证。

## 真实客户端玩家流程（隔离夹具）

- NeoForge 21.1.263 客户端以 FlowBot 连接；仙窍会话由隔离夹具写入（FORMING，518/16/22/plot1），不是真实升仙。
- enter -> Pos [256.5,65,0.5]，Dimension shengxian:aperture；status 518/16/22/internal 0。
- 随后 status -> essence 47.6、internal 2560。
- leave -> Pos [-9.5,73,-7.5]，Dimension minecraft:overworld；aperture-space.properties 记录 built.1 与返回点。
- 结论：空间进入/离开/传送/返回点/在线 tick 已实机验证；碎窍/纳气/放蛊/灾劫/护道未验证。

## 真实客户端三气控制

- 隔离夹具 ABSORBING：气 30/30/30，外部 20/20，储备 100，修订 4。
- FlowBot 联机后 /shengxianqi 0.5 0.5 0.5 true；4 秒后修订 8、气 38/38/36、风险 1.83、窗口 4。
- 停止后修订 14、气 50/50/45、风险 4.05、窗口 10。
- 结论：命令接口可设置受限意图并由服务端步进；不是 GUI，也不是真实碎窍/纳气。
