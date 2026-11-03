# 0.3.49 原创灾劫美术与在线图像工具验证

日期：2026-11-01。用户明确要求开发代理创作美术，并寻找在线图像生成工具。

## 已交付的美术

五个可编辑Bedrock/Gecko几何资源、五张512×512原创像素atlas、五份数字骨骼动画，共15文件、139骨骼、432立方体、27动画片段。

| ID | 骨骼/立方体 | 资源内容 |
|---|---:|---|
| horned_tiger |25/77|12格长、7格高，单角、岩甲、虎纹、爪牙、尾巴|
| ancient_beast |26/90|12格高，厚骨甲、暗蓝裂隙、背脊|
| jade_willow |47/96|中空青玉风眼、层叠悬垂枝叶|
| eight_chimes |17/85|单口约6格高的L形青铜石磬、兽首与铭纹|
| blood_core |24/84|暗红裂晶壳、内腔心、悬浮碎片|

各含idle/charge/attack/hurt/death；双兽另有walk。三种悬浮核心缺walk时显式退回idle。
模型是当前首版方块风格原创，不承诺等同在线高模设定图。单口音磬当前共用同一造型，尚非八套不同形状。

## 代码接入

- client/CalamityArtModel.java：实际加载geo/atlas/animation，骨骼父pivot换算、坐标系变换、关键帧插值与姿态重置。
- client/CalamityArtRenderer.java：在现有具名载体锚点显示原创模型；缓存资源，F3+T或命令重载，单资产异常隔离；128格裁剪、最多16模型。
- 命令：/shengxianart gallery、clear、reload、animate idle|walk|charge|attack|hurt|death。
- 画廊只有客户端视觉，不创建碰撞/生命/伤害实体，退出客户端即清除。

## 在线图像工具（实际调用）

使用Black Forest Labs官方FLUX.1 Schnell演示：
https://huggingface.co/spaces/black-forest-labs/FLUX.1-schnell

已查公开API schema并实际匿名调用，无账户、密钥或付费。保留首张双角草案后，以新prompt生成单角虎与柳风两张可用设定参考。单角虎角尖靠左边界、背甲偏金属；柳风环过于规整，均有审查记录。音磬两次event:error/data:null，第二次降为768×768；错误原因未知，随后停止，荒兽/血核未请求。

生成脚本与prompt/seed/event/图片SHA256见tools/generate_calamity_concepts.py及docs/concepts/*manifest.json。这些图片是参考图，不是三维资源或实机截图。

其他找到的官方入口（未实际使用）：Recraft https://www.recraft.ai/generate/game-assets；Meshy https://www.meshy.ai/。不承诺它们当前的免费额度和匿名可用性。

## 验证结果

- 全Build 0.3.49退出0，Framework 208通过/0失败，现有Gu hooks/NBT等检查通过。
- tools/verify_calamity_art.py：15资源的骨骼/正尺寸/UV/关键帧/PNG校验通过，jar打包字节与源文件逐一相等。
- tools/VerifyCalamityArtLoader.jsh：使用0.3.49 jar中的模型加载器和磁盘资源，5/5通过；6状态×3时间采样产生非空有限顶点；idle三个额外采样均变化，姿态reset可复现。磁盘与jar资源字节相等另由上项证明。
- NeoForge21.1.263测试服务端成功启动，FlowBot实际登录。客户端日志确认五模型的骨数、立方体数、512贴图与动画列表。
- 真实F2截图：docs/art/ingame-carrier-models-0.3.49.png（原有锚点显示、夜间密集场景）；docs/art/ingame-gallery-0.3.49.png（白天分开展示）。均已read_image检查，模型立体轮廓、材质可见，无缺失贴图。
- docs/art/calamity_contact_sheet.png和jade_willow_idle.gif是离线实际模型渲染，已明确标记非游戏截图。
- 近景手动切换动画前，客户端记录Stopping!后退出；没有取得这项实机验证，不据此认定崩溃。
- F3+T重载、全部动画实机切换、低中高画质档尚未实机验收。

## 清理及边界

美术画廊在退出时清除；测试服务器通过RCON stop正常停止。仅更新build/test-client与build/test-server两个隔离测试目录，未部署到用户日常整合包。

本次是美术交付，不代表用户完整战斗规格已完成。独立战斗实体碰撞、血量/攻击判定、服务端攻击事件动画同步、投射物/屋顶遮挡、持久化攻击去重、多人目标隔离、256格天象与画质档仍需接线/测试。原版外观替代提案已撤回，不再要求用户另找美术。

产物build/libs/shengxian-0.3.49.jar，SHA256 f88c6c3a5d96c4370e8ee306c5e82809cbe29b8ee57c841b67c3b56a27edea07。
