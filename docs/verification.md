# 升仙框架验证记录

最新验证记录：`docs/verification-0.3.8.md`。普通升仙入口仍关闭；0.3.8仅执行离线代码与着色器检查，未启动Minecraft或服务器。以下为更早版本记录。

2026-10-25，版本 **0.2.0**。本记录覆盖阶段 1-6 的部分成果；真实升仙入口仍关闭。已在隔离 NeoForge 服务器验证模组加载、命令与维度注册；玩家流程、传送与 tick 尚未验证。

## 1. 本轮实际执行的命令与结果

| 命令 | 工作目录 | 结果 | 退出码 |
| --- | --- | --- | --- |
| `& ./build.ps1 -Action Test`（改动前基线） | `D:/daima/mcmod/升仙` | 16 passed / 0 failed | 0 |
| `python build/scan_fields2.py` | 同上 | 22,371 class 全部解析；31,671 字段访问；0 解析错误 | 0 |
| `& ./build.ps1 -Action Test`（schema 5 后） | 同上 | 74 passed / 0 failed | 0 |
| `& ./build.ps1 -Action Build` | 同上 | 测试通过、编译、打包、结构校验通过 | 0 |
| `& ./build.ps1 -Action Verify` | 同上 | 产物校验通过 | 0 |

- 依赖：Minecraft 1.21.1、NeoForge 21.1.263、Java 21、110 个本地 jar；指纹见 `build/dependency-evidence.txt`。
- 产物：`build/libs/shengxian-0.2.0.jar`，124,527 字节。
- SHA256：`dc18647ef80873d12a0306bb7f414055f5ca2d5fd5aee181a78d12231a294895`。
- 产物旁 `.sha256` 与实际文件一致；JAR 只含 `net/guzhenren/shengxian/` 的 class 与 `META-INF/neoforge.mods.toml`，不含任何依赖 JAR 或外部 class。
- metadata 版本 `0.2.0`，与 `build.gradle`/`build.ps1` 产物名一致。

## 2. 本轮修复的真实问题

### 2.1 Windows PowerShell 5.1 读取无 BOM 的 UTF-8 脚本

- 现象：直接执行 `build.ps1 -Action Build`，在 `Prepare-Classpath` 第 45 行报"找不到属性 Hash"，原因是脚本无 BOM 时 Windows PowerShell 5.1 按 ANSI 读取，默认外层 JAR 路径中的中文被破坏，`Get-FileHash` 返回空。
- `pwsh`（PowerShell 7）在本机不存在，不能把"用 pwsh 就没问题"当成修复。
- 修复：把默认外层路径改成纯 ASCII 的 `[char]` 码点拼接；文件另存为 UTF-8 BOM。现在即使编辑器再次去掉 BOM，路径也不依赖脚本编码。
- 验证：`-Action Build` 与 `-Action Verify` 均退出 0。

### 2.2 状态模型不能保存同阶段更新

- 旧`AscensionSession`把 `revision` 同时当阶段转换次数和保存版本，并用 `(revision-minimum)%2` 限制可达阶段；旧仓库每次保存强制经过 `SessionFlow.advance`，所以 ABSORBING 期间每刻更新无法保存。
- 修复：拆成 `phaseRevision/stateRevision/lastActionSequence/serverTick`，另加 `effectKey` 幂等账本；同阶段更新只增 `stateRevision`；阶段图奇偶性只约束 PREPARING/BREAKING（以及未碎的 FAILED）。
- schema 1 迁移：`phaseRevision=stateRevision=revision`、`lastActionSequence=sequence`、`serverTick=0`、进度为空；只有成功保存时才写成 schema 2。
- 保护目标保留：碎窍不可逆、双空窍隔离、坏档/未来 schema 不覆盖、陈旧写入拒绝、名称大小写与 Unicode 文件名。

## 3. 测试覆盖（74 项）

