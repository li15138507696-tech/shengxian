# 本体蛊激活调用点审计

日期：2026-10-28。此页记录静态字节码/反编译核验结果，供后续精确接入用蛊反噬；它不提供损毁概率、保护规则或原著判定。

## 检查对象与边界

- 上游内层模组：guzhenren-0.1-neoforge-1.21.1.jar
- SHA256：b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc
- 审计脚本：tools/audit-gu-activations.ps1 与 tools/AuditGuActivations.java
- 可复现：pwsh -NoProfile -File ./tools/audit-gu-activations.ps1
- 审计按实际 JAR 指纹拒绝其他文件；只解析 class bytes 和登记表，不加载本体游戏类，不启动 Minecraft、客户端或服务器。
- 发现计数是静态候选调用点，不等于玩家成功使用次数，也不代表反噬覆盖率。

## 入口不是一个统一右键事件

- 月光蛊 YueGuangGuItem.use 调用对应位置判定 Procedure 后返回原 super.use 结果。Procedure 内还含喂养分支；只有通过静默、食物、耐久和真元条件的分支才进入催动，并调用 GuChongCDProcedure，随后排队生成弹道并扣除真元。Procedure 入口、物品 use 返回值和右键事件都不足以判定成功。
- 白象蛇蛊 BaiXiangSheGuItem.use 也调用 Procedure 后保留原返回结果。正确主手、无喂养物、蛊虫耐久为零且真元满足条件后，才扣真元、移除物品并生成白象仙蛇；它不调用 GuChongCDProcedure。生成结果另有空值检查。
- 哀别持攻击入口在 AiBieChiItem.hurtEnemy，进入攻击目标命中 Procedure。成功分支扣蛊耐久并加冷却。上游 Procedure 将真元差值写到 target 的变量，取值却来自 attacker；不能默认所有 Procedure 都把消耗写回使用者。
- 定蛊二号通过 Item.useOn 的方块交互 Procedure，检查沉默与真元后扣真元、加冷却并放置铁炼鼎；只覆盖右键空气会漏掉它。
- 小光蛊的有效维护位于 inventoryTick Procedure。它要求未被沉默、玩家一转、qi_yong 附魔启用且真元足够；拥有蛊虫或发生背包 tick 不等于有效催动。
- 三招按键由网络消息服务器侧 pressAction 进入，事件类型和已加载区块通过检查后再调用杀招 Procedure。杀招使用玩家变量中的多组 ItemStack，不一定对应当前手持物品。
- PuGongMessage 与多个攻击/方块/挥击 Procedure 是独立入口；收包本身不证明被接受或成功。

## 可复核发现规模

- rank 标签覆盖 154 个物品 ID，全部映射到具体物品类；在 4,806 个登记初始化位置找到匹配实例。
- 标签之外另有 3,116 个物品类含 wrapper Procedure 调用；静态扫描记录 1,860 个含信号调用的 Procedure 方法及 14 个按键入口候选。
- 原始调用关系共找到 656 个类引用 GuChongCDProcedure，其中包含该工具类自身。冷却只是冷却操作，不是通用成功事件。
- 清单只做发现与分类；代表案例必须逐个核验具体接受条件、扣费对象与效果发生时点。没有发现可证实为全体玩家通用的激活成功 API，也未能确认内层 JAR 中存在可安全归类的仙蛊物品类。

## CANON-008 对实现窗口的新增约束

第 687 节记载升仙期间天地二气剧烈波动时凡蛊、仙蛊均受反噬，并记有方源损失大量凡蛊；第 625 章则把方正催蛊遭反噬及蛊虫毁灭写在升仙力量托举、心神无防备的阶段。原文支持真实反噬与蛊损失，不提供通用精确阶段边界、概率或逐蛊损失率。实现必须继续追踪真实接受催动的调用点，并由明确的升仙状态控制风险窗口；不得把所有阶段、所有物品 tick 或所有失败尝试都当成催蛊成功，也不得因本体事件入口已找到就发明毁损概率。

本体入口审计和这两段原著现象分别见 [canon-rules.md 的 CANON-008 / GAP-004](canon-rules.md)。中文章节页为网络转载，非官方校订版本：第 687 节 https://www.hetubook.com/book/7640/5345922.html；第 625 章 https://www.hetubook.com/book/7640/5346884.html。
## 后续接入约束

若实施反噬，必须绑定本页所列上游哈希并在具体接受分支接入，限制为服务端 ServerPlayer，逐入口界定“接受催动”还是“效果实际完成”。月光蛊延迟弹道与白象蛇蛊生成结果若要求“实际完成”语义，还需在各自结果点分别处理。禁止把全局 right-click、wrapper Procedure 入口/返回、冷却 helper 或背包 tick 当成成功的完整覆盖。

