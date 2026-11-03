# 0.3.92 普通升仙门禁默认开启（字段边界重新归类）

日期：2026-11-02。对应决策 PROJECT-003，取代 PROJECT-002 的开关方向。

## 问题

普通升仙路线一直默认关闭。关闭理由只有一个：`RuleProfile.Capability.UPSTREAM_APERTURE_WRITE` 不在 `implementedCapabilities()` 里。而这一项要求的动作是"把仙窍写回本体玩家数据"。

这个条件按定义无法完成。UPSTREAM-010 早已记录本体 `PlayerVariables` 没有仙窍/仙元字段；把一个不可能满足的条件留在必需集里，结果是整条路线永久不可用，却不会因此让实现更接近原著——它只把"做不到的事"伪装成"还没做的事"。

## 复核（本次独立重做，不引用旧结论）

- 外层 JAR：`D:\cxdownload\guzhenrn蛊真人模组本体10.20.jar`，实测 SHA256 `3733c20ffbd16d9068bb8188d45b24a0d61b279d7cb6fdc8ea341938a7f4cf04`，与 `docs/evidence/upstream-audit.md` 一致。
- 内层本体：`.cache/guzhenren-0.1-neoforge-1.21.1.jar`，106,304,539 字节。
- 字段穷举：`build/decompiled/net/guzhenren/network/GuzhenrenModVariables$PlayerVariables.java`，411 处字段声明，对 `xianqiao|xianyuan|aperture|essence|dongtian|fudi|immortal|仙窍|仙元` 的匹配结果为**空**。
- 仅有的相关存储：凡人 `zhenyuan`/`zuida_zhenyuan`、分类字段 `kongqiao`、布尔 `shiqiao`。UPSTREAM-006/008 已明确禁止把这三者当仙窍状态复用。
- `build/refs/GuzhenrenModVariables.javap.txt` 反向印证：唯一含 `fudi` 的成员是 `fudichouxin:D`，与仙窍无关。

结论：`UPSTREAM_APERTURE_WRITE` 是**已审计字段边界**，不是未完成工作。

## 处理

- `RuleProfile` 新增 `ordinaryRequired(boolean requireUpstreamApertureWrite)`：默认集为除边界外的 7 项；`true` 时恢复 8 项（严格模式）。`ordinary()` 改用默认集。
- `AscensionReadiness.evaluate(implemented, required)` 新增必需集重载；单参版本委托默认集。core 不读配置，纯 Java 测试仍可独立运行。
- `StartGate.check` 由遍历 `Capability.values()` 改为遍历**显式必需集**。这是此前的隐藏第二道门禁：两处都强制第 8 项，只改一处会留下半开的门。
- `AscensionService`、`SessionStore` 增加必需集构造参数并向下透传；服务端由 `GuzhenrenPlayerBridge.requiredCapabilities()` 统一提供。
- `ShengxianConfig`：移除 `apertureLedgerOnly`，新增 `requireUpstreamApertureWrite`（默认 false）。旧配置文件的 `apertureLedgerOnly` 行被 NeoForge 忽略，无需迁移脚本。
- `GuzhenrenPlayerBridge.capabilities()` 不再虚填边界——能力集只报告本模组真能提供的项，严格度全部由必需集表达。

## 验证

| 项目 | 结果 |
| --- | --- |
| 核心回归 `-Action Test` | **210 项通过，0 失败**（原 209，+1 严格模式迁移测试） |
| 完整构建 `-Action Build -ModVersion 0.3.92` | 通过，exit 0 |
| 产物校验 `-Action Verify -ModVersion 0.3.92` | 通过，exit 0 |
| 静态依赖边界 | 275 个 class 文件 |
| 内嵌 Veil | 4.5.2（SHA256 `e372d963…8d66`） |

产物：`build/libs/shengxian-0.3.92.jar`
SHA256：`3428128e007d40b4407c4b971f946532cedbd2417c325668474783467de7e907`

清理后复验：`Verify`、`SourceCheck`、`Test` 三项在删除约 3.6 GB 构建残留后重新执行，全部 exit 0，产物哈希不变——证明被删内容均非构建输入。

## 构建产物回收（同轮，不改变模组行为）

- `build/runs/<GUID>/` 是每次调用的暂存目录，此前从不清理，积压 **170 个 / 582 MB**。现于 `Build`/`Test`/`SourceCheck`/`NbtCheck`/`Verify` **成功**收尾时清空；**失败的一次保留**供排查。
- 新增 `-Action Clean`：清 `build/runs`、删除 `.cache` 本体 jar 的可重生成副本（`build/upstream-extract`、`build/upstream-classes`），并剪枝发布产物。
- 发布产物改为**滚动保留最近 3 个版本**（0.3.90/0.3.91/0.3.92）。理由：旧 jar 无法重建（源码不随版本留存），全删等于销毁唯一副本，全留则无限增长；剪枝只针对 `shengxian-<x.y.z>.jar` 与 `.sha256`，内嵌 Veil jar 与 `metadata.json` 作为输入永不剪枝。
- 另删除 25 个一次性验收/测试环境目录（`test-server*`、`test-client*`、`bot`、`ref-5.4b`、`ref-5.8`、`visual-review-*`、各类 `*-preview` 等）与根目录陈旧解包副本（`net/`、`META-INF/`、`assets/`、`data/` 等）。删除前已确认无脚本按路径依赖，且 `src/main/resources` 持有正本。
- 保留：`.cache`（本体 jar）、`build/optional-deps`（Photon/LDLib2 可选依赖，构建输入）、`build/decompiled` 与 `build/refs`（审计证据）、根目录 `logs/`、`config/`、`shengxian.visual.mixins.json`（后者仍被 `tools/analyze_visual_jar_*.py` 引用）。
- 结果：`build/` 由 3.65 GB 降至约 35 MB。策略已写入 `README.md` 构建章节。

新增/改写的断言：

- 默认必需集不含 `UPSTREAM_APERTURE_WRITE`；`implementedCapabilities()` 现在满足默认路线（`available()==true`，缺失集为空）。
- 严格模式 `ordinaryRequired(true)` 下缺失集恰为 `{UPSTREAM_APERTURE_WRITE}`，消息含"本体空窍状态写入"。
- `StartGate` 严格模式拒绝、默认模式放行同一组输入。
- 原有 `Set.of()` 全缺、`ALL` 全足、不可变缺失集等断言保持通过与原意不变。

## 边界

- 本次没有新增任何原著能力，也没有把 GAP-001/004/006 变成"已核"。灾劫目录、反噬保护差异、失败后存活规则仍是证据边界，口径见 `docs/evidence/canon-rules.md`。
- 升仙流程、仙窍账本字段、失败结算、六转写入、视觉层全部未改动。
- 未启动游戏或服务器。门禁默认开启后的**实机**表现（新角色能否走完三步、多人重启、死亡后不可逆后果）本次未验收；上一次真机端到端记录是 0.3.62，当时用 `apertureLedgerOnly=true` 绕过同一门禁。严格模式的存在不改变这一点。
