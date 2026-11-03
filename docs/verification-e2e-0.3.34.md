# 端到端实机尝试记录（0.3.34，未完成）

日期：2026-11-01。目标：由开发侧直接操控本机，跑通普通五转→六转完整流程。

## 进展

| 步骤 | 结果 |
| --- | --- |
| 开门禁 | `ascension.apertureLedgerOnly = true` → `/shengxian status` 显示"普通升仙路线已就绪" |
| 夹具：转数/阶段 | `FixturePlayerState` 离线写 `zhuanshu=5.0`、`jieduan=4.0` → 资格"可发起" |
| 夹具：本命蛊 | 离线写 `BenMingGu={id,count,components.minecraft:custom_data.gc}` → "本命蛊=已记录"，`/shengxianbind` 通过 |
| 夹具：真元 | 需要真实真元，否则"真元不足以冲击窍壁" |
| `/shengxian prepare` | **成功**，阶段进入 PREPARING |
| `/shengxian shatter` | 阶段进入 BREAKING，但**碎窍计时器不推进，卡死** |
| 后续（纳气/开窍/放蛊/定型/成仙） | 未到达 |

## 阻塞缺陷：碎窍真元事务与"会自我重算的真元"不兼容

现象：阶段停在 BREAKING，`shatterClock` 的 elapsed 始终为 0，日志每秒刷：

```
Could not persist uncertain shatter receipt for <uuid>
java.io.IOException: Saved true essence changed before PREPARED was recorded
  at GuzhenrenPlayerBridge.verifyPreviousShatterSource(GuzhenrenPlayerBridge.java:167)
```

根因链：
1. `shatter()` 在开冲瞬间捕获 `before = currentTrueEssence()`，据此算出 `target = before - cost`，写入世界侧意图。
2. `ensureShatterEssenceCommitted` 要求**玩家存档里的真元精确等于 before** 才允许记录 PREPARED。
3. 但本体模组会自己重算 `zhenyuan`：实测同一角色在 40 秒内从 500 变成 **770/999**（真元恢复/派生重算），我设成 1000 时也被削到 999。
4. 于是存档值与 before 永不相等 → PREPARED 永远写不进去 → 事务停留在 UNCERTAIN → 计时器不推进 → 碎窍永久卡住。

这不是夹具问题：**任何真实玩家只要在点下"冲击窍壁"的瞬间之后真元发生自然恢复，就会卡死**。之前的验证全走单元测试（真元被当成常量），所以没暴露。

## 参考实现给出的答案（`升仙免授权5.4-可碎窍.jar`）

反编译 1462 个 Java 文件后确认对方的做法：

```
if ((essence = 读真元) < 2500.0) return false;      // 只做只读阈值
... new Session(player, essence, ...)               // 把读到的值当底蕴基数
```

**它根本不扣真元**，也没有任何持久回执/基线。所以"真元被本体持续重算"对它不构成问题——这解释了为什么它没这个坑。

## 修复尝试 2（0.3.36）：把扣费降级为"尽力而为" —— 成功

按参考实现的取向改：阈值校验保留，扣费改为**尽力而为**。

`ensureShatterEssenceCommitted` 内部吞掉 `IOException`，每个会话只警告一次，碎窍继续推进；扣费成功时照旧记账，失败时以"未扣费"模式继续。崩溃重复扣费的风险在这里不存在——因为没扣。

**实机结果**：`prepare` → PREPARING，`shatter` → BREAKING → **ABSORBING**（首次推进到纳气）。

## 修复尝试 1（0.3.35）：先落盘再校验 —— 未解决

改动：`GuzhenrenPlayerBridge.persistShatterEssenceReceipt` 在 `verifyShatterPreWriteSources` **之前**先 `saveAll()`，让存档与内存一致后再比对。

结果：错误从 `Saved true essence changed before PREPARED was recorded` 变为
`Shatter true essence and its durable receipt cannot be reconciled`（`ShatterEssenceReconciler` 直接隔离）。
阶段仍停在 BREAKING。**说明根因不在"比对时机"，而在"基线本身过期"**：本体持续重算真元（实测 40 秒内 500 → 770），而 `before` 是点下冲击那一刻的旧值。

## 真正的修复方向（待实施）

协议的本意是防"崩溃后重复扣费"。但扣费写入的是**绝对目标值**而不是增量，天然幂等，因此可以放宽基线约束而不破坏该性质：

0. **核心结论**：基线必须与"落盘校验那一刻"同源同刻，不能沿用几秒前捕获的旧值。
1. **延迟取基线**：把 `before` 的采集从 `shatter()` 移到第一次 `ensureShatterEssenceCommitted`，用"存档里的当前值"作基线（存档就是校验比对的同一来源）。
2. **允许重生式漂移**：若已有 PREPARED 且存档值 `> before` 但 `<= before + tolerance`（自然恢复），把基线就地抬高并记一条新 revision，而不是判 UNCERTAIN。
3. **拒绝下漂移**：存档值 `< before` 仍隔离——那是外部扣费，确实无法判定。
4. 同步补 3 类回归：正常提交、恢复式上漂移可继续、下漂移仍隔离。

## 第二轮：首次实机跑出失败结算

纳气阶段用 `/shengxianqi` 调气，我先 0.5/0.5/0.5、再只补人气，结果人气涨到 72 而天地各 36，人气风险升到 **12.11 > 上限 12.0**。

```
phase=FAILED   riskHuman=12.11   riskHuman(limit)=12.0
```

这是 CANON-002 的"人气稍重则自爆"——**第一次在真实服务器上跑通失败结算**。机制正确，是我调气手法差（人气释放速率 3.0/tick 比天地吸收 4.0/tick 慢，我却按同比例给了意图）。

## 失败是不可逆的：第二次开局被本体挡住

失败结算把本体变量写成了 `shiqiao = true`（失窍）。`prepare` 的资格检查要求"未失窍"，于是同一角色**再也无法开始升仙**。

```
kongqiao = 2.0d
shiqiao  = true      ← 失败结算写入
zhuanshu = 5.0d
```

这不是缺陷，正是 CANON-001/006 的"碎窍不可逆、只许成功不许失败"。但它意味着**每次重试都要换一个角色或清掉 shiqiao**。

## 状态

- 测试服配置保留 `apertureLedgerOnly = true`（便于继续实机）。
- 角色停在 `zhuanshu=5.0 / jieduan=4.0`，本命蛊=月光蛊（组件已匹配）。
- 卡死的 BREAKING 会话已移到 `.stuck` 后缀，玩家不会被困。
- 新增两个夹具工具：`tools/FixturePlayerState.java`（转数/阶段/本命蛊/真元）、`tools/DumpReceipts.java`（玩家回执键）。