原有 16 项保护目标全部保留并适配到五身份（阶段顺序、不可逆、失败终止、陈旧 revision/序号、非法阶段标记、StartGate、profile 不可变、双空窍、冲突写、大小写碰撞、64 字 Unicode、未来 schema、坏 broken、非法转义、不可达进度）。

新增 13 项：

1. 同阶段更新持久化，phaseRevision 不变、stateRevision +1。
2. 同阶段更新拒绝陈旧 stateRevision 与重复动作序号。
3. serverTick 不能倒退，允许同 tick 不同序号。
4. effectKey 重复拒绝；仓库不允许账本缩小。
5. schema 1 迁移后保存为 schema 2。
6. 非法 schema 1 保留原字节、不能覆盖。
7. schema 2 拒绝 `stateRevision < phaseRevision`。
8. 终态不能同阶段更新。
9. 三气 NaN/负数/超上限拒绝（构造与读取）。
10. schema 1 的 sequence 不再强制大于 revision（身份解耦）。
11. 创建可带 serverTick，但不能夹带进度/幂等键。
12. ABSORBING 连续 3 个 tick 更新、保存、重开，phase 不跳、序号/时钟/气量/幂等键正确。
13. schema 1 PREPARING 记录迁移为空准备状态。
14. 服务层在门槛关闭时拒绝创建会话。
15. 服务层拒绝重复创建会话。
16. 服务层 effectKey 跨重启幂等。
17. 服务层推进阶段时携带进度，且不能绕过阶段图。
18. 服务层拒绝陈旧 expected revision。
19. 世界会话缓存重启后仍从仓库恢复，evict/flush 不丢已保存记录。
20. 适配器能力为空时 SessionStore 拒绝创建会话且不落盘。
21. SessionStore 的变更仍受 stateRevision CAS 约束。
22. schema 2 记录迁移到 schema 3，risk/window 取空值且 qi/领悟/幂等键保留。
23. 人气释放只能消耗有限储备，耗尽后重复操作不产气。
24. 人气/天气/地气三种偏重分别累积到致命。
25. 平衡时风险衰减并开启交感窗口。
26. 同一窗口只授予一次领悟；窗口关闭后重开才可再次授予。
27. 求知的分心风险上升，但气机不被暂停。
28. 全零气机不会免费衰减风险。
29. risk/window 随仓库重启保存与恢复。
30. 服务层 ABSORBING 步进持久化领悟，重开仓库仍成立。
31. 致命步进在同一次保存中提交 FAILED，之后拒绝继续模拟。
32. schema 3 记录迁移到 schema 4，灾劫取空值且风险/窗口保留。
33. 灾劫只在二气浓度达到阈值时开始，并区分音磬/荒兽载体。
34. 灾劫每次冲击累加风险，达到阈值可致命。
35. 护道人击破载体不改变气机/风险，存活只记一次、无掉落。
36. 活动灾劫的载体状态随仓库重启保存。
37. 服务层同一 tick 合并三气与灾劫，并持久化。
38. 灾劫致命冲击在同一次保存中提交 FAILED。
39. schema 4 记录迁移到 schema 5，仙窍取空值且灾劫保留。
40. 仙窍只形成一次，面积/时间比/仙元/地块保留。
41. 内部时钟按时间比推进，仙元产出可支出。
42. 未形成仙窍不能支出仙元。
43. 已形成仙窍随仓库重启保存。
44. 仙窍地块建造记录跨重启保存。
45. 非法地块记录被拒绝并保留。
46. 仙窍返回点在重启后保留。
47. 非法返回点记录被拒绝。

48. 服务只在 FORMING 形成仙窍并一次提交 IMMORTAL；非 FORMING、重复形成、活动灾劫被拒绝。
49. 本命蛊托管首次绑定并跨重启保留。
50. 同一本命蛊重复绑定幂等，不同本命蛊被拒绝。
51. 非法本命蛊托管记录被拒绝。
52. schema 5 记录迁移到 schema 6，本命蛊取空值。
53. 仙窍规划按原著面积档位生成，档位由领悟数选择。
54. 仙窍地块预留唯一、跨重启保留，释放后可复用。
55. 仙窍来访名单跨重启保留并可撤销。
56. 非法来访名单记录被拒绝。
57. 护道人名单跨重启保留并可撤销。
58. 非法护道人名单记录被拒绝。

