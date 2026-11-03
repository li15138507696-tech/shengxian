# 0.3.19 验证记录：仙窍维持消耗（仙元的第一笔支出）

日期：2026-10-29。基线 0.3.18。普通升仙入口继续关闭；本版只给仙窍账本增加一笔真实支出，不改变任何原著规则。

## 变更

| 文件 | 变更 |
| --- | --- |
| `core/ApertureEngine.java` | 新增四参数 `advanceInternal(current, serverTicks, essencePerInternalTick, upkeepPerInternalTick)`；三参数重载委托为 `upkeep = 0`，旧行为不变 |
| `ShengxianConfig.java` | 新增 `apertureUpkeepPerInternalTick`，默认 **0.004**，范围 0–1 |
| `server/ApertureTickService.java` | 每秒推进时传入维持速率 |

语义：**先扣维持，再算产出**。余额不足以支付本次维持时，内部时钟与产出都不前进（停摆），因此余额永远不会变成负数，也不会凭空产出仙元。

## 证据状态

CANON-005/006 只说福地会酿出青提仙元，**没有任何读到的章节说福地有运转成本**。维持速率是纯平衡参数，用于让账本有真实支出；不得写成原著规则。

## 回归

新增 4 项（`FrameworkTest` 164 → 168）：

1. 维持先于产出扣除（10.0 + 16×0.01 − 16×0.004 = 10.096）；
2. 余额不足则停摆，且余额正好等于维持时能支付并只产出；
3. 维持为 0 时与旧三参数路径逐位相同；
4. 负值 / NaN 维持被拒绝，未形成的账本不受影响。

```
& ./build.ps1 -Action Test
Framework tests: 168 passed, 0 failed

& ./build.ps1 -Action Build -ModVersion 0.3.19
Artifact verified: build/libs/shengxian-0.3.19.jar
Embedded Veil: 4.5.2 (e372d963…)
SHA256: 84d50ccd6d40c6ad102a78185fa4d47742bb91cab330384418ca20f529d91e97
```

产物：`build/libs/shengxian-0.3.19.jar`，3,611,465 字节。

## 实机验证（隔离服务器 + FlowBot 客户端）

`/shengxian aperture enter` 由 RCON 以 FlowBot 身份执行，进入后按秒采样会话账本：

| 时刻 | 仙元 | 内部时钟 |
| --- | --- | --- |
| t0 | 25.52 | 420 |
| t1（12 秒后） | 35.60 | 2100 |

- 内部时钟增量 1680 = 7.0（时间比）× 20 × 12 秒，与服务端每秒推进 20 tick 一致。
- 仙元增量 10.08 = 1680 × (0.01 − 0.004)，**正好是扣除维持后的净值**；若没有维持应为 16.8。

这证明维持消耗在真实服务器上生效，而不只是单元测试里的算术。

## 边界与未做

- 默认维持（0.004）低于产出（0.01），所以实机中仙窍始终盈余，**停摆路径只有提高配置才可达**；这是有意的保守默认值。
- 没有 UI 展示维持速率或净收支（仙窍页仍只显示仙元余额）。
- 仙元仍没有其他支出入口，也没有其他收入来源；完整经济（收入/支出/破产后果）未设计。
- 没有改动 `CANONICAL_FAILURE`、`GU_BACKLASH` 或 readiness；八项能力仍全关。
- 实机只覆盖单人单仙窍；多人、离线、停摆后的恢复未验证。
