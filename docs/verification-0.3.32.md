# 0.3.32 验证记录：内劫（CANON-012）与余气升炼（CANON-013）

日期：2026-11-01。基线 0.3.31。本轮把最后两条"已核但未实现"的原著个例落地。

## 一、内劫（CANON-012）——让仙窍不再是空壳

**原文**：太白云生吸纳三气时，来不及纳入的余天气、地气被排斥在外；**仙窍形成期间，其内部也酝酿出另一场天劫地灾**。

**实现** `core/InnerTribulation.java`（纯领域，可单测）：

- 立窍之后，若余气（`ambientHeaven` 或 `ambientEarth`）仍达触发阈值的一半，就在**仙窍维度**内凝结出第二场灾劫：载体锚点落在本人地块（`shengxian:aperture`，plot origin + 偏移）。
- 天气够优先生成柳风型（`JADE_WILLOW_WIND`），否则地气生成峥虎型（`SINGLE_HORNED_TIGER`）——两者都是 CANON-012 已核的具体载体。
- 生成时消耗那部分余气（它就是燃料），并写一条 `inner-<tick>` 前缀的载体 id。
- **仅限 `TAI_BAI_WITNESSED` 个例**：普通路线的内部灾劫没有任何正文依据（GAP-001），调用直接返回原状。
- 一次性：已记录 survived 后不再生成。

**世界层接线**：

- `TribulationWorldService` 原来只认 `ABSORBING` 阶段。新增 `phaseSupports(phase, carrierId)`：外层灾劫仍是纳气期，`inner-` 前缀的载体在 `IMMORTAL` 期也有效。reconcile / 实体自检 / 攻击处理三处都改用它。
- 主人可直接攻击自己的洞内灾劫（`strikeInnerByOwner`）。原文没有写太白云生用什么手段处理这场内劫，**直击是游戏映射**，已在注释标明；外层灾劫仍保持"护道者处理"的 CANON-009 规则。
- 立窍成功后由 `FormApertureCommand` 调用 `AscensionLifecycleService.brewInnerTribulation`，失败只记警告不回滚立窍。

## 二、余气升炼（CANON-013）——仙蛊的第一条非管理员获取路径

**原文**：方正成窍后，把核心蛊与血道蛊投入仙窍；**残余天地二气支持其本命血道凡蛊升炼为第二只血道仙蛊**。

**实现** `core/LeftoverRefinement.java`：

- 条件：仙窍已成、`FANG_ZHENG_WITNESSED` 个例、余天气与余地气都达阈值（原文是"天地二气"共同转化，缺一不可）、尚未升炼过。
- 效果：消耗余气，把 `fang_zheng_blood` 记入仙蛊账本，写一次性标记 `xiangu:refined:leftover`。
- **不编造效果**：原文只称"第二只血道仙蛊"，没有名字也没有效果。因此新增 `XianGu.Effect.UNSPECIFIED`，目录条目名为「血道仙蛊（原文未名）」，canonRef=CANON-013；它可以被持有与记录，**不能催动**。
- 命令：`/shengxiangu refine`（玩家）。

## 三、验证

```
Framework tests: 204 passed, 0 failed   (+2：内劫语义、余气升炼语义)
Artifact verified: shengxian-0.3.32.jar
SHA256: 39089b58b1aa9f48c4b58aef815b84791e11167b5ca1b73fd67731152b198c5c
```

新增回归覆盖：
- 内劫：余气够 → 生成 1 个载体、id 带 `inner-` 前缀、类型为柳风、锚点在仙窍维度、余气被消耗；普通 profile 不变；未立窍不变；二次调用不叠加。
- 余气升炼：方正 profile 下成功、账本出现「血道仙蛊（原文未名）」、余气被消耗、一次性；其他 profile 与余气不足均不触发。
- 目录测试同步更新为 5 条，并断言每条都有证据编号、「未名仙蛊」效果为 UNSPECIFIED 且不可重复催动。

**实机**（0.3.31 → 0.3.32）：

| 项 | 结果 |
| --- | --- |
| 启动 | `Done (3.347s)`，shengxian ERROR 0 条 |
| `/shengxiangu refine` | `当前没有可升炼的余气，或这次余气已经用过了。`（当前 profile 为 UNRESOLVED_ORDINARY，且该角色余气为 0） |
| `/shengxiangu list` | 5 条，含「血道仙蛊（原文未名）(fang_zheng_blood, 1.0 仙元, 个例损毁)」 |
| 内劫 | 未触发（需要先立窍，而立窍需要本命蛊仪式；且当前 profile 不是太白云生个例） |

## 四、一个设计确认

实机查到 FlowBot 的会话 `ambientHeaven=0 / ambientEarth=0`——它是把二气吸尽后才立的窍，所以没有余气。这**符合原著**：余气升炼的前提正是"来不及纳入的余气"（CANON-012/013 都用这个词）。吸尽则无余气，也就不会触发内劫与升炼。因此两条机制的触发条件在真实流程里是可达的（玩家可以选择早停手），只是本轮测试角色不满足。

## 五、未验证

- 内劫的**实机战斗**未跑：需要太白云生 profile + 完整立窍流程。
- 余气升炼的**成功路径**未在游戏里跑：同上。
- 两条都是个例，普通路线仍然关闭（GAP-001/004 未核）。