## 4. 未执行 / 未验证

- 隔离 NeoForge 21.1.263 服务器已实际启动并停止；模组加载、/shengxian status、维度 shengxian:aperture 注册、forceload/setblock 已通过。真实客户端 FlowBot 已连接并验证空间流程；真实碎窍/纳气/放蛊未验证。
- 已实现：`SessionStore` 世界目录缓存、`AscensionServer` 在 `ServerStartedEvent` 绑定 `<world>/shengxian/sessions`、停服清空、玩家登出 evict、命令读取会话状态。
- 本命蛊托管账本已实现（只读指纹、一次性/幂等/拒绝不同蛊）；真实物品唯一来源、放蛊、受控本体写入和真实碎窍仍未完成。
- **未安装到真实游戏实例、未修改真实存档、未修改本体 JAR**。
- 未接服务器世界存档生命周期；`FileSessionRepository` 只在测试临时目录使用。
- 已实现纯 Java 三气步进（`QiEngine`）、失衡风险、交感窗口与领悟去重、schema 3 持久化。
- 已实现灾劫领域原型（`TribulationEngine`）：浓二气触发、音磬/荒兽载体、冲击风险、护道人击破、存活幂等标记、schema 4 持久化；**没有** Minecraft 载体实体、区块加载、可见特效或掉落。
- 已实现仙窍账本（`ApertureEngine`）：面积/时间比/仙元/内部时钟/地块，形式化一次性；schema 5 持久化；**没有**独立维度、传送、建造、权限入口。
- 已加入 data-driven 维度 shengxian:aperture（空 flat 生成）、地块建造记录、/shengxian aperture enter|leave|status；JAR 已包含维度 JSON。
- 已实机验证维度注册与维度内 forceload/setblock；传送、玩家建造、返回点、tick 与区块恢复未验证。
- 尚未接入玩家动作协议或可视界面。
- 未实现灾劫、师法自然知识池、仙窍、仙元、仙蛊、客户端操作；`IMMORTAL` 仍是领域枚举。
- Gradle 入口未运行：本机 `gradle` 不在 PATH；本轮只验证 `build.ps1`。不声称两条构建通道都通过。
- 中文原站正文不可读（403）；原著证据基于日文全译本对应章节，标记为"原著已核·译本"，待核项见 `docs/evidence/canon-rules.md`。

## 5. 可复现构建

- 连续两次 `-Action Build` 使用同一源码，产物 SHA256 均为 `dc18647e…`（上一轮为 `6b94caae…`，本轮新增返回点与内部时钟 tick后重新计算），说明 `jar --date` 消除了条目时间戳差异；仍不承诺跨 JDK 版本逐字节一致。
- 构建时间戳固定为 2026-01-01T00:00:00Z，仅影响 JAR 条目元数据，不改变行为。

## 6. 产物与版本一致性

- 版本：`0.2.0`（`build.gradle`、`neoforge.mods.toml`、`build.ps1` 校验、README 一致）。
- 文件名：`shengxian-0.2.0.jar`。
- 校验：以上 SHA256 为本轮实际 Build 生成；未沿用旧日志。
- 本体未嵌入产物；内层仅缓存在 `.cache/`。
## 7. 隔离 NeoForge 服务器实机验证（本轮新增）

环境：D:/daima/mcmod/升仙/build/test-server，NeoForge 21.1.263 安装器生成；只复制了本项目 0.2.0 产物与用户给定外层本体到 mods。没有修改真实游戏实例或真实存档。

