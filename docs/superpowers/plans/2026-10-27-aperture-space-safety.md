# 仙窍共享维度安全实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 隔离共享仙窍地块并防止生成时覆盖世界方块。

**Architecture:** 纯核心地块几何/授权规则配服务端边界及交互事件；首次平台生成先完成全区域预检。

**Tech Stack:** Java 21、Minecraft 1.21.1、NeoForge 21.1.263、`build.ps1`。

## Global Constraints

- 无外部依赖；保留现有 plot 编号和孤立地块。
- 主人独占建造和物品权限；掉落物与其来源地块绑定；访客/护道者只读且默认关闭。
- 已占用区域拒绝生成；不覆盖方块、不回收地块。

---

### Task 1: 地块几何与授权规则

**Files:** Create `src/main/java/net/guzhenren/shengxian/core/AperturePlotBounds.java`; modify `src/test/java/net/guzhenren/shengxian/FrameworkTest.java`。

**Interfaces:** `contains(int plot,double x,double z)`、`find(Set<Integer> plots,double x,double z)` ；`AperturePlotPolicy.mayModify(int plot, UUID owner, UUID actor, double x, double z)` 仅在主人位于自己地块内时返回 true。

- [x] 添加边界、间隙、非法编号、地图极值、非有限坐标与访客授权用例。
- [x] 按测试驱动流程验证新同地块影响规则在实现前失败。
- [x] 实现合法编号 `0..7_500_031`、间距 256、边长 16 的几何和授权纯函数。
- [x] 重跑 `& .\build.ps1 -Action Test`，确认核心回归全绿。

### Task 2: 服务端权限与边界

**Files:** Create `src/main/java/net/guzhenren/shengxian/server/ApertureProtection.java`; modify `src/main/java/net/guzhenren/shengxian/ShengxianMod.java`、`src/main/java/net/guzhenren/shengxian/ShengxianConfig.java`、`src/main/java/net/guzhenren/shengxian/server/AscensionServer.java`、`src/main/java/net/guzhenren/shengxian/server/ApertureSpace.java`。

**Interfaces:** 使用 plot claim、返回点与现有访客/护道名单；注册移动、破坏、放置、方块交互、物品投掷/拾取、玩家攻击、投射物、伤害与爆炸边界保护。

- [x] 默认将 `allowGuests`、`allowGuardians` 设为 false；即使旧配置仍开启，访客与护道者也只读。
- [x] 每 tick 检查仙窍维度内玩家是否位于其主人或仍授权的地块；越界或无效时调用现有 `ApertureSpace.leave`。
- [x] 只允许主人修改地块、操作物品；攻击、投射物、受击和爆炸只能影响同地块对象。
- [x] 运行 `& .\build.ps1 -Action Compile`，按 NeoForge 21.1.263 实际 API 修正事件签名，全源码编译通过。

### Task 3: 安全平台生成和复验

**Files:** modify `server/ApertureSpace.java`。

- [x] 规则用例检查已建/占用/遮挡拒绝；生成实现先检查落脚面和落点头部空间。
- [x] 对 16×16 地面和玩家头部空间先整体预检；全部安全后才放平台、写标记和传送。
- [x] 运行 `& .\build.ps1 -Action Build`；核心测试、全源码编译、JAR 与 Veil 校验全部通过。
