# 严格升仙能力门禁实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 阻止未实现本体空窍写入、真实灾劫处理与失败后果时启动不可逆升仙。

**Architecture:** 核心能力门禁 + 生产适配器真实能力清单；服务端动作和 tick 共用门禁，未完成会话只读保留。

**Tech Stack:** Java 21、Minecraft 1.21.1、NeoForge 21.1.263、`build.ps1`。

## Global Constraints

- 任何缺失能力必须在物品、真元、会话数据写入前拒绝。
- 门禁关闭时旧的非 IMMORTAL 会话不删除、不推进、不自动失败；IMMORTAL 维护继续。
- 纯核心模拟仅供测试；版本升至 0.2.5，历史 0.2.4 记录保留。

---

### Task 1: 核心门禁测试

**Files:** Create `core/AscensionReadiness.java`; modify `core/RuleProfile.java`、`src/test/java/net/guzhenren/shengxian/FrameworkTest.java`。

**Interfaces:** `AscensionReadiness.evaluate(Set<RuleProfile.Capability>)` 返回 `available()` 与不可变缺项集合。

- [x] 测试空能力、缺任一严格能力和能力齐全三种结果，并断言缺项列表不可修改。
- [x] 运行核心测试验证门禁回归能拦截不完整能力清单。
- [x] 增加 `UPSTREAM_APERTURE_WRITE`、`CANONICAL_TRIBULATION`、`CANONICAL_FAILURE`，实现与普通路线需求集比较。
- [x] 重跑核心测试，100 项全绿。

### Task 2: 服务端统一拦截

**Files:** modify `src/main/java/net/guzhenren/shengxian/compat/GuzhenrenPlayerBridge.java`、`src/main/java/net/guzhenren/shengxian/server/AscensionLifecycleService.java`、`src/main/java/net/guzhenren/shengxian/server/AscensionControlService.java`、`src/main/java/net/guzhenren/shengxian/command/FormApertureCommand.java`、`src/main/java/net/guzhenren/shengxian/command/BindBenMingCommand.java`、`src/main/java/net/guzhenren/shengxian/command/InsightCommand.java`、`src/main/java/net/guzhenren/shengxian/command/ShengxianCommands.java`。

- [x] 测试并修正生产适配器能力清单，未实现项不得返回已实现。
- [x] 在 prepare 前置检查门禁，确保拒绝时没有绑定记录、会话或真元变化。
- [x] 对 shatter/open/offer/form、本命蛊独立绑定命令、求知命令、调气意图与 simulation tick 共用拒绝原因；状态命令显示门禁关闭原因。
- [x] 门禁关闭时暂停非 IMMORTAL 旧会话并保留文件内容；已付费 BREAKING 可结束至 ABSORBING 后暂停；不改变 IMMORTAL 内部 tick/仙元服务。
- [x] 运行 `& .\build.ps1 -Action Build`，核心与 NeoForge 全源码接线通过。

### Task 3: 校正文档并生成 0.2.5

**Files:** modify `docs/evidence/qi-parameters.md`、`docs/evidence/tribulation-parameters.md`、`README.md`、`docs/verification.md`、`docs/progress.md`、`build.gradle`、`build.ps1`、`src/main/resources/META-INF/neoforge.mods.toml`；create `docs/verification-0.2.5.md`。

- [x] 写明 40 个模拟步约为 40 秒；音磬/荒兽间隔分别约 20/40 秒。
- [x] 最新状态写为门禁关闭的原型，不称完整原著复刻；核心测试数量采用实际 100 项。
- [x] 只更新当前版本和当前产物命名为 0.2.5；保留历史 0.2.4 验证。
- [x] 运行 `& .\build.ps1 -Action Build`；测试、全源码编译、JAR 元数据和 Veil 4.5.2 SHA256 校验通过。