- 安装：java -jar neoforge-installer.jar --installServer .，退出码 0。
- 启动：java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.263/win_args.txt nogui。
- 模组列表实际包含：guzhenren 0.1、guzhenren_library 0.1、shengxian 0.2.0，以及外层 jarjar 的 Pehkui/GeckoLib/PlayerAnimator/Prickle/Bookshelf/AttributeFix。
- 启动日志：Done (5.272s)! For help, type "help"；随后 shengxian session store opened at .\world\.\shengxian\sessions。
- RCON 实际执行（127.0.0.1:25575）：
  - /shengxian status -> 返回框架版本、世界会话仓库路径、门槛关闭；没有异常。
  - /execute in shengxian:aperture run time query gametime -> The time is 83。证明维度已注册且可执行。
  - /execute in shengxian:aperture run forceload add 0 0 -> Marked chunk [0, 0] in shengxian:aperture to be force loaded。
  - /execute in shengxian:aperture run setblock 0 64 0 minecraft:stone -> Changed the block at 0, 64, 0。
  - /shengxian aperture status（控制台）-> 只能由玩家执行；命令的玩家校验生效。
  - /stop -> Stopping the server；保存日志包含 Saving chunks for level 'ServerLevel[world]'/shengxian:aperture 与 All dimensions are saved，退出码 0。
- 结论：**模组加载、命令注册、世界目录会话仓库、data-driven 维度注册与维度内方块操作已实机验证**。
- 玩家连接尝试：mineflayer 4.39.0 以 1.21.1 连接被 NeoForge 拒绝，提示 vanilla client not supported；没有 NeoForge 客户端握手。
- FakePlayer 夹具尝试直接调用 ApertureSpace.enter：传送时 NullPointerException，Connection.channel() 为 null；该夹具命令已从最终 JAR 移除。因此进入/离开传送、返回点、地块自动建造、在线 tick、护道人来访、死亡/重连/区块卸载仍未实机验证。
- build/test-server 与其中的世界/本体副本属于隔离测试产物，不进入发布 JAR，也不代表真实服务器部署。

## 8. 真实 NeoForge 客户端玩家流程（隔离夹具仙窍）

- 客户端：同一 NeoForge 21.1.263 客户端，gameDir build/test-client，--quickPlayMultiplayer 127.0.0.1:25565，username FlowBot，offline UUID 9083676c-a7d5-34cc-93d7-4a181541898d。
- 服务端：build/test-server，mods 含给定本体与 shengxian 0.2.0（dc18647e）。
- 仙窍来源：不是玩家碎窍/纳气/放蛊，而是隔离夹具通过 FileSessionRepository 写入 FORMING 会话：area=518、ratio=16、essence=22、plot=1。它只验证空间能力，不代表升仙流程完成。
- RCON 实测：
  - /execute as FlowBot at FlowBot run shengxian aperture enter -> 已进入仙窍。
  - /data get entity FlowBot Pos -> [256.5d, 65.0d, 0.5d]。
  - /data get entity FlowBot Dimension -> "shengxian:aperture"。
  - /execute as FlowBot run shengxian aperture status -> 面积 518、时间比 16、仙元 22、内部时钟 0、地块 1。
  - 随后再次 status -> 仙元 47.6、内部时钟 2560，证明在线 tick 推进。
  - /execute as FlowBot run shengxian aperture leave -> 已离开仙窍；Pos [-9.5, 73.0, -7.5]，Dimension "minecraft:overworld"。
  - world/shengxian/aperture-space.properties：built.1=true；return.9083... = overworld/-9.5/73.0/-7.5。
- 结论：真实客户端进入/离开、传送、返回点、地块建造标记、在线内部时钟/仙元推进已实机验证。
- 未验证：真实碎窍/纳气/放蛊、交感窗口、灾劫、护道、死亡/重连/停服重启后的玩家流程。

## 9. 真实客户端三气控制（命令接口）

