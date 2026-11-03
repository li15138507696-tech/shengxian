# 0.3.34 验证记录：去掉个例门禁（"不要特例"）

日期：2026-11-01。基线 0.3.32（0.3.33 为同一批改动的中间构建）。

## 用户指示

> 不要特例。

此前多处把经证实的**个别案例**当成许可开关：普通路线没有灾劫，只有"某某 witnessed"才有；内劫与余气升炼只在特定 profile 下允许。这些都是把个例当门禁，本轮全部拆掉。

## 改动

### 1. 灾劫引擎：普通路线与个例走同一套规则

`TribulationEngine.startIfDue` 原来 `UNRESOLVED_ORDINARY -> 无载体`，与 `TAI_BAI_WITNESSED` 分支互斥。

现在 `UNRESOLVED_ORDINARY` 与 `TAI_BAI_WITNESSED` **共用** `startFromAmbientQi`：天气浓度达阈值一半 → 凝结柳风载体；地气达阈值 → 凝结峥虎载体；各自消耗该阈值。个例只是这套机制的实例，不再拥有独占许可。

`BLACK_LOU_LAN_WITNESSED`（音磬→荒兽序列）与 `FANG_ZHENG_WITNESSED`（血雨）保留为**参数变体**，不再是"只有他们能出灾劫"。

### 2. 内劫（CANON-012）不再要求个例 profile

`InnerTribulation.profileAllows` 恒真：立窍后只要**余气真的还在**，就在仙窍内凝结第二场灾劫。触发条件由状态决定，不由身份决定。

### 3. 余气升炼（CANON-013）不再要求个例 profile

`LeftoverRefinement.profileAllows` 恒真：只要余天气与余地气都达阈值就能升炼一次。
产出的仙蛊也从"某个人的专属蛊"改为通用的 `leftover_xian_gu`「余气仙蛊（效果未核）」（原文无名无效，仍是 `Effect.UNSPECIFIED`，可持有不可催动）。

### 4. 门禁不再谎报

`AscensionReadiness.implementedCapabilities()` 原来返回**空集**，等于宣称 8 项能力全未实现。实际上只有 1 项真的缺：

| 能力 | 状态 |
| --- | --- |
| BREAK_APERTURE / CONTROL_QI / FORM_APERTURE / IMMORTAL_ESSENCE | 已实现 |
| CANONICAL_TRIBULATION / CANONICAL_FAILURE / GU_BACKLASH | 已实现 |
| UPSTREAM_APERTURE_WRITE | **真缺**：UPSTREAM-010 查明本体没有仙窍/仙元字段，模块只能记世界侧账本 |

现在如实返回 7 项，门禁消息从 8 条假理由变成 1 条真理由。

### 5. 唯一缺口做成显式开关，而不是隐藏特例

新增服务端配置 `ascension.apertureLedgerOnly`（默认 **false**）：

- false：普通路线关闭，提示"本体空窍状态写入尚未接入"——诚实反映 UPSTREAM-010。
- true：运维显式接受"升仙只存在于本模组账本、本体不会知道"，路线开启。

这样没有隐藏的空白名单，也没有替用户偷偷开门。

### 6. 冲击失败的原因校验去掉 profile 白名单

`hasVerifiedTribulationImpactCause` 原来只认三个 witnessed profile，现在只排除内部标记 `NOT_FROZEN`：身份不再决定某个失败原因算不算数。

## 验证

```
Framework tests: 204 passed, 0 failed
Artifact verified: shengxian-0.3.34.jar
SHA256: 856c335bcdeeeaaf0d22df6fe40f80b14fcafe87140c98170c8bdc8c52972037
```

测试同步改写（旧断言编码的是旧政策）：
- "ordinary route runs the same ambient-qi calamity rule"：普通路线密二气必须生成 2 个载体、薄二气不生成；且 witnessed profile 在同样输入下结果完全相同（"无特权案例"）。
- 内劫/余气升炼：`standard()` 也触发。
- 就绪测试：缺项必须恰好是 `UPSTREAM_APERTURE_WRITE`，消息恰为 1 条且含"本体空窍状态写入"。

**实机**（0.3.34，隔离服务器）：

| 配置 | `/shengxian status` |
| --- | --- |
| 默认（`apertureLedgerOnly = false`） | `升仙暂未开放，尚缺必要机制：本体空窍状态写入尚未接入`——**只剩一条真理由** |
| `apertureLedgerOnly = true` | `普通升仙路线已就绪。` |

配置文件已还原为默认值。

## 边界与说明

- 这次是把"机制"通用化，不是因为拿到了新的原著正文。原文只见证了个例，通用化属于**项目决策**，已在 `docs/evidence/canon-rules.md` 记入"项目决策"一节，不算原著结论。
- `UPSTREAM_APERTURE_WRITE` 依旧无法实现：本体没有对应字段，写进去只能编造。这是字段边界，不是遗留特例。
- 普通路线在开关打开后的实机通关仍未验证（需要本命蛊仪式等本体玩法）。