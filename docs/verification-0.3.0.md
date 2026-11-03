# 0.3.0 普通升仙安全收尾验证

日期：2026-10-27

## 构建与核心回归

- `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -Action Build`：通过；包括核心测试、Java 21 全源码编译、JAR 结构和 Veil 4.5.2 内嵌指纹校验。
- 核心行为：107 passed / 0 failed。覆盖阶段不可逆、封闭门禁、离线碎窍计时重基、三气/灾劫领域状态、六转同步恢复、本命蛊 SNBT 托管与地块并发保留。
- 最终产物：`build/libs/shengxian-0.3.0.jar`，3,444,202 字节。
- SHA256：`bdf246b0b53b60e11165cfdfd91702c61d8adb27d217f13d974f0a8c41c8eeee`。
- 内嵌 Veil 4.5.2 SHA256：`e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66`。
- `build/libs/shengxian-0.2.5.jar` 与 `build/libs/shengxian-0.2.6.jar` 未覆盖。

## 隔离服务端启动

- 环境：Windows 11、Java 21.0.7、Minecraft 1.21.1、NeoForge 21.1.263、蛊真人本体 10.20。
- 世界：`build/ascension-runtime-check-20261027/validation-world`，仅用于验证，未使用玩家真实存档。
- Mods 目录中的 `shengxian-0.3.0.jar` 与最终产物 SHA256 一致。
- 服务端加载 `shengxian 0.3.0` 和其 Jar-in-Jar 的 Veil 4.5.2；日志达到 `Done (2.213s)`，升仙会话仓库成功打开，执行 `stop` 后保存所有维度并干净退出。
- 最新启动日志未发现 `shengxian` 错误；日志记录了蛊真人本体已有的缺失注册项、标签、配方及 advancement 错误，共 6 条相关错误，不归因于本模组。
- 原始日志：`build/ascension-runtime-check-20261027/logs/latest.log`。

## 验证范围限制

- 这次服务器只验证加载、世界仓库打开、维度注册与干净关服；没有真实客户端玩家跑完普通升仙。
- 三气/灾劫核心计算、物品托管、死亡和六转恢复未在真实玩家会话中端到端复验。
- 仍无游戏内音磬/荒兽载体、护道攻击联机和真实用蛊反噬调用点。
- 原著尚无可核实的普通路线逐蛊余气升炼映射；当前保持本命蛊原物托管，不生成虚假的仙蛊。
- 因以上缺口，普通升仙门禁保持关闭；0.3.0 是可加载的安全框架版本，不代表完整可玩的原著流程。
- 隔离服由最新 0.3.0 包启动并保存；不修改游戏实例、用户世界或既有 0.2.5/0.2.6 产物。