- 会话：隔离夹具 ABSORBING，初始 qi=30/30/30、ambient=20/20、reserve=100、stateRevision=4；不是真实碎窍/纳气/放蛊。
- FlowBot 通过 NeoForge 客户端联机后，RCON 以玩家身份执行：
  - /execute as FlowBot run shengxiansim -> 阶段=ABSORBING、修订=4、气=30/30/30、外部=20/20、储备=100、风险=0/0/0、窗口=0。
  - /execute as FlowBot run shengxianqi 0.5 0.5 0.5 true -> 调气意图已设置。
  - 4 秒后 shengxiansim -> 修订=8、气=38/38/36、外部=18/18、储备=94、风险=1.83/1.83/1.83、窗口=4/w6649、领悟=0。
  - /execute as FlowBot run shengxianqi stop，随后 shengxiansim -> 修订=14、气=50/50/45、外部=15/15、储备=85、风险=4.05/4.05/4.05、窗口=10/w6649。
- 结论：真实客户端可以设置受限三气意图，服务端每秒步进三气；客户端不提交绝对气值，也没有总成功率。
- 限制：这是 /shengxianqi 命令接口，不是 GUI；会话是隔离夹具。真实碎窍/纳气/放蛊、交感窗口闭合、灾劫、护道仍未验证。

## 10. 客户端三气界面与网络载荷（已编译，未视觉验收）

- 新增 keybind：K 打开三气控制界面（key.shengxian.qi）。
- 界面：天/地/人三个 0..1 输入框、师法自然开关、发送、停止；Enter 发送，Esc 关闭。
- 网络：QiIntentPayload（double/double/double/boolean/boolean）通过 playToServer 注册；服务端再次校验 0..1，非法值丢弃。
- 构建：客户端类、lang、payload 均打入最终 JAR；服务端与客户端均以该 JAR 启动并成功联机。
- 未验证：受 Windows 前台窗口限制，自动化无法把 Minecraft 窗口切到前台，K 键未实际按下，界面未截图、未发送过网络包。因此不能宣称 GUI 已通过。
- 已通过的命令路径仍有效：/shengxianqi 与 /shengxiansim 的三气步进见第 9 节。

### 复验：界面渲染与载荷路径（测试开关）

- 使用 -Dshengxian.testScreen=true 启动测试客户端，客户端自动打开界面；PrintWindow 截图成功（build/mc-ui.png）：标题 Three Qi Control、三个 0.5 输入框、师法自然开关、发送、停止。
- 同一测试开关在 60 tick 后发送 QiIntentPayload(0.6, 0.4, 0.5, true)：
  - 发送前：修订 14、气 50/50/45、风险 4.05、窗口 10。
  - 发送后：修订 17、气 57.2/54.8/49.5、风险 5.16、窗口 13。
- 结论：界面能渲染；客户端 payload 能到达服务端并改变三气。最终 JAR 已移除测试开关。
- 仍未验证：真实鼠标点击“发送”、键盘 Enter、输入框编辑；PostMessage 鼠标/键盘注入未被 GLFW 接受。因此“按钮/按键交互”仍未验收。

## 11. 形成服务与可见特效（本轮）

- AscensionService.formAperture：仅 FORMING、无活动灾劫、plot >= 0 时形成仙窍，并在同一次保存中提交 IMMORTAL；重复形成、非 FORMING、活动灾劫均拒绝。测试 48。
- 新增 FxPayload（server -> client）与 AscensionFx：灾劫开始/冲击/致命、每次三气步进、仙窍进入/离开会向附近 64 格玩家发送视觉 cue。
- 客户端 ClientFx：原版粒子（ELECTRIC_SPARK/EXPLOSION/PORTAL/END_ROD/REVERSE_PORTAL/DUST）、声音（雷/信标/凋灵）与全屏叠色；不是 Veil 着色器包。
- 实机诊断构建（含临时 stderr）在客户端日志中确认：
  - SHENGXIAN_FX_PACKET tribulation_start
  - SHENGXIAN_FX_PLAY tribulation_start
  - 后续 SHENGXIAN_FX_PACKET qi / SHENGXIAN_FX_PLAY qi
  证明 payload 到达并进入客户端渲染函数。最终 JAR 已移除这些诊断输出。
