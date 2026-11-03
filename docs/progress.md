

## 2026-10-26 0.2.4 普通升仙流程接线

- 接通真实玩家备妥门禁：五转四至五阶段、非十绝体、未失窍；真实本命蛊镜像与背包 fingerprint 必须一致。
- 初始化独立人气储备，读取魂魄、体质、气运作有界游戏映射；明确排除派生 renqi、名声与魅力。
- 接入带确认窗口的碎窍阶段与真元扣除；扣除目标写入同一次会话保存，可在停服/重连后幂等恢复。
- 接通三气稳定检查、本命蛊投入门禁、仙窍形成与本体六转同步。
- K 键界面新增升仙流程页与三气控制页；新增服务端动作封包及 /shengxian 子命令，客户端只提交动作编号。
- 构建入口新增 Compile，允许只编译/打包不运行行为测试。
- 修正初始存档门禁：允许备妥时写入有界人气储备与已核验本命蛊，同时仍拒绝预置三气、风险、奖励或仙窍。无升仙会话的在线玩家从服务端调气周期中正常跳过。
- 52 个 Java 文件通过 Java 21 / NeoForge 21.1.263 classpath 编译与打包检查；JAR 结构和 Veil 4.5.2 嵌入指纹通过。本轮未运行测试，也未完成从五转到仙窍的完整真实玩家实机验收。
- 六转恢复标记绑定于仙窍形成记录；/shengxian sync 仅管理员可用且需指定在线目标玩家，普通玩家不能用它重写后续降转。
- 待完成：十绝体及其他特殊路线、仙蛊炼成/唯一性、本命蛊放入气团后的实际物品形态、蓝图中的其余 UI 页面、多个 GUI 尺寸真人操作验收、全流程多人实机验收。
- 加入服务端快照请求/回传：K 键页面实时显示阶段、三气、人气储备、最大风险、窗口、灾劫与仙窍结算；无主动输入时仍推进零抽纳模拟。产物 SHA256：7f9597d092cf8398aad247414fd872f6cbc17c42abd4694ccd294e66694947a4。
## 2026-10-27 接手 Codex 0.2.4
- 修复 AscensionControlService 变量遮蔽编译错误。
- 修复旧会话 formed aperture 导致整服启动崩溃：lenient claims 跳过无效记录并保留地块占用。
- 92 项测试通过，Build/Verify 通过，隔离服务器启动通过；产物 SHA256 5ecb0253...，3,397,485 字节。
- 新增 NeoForge 服务端配置：shatterWaitTicks、shatterEssenceFraction、qiTickInterval、allowGuests、allowGuardians；服务器启动生成 shengxian-server.toml。
- 最终产物 SHA256 5ecb0253...，3,397,485 字节；92 项测试与 Build/Verify 通过。
- 全流程实机：测试夹具 GuestBot 跑通 prepare→shatter→ABSORBING→open→offer→form→enter/leave；进入 [1280.5,65,0.5] aperture，离开回 [4.5,74,0.5]。
- 修复 ApertureSpace.enter 目标区块未加载导致传送无效：setChunkForced + 目标维度传送重载。
- 最终产物 SHA256 0c5f0ce7...，3,397,737 字节；92 项测试与 Build/Verify 通过。
- 本命蛊消耗：形成仙窍后移除一个匹配实物，consumed 记录幂等/跨重启；94 项测试。
- 最终产物 SHA256 e198b81e...，3,399,945 字节；Build/Verify 通过。
- 最终消耗版本实机复跑：钻石 3→2，consumed 记录写入，IMMORTAL，进入 [1536.5,65,0.5]。
- 并发地块测试：50 并发预留唯一；95 项测试通过。
- 蛊仙真元无限：ImmortalEssenceService 每 20 tick 补满 IMMORTAL 玩家真元；实机 0/999→999/999。
- 最终产物 SHA256 7b2d5456...，3,401,694 字节；95 项测试与 Build/Verify 通过。

