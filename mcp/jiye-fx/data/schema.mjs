// Veil 4.5.2 权威 schema —— 全部来自 veil-neoforge-1.21.1-4.5.2.jar
// （常量池 / 官方源码逐个核对；生成物已用 tools/javac_fx.py 实编译）
export const SCHEMA_META = {
  version: 'veil-neoforge-1.21.1-4.5.2',
  verified: 'jar 常量池 + 官方 1.21 源码 + 实编译',
  note: '4.5.2 的 textures 只认 location / framebuffer 两种 type（1.20 时代的 "asset" 已取消）；后处理管理器是 getPostProcessingManager()。'
};

export const SCHEMA = [
  {
    id: 'rendertype',
    title: 'rendertype（assets/<ns>/pinwheel/rendertypes/<name>.json）',
    codec: 'foundry.veil.api.client.render.rendertype.layer.CompositeRenderTypeData',
    fields: [
      { name: 'format', type: 'enum', required: true, values: ['BLIT_SCREEN', 'BLOCK', 'NEW_ENTITY', 'PARTICLE', 'POSITION_COLOR', 'POSITION_COLOR_NORMAL', 'POSITION_COLOR_LIGHTMAP', 'POSITION_TEX', 'POSITION_TEX_COLOR', 'POSITION_COLOR_TEX_LIGHTMAP', 'POSITION_TEX_LIGHTMAP_COLOR', 'POSITION_TEX_COLOR_NORMAL'], note: '顶点格式；决定 vsh 里 in 变量的名字与数量' },
      { name: 'mode', type: 'enum', required: true, values: ['POINTS', 'LINES', 'LINE_STRIP', 'TRIANGLE_STRIP', 'TRIANGLE_FAN', 'QUADS', 'TRIANGLES'] },
      { name: 'bufferSize', type: 'enum', required: true, values: ['BIG', 'SMALL', 'TRANSIENT'], note: '对应 1536 / 256 / 256 字节的缓冲档；会反复重提交的用 TRANSIENT' },
      { name: 'affectsCrumbling', type: 'boolean', required: false, default: false },
      { name: 'sort', type: 'boolean', required: false, default: false, note: '半透明要按距离排序才开；additive/天空别开' },
      { name: 'outline', type: 'boolean', required: false, default: false },
      { name: 'layers', type: 'layer[] | layer[][]', required: true, note: '平铺=单趟；嵌套=多趟（**必须 ≥2 趟**）；多趟共用同一份 mesh' }
    ]
  },
  {
    id: 'layer',
    title: 'layer（层链，每层一个 RenderStateShard）',
    codec: 'foundry.veil.api.client.registry.RenderTypeLayerRegistry（veil: 与 minecraft: 同一注册表）',
    ids: ['shader', 'texture', 'multi_texture', 'transparency', 'depth_test', 'cull', 'lightmap', 'overlay', 'layering', 'output', 'texturing', 'write_mask', 'line', 'color_logic', 'patches', 'depth_clamp', 'multisample', 'seamless_cubemap'],
    shapes: [
      { type: 'veil:shader', fields: [{ name: 'name', type: 'ResourceLocation', required: true, note: 'program id，如 "mymod:my_effect"' }] },
      { type: 'minecraft:depth_test', fields: [{ name: 'mode', type: 'enum', required: false, default: 'lequal', values: ['always', 'equal', 'lequal', 'less', 'gequal', 'greater', 'notequal', 'never'] }] },
      { type: 'minecraft:cull', fields: [{ name: 'face', type: 'enum', required: false, default: 'back', values: ['none', 'back', 'front', 'front_and_back'], note: '薄片/壳/粒子面片一律 none' }] },
      { type: 'minecraft:write_mask', fields: [{ name: 'color', type: 'boolean', required: false, default: true }, { name: 'depth', type: 'boolean', required: false, default: true, note: '半透明几乎都 false' }] },
      { type: 'minecraft:transparency', fields: [{ name: 'mode', type: 'enum', required: true, values: ['none', 'additive', 'lightning', 'glint', 'crumbling', 'translucent'] }] },
      { type: 'minecraft:output', fields: [{ name: 'framebuffer', type: 'ResourceLocation', required: true, note: 'veil:bloom 或自定义 MRT；这一趟写进该 FBO 而不是主画面' }] },
      { type: 'minecraft:texture', fields: [{ name: 'texture', type: 'ResourceLocation', required: true }, { name: 'blur', type: 'boolean' }, { name: 'mipmap', type: 'boolean' }] },
      { type: 'minecraft:lightmap', fields: [] },
      { type: 'minecraft:overlay', fields: [] }
    ],
    note: 'setup 正序应用、clear 逆序恢复；顺序有意义：先 shader，再状态，最后 output。'
  },
  {
    id: 'program',
    title: 'program（assets/<ns>/pinwheel/shaders/program/<name>.json）',
    codec: 'foundry.veil.api.client.render.shader.program.ProgramDefinition$Deserializer',
    fields: [
      { name: 'vertex', type: 'ResourceLocation', required: false, note: '省略=没有 vsh（后处理只要 fragment）' },
      { name: 'fragment', type: 'ResourceLocation', required: false },
      { name: 'geometry', type: 'ResourceLocation', required: false },
      { name: 'tesselation_control', type: 'ResourceLocation', required: false, note: '★snake_case' },
      { name: 'tesselation_evaluation', type: 'ResourceLocation', required: false, note: '★snake_case' },
      { name: 'compute', type: 'ResourceLocation', required: false },
      { name: 'definitions', type: '(string | {NAME:value})[]', required: false, note: '编译期宏；值变会触发重编译' },
      { name: 'textures', type: 'Record<string, textureSource>', required: false, note: '采样器绑定，见 texture-source' },
      { name: 'blend', type: 'object', required: false, note: '覆盖混合方程；一般交给 rendertype 的 transparency 层' },
      { name: 'required_features', type: 'string[]', required: false, note: '★snake_case；如 TESSELLATION / COMPUTE，缺特性就不加载' }
    ],
    forbiddenKeys: ['attributes', 'uniforms', 'samplers'],
    note: '★没有 attributes / uniforms / samplers 三个键（写了会被静默忽略）；顶点属性由 rendertype 的 format 决定。'
  },
  {
    id: 'texture-source',
    title: 'textureSource（program.textures 的值）',
    codec: 'foundry.veil.api.client.render.shader.texture.ShaderTextureSource',
    forms: [
      { form: '"ns:path"', meaning: '等价 location' },
      { form: '{ "type": "location", "location": "ns:path" }', meaning: '普通纹理' },
      { form: '{ "type": "framebuffer", "name": "ns:fbo" }', meaning: 'FBO 颜色附件 0' },
      { form: '{ "type": "framebuffer", "name": "ns:fbo", "sampler": 1 }', meaning: 'FBO 第 2 个颜色附件' },
      { form: '{ "type": "framebuffer", "name": "ns:fbo:depth" }', meaning: '深度附件（也可写 "depth": true）' }
    ],
    note: '★没有 "asset" 这个 type（1.20 时代写法；几页 demo 用的是裸字符串，不受影响）。'
  },
  {
    id: 'post',
    title: 'post（assets/<ns>/pinwheel/post/<name>.json）',
    codec: 'foundry.veil.api.client.render.post.stage.CompositePostPipeline',
    fields: [
      { name: 'stages', type: 'stage[]', required: true },
      { name: 'textures', type: 'Record<string, textureSource>', required: false, note: '管线级全局采样器' },
      { name: 'framebuffers', type: 'Record<string, framebufferDef>', required: false, note: '管线私有临时 FBO（命名空间 temp）' },
      { name: 'renderStage', type: 'enum', required: false, note: '★不负责激活', values: ['after_sky', 'after_solid_blocks', 'after_cutout_mipped_blocks', 'after_cutout_blocks', 'after_entities', 'after_block_entities', 'after_translucent_blocks', 'after_tripwire_blocks', 'after_particles', 'after_weather', 'after_level'], note: '省略则需 Java add() 手动启用' },
      { name: 'priority', type: 'int', required: false, default: 1000, note: '越大越晚' },
      { name: 'replace', type: 'boolean', required: false, default: false },
      { name: 'dynamicBuffers', type: 'int', required: false, note: '位掩码：让管线直接读 albedo/normal 等动态缓冲' }
    ],
    note: '★管线必须 PostProcessingManager.add() 才会执行 —— renderStage 不是激活开关，只是每帧的 stage 过滤条件（2026-10-25 实机踩过）。管线层没有 uniforms 键，静态值写在 stage 里。'
  },
  {
    id: 'post-stage',
    title: 'stage（post 的 stages[]）',
    codec: 'foundry.veil.api.client.registry.PostPipelineStageRegistry',
    types: [
      { type: 'veil:blit', fields: [{ name: 'shader', type: 'ResourceLocation', required: true }, { name: 'in', type: 'ResourceLocation', required: false, note: '省略=当前绘制 FBO（minecraft:main）' }, { name: 'out', type: 'ResourceLocation', required: false, default: 'veil:post' }, { name: 'clear', type: 'boolean', required: false, default: true }, { name: 'uniforms', type: 'Record<string,{type,value}>', required: false, note: '静态值：type=float/int/mat2/mat3/mat4，value 是数组' }], note: 'in 与 out 不能相同（解码期就报错）' },
      { type: 'veil:copy', fields: [{ name: 'in', type: 'ResourceLocation' }, { name: 'out', type: 'ResourceLocation' }, { name: 'clear', type: 'boolean' }] },
      { type: 'veil:mask', fields: [{ name: 'red', type: 'boolean' }, { name: 'green', type: 'boolean' }, { name: 'blue', type: 'boolean' }, { name: 'alpha', type: 'boolean' }, { name: 'depth', type: 'boolean' }] },
      { type: 'veil:depth_function', fields: [{ name: 'function', type: 'enum', values: ['ALWAYS', 'EQUAL', 'LEQUAL', 'LESS', 'GEQUAL', 'GREATER', 'NOTEQUAL', 'NEVER'] }] }
    ]
  },
  {
    id: 'framebuffer',
    title: 'framebuffer（assets/<ns>/pinwheel/framebuffers/<name>.json）',
    codec: 'foundry.veil.api.client.render.framebuffer.FramebufferDefinition（FULL 或 COMPACT 二选一）',
    full: [
      { name: 'width', type: 'molang', required: false, default: 'q.screen_width' },
      { name: 'height', type: 'molang', required: false, default: 'q.screen_height' },
      { name: 'color_buffers', type: 'attachment[]', required: true, note: '≥1 个' },
      { name: 'depth', type: 'attachment | true | false', required: false },
      { name: 'autoClear', type: 'boolean', required: false, default: true }
    ],
    compact: [
      { name: 'width / height', type: 'molang', required: false },
      { name: 'type', type: 'texture | render_buffer', required: false, defaults: 'texture' },
      { name: 'format', type: 'enum', required: false, default: 'RGBA8' },
      { name: 'filter', type: 'enum', required: false, default: 'CLAMP' },
      { name: 'levels', type: 'int', required: false, default: 1 },
      { name: 'name', type: 'string', required: false, note: '采样器名（同时作为 fsh 里的 uniform 名）' },
      { name: 'depth', type: 'attachment | true | false', required: false },
      { name: 'autoClear', type: 'boolean', required: false, default: true }
    ],
    attachment: [
      { name: 'type', type: 'texture | render_buffer', required: false, default: 'texture', note: '大小写不敏感' },
      { name: 'format', type: 'enum', required: false, default: 'RGBA8（depth 附件默认 DEPTH_COMPONENT）', values: ['RGBA8', 'RGBA16F', 'RGB16F', 'R11F_G11F_B10F', 'R8', 'R16F', 'R32F', 'RGB10_A2', 'SRGB8_ALPHA8', 'DEPTH_COMPONENT', 'DEPTH_COMPONENT16', 'DEPTH_COMPONENT24', 'DEPTH_COMPONENT32F', 'DEPTH24_STENCIL8', 'DEPTH32F_STENCIL8'] },
      { name: 'filter', type: 'enum', required: false, default: 'CLAMP' },
      { name: 'levels', type: 'int', required: false, default: 1 },
      { name: 'name', type: 'string', required: false }
    ]
  },
  {
    id: 'ubo',
    title: '内建 UBO（只有两个）',
    codec: 'foundry.veil.api.client.registry.VeilShaderBufferRegistry',
    buffers: [
      { id: 'veil:camera', block: 'VeilCamera', members: ['ProjMat', 'ViewMat', 'IProjMat', 'IViewMat', 'CameraPosition', 'NearPlane', 'FarPlane', 'CameraBobOffset'], usage: '#veil:buffer veil:camera VeilCamera' },
      { id: 'veil:gui_info', block: 'GuiInfo', members: ['（GUI 变换矩阵/缩放）'], usage: '#veil:buffer veil:gui_info GuiInfo' }
    ],
    note: '#include veil:camera 已 deprecated（camera.glsl 里就写着换成 #veil:buffer）；块名写错不会报错，只会全部读到 0。'
  },
  {
    id: 'java',
    title: 'Java 侧签名（javap 逐个核对）',
    signatures: [
      'VeilRenderType.get(ResourceLocation, Object...) → RenderType（可空）',
      'VeilRenderSystem.renderer() → VeilRenderer',
      'VeilRenderer.getShaderManager() / getShaderDefinitions() / getPostProcessingManager() / getFramebufferManager() / getDynamicRenderTypeManager()',
      'ShaderManager.getShader(ResourceLocation) → ShaderProgram（可空）',
      'ShaderProgram.getUniform(CharSequence) → ShaderUniformAccess（非 null，要判 isValid()）',
      'ShaderUniformAccess.setFloat / setVector(2..4) / setInt / setVectorI / setDouble / setLong / setFloats / setInts',
      'PostProcessingManager.add(ResourceLocation) / add(int, ResourceLocation) / remove / isActive / getPipeline / getPipelines',
      'VeilEventPlatform.INSTANCE.preVeilPostProcessing((ResourceLocation, PostPipeline, PostPipeline.Context) -> {})',
      'ShaderPreDefinitions.set / set(String,String) / setStatic / remove（无 addGlobal）'
    ]
  }
];

export const SCHEMA_BY_ID = Object.fromEntries(SCHEMA.map(s => [s.id, s]));
