export const MECHANISM = [
  {
    id: 'pipeline',
    title: '一帧里发生了什么：从几何到像素',
    body: [
      '1. Java 在渲染事件里取 RenderType：VeilRenderType.get(id)。它从 DynamicRenderTypeManager 取缓存实例；资源重载后旧实例失效，所以必须每帧取。',
      '2. Java 用 bufferSource.getBuffer(rt) 拿 VertexConsumer，按 rt.format() 写顶点。顶点是"世界坐标"，不是屏幕坐标。',
      '3. 缓冲提交时，RenderType 逐层 setup：先绑 shader（veil:shader 层），再设深度/剔除/写掩码/混合；如果是多趟（layers 是数组的数组），同一份 mesh 会被重复画多次。',
      '4. 顶点着色器把世界坐标变换到裁剪空间；片元着色器按你写的 uniform 与采样器出颜色。若 rendertype 带 output 层，这一趟写进指定 FBO（veil:bloom 或自定义 MRT），否则写主画面。',
      '5. 所有世界渲染结束后，Veil 在 renderStage 指定的阶段跑 post 管线：把 in 指定的 FBO 颜色绑成 DiffuseSampler0..N、深度绑成 DiffuseDepthSampler，再 blit 到 out。最后一站通常是 veil:post。',
      '6. veil:post 之后由 Veil 做 bloom/最终合成到屏幕。'
    ],
    pitfalls: [
      'RenderType 缓存实例不能长期持有（热重载后是旧对象）。',
      '多趟 rendertype 的每一趟都会跑一遍顶点着色器；顶点里别做重活。',
      'post 的 in/out 写错不会崩，只会黑屏或原样输出——先查 FBO 名。'
    ]
  },
  {
    id: 'layers',
    title: '层链是状态机，不是"效果列表"',
    body: [
      'layers 里每一项都是一个 RenderStateShard：shader / texture / transparency / depth_test / cull / write_mask / lightmap / overlay / output / layering / line / color_logic / patches / depth_clamp / multisample / seamless_cubemap。',
      'setup 时从上到下应用；clear 时逆序恢复（和原版 RenderType 一致）。所以顺序有意义：先绑 shader，再设状态，最后 output。',
      'layers 写成 [[...],[...]] 时是 LayeredRenderType：一份 mesh、多个 pass。Veil 的 draw 会先走父类，再对每个额外 layer 重新 setup → 用 RenderSystem.getShader() 画同一份缓冲。',
      'bloom 就是第二趟：同一 shader + additive + output=veil:bloom。它不改变第一趟，所以主画面和辉光可以分别控制强度。'
    ],
    pitfalls: [
      'write_mask.depth=true + 半透明 = 后面的东西被挡掉；半透明几乎都 depth:false。',
      'cull 默认 back；薄片/体积壳必须 none，否则背面消失。',
      'bloom 趟不加 additive 会重复压暗（校验器会 warning）。'
    ]
  },
  {
    id: 'uniforms',
    title: 'uniform 的绑定时机',
    body: [
      'Veil 不做 uniform 白名单：GLSL 里声明了什么，运行时就按名字找。ShaderProgram.getUniform 返回的是"空 access"而不是 null（拼错/被优化掉时 isValid()=false），所以判空之后还要判 isValid()。',
      '数据驱动 rendertype 的 shader 由层链绑定。推荐用 VeilRenderSystem.renderer().getShaderManager().getShader(id) 拿同一个 program 对象，在提交几何之前写值；写入即存于 program，绑定时上传。',
      'post 管线在 stage 里才绑定，所以要在 VeilEventPlatform.preVeilPostProcessing 里按 pipeline 名过滤后写 pipeline.getUniform(...)。',
      '想"编译期开关"用 definitions（VeilRenderSystem.renderer().getShaderDefinitions().set），但改值会触发依赖它的 shader 重编译，不能每帧改。'
    ],
    pitfalls: [
      '每帧 new uniform 对象/查字符串有开销；高频参数可缓存 ShaderUniformAccess。',
      'uniform 名大小写敏感；生成器统一用 <家族><语义> PascalCase。',
      'vec3 用 setVector(x,y,z)，int 用 setInt；**没有 setMatrix** —— 矩阵只能拆 4 个 vec4 走 setVectors(...)，或先乘好再逐列传。'
    ]
  },
  {
    id: 'mrt',
    title: 'MRT 体积场：为什么是两张颜色 + 一张深度',
    body: [
      'field 趟不是画"颜色"，是画"数据"：RGBA16F 存法线（xyz*0.5+0.5）与覆盖率（a）；RGBA8 存材质（强度 r / 种类 g / 凝结 b / 标志 a）。',
      '深度附件让 composite 能算出"这个像素上的场在世界里多厚/多远"，从而做厚度吸收、深度守卫、屏幕空间折射。',
      'composite 趟是 fullscreen blit：按 texCoord 采样三张附件 + 场景色深，把场"解释"成颜色。因为场是屏幕分辨率的，所以折射/扭曲天然是屏幕空间的。',
      '四套（fan_wind/hua_qi/iron_wall/wave）骨架完全一致，只换噪声与颜色——这就是"换算法不换管线"。'
    ],
    pitfalls: [
      'RGBA16F 在 4K 下很吃带宽；不需要法线时可降 RGBA8。',
      'field 的 fsh 必须 layout(location=0/1) out，且 rendertype 有 output 层指向该 FBO。',
      'composite 里采样场深度要用 screenToLocalSpace 反算世界点，直接拿深度当距离会错。'
    ]
  },
  {
    id: 'post-order',
    title: 'post 的顺序与合并',
    body: [
      '★renderStage 只是「在哪个阶段跑」的过滤条件（实测：after_level 27 个、after_translucent_blocks 5 个）；**它不负责激活** —— 没 add() 的管线一次都不会执行。',
      '★同一 stage 内按 priority **降序**执行（源码：Comparator.comparingInt(getPriority).reversed()）—— **数大 = 先跑**。几页实测的 1210/1215/1220/1225/1230 是体积/领域类的固定叠放顺序，春秋蝉用 1700 抢最后。升序的旧说法是错的（2026-10-25 按源码改正）。',
      '★每跑完一条管线，Veil 会立刻把 veil:post resolve 回 minecraft:main（GL_NEAREST），所以后一条读到的就是前一条的结果 —— 多条管线可以自然叠加。',
      '★preVeilPostProcessing 事件在 Veil 源码里位于 try 块**之外**：推 uniform 的代码自己必须 try/catch，否则异常会打断整个渲染阶段。',
      '同名 post 文件会被合并/覆盖：priority 低的先插入，replace=true 覆盖更高 priority 的同名管线。',
      '一个 post 可以定义私有 framebuffers（只在管线内可见），跨管线共享要放全局 framebuffers 目录。'
    ],
    pitfalls: [
      '别用 priority 抢别人的阶段；先确认你要的 stage。',
      '★要「我的效果压在别人上面」就得给**更小**的 priority（降序执行，小的晚跑）—— 想反了就先算清楚降序这件事。',
      '多段管线要 copy 原场景到私有 FBO，否则 trail 采样会读到已经改过的画面。',
      'blit stage 的 uniforms 只能写静态值（float/int/mat2~4）；每帧变化的量仍由 Java 事件推。'
    ]
  },
  {
    id: 'schema-452',
    title: 'Schema 版本：4.5.2 实测口径 vs 旧 demo 写法',
    body: [
      '以下全部用 veil-neoforge-1.21.1-4.5.2.jar 的常量池/源码逐个核对过；几页 demo（guzrlevelupdemo V5.4）本身在 4.5.2 下是可解析的，真正不认的是网上流传的 1.20 时代写法。',
      'rendertype（顶层）：format / mode / bufferSize 必填；affectsCrumbling / sort / outline 可选（默认 false）；layers 必填。bufferSize 只认 BIG / SMALL / TRANSIENT。',
      'program：vertex / tesselation_control / tesselation_evaluation / geometry / fragment / compute / definitions / textures / blend / required_features（★snake_case 的是这两个）；**没有 attributes / uniforms 这两个键**。',
      'program.textures 的值：可以直接写 "namespace:path"，也可以写 { "type": "location", "location": "..." } 或 { "type": "framebuffer", "name": "...", "sampler": 0 }。**没有 "asset" 这个 type**（那是 1.20 时代的写法）。',
      'post：stages 必填；textures / framebuffers / renderStage / dynamicBuffers / priority / replace 可选。stage 只有 4 类：veil:blit / veil:copy / veil:mask / veil:depth_function。★加载 ≠ 激活：PostProcessingManager 每帧只遍历 activePipelines，add() 是唯一入口。',
      'blit 的 in 省略 = 当前绘制 FBO；out 省略 = veil:post。in 与 out 相同会直接解码失败。',
      'framebuffer：color_buffers 数组（≥1）或 COMPACT 单附件；depth 可写 true / 附件对象 / false；autoClear 默认 true。',
      'layer 类型名走两个注册表：veil: 前缀的 19 个（shader/texture/multi_texture/transparency/depth_test/cull/lightmap/overlay/layering/output/texturing/write_mask/line/color_logic/patches/depth_clamp/multisample/seamless_cubemap）与 minecraft: 别名（同一个注册表的 vanilla 视图）——写 minecraft:depth_test 与 veil:depth_test 等价。',
      'UBO：只有 veil:camera 与 veil:gui_info 两个；写法固定 #veil:buffer veil:camera VeilCamera，成员只有 ProjMat / ViewMat / IProjMat / IViewMat / CameraPosition / NearPlane / FarPlane / CameraBobOffset。旧的 #include veil:camera 已标记 deprecated。',
      'Java：拿后处理管理器是 getPostProcessingManager()（不是 getPostPipelineManager()）；取 FBO 是 getFramebufferManager().getFramebuffer(rl)。'
    ],
    pitfalls: [
      '只写 renderStage、不在 Java 里 add()：日志会打 "Loaded N post pipelines"，但画面一辈子不动 —— 2026-10-25 实机踩过。',
      '从 1.20 教程里抄 {"type":"asset"} ⇒ 整个 program 加载失败，getShader 返回 null，画面什么都不出（正确写法是裸字符串）。',
      'program 里写 "attributes" 不会报错（多余键被忽略），但也不会生效——顶点属性由 format 决定，名字必须对上 format 的语义。',
      'layers 写成 [[...]]（只有一趟的嵌套数组）会报 "Expected at least 2 layers"；单趟请写平铺数组。'
    ]
  },
  {
    id: 'hot-reload',
    title: '热重载与失败模式',
    body: [
      'F3+T 重载资源：JSON 重新解析、shader 重新编译、RenderType 缓存换新。所以 get 要每帧调。',
      'shader 编译失败通常只在日志里；表现是画面不变/黑屏/程序返回 null。先看 latest.log 的 Veil 报错。',
      'RD 定义的变更会触发依赖 shader 重编译，有帧尖峰；不要拿它做动画。',
      'Veil 编辑器（F6，需 ImGuiMC）能看 framebuffer；/veilc buffers enable 可开启动态缓冲调试。'
    ],
    pitfalls: [
      'JSON 少一个逗号 = 整个 rendertype 不加载，get 返回 null，静默不画。',
      'program 名与文件名不一致：JSON 里写 modid:name，文件必须是 name.json。',
      '改了图片但没改 JSON 也要 F3+T（纹理缓存）。'
    ]
  },
  {
    id: 'perf',
    title: '性能：什么时候会翻车',
    body: [
      '全屏 post 每帧固定成本 = 屏幕像素数 × 采样次数。强度 0 必须早退，否则待机也在烧 GPU。',
      '半透明 + sort=true 会按距离排序，几何多时 CPU 开销明显；能用不排序（additive/天空）就别开。',
      '体积场是两趟 + 一张屏幕分辨率 RGBA16F；移动端/4K 先降分辨率或降格式。',
      '多趟 bloom 等于再画一遍几何；顶点重的模型慎用，或只在第二趟用低模。'
    ],
    pitfalls: [
      '每帧 new RenderType / new BufferBuilder 是常见瓶颈；RenderType 用缓存实例，Buffer 用 bufferSource。',
      '全屏 shader 里 for 循环上限必须是常量；uniform 当上限会编译失败或跑飞。',
      'discard 会影响 early-z；能用 alpha=0 混合就别 discard（但半透明排序下 discard 更省 overdraw，按场景选）。'
    ]
  },
  {
    id: 'photon-vs-veil',
    title: '两条栈怎么选',
    body: [
      'Photon（光子）：粒子/发射器/时间轴/postfx 图；Java API 构造，编译期校验；适合"一团火/一圈气/一条雷"这种粒子与短时特效。',
      'Veil（pinwheel）：data-driven rendertype + 自定义 shader + MRT + post 管线；适合"屏幕级/体积级/自定义几何"的持续效果。',
      '两者可共存：粒子负责主体，Veil 负责屏级调色与体积场；但发布形态要分别有/无这两个依赖的开关。',
      '几页的 469 个文件里，shaders/core 四组是原版 ShaderInstance（光子吃），pinwheel 全部是 Veil。'
    ],
    pitfalls: [
      '不要用 Photon 的粒子去模拟全屏后处理（贵且不可控）。',
      '不要用 Veil 的 post 去做跟随实体的小特效（每帧全屏，浪费）。',
      '无 Veil 的发布版要有降级路径（原版 ShaderInstance / 纯粒子）。'
    ]
  }
];