- 截图：build/mc-fx2.png（叠色在夜间场景中较暗，只作辅助证据）。
- 未验证：Veil 4.5.2 着色器包、多人同步边界、长时间性能、真实碎窍/纳气/放蛊触发链。

## 12. 本命蛊唯一托管账本（本轮）

- 新增 BenMingGuStore：世界目录 shengxian/benminggu.properties，按 owner 保存 fingerprint + displayName。
- fingerprint = SHA-256(物品注册名 + 数据组件 patch)，只读取 GuzhenrenReadOnlyBridge 返回的 ItemStack 副本，不移动/消耗/改写本体物品。
- 首次绑定成功；同一 fingerprint 幂等；不同 fingerprint 明确拒绝；坏记录拒绝加载。
- 新增 /shengxianbind 玩家命令：读取本体 BenMingGu 镜像，绑定并返回短指纹。
- 测试 49-51。
- 已接入形成门禁：formAperture 要求会话已绑定本命蛊；/shengxianbind 同时写托管账本与会话。真实放蛊、物品唯一来源和受控本体写入仍未完成。

## 13. schema 6：本命蛊进入会话并成为形成门禁（本轮）

- schema 6 新增 vitalGuFingerprint / vitalGuName；schema 1/2/3/4/5 读取时按明确映射迁移，5→6 取空本命蛊，保存时才升级。
- AscensionProgress.VitalGu：空或 64 位小写 hex fingerprint；绑定后不能替换；requireMonotonicFrom 拒绝身份变化。
- /shengxianbind 同时写入世界托管账本和已存在的会话；形成时 formAperture 要求会话已绑定本命蛊，否则拒绝。
- 测试 52；当前总计 74 passed。
- formAperture 另有 liveFingerprint 重载；BenMingGuCustody.matches 可重新比对在线玩家镜像，fingerprint 不一致时拒绝形成。
- 仍未做：真实物品唯一来源、放蛊消耗/托管、受控本体写入；当前只记录镜像身份，不移动物品。

## 14. 玩家形成入口与地块预留（本轮）

- 新增 /shengxianform 玩家命令：FORMING + 已绑定本命蛊 + 在线 fingerprint 一致时才形成；失败释放地块。
- 新增 ApertureFormationPlanner：
  - 面积档位按 CANON-006：小 100-300、中 400-600、上 700-900（游戏亩，原著百万亩缩尺）；
  - 初生仙元：10-15 / 20-25 / 30-40；
  - 时间比 1-4 / 5-10 / 11-16；
  - 档位由 insightEarned 选择（0/1/2+），时间比与档位规则是游戏化参数，不是原著公式。
- ApertureSpaceStore 新增 reservePlot/releasePlot：最小未用地块，预留写入文件，跨重启保留；建造后不会重复分配。
- 测试 53-54；总计 74 passed。
- 已用测试夹具 Gu 实机验证 /shengxianbind → /shengxianform → 进入仙窍；真实 Gu 来源/消耗、受控本体写入仍未完成。

## 15. 玩家形成链路实机验证（夹具 Gu）

- 临时测试命令 shengxianfixturegu 给 FlowBot 设置一个镜像 BenMingGu（Diamond）；该命令已从最终 JAR 移除。
- /shengxianbind -> 本命蛊已托管：Diamond [93168338]；benminggu.properties 记录 64 位 fingerprint。
- /shengxianform -> 仙窍已成：SMALL 面积=204.0 时间比=3.0 仙元=13.0 地块=0。
- /shengxian aperture status -> 面积 204.0、时间比 3.0、仙元 13.0、内部时钟 0、地块 0。
- /shengxian aperture enter -> Pos [0.5, 65.0, 0.5]，Dimension shengxian:aperture。
- 会话文件 schema=6、phase=IMMORTAL、vitalGuFingerprint=93168338...；aperture-space.properties 记录 reserved.0=true 与 built.0=true。
- 结论：schema 6 + 托管账本 + 形成规划 + 地块预留 + 仙窍进入的玩家命令链路已实机跑通。
- 限制：Gu 是测试夹具（Diamond），不是真实蛊虫来源；未验证放蛊消耗、真实物品唯一性、受控本体写入和六转本体提交。

