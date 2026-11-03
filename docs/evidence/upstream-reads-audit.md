# 本体 renqi/kongqiao 读取端审计（UPSTREAM-006/007 补全）

日期：2026-10-30。工具：`tools/AuditUpstreamReads.java`（离线 ASM，22371 个类全扫，`GETFIELD` on `GuzhenrenModVariables$PlayerVariables`）。完整逐条输出保留在 `build/reads-audit.txt`（UTF-16）。

## 总量

| 字段 | 读取处 | 涉及类 | 读取端结论 |
| --- | --- | --- | --- |
| `kongqiao`（空窍数） | 73 | 28 | 见下 |
| `renqi`（人气） | 10 | 6 | 见下 |
| `jieduan`（阶段） | 4067 | 1836 | 只做分支/显示，无时间累积语义 |
| `shiqiao`（十窍） | 5 | 5 | 十绝体专用，普通路线不触 |

## kongqiao 读取端分类（73 处）

1. **NBT 序列化/克隆**（2 处）：`serializeNBT`、`clonePlayer`——纯持久化。
2. **KE1Procedure（开窍流程）**（约 20 处）：读后与 2/3/4/5/7/8 等常量比较分支——**空窍数决定下一窍的开窍条件**。这是本体自己的状态机，升仙模块从未写过 kongqiao（UPSTREAM-010 成立）。
3. **KongqiaoProcedure（显示）**：读后 `String.replace` 生成中文显示文本（“第一窍”…），纯 UI。
4. **HuaXieLuGu / WwupinDang / BenMingGu1 等用蛊 Procedure**：读 `kongqiao` 后 `ldc:3.0 op151 jump`——**用蛊效果按空窍数分档**（≥3 加成）。
5. **JingJieBaoCunProcedure（境界保存）**：读后分支。

## renqi 读取端分类（10 处）

1. **序列化/克隆**（2 处）：持久化。
2. **RenqiProcedure**：`DecimalFormat.format` 纯显示。
3. **TupochenggonglvProcedure（突破成功率）**（2 处）：读后 `ldc:7.5E-4 / 5.0E-4` 相乘——**人气是突破成功率的乘法因子**（0.00075、0.0005 系数）。
4. **UIxiuliananjianProcedure（修炼按键 UI）**（4 处）：同样乘法因子。
5. **WwupinDang（物品档位）**：`ldc:0.001` 乘法。

## 对升仙模块的意义

- `renqi` 在本体里的语义是**凡人修炼速度/突破率因子**，与升仙的“人气（human）”三气分离——我们的 `GuzhenrenPlayerBridge` 只读 `zhuanshu`（转数）与 `zhenyuan`（真元）是**正确边界**，不该扩大到 renqi。
- `kongqiao` 是本体开窍状态机的核心字段，**读取端全部在本体自己的 procedure 里**，升仙模块没有任何钩子触碰它——UPSTREAM-006 的“读取端未审计”缺口关闭，结论：**无需接入，也不应接入**（BREAK_APERTURE 的“接入本体空窍”仍卡在本体没有可写的目标字段，读取端不能替代写入端）。
- `shiqiao` 只被十绝体专用类读（5 处），普通五转路线无交集。

## 审计边界

静态字节码审计；未反编译每处方法的完整语义，`after=` 窗口只到 10 条指令。数值系数（7.5E-4 等）按字节码常量记录，未在游戏里复现验证。