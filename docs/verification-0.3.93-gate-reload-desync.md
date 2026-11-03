# 0.3.93 bug 排查与门禁重载脱钩修复

日期：2026-11-02。起因：对 0.3.92 做一次带警告的完整排查。

## 排查手段

项目平时用 `-nowarn` 编译，javac 的静态检查全部被压掉。本轮改用 `-Xlint:all` 重编 159 个源文件，另加常见缺陷模式扫描。

第一轮 11 条警告、0 错误。**修掉 2 条后复查为 9 条。**

## 修复 1（真 bug）：门禁判定在配置重载后脱钩

**症状**：`readiness()` 每次调用都重新读取 `ascension.requireUpstreamApertureWrite`，而 `SessionStore`／`AscensionService` 在服务器启动时**一次性捕获**必需集存进 final 字段。

运维在运行中把该开关改为 true 并让 NeoForge 重载配置后：

- `/shengxian status`、`readiness()`、所有 `!readiness.available()` 守卫 → 报"门禁关闭"
- `store.start()` → 仍用启动时的旧集合 → **照常放行新会话**

判定分裂，界面说关、实际能开。

**为什么 0.3.92 之前不可达**：旧默认是 false 且没有反向开关，必需集恒定，捕获与实时读等价。0.3.92 引入了真正可切的运维开关，才让这条潜伏不一致变得可达。属于本轮的回归，不算历史遗留。

**修复**：必需集从「捕获的 `Set`」改为「每次判定求值的 `Supplier<Set>`」。

- `AscensionService` 新增 `Supplier` 构造器；`start()` 内 `requiredCapabilities.get()`
- `SessionStore` 新增 `Supplier` 重载并透传
- `AscensionServer` 传 `GuzhenrenPlayerBridge::requiredCapabilities`
- 既有的 `Set` 构造器保留（测试与纯域用法不受影响），内部包装成常量 supplier

core 仍然不读配置，纯 Java 测试可独立运行。

**回归测试**：`start gate re-reads the requirement set instead of capturing it` —— 用可变 supplier 构造服务，第一次以宽松集启动成功，翻转开关后第二次启动必须被拒且不落盘。旧实现（捕获 `Set`）无法通过该断言。

## 修复 2（清理）：静态方法经实例调用

`AscensionLifecycleService:137` 与 `FailureSettlementService:70` 用 `UPSTREAM.capabilities()` 调用静态方法，触发 `[static]` 警告。改为 `GuzhenrenPlayerBridge.capabilities()`。行为不变。

## 未修复的 9 条（按性质分类）

| 警告 | 位置 | 判断 |
| --- | --- | --- |
| `[removal] EventBusSubscriber.bus()/Bus` ×6 | `CalamityArtRenderer:209`、`CalamityClientSetup:11`、`ShengxianEntityAttributes:9` | NeoForge 已标记移除。1.21.1 上仍有效，**升级 NeoForge 时会硬失败**。属未来破坏，不是当前缺陷。 |
| `[this-escape]` | `CalamityCarrierEntity:41` 构造器内 `setNoGravity(true)` 等 | 构造函数调用可覆写方法。该类无子类，当前无害；若将来被继承需改为 super 阶段赋值。 |
| `[deprecation] hasChunkAt(BlockPos)` | `GrandRitualRenderer:42` | 客户端预览用。NeoForge 提供了替代重载，低优先。 |
| `[try]` 锁未被引用 | `FileSessionRepository:328` | **虚警**。`lock` 只在 try 期间持有文件锁，`synchronized` 已保证 JVM 内互斥，无需在体内引用。 |

另做了模式扫描，以下均**干净**：字符串 `==` 比较 0 处、`equals` 缺 `hashCode` 0 处、文件/流未用 try-with-resources 0 处。

`VeilAscensionPostRenderer` 有 4 处空 catch，属 Veil 可选集成的反射回退路径，失败即回退原版蒙层，是设计意图。

## 验证

| 项目 | 结果 |
| --- | --- |
| 核心回归 `-Action Test` | **211 项通过，0 失败**（原 210，+1 回归测试） |
| `-Action Build -ModVersion 0.3.93` | 通过，exit 0 |
| `-Action Verify -ModVersion 0.3.93` | 通过，exit 0 |
| `-Xlint:all` | 警告 11 → 9，错误 0 |
| 静态依赖边界 | 275 个 class 文件 |

产物：`build/libs/shengxian-0.3.93.jar`
SHA256：`4f2d769d31dddbbc043cd2c6b1ef89ce1ae1af93b1fc7f7a6473b49624b96290`

## 边界

- 未启动游戏或服务器。门禁脱钩修复的实机表现（运行中改配置再试启动）**未实机验证**，只有域层回归覆盖。
- 9 条保留警告未改动代码；`EventBusSubscriber.bus()` 那 6 条建议在下次升级 NeoForge 前统一迁移。
- 本轮只做排查与修复，未改动升仙流程、仙窍账本、失败结算、视觉层。