### 复验：背包中存在对应本命蛊

- 夹具现在同时写入镜像 BenMingGu 与一个同 fingerprint 的背包 Diamond。
- /shengxianbind 通过 BenMingGuCustody.holds：镜像存在且背包中存在同 fingerprint 物品。
- /shengxianform -> SMALL 面积=228.0 时间比=4.0 仙元=14.0 地块=2。
- /shengxian aperture enter -> Pos [512.5, 65.0, 0.5]，Dimension shengxian:aperture。
- 结论：绑定与形成现在要求镜像 + 背包物品同时存在；仍不消耗物品，也不是真实蛊虫来源。
- 夹具命令已从最终 JAR 移除。

## 16. 仙窍来访权限实机验证（本轮）

- 环境：FlowBot 为已形成仙窍（plot 2）的主人，GuestBot 在 overworld。
- /shengxianinvite GuestBot（FlowBot）-> 已邀请 GuestBot 进入仙窍地块 2。
- /shengxianvisit FlowBot（GuestBot）-> GuestBot Pos [512.5, 65.0, 0.5]，Dimension shengxian:aperture。
- /shengxianrevoke GuestBot（FlowBot）-> 已撤销来访权限。
- GuestBot /shengxian aperture leave -> 回到 overworld。
- 再次 /shengxianvisit FlowBot -> 失败：没有该仙窍的来访权限。
- 结论：主人邀请/撤销、访客进入/离开、撤销后拒绝均已实机验证；来访名单写入 aperture-space.properties 的 guest.<plot>.<uuid>。
- 未验证：访客方块权限、护道人专属权限、多人同时进入；离线主人来访已在第 17 节验证。

## 17. 离线主人来访实机验证（本轮）

- FlowBot 在线时 /shengxianinvite GuestBot，授予 plot 2 来访权限。
- 关闭 FlowBot 客户端，服务端记录 FlowBot left the game。
- GuestBot 执行 /shengxianvisit FlowBot：通过 profile cache 解析离线主人 UUID，读取其会话并校验来访名单。
- 结果：GuestBot Pos [512.5, 65.0, 0.5]，Dimension shengxian:aperture。
- 结论：离线主人仍可被来访；权限判定不依赖主人在线。
- 未验证：主人 UUID 从未登录过（profile cache 无记录）、离线主人撤销、访客方块权限。

## 18. 护道人与离线撤销（本轮）

- 新增 guardian.<plot>.<uuid> 名单与 addGuardian/removeGuardian/isGuardian；ApertureSpace.enter 允许 guest 或 guardian。
- 新增 /shengxianguardian <在线玩家> 指定护道人。
- /shengxianrevoke 与 /shengxianguardianrevoke 改为名字参数，可解析离线玩家 UUID 后撤销。
- 测试 57-58；总计 74 passed。
- 护道人实机进入已在本轮第 19 节验证；未验证：护道人与来访的方块权限差异、护道人在灾劫中的实际作用。

## 19. 护道人进入与撤销实机验证（本轮）

- FlowBot 为 plot 2 主人；GuestBot 在线。
- FlowBot /shengxianguardian GuestBot -> 已指定为护道人。
- FlowBot /shengxianrevoke GuestBot -> 撤销普通来访权限。
- GuestBot leave 后 /shengxianvisit FlowBot -> 仍可进入：Pos [512.5, 65.0, 0.5]，Dimension shengxian:aperture。
- GuestBot leave；FlowBot /shengxianguardianrevoke GuestBot -> 撤销护道权限。
- GuestBot 再次 /shengxianvisit FlowBot -> 失败：没有该仙窍的来访权限。
- 结论：护道人名单独立于来访名单，可单独授权/撤销，并能实际进入仙窍。
- 未验证：护道人在灾劫中的实际防护、方块权限差异、离线护道撤销实机。
