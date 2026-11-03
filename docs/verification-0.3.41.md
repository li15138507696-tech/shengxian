# 0.3.41 蛊仙真元无限（∞）

日期：2026-11-01

## 实现

- immortalInfiniteTrueEssence 默认 true。
- IMMORTAL 且仙窍形成后，ImmortalEssenceService 每 20 tick 检查：
  - 将本体 zhenyuan 与 zuida_zhenyuan 都设为 Double.POSITIVE_INFINITY；
  - 已经是正无穷时不重复写。
- ShengxianCommands.status 将正无穷显示为 ∞。
- GuzhenrenPlayerBridge 的 snapshot/currentTrueEssence 接受正无穷，不再把它归一化为 0。
- 这是游戏映射：不是有限上限，而是正无穷。

## 验证

- & ./build.ps1 -Action Test：204 passed / 0 failed。
- & ./build.ps1 -Action Build -ModVersion 0.3.41：通过。
- & ./build.ps1 -Action Verify -ModVersion 0.3.41：通过。
- 产物：build/libs/shengxian-0.3.41.jar，3,888,751 字节。
- SHA256：7a5628080e6bff2e53617ae7eebcb72a3bae1f33005c64d6f629102cf2096527。

## 限制

- 0.3.41 未启动真实客户端/服务器复验 ∞ 回写；当前门禁默认关闭，没有 IMMORTAL 玩家时不会触发。
- 上游本体若自行重算 zhenyuan，服务会在下一个 20 tick 周期重新写回 ∞。
