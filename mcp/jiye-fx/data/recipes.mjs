export const RECIPES = [
  {
    id: 'volume-shell',
    title: '体积壳：菲涅尔 + 厚度',
    when: '水体、气场、护罩、翅膀等“有外壳的半透明体积”。',
    code: `vec3 n = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
if (dot(n, viewDir) < 0.0) n = -n;
float facing = clamp(dot(n, viewDir), 0.0, 1.0);
float fresnel = pow(1.0 - facing, 3.2);
float frontFace = gl_FrontFacing ? 1.0 : 0.0;
float thickness = clamp((1.0 - facing) * 0.72
        + (1.0 - frontFace) * 0.52, 0.0, 1.0);
vec3 color = mix(clearWater, abyss, thickness * 0.76);
float alpha = vColor.a * (0.12 + thickness * 0.13 + fresnel * 0.12);
if (alpha < 0.008) discard;
fragColor = vec4(color, clamp(alpha, 0.0, 0.72));`,
    params: ['法线由 dFdx/dFdy 现算，省顶点属性', 'facing 记得翻面', '厚度用 frontFace 加权'],
    pitfalls: ['不要用 alpha 直接当厚度：背面不会更暗', 'fresnel 指数 2~3.6，太低会整片发亮']
  },
  {
    id: 'depth-guard',
    title: '屏幕折射：深度守卫三条件',
    when: '任何用 scene depth 做折射/软交界的体积场。',
    code: `vec2 offset = viewNormal.xy * (0.0012 + strength * 0.0038)
        + fbm2(worldXZ * 1.5 + t) * 0.0013;
vec2 uv = clamp(texCoord + offset, vec2(0.001), vec2(0.999));
float cand = texture(DiffuseDepthSampler, uv).r;
if (sceneDepth + 0.0008 < fieldDepth
        || cand + 0.0008 < fieldDepth
        || abs(cand - sceneDepth) > 0.015) {
    uv = texCoord;              // 放弃偏移，退回原像素
}
vec3 refracted = texture(DiffuseSampler0, uv).rgb;`,
    params: ['阈值固定 0.0008（几页全库统一）', '跨物体差 >0.015 视为越界'],
    pitfalls: ['只判一侧会在轮廓处穿帮', '别忘了 clamp uv，否则边缘采样到重复']
  },
  {
    id: 'crosshair-safe',
    title: '准星安全区',
    when: '全屏折射/扭曲类效果，防止中心糊住准星。',
    code: `vec2 d = (texCoord - 0.5) / vec2(0.27, 0.22);
float safe = mix(0.42, 1.0, smoothstep(0.18, 0.72, length(d)));
offset *= safe;`,
    params: ['fan_wind 用 0.27×0.22，water_wave 用 0.24×0.20'],
    pitfalls: ['安全区太小会明显“中心一块不动”，0.4~0.5 的衰减下限比较自然']
  },
  {
    id: 'cloud-plane',
    title: '世界空间云顶：ray-plane + 深度剔除',
    when: '云灾劫、天幕、穹顶类，要求不被屋顶/地形穿透。',
    code: `// rayDir 由 screenToLocalSpace(texCoord, depth) 归一化得到
if (rayDir.y <= 0.002) { fragColor = vec4(scene, base.a); return; }
float cloudDistance = (center.y + altitude) / rayDir.y;
if (cloudDistance <= 0.0 || cloudDistance >= sceneDistance - 0.35) {
    fragColor = vec4(scene, base.a); return;      // 被地形挡住
}
vec3 p = rayDir * cloudDistance;
vec2 uv = (p.xz - center.xz) * 0.072 + drift;
float density = smoothstep(0.30, 0.69,
        fbm(uv * 0.36) * 0.55 + fbm(uv * 0.78) * 0.37 + fbm(uv * 1.48) * 0.20);`,
    params: ['高度 23~52（三层）', '半径边缘 0.68~1.0 淡出'],
    pitfalls: ['只看 rayDir.y 不够，必须和场景距离比较', '云平面别用固定 UV，否则贴图随视角滑动']
  },
  {
    id: 'fbm-warp',
    title: '多层 fbm 域扭曲',
    when: '天空、墨、水、雾，一切要“流动感”的地方。',
    code: `float warpA = fbm(uv * 1.85 + vec2(t * 0.024, -t * 0.019));
float warpB = fbm(uv * 2.75 + vec2(-t * 0.016, t * 0.022)
        + vec2(warpA, -warpA) * 1.45);
vec2 warped = uv + vec2(warpA - 0.5, warpB - 0.5) * 0.16;`,
    params: ['两层足够，三层开始糊', '频率比 1.5~1.8'],
    pitfalls: ['fbm 返回值要减 0.5 再用，否则整体偏移', '极坐标下要用方向向量采样，避免 atan 接缝']
  },
  {
    id: 'log-spiral',
    title: '对数螺旋 + 高斯环',
    when: '漩涡、法阵、曼陀罗、巨构。',
    code: `float spiralRibbon(float th, float r, float arms, float twist,
                   float phase, float soft) {
    float w = abs(sin(th * arms - log(r + 0.035) * twist + phase));
    return 1.0 - smoothstep(0.06, soft, w);
}
float gaussianRing(float r, float c, float w) {
    float x = (r - c) / w; return exp(-x * x);
}`,
    params: ['arms 2.5~7，twist 6~31', 'soft 0.20（细芯）/0.92（宽体）'],
    pitfalls: ['log(r+0.035) 的小偏移防止 r=0 爆掉', '环要用高斯，不要用 smoothstep 硬边']
  },
  {
    id: 'ten-skies',
    title: '一套算法 × N 种天色',
    when: '多重天象/位面/势力配色。',
    code: `// include: float renderSky() { ... 只读 uniform ... }
// 10 个 fsh: void main() { fragColor = vec4(renderSky(), vColor.a); }
// 10 个 post 共用同一 fragment：uniform vec3 HeavenTint; uniform vec3 HeavenFog;
// Java: pipeline.getUniform("HeavenTint").setVector(r,g,b);`,
    params: ['6 个 sky uniform + 2 个 post uniform'],
    pitfalls: ['颜色写进 GLSL 就只能一份；全部走 uniform', 'post JSON 不写 uniform，值由代码推']
  },
  {
    id: 'two-pass-bloom',
    title: '双趟 bloom（不改 shader 的发光边）',
    when: '轮廓要自发光，但不想写第二份 shader。',
    code: `"layers": [
  [ { "type": "veil:shader", "name": "modid:fx" },
    { "type": "minecraft:write_mask", "color": true, "depth": false },
    { "type": "minecraft:transparency", "mode": "translucent" } ],
  [ { "type": "veil:shader", "name": "modid:fx" },
    { "type": "minecraft:write_mask", "color": true, "depth": false },
    { "type": "minecraft:transparency", "mode": "additive" },
    { "type": "minecraft:output", "framebuffer": "veil:bloom" } ]
]`,
    params: ['第二趟 additive + output veil:bloom', '需要更强就再写一份 ×2.5 的 bloom fsh'],
    pitfalls: ['两趟都用同一个 shader 时 alpha 会叠加，必要时在第二趟降低 alpha', 'bloom 阈值由 Veil 配置决定']
  },
  {
    id: 'rewind-11-taps',
    title: '时间回溯：11 采样',
    when: '倒流、残影、时间减速。',
    code: `vec3 acc = vec3(0.0); float wsum = 0.0;
for (int i = 0; i < 11; i++) {
    float s = float(i) / 10.0;
    float w = exp(-s * 2.35);
    vec2 uv = clamp(texCoord + reverseVector * s * (1.0 + s * 2.8),
                    vec2(0.001), vec2(0.999));
    acc += texture(DiffuseSampler0, uv).rgb * w;
    wsum += w;
}
vec3 scene = acc / wsum;`,
    params: ['11 采样 / 权重指数 2.35', '反向向量 = 径向 × 深度层'],
    pitfalls: ['循环上限必须是常量，不能是 uniform', '需要保留原场景时用 copy stage 到私有 FBO']
  },
  {
    id: 'screen-shake-chroma',
    title: '屏幕震动 + 色散',
    when: '受击、回溯、不稳定、炼蛊失败。',
    code: `float chroma = instability * intensity * (0.0012 + edge * 0.0024);
scene.r = texture(S, uv + dir * chroma).r;
scene.b = texture(S, uv - dir * chroma).b;`,
    params: ['色散 0.0012~0.0036', '方向 = 径向或风向'],
    pitfalls: ['只分离 R/B 就够，G 保持中心', '色散别超过 0.01，会晕']
  },
  {
    id: 'soft-quad',
    title: '2D 图元软遮罩',
    when: '斩击、雨、雷、眼、环——所有贴片招式。',
    code: `float core   = exp(-x * x * 58.0);                          // 芯
float sheath = 1.0 - smoothstep(0.34, 1.0, abs(x));          // 鞘
float tip    = smoothstep(0.0, 0.12, uv.y)
             * (1.0 - smoothstep(0.72, 1.0, uv.y));          // 两端收口
float alpha  = vColor.a * tip * sheath * (0.32 + core * 0.92);
if (alpha <= 0.004) discard;`,
    params: ['core 指数 50~90（细）', '鞘用 smoothstep，别用线性'],
    pitfalls: ['UV 要先中心化（vsh 里 UV*2-1）', '两端收口不做会看到硬切边']
  },
  {
    id: 'stage-uniforms',
    title: '阶段参数组（一个效果一套 uniform）',
    when: '灾劫/升仙劫/领域等长生命周期效果。',
    code: `uniform float FxStrength;    // 0..1  总强度
uniform float FxPhase;       // 0..20 相位（t 的函数）
uniform float FxProgress;    // 0..1  进度
uniform float FxFog;         // 0..0.28 远景雾
uniform float FxDisturb;     // 0..1  扰动
uniform float FxFlash;       // 0..1  闪白
uniform float FxRecovery;    // 0..1  恢复（mix 回原图）`,
    params: ['命名 = 家族 + 语义，PascalCase', '默认值写进 Java 常量表'],
    pitfalls: ['不要一个阶段一个 shader', '不要把 t 直接当进度：进度由玩法推']
  },
  {
    id: 'data-driven-particle',
    title: '数据驱动粒子（光子 core）',
    when: '粒子颜色/形状要由曲线与渐变控制，而不是写死。',
    code: `#moj_import <photon:particle_utils.glsl>
float radial = getCurveValue(SamplerCurve, 0, clamp(radius, 0.0, 1.0));
vec4 earth  = getGradientValue(SamplerGradient, 0,
        clamp(vUv.y * 0.58 + grain * 0.42, 0.0, 1.0));`,
    params: ['SamplerCurve/SamplerGradient 由 Photon 自动挂'],
    pitfalls: ['curve 的 t 是 0..1 归一化，不是秒', '粒子 vsh 要 #moj_import <photon:particle.glsl> 才有 getParticleData()']
  },
  {
    id: 'per-frame-trail',
    title: '拖尾丝带：每帧采样 + 面向相机',
    when: '刀光/剑气/飞刃残迹——任何高速移动物体身后的发光带。实战：guzhenren-ui 刀道附属（巨剑斩 + 刀灵两处共用）。',
    code: `// ① 每帧采样进环形缓冲（nanoTime 计时，超窗出队）——不要按 tick 采，20Hz 会一顿一顿
trail.addLast(new long[]{System.nanoTime(),
    Double.doubleToLongBits(p.x), Double.doubleToLongBits(p.y), Double.doubleToLongBits(p.z)});
while (trail.size() > MAX_POINTS) trail.pollFirst();
long windowNanos = (long) (WINDOW_S * 1_000_000_000L);
while (!trail.isEmpty() && now - trail.peekFirst()[0] > windowNanos) trail.pollFirst();
// ② 每点宽度方向 = 前后段平均切线 × 指向相机 ⇒ 相邻段共用端点，接缝连续
Vec3 prev = k > 0 ? pts[k].subtract(pts[k - 1]) : pts[1].subtract(pts[0]);
Vec3 next = k < n - 1 ? pts[k + 1].subtract(pts[k]) : pts[n - 1].subtract(pts[n - 2]);
Vec3 side = prev.add(next).cross(cam.subtract(pts[k])).normalize();
// ③ 顶点按「相对实体原点」写（poseStack 原点 = 实体，世界坐标必须减实体位置）
vc.addVertex(pose.last(), (float)(p.x - ent.x), (float)(p.y - ent.y), (float)(p.z - ent.z))
  .setColor(r, g, b, (int)(Mth.clamp(alpha, 0, 1) * 255)).setUv(0.5F, v)
  .setLight(LightTexture.FULL_BRIGHT);`,
    params: [
      '每帧采样与帧率同频，且零网络同步开销（位置靠原版实体追踪）',
      '窗 0.3~0.55s、段数 10~32；按采样年龄幂次淡出（见 additive-eyes 的 fade）',
      '慢速时按段长收细（segLen/0.25 钳 0..1）：环绕游动只留一丝尾迹，冲刺才拉满'
    ],
    pitfalls: [
      '按 tick 采样 = 20Hz 抖动，淡出做得再好也救不回来',
      '竖切时「剑身扫过的面」侧对镜头，第一人称只看一条线——要画刃尖轨迹的光带，不是扫过面',
      '挥击角度 / 命中判定 / 拖尾必须同一个规则层取数；三处各写一份数学必错位（横切刀光挥反过）',
      '同 RenderType 的三角形必须连续写完：MultiBufferSource 每取一个新类型会把上一个共享池 endBatch'
    ]
  },
  {
    id: 'additive-eyes',
    title: '零依赖发光带：RenderType.eyes + 软边贴图',
    when: '不引 Veil/光子，也要「全亮、加法叠加、带软边贴图」的刀光/剑气。实战：daodao 零客户端库依赖（run/mods 无 Veil）。',
    code: `// 材质：原版 eyes = 加法混合 + 全亮 + 贴图，不需要任何前置库
VertexConsumer vc = buffers.getBuffer(RenderType.eyes(TRAIL_TEX));
// 贴图约定：u=0.5（对称软边）；v=0 透明内缘 → v=1 亮刃缘 ⇒ 宽鞘 + 窄芯两层叠
vert(vc, pose, p, ent, r, g, b, alpha, 0.0F);   // 内缘
vert(vc, pose, p, ent, r, g, b, alpha, 1.0F);   // 亮缘
// 淡出：年龄 0=最新最亮 → 1=最老透明；幂次比线性收得自然
static float fade(float age) {
    float f = Mth.clamp(1.0F - age, 0.0F, 1.0F);
    return f * f * (0.35F + 0.65F * f);
}`,
    params: [
      '两层：刃尖光带（面向镜头双面，α≈0.85）+ 扫过面大鞘（α≈0.30，内缘随年龄收窄）',
      '颜色走数据（0xRRGGBB 的 trailColor），不写进代码',
      '原版粒子一律不用——拖尾即全部特效（用户口径：只要刀光）'
    ],
    pitfalls: [
      'alpha 线性淡出会「突然消失」，必须幂次',
      'eyes 全亮：黑夜白天都发光；想要受光/投影效果别用 eyes',
      '环形缓冲放实体字段上、仅客户端渲染碰；服务端不分配不访问',
      '双面写（正反两组三角），否则侧对镜头时整条带消失'
    ]
  }
];

export const RECIPE_BY_ID = Object.fromEntries(RECIPES.map(r => [r.id, r]));
