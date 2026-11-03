# 0.3.1 源码与产物验证

日期：2026-10-27

## 构建检查

- build.ps1 -Action Test：111 passed / 0 failed。
- build.ps1 -Action SourceCheck：Minecraft 1.21.1 / NeoForge 21.1.263 / 110 个本地依赖；完整 NeoForge 源码编译通过。
- build.ps1 -Action Build：测试、编译、JAR 结构和内嵌依赖检查全部通过；build.ps1 -Action Verify 独立复核通过。
- 产物：build/libs/shengxian-0.3.1.jar，3,459,666 字节。
- SHA256：9377c7c13b50ec5ce768a3b10ddadb5b5c9ed100cc2b1f27ff3069ba411ba353。
- 内嵌 Veil 4.5.2 SHA256：e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66。
- 0.3.0 原产物仍为 3,444,202 字节，SHA256 bdf246b0b53b60e11165cfdfd91702c61d8adb27d217f13d974f0a8c41c8eeee。

## 隔离服务端加载

- 环境：Windows 11、Eclipse Temurin Java 21.0.7、Minecraft 1.21.1、NeoForge 21.1.263、蛊真人本体 10.20。
- 新建隔离目录：build/ascension-runtime-check-20261027-031；其中使用新建空世界 validation-world。NeoForge libraries 以只读目录联接复用，mods 仅安装蛊真人本体与 0.3.1。
- 服务端加载 shengxian 0.3.1 及其 Jar-in-Jar Veil 4.5.2；日志达到 Done，升仙会话仓库成功打开。发送 stop 后正常保存所有维度并干净退出。
- 启动日志共 7 行 ERROR，均来自蛊真人本体的 deleted_mod_element 注册/配方与成就资源问题；没有 shengxian 错误行。另有本体既有缺失物品标签与 advancement 警告。
- 日志：build/ascension-runtime-check-20261027-031/logs/latest.log。

## 验收边界

- 0.3.1 是保持门禁关闭的可加载框架版本，不表示普通升仙已经可玩。
- 没有运行真实客户端玩家全路线、GUI/Veil 实际渲染、多人护道、断线后的实际 inventory 或失败死亡验收。
- 真实场景灾劫载体与护道战斗、区块加载/卸载语义、真实用蛊反噬调用点仍缺失。
- 一般升仙的灾劫目录、反噬损失率与本命蛊保护没有足够原著证据可硬编码；默认灾劫 profile 保持 UNRESOLVED_ORDINARY，不推演太白/黑楼兰/方正个例为一般规则。
- 测试仅在项目内新建空世界执行；未修改用户真实存档或游戏实例。0.2.5、0.2.6 和 0.3.0 产物均保留。