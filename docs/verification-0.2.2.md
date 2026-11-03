# 0.2.2 Veil 内嵌交付记录

## 改动

- 以 NeoForge Jar-in-Jar 格式把官方 `veil-neoforge-1.21.1-4.5.2.jar` 原样嵌入升仙 JAR，并附带 `META-INF/jarjar/metadata.json`。
- 保留 Veil JAR 自带的 MoLang 与 GLSL Processor 嵌套依赖，以及其中的 `LICENSE_Veil`；升仙包同时注明 Veil 的 LGPL-3.0 许可与来源。
- 客户端依赖限制到 Veil 4.5.2.x；专用服务器侧仍不要求加载 Veil。
- 版本升级至 0.2.2。三气着色器、纳气触发和升仙状态流程未改变。

## 核验范围

- 核对官方 Maven 来源的 Veil JAR SHA-1 / SHA-256 与发布页面信息。
- 静态检查外层 JAR 的 Jar-in-Jar 元数据、内嵌文件路径、Veil 模组 ID / 版本、许可文件和 SHA-256。
- Java 21 编译并执行项目 `Verify`；未运行行为测试或启动游戏，因此未验证实机启动、Dedicated Server 分侧行为或最终画面。
