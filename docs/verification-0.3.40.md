# 0.3.40 蛊仙真元无限

日期：2026-11-01

## 实现

- 新增服务端配置 immortalInfiniteTrueEssence，默认 true。
- 新增 server/ImmortalEssenceService：
  - 每 20 tick 检查在线玩家；
  - 仅当会话阶段为 IMMORTAL 且仙窍已形成；
  - 将本体当前 zhenyuan 补到 zuida_zhenyuan；
  - 只在低于上限时写入并 markSyncDirty。
- GuzhenrenPlayerBridge 新增 refillImmortalTrueEssence。
- 这是游戏映射：不是数值 Infinity；费用仍会先扣，下一周期补满。

## 验证

- & ./build.ps1 -Action Test：204 passed / 0 failed。
- & ./build.ps1 -Action Build -ModVersion 0.3.40：通过。
- & ./build.ps1 -Action Verify -ModVersion 0.3.40：通过。
- 产物：build/libs/shengxian-0.3.40.jar，3,888,513 字节。
- SHA256：40ac0c85f480a361578831c676355ee33df018625350199e94b65a4936111a0a。

## 限制

- 0.3.40 未启动真实客户端/服务器复验真元回满；0.2.4 的同类实现曾在实机验证 999→0→999，但该版本已被 0.3.x 重构覆盖。
- 普通升仙门禁在当前版本仍默认关闭；没有 IMMORTAL 玩家时该服务不会产生可见效果。
