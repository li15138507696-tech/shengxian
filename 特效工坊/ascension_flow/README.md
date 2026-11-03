# 升仙阶段特效：已集成
由几页特效工坊生成基础post模板，再按项目的完整阶段、来源隔离与转场要求实现。此目录assets已与src/main/resources对应资源同步。
实际运行Java代码位于 src/main/java/net/guzhenren/shengxian/client 的 ClientAscensionEffects、VeilAscensionPostRenderer、VeilSanqiAuraRenderer。java/下的AscensionFlowFx.java是生成器原始示例，不参与编译，也不是实际接线入口。
实际着色器参数见fx.json。禁止拿原始示例覆盖运行代码。
离线检查：pwsh -NoProfile -File ./tools/verify-fx.ps1。结果与边界见docs/verification-0.3.8.md。该命令只启动独立隐藏OpenGL窗口，不加载Minecraft或服务器。
