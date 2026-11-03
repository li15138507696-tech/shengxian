# 0.3.10 验证记录

日期 2026-10-28。接续 0.3.9 仪式特效，实现与全部阶段/文件/API/质量开关见 ascension-ritual-effects.md；0.3.9 的功能和测试记录保留，不覆盖其 JAR。

## 修复与证据

独立 Superpowers 代码复审发现：云体使用背面（出口面）和 lequal 深度测试时，盒内墙面会把本该可见的前段云一起剔除。

已改成：
- 私有 TextureTarget 保存本帧场景深度，镜像主目标尺寸与 stencil 格式；每 AFTER_PARTICLES 最多复制一次，两个云来源共享，不从当前正在绘制的深度附件采样。
- 云 RenderType 为 always，不写深度；shader 逆投影重建不透明场景距离，finish=min(finish,sceneDistance)，小于 start 则 discard。
- 从真实事件投影矩阵每帧缓存逆矩阵，不修改事件原矩阵。
- 复制、分配、重建、释放前后保存/恢复独立读写帧缓冲、活动纹理与绑定、viewport、深度开关、清屏色/深度；不重新绑定刚删除的副本纹理。
- 断线/切世界/关闭效果/无云时释放副本，分辨率或 stencil 变化重建。没有运行中的旧副本继续持有。
- 实际 Veil 4.5.2 的 TextureUniformAccess.setTexture 与 ShaderUniformAccess.setMatrix 均经 javap 核对，公共类没有渲染依赖。

## 实际验证结果

- Build -ModVersion 0.3.10：137 核心测试通过，0 失败，全源码 Java 21/NeoForge 编译、资源检查和打包通过。
- Verify -ModVersion 0.3.10：独立通过；新增云 always 深度模式与 SceneDepthSampler/FrameSize/InverseProjection 和 finish 截断检查。
- 静态扫描 120 公共类，没有 Minecraft 客户端、Veil、LWJGL、Blaze3D 直接依赖。
- tools/verify-protocol.ps1：实际八阶段包往返和边界通过，权威结果验证 23 项通过。
- tools/verify-fx.ps1：实际 GLSL 编译/链接、八阶段中心视线/关闭/近深度保护通过；五种云体观察位置、16/24 步、有限 HDR、关闭零输出通过。
- 新遮挡回归先切回旧出口面 lequal 模式，稳定以 Wall wrongly erases foreground cloud 失败（RED）；恢复新方案后，盒内墙面保留前云、前景墙面完全遮挡云均通过（GREEN）。测试正常读取真实云 RenderType 的深度模式。
- 独立复审再次检查深度副本尺寸/stencil/释放/渲染状态路径，未发现新的重要问题；其指出的事件投影与删除纹理恢复两项随后已落实并全源码编译。具体运行时资源生命周期仍待 Minecraft 验收。
- 特效工坊 Veil 4.5.2 schema：23 文件、5 program、3 RenderType、2 post；0 错误、1 depth-no-guard 提示（既有 ascension_flow_post）。保留提示，源码的 soft guard 和已做验证范围见 0.3.9 记录。
- 合成背景与近似 bloom 的共享几何/实际 shader 预览已更新。所测 12 夹具峰值三角形顶点 26,412，不代表真实游戏帧率或全部参数的理论上限。

## 产物

build/libs/shengxian-0.3.10.jar，3,559,567 字节。
SHA256：bef355aa026bd706633f8ff71128451f09b98d721cfaea60e995103c55456c2d。
内嵌 Veil 4.5.2 SHA256：e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66。

历史候选均保留：
- 0.3.9：0abd330702bad16d3d03e9625e2ae1adeccf9564c277966c01e970ec42b0e359（存在上述已修复的出口面遮挡问题）。
- 0.3.8：1ca57cb41a2429f1947a603764f46e41e3567e5a9bfedad9d7774cd5de1b0e9d。

## 可核对日志

- build/fx-offline/ritual-build-0.3.10.log
- build/fx-offline/ritual-verify-0.3.10.log
- build/fx-offline/ritual-protocol-0.3.10.log
- build/fx-offline/ritual-depth-source.log
- build/fx-offline/ritual-depth-red.log
- build/fx-offline/ritual-depth-gpu.log

预览：build/fx-offline/ascension-rich-world-contact.png、ascension-rich-sanqi.png、ascension-aperture-vision.png、ascension-ritual-failure.png、ascension-flow-contact.png。

## 未执行和未完成

用户禁止开服务器，本轮没有启动 Minecraft 客户端、单人世界、专用服务器或 DSH，也未修改用户存档/安装目录或本体 JAR。上述离线和静态验证不替代真实客户端/专用服/多人、Fabulous、窗口缩放、资源重载或性能验收。

普通升仙完整闭环仍未完成，readiness 门禁未开放；一般灾劫、真实用蛊反噬/损失保护、本体空窍事务和完整失败恢复/多人流程仍有缺项。特效只读取真实状态，不改变判定、奖励或伤害。