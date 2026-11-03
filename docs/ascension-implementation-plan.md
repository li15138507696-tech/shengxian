# 升仙实施计划（阶段 1 修订）

日期：2026-10-25。基线版本 0.1.0；本轮交付 **0.2.0**（schema 2）。本计划取代 `docs/reference/` 中面向"天意"的历史任务，只适用于独立项目 `D:/daima/mcmod/升仙`。

## 当前进度（0.3.92，2026-11-02）

- **普通升仙门禁默认开启**：唯一阻塞项 `UPSTREAM_APERTURE_WRITE` 被重新归类为**已审计字段边界**并移出默认必需集（决策 PROJECT-003，取代 PROJECT-002）。理由：本体 `PlayerVariables` 没有任何仙窍/仙元字段（UPSTREAM-010，本次对 411 处字段穷举复核，关键字 0 命中，外层 JAR 指纹实测一致），要求写回该字段等于挂一个按定义不可能满足的条件。
- 门禁判定改为消费**显式必需集**：`RuleProfile.ordinaryRequired(boolean)`、`AscensionReadiness.evaluate(implemented, required)`、`StartGate.check(..., required)`。`StartGate` 此前遍历 `Capability.values()`，是与 `AscensionReadiness` 并行的隐藏第二道门禁，两处必须同步改，否则只半开门。
- 严格模式 `ascension.requireUpstreamApertureWrite`（默认 false）保留旧行为且无配置可满足；旧键 `apertureLedgerOnly` 移除，旧配置文件该行被忽略。
- 210 项回归（+1 严格模式迁移测试）；Build/Verify 通过；产物 `shengxian-0.3.92.jar`，SHA256 `3428128e…e907`。见 `docs/verification-0.3.92-gate-default-open.md`。
- **未改变**：升仙流程、仙窍账本字段、失败结算、六转写入、视觉层。GAP-001（一般灾劫目录）、GAP-004（反噬保护差异）、GAP-006（失败后存活）仍是证据边界，本次未升级为已核；未启动游戏/服务器，实机多人验收仍待做。

## 历史进度（0.3.44，2026-11-01）

- **灾劫特效工坊**：接入 Photon 2.2.9（optional 依赖）+ LDLib2 2.2.45。五类灾劫各有专属粒子发射器，由 `PhotonTribulationFx` 构建；Photon 缺席时反射桥接失败并回落到服务端原版粒子，jar 不打包这两个库。客户端日志已验证 `workshop active` 与 `effect started: BLOOD_RAIN / SOUND_CHIMES`；画面观感未截图确认。
- **修掉 build.ps1 的静默中断缺陷**： PowerShell 5.1 把 javac 的 stderr 弃用提示当终止错误，导致构建在 Prepare 后无声失败。
- 前序：端到端升仙跑通（0.3.36）、纳气小游戏（0.3.37）、界面重绘（0.3.39）。产物 `shengxian-0.3.44.jar`，SHA256 `1f8bf9be…`。

- **界面重绘**：`QiControlScreen` 改为玄墨金线仙侠风——深墨面板、金色双线勾边与角花、玉色三气条（金头渐变）、金菱速率滑钮、三段变色"劫"风险槽；全部文字换主题色。实机截图确认（`build/ui-v2.png`）。
- 首次端到端升仙成功 + 纳气小游戏见 0.3.36/0.3.37 记录。产物 `shengxian-0.3.39.jar`，SHA256 `bacb1609…`。

- **首次端到端实机升仙成功**（0.3.36 + 夹具）：prepare → shatter → 纳气 → open → offer → form → **IMMORTAL**。结果：面积 5,853,473 亩（CANON-006 中等福地）、初生仙元 29 颗、时间比 8.0；客户端本体 UI 显示「境界：六转 初阶」。记录见 `verification-e2e-success.md`。
- 实机暴露并修复的缺陷：
  - 碎窍真元事务与"本体持续重算真元"不兼容导致永久卡死（0.3.35 试修未果）；参考《升仙免授权5.4》只做只读阈值的做法后，0.3.36 把扣费降级为尽力而为，碎窍得以推进。
  - 失败结算写 `shiqiao=true` 使角色永久失去资格——符合原著"不可逆"，但夹具需复位才能重试。
- **纳气小游戏（0.3.37）**：三个输入框换成三条拖拽速率轨道，实时显示气量条/平衡带/失衡变红/风险值，并提供"速率配平"按钮（人气 = 天地 × 4/3，本轮实机踩出的关键比例）。
- 产物 `shengxian-0.3.37.jar`，SHA256 `095722c2…`。

- **去掉个例门禁（用户指示"不要特例"）**：灾劫引擎的普通路线与 witnessed profile 合并为同一套 `startFromAmbientQi`；内劫与余气升炼不再要求特定 profile，只看余气是否真的还在；产出仙蛊改为通用「余气仙蛊（效果未核）」。
- `implementedCapabilities()` 从空集改为如实返回 7 项：门禁消息由 **8 条假理由**变为 **1 条真理由**（`本体空窍状态写入尚未接入`，UPSTREAM-010 的字段边界）。
- 唯一缺口做成显式配置 `ascension.apertureLedgerOnly`（默认 false）：设为 true 时 `/shengxian status` 显示"普通升仙路线已就绪"。实机两态均已验证。
- 冲击失败原因校验去掉 profile 白名单。204 项回归通过（新增/重写"普通路线与个例同规则""无特权案例""门禁只缺一项"等断言）。
- 产物 `shengxian-0.3.34.jar`，SHA256 `856c335b…`。通用化记为 PROJECT-001/002（项目决策，非原著结论）。

- **内劫（CANON-012）**：`core/InnerTribulation` —— 立窍后余气达阈值即在仙窍维度内凝结第二场灾劫（柳风/峥虎），锚点落在本人地块、消耗余气、一次性；世界层新增 `phaseSupports` 让 `inner-` 载体在 IMMORTAL 期生效，主人可直击洞内灾劫（游戏映射）。仅太白云生个例启用。
- **余气升炼（CANON-013）**：`core/LeftoverRefinement` + `/shengxiangu refine` —— 方正个例下消耗余气把本命血道凡蛊升炼为「血道仙蛊（原文未名）」。原文无名无效，故新增 `XianGu.Effect.UNSPECIFIED`：可持有不可催动。这是**唯一**非管理员发放的仙蛊获取路径。
- 204 项回归（+2）。产物 `shengxian-0.3.32.jar`，SHA256 `39089b58…`。
- 至此"已核未实现"清单清空；剩余未实现项全部缺正文（GAP-001 一般灾劫目录、GAP-004 反噬保护差异、GAP-006 失败处置）。readiness 仍为空集。

