# 0.3.8 验证记录（2026-10-28）

本轮按用户要求只完成升仙特效与相关代码，不启动专用或集成服务器，也不加载 Minecraft 客户端。普通升仙 readiness 能力集合仍为空，完整原著流程尚未开放。

## 已完成代码
- 准备低强度环流、碎窍壳纹与散逸碎光、天地人三向纳气、交感窗口、失衡扰动、三色混元气团、投蛊内收光迹、成窍展开、青提色仙元意象和失败消散。
- 来源按玩家 UUID、会话 UUID、实体 ID、维度及状态修订隔离。服务端只读已提交记录，每10 tick广播；关闭门禁、退出和换维度时停止。客户端最多16个来源，48格内最多绘制8个，优先自己。
- 世界几何改用 AFTER_PARTICLES，覆盖第一人称；全屏后期只服务本地升仙者。连续心跳不重播动画；终态只播放64 tick，旧终态登录不重播。连续来源60 tick失联过期，淡出12 tick。
- 16 tick仙窍进出过渡有优先权；屏幕中心保留，单次水墨混合最大28%，附客户端开启/关闭、低动态及12–48段细节设置。
- 灾劫提示绑定源实体，全屏边缘提示只作用本人。音磬/荒兽提示不再统一用雷声和电火花。
- 新增S2C视觉协议，网络版本从4改为5；需客户端与服务端使用同版本。本轮未升级、写入或修改真实存档。
- 时长、颜色、缩尺、仙元光点数均为美术参数，不是原著规则。成窍画面不触发成窍结算。

## 验证证据
- 6项新增纯Java回归先观察到缺少实现的失败，再实现；全套核心测试最终130 passed / 0 failed。
- 新视觉代码通过完整 NeoForge 源码编译；Build 0.3.8再次运行130项核心测试、110个本地依赖下的全源码编译、打包与结构校验。
- 独立 Verify 0.3.8通过；检查新增客户端/服务端/协议类、post/program/fsh、动态uniform、零强度/深度守卫及内嵌Veil。
- 特效工坊 fx_validate 对 src/main/resources 全目录返回0 errors、0 warnings：3个program、1个rendertype、2个post。
- tools/verify-fx.ps1启动独立隐藏 OpenGL3.3上下文，未加载Minecraft或NeoForge。NVIDIA GeForce RTX 5060 Laptop GPU / 驱动616.92。新post与世界aura GLSL实际编译、链接成功；所有动态uniform未被优化掉；Strength=0保持输入、depth<=0.0008保持输入、8阶段中心区域保持输入均通过。
- build/fx-offline/ascension-flow-contact.png是新post在合成背景上的GPU输出。它不展示世界几何，也不是Minecraft实机截图。
- 全构建日志：build/fx-offline/build-0.3.8.log。

## 修复与复审
- 只读代理复审指出正常流程可同刻跳过BROKEN，新增观察到 BREAKING→ABSORBING 时播放一次14 tick碎光的时间线回归；重新登录已有ABSORBING不重播。修复后130/130通过。
- 复审另指出Veil的add仅登记ID，不保证资源存在。现在先getPipeline判空，缺失时启用边缘备用效果并延迟重试；此分支已静态核对真实Veil API，资源重载异常的实机降级尚未验证。
- 同步源码版本为0.3.8后复现构建脚本把“版本已正确”误判为替换失败。守卫改为验证暂存元数据恰好声明一次请求版本；重新Build通过。
- 没有Git仓库，未创建提交。0.3.7原产物哈希仍为0476c89e1bcf311dd52a89c343ca812306224e3f0c545cdbec686c8b4d20f4ed。

## 产物
- build/libs/shengxian-0.3.8.jar：3,508,959字节。
- SHA256：1ca57cb41a2429f1947a603764f46e41e3567e5a9bfedad9d7774cd5de1b0e9d。
- 内嵌Veil 4.5.2，SHA256：e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66。

## 可重复检查
    pwsh -NoProfile -File ./build.ps1 -Action Test
    pwsh -NoProfile -File ./build.ps1 -Action SourceCheck
    pwsh -NoProfile -File ./tools/verify-fx.ps1
    pwsh -NoProfile -File ./build.ps1 -Action Verify -ModVersion 0.3.8

未做真实游戏画面、Fabulous/光影整合、多人现场与异常重连验收；GPU着色器验证不能代替这些检查。全项目还缺普通灾劫通则、真实用蛊反噬及保护/损失规则、本体空窍映射与全流程多人恢复验收。
