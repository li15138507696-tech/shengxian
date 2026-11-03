// 调用方式速查。
// verified 口径：'jar' = 已用 veil-neoforge-1.21.1-4.5.2.jar 的 javap 逐个方法签名核对，并用
// tools/javac_fx.py 实际编译通过；'source' = 仅官方源码/ wiki 核对。
export const API_VERSION = 'veil-neoforge-1.21.1-4.5.2';

export const API = [
  {
    id: 'veil-rendertype',
    title: 'Veil：数据驱动 RenderType（几何提交）',
    verified: true,
    verifiedBy: 'jar',
    code: `// 1) JSON: assets/<modid>/pinwheel/rendertypes/<name>.json
//    layers 可以是一层，也可以是"数组的数组"=多趟（第二趟常用于 bloom）
RenderType rt = VeilRenderType.get(
        ResourceLocation.fromNamespaceAndPath(MODID, "my_effect")); // 可为 null
if (rt == null) return;                     // 加载失败就当没画

VertexConsumer vc = bufferSource.getBuffer(rt);
// 按 rt.format() 的格式写顶点；世界坐标提交，vsh 里再减 CameraPosition
vc.addVertex(pose.last().pose(), x, y, z)
  .setColor(r, g, b, a)
  .setUv(u, v)
  .setLight(light)
  .setNormal(pose.last(), nx, ny, nz);`,
    notes: [
      '签名：public static RenderType get(ResourceLocation, Object...) —— 第 2 个参数是可变参数，填 JSON 里的 %1$s 占位。',
      '必须放在渲染循环里调用：资源包热重载后会自动换新实例，缓存住会拿到已释放的 RenderType。',
      'layers 写成 [[...],[...]] 时是 LayeredRenderType：同一份网格画多趟，第二趟可 output 到 veil:bloom；**嵌套数组必须 ≥2 趟**（1 趟请写平铺数组，否则解码报 "Expected at least 2 layers"）。',
      'format 只认 12 个名字：BLIT_SCREEN / BLOCK / NEW_ENTITY / PARTICLE / POSITION_COLOR / POSITION_COLOR_NORMAL / POSITION_COLOR_LIGHTMAP / POSITION_TEX / POSITION_TEX_COLOR / POSITION_COLOR_TEX_LIGHTMAP / POSITION_TEX_LIGHTMAP_COLOR / POSITION_TEX_COLOR_NORMAL。',
      'bufferSize 只认 BIG / SMALL / TRANSIENT；mode 是 VertexFormat.Mode（QUADS/TRIANGLES/TRIANGLE_STRIP/POINTS/LINES...）。'
    ],
    links: ['https://github.com/FoundryMC/Veil/wiki/CustomRenderType']
  },
  {
    id: 'veil-shader',
    title: 'Veil：取 shader 并推 uniform',
    verified: true,
    verifiedBy: 'jar',
    code: `ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(MODID, "my_effect");

// A. 取程序但不改变当前绑定（推荐给 rendertype 用）
ShaderProgram program = VeilRenderSystem.renderer()
        .getShaderManager().getShader(SHADER);   // 找不到返回 null
if (program != null) {
    ShaderUniformAccess strength = program.getUniform("MyEffectStrength");
    if (strength != null && strength.isValid()) strength.setFloat(0.8f);
    ShaderUniformAccess tint = program.getUniform("MyEffectTint");
    if (tint != null && tint.isValid()) tint.setVector(0.62f, 0.12f, 0.15f); // vec3
}

// B. 取并立即绑定（给手写 RenderSystem 调用用）
ShaderProgram bound = VeilRenderSystem.setShader(SHADER);  // 可为 null
if (bound != null) { /* draw */ }
ShaderProgram.unbind();`,
    notes: [
      '签名：VeilRenderSystem.renderer() → VeilRenderer；getShaderManager() → ShaderManager；getShader(ResourceLocation) → ShaderProgram（可空）。',
      'getUniform(CharSequence) 返回的不是 null 而是"空 access"：判空之后还要判 isValid()，否则调用被静默丢弃、你会以为值推上去了。',
      '可用 setter（**没有 setMatrix**）：setFloat / setVector(2,3,4 个 float 或 Vector2fc/3fc/4fc) / setInt / setVectorI / setDouble / setVector64 / setLong / setFloats(float...) / setInts(int...)。',
      '矩阵只能拆成 4 个 vec4 用 setVectors(...) 传，或干脆在 Java 里先乘好再逐列传。',
      'Veil 优先走 glProgramUniform（DSA）⇒ 不必先 bind 也能写；不支持 DSA 的驱动会退化成 glUniform，此时必须先 bind。要跨驱动稳，就在 program 绑定期间推值。',
      'setShader 会改变绑定状态；给数据驱动 rendertype 推值优先用 getShaderManager().getShader，避免打乱渲染状态。'
    ],
    links: ['https://github.com/FoundryMC/Veil/wiki/Shader']
  },
  {
    id: 'veil-post',
    title: 'Veil：后处理管线（必须 add() 激活）+ 推 uniform',
    verified: true,
    verifiedBy: 'jar',
    code: `// JSON: assets/<modid>/pinwheel/post/<name>.json
// { "renderStage": "after_level", "priority": 1210,
//   "stages": [ { "type": "veil:blit", "shader": "modid:my_effect_post",
//                 "in": "minecraft:main", "out": "veil:post", "clear": true,
//                 "uniforms": { "MyBias": { "type": "float", "value": [0.25] } } } ] }
// ★写了一 renderStage 也**不会**自动跑：必须 PostProcessingManager.add() 一次
PostProcessingManager mgr = VeilRenderSystem.renderer().getPostProcessingManager();
mgr.add(1210, ResourceLocation.fromNamespaceAndPath(MODID, "my_effect"));

// 推 uniform：在 Post 事件里按 pipeline 名过滤
VeilEventPlatform.INSTANCE.preVeilPostProcessing((pipelineName, pipeline, context) -> {
    if (PIPELINE.equals(pipelineName)) {
        ShaderUniformAccess s = pipeline.getUniform("MyEffectStrength");
        if (s != null && s.isValid()) s.setFloat(strength);
    }
});`,
    notes: [
      '事件签名：preVeilPostProcessing(VeilPostProcessingEvent.Pre)，其方法为 (ResourceLocation, PostPipeline, PostPipeline.Context)。',
      'stage 类型只有 4 种：veil:blit（带 shader）、veil:copy（纯拷贝）、veil:mask（red/green/blue/alpha/depth 布尔）、veil:depth_function（function: LEQUAL 等）。',
      'blit 的 in 省略 = 当前绘制 FBO（一般是 minecraft:main）；out 省略 = veil:post（内建后处理缓冲）。in 与 out 不能相同。',
      'blit 的 uniforms 是**静态值**（type: float/int/mat2/mat3/mat4 + value 数组），每帧变化的值仍走事件。',
      'renderStage 取值（小写）：after_sky / after_solid_blocks / after_cutout_mipped_blocks / after_cutout_blocks / after_entities / after_block_entities / after_translucent_blocks / after_tripwire_blocks / after_particles / after_weather / after_level。',
      'priority 默认 1000，数越大越晚；replace=true 会顶掉优先级更高的管线（少用）。',
      '想要"强度 0 = 完全关闭"：shader 里 if (strength <= 0.001) { fragColor = base; return; }，不要动态删管线。'
    ],
    links: ['https://github.com/FoundryMC/Veil/wiki/PostProcessing']
  },
  {
    id: 'veil-post-manager',
    title: 'Veil：Java 侧开关/查询后处理管线',
    verified: true,
    verifiedBy: 'jar',
    code: `PostProcessingManager mgr = VeilRenderSystem.renderer().getPostProcessingManager();
// ★ 类名是 getPostProcessingManager()，不是 getPostPipelineManager()（旧文档常见错误）

boolean on = mgr.isActive(ResourceLocation.fromNamespaceAndPath(MODID, "my_effect"));
mgr.add(ResourceLocation.fromNamespaceAndPath(MODID, "my_effect"));      // 末尾追加
mgr.add(1200, ResourceLocation.fromNamespaceAndPath(MODID, "my_effect")); // 指定优先级
mgr.remove(ResourceLocation.fromNamespaceAndPath(MODID, "my_effect"));

PostPipeline p = mgr.getPipeline(ResourceLocation.fromNamespaceAndPath(MODID, "my_effect"));
Set<ResourceLocation> all = mgr.getPipelines();`,
    notes: [
      'add(ResourceLocation) / add(int, ResourceLocation) / remove(ResourceLocation) / isActive(ResourceLocation) / getPipeline(ResourceLocation)。',
      'JSON 里写了 renderStage 的管线**默认就是激活的**；只有在想按玩法动态开关时才需要 add/remove。',
      '手动跑一次：mgr.runPipeline(pipeline) —— 调试用，不要在渲染循环里乱调。'
    ],
    links: []
  },
  {
    id: 'veil-framebuffer',
    title: 'Veil：MRT 离屏缓冲（体积场/多附件）',
    verified: true,
    verifiedBy: 'jar',
    code: `// assets/<modid>/pinwheel/framebuffers/<family>_field.json
{ "width": "q.screen_width", "height": "q.screen_height", "autoClear": true,
  "color_buffers": [
    { "type": "texture", "format": "RGBA16F", "levels": 1, "name": "MyDataSampler" },
    { "type": "texture", "format": "RGBA8",  "levels": 1, "name": "MyMaterialSampler" } ],
  "depth": { "type": "texture", "format": "DEPTH_COMPONENT", "levels": 1, "name": "MyDepthSampler" } }

// program 里把附件绑成 sampler（name 必填；sampler 是可选序号；:depth 后缀取深度附件）
"textures": {
  "MyDataSampler":     { "type": "framebuffer", "name": "modid:my_field", "sampler": 0 },
  "MyMaterialSampler": { "type": "framebuffer", "name": "modid:my_field", "sampler": 1 },
  "MyDepthSampler":    { "type": "framebuffer", "name": "modid:my_field:depth" } }

// field 的 rendertype 加一层输出：
{ "type": "minecraft:output", "framebuffer": "modid:my_field" }`,
    notes: [
      'framebuffer JSON 两种写法：FULL（color_buffers 数组，≥1 个）与 COMPACT（单附件，直接写 type/format/filter/levels/name）；二者都用 depth 字段。',
      'depth 可以写 true（等价 DEPTH_COMPONENT 纹理）或一个附件对象；写 false/省略表示不要深度。',
      '附件字段：type（texture / render_buffer，大小写不敏感）、format（RGBA8/RGBA16F/RGB16F/R11F_G11F_B10F/DEPTH_COMPONENT/DEPTH_COMPONENT24...）、filter（CLAMP 等）、levels、name。',
      'width/height 是 Molang 表达式，可用 q.screen_width / q.screen_height 做分辨率倍数。',
      '写 MRT 的 fsh 要有 layout(location=0)/layout(location=1) 两个 out；只写一个 out 却有 2 个颜色附件 ⇒ 第二个附件永远空。',
      'Java 侧取缓冲：VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(rl)（没建好会是 null）。'
    ],
    links: ['https://github.com/FoundryMC/Veil/wiki/Framebuffer']
  },
  {
    id: 'veil-definitions',
    title: 'Veil：#define 开关（编译期分支）',
    verified: true,
    verifiedBy: 'jar',
    code: `// program JSON: { "definitions": ["MY_FEATURE", { "QUALITY": 2 }] }
// GLSL: #ifdef MY_FEATURE ... #endif ；带默认值的用 #if QUALITY >= 2

ShaderPreDefinitions defs = VeilRenderSystem.renderer().getShaderDefinitions();
defs.set("MY_FEATURE");              // 触发依赖它的 shader 重编译
defs.set("QUALITY", "2");
defs.remove("MY_FEATURE");
defs.setStatic("ALWAYS_ON");         // 不触发重编译的静态定义
String v = defs.getDefinition("QUALITY");`,
    notes: [
      '方法只有 set / set(String,String) / setStatic / setStatic(String,String) / remove / getDefinition / getDefinitions —— **没有 addGlobal**。',
      'definitions 数组元素既可以是字符串，也可以是 { "NAME": 默认值 } 对象，对象形式会进 definitionDefaults。',
      '不要拿它做每帧变化的参数：值一变就重编译，会卡帧。'
    ],
    links: ['https://github.com/FoundryMC/Veil/wiki/Shader']
  },
  {
    id: 'photon-particle',
    title: 'Photon：Java API 构造粒子（本项目现行主力）',
    verified: true,
    verifiedBy: 'source',
    code: `ParticleEmitter emitter = new ParticleEmitter();
ParticleConfig c = emitter.config;
c.setDuration(600); c.setLooping(true); c.setPrewarm(60);
c.setMaxParticles(120);
Circle circle = new Circle();
circle.setRadius(1.6f);
circle.setRadiusThickness(0.04f);   // ★ inner=(1-thickness)*r，取 1 是实心盘
c.shape.setShape(circle);
c.shape.setPosition(new NumberFunction3(0, 3.5f, 0));
c.setStartLifetime(NumberFunction.constant(60));
c.setStartSpeed(NumberFunction.constant(0));   // ★ 速度×0.05
c.setStartSize(new NumberFunction3(0.6f, 0.6f, 0.6f));
c.colorOverLifetime.setEnable(true);
c.colorOverLifetime.setColor(new Gradient(new GradientColor(
        0x00FFFFFF, 0xAAFFFFFF, 0xFFFFFFFF, 0xAAFFFFFF, 0x00FFFFFF)));
c.renderer.setLayer(RendererSetting.Layer.Translucent);
c.renderer.setRenderMode(ParticleRendererSetting.Mode.Horizontal);
c.emission.setEmissionRate(NumberFunction.constant(2f));

FX fx = new FX();
fx.setFxLocation(ResourceLocation.fromNamespaceAndPath(MODID, "my_fx"));
fx.getFxData().objects().add(emitter);`,
    notes: [
      '速度统一再乘 0.05；radial 乘 0.01；orbital = toRadians(度/20)/0.05。',
      '播放：new EntityEffectExecutor(fx, level, entity, AutoRotate.NONE) → setOffset/setScale → start()。',
      '锚点是眼睛位置；要脚底原点就 offset.y − eyeHeight。',
      '停止只能 runtime.destroy(true)；forceDeath 只在实体死亡分支生效。'
    ],
    links: ['https://github.com/FoundryMC/Veil']
  },
  {
    id: 'photon-postfx',
    title: 'Photon：postfx 图（不引 Veil 的屏幕效果）',
    verified: true,
    verifiedBy: 'source',
    code: `// 节点图：SceneColorInputNode / SceneDepthInputNode / PassNode / OutputNode / EffectWeightNode
// 运行时：PostEffectStack + CompiledEffect + CustomShaderPass（UniformSpec）
// 时间轴：fx.timeline.PostProcessClip —— 后处理可跟粒子一起播
// core shader 是原版 ShaderInstance 格式（RegisterShadersEvent 注册）`,
    notes: [
      '几页的 shaders/core 四组就是这种格式：crystal_ball / crystal_shard / aperture_transit / zheng_tiger_earth_qi。',
      '不引 Veil 时，屏幕级效果优先走这条；粒子+后处理可共用同一条时间轴。',
      'uniform 通过 UniformSpec 声明，Java 每帧推。'
    ],
    links: ['https://github.com/FoundryMC/Veil/wiki/PostProcessing']
  },
  {
    id: 'vanilla-fallback',
    title: '原版回退：ShaderInstance + RenderType（无 Veil 也能跑）',
    verified: true,
    verifiedBy: 'jar',
    code: `// 注册：RegisterShadersEvent → ShaderInstance(json, vertex, fragment)
// 使用：RenderSystem.setShader(() -> MY_SHADER); 推 uniform 用 MY_SHADER.getUniform("X").set(v)
// RenderType.create(name, format, mode, size, affectsCrumbling, sort, state)`,
    notes: [
      '升仙的"无光子版"就走这条：原版 ShaderInstance + 自定义 RenderType，不依赖任何第三方渲染库。',
      '代价：没有 data-driven rendertype / MRT / post 管线，复杂效果要自己写 FBO 与全屏 pass。',
      '这条路径下 bloom 只能用原版 outline/发光纹理或后期自己叠。',
      'RenderType.create 的 size 是 int（不是枚举），Veil 的 BIG/SMALL/TRANSIENT 最终也是映射成 1536/256/256 这类字节数。'
    ],
    links: []
  }
];

export const API_BY_ID = Object.fromEntries(API.map(a => [a.id, a]));
