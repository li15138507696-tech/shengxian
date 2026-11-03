# 0.2.5 安全门禁与仙窍边界验证

日期：2026-10-27

## 改动

- 升仙 readiness 门禁在备妥、碎窍、收束、投入本命蛊、形成、独立绑定、求知和调气入口前拒绝动作。当前生产适配器不报告任何完整原著路线能力。
- 既有非 IMMORTAL 会话不删除、不自动失败、不继续模拟；已支付碎窍并处于 BREAKING 的会话可完成到 ABSORBING 后暂停。IMMORTAL 仙窍维护继续。
- 共享仙窍地块只允许主人修改方块和操作物品；访客/护道者默认关闭，配置开放时仍只读。掉落物记录所属地块，进入另一已认领地块时移除。
- 玩家直接攻击、投射物命中、伤害和爆炸的影响限制在同一已认领地块。首次平台生成会先检查 16×16 落脚面，已有方块不会被覆盖。
- 地块归属通过坐标计算候选编号后查 claim，不再遍历所有认领地块。
- 用户提供的 5.4 JAR 已在隔离目录 `build/reference-test-server` 作为独立模组运行，与蛊真人本体一同启动；日志记录 `Done (6.330s)` 和 `GuZRLevelUp server started; runtime mode=DEVELOPMENT`。这是参考包的服务端启动验证，不是当前 `shengxian-0.2.5.jar` 的运行验证；审查记录：`docs/evidence/reference-5.4-ascension.md`。

## 验证

- `& .\build.ps1 -Action Test`（由 `Build` 调用）：100 passed / 0 failed。
- `& .\build.ps1 -Action Build`：退出码 0；Java 21、Minecraft 1.21.1、NeoForge 21.1.263 与 110 个本地依赖 JAR 编译通过。
- `& .\build.ps1 -Action Verify`：退出码 0；模组元数据版本、JAR 结构和 Veil 4.5.2 内嵌依赖校验通过。
- 产物：`build/libs/shengxian-0.2.5.jar`，93 个 class，3,415,893 字节。
- 产物 SHA256：`a4294b066271eb1e8cce924f5453c4bb8195d95977af77ca0384573e11cfce19`。
- 内嵌 Veil 4.5.2 SHA256：`e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66`。

## 限制

- 升仙入口按用户选择保持关闭；本轮没有宣称或测试完整原著升仙流程。参考包与当前 `shengxian` 客户端不能直接同装：参考包要求 Veil `[4.4.1,4.4.2)`，当前项目要求 `[4.5.2,4.6.0)`。
- 本轮验证覆盖核心行为、全源码编译/产物校验，以及参考包的独立服务端启动；未在真实客户端完成当前项目玩家事件演练或两个模组客户端共存验证。参考包启动日志另有蛊真人本体资源引用 `guzhenren:deleted_mod_element` 的数据错误，但未阻止服务端进入 Done。管理员/控制台命令以及其他模组直接写世界的操作不属于玩家事件隔离范围。
- 未安装到真实 Minecraft 实例，未修改玩家存档或蛊真人本体 JAR。
