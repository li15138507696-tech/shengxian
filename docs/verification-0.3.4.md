# 0.3.4 验证记录

日期：2026-10-27。构建目标：Minecraft 1.21.1 / NeoForge 21.1.263 / Java 21。

## 自动验证

- `build.ps1 -Action Test`：120 项通过，0 项失败。
- `build.ps1 -Action SourceCheck`：完整 NeoForge 源码编译通过，依赖为本地 110 个 JAR。
- `build.ps1 -Action Build -ModVersion 0.3.4`：核心测试、源码编译、打包和产物检查均通过。
- 产物：`build/libs/shengxian-0.3.4.jar`。
- SHA256：`55006655fe46f4e85f4ee4669c1ea1327f216f7cad62c38ae081eca8011d6445`。
- 包内含未经修改的 Veil 4.5.2 Jar-in-Jar；SHA256 `e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66`。
- 代理只读复审：护道人权限双名单原子写入、写盘失败回滚、离线 profile 缓存空值处理、成窍后登录恢复重试均未发现必须修项。

## 隔离服务端冒烟

- 专属空世界目录：`build/test-server-0.3.4`；仅复用项目已有 NeoForge 依赖库联接，没有复用旧测试世界或真实用户存档。
- Mods：蛊真人 10.20 外层本体与 `shengxian-0.3.4.jar`；Veil 4.5.2 从升仙 Jar-in-Jar 加载。
- 日志确认模组列表含 `shengxian 0.3.4`，服务端达到 `Done`，升仙会话仓库成功打开（0 个已形成仙窍），随后执行 `stop` 并保存全部维度，服务端正常退出。
- 本体同时报告缺少 `guzhenren:deleted_mod_element`、`guzhenren:huyao_chestplate`、进阶 `guzhenren:zijingsheligu` 等注册/数据引用错误；这些日志来源于本体资源，本次未改本体。升仙自身没有启动异常。

## 未验证范围

- 没有连接真实客户端玩家；没有验证 K 键界面渲染、Veil 着色器画面、三气过程或实际升仙流程。
- 没有实测双人护道、攻击授权/撤销、实体区块卸载重载、崩溃/重启恢复及完整多人升仙。
- 普通路线能力门禁保持关闭。上面的启动冒烟只证明模组能在该隔离服务端装载并打开数据仓库，不等于完整路线已完成。

本项目没有 Git 仓库；此次修改没有提交记录。0.2.5/0.2.6/0.3.0/0.3.1/0.3.2/0.3.3 产物未覆盖。