## 2026-10-27 0.3.0 普通升仙恢复与安全收尾（未开放）

- 成窍后的六转本体同步改为持久意图/确认；登录自动恢复，已发生降转或异常状态不被覆盖。
- 本命蛊完整 `ItemStack` 先进入可恢复托管，再从背包移除；成窍提交后原物返还，背包满则继续保存在托管账本中。不伪造仙蛊升炼。
- 三气/灾劫失败提交具体原因后，恢复流程写入本体失窍并触发相应死亡，随后记录确认；在线失败、登录、重生走同一恢复方法。
- 删除旧的真元“补满”服务，仙元账本不冒充本体数据。
- 修复门禁关闭时旧 `BREAKING` / `BROKEN` 会话仍被服务端 tick 推进的错误。新增回归后，核心测试为 107 passed / 0 failed。
- 0.3.0 Java 21 / NeoForge 21.1.263 编译通过；隔离新世界服务器达到 `Done` 并正常保存关闭。启动日志未见 `shengxian` 错误；基础模组有既存注册/标签/配方警告。
- 验证边界：无真实客户端玩家全程、失败死亡、GUI/Veil 渲染、多人护道实测。尚缺游戏内灾劫载体与护道交互、基于余气与已知蛊方的升炼映射。门禁保持关闭。
- 0.2.5 与 0.2.6 原 JAR 均保留。0.3.0 及验证细节见 `docs/verification-0.3.0.md`。

## 2026-10-27 原著灾劫证据补充与本命蛊托管修正（已进入 0.3.1 并验证）

- 新核对第三卷第 202–205 章及第五卷第 625 章：太白云生个例分别出现柳风/峥虎与后续内外劫；方正个例是天庭刻意推动的血道灾劫，护道者可干扰血云，且余气使本命血道蛊升炼成功。新增 CANON-012/013；这些个例不作为所有普通升仙的固定模板。
- 修正一个与严格原著冲突的收尾：成窍后不再自动把本命蛊原物返还。完成形成的会话将 escrow 标为 `EMBEDDED`，保留完整 ItemStack，不推断未有普遍依据的返还或仙蛊升炼；旧版本已进入 `RETURN_PENDING/RETURNED` 的事务仍按原状态完成，避免改写既有物品结果。
- 先增加失败回归，再实现 `EMBEDDED` 状态；核心测试现为 108 passed / 0 failed。生产门禁保持关闭；该变更已进入 0.3.1 并通过 Build/Verify 与隔离服务端加载，详见 docs/verification-0.3.1.md。

## 2026-10-27 0.2.5 封闭不完整入口并补强仙窍边界

- 重新关闭备妥、碎窍、调气、求知、绑定本命蛊、收束和形成入口；门禁在任何新会话/物品账本/真元写入前检查。
- 生产能力清单改为空集。此前三气控制属于领域原型，不能冒充完整的原著调和机制。
- 已有非 IMMORTAL 会话保留并暂停；付费后仍处于 BREAKING 的会话可完成到 ABSORBING 后暂停；IMMORTAL 仙窍维护不变。
- 共享仙窍加强主人独占修改、访客只读、掉落物归属、投射物/受击/爆炸同地块限制；地块定位不再遍历全部地块，平台生成仍拒绝覆盖已有方块。
- 用户提供的 5.4 参考包已放入隔离服务端并实际启动验证；作为独立模组加载成功，目前未合并进 `shengxian-0.2.5.jar`。客户端依赖要求 Veil 4.4.1，与当前 4.5.2 依赖冲突；运行记录与流程审查见 `docs/evidence/reference-5.4-ascension.md`。
- 核心回归 100 项通过；Java 21 / NeoForge 21.1.263 编译通过。最终 JAR 为 3,415,893 字节，SHA256 `a4294b066271eb1e8cce924f5453c4bb8195d95977af77ca0384573e11cfce19`；Veil 4.5.2 指纹 `e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66`。

