# 0.3.21 验证记录：仙蛊最小闭环

日期：2026-10-29。基线 0.3.20。普通升仙入口继续关闭；本版只实现 CANON-009 展示过的仙蛊行为。

## 变更

| 文件 | 内容 |
| --- | --- |
| `core/XianGu.java` | 仙蛊定义：id、名称、仙元消耗、效果、原著编号 |
| `core/XianGuCatalog.java` | 目录，只有 `pai_nan`（排难仙蛊，CANON-009），消耗 5.0 仙元 |
| `core/XianGuLedger.java` | 持有记录，写进会话追加式效果账本（owned/used 计数） |
| `server/XianGuService.java` | `use`：校验阶段/持有/载体/仙元后，一次 `mutate` 内完成扣仙元 + 摧毁载体 + 计数消耗；`grant`：管理员发放 |
| `command/XianGuCommand.java` | `/shengxiangu list`、`use <id>`、`grant <目标> <id>`（需权限 2） |

## 0.3.20 的缺陷与本版修复

0.3.20 用 `withoutAppliedEffect` 在催动后删除持有键，实机直接失败：

```
java.lang.IllegalStateException: Applied-effect ledger cannot shrink
  at AscensionProgress.requireMonotonicFrom(AscensionProgress.java:467)
  at AscensionService.mutate(AscensionService.java:73)
  at XianGuService.use(XianGuService.java:78)
```

会话账本只能追加，不能删除。0.3.21 改成 owned/used 计数对：持有 = `owned > used`，消耗只是再追加一条 used。0.3.20 的产物保留，未覆盖。

## 回归

新增 4 项（168 → 172）：目录只有原著条目、持有/消耗计数与再次发放、催动扣仙元并摧毁一个载体、定义拒绝非法字段。

```
& ./build.ps1 -Action Test                 → 172 passed, 0 failed
& ./build.ps1 -Action Build -ModVersion 0.3.21
  → 4 个催蛊钩子、22+22 项 NBT、158 个 common-side 类、Veil 4.5.2
  → SHA256 ec4aaec8a654a29df9a7ccd8fdb5abeb5a9fe17d4160ced8e6aa25f96befc8a0
```

产物：`build/libs/shengxian-0.3.21.jar`，3,624,859 字节。

## 实机验证（隔离服务器 + FlowBot，一次性 smoke 构建）

发布态下门禁关闭且生产 profile 为 `UNRESOLVED_ORDINARY`，不会出现载体，因此用一次性 smoke 构建（临时开门禁 + 太白云生 profile，验证后删除）取得载体。

| 步骤 | 命令 | 结果 |
| --- | --- | --- |
| 发放 | `shengxiangu grant FlowBot pai_nan` | 账本新增 `xiangu:owned:pai_nan:1` |
| 催动 | `execute as FlowBot run shengxiangu use pai_nan` | “排难之光击退了灾劫载体；该仙蛊已损毁。” |
| 账本 | 会话文件 | 仙元 51.56 → 46.56（−5.0）；新增 `xiangu:used:pai_nan:1` |
| 重复催动 | 再次 use | “你没有持有该仙蛊。” |

补充说明：被摧毁的柳风载体在同一 tick 之后由同一 profile 重新生成（外部二气仍为 120），因此载体数量仍为 2，但 id 已变（`heaven-152521`）。这是太白云生个例 profile 的既有行为，不是仙蛊失效。

## 边界

- 没有炼成路径；`grant` 需要管理员权限。
- 唯一性只按会话成立，世界唯一性未核（GAP-004）。
- 只实现“摧毁载体”一种效果；其他仙蛊不存在。
- 双瞳爆裂等个例代价未实现。
- 未验证：多人同时催动、重启后的计数一致性、与失败结算的交互。