- **CANON-009 个例落地**：新增 `core/XianGuForcedUse` 与 `/shengxiangu force <id>`——仙元不足仍可强催排难仙蛊：仙元归零、载体被灭、仙蛊自爆、追加不可逆道痕 `xiangu:daohen:eyes`，并施加 200 tick 失明；带道痕者每 1200 tick 复现一次。零仙元、无载体、可重复型仙蛊三种情形拒绝。202 项回归（新增 2 项）。
- 顺带修两处缺陷：`TribulationParameters.configuredProfile` 的重复空值检查；强催遇到可重复型仙蛊时误报"操作已保留"的文案。
- 已确认无需重做：福地三档（CANON-006）与天灾→地劫接续（CANON-009/011）在 0.3.x 早已实现。
- 仍未实现且缺证据：内劫（CANON-012）、余气升炼（CANON-013）、一般灾劫目录（GAP-001）、失败处置（GAP-006）。本轮再次尝试取原著文本失败（fetch 404 / firecrawl 403 / 搜索无正文）。
- 产物 `shengxian-0.3.31.jar`，SHA256 `ad7effaf…`。readiness 仍为空集。

- **音效层**：新增 `server/AscensionSounds.java`（原版 SoundEvents、每 tick 上限 8 条、同格/维度冷却、强度阈值）；接入全部 FX cue、8 个 phase 转场、三气调和脉冲、三只仙蛊专属音色。顺带修掉 `XianGuEffects.terrain` 每 tick 可播几十次的环境音刷屏 bug。客户端原本已有 `ClientFx`/`RitualSoundscape` 前景音，服务端层刻意选不同事件做底层厚度。
- **进度成就**：8 个成就（主线 root→shatter→absorb→opening→forming→immortal，failed 分支，xian_gu goal），`minecraft:impossible` + 服务端授予，幂等；IMMORTAL/FAILED 走 20 tick 对账兜底。
- 两处接线由 Lead 补（`AscensionLifecycleService` 7 个转移点、`AscensionControlService` 调和脉冲、`AscensionOutcomeService` 终态），并新增按 (玩家,阶段) 去重的 `AscensionSounds.phaseOnce`。
- 验证：200 项回归 + 9 类静态校验通过；隔离服务器 `Done (2.306s)`、0 异常；成就实机确认已注册且自动授予生效。产物 `shengxian-0.3.29.jar`，SHA256 `b3243f1e…`。
- 更正：回执检查项 `reusable immortal Gu receipt` 实为 23 项（0.3.27/0.3.28 文档沿用的 19 是旧校验器的数字）。

- **账面清账版（无玩法变更）**：`build.ps1` 默认版本从固定的 0.3.18 改为跟随最新（0.3.27），消除"忘记传 -ModVersion 就撞不可变产物锁"的脚枪；补档 `verification-0.3.11.md` 与 `verification-0.3.17.md`；`docs/evidence/README.md` 补登 `upstream-reads-audit.md`。
- 全量验证：200 项回归 + 8 钩子 + 67 项资源 + 6 类回执（22/22/22/31/30/19）+ 237 个 common-side 类全通过；隔离服务器 `Done (2.317s)`、0 异常。产物 `shengxian-0.3.28.jar`，SHA256 `35a7c31c…`。
- 验证档自 0.3.1 到 0.3.28 连续无缺档。readiness 仍为空集。

- 快照协议新增 `apertureUpkeep` 与 `backlashThreshold`；仙窍页显示维持/净收支（亏损提示），灾劫页显示反噬阈值。200 项回归（含协议回环）通过。
- 新增 `tools/AuditUpstreamReads.java` 读取端审计：renqi 是凡人突破率因子、kongqiao 是本体开窍状态机字段，升仙模块不应接入——UPSTREAM-006/007 读取端缺口关闭（`docs/evidence/upstream-reads-audit.md`）。
- 深水区多人验收：smoke 实测 6 类前置门禁全部正确拒绝（资格转数精确 5.0/4.0、本命蛊、旧会话、无会话），但继续推进需要真人完成本体本命蛊仪式——机器人链路边界，非代码缺陷。smoke 已删，环境还原，详见 `verification-0.3.27.md`。
- 产物 `shengxian-0.3.27.jar`，SHA256 `a1023b50…`。readiness 仍为空集。

- 三只代表仙蛊换用像素画贴图（定仙游/人如故 64×64 蝴蝶、江山如故 64×64 山水甲虫，透明背景），物品模型改为 `item/generated`；`VerifyRepresentativeGu` 模型校验扩展为接受几何/贴图两种形态。产物 `shengxian-0.3.26.jar`，3,855,688 字节，SHA256 `2f94a1d5…`；实机发放正常，Missing model/texture 0 条。
- **更正**：0.3.25 记录的"人如故 −2.0、江山如故 COMMITTED"是错误结论——当时两只因"未持有"被拒，完成事务的只有定仙游（−3.0）。已改写 0.3.25 文档并在 `verification-0.3.26.md` 说明。
- 人如故/江山如故的效果路径仍待一次"活人受伤/真实爆炸"场景验收；本轮验证到前置拦截（含死亡状态拒绝催动）全部正确。readiness 仍为空集。

- 三只代表仙蛊换用像素画贴图（定仙游/人如故 64×64 蝴蝶、江山如故 64×64 甲虫，透明背景），物品模型从 3D 元素模型改为 `item/generated` 平面模型。`tools/VerifyRepresentativeGu.java` 的模型校验扩展为同时接受几何与贴图两种形态（67 项资源检查通过）。
- 200 项回归、Build/Verify 通过；隔离服务器 + 客户端实测：三只物品正常发放，Missing model/texture 错误 0 条。产物 `shengxian-0.3.26.jar`，3,855,688 字节，SHA256 `2f94a1d5…`。

## 历史进度（0.3.25，2026-10-30）