原著 CANON-008/GAP-004 目前只支持升仙期间催蛊可能伴随反噬和蛊虫损失的定性风险；具体时间窗、概率、毁损判定、仙蛊与本命蛊保护仍未核。不得根据本审计发明概率、销毁或免疫规则。readiness 保持关闭。

## 2026-10-28 阶段 2 增量：可追踪事件层（未接线）

本轮把"什么算一次真实催动"落成代码与测试，但**没有安装任何钩子**，因此游戏内不会产生事件，也不会改变任何玩家状态。

### 已核验的接线目标（按实际 JAR 指纹）

用 `javap` 从 `guzhenren-0.1-neoforge-1.21.1.jar`（`b6d314d3…`）读到的精确签名：

| 入口 | 类 | 方法描述符 |
| --- | --- | --- |
| 右键催动 | `net.guzhenren.item.YueGuangGuItem` | `use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;` |
| 右键催动 | `net.guzhenren.item.BaiXiangSheGuItem` | 同上 |
| 攻击命中 | `net.guzhenren.item.AiBieChiItem` | `hurtEnemy(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z` |
| 方块使用 | `net.guzhenren.item.DingGu2Item` | `useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;` |
| 背包维护 | `net.guzhenren.item.XiaoguangguItem` | `inventoryTick(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V` |
| 杀招按键 | 上游网络消息（`pressAction` 路径） | 本轮未定位到单一类名，仍需按消息类型逐个确认 |

`use` 与 `inventoryTick` 在这些类里同时存在，所以**不能**用"类被调用"当成功；必须落到具体分支。

### 领域层

- `core/GuActivation`：`owner / guId / Entry / Outcome / serverTick / sequence`；`Entry` 区分六种入口，`Outcome` 区分 `ACCEPTED`（分支已扣费）与 `COMPLETED`（结果已存在，例如延迟弹道生成、白象仙蛇生成）。
- `core/GuActivationLedger`：按 owner 有界存储，`key()` 幂等；`REJECTED` 永不入库。
- `core/GuActivationPolicy`：喂养、持有、纯背包 tick 不算催动；`mayApplyBacklash` 恒为 `false`（GAP-004 未核）。
- `core/UpstreamFingerprint`：固定外层 `3733c20f…` 与内层 `b6d314d3…`，接线前必须匹配。

### 仍未完成

- 未安装 mixin 或事件钩子：真实分支尚未把事件写入 ledger。
- 未实现反噬、损失、本命蛊保护；`GU_BACKLASH` 能力保持关闭。
- 杀招按键入口的类定位与"接受"判定未完成。

## 2026-10-28 阶段 2 增量：0.3.14 四条接受催动观察钩子

0.3.14 将下列四个已审计 Procedure 接入 common Mixin。注入点均为目标 execute 方法中唯一的 PlayerVariables.zhenyuan PUTFIELD 之后；回调描述符与目标方法的全部参数逐项匹配：

| 入口 | Procedure 类 | 记录入口 |
| --- | --- | --- |
| 右键催动 | YueGuangGuDangYouJiKongQiShiShiTiDeWeiZhiProcedure | RIGHT_CLICK_USE |
| 右键催动 | BaiXiangSheGuYouJiKongQiShiShiTiDeWeiZhiProcedure | RIGHT_CLICK_USE |
| 攻击命中 | AiBieChiDangShengWuShiTiBeiGaiWuPinJiZhongProcedure | ATTACK_HURT_ENEMY；所有者按攻击者实体记录 |
| 方块使用 | DingGu2YouJiFangKuaiShiFangKuaiDeWeiZhiProcedure | BLOCK_USE_ON |

GuActivationObserver 只接受服务端 ServerPlayer，只在其 first 会话处于 BREAKING、BROKEN、ABSORBING、OPENING 或 FORMING 时记入 ACCEPTED。配置绑定上游内层 JAR 的精确指纹，构建时用 ASM 核对类、描述符、唯一真元写入、Mixin 注解目标、完整回调方法描述符与 PUTFIELD/AFTER 注入点；产物校验要求四个类和配置齐备、TOML 只注册一次。

这四个点只证明上游分支已接受催动并写入真元，不证明延迟弹道、召唤或方块效果最终完成。当前仅四种入口可观察，杀招按键、被动维护及其余 Procedure 未接线。事件留在服务器内存账本；没有反噬、扣除/销毁蛊虫、概率、本命蛊保护或任何升仙结果修改。GU_BACKLASH 与普通路线门禁继续关闭。游戏运行时 Mixin 引导、客户端/专用服务器加载和多人行为未验证。

