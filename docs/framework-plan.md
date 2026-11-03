> 历史计划：本文早于普通升仙路线接线，目标曾是保持入口关闭；0.2.5 已重新关闭升仙入口。当前状态以 `docs/ascension-implementation-plan.md` 和 `docs/verification-0.2.5.md` 为准。

# 升仙独立框架 Implementation Plan

> For agentic workers: use superpowers:executing-plans, complete the tasks with behavior tests and a final review.

**Goal:** 建立可独立构建的 NeoForge 升仙框架，交付安全关闭的真实升仙入口。

**Architecture:** core 只含 Java 领域状态与文件仓库；compat 只读本体；mod/command 负责加载与诊断。

**Tech Stack:** Minecraft 1.21.1、Java 21、NeoForge 本地 profile、PowerShell、Gradle Java。

**Spec:** framework-design.md；完整原著方案在 reference/ 中存为后续资料。

## Global Constraints

- 不依赖天意，不修改原本体或真实存档。
- 真实三气、灾劫、仙窍、仙蛊未实现时不得开放实际升仙。
- 碎窍不可回退；特殊体质规则未核实不能默认普通路线。
- 未知 schema、陈旧 revision 与重复序号不得产生新状态或覆盖数据。

## Review Focus

- 状态仓库恢复非法组合、重复保存、未来版本、两空窍覆盖。
- 客户端或其他调用者跳过规则门槛。
- 新 JAR 意外嵌入本体、依赖天意、或包含无用编译文件。
- 只读适配器被误当作可用升仙实现。
- 构建在新目录独立运行。

## Tasks

- [x] Task 1：建构建入口、模型接口与失败测试，实际观察测试失败。
- [x] Task 2：实现合法阶段图、不可逆标记、能力门槛、双空窍与动作序号，测试通过。
- [x] Task 3：实现带版本的会话保存、原子替换、并发版本校验和坏档拒绝，测试通过。
- [x] Task 4：只读本体适配、模组入口、诊断命令、准确 metadata，编译出包并验证。
- [x] Task 5：独立审阅、修复重要问题、完整测试与新目录验证，交付说明当前边界。

## 2026-10-25 阶段 2 追加

原计划的 5 个任务保持完成状态；新计划见 `docs/ascension-implementation-plan.md`。

- [x] Task 6：拆分 phaseRevision/stateRevision/lastActionSequence/serverTick/effectKey，支持同阶段更新。
- [x] Task 7：schema 2 仓库、schema 1 迁移、坏档/未来 schema 保留，新增迁移与并发测试。
- [x] Task 8：版本升到 0.2.0，重新运行测试、构建与产物校验。