- 修复 grant 在 ALREADY_HELD 时不补发绑定物品的缺陷（玩家清背包后会永久无法手持仙蛊）；新增回归，200 项通过。
- 新增 `tools/FixtureUpstreamRank.java` 离线夹具工具，将测试角色修为补到 6 转后，**三只代表仙蛊在真实服务器上完成 check→扣费→效果→verify→COMMITTED 全链路**：定仙游 −3.0 仙元、人如故 −2.0、江山如故交付与提交正常。产物 `shengxian-0.3.25.jar`，SHA256 `e7284b5d…`，详见 `docs/verification-0.3.25.md`。
- 边界：实际传送落点、真实爆炸复原、无伤时人如故的语义需设计确认；夹具 playerdata 已还原。readiness 仍为空集。
- 此前增量：已实现定仙游、人如故、江山如故三个可重复催动的实体仙蛊，按已批准规格提供蓄力传送、近期伤势救治、以及默认关闭的灾毁留景/地形复原。游戏参数、范围和恢复边界见 docs/superpowers/specs/2026-10-30-representative-immortal-gu-design.md。
- 最终源码验证：FrameworkTest 200/200；SourceCheck 全源码编译，既有 8 个蛊入口检查与代表仙蛊/地形资源 70 项检查通过；NbtCheck 含新增 23 项仙蛊回执检查通过。独立 NeoForge 服务端烟测注册三件物品、应用两个地形 mixin、执行普通 TNT 爆炸路径并干净停服。
- 尚未连接客户端或玩家；具体仙蛊操作与渲染、多人并发、崩溃注入、真实存档、磁盘故障及与 Tianyi 同装运行仍待实机验收。升仙 readiness 仍为空集。未改版本号、发行 JAR 或真实存档。详见 docs/verification-representative-immortal-gu.md。

## 历史进度（0.3.23，2026-10-30）

- 0.3.22 整批改动（本体字段事务、灾劫冲击回执、蛊反噬、代表仙蛊、地形/爆炸）首次打包，但在隔离服务器启动时崩溃：`tribulationProfile` 用 `defineInList` 定义，NeoForge 用 null 探测校验器，`ImmutableCollections.ListN.contains(null)` 抛 NPE。0.3.22 产物保留但不可用。
- 0.3.23 改为普通 `define` + 读取端 null 兜底；同时修正 `build.ps1` 的陈旧 mixin 守卫（4 → 10）。199 项回归、SourceCheck、NbtCheck、Build/Verify 全通过。
- 隔离服务器首次成功启动该批代码：`Done (2.398s)`，10 个 mixin 就位，三只代表仙蛊物品可 `/give`，`/shengxiangu list` 显示 4 条。
- 产物 `shengxian-0.3.23.jar`，3,819,862 字节，SHA256 `dadf2889…`。详见 `docs/verification-0.3.23.md`。
- 仍未实机：反噬语义、三只仙蛊实际效果、爆炸掉落复原、碎窍事务、多人/崩溃注入。readiness 仍为空集。

## 历史进度（0.3.21 源码，2026-10-30）

- 仙蛊最小闭环：目录只有 CANON-009 的排难仙蛊（消耗 5.0 仙元，游戏参数），持有记录写进会话追加式账本（owned/used 计数），催动时在一次 `mutate` 内扣仙元、摧毁一个活动载体、计数消耗。
- 命令：`/shengxiangu list|use|grant`（grant 需权限 2）。没有炼成路径——原著未核；唯一性只按会话成立，世界唯一性未核（GAP-004）。
- 0.3.20 曾用“删除持有键”实现消耗，被会话账本的追加式不变量拒绝（`Applied-effect ledger cannot shrink`），实机暴露；0.3.21 改为计数对修复，0.3.20 产物保留。
- 172 项回归通过；0.3.21 SHA256 `ec4aaec8…`，3,624,859 字节。实机：发放 → 催动 → 仙元 −5.0、载体被摧毁、再次催动被拒。
- 仍未做：其他仙蛊、炼成、世界唯一性、双瞳爆裂类个例代价、多人并发。readiness 仍为空集。
- 2026-10-29 首轮审计（不改模组版本）：离线审计器把 14 个按键消息映射到 Procedure，并区分耐久/冷却门槛读取与写入。全类交叉引用和 GUI 维护过程已确认 `ItemStack` 槽与 `_NaiJiu` 计数的配对。C/V/X/Z 方法级耐久字段引用组分别为 1+3、1+4、1+2、1；这不是每个分支的实际消耗集合。首轮审计时还未接入钩子，`GU_BACKLASH` 继续关闭。详见 `docs/evidence/gu-activation-api.md`。
- 2026-10-29 再增量（源码、证据与隔离服校验；未升版、未覆盖发行产物）：C/V/X/Z 四个钩子仅记录实际变化的非空蛊槽。173 项 FrameworkTest 与 SourceCheck 通过；临时探针服 NeoForge 21.1.263 / Java 21.0.7 启动至 Done，四个目标类均触发 Mixin 应用，RCON 停服且干净退出。没有玩家或按键调用；上游本体仍有 deleted_mod_element 等资源错误，详见 smoke 记录。普通路线和 GU_BACKLASH 仍关闭。
- 2026-10-29 修为桥接增量：Snapshot 增加 upstreamShouyuanRaw，`/shengxian status` 展示本体 shouyuan 原始值。已核对上游字段类型、NBT 读写、Clone 复制及两位小数显示；单位尚未确认，因此不换算、不参与升仙门禁或结算。Java 21 全源码编译通过；本轮未运行测试、未升版本、未重打包。
- 2026-10-29 仙窍仙元诊断增量（源码未升版）：`/shengxian aperture status` 现在按真实服务端步进间隔展示游戏参数的每内部刻产出、维持、净变化，以及单次在线步进预计值和余额能否覆盖维持。174 项领域回归通过，Java 21 全源码编译通过（190 个 class）；未实机验证、未重打包。