## 2026-10-27 0.3.1 继续收尾（尚未完成）

- 将默认灾劫 profile 改为 UNRESOLVED_ORDINARY，并把太白云生、黑楼兰案例拆成明确隔离的领域 profile；旧默认阈值和灾劫类型不再被文档表述为一般升仙规则。
- 多个并发载体持久化，schema 6 单载体读取兼容；更新收束/成窍检查读取完整载体列表，状态命令显示所有载体与冲击倒计时。
- 六页 K 键界面读取服务端快照；路线依旧门禁关闭。
- 本轮核心测试 111 项通过，完整源码编译通过。Build/Verify 与隔离服务器加载已通过，详情见 docs/verification-0.3.1.md。
- 尚未完成：真实 Minecraft 灾劫载体与护道交互、区块生命周期语义、真实用蛊反噬、一般路线灾劫规则和真实客户端/多人流程验收。不得把这一轮称为完整可玩的原著升仙。
## 2026-10-28 0.3.8 全阶段特效
特效与同步接线完成；130项核心测试、完整源码编译、Build/Verify通过，资源校验0errors/0warnings，独立隐藏OpenGL检查通过。本轮未启动服务器或Minecraft客户端。详见docs/verification-0.3.8.md；完整原著流程仍受readiness门禁关闭。

## 2026-10-28 0.3.13 阶段 1：灾劫证据矩阵与方正个例 profile（仅源码）

- 新增 `docs/evidence/tribulation-matrix.md`：CANON-007/009/011/012/013 与 GAP-001/002 逐条对到触发、外形、影响对象、应对、结束条件、个例/通则、代码位置与载体表达力。
- 探针实测：`Type.BLOOD_RAIN` 在枚举、粒子、显示名、冲击间隔与视觉掩码中均已实现，但三个已有 profile 都产生不了它——属于"有实现、无来源"的不可达能力。按 CANON-013 + GAP-001 新增 `FANG_ZHENG_WITNESSED` 与 `TribulationEngine.startFangZheng`（天地二气同时达阈值才启动；击退后不重启）。
- 接入 `STARTED_BLOOD_RAIN` 的服务端 FX 与客户端表现；生产 profile 仍为 `UNRESOLVED_ORDINARY`，readiness 空集不变。
- 回归：`FrameworkTest` 149 项通过（新增 3 项）；禁用新分发时该 3 项中 2 项失败，证明测试有效。`SourceCheck` 全源码编译通过。
- 产出 0.3.13（3,565,516 字节，SHA256 a298672f…），Build/Verify 通过。
- 实机（用户授权）：发布 JAR 在隔离专用服务器加载正常、门禁关闭、干净停服；一次性 smoke 构建（临时开门禁 + 太白云生 profile，已删除）配合真实客户端验证世界侧载体：两具 ArmorStand 按锚点生成、UUID 与会话记录一致、停服重启后同 UUID 复原。护道击退与客户端视觉未验证。
- 荒兽与峥虎的真实实体/AI、区域型血雨、灾劫自行结束条件无原著依据，登记为 TRIB-CARRIER-01/02、TRIB-END-01/02。详见 `docs/verification-0.3.13.md`。
- 阶段 2 增量（未构建、未接线）：催蛊事件层落地——`GuActivation`（owner/入口/接受-完成-拒绝/幂等键）、`GuActivationLedger`（按 owner 有界、拒绝事件不入库）、`GuActivationPolicy`（喂养与纯 tick 不算催动；反噬恒关闭）、`UpstreamFingerprint`（外层/内层哈希固定）。7 项回归，合计 156 项通过。四个入口的类与描述符已用 javap 从实际 JAR 核出。

## 2026-10-28 0.3.14：四条本体催蛊观察钩子（未启用反噬）

