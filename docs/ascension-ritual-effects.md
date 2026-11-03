# 升仙仪式特效接入说明（0.3.10）

2026-10-28。目标环境：Minecraft 1.21.1、NeoForge 21.1.263、Java 21，内嵌 Veil 4.5.2。此页描述实际代码和验证边界，完整普通升仙仍受 readiness 门禁保护。

## 阶段与触发

真实顺序为 PREPARING → BREAKING → BROKEN → ABSORBING → OPENING → FORMING → IMMORTAL，失败进入 FAILED。保留 AscensionService、QiEngine、TribulationEngine 的判定。碎窍发生在纳气之前，不为动画重排游戏阶段。

- PREPARING：多层阵纹、抽象符文、缓流与凡躯薄壳。持续时间由实际准备停留决定，不强制推进。
- BREAKING：按已保存碎窍时钟显示球壳裂光。观察到 BREAKING→BROKEN/ABSORBING 才播放一次碎光；初次登录已有 ABSORBING 不补播。
- BROKEN：短暂壳片消散。服务端跳过该快照时，Track 仍保留一次真实碎窍脉冲。
- ABSORBING：人气为赤金细线、地气为翡翠上行粗流、天气为银白下行丝带。权重读取已释放/吸收的三气，不把人气储备算入。视觉年龄 0–80 tick 强调人气，65–165 强调地气，145–245 强调天气；只是同一阶段的美术渐显，不是三项游戏阶段。实际对应气量为零时不显示该通路；三项均有时显示倾角/速度各异的交错轨道与三色核心。
- 师法自然：读取实际 window 完成值，只改变细线、光轮与边缘后期。
- 灾劫：仍与 ABSORBING 并发。已保存灾劫提供风/音/兽/血四类视觉位，实际 IMPACT 事件提供 24 tick 连续扩张环。现有引擎没有雷型，不伪造雷击或增加扣血/耗材/输赢判定。
- OPENING：三气收束为混元气团，等待实际投蛊。
- FORMING：服务端进入此阶段才扩张气团，不提前播放完整仙窍或发奖。
- IMMORTAL 且 aperture.formed：压缩核心、扩张金青环；24 tick 后逐渐展现七层有空间深度的山海异象。总视觉寿命 200 tick，末 60 tick 淡出；阵纹/云层按年龄退场。第三人称异象位于来源背后，朝向在成功事件锁定；本人第一人称把同一景象移到前方可见位置。
- FAILED：实际失败才播放三气逸散、阵纹熄灭。惩罚由既有服务端逻辑和门禁决定。
- 结果卷：专用服务端终态包提供真实快照；成功延后 140 tick，失败延后 24 tick 展示，可合卷，不暂停游戏。面积、仙元、时间比、三气、失衡风险、本命蛊、悟道点收入/支出均取快照。品级按已保存面积在服务端归类；未知范围显示未知，不从动画反推。

时长、颜色、半径和光点数都是美术参数，不作为原著常数。真实阶段变化立即生效，不为动画阻塞玩法。现有状态机没有独立取消阶段；收到 stop、会话消失、门禁关闭或身份变化时停止并淡出。

## 同步与生命周期

每 10 tick 观察已提交会话，广播范围 64 格；包包含 UUID、实体ID、维度、会话ID、状态修订、实际灾劫位、成窍确认和阶段年龄。协议 6，需要两端同版，没有逐帧网络包。

同阶段心跳不重置年龄；新观察者追赶服务端阶段年龄。重登中途纳气以成熟视觉恢复，不重播蓄力和碎窍；旧终态存档不再次庆祝或弹结果。终态只在观察到活跃阶段转入终态时播一次，结果只发本人。

客户端缓存最多 16 来源，通常绘制最近 8 个，本人优先；按已知实体ID索引，不遍历世界实体。断线、切维度、移除、超距、停止或超时清理。心跳超时 60 tick，停止淡出 12 tick。结果去重缓存上限 16，换世界/退出清空。

