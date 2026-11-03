# 0.3.9 验证记录

历史中间候选：后续独立复审发现云出口面错误遮挡，已在 0.3.10 修复，请使用新版本。2026-10-28。阶段与文件清单见 ascension-ritual-effects.md。

## 产物

build/libs/shengxian-0.3.9.jar：3,555,907 字节。
SHA256：0abd330702bad16d3d03e9625e2ae1adeccf9564c277966c01e970ec42b0e359。
内嵌 Veil 4.5.2 SHA256：e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66。
0.3.8 保留，SHA256 仍为 1ca57cb41a2429f1947a603764f46e41e3567e5a9bfedad9d7774cd5de1b0e9d。

## 实际执行与结果

1. Test：137 passed、0 failed。高强度关闭后仍有天空几何的问题先有失败回归，独立开关修复后通过；新增云体 24 顶点/朝外绕序/边界/关闭，以及真实天气渐显、无天气不伪造、失败清云检查。
2. SourceCheck：Java 21 / NeoForge 21.1.263 / 110 个本地依赖，全源码编译通过。
3. Build -ModVersion 0.3.9：重新运行 137 项测试、全源码编译、打包和资源/内嵌 Veil 检查通过，生成不可覆盖的新产物。
4. Verify -ModVersion 0.3.9：独立校验通过；云体/山海资源和结果类齐备，深度写入关闭，关键后期 uniform 和保护存在。
5. tools/verify-protocol.ps1：实际 Codec 八阶段往返、新元数据与阶段年龄、非法 entityId/年龄拒绝通过；OutcomeVerification 23 项通过，含保存面积分档/未知范围、权威快照往返与非法终态拒绝。没有建立游戏连接。
6. tools/verify-fx.ps1：隐藏 GLFW/OpenGL 3.3，NVIDIA GeForce RTX 5060 Laptop GPU/616.92。实际 GLSL 编译链接通过；后期八阶段中心保护、关闭还原、近深度保护、全部运行 uniform 有效。
7. 云体 GPU 检查：五种外部/第一人称/内部相机、16/24 步、关闭零输出和有限 HDR 值通过。曾发现离线相机垂直向上时退化，补稳定基向量后重跑通过。
8. 共享几何与实际 shader 的 12 阶段合成预览生成成功；所测夹具峰值三角形顶点 26,412，非全部参数组合的理论最大值。核心另有几何预算检查。
9. 特效工坊 fx_validate：Veil 4.5.2 schema，23 文件、5 program、3 RenderType、2 post；0 errors、1 warning。警告为 ascension_flow_post 的 depth-no-guard。源码已有近深度返回和采样 UV 深度差 soft guard，工具仍未识别；保留提示，不报告零警告。现有像素检查也不代表所有实际前景交界已验收。
10. 0.3.8/0.3.9 实际 JAR 对比：修改/增加 24 个主 Java 类、2 个 RenderType、6 个对应 shader 文件、2 个既有 fragment shader 和版本元数据；内嵌 Veil 未变。

11. 新打包校验静态扫描 120 个公共类字节码，没有直接 Minecraft 客户端/Veil/LWJGL/Blaze3D 类引用；已对 0.3.9 产物独立执行通过。只证明这项类型依赖边界，不代表专用服已启动。

## 日志和预览

- build/fx-offline/ritual-core-tests.log
- build/fx-offline/ritual-source-check.log
- build/fx-offline/ritual-build-0.3.9.log
- build/fx-offline/ritual-verify-0.3.9.log
- build/fx-offline/ritual-common-side.log
- build/fx-offline/ritual-protocol.log
- build/fx-offline/ritual-gpu.log
- build/fx-offline/ascension-rich-world-contact.png
- build/fx-offline/ascension-rich-sanqi.png
- build/fx-offline/ascension-aperture-vision.png
- build/fx-offline/ascension-ritual-failure.png
- build/fx-offline/ascension-flow-contact.png

## 客户端、专用服务器和多人

本轮都未启动，遵守用户“不要开服务器，先做特效和代码”。全源码编译/依赖静态检查不能替代专用服启动；Codec 往返不能替代多人；隐藏 OpenGL 不能替代 Minecraft 的 Veil bloom、遮挡、性能和视角验收。

仍需实机覆盖第一/第三/旁观、并行仪式、来源预算、取消/失败/确认成功、切维度/退出/重登/重启、资源重载/缺失和低性能/禁用切换。当前门禁关闭，不为了验收绕过能力检查或向真实玩家注入结果。

## 剩余问题

一条上述静态 shader 提示保留。普通升仙完整闭环未完成，readiness 能力集合仍空；灾劫通则、用蛊反噬/损失保护、本体空窍事务和完整恢复/多人验收继续有缺项。没有伪造雷击，也没有用视效发奖、扣血或改升仙结果。本轮没有修改本体 JAR、用户世界或游戏安装目录。

真实阶段优先，九段美术不强制各播固定秒数，不人为重排碎窍顺序。