- 依据本体内层 JAR 的固定 SHA256 b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc，在月光蛊、白象蛇蛊、哀别持、定蛊二号四个 Procedure 的唯一 zhenyuan 写入后记录 ACCEPTED。仅服务端玩家、仅存在 first 升仙会话且处于 BREAKING 至 FORMING 时计入有界事件账本。
- 修复了 Mixin 回调必须完整匹配目标方法参数的签名问题，并清除 NeoForge TOML 中重复的四个注册表。构建器新增 ASM 静态检查：目标类/描述符、唯一真元写入、Inject 方法、完整回调参数、字段写入点、AFTER 位移和配置单次注册。
- FrameworkTest 157/157；真实压缩玩家 NBT 检查 22/22；Java 21 完整编译与四个目标校验通过；common-side 134 个类不含客户端渲染引用；Build/Verify 通过。0.3.13 不可变产物哈希保持不变。
- 反噬、销毁、概率、本命蛊豁免均未实现；被观察的只是四条接受分支，非全本体覆盖。普通升仙 readiness 仍关闭。没有启动 Minecraft、集成世界或服务器；运行时 Mixin 启动及多人行为未验证。见 docs/verification-0.3.14.md 与 docs/evidence/gu-activation-api.md。
## 2026-10-28 0.3.15：全局有界的催蛊观察账本

- 独立审查指出 0.3.14 账本只限制每位玩家条数，长期服务器的 owner 数量仍可增长。先加 RED 回归，再把每位玩家的序号和事件移入同一 access-ordered LRU 状态。
- 默认最多保留 4096 个 owner，每位最多 64 条事件；达到上限时一起淘汰最久未访问 owner 的事件、去重键与序号。新 owner 从序号 1 开始；账本清空时三者同时清理。
- FrameworkTest 158/158；Build 的 NBT 与四个上游 Mixin 静态核验通过；common-side 135 个类没有客户端渲染引用。完整结果见 docs/verification-0.3.15.md。
- 本轮没有启动 Minecraft、客户端、集成世界或服务器。门禁仍关闭；账本不是持久化来源，仍不应用反噬与物品损失。
## 2026-10-28 0.3.16：失败结算原因门禁收紧

- 失败策略只接受 readiness 已开放且恰好一个明确三气失衡原因；灾劫冲击、未分类、空集和混合原因拒绝；玩家桥接层重复检查。
- FrameworkTest 158/158；Build/Verify、22 项压缩玩家 NBT、四个 Mixin 静态目标检查、135 个 common-side 边界和离线协议回环通过。详见 docs/verification-0.3.16.md。
- 失败副作用的持久确认晚于本体失窍/致死效果，登录/重生可重试未确认记录。CANONICAL_FAILURE 仍关闭，readiness 仍为空集。没有启动游戏或服务器；死亡崩溃恢复、运行时 Mixin 和多人流程未验证。

## 2026-10-29 0.3.19 仙窍维持消耗

- 仙元第一次有了真实支出：`advanceInternal` 先扣维持再产出，余额不足则停摆；速率走服务端配置（默认 0.004，低于产出 0.01，保守默认）。
- 168 项回归通过（新增 4 项）；0.3.19 产物 3,611,465 字节，SHA256 84d50ccd…。
- 实机（隔离服务器 + FlowBot，RCON 以玩家身份进入仙窍）：12 秒内内部时钟 420→2100、仙元 25.52→35.60，增量 10.08 = 1680×0.006，证明维持已在服务端生效。
- 仍未做：仙元其他支出/收入、停摆恢复、净收支 UI、多人；readiness 与两项能力保持关闭。详见 docs/verification-0.3.19.md。

## 2026-10-29 0.3.21 仙蛊最小闭环

- 新增 `XianGu` / `XianGuCatalog` / `XianGuLedger` / `XianGuService` / `XianGuCommand`：排难仙蛊（CANON-009）消耗 5.0 仙元摧毁一个活动载体，并消耗自身。
- 0.3.20 用删除键实现消耗，被追加式账本拒绝（实机报 `Applied-effect ledger cannot shrink`）；0.3.21 改为 owned/used 计数对。
- 172 项回归；0.3.21 SHA256 ec4aaec8…。实机：grant → use → 仙元 51.56→46.56、载体被毁、二次 use 被拒。
- 未做：炼成、其他仙蛊、世界唯一性、个例代价、多人。