离线验证详见 verification-0.3.14.md。
## 2026-10-28 阶段 2 增量：0.3.15 全局 owner 上限

观察事件与递增序号现在存放在同一 owner 状态中，默认上限为 4096 位最近使用的 owner，每位最多保留 64 条事件。新 owner 到达上限时，最近最少访问的 owner 状态连同事件去重键与序号一起淘汰；后续重新出现时从序号 1 开始。单条事件出队或 owner 被淘汰后，其去重键即释放，因此去重只覆盖仍保留在内存中的事件，不提供永久恰好一次保证。服务器停止时整体清空。

这是有界、易失的观察数据，不是可用于灾劫结算的持久事实；淘汰不触发惩罚，也不能被解释为蛊虫损失。回归覆盖 owner 淘汰和序号重置。当前回调与服务端生命周期清理均在服务端线程执行，账本本身不加锁，其他线程不得调用。详见 verification-0.3.15.md。

## 2026-10-29 阶段增量：14 个杀招按键入口及成本信号

离线审计器现将 `GETFIELD` 门槛读取与 `PUTFIELD` 写入分开标记，并把 `pressAction` 的直接 Procedure 调用关联到 `targetProcedureSignals`。脚本先校验上游内层 JAR SHA256 `b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc`，不加载游戏类、不启动游戏。当前为 14 个按键入口、1,881 个含已识别信号的 Procedure 方法；均是静态发现数量，不代表成功使用次数或覆盖率。

| 按键消息 | 直接调用的 Procedure | 已发现的耐久/冷却字段族 |
| --- | --- | --- |
| `FfffffMessage` | `FERFProcedure` | 未发现本审计器识别的信号 |
| `HhoushanMessage` | `HhoushanliuchengProcedure` | 未发现本审计器识别的信号 |
| `LianGuMessage` | `LianGuAnXiaAnJianShiProcedure` | 未发现本审计器识别的信号 |
| `PuGongMessage` | `PuGongAnXiaAnJianShiProcedure` | 未发现本审计器识别的信号 |
| `RwLMessage` | `RwLAnXiaAnJianShiProcedure` | 未发现本审计器识别的信号 |
| `SanZhaoAnJianCMessage` | `SanZhaoAnJianCAnXiaAnJianShiProcedure` | `ShaZhao1_*`、`ShaZhao3_*` 耐久读取/写入；`sanzhao_CD2` 读取/写入 |
| `SanZhaoAnJianVMessage` | `SanZhaoAnJianVAnXiaAnJianShiProcedure` | `ShaZhao1_*`、`ShaZhao4_*` 耐久读取/写入；`sanzhao_CD3` 读取/写入 |
| `SanZhaoAnJianXMessage` | `SanZhaoAnJianXAnXiaAnJianShiProcedure` | `ShaZhao1_*`、`ShaZhao2_*` 耐久读取/写入；`sanzhao_CD1` 读取/写入 |
| `SanZhaoAnJianZMessage` | `SanZhaoAnJianZAnXiaAnJianShiProcedure` | `ShaZhao1_*` 耐久读取/写入；`sanzhao_CD` 读取/写入 |
| `ShanbiwMessage` | `ShanbiwAnXiaAnJianShiProcedure` | 未发现本审计器识别的信号 |
| `UigushijiemiandakaiMessage` | `UigushijiemiandakaiAnXiaAnJianShiProcedure` | 未发现本审计器识别的信号 |
| `YoushanMessage` | `YyoushanliuchengProcedure` | 未发现本审计器识别的信号 |
| `ZhanDouMoShiMessage` | `ZhanDouMoShiAnXiaAnJianShiProcedure` | 未发现本审计器识别的信号 |
| `ZuishanMessage` | `ZzuoshanLIUCHENGProcedure` | 未发现本审计器识别的信号 |

对 C/V/X/Z 四个 Procedure 的静态反编译显示：各自先检查对应组的十个 `_NaiJiu` 计数低于 `102.0`，再检查对应冷却字段不大于 `0`；选中具体招式后调用子 Procedure，随后写入冷却。过程同时读取、写入上表中的耐久字段族。故这些过程比“消息已收包”提供了更强的接受/成本线索，但审计器仍将所有 14 条 `acceptedUseVerified` 保持为 `false`。

上游 `PlayerVariables` 把 `ShaZhao*_GuChong1..10` 声明为 `ItemStack`，而杀招过程直接写对应的 `_NaiJiu` 数值字段。全本体类交叉引用扫描找到 80 个触及这些物品栈的方法，其中 15 个方法也在同一方法中访问了配对耐久字段。已反编译的第一组 GUI 维护过程会把 `ShaZhao1_GuChong1..10` 复制到菜单槽 0..9，用同名 `_NaiJiu` 设置栈伤害值，并在计数达到 `102.0` 时移除该槽物品；第二至第四组也各有对应的 GUI 维护过程和同名字段配对。由此确认耐久字段确实映射到对应蛊槽，而非仅靠字段命名推断。