- 2026-10-29 灾劫反馈增量（源码未升版、未打包）：音磬起始/冲击使用晶音和环形末地烛粒子，荒兽使用低音咆哮、震地云与电火花，血雨使用暗红下落尘和降雨声；服务端载体光环按三类灾劫区分。未新增伤害、异常状态或掉落规则。灾劫类型边界回归经 RED→GREEN；175 项 FrameworkTest、Java 21 全源码编译（192 个 class）通过。未启动客户端，粒子/音效实机表现未验收，readiness 仍关闭。
- 2026-10-29 成窍事务恢复增量（源码未升版、未打包）：地块预留现在持久化主人/空窍/会话身份；相同升仙会话重启后复用原预留，其他会话不能提交该地块。成窍账本提交后，命令重试或玩家重登会补确认预留与地块登记；旧版无身份预留保持占用，不猜归属。176 项 FrameworkTest、完整 Java 21 SourceCheck、8 个蛊钩子与 44 项压缩 NBT 检查通过。没有跑客户端/服务器；本体尚无经核验的空窍/仙元原生字段，仍以世界账本为准，普通路线门禁保持关闭。
- 2026-10-29 具名灾劫冲击增量（源码未升版、未打包）：服务器可选三个具名 profile，旧 schema 活动纳气记录迁移为 `UNRESOLVED_ORDINARY`；外劫压力与序号独立于三气风险，致命冲击保存来源并走独立一次性回执事务。补上 schema 1 ABSORBING 迁移及 schema 9 残留来源字段拒绝回归。FrameworkTest 183/183；SourceCheck、NbtCheck 均通过，含 8 个蛊钩子与 rank/三气失败/灾劫冲击回执各 22 项压缩 NBT 检查。readiness 仍为空；未跑游戏或服务器，死亡/重生/多人/崩溃运行验收仍待隔离世界验证。
- 2026-10-29 纳气期蛊反噬增量（源码未升版、未打包）：8 个已核实真实入口只在效果完成后每次 +1；默认阈值 8、配置范围 1–64 并按 session 冻结。达阈值只损失本次触发蛊一只；内容指纹与本命蛊绑定及上游 `BenMingGu` 镜像匹配时保护所有同内容副本。增加玩家 NBT PREPARED/COMMITTED/UNCERTAIN 回执、目标槽与计数核验、登录恢复隔离；普通背包与 `PlayerVariables` 杀招槽均支持精确定位。RED→GREEN 后 FrameworkTest 188/188，Gu 回执 31 项压缩 NBT 检查，NbtCheck 和 Java 21 SourceCheck 通过。未跑游戏；玩家催动、客户端同步、多人、重登和崩溃行为仍需隔离实机验收。未分类入口不处罚，`GU_BACKLASH` 与总 readiness 仍关闭。详见 `docs/evidence/gu-activation-api.md`、`docs/verification-gu-backlash.md`。
- 2026-10-30 本体碎窍事务恢复增量（源码未升版、未打包）：世界 schema 10 意图同时保存 `zhenyuan` 扣费前值与固定目标；玩家 `PlayerPersisted/shengxian:shatter_essence` 以 PREPARED→COMMITTED 记录一次扣费，异常时尽力记 UNCERTAIN。每次更新前先比较玩家 `.dat` 与单人 `level.dat/Data/Player` 的旧回执和值，保存后分别读回核验；冲击重登与 BREAKING tick 不再重放目标。schema 9 target-only 会话不推导原值，只有匹配 COMMITTED 回执才能确认，否则隔离且不补扣。RED→GREEN 后 FrameworkTest 192/192；shatter 回执 30 项压缩 NBT 检查，完整 NbtCheck、Java 21 SourceCheck 通过。复核了 `zhuanshu/jieduan` 和 `shiqiao` 专用回执路径，未发现需改动的已测缺口。`AscensionReadiness.implementedCapabilities()` 仍为空；`kongqiao`、`renqi`、`shouyuan`、仙窍/仙元字段保持只读。未运行 Minecraft、未打开真实存档、未升版或打包。详见 `docs/verification-native-body-transaction.md`。

## 历史进度（0.3.19，2026-10-29）

- 给仙窍账本加了第一笔真实支出：`ApertureEngine.advanceInternal` 新增维持参数，先扣维持再算产出，余额不足则停摆（时钟与产出都不前进，余额不会为负）。速率由 `ShengxianConfig.apertureUpkeepPerInternalTick`（默认 0.004）控制。
- 这是**游戏平衡参数**，不是原著规则：已读章节只说福地酿出青提仙元，没有任何运转成本的说法。
- 168 项回归通过；0.3.19 产物 SHA256 `84d50ccd…`，3,611,465 字节。实机（隔离服务器 + FlowBot）验证：12 秒内内部时钟 +1680、仙元 +10.08，正好等于 1680×(0.01−0.004)。
- 仍未做：仙元的其他支出入口、其他收入来源、停摆后的恢复、UI 展示净收支、多人验收。readiness 仍为空集，`CANONICAL_FAILURE` 与 `GU_BACKLASH` 仍关闭。
- 详见 `docs/verification-0.3.19.md`。

## 历史进度（0.3.18，2026-10-28）

- 阶段 1 的离线灾劫证据与方正个例 profile 已完成；普通通用灾劫仍未有充分原著依据，生产 profile 继续为 UNRESOLVED_ORDINARY，readiness 为空集。
- 0.3.14 接入月光蛊、白象蛇蛊、哀别持、定蛊二号四个真实 Procedure 的真元写入观察点；0.3.15 限制最近 4096 位 owner、每人最多 64 条。四条观察钩子不代表全本体覆盖；反噬、损失、本命蛊保护未实现，GU_BACKLASH 保持关闭；杀招按键入口未定位。
- 0.3.16 收紧失败原因白名单。0.3.17–0.3.18 增加派生稳定操作 ID、PlayerPersisted STARTED/RESPAWN_RECEIPT 回执、一次伤害尝试、Clone/Respawn 核验、只确认不重放的恢复路径和 UNCERTAIN 隔离。旧的无结算标记失败记录按 UNCERTAIN 解释；0.3.18 在伤害事件完整分发后检查最终取消状态，并在服务 API 与桥接层双重限制原因白名单；状态命令显示世界状态、玩家收据与操作 ID。
- implementedCapabilities() 仍为空集，CANONICAL_FAILURE 不会执行。协议没有跨玩家/世界文件原子事务，不宣称 exactly-once；登录、Clone、Respawn、崩溃、保存失败与存档源冲突的运行验收仍待在隔离存档中完成。
- 当前离线构建与产物指纹见 docs/verification-0.3.18.md；失败依据与安全边界见 docs/evidence/failure-settlement.md。按用户要求，本轮没有启动 Minecraft 客户端、集成世界、专用服务器或 DSH。
- 其余待办：普通灾劫通则、完整蛊虫催动覆盖与反噬损失/本命蛊保护、真实本体仙窍映射、最终门禁审计和多人运行验收。启动期 JAR 哈希钩子仍不实施，除非证明 ModLauncher/Mixin 可取得原始依赖内容。
## 历史进度（0.3.12，2026-10-28）
- 0.3.11 修复六转同步恢复时先确认世界、后证明玩家存档落盘的崩溃窗口；0.3.12 增加成窍意图协议标记，旧版未标记记录在角色仍为五转时安全延后，不覆盖合法降转。实际加载源 level.dat/Data/Player 与 UUID 玩家文件均纳入持久化核验。
- 146 项核心回归、22 组压缩 NBT 检查、23 项升仙结果检查、八阶段协议回环及完整源码构建见 docs/verification-0.3.12.md。
- 真实本体蛊激活审计已覆盖右键、攻击、被动维护、方块使用、杀招快捷键等不同入口；它没有证明完整的反噬/损失/本命蛊保护规则，详见 docs/evidence/gu-activation-api.md。
- 没有启动游戏/服务器；普通完整路线门禁继续关闭，实机/多人尚未验收。