## 2026-10-30 0.3.23 启动崩溃修复

- 0.3.22 打包成功但服务器启动即崩：`defineInList` 校验器被 null 探测 → `ImmutableCollections.ListN.indexOf(null)` NPE。改为普通 `define` + 读取端兜底。
- 修正 `build.ps1` mixin 守卫（4 → 10 个钩子）。
- 0.3.23：199 项回归、SourceCheck/NbtCheck/Build/Verify 通过；隔离服务器 `Done (2.398s)`、10 个 mixin、三只仙蛊物品可发放、目录命令正常。
- 产物 3,819,862 字节，SHA256 dadf2889…。仍未实机：反噬、仙蛊效果、爆炸复原、碎窍事务、多人。

## 2026-10-30 0.3.24 三处缺陷修复

- `JiangShanRuGuService` 的 `hasChunkAt` 弃用调用改为 `isLoaded`，构建警告清零。
- 仙蛊催动失败只报具体原因，不再追加通用“操作失败”消息（新增 `Outcome.REJECTED` 空消息）。
- 人如故在目标无采样记录时改报“尚无近期伤势记录”，不再误判跨维度并清空历史。
- 199 项回归、Build/Verify 通过；0.3.24 隔离服务器 `Done (2.102s)`、0 异常；SHA256 2dddccd0…。

## 2026-10-30 0.3.25 grant 补发修复 + 代表仙蛊实机验收

- `XianGuService.grant` 在 ALREADY_HELD 时跳过 `deliver`，绑定物品丢失后玩家永久卡死；修复为幂等补发，新增回归（200 项）。
- 新增 `tools/FixtureUpstreamRank.java` 离线 NBT 夹具工具；FlowBot 修为补到 6 转后三只代表仙蛊实机全链路通过：定仙游 −3.0 仙元/冷却 200t、人如故 −2.0、江山如故 COMMITTED。
- 夹具 playerdata 已还原；测试环境停在 0.3.25。

## 2026-10-30 多人并发验收（0.3.25）

- 双客户端（FlowBot + GuestBot）同服；同一玩家并发催动只有一次扣费（−3.0）、一个 operation id，第二次被冷却拒绝；跨玩家会话/账本完全隔离；离线玩家 grant 被正确拒绝。
- 修复 `launch-client-guest.ps1` gameDir 冲突，Guest 客户端升到 0.3.25。记录见 `docs/verification-0.3.25-multiplayer.md`。
- 仍未验收：双人同窍实时交互、死亡瞬间并发、同载体并发击退、反噬阈值累计。

## 2026-10-30 0.3.26 仙蛊贴图更换

- 三只代表仙蛊按用户提供的像素画换贴图：定仙游/人如故（绿蝴蝶）、江山如故（山水甲虫），64×64 透明背景，模型改为 item/generated。
- `VerifyRepresentativeGu` 模型校验扩展为接受几何/贴图两种形态；200 项回归 + 67 项资源检查通过。
- 实机：三只物品发放正常，Missing model/texture 0 条。产物 SHA256 2f94a1d5…。

## 2026-10-30 0.3.26 验收更正 + 贴图验收

- **更正 0.3.25 错误验收**：人如故/江山如故当时被"未持有"拒绝（grant 在 use 之后），没有完成事务；完成事务的只有定仙游。会话文件只有一条 use 记录佐证。0.3.25 文档已追加更正节。
- 人如故复试：夹具转数 6、持有正常，但 FlowBot 在上次伤害测试中死亡且无人点重生，`check` 以 `!isAlive` 拒绝——前置拦截正确，效果路径仍需"活人受伤"场景。
- 贴图：三只仙蛊 64×64 像素画上物，Missing model/texture 0 条。

