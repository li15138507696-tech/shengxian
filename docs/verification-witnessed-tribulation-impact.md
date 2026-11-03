# 具名灾劫冲击结算验证记录

日期：2026-10-29。对应设计：[witnessed tribulation impact settlement](superpowers/specs/2026-10-29-witnessed-tribulation-impact-settlement-design.md)。本记录只覆盖离线行为与源码检查。

## 已验证

- `.\build.ps1 -Action Test`：FrameworkTest **183 passed, 0 failed**。
- `.\build.ps1 -Action SourceCheck`：Java 21 NeoForge 源码编译通过；8 个已审计蛊激活钩子通过；rank projection、三气 failure receipt、tribulation impact receipt 分别通过 22 项压缩 NBT 检查。
- `.\build.ps1 -Action NbtCheck`：上述源码编译、8 个钩子和三套各 22 项 NBT 检查均通过。
- 新增 schema 1 ABSORBING 测试先以 RED 复现了 `NOT_FROZEN` 迁移，再修复为 `UNRESOLVED_ORDINARY` 并通过 GREEN。现有 schema 8 活动会话迁移也验证保留 carrier，同时冻结为惰性 profile。
- schema 9 对“来源标记为 false 却带有残留来源字段”的回归先以 RED 复现静默忽略，再改为拒绝记录且保留原文件；最终回归通过。
- 配置列表只接受 `UNRESOLVED_ORDINARY` 与三个具名 profile；无效/internal profile 回退惰性。profile 会在首次纳气模拟时冻结进 schema 9 会话。
- `TRIBULATION_IMPACT` 只接受 singleton cause 和具名来源凭证；`hasCanonicalFatalQiCause` 仍只接受三种 qi-overload 原因。独立结算及桥接仍检查总 readiness，而 `implementedCapabilities()` 返回空集。

## 实现边界

- 具名 profile 冲击只增加独立外劫压力与序号，不增加天地人三气风险。当前每次压力增量 2.0、冲击阈值使用 `QiParameters.riskLimit()`（默认 12.0），均为游戏映射参数。
- 达阈值时保存 profile、载体类别、载体 ID、冲击序号。独立事务使用玩家 `shengxian:tribulation_impact` 持久回执；经玩家 STARTED 与世界 STARTED 核验后，只尝试一次当前固定为 2048.0F 的 `magic()` 游戏伤害。只有匹配死亡 Clone 与 Respawn 回执才确认；任何不确定状态都隔离为 `UNCERTAIN`，不自动重试。
- 旧 schema 活动纳气会话采用 `UNRESOLVED_ORDINARY`；缺来源的旧冲击失败不会自动致死。会话文件和玩家 NBT 不能组成跨文件原子事务，因此不宣称 Minecraft 副作用 exactly-once。

## 未验证

本阶段没有启动 Minecraft 客户端、集成世界、服务端或 DSH，也没有改动真实存档。客户端展示、实际死亡/取消事件、Clone/Respawn、重登、多人、保存失败与崩溃恢复仍需单独隔离世界验收。没有升版本、打包或提交 Git。