## 历史进度（0.3.10，2026-10-28）
- 完成独立复审提出的体积云遮挡修复：独立深度副本、积分截断、渲染状态恢复与资源释放。
- 137 核心测试、23 结果检查、八阶段协议回环、全源码 Build/独立 Verify、显卡遮挡 RED→GREEN 通过。详见 docs/verification-0.3.10.md。
- 没有启动游戏/服务器；普通完整路线门禁继续关闭，实机/多人尚未验收。

## 历史进度（0.3.9，2026-10-28）
- 已接入体积云、高空三气、七层山海和服务端权威结果卷；质量/距离/来源/粒子预算和身份清理已实现。
- 137/137、Build/独立 Verify、实际 Codec 回环、23 项结果检查和隐藏显卡验证通过；资源 0 错误 1 提示。见 docs/verification-0.3.9.md。
- 未启动 Minecraft/服务器/DSH，实机/多人仍未验收。普通完整路线门禁未变。
- 真实阶段与修改清单：docs/ascension-ritual-effects.md。

## 历史进度（0.3.8，2026-10-28）
- 已补齐升仙各阶段的世界几何与本人Veil后期、服务端只读视觉广播、客户端来源隔离与生命周期；美术参数不作为原著常数。
- 130项核心回归、完整源码编译、Build/Verify通过；特效资源0错误0警告，独立隐藏OpenGL完成GLSL编译链接与8阶段中心/关闭/深度检查。
- 用户本轮禁止开服务器，未启动任何Minecraft客户端或服务器；这些离线结果不代表世界几何和多人实机验收。
- 普通升仙完整路线仍未完成，门禁关闭；当前证据见docs/verification-0.3.8.md。
## 历史进度（0.3.7，2026-10-27）

- 0.3.5 修正成窍档次计算，按已汇入天地人三气总量计分并排除未释放人气储备；0.3.6 修正上等福地初始仙元范围为 31–40，严格符合“超过三十”。
- 0.3.7 修复形成命令的门禁边缘：新形成必须先通过 readiness；已提交的 IMMORTAL 成窍记录仍保留崩溃恢复代码路径。新增回归用例验证策略边界；恢复路径本轮仅静态复核，未做运行验收。
- `build.ps1 -Action Test`：124 项通过；`SourceCheck`、0.3.7 Build/Verify 通过。JAR SHA256 为 `0476c89e1bcf311dd52a89c343ca812306224e3f0c545cdbec686c8b4d20f4ed`。验证见 `docs/verification-0.3.7.md`。
- 实际 NeoForge 客户端以 FlowBot 登录隔离 0.3.7 测试服；Veil 4.5.2 渲染管线加载。新形成命令被 readiness 拒绝，`first` 会话仍不存在。命令由服务端控制台对在线 ServerPlayer 执行，不代表 K 键 UI 点击或完整玩家流程已验收。
- 普通升仙入口仍关闭。灾劫通则、真实本体碎窍/成窍状态映射、用蛊反噬与蛊损失、单空窍失败处理以及完整玩家和多人恢复流程仍未完成。
## 历史进度（0.2.5，2026-10-27）

- 严格升仙门禁重新关闭；`GuzhenrenPlayerBridge` 暂不报告任何完整路线能力。动作在写本命蛊账本、会话或真元前拒绝。
- 已存在的非 IMMORTAL 会话保留并暂停；此前已进入 BREAKING 的会话可结算到 ABSORBING 后暂停；IMMORTAL 维护继续。
- 共享仙窍启用主人独占修改/物品操作、访客只读、掉落物地块归属、地面预检和同地块投射物/伤害/爆炸限制。
- 用户提供的 5.4 JAR 仅作流程参考；身份、观察结果及边界见 `docs/evidence/reference-5.4-ascension.md`。
- 核心回归 100 项通过；Java 21 / NeoForge 21.1.263 源码编译通过。0.2.5 完整打包结果见 `docs/verification-0.2.5.md`。

## 历史进度（0.3.1，2026-10-27）

- 0.3.1 源码已将灾劫参数按原著个例 profile 分开；默认 UNRESOLVED_ORDINARY 不生成未核灾劫，太白与黑楼兰 profile 仅供领域验证，不作为任意普通升仙模板。
- 并发灾劫状态已持久化，schema 7 单载体记录迁移到 schema 8；K 键界面已有六页，所有展示来自服务器快照。
- 本轮回归为 111 项通过、0 项失败；完整 NeoForge 源码编译通过。0.3.1 Build/Verify 通过，隔离空世界服务端启动、会话仓库打开并正常关闭；真人客户端流程仍未验收。
- 真实 Minecraft 灾劫载体/护道战斗、区块加载语义、真实用蛊反噬调用点、蛊损失与本命蛊保护规则仍未完成或未核；没有真人客户端与多人全路线验收。
- 因原著明确灾劫种类繁多、情境不同，现有案例不足以推定一般路线灾劫目录；生产能力清单保持空集，所有升仙新入口继续封闭。
- 本命蛊托管改为成窍后 EMBEDDED，不再凭空返还或升炼；0.3.1 产物已通过 Build/Verify 和隔离服务端加载。
- 原著证据与对应能力缺项见 docs/evidence/canon-rules.md、docs/evidence/tribulation-parameters.md 和本文件后续阶段。

## 历史进度（0.2.4，2026-10-26）