## 2026-10-30 0.3.27 UI 收支显示 + 读取端审计 + 深水区门禁实测

- 快照协议加 `apertureUpkeep`/`backlashThreshold`；仙窍页显示维持与净收支，灾劫页显示反噬阈值；200 项回归全过。
- `tools/AuditUpstreamReads.java`：22371 类全扫 GETFIELD；renqi=凡人突破率因子（7.5E-4/5.0E-4 系数），kongqiao=本体开窍状态机（KE1Procedure），升仙模块不应接入。
- 深水区 smoke 实测：6 类前置门禁全部正确拒绝；卡在本体本命蛊仪式（需真人玩法）。smoke 清理完毕，环境还原。

## 2026-10-31 0.3.28 账面清账

- `build.ps1` 默认版本跟随最新，消除漏传 -ModVersion 撞产物锁的脚枪。
- 补档 `verification-0.3.11.md`、`verification-0.3.17.md`；证据索引补登读取端审计。验证档自 0.3.1 起连续无缺档。
- 全量验证通过（200 回归 + 8 钩子 + 67 资源 + 6 类回执 + 237 类）；隔离服务器 0 异常。产物 SHA256 35a7c31c…。

## 2026-11-01 0.3.29 音效层 + 进度成就（双代理并行）

- 派两个并行代理：A 做音效、B 做成就，Lead 负责接线、合并构建、实机验收。
- 音效：`AscensionSounds` 原版事件层（节流：每 tick ≤8、同格冷却、强度阈值），覆盖 FX/8 个 phase/调和脉冲/三只仙蛊；修掉 `XianGuEffects.terrain` 单 tick 可播几十次的刷屏 bug。代理纠正了"模组无声音"的前提——客户端本有 `ClientFx`/`RitualSoundscape`。
- 成就：8 个（主线 6 + failed 分支 + 仙蛊 goal），impossible 触发 + 服务端授予，实机确认自动授予生效。
- 更正回执项数字 19→23（旧校验器口径）。产物 SHA256 b3243f1e…。

## 2026-11-01 0.3.31 CANON-009 强催与道痕

- 盘查"已核未实现"清单：内劫（CANON-012）、余气升炼（CANON-013）、强催道痕（CANON-009）三项仍未做；福地三档与天灾→地劫接续早已实现。
- 实现强催：`XianGuForcedUse` + `/shengxiangu force`——仙元归零、载体被灭、仙蛊自爆、永久道痕 `xiangu:daohen:eyes` + 200 tick 失明，道痕每 1200 tick 复现。202 项回归（+2）。
- 修：`configuredProfile` 重复空值检查；可重复型仙蛊强催的误导文案（0.3.30→0.3.31）。
- 再次尝试取原著正文失败（fetch 404 / firecrawl 403 / 搜索仅百科论坛），GAP-001/006 仍关闭。

## 2026-11-01 0.3.32 内劫 + 余气升炼（已核未实现清单清空）

- 内劫（CANON-012）：`InnerTribulation` + `TribulationWorldService.phaseSupports`——立窍后余气在仙窍内凝结第二场灾劫（柳风/峥虎），主人可直击；仅太白云生个例。
- 余气升炼（CANON-013）：`LeftoverRefinement` + `/shengxiangu refine`——方正个例下得到「血道仙蛊（原文未名）」，新增 `Effect.UNSPECIFIED`（可持有不可催动，拒绝编造效果）。这是唯一非管理员获取路径。
- 204 项回归（+2）；实机：`refine` 拒绝分支正确、目录显示 5 条、服务器 0 异常。
- 确认：余气为 0 的会话不会触发两条机制——符合原著"来不及纳入的余气"前提。

## 2026-11-01 0.3.34 去掉个例门禁（不要特例）

