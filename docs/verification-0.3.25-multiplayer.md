# 多人并发验收记录（0.3.25）

日期：2026-10-30。环境：隔离专用服务器 + 双客户端（FlowBot 9083676c、GuestBot 2949dac0），0.3.25，RCON 双连接并发注入命令。

## 前置修复

`launch-client-guest.ps1` 的 gameDir 原指向 `test-client`（与 FlowBot 冲突），改为 `test-client-guest`；Guest 客户端 mods 升到 0.3.25 并补 tianyi。

## 验收项与结果

### 1. 同一玩家并发催动互斥 ✅

两个 RCON 连接**同时**对 FlowBot 发 `use ding_xian_you`：

- 会话账本只有 **一次** 扣费：`xiangu.use.before=48.56 → after=45.56`（−3.0，正好 fixedTravelCost）
- 只有 **一个** operation id（bff22ad7…），stage=COMMITTED
- 第二次被冷却拒绝（cooldown 176998 → 177198，正好 200 tick）
- debug 日志两条独立 rejected 记录，无异常、无重复扣费

### 2. 跨玩家会话隔离 ✅

- FlowBot 的仙蛊持有/扣费只出现在 `9083676c…` 会话文件；GuestBot（`2949dac0…`）有独立的 held 列表与仙元余额（114.0）
- GuestBot 本体转数为 0，其 use 被六转前置正确拒绝——FlowBot 的夹具修复不会泄漏到其他玩家
- 并发 grant：GuestBot 成功交付（held 2 条），FlowBot 因客户端退出返回 No player was found

### 3. 离线边界 ✅

对已离线的 FlowBot 执行 grant：`No player was found`，无离线写账、无崩溃。

### 4. 双客户端同服运行 ✅

两个 0.3.25 客户端同时在线（`2 of a max of 20: FlowBot, GuestBot`），tianyi 通道协商通过，服务器无 shengxian 异常。

## 过程事故（非产品缺陷）

- FlowBot 客户端进程多次自行退出（与此前单人验收一致，疑似本机资源限制）；中途一次服务器 job 被误杀（验收脚本清理 running 任务时误伤），重启后继续，被杀前数据完整。
- 会话 `Skipping invalid formed aperture`（owner e727022b）为旧夹具遗留坏档，启动时被安全跳过。

## 未验收

- 双人同窍（护道/访客）的实时交互、死亡/掉线瞬间的并发操作、双人对同一载体的并发击退。
- 反噬在多入口下的阈值累计（需要真实用蛊节奏）。
