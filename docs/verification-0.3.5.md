# 0.3.5 验证记录

日期：2026-10-27。目标环境：Minecraft 1.21.1 / NeoForge 21.1.263 / Java 21。

## 成窍分档回归

- 新测试使用平衡三气 20/20/20、30/30/30、70/70/70 验证档次随总纳入三气不下降。
- Red：修复前 120 项旧测试通过，新测试因中档判定错误失败。
- Green：修复后 `build.ps1 -Action Test` 为 121 passed / 0 failed。
- 实现只将已汇入的天气、地气、人气计入档次；尚未释放的人气储备不计。80/200 阈值仍是游戏参数，不是原著数值。

## 源码与产物

- `build.ps1 -Action SourceCheck`：完整 NeoForge 源码编译通过；本地依赖 110 个 JAR。
- `build.ps1 -Action Build -ModVersion 0.3.5`：121 项核心回归、源码编译、打包与产物检查通过。
- `build.ps1 -Action Verify -ModVersion 0.3.5`：独立产物检查通过。
- 产物：`build/libs/shengxian-0.3.5.jar`，3,476,899 字节。
- SHA256：`8596579b1b62bc060021231f5f7c5764c935c7b1cbeb805b6cee9dd9d445fa6b`。
- 包含未经修改的 Veil 4.5.2；SHA256 `e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66`。

## 隔离服务端

- 新目录 `build/test-server-0.3.5`，使用新建空世界；只复用 NeoForge 依赖与非世界配置模板。
- 日志确认本体与升仙 `0.3.5` 均加载，Veil 4.5.2 从升仙 JAR 中加载；服务端达到 `Done`，会话仓库打开，执行 `stop` 后所有维度保存并正常退出。
- 无玩家连接，因此这只是装载与仓库启动验证，不证明界面、三气、战斗、境界同步、重连或完整升仙流程。
- 启动期间仍出现本体资源问题：`guzhenren:deleted_mod_element` 注册引用、`guzhenren:nilvhuafenjie` 配方和 `guzhenren:zijingsheligu` 进阶无法解析。本轮未改本体资源。

## 当前未验收

- 普通升仙门禁继续关闭：完整实现能力仍为空集。
- 没有真人客户端或玩家接入；没有多人护道、实际本体碎窍/成窍状态映射、用蛊反噬和蛊损失、失败后的单空窍恢复/存活、或玩家数据重启验收。
- CANON-014 仅核实双空窍个例失败后失去一窍而保留另一窍，不能推广为普通单空窍复活规则。

本轮只在新建隔离世界验证；未修改真实客户端、用户存档或旧版本产物。项目没有 Git 元数据。