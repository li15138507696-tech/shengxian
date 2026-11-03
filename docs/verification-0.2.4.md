# 0.2.4 普通升仙服务端流程接线（接手验证）

日期：2026-10-27

## 改动

- 打开普通五转路线门禁，并补充备妥、碎窍、取消、三气收束、本命蛊投入、仙窍形成的服务端操作。
- 人气储备由魂魄、体质、气运字段作有界游戏映射，不读取 renqi、名声或魅力。
- 碎窍真元扣除目标与阶段转换一起持久化；tick 恢复按目标设值，避免崩溃后重复扣除。
- 三气及仙窍形成继续使用服务端会话校验；形成记录带有管理员恢复标记，若本体六转字段写入遇到中断，可由管理员使用 /shengxian sync 并指定在线目标玩家修复。
- K 键界面整理为“升仙卷 / 三气调和”两页；打开时请求服务端快照，服务器回传阶段、三气、风险、窗口、灾劫与仙窍结果。
- 本命蛊内容指纹按玩家绑定；物品仍由本体镜像与背包持有，没有移入独立托管容器，也未炼成仙蛊。特殊路线、灾劫实体及 UI 蓝图中的其他页面仍未完成。

## 接手修复

- 修复 AscensionControlService 中 for-each 变量 event 遮蔽方法参数导致的编译失败，重命名为 tribulationEvent。
- 修复旧会话中“已形成仙窍但阶段不是 IMMORTAL”导致整服启动失败：新增 formedPlotClaimsLenient()，启动时记录并跳过无效记录，同时保留其合法地块占用，避免地块被重新分配；严格校验仍供测试和显式对账使用。
- 测试源码被写入截断后，从最近一次完整编译产物反编译恢复，并补回宽松校验断言；当前 95 项全部通过。

## 验证

- Java 21、NeoForge 21.1.263 classpath 下，54 个 Java 源文件编译通过。
- & ./build.ps1 -Action Test：95 passed / 0 failed，退出码 0。
- & ./build.ps1 -Action Build：测试、编译、打包、结构检查通过，退出码 0。
- & ./build.ps1 -Action Verify：通过；Veil 4.5.2 嵌入指纹通过。
- 隔离服务器实际启动：日志显示 formed plots=2, restored reservations=1，并记录 Skipping invalid formed aperture ...，未再崩溃；随后正常停止。
- 产物 SHA256：7b2d5456f779747a0202b60516cc81fd0c60a247803f3e962917e5c6253f6c14，3,401,694 字节。
- 新增 NeoForge 服务端配置 shengxian-server.toml：碎窍等待、碎窍真元比例、三气步进间隔、来访/护道开关；默认值与 0.2.4 行为一致。
- 仍未完成：真实玩家从五转到仙窍的完整实机验收、特殊路线、仙蛊炼成/唯一性、灾劫实体、本命蛊投入后的实际物品形态。

## 游戏映射参数

- 碎窍费用：五转真元上限的 10%，至少 1 点。
- 碎窍等待：100 服务端 tick（5 秒）。
- 福地档次阈值、初生仙元及空间缩尺仍见 docs/evidence/aperture-state.md，均属于游戏映射。

## 全流程实机验证（测试夹具角色）

- 使用 GuestBot（测试夹具角色，非真实蛊虫来源）在隔离服务器跑通：
  - shengxianfixture 设置五转四阶段、真元、本命蛊镜像与背包物品；
  - shengxian prepare → shengxian shatter → 等待碎窍完成；
  - shengxianfixtureqi 设置三气意图，等待约 20 秒；
  - shengxiansim 显示 ABSORBING，气 31.5/31.5/30.0，风险 0；
  - shengxian open → offer → form；
  - 状态显示转数=6.0、阶段=1.0、IMMORTAL、仙窍已形成；
  - shengxian aperture enter → Pos [1280.5, 65.0, 0.5]，Dimension shengxian:aperture；
  - shengxian aperture leave → 回到 overworld [4.5, 74.0, 0.5]。
- 修复：ApertureSpace.enter 对目标地块调用 setChunkForced，并使用 ServerPlayer 的目标维度传送重载；此前目标区块未加载时传送表现为无效。
- 测试夹具命令已从最终 JAR 移除。
- 限制：角色和本命蛊都是测试夹具；真实玩家存档、真实蛊虫来源、消耗/返还仍未验证。

## 本命蛊消耗（本轮）

- 形成仙窍成功后，FormApertureCommand 调用 BenMingGuCustody.consumeOne 从主背包/副手移除一个与镜像匹配的实物。
- BenMingGuStore 新增 consumed 记录：同一 fingerprint 幂等，不同 fingerprint 拒绝，跨重启保留。
- 单元测试 93-94：消耗记录幂等、跨重启、非法记录拒绝。
- 限制：消耗发生在会话提交 IMMORTAL 之后；若提交后找不到实物，只记录警告并保留已形成仙窍，需管理员对账。完整实机流程尚未在最终消耗版本上复跑。

## 最终消耗版本实机复跑

- GuestBot 测试夹具：形成前 /clear diamond 0 报告 3 个匹配物品。
- prepare → shatter → shengxianfixturefill（30/30/30）→ open → offer → form。
- 形成后 /clear diamond 0 报告 2 个，确认消耗 1 个匹配实物。
- status：转数=6.0、阶段=1.0、IMMORTAL。
- aperture enter：Pos [1536.5, 65.0, 0.5]，Dimension shengxian:aperture。
- benminggu.properties 同时存在 binding.* 与 consumed.* 记录。
- 夹具命令已从最终 JAR 移除。
- 并发地块：50 个并发 reservePlot 全部唯一，0..49 无重复，跨重启仍保留。

## 蛊仙真元无限（本轮）

- 新配置 immortalInfiniteTrueEssence，默认 true。
- ImmortalEssenceService 每 20 tick 检查 IMMORTAL 且仙窍已形成的玩家，把本体 zhenyuan 补到 zuida_zhenyuan；只在低于上限时写入并 markSyncDirty。
- 实机验证（测试夹具）：999.0/999.0 → 设为 0.0/999.0 → 约 3 秒后恢复 999.0/999.0。
- 这是游戏映射：不是把数值写成 Infinity，而是蛊仙的当前真元持续回满；仍会先扣费，再在下一个周期补满。
- 夹具命令已从最终 JAR 移除。