- 已接入普通五转路线的备妥、碎窍、三气纳气、三气收束、本命蛊投入、仙窍形成和六转字段同步；K 键界面提供流程页与三气页。
- 碎窍真元目标与 BREAKING 阶段同次保存，重连按持久化目标幂等恢复；碎窍等待为 100 tick，属游戏参数。
- 六转字段只在仙窍提交后写入；形成会持久化管理员恢复标记，若跨存档写入中断可由管理员使用带在线目标玩家的命令显式修复。普通玩家不能用恢复入口改写后续合法发生的降转。
- 人气储备以魂魄、体质、气运字段作有界游戏映射，明确排除派生 `renqi`、名声与魅力。
- 普通路线已解除空能力门禁；十绝体、方正升仙、神不知、仙僵、至尊仙胎及其他待核路线仍关闭。
- 52 个 Java 源文件已通过 Java 21 / NeoForge 21.1.263 classpath 编译并打包校验。本轮未运行测试，也未做真实玩家全流程实机验收；见 `docs/verification-0.2.4.md`。
- 本命蛊指纹绑定按玩家隔离；指纹只表示物品种类与组件，不能证明跨玩家个体唯一。存档初始校验仅接受有界人气储备及已核验本命蛊，不接受预置三气、风险、奖励或仙窍。物品没有移入独立容器，也未转阶/炼成仙蛊。真实灾劫载体、其他 UI 页面和真人全流程交互验收仍未完成；不得把普通路线游戏映射称为原著全部路线的完整复刻。

## 0.2.5 历史快照

- 世界会话仓库、三气/灾劫引擎、知识目录、仙窍账本与空间已接入，但升仙动作入口关闭。
- `AscensionLifecycleService` 仍保留 0.2.4 普通路线实现；统一 readiness 门禁在读写服务端状态前拒绝新流程。
- K 键流程页、三气页和状态命令仍可查看快照与缺失机制；调气、求知、绑定、碎窍及形成入口均关闭。
- 尚缺本体空窍事务、原著灾劫载体与应对、原著失败后果、本命蛊真实投入/损毁以及一致的本体境界/仙元提交恢复。
- 0.2.5 版本的测试、编译与打包记录见 `docs/verification-0.2.5.md`；0.2.4 玩家夹具记录保留为历史，不代表当前入口开放。

**时效说明：** 第 3 节各阶段的已完成/未完成条目保留 0.2.0–0.2.3 时的计划快照与验收目标，不代表当前状态；0.2.4 流程接线为历史实现；当前状态以文首 0.3.15 进度与 docs/verification-0.3.15.md 为准。

## 1. 本阶段证据结论

详见：

- `docs/evidence/canon-rules.md`：CANON-001..013、GAP-001..008；方正个例已有证据，仍未实现为独立 profile。
- `docs/evidence/upstream-audit.md`：UPSTREAM-001..013。

三条会改变实现的结论：

1. 本体 `renqi` 是每刻重算的派生值，且掺入 `mingsheng`（名声）与 `meilizhi`（魅力），不能当原著人气。
2. 本体没有仙元/福地/真实仙窍；`kongqiao` 是资质/体质编码（3=十绝体），`shiqiao` 才是"失窍"布尔，二者都不是"碎窍完整度"。
3. 本体在五转巅峰会封住突破；`zhuanshu=6` 在数据层合法，死亡复制会保留，但寿元归零死亡会触发 `JingJieBaoCun` 大重置；另有至少三种蛊虫/效果可降低 `zhuanshu`。

## 2. 状态身份模型（阶段 2 前置设计）

现有模型把 `revision` 同时当作"阶段转换次数"和"保存版本"，并用奇偶性绑定可达性，导致同阶段每刻更新无法保存。阶段 2 必须先拆成五个身份，再改仓库。

### 2.1 五个身份

| 名字 | 含义 | 单调性 | 谁生成 | 用途 |
| --- | --- | --- | --- | --- |
| `phaseRevision` | 已接受的阶段转换次数 | 严格递增，仅在阶段变化时 +1 | 服务端 `SessionFlow` | 判断阶段图可达性；阶段循环只允许 PREPARING↔BREAKING |
| `stateRevision` | 已持久化的状态变更次数（含阶段转换与同阶段更新） | 严格递增，每次成功保存 +1 | 服务端 | 仓库 CAS 版本；任何状态/进度变化都递增 |
| `lastActionSequence` | 该会话已接受的最大客户端动作序号 | 严格递增 | 客户端提交，服务端校验 | 拒绝重放/乱序；与 revision 解耦 |
| `serverTick` | 服务端模拟时钟（`ServerLevel.getGameTime()` 或测试时钟） | 非递减 | 服务端 | 三气/灾劫/离线补偿的同一时间轴；禁止倒退 |
| `effectKey` | 副作用/奖励的幂等编号 | 集合内唯一 | 服务端生成（如 `break:first`、`insight:12`、`aperture:formed`） | 保证同一奖励/扣费只结算一次 |

### 2.2 保存语义

- 同阶段更新：`phase` 不变、`phaseRevision` 不变、`broken` 不变、`stateRevision+1`、`lastActionSequence` 更新、`serverTick` 更新、进度对象可替换。
- 阶段转换：在上一行基础上 `phaseRevision+1`，并按阶段图检查目标；`stateRevision` 同样 +1。
- 事务恢复：仓库只做单文件原子替换；`expectedStateRevision` 不匹配即拒绝写入。读取到未知 schema、坏档、身份不一致时保留原文件并抛错，不自动补发、不删除、不覆盖。
- 阶段转换与进度更新可以在同一次写入中发生，但必须由同一个 `stateRevision` 保护；不允许"先写进度、再写阶段"的两阶段提交冒充原子事务。

### 2.3 schema 2 与 0.1.0 迁移

- schema 1 字段：`id/owner/aperture/phase/broken/revision/sequence`。
- 迁移映射：`phaseRevision=revision`、`stateRevision=revision`、`lastActionSequence=sequence`、`serverTick=0`；进度为空（三气/领悟等为 0，幂等集合为空）。
- 迁移后仍用原有构造校验（不可达阶段、broken 不一致、非法数字）拒绝坏记录；坏记录保持字节不变。
- 只有成功读取并再次保存时才写成 schema 2；单纯 `find` 不改文件。
- 未来 schema（如 3）继续拒绝读写覆盖。

### 2.4 阶段 2 最小改动

1. `AscensionSession` 增加 `phaseRevision/stateRevision/serverTick`，`lastSequence` 改名 `lastActionSequence`；去除"lastSequence 必须 >= revision"的耦合。
2. 新增 `AscensionProgress`（三气容器、领悟额度、已应用 effectKey 集合、仙窍草案 ID 等，先只做有限容器与校验，不写玩法公式）。
3. 新增 `AscensionRecord = (session, progress)`。
4. `SessionFlow.advance(...)` 只负责阶段转换；新增 `SessionFlow.update(...)` 负责同阶段更新。
5. `SessionRepository` 改存 `AscensionRecord`，CAS 参数改为 `expectedStateRevision`。
6. `FileSessionRepository` 升到 schema 2，并保留 schema 1 读取迁移。
7. 测试：同阶段连续多刻保存/恢复、陈旧 stateRevision、重复动作序号、serverTick 倒退、effectKey 重复、schema 1 迁移、坏档保留、未来 schema 拒绝、双空窍隔离、终态禁止更新。

