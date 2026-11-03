# 0.3.55 灾劫战斗接线（真实伤害）

日期：2026-11-02。把此前只有规则、没有执行的灾劫招式接入服务端。

## 接线内容

- server/CalamityCombatService.java：每 tick 推进 CORE 里的攻击状态机，预警期画地面警告环，STRIKE 那一 tick 结算伤害后归零进入后摇。
- 载体（现有具名盔甲架）就是攻击原点；伤害只在服务端算：距离 + 方块遮挡射线 + 命中去重，三关都过才 hurt。
- 参与者：真实灾劫只打渡劫者本人与其已任命的护道者（allowGuardians 关闭时只打渡劫者）；无主测试载体打附近所有人。
- 命中身份 CalamityCombat.HitId（载体UUID + 攻击序号 + 目标），一次攻击对同一目标只结算一次；账本上限 512 条，超出后淘汰最早条目。
- 配置新增：calamityCombat（默认 true）、calamityDamageScale（默认 1.0，作用在已有的 ×500 基础表之上）。
- 测试入口：/shengxiantest jade_willow|tiger|chimes|beast|blood 生成无主测试载体，/shengxiantest clear 清除。需要 op。

## 实机验证（NeoForge 21.1.263 专用测试服 + 客户端）

把 calamityDamageScale 临时设为 0.0002，使伤害落在可观察范围：

- 生成 tiger 测试载体后，血量 20 → 16.4 → 11.4 → 3.9，逐次下降且未死亡。
- 服务端日志逐招记录：
  Calamity strike HORN_CHARGE ... for 1.6 damage each (radius 2.4)
  Calamity strike GROUND_ROAR ... for 0.6 damage each (radius 8.0)
  1.6 = 16（冲锋基础）×500×0.0002；0.6 = 6（虎啸基础）×500×0.0002，数值与公式一致，且不同招式的半径不同。
- 倍率未生效（配置解析失败回退默认 1.0）时，日志出现 FlowBot was killed by magic——同一条伤害路径可致死，不是只掉血。

## 配置踩坑（已修）

PowerShell 5.1 的 Set-Content -Encoding UTF8 会写 BOM，NeoForge 读 shengxian-server.toml 直接 ParsingException，随后回退默认值，表面上“改了小伤害却一击必杀”。改用 [System.IO.File]::WriteAllText + UTF8Encoding(false) 后正常。以后改配置必须无 BOM。

## 仍未完成

- 载体自身没有独立血量/被摧毁：目前仍是按单位削减的击退计数，模型没有受击/死亡表现。
- 攻击事件没有同步给客户端做动画（charge/attack/hurt 只能手动命令切）。
- 预警环只有服务端粒子，未取得截图确认。
- 重登去重仍只在内存账本，没有落盘；多人目标隔离未做专项测试。
- 投射物（风刃/血刃）、血池区域、屋顶遮挡的专项用例未做（遮挡射线已实现但未有测试用例）。
- 独立战斗实体（3 核心 / 8 磬 / 4 血核）尚未生成，仍是每类一个锚点。

## 产物

build/libs/shengxian-0.3.55.jar，SHA256 e10e46080a904db64ed4214bbe47bc1097f0fa45b5653d7b4163a05ec27c5b89，Framework 208 通过。