- 灾劫引擎：`UNRESOLVED_ORDINARY` 与 `TAI_BAI_WITNESSED` 合并为 `startFromAmbientQi`，普通路线也有柳风/峥虎；个例降级为参数变体。
- 内劫/余气升炼：`profileAllows` 恒真，改为只看余气状态；仙蛊条目改为通用「余气仙蛊（效果未核）」。
- 门禁如实化：`implementedCapabilities()` 空集 → 7 项；消息 8 条 → 1 条（UPSTREAM-010 字段边界）。
- 新配置 `ascension.apertureLedgerOnly`（默认 false）显式放行；实机验证两态：默认报 1 条理由、开启后「已就绪」。
- 204 项回归；产物 SHA256 856c335b…。通用化记入 canon-rules PROJECT-001/002。

## 2026-11-01 0.3.36–0.3.37 首次端到端升仙成功 + 纳气小游戏

- 碎窍卡死根因：真元事务要求精确基线，而本体连续重算 zhenyuan。参考 5.4 实现（只做只读阈值、不扣真元）后改为尽力扣费，0.3.36 打通。
- 端到端：prepare→shatter→纳气→open→offer→form→IMMORTAL，面积 585 万亩 / 仙元 29 / 时间比 8.0；本体 UI 显示六转初阶。
- 首轮失败（人气过重，风险 12.11 > 12）验证了 CANON-002 失败结算，并发现 shiqiao 永久资格后果。
- 0.3.37：纳气页改为三轨道拖拽小游戏（平衡带/失衡变红/速率配平按钮）。

## 2026-11-02 0.3.92 门禁默认开启：字段边界重新归类

- 盘查"为什么普通路线还关着"：唯一理由是 `UPSTREAM_APERTURE_WRITE`，而它要求把仙窍写回本体玩家数据——本体没有这个字段。
- 独立复核本体：外层 JAR SHA256 `3733c20f…cf04` 实测一致；`PlayerVariables` 411 处字段穷举，仙窍/仙元关键字 0 命中。仅有的相关存储是 `zhenyuan`/`zuida_zhenyuan`/`kongqiao`/`shiqiao`，UPSTREAM-006/008 已禁止复用。UPSTREAM-010 成立。
- 决策 PROJECT-003：边界由"未实现能力"改记为"已审计字段边界"，移出默认必需集；仙窍仍由世界侧账本承载（对等替代，非伪造字段）。
- 发现隐藏第二道门禁：`StartGate.check` 遍历 `Capability.values()`，与 `AscensionReadiness` 各自强制第 8 项；两处一起改为消费显式必需集，否则只会半开门。
- `RuleProfile.ordinaryRequired(boolean)` + `evaluate(implemented, required)` + `StartGate.check(..., required)` + `AscensionService`/`SessionStore` 透传；`ShengxianConfig` 旧键 `apertureLedgerOnly` → 新键 `requireUpstreamApertureWrite`（默认 false，严格模式无配置可满足）。
- 210 项回归（+1 严格模式迁移测试）；Build 与 Verify 全过。产物 `shengxian-0.3.92.jar`，SHA256 `3428128e…e907`。
- 未改动：升仙流程、仙窍账本、失败结算、六转写入、视觉层。GAP-001/004/006 仍是证据边界，未升级为已核。未启动游戏，实机表现待验收。
- **构建产物回收（同轮）**：`build/runs` 每调用建一个 GUID 暂存目录且从不清理，已积压 170 个 / 582 MB。`build.ps1` 现于成功收尾自动清空 `build/runs`（失败的那次保留以便排查）；新增 `-Action Clean` 回收积压，并删除 `.cache` 本体 jar 的可重生成副本。发布产物改为**滚动保留最近 3 个版本**（旧 jar 不可重建，故不全删），成功构建后自动剪枝。另清掉 25 个一次性验收/测试环境目录与根目录陈旧解包副本。`build/` 由 3.65 GB 降至约 35 MB；清理后 Verify/SourceCheck/Test 全部照常通过。
