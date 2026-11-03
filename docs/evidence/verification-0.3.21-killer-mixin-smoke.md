# 0.3.21 源码候选：C/V/X/Z Mixin 隔离服加载检查

日期：2026-10-29

## 环境与候选包

- NeoForge 21.1.263，Java Temurin 21.0.7，专用服务器。
- 服务端目录为 build/ascension-runtime-mixin-smoke-20261029-1e3d7320bee0454fa369e8de3a6283a7，使用新建的隔离世界 ascension-mixin-probe-world；没有复制或打开用户世界。
- 临时 Shengxian 候选包由本轮 SourceCheck 编译类和源码资源组装，元数据仍标记 0.3.21，大小 3,633,805 字节，SHA256：1607cce76696831587c65d81b0b33ca6ec2ade463f870669fe08dba7d923e08f。该文件只在隔离测试 mods 目录，不在 build/libs，也没有替换已有 0.3.21 产物。
- 测试目录另放一次性 mixinprobe 1.0.0。它在 Mixin 配置准备后只加载四个目标 Procedure 类，不调用 execute，不创建玩家，不改玩家变量或蛊虫。

## 观测结果

- 服务端启动日志到达 Done (5.995s)。
- server-stdout.log 出现四条 MIXIN_PROBE_LOADED，分别对应 C、V、X、Z Procedure。
- debug.log 明确出现四条 Mixing 记录，来源均为 shengxian.common.mixins.json，目标分别为：
  - SanZhaoAnJianCProcedureMixin -> SanZhaoAnJianCAnXiaAnJianShiProcedure
  - SanZhaoAnJianVProcedureMixin -> SanZhaoAnJianVAnXiaAnJianShiProcedure
  - SanZhaoAnJianXProcedureMixin -> SanZhaoAnJianXAnXiaAnJianShiProcedure
  - SanZhaoAnJianZProcedureMixin -> SanZhaoAnJianZAnXiaAnJianShiProcedure
- RCON 接受 stop，日志显示所有区块保存，Java 服务端进程干净退出。

## 验证边界与环境问题

这证明四个目标类在专用服环境经过 Mixin 转换，HEAD/RETURN 注入点可应用；没有连接玩家，也没有执行任一杀招按键，因此不证明观察回调运行、蛊槽记录正确或攻击效果完成。客户端流程和多人行为仍未验证。

基座内容加载时另外记录了 guzhenren:deleted_mod_element 未知实体/物品、guzhenren:fangju 标签引用缺失、guzhenren:nilvhuafenjie 配方解析失败，以及 guzhenren:zijingsheligu advancement 无法载入。这些条目来自上游内容数据，本次未追查或修复；它们不能用于判断玩家端功能正确。

原始运行日志保存在上述隔离目录的 logs/latest.log、logs/debug.log 与 server-stdout.log。测试包和一次性探针不进入发行目录。GU_BACKLASH、普通升仙路线和 readiness 继续关闭。