## 修改 Java 文件与用途

路径前缀为 src/main/java/net/guzhenren/shengxian/，以下 24 个主类由 0.3.8/0.3.9 实际 JAR 条目差异核对：

- core/AscensionAuraMesh.java：共享阵纹、符文笔画、三气丝带/拖尾、光壳、冲击环和光尘；离线与游戏使用同一生成器，每段复用坐标。
- core/ApertureVisionMesh.java：七个弯曲切片，真实深度 1.75–4.35，按距离排序；不是单张传送门贴图。
- core/AscensionCloudMesh.java：24 顶点包围体，实际云厚度由 shader 射线步进产生。
- core/AscensionVisuals.java：只读投影、真实灾劫位、成窍确认、年龄和淡出。
- client/VeilSanqiAuraRenderer.java：共享几何的 Minecraft 顶点适配和既有双趟辉光。
- client/VeilAscensionCloudRenderer.java：每来源独立相机/强度 uniform，立即提交云体，避免下一来源覆盖。
- client/VeilApertureVisionRenderer.java：七层异象的 Veil 适配。
- client/VeilAscensionPostRenderer.java：本人水墨、轻微折射/色散、实际失衡和冲击响应；保护中心视线。
- client/ClientAscensionEffects.java：来源、质量、距离、朝向和渲染调度。
- client/ClientFx.java：实际事件音效和原版备用粒子，移除旧常驻气场，增加粒子总预算和距离限制。
- client/ClientFxEvents.java：客户端 tick、渲染、GUI 和退出清理。
- client/RitualSoundscape.java：实际阶段的一次性原版音效，不改音量或保留循环音。
- client/AscensionOutcomeClient.java、AscensionResultScreen.java：权威结果排队和水墨卷册。
- client/ShengxianClientKeys.java、ShengxianClientTick.java：安装网络显示回调。
- network/AscensionVisualPayload.java、AscensionOutcomePayload.java、ShengxianNetwork.java：视觉元数据、权威终态包和协议 6。
- server/AscensionVisualService.java、AscensionOutcomeService.java：只读广播及已提交结果投影。
- server/AscensionLifecycleService.java：复用 snapshotOf，不改结算；server/AscensionControlService.java：真实事件身份/灾劫分类，删除旧 qi 常驻广播。
- ShengxianVisualConfig.java：显示和质量偏好。

其他修改：FrameworkTest.java；tools/PreviewAscensionWorld.java、VerifyFxShaders.java、VerifyCloudVolume.java、verify-fx.ps1；tools/VerifyVisualProtocol.java、OutcomeVerification.java、verify-protocol.ps1；build.ps1、build.gradle、META-INF/neoforge.mods.toml；对应文档。

## 材质、粒子和着色器

资源前缀 assets/shengxian/pinwheel/：
- 新 rendertypes/ascension_aperture_vision.json 和 shaders/program/ascension_aperture_vision.json/.vsh/.fsh：七层山峦、云海、星空、天光。
- 新 rendertypes/ascension_cloud_volume.json 和 shaders/program/ascension_cloud_volume.json/.vsh/.fsh：旋转噪声密度、透射/散射的体积云，16/24 步。
- 修改 shaders/program/ascension_sanqi_converge.fsh：软丝带、亮芯和流动亮点。
- 修改 shaders/program/ascension_flow_post.fsh：折射、色散、Calamity/Impact 与深度交界保护。
- 复用 ascension_sanqi_converge 双趟 RenderType、ascension_flow 后期和 16 tick aperture_transit。没有新增位图贴图或自定义粒子注册；备用粒子使用原版，主体为程序化 shader/几何。

## Veil 4.5.2 实际 API