## 3. 分阶段任务与验收

### 阶段 1：基线、原著证据、本体审计（本轮完成）

- 重新运行测试/构建/校验；记录产品指纹。
- 读完全部指定文档与实际代码。
- 核对本体指纹与字段读写；产出 CANON/UPSTREAM 证据。
- 验收：能回答"普通路线每一项能力由谁实现/缺什么"；不开放入口；不改玩家数据。

### 阶段 2：状态模型、存档与迁移（口径：74 项测试通过）

- 按第 2 节实现五个身份、同阶段更新、schema 2 迁移。
- 保留 16 项既有测试的保护目标（阶段顺序、不可逆、双空窍、坏档、未来 schema、名字碰撞等），并新增迁移/并发/幂等测试。
- 验收：连续多刻处于 ABSORBING 的记录可保存/恢复；重复事件不跳阶段；旧 0.1.0 记录迁移后仍可读；坏记录不被覆盖。

### 阶段 3：服务端服务与本体接线（本轮部分完成）

- 已完成：纯 Java `AscensionService` 的 StartGate 创建、同阶段更新、阶段推进、effectKey 幂等与陈旧 revision 拒绝。
- 已完成：`SessionStore` 世界目录缓存、`AscensionServer` 在服务器启动/停止/玩家登出时绑定、清空与逐出；`/shengxian status` 读取会话。
- 未实机验证：本机只有客户端实例，缺少服务端 jar，未启动独立服务器。
- 未完成：世界存档生命周期、`ServerPlayer` 身份绑定、本命蛊唯一来源/托管、受控本体写入、真实碎窍。

- `AscensionService`（服务端单例）负责身份、阶段、条件、副作用；客户端只提交意图。
- 会话目录绑定 `ServerLevel.getServer().getWorldPath(LevelResource.ROOT)/shengxian/sessions`；登录加载、登出/停服落盘、缓存清理。
- 受控本体适配：只允许 `ServerPlayer`；写入前调用 `markSyncDirty()`；依赖指纹不符拒绝路线；`zhuanshu` 只作为兼容字段，不作为升仙事实来源。
- 本命蛊唯一来源与托管：绑定/暂存/嵌入仙窍/旧版退回状态互斥，记录真实 ItemStack（含组件）；尚无依据的结果继续保留原物。
- 碎窍写入必须可追踪、可恢复；缺真实仙窍能力前不向真实玩家开放不可逆碎窍。
- 验收：非法发起不丢物品；重复碎窍/放蛊只产生一次副作用；不能绕过 StartGate。

### 阶段 4：三气与师法自然（本轮部分完成）

- 已完成：schema 3 持久化 risk/window；`QiEngine` 连续三气、有限人气、三向失衡、平衡衰减、交感窗口、每窗一次领悟、分心风险；`AscensionService.stepQi` 仅 ABSORBING 可步进，致命失衡提交 FAILED。
- 已接入（原型）：`/shengxianqi` 与 K 键界面提交有界意图，服务端按秒模拟；0.2.3 只接受已有 `ABSORBING` 会话并清理失败/离线意图。
- 0.2.3 历史状态：真实升仙会话仍被 StartGate 阻止；知识池/蛊方授予、按钮/按键实机交互和参数平衡仍待完成。

- 服务端每个模拟步推进：外部天气/地气储量、已纳三气、可用人气、失衡累积、交感窗口、领悟额度。
- 人气使用本项目独立记录，禁止复用 `renqi`；不得由声望/魅力/击杀产生。
- 参数可配置并标注来源（CANON-002/003/004 + 游戏参数）。
- 最小可操作界面：调气、求知、停止；关闭界面不暂停。
- 验收：三种失衡分别可复现；求知有收益也有风险；重登不刷收益。

### 阶段 5：灾劫、反噬与护道（本轮部分完成）

- 已完成：schema 4 持久化灾劫状态；`TribulationEngine` 以二气浓度触发音磬/荒兽载体，冲击累加风险，护道人击破载体，存活幂等标记且无掉落；`stepSimulation` 与三气同 tick 合并保存，致命提交 FAILED。
- 未完成：Minecraft 载体实体、区块加载/卸载语义、多人现场同步、用蛊反噬真实调用点、无限掉落防护的实体验收。已有三气/灾劫的基础可视 cue；Veil 三气 shader 见 0.2.1/0.2.2 记录。

- 每种灾劫必须有来源、载体、影响、应对、结束规则；先实现有正文依据的"八音天灾"与"荒兽地劫"原型（CANON-009/011），其余列缺口。
- 灾劫实体/载体与纳气并发；区块卸载不判死亡；护道人处理外劫但不能代调三气。
- 用蛊反噬接到真实用蛊调用点（CANON-008）；本命蛊保护待 GAP-004 核后再启用。
- 验收：外劫同时生效；护道有效但不越权；无无限掉落。

### 阶段 6：真实仙窍、仙元与六转兼容（本轮部分完成）

- 已完成：schema 5 仙窍账本（面积、时间比、仙元、内部时钟、地块），一次性形成与支出校验；不自动补寿元。
- 已完成（原型）：data-driven 空维度 shengxian:aperture、每仙窍 16x16 地块、主人进入/离开命令、地块建造标记。
- 未完成：实机验证维度加载/传送/建造/区块恢复、来访与护道人权限、内部时钟实际刻推进、六转结果统一提交与本体漂移对账。

- 独立有界空间、主人权限、入口/来访、内部时钟、面积/流派/生命状态、仙元账本；缩尺标为游戏映射。
- 统一提交仙窍与六转结果；用本项目记录对账 `zhuanshu`；检测并记录本体重置/降转（UPSTREAM-011/012）。
- 成仙不自动补寿元（项目约束；GAP-008）。
- 验收：完整普通路线产生可进出、可保存、可消耗仙元的仙窍；重启后仍成立。

### 阶段 7：余气升炼与特殊路线

- 余气升炼、内劫援助、本命蛊保护在 GAP-001/004 补齐后再实现。
- 仙蛊真实注册、能力、消耗（仙元/真元按核实）、饲养、唯一性。
- 十绝体按 CANON-007 独立 profile；神不知/仙僵/纯梦求真体等保持未启用并逐条核。
- 验收：普通路线通过不等于全部变体通过。

### 阶段 8：交互、联机与实机验收

