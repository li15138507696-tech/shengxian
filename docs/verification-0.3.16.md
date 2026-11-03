# 0.3.16 离线验证记录：失败后果原因边界

日期：2026-10-28。普通升仙入口继续关闭。本版本只收紧失败结算准入，不实现新的游戏结果。

## 变更

- AscensionReadiness 只允许唯一、明确的三气失衡原因：HUMAN_OVERLOAD、HEAVEN_OVERLOAD 或 EARTH_OVERLOAD。
- TRIBULATION_IMPACT、UNCLASSIFIED、空集合和混合原因均拒绝。
- GuzhenrenPlayerBridge.applyAscensionFailure 在任何本体写入前再次执行 readiness 与 cause 校验。
- implementedCapabilities() 仍为空集；CANONICAL_FAILURE 没有启用，生产服务端不会触发这段死亡副作用。

## 验证

- FrameworkTest：158 passed，0 failed；覆盖三种允许原因与空集、未分类、灾劫冲击、混合原因拒绝。
- Java 21 / NeoForge 21.1.263 Build 与独立 Verify 通过。
- 玩家压缩 NBT：22 项检查通过；四个上游 Mixin 目标/描述符静态检查通过；common-side 135 个类无客户端渲染引用。
- tools/verify-protocol.ps1：8 阶段、23 结果、22 项 NBT 离线回环检查通过。
- 没有启动 Minecraft 客户端、集成世界、专用服务器或 DSH；运行时 Mixin、多人流程、玩家死亡副作用和崩溃恢复均未实测。

## 已知风险与未解决项

失败恢复造成致命伤害后，才持久写入 acknowledgement；登录与重生会重试尚未确认记录。崩溃或写盘失败可能重复死亡。因此仓库 acknowledgement 的幂等性不足以证明玩家副作用 exactly-once。见 [失败结算证据](evidence/failure-settlement.md)。

下一项必须解决死亡后果的写前意图、玩家侧持久收据及恢复判断，并通过隔离世界崩溃/登录/重生验收；不能在副作用恢复语义可靠之前开放 CANONICAL_FAILURE。

## 产物

- 路径：build/libs/shengxian-0.3.16.jar
- 大小：3,582,334 bytes
- SHA256：6814e50a25bd7a942857972e50ae94ed98a8e1652e30dde5e4ee53a936820e26
- 内嵌 Veil 4.5.2 SHA256：e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66