对内嵌 JAR 使用 javap 核对。所有渲染反射仅在 client 包：
- VeilRenderType.get(ResourceLocation,Object[]) 读取 RenderType，重载后重新取值。
- VeilRenderSystem.renderer().getShaderManager().getShader(ResourceLocation) 取得实际云 shader。
- UniformAccess.getUniform(CharSequence)，ShaderUniformAccess.isValid()/setFloat(float)/setVector(float,float,float)/setInt(int) 设置有效 uniform。
- VeilEventPlatform.INSTANCE.preVeilPostProcessing(VeilPostProcessingEvent.Pre) 在本人后期前更新参数，PostProcessingManager.getPipeline/isActive/add/remove 控制启停。
- 丝带基础透明趟输出主场景，additive 趟输出 veil:bloom。山海使用透明深度测试；云体深度测试为 always、剔除正面、不写深度，在 shader 内依据独立场景深度截断积分。这样既保留墙面前的云，也避免穿墙。
- 云体参数 CameraLocal、Strength、Storm、Time、Steps、LowQuality 每来源独立设置并立即提交。0.3.10 新增 SceneDepthSampler、FrameSize、InverseProjection；TextureUniformAccess.setTexture(CharSequence,int,int) 绑定独立副本，ShaderUniformAccess.setMatrix(Matrix4fc) 设置本帧事件投影矩阵的逆。RenderTarget.copyDepthFrom 后显式恢复原读/写帧缓冲，不假定始终绑定主目标。
- 后期采样已有主画面/深度，Strength=0 或近深度直接返回原色；UV 深度差 soft guard 衰减折射，保护前景交界和中心视线。

## 性能与回退

配置 shengxian-client.toml 的 visuals：
- enabled=false 关闭世界和屏幕后期，权威结果卷仍可查看。
- cinematicEffects=false 去除大阵纹/天环/巨轮/体积云/山海，保留小范围三气及真实事件提示，独立于 reducedMotion。
- lowPerformance=true：16 段、最多 2 来源/32 格，关闭体积云和七层异象。
- reducedMotion=true：减慢流动和云场，减弱后期；screenEffects=false 关闭本人后期。
- segments 范围 12–48，16 格外细节减半。常规距离 48 格，体积云最多 2 来源/32 格、24 步。
- 备用粒子每客户端 tick 总预算 96，低性能 24；寿命由原版粒子管理。
- 丝带 Veil 资源缺失则原版透明白材质几何；后期缺失则弱边缘蒙层；云体/山海缺失则跳过，其余三气继续。
- 维护一份按需分配的 TextureTarget 场景深度副本，每渲染帧最多复制一次，不直接采样当前附件；分辨率或 stencil 格式变化重建，换世界、无云或关闭效果时释放。复制/分配/销毁前后恢复读写帧缓冲、纹理单元/绑定、视口、深度开关和清屏值，避免重新绑定已删除的副本纹理。没有持续音源，不重复编译 shader。

整包固定嵌入 Veil 4.5.2；回退不代表可以移除必需依赖，既有优化渲染模组兼容限制仍见 README。

## 验证与剩余事项

完整结果见 verification-0.3.10.md，0.3.9 记录保留为历史候选。离线预览使用真实共享几何/GLSL，但背景为合成场景、bloom 为近似，不能代替 Minecraft 内视角、遮挡、资源重载、帧率或多人/专用服验收。

遵守用户指令，本轮没有启动 Minecraft、单人世界、专用服务器或 DSH。普通升仙整个闭环尚未完成：一般灾劫目录/应对、用蛊反噬/损失保护、本体空窍事务和完整失败/恢复/多人验收仍有缺项。implementedCapabilities 保持空集，不能把视效完成视为整个普通升仙完成。
0.3.10 额外修改 VeilAscensionCloudRenderer.java、ClientAscensionEffects.java、ascension_cloud_volume.json/.fsh、VerifyCloudVolume.java 和 PreviewAscensionWorld.java；处理独立复审发现的出口面遮挡问题，新增带墙体的显卡回归。
