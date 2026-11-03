# 0.3.7 验证记录（2026-10-27）

## 本轮修正

- `ApertureFormationPlanner.gradeFor()` 按已融合的天地人三气总量分档，并忽略未释放的人气储备。
- 上等福地初始仙元取 31–40，排除与“超过三十”冲突的 30。
- 新形成仙窍现在必须先通过 readiness 门禁；已有已提交 IMMORTAL 记录仍可走幂等恢复（本轮仅静态核对恢复分支，未运行已提交记录的恢复验收）。

## 自动验证与产物

- TDD 回归先因 `mayAttemptFormation` 尚未实现而失败；实现后 `build.ps1 -Action Test`：**124 passed / 0 failed**。
- `build.ps1 -Action SourceCheck`：完整 NeoForge 源码编译通过，Minecraft 1.21.1 / NeoForge 21.1.263 / Java 21 / 110 本地依赖。
- `build.ps1 -Action Build -ModVersion 0.3.7`：测试、完整编译、打包、结构校验通过。
- `build.ps1 -Action Verify -ModVersion 0.3.7`：独立产物复核通过。
- 产物：`build/libs/shengxian-0.3.7.jar`，3,477,305 字节。
- SHA256：`0476c89e1bcf311dd52a89c343ca812306224e3f0c545cdbec686c8b4d20f4ed`。
- Jar-in-Jar 内嵌 Veil 4.5.2，SHA256：`e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66`。

## 隔离实机门禁检查

- 新建隔离目录 `build/test-server-0.3.7` 与 `build/test-client-0.3.7`；未使用真实存档或游戏实例世界。
- 服务端加载本体与 `shengxian-0.3.7.jar`，到达 `Done` 并打开会话仓库；NeoForge 1.21.1 客户端加载同版模组并以 `FlowBot` 登录。
- 客户端输出显示 Veil 编译 vanilla shaders 并完成上传；该结果仅证明渲染依赖初始化，未证明升仙过渡画面已触发。
- 通过服务端控制台对在线 `ServerPlayer` 执行 `/execute as FlowBot run shengxian form` 与 `/execute as FlowBot run shengxianform`，两条形成入口都返回“升仙暂未开放”的 readiness 能力清单。随后 `/shengxian status` 显示 `first` 无会话；会话目录为空。
- 此前 0.3.6 的同一隔离玩家检查发现 `prepare/shatter/open/offer` 被 readiness 拒绝，但 `form` 因无会话先返回缺记录。0.3.7 的门禁回归覆盖这个次序边缘。
- 命令由服务端控制台以在线玩家为执行实体派发；这不证明玩家从 K 键界面或聊天栏发起，也不等于多人升仙、仙窍形成或视觉过渡验收。
- 停止请求时，原运行会话句柄已不存在；按进程名及命令行未发现对应的 0.3.7 测试服进程。服务端日志也没有全部维度已保存或正常停服标记，因此仅确认该进程已退出，不声称获得正常保存或停服证据。客户端是否正常退出也未核实。

## 尚未完成

普通升仙入口保持关闭，`AscensionReadiness.implementedCapabilities()` 仍为空。尚未解决普通灾劫通则、用蛊反噬及损失/保护规则、本体空窍事务映射、单空窍碎窍失败后果，以及完整 UI、多人和重启恢复验收。测试服命令检查不能替代这些实现。