- 客户端展示服务端快照；封包不接受客户端结果或他人身份。
- 独立服务器：双人护道/袭击、跨维度、区块卸载、退出重连、停服恢复、死亡复制、满背包、坏档、完整升仙。
- 验收：没有实机运行就明确写"未实机验证"。

## 4. 本阶段不做什么

- 不开放真实碎窍、不写玩家 `zhuanshu`、不生成仙窍/仙元/仙蛊。
- 不实现三气公式、灾劫实体、领悟知识池。
- 不修改本体、不安装真实实例、不改真实存档。
- 不把待核规则写成硬编码。

## 5. 阻塞与待核（会影响后续阶段）

- GAP-001：方正余气升炼/血雨灾劫/护道干预已在 CANON-013 核到，但只支持个例，独立 profile 与余气结算仍未实现 → 阶段 7 部分阻塞。
- GAP-002：神不知 → 特殊路线阻塞。
- GAP-004：本命蛊保护/仙蛊唯一性 → 阶段 5/7 阻塞。
- GAP-006：碎窍后未成仙的存活与重开窍 → 失败/恢复分支阻塞。
- GAP-007：仙僵/纯梦求真体/宿命 → 特殊路线阻塞。
- 技术：真实仙窍维度/空间实现需要独立设计与性能预算；在完成前，不可逆碎窍继续关闭。- 阶段 6 追加：返回点记忆与在线内部时钟 tick 已实现；未验证离线/区块/停服行为。
- 阶段 6 实机：隔离服务器已验证模组加载、命令注册、世界会话仓库、维度注册与维度内方块操作；玩家流程仍未验证。
- 玩家连接限制：无 NeoForge 客户端握手；FakePlayer 传送不可用。下一步需真实 NeoForge 客户端或实现握手/客户端自动化。
- 阶段 6 玩家流程：真实客户端 + 隔离夹具仙窍已验证进入/离开/传送/返回点/地块/在线 tick；真实升仙流程仍未接入。
- 阶段 4 玩家接口：真实客户端可用 /shengxianqi 设置受限三气意图并由服务端每秒步进；尚未 GUI，也未接入真实碎窍。
- 阶段 4 UI：K 键三气界面已渲染并截图；客户端载荷实测改变三气；按钮/Enter 交互未验收。
- 阶段 6：新增 formAperture（FORMING→IMMORTAL 一次提交），测试 48。
- 阶段 5：新增 FxPayload/AscensionFx/ClientFx；0.2.1 接入 Veil 着色器资源，0.2.2 将 Veil 4.5.2 作为 Jar-in-Jar 内嵌。
- 阶段 3：新增本命蛊唯一托管账本与 /shengxianbind；尚未作为形成门禁，也未做受控本体写入。
- 阶段 3：schema 6 本命蛊字段 + 形成门禁；真实物品来源/放蛊/受控写入仍未完成。
- 阶段 6：/shengxianform + 档位规划 + 地块预留；实机玩家流程与放蛊物品来源仍未完成。
- 阶段 6 实机：/shengxianbind → /shengxianform → 进入仙窍已用夹具 Gu 跑通；真实 Gu 来源/消耗仍未完成。
- 阶段 6：仙窍来访 invite/revoke/visit 实机通过；离线主人/方块权限/护道权限仍未完成。
- 阶段 6：离线主人来访实机通过；未登录过主人/离线撤销/方块权限仍未完成。
- 阶段 6：护道人名单与离线撤销；护道实机作用/方块权限仍未完成。
- 阶段 6：护道人进入/撤销实机通过；灾劫防护与方块权限仍未完成。

## 历史进度（0.3.2 实施中，2026-10-27）

- 灾劫会话新增维度、方块坐标和确定性实体 UUID；schema 8 可恢复锚点，schema 7 并发载体迁移为未放置状态。
- 服务端灾劫实体采用不可见 ArmorStand 命中体、名称与原版粒子；玩家攻击按载体 ID 结算，升仙者本人不能攻击自己的载体；右键和其他伤害不会装备/摧毁实体。
- 载体只会在已加载的目标区块生成；区块未加载不判死；崩溃后按持久化 UUID 恢复，状态结束后实体自行清理。此实现只是可交互的原版可视锚点，不代表荒兽 AI 或普通路线灾劫目录已按原著完整还原。
- 失败后果在 readiness 关闭时只保留并告警，不写入本体失窍/死亡；碎窍冲击开始后不允许回到 PREPARING，登录恢复幂等补齐已提交的真元扣费。
- GU_BACKLASH 已加入必需能力清单。普通灾劫通则、反噬具体损失/保护规则和所有特殊路线缺口仍未核，升仙门禁保持关闭。
- 中等福地的游戏倍率区间已放宽到 5..16，以包含已核方源个例 1:16；它仍只是游戏映射，不声称该倍率公式是原著通则。
- 当前核心回归 114 项通过；真实客户端/多人护道和停服恢复仍需隔离实机验收。完整打包与服务器启动将在 0.3.2 验证记录中结项。


## 历史进度（0.3.4，2026-10-27）

- 灾劫锚点持久化维度、坐标和确定性实体 UUID；schema 8 写入，schema 7 多载体状态迁移为未放置锚点。
- 世界端锚点用不可见 ArmorStand、名字和原版粒子显示；击退要求服务器配置已加载并启用，且由升仙者本人邀请；升仙者不能击退自己的载体。交互/伤害拦截、已加载区块对账及登录/服务器 tick 恢复已接线。
- 护道人授权在形成前按主人保存；形成后授权与地块权限单次落盘，写入失败回滚；成窍提交、恢复和主人登录均补做地块名单同步。
- 碎窍失败和登录恢复边界已收紧：能力门禁关闭时不再由旧失败记录自动写本体失窍/死亡；已提交真元目标幂等补齐；完全碎窍后不能取消或重试。
- 构建脚本现在把 `-ModVersion` 写入暂存元数据，避免把静态 0.3.1 版本写入后续版本产物。
- 0.3.4 核心回归 120/120，SourceCheck、Build、Jar Verify 均通过；隔离空世界服务端加载 0.3.4 并正常保存关闭。启动日志还包含本体既有的 deleted_mod_element、huyao_chestplate、zijingsheligu 引用错误。
- **整条原著路线未完成**：生产能力集合仍为空。普通路线灾劫通则、用蛊反噬具体损失/保护、余气升炼结算、实机客户端与多人/重启验收仍未解决；门禁保持关闭。详见 `docs/verification-0.3.4.md`。