C/V/X/Z 杀招 Procedure 的**方法级字段引用**分别涉及组 1+3、1+4、1+2、1；这只是整段方法的静态集合，不代表每个招式分支都会磨损整组。比如单个分支可以只写其中一个槽的 `_NaiJiu`。审计器仍将所有 14 条 `acceptedUseVerified` 保持为 `false`；不应把方法级引用集合当成执行记录。`GU_BACKLASH`、普通路线与 readiness 继续关闭。

## 2026-10-29 阶段增量：C/V/X/Z 杀招按键的实际耐久差值观察

C/V/X/Z 现在各有一个过程观察 Mixin：在上游 Procedure 入口快照四组共 40 个 `_NaiJiu` 计数，在返回时逐槽比较，仅把发生变化且对应 `ItemStack` 非空的蛊记入既有服务端内存账本。这样按实际执行分支归属组别和槽位，不再把同一按键下未磨损的其他蛊误记为已催动。观察只读玩家数据，不改蛊虫、真元或升仙状态；计数变化说明上游过程写入了耐久，不保证其子 Procedure 最终产生了可见效果。

VerifyGuActivationHooks 核对四组过程的 HEAD/RETURN 注入、方法签名、观察调用与按键值；与原四个蛊激活点合计 8 个静态目标通过。173 项 FrameworkTest 通过，SourceCheck 全源码编译和 8 个钩子静态检查通过。随后在隔离 NeoForge 专用服中用临时探针加载四个目标类，debug.log 确认四个 Mixin 均已应用；这只证明类转换及注入点可加载，未执行真实按键或玩家路径，详见 docs/evidence/verification-0.3.21-killer-mixin-smoke.md。GU_BACKLASH、普通升仙路线和 readiness 仍关闭。

## 2026-10-29 阶段增量：具名入口的纳气期累计反噬与蛊损失

本增量将“确认成功的催动”接入游戏化累计规则。只在服务端 `ABSORBING` 会话中加压，每个 `COMPLETED` 事件 +1，默认阈值 8（服务器参数范围 1–64）；配置阈值在 session 首次使用时冻结。CANON-008/GAP-004 没有给出这些数值或通用公式，因此均是游戏参数，不作为原著常数。

- 月光蛊在 `lambda$execute$1(Entity)` 的 `Level.addFreshEntity` 成功后提交延迟完成事件；拒绝生成则丢弃待处理记录。此处的 `$1` 是弹道生成分支，`$2` 是延迟音效分支。
- 白象蛇蛊只在 `EntityType.spawn` 成功后、位于成功分支中的 `setDeltaMovement` 结果点提交。
- 哀别持在 Procedure 正常返回时提交，定蛊二号只在 `LevelAccessor.setBlock` 返回 `true` 时提交。
- C/V/X/Z 杀招只把方法返回时耐久实际变化的组/槽提交，绑定过程入口保存的同一 `ItemStack` 对象和精确 `ShaZhaoN_GuChongM` 字段；其他未核按键、wrapper、被动 tick 和 Procedure 不参与。

达到阈值时只尝试从触发该次事件的确切槽位减 1，不替换目标。普通背包按对象身份在 main/armor/offhand 中唯一解析；杀招槽按已核组/槽访问。若目标已被本体正常消耗、槽位发生替换或归属不清，阈值照样消费并记为 `TARGET_UNAVAILABLE`，不会改动其他蛊。

本命蛊保护要求升仙绑定指纹与上游 `PlayerVariables.BenMingGu` 镜像指纹一致，再保护所有规范化内容指纹相同的副本。损失事务将 pressure、PREPARED/COMMITTED 回执和物品数量变化写入同一玩家 NBT；写前、写后核验 UUID 玩家文件，单人房主另核验 `level.dat/Data/Player`。遗留 PREPARED、来源不一致或保存异常进入 UNCERTAIN/quarantine，登录时不补做损失。

截至此阶段，FrameworkTest 188/188，NbtCheck 与 SourceCheck 通过；hook verifier 在精确上游内层 JAR SHA256 `b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc` 上核验 8 个已接入口，Gu 事务验证 31 项压缩 NBT 检查。没有运行游戏；真实右键/攻击/方块/杀招、客户端同步、多人、重登与崩溃行为仍未实机验收。未分类入口仍无惩罚，`AscensionReadiness.implementedCapabilities()` 仍为空。验证记录：`docs/verification-gu-backlash.md`。
