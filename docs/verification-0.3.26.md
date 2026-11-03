# 0.3.26 验证记录：仙蛊像素画贴图与验收更正

日期：2026-10-30。基线 0.3.25。

## 变更

- 三只代表仙蛊按用户提供的像素画换贴图：定仙游/人如故（64×64 蝴蝶，透明背景）、江山如故（64×64 山水甲虫）；物品模型从 3D 元素模型改为 `item/generated` 平面模型。
- `tools/VerifyRepresentativeGu.java` 模型校验扩展为同时接受几何与贴图两种形态（旧校验只认 ≥5 个 elements，会让新贴图模型构建失败）。

## 验证

```
Framework tests: 200 passed, 0 failed
PASS representative Gu explosion hooks and assets: 67 checks
Artifact verified: shengxian-0.3.26.jar
SHA256: 2f94a1d59633a2bb35c89c564d1a7997af92240e1acba3081aa920cb910dd7cb
```

产物 3,855,688 字节。实机：客户端发放三只物品成功，Missing model/texture 0 条。

## 人如故/江山如故实机验收的更正与现状

- **更正**：0.3.25 记录的"人如故 −2.0、江山如故 COMMITTED"是错误结论——当时两只是被"未持有"拒绝的，实际完成事务的只有定仙游。详见 0.3.25 文档末尾的更正节。
- **0.3.26 复试**：夹具修为已补到 6 转、持有正常（grant 返回"目标已持有"），但 use 仍被拦：
  - `damage` 报 Target is invulnerable（死亡状态残留），第一次把血量上限调到 40 后 `damage 20` 直接打死（0.0 血），无人点击重生按钮，RCON 无法完成重生流程；
  - 死亡状态下 `check()` 以"当前不能催动仙蛊"（`!isAlive`）拒绝——**该拒绝本身是正确行为**。
- 结论：人如故/江山如故的效果路径（含真实伤害恢复、爆炸复原）仍需一次"有活人受伤"的场景才能验收；本轮验证到的是**前置拦截全部正确**。

## 残留边界

- 爆炸复原需要真实爆炸源；传送落点需要先潜行标记。
- 多人项见 `verification-0.3.25-multiplayer.md`。readiness 仍为空集。