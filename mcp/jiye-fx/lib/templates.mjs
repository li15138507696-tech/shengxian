// 特效包模板：把 spec 变成一组 Veil 资源 + 可选的 Java 胶水。
// 设计原则全部来自几页 469 文件的实测规则（见 几页升先/几页特效规范-SKILL.md）。

export const KINDS = {
  screen_post: {
    title: '屏幕后处理（灾劫/天象/滤镜）',
    requires: 'veil',
    pieces: ['post', 'program', 'frag'],
    desc: '全屏 blit，采样场景色+深度，做去饱和/远景分级/雾/暗角/闪白/恢复。'
  },
  domain_sphere: {
    title: '世界球域领域',
    requires: 'veil',
    pieces: ['post', 'program', 'frag'],
    desc: 'post 里反算世界点 + 天空射线补 mask，球内才染色，绝不全屏贴色。'
  },
  volume_field: {
    title: '体积场（热浪/毒雾/结界/水墙）',
    requires: 'veil',
    pieces: ['framebuffer', 'rendertype', 'field_program', 'field_vsh', 'field_frag', 'composite_program', 'composite_frag', 'post'],
    desc: '两趟：field 写 MRT（法线+覆盖/材质），composite 全屏采样做深度守卫折射。'
  },
  skybox: {
    title: '程序化天空盒',
    requires: 'veil',
    pieces: ['rendertype', 'program', 'vsh', 'frag'],
    desc: 'vsh 用 mat3(ViewMat) + clip.z=clip.w*0.9998 钉在远平面；fsh 程序化云/星/日。'
  },
  combat_quad: {
    title: '招式贴片（斩/雨/雷/环/眼）',
    requires: 'veil',
    pieces: ['rendertype', 'program', 'vsh', 'frag'],
    desc: '世界坐标 quad，vsh 把 UV 中心化成 -1..1，fsh 用 exp/smoothstep 做芯+鞘+收口。'
  },
  entity_aura: {
    title: '实体挂件（光环/翅膀/衣饰）',
    requires: 'veil',
    pieces: ['rendertype', 'program', 'vsh', 'frag'],
    desc: 'NEW_ENTITY 格式（quad + lightmap + overlay），菲涅尔+流带，可双趟进 bloom。'
  },
  item_glow: {
    title: '物品轮廓辉光',
    requires: 'veil',
    pieces: ['rendertype', 'program', 'vsh', 'frag'],
    desc: 'NEW_ENTITY + Sampler0=blocks atlas；alpha 邻域差取轮廓 + 调色板循环。'
  },
  water_body: {
    title: '水体 / 水牢 / 水衣外壳',
    requires: 'veil',
    pieces: ['rendertype', 'program', 'vsh', 'frag'],
    desc: '菲涅尔 + 视厚 + Beer-Lambert 吸收 + 泡沫；适合球体/壳/面片。'
  },
  model_pbr: {
    title: 'PBR 模型材质（需已有模型）',
    requires: 'veil',
    pieces: [],
    desc: '主材质 + fallback 双 program；本工具只给骨架，模型请用 glTF/OBJ。'
  }
};

const DEFAULT_COLORS = {
  screen_post: { primary: '#7a1f2b', secondary: '#d94a3d', accent: '#ffd166' },
  domain_sphere: { primary: '#5a0010', secondary: '#c1121f', accent: '#ff4d4d' },
  volume_field: { primary: '#9fd8d0', secondary: '#2b6f6a', accent: '#d8fff8' },
  skybox: { primary: '#0a1030', secondary: '#2b3f7a', accent: '#ffd98a' },
  combat_quad: { primary: '#8fd3ff', secondary: '#2b6cff', accent: '#ffffff' },
  entity_aura: { primary: '#3fd0c9', secondary: '#2b6cff', accent: '#ffb347' },
  item_glow: { primary: '#ffd166', secondary: '#7ef0d0', accent: '#ffffff' },
  water_body: { primary: '#1f6fa8', secondary: '#7ee8ff', accent: '#d8fbff' }
};

const DEFAULT_UNIFORMS = {
  screen_post: [
    ['Strength', 'float', 0.8, '总强度'],
    ['Phase', 'float', 0.0, '相位（t*速度）'],
    ['Progress', 'float', 0.0, '玩法进度'],
    ['Fog', 'float', 0.10, '远景雾密度'],
    ['Disturbance', 'float', 0.5, '扰动强度'],
    ['Flash', 'float', 0.0, '闪白'],
    ['Recovery', 'float', 0.0, '恢复比例']
  ],
  domain_sphere: [
    ['Center', 'vec3', [0, 64, 0], '世界中心'],
    ['Radius', 'float', 24.0, '半径（格）'],
    ['Strength', 'float', 0.8, '总强度'],
    ['Phase', 'float', 0.0, '相位'],
    ['Active', 'float', 1.0, '开关']
  ],
  volume_field: [
    ['Strength', 'float', 1.0, '总强度'],
    ['Turbulence', 'float', 0.5, '湍流'],
    ['Condensation', 'float', 0.6, '凝结'],
    ['Progress', 'float', 0.0, '进度']
  ],
  skybox: [
    ['MotionScale', 'float', 1.0, '运动速度'],
    ['StarDensity', 'float', 0.08, '星密度'],
    ['Phase', 'float', 0.0, '相位']
  ],
  combat_quad: [
    ['Progress', 'float', 1.0, '进度 0..1'],
    ['Intensity', 'float', 1.0, '强度'],
    ['DirectionX', 'float', 0.0, '方向 X'],
    ['DirectionZ', 'float', 1.0, '方向 Z']
  ],
  entity_aura: [
    ['Palette', 'float', 0.0, '调色板 0..9'],
    ['Pulse', 'float', 0.5, '脉动'],
    ['Alpha', 'float', 0.8, '整体透明度']
  ],
  item_glow: [
    ['Palette', 'float', 0.0, '调色板 0..9'],
    ['Glow', 'float', 0.8, '辉光'],
    ['Pulse', 'float', 0.5, '脉动']
  ],
  water_body: [
    ['Flow', 'float', 1.0, '流速'],
    ['Foam', 'float', 0.4, '泡沫'],
    ['Thickness', 'float', 0.6, '厚度'],
    ['Alpha', 'float', 0.75, '透明度']
  ],
  model_pbr: []
};

export function pascal(s) {
  const parts = String(s).replace(/[^A-Za-z0-9]+/g, '_').split('_').filter(Boolean);
  let out = parts.map(p => p[0].toUpperCase() + p.slice(1)).join('');
  if (!/^[A-Za-z]/.test(out)) out = 'Fx' + out;
  return out || 'Fx';
}

export function hexToVec3(hex) {
  const m = /^#?([0-9a-fA-F]{6})$/.exec(String(hex || '').trim());
  const n = m ? parseInt(m[1], 16) : 0xffffff;
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

function vec3Literal(v) {
  const a = Array.isArray(v) ? v : hexToVec3(v);
  return `vec3(${a.map(x => Number(x).toFixed(4)).join(', ')})`;
}

export function slugOk(name) {
  return /^[a-z][a-z0-9_]{1,40}$/.test(String(name || ''));
}

export function normalizeSpec(input = {}) {
  const name = String(input.name || '').trim();
  if (!slugOk(name)) throw new Error('name 必须是 ASCII 小写蛇形（如 blood_domain），当前：' + JSON.stringify(name));
  const kind = input.kind || 'screen_post';
  if (!KINDS[kind]) throw new Error('未知 kind：' + kind + '；可选 ' + Object.keys(KINDS).join(', '));
  const modid = String(input.modid || 'shengxian');
  if (!/^[a-z][a-z0-9_]{1,30}$/.test(modid)) throw new Error('modid 非法：' + modid);
  let family = String(input.family || name);
  // 避免双后缀：xxx_field_field / xxx_sky_sky
  if (kind === 'volume_field') family = family.replace(/_field$/, '');
  if (kind === 'skybox') family = family.replace(/_sky$/, '');
  if (kind === 'screen_post' || kind === 'domain_sphere') family = family.replace(/_post$/, '');
  const P = pascal(family);
  const colors = { ...(DEFAULT_COLORS[kind] || DEFAULT_COLORS.screen_post), ...(input.colors || {}) };
  const stage = input.stage || 'after_level';
  const priority = Number.isFinite(input.priority) ? input.priority : 1210;
  const bloom = input.bloom === true;
  const target = input.target || 'veil';
  const javaPackage = input.javaPackage || `net.guzhenren.${modid}.vfx`;

  let uniforms;
  if (Array.isArray(input.uniforms) && input.uniforms.length) {
    uniforms = input.uniforms.map(u => {
      const raw = typeof u === 'string' ? { name: u } : u;
      let n = String(raw.name || raw[0] || '').trim();
      if (!n) throw new Error('uniform 缺少 name');
      if (!n.startsWith(P)) n = P + n[0].toUpperCase() + n.slice(1);
      const type = raw.type || 'float';
      return { name: n, type, default: raw.default ?? 0, desc: raw.desc || '' };
    });
  } else {
    uniforms = (DEFAULT_UNIFORMS[kind] || []).map(([n, type, def, desc]) => ({
      name: P + n, type, default: def, desc
    }));
  }
  // 颜色统一作为 <P>Tint
  if (['screen_post', 'domain_sphere', 'volume_field', 'skybox', 'combat_quad', 'entity_aura', 'item_glow', 'water_body'].includes(kind)) {
    if (!uniforms.some(u => u.name === P + 'Tint')) {
      uniforms.push({ name: P + 'Tint', type: 'vec3', default: hexToVec3(colors.primary), desc: '主题色' });
    }
  }

  return {
    name, family, modid, kind, target, colors, stage, priority, bloom, javaPackage, uniforms,
    title: input.title || family,
    notes: input.notes || ''
  };
}

function glslUniforms(uniforms) {
  return uniforms.map(u => {
    if (u.type === 'vec3') return `uniform vec3 ${u.name};`;
    if (u.type === 'int') return `uniform int ${u.name};`;
    return `uniform float ${u.name};`;
  }).join('\n');
}

const NOISE_LIB = `float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    mat2 r = mat2(0.80, -0.60, 0.60, 0.80);
    for (int i = 0; i < 5; i++) {
        v += vnoise(p) * a;
        p = r * p * 2.03 + vec2(7.7, 13.1);
        a *= 0.49;
    }
    return v;
}`;

function header(comment) {
  return `// ${comment}\n// 生成：几页特效工坊（jiye-fx）。规则：早退 discard / 深度守卫 0.0008 / 准星安全区 / 主题色只走 uniform。\n`;
}

// ---------- 各 kind 的 fsh ----------
function fshScreenPost(spec) {
  const P = pascal(spec.family);
  return `${header(spec.family + ' 屏幕后处理')}
${glslUniforms(spec.uniforms)}
uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform float VeilRenderTime;

in vec2 texCoord;
out vec4 fragColor;

${NOISE_LIB}

void main() {
    vec4 base = texture(DiffuseSampler0, texCoord);
    float progress = clamp(${P}Progress, 0.0, 1.0);
    float strength = clamp(${P}Strength, 0.0, 1.0) * mix(0.25, 1.0, smoothstep(0.0, 0.30, progress));
    if (strength <= 0.001) {
        fragColor = base;
        return;
    }
    float phase = ${P}Phase;

    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec2 centered = texCoord - vec2(0.5);
    float radius = length(centered);

    // 1) 去饱和：只去一点，保留可读性
    float lum = dot(base.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 scene = mix(base.rgb, vec3(lum), strength * 0.16);

    // 2) 远景分级：近处不染色
    float far = smoothstep(0.72, 0.995, depth);
    vec3 graded = scene * mix(vec3(1.0), ${P}Tint, 0.72) + ${P}Tint * 0.02;
    scene = mix(scene, graded, far * ${P}Fog * 0.85);

    // 3) 低频湍流：只在远景，避免糊脸
    float turb = fbm(centered * vec2(3.2, 5.8)
            + vec2((VeilRenderTime + phase) * 0.07, -(VeilRenderTime + phase) * 0.05));
    scene += ${P}Tint * (turb - 0.5) * far * ${P}Disturbance * 0.06;

    // 4) 中心压力 + 暗角
    scene *= 1.0 - strength * 0.10 * exp(-radius * 3.0);
    scene *= 1.0 - smoothstep(0.32, 0.86, radius) * strength * 0.22;

    // 5) 闪白
    scene = mix(scene, vec3(0.95, 0.98, 1.0), clamp(${P}Flash * 0.72, 0.0, 0.85));

    // 6) 恢复
    scene = mix(scene, base.rgb, clamp(${P}Recovery, 0.0, 1.0));

    fragColor = vec4(max(scene, vec3(0.0)), base.a);
}
`;
}

function fshDomain(spec) {
  const P = pascal(spec.family);
  return `${header(spec.family + ' 世界球域')}
#include veil:space_helper
#veil:buffer veil:camera VeilCamera

${glslUniforms(spec.uniforms)}
uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform float VeilRenderTime;

in vec2 texCoord;
out vec4 fragColor;

${NOISE_LIB}

void main() {
    vec4 base = texture(DiffuseSampler0, texCoord);
    float strength = clamp(${P}Strength, 0.0, 1.0) * clamp(${P}Active, 0.0, 1.0);
    if (strength <= 0.001) {
        fragColor = base;
        return;
    }

    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec3 local = screenToLocalSpace(texCoord, min(depth, 0.9999)).xyz;
    vec3 world = local + VeilCamera.CameraPosition;
    float dist = distance(world, ${P}Center);
    float radius = max(${P}Radius, 1.0);

    float interior = 1.0 - smoothstep(radius * 0.18, radius * 0.86, dist);
    float edge = smoothstep(radius * 0.58, radius, dist);

    // 天空像素按射线最近距离补 mask，否则仰头会看到硬边
    float sky = smoothstep(0.996, 1.0, depth);
    vec3 ray = normalize(local);
    vec3 toCenter = ${P}Center - VeilCamera.CameraPosition;
    float along = max(dot(toCenter, ray), 0.0);
    vec3 closest = VeilCamera.CameraPosition + ray * along;
    float skyDist = distance(closest, ${P}Center);
    float skyMask = sky * (1.0 - smoothstep(radius * 0.80, radius, skyDist));

    float field = max(interior, skyMask);
    vec2 drift = vec2(VeilRenderTime * 0.055, -VeilRenderTime * 0.041);
    float large = fbm(world.xz * 0.026 + drift);
    float rolling = fbm(world.yz * 0.062 + vec2(-drift.y, drift.x * 1.2));
    float wisps = vnoise(world.xy * 0.13 + vec2(VeilRenderTime * 0.13, -VeilRenderTime * 0.09));
    float mist = smoothstep(0.30, 0.78, large * 0.58 + rolling * 0.30 + wisps * 0.12);
    float pulse = 0.84 + 0.16 * sin(${P}Phase + mist * 5.0);

    float fogAmount = clamp(field * (0.38 + mist * 0.44) * pulse, 0.0, 0.86);
    vec3 deep = ${P}Tint * 0.22;
    vec3 mid = ${P}Tint * 0.78;
    vec3 hot = mix(${P}Tint, vec3(1.0, 0.35, 0.28), 0.35);
    vec3 mistColor = mix(deep, mid, smoothstep(0.20, 0.92, mist));
    vec3 graded = base.rgb * mix(vec3(1.0), ${P}Tint, 0.55) + mistColor * (0.34 + mist * 0.20);
    vec3 result = mix(base.rgb, graded, fogAmount);

    float strand = smoothstep(0.42, 0.86,
            vnoise(world.xz * 0.095 + vec2(-VeilRenderTime * 0.12, VeilRenderTime * 0.075)));
    result += hot * strand * field * 0.055;
    result += hot * edge * (0.48 + 0.52 * mist) * 0.14;

    fragColor = vec4(max(result, vec3(0.0)), base.a);
}
`;
}

function fshField(spec) {
  return `${header(spec.family + ' 体积场写入（MRT）')}
#veil:buffer veil:camera VeilCamera

in vec2 vUv;
in vec4 vMaterial;
in vec3 vWorldPos;

layout(location = 0) out vec4 fxData;
layout(location = 1) out vec4 fxMaterial;

${NOISE_LIB}

void main() {
    float coverage = vMaterial.a;
    if (coverage < 0.004) discard;

    vec3 normal = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
    vec3 viewDir = normalize(VeilCamera.CameraPosition - vWorldPos);
    if (dot(normal, viewDir) < 0.0) normal = -normal;

    // 边缘淡出：正对镜头的薄壳不写，避免硬边
    float facing = smoothstep(0.025, 0.32, abs(dot(normal, viewDir)));
    coverage *= facing;
    if (coverage < 0.004) discard;

    // 种类 g：0=薄雾 0.5=主体 1=锋面（由 CPU 按状态写进顶点色）
    fxData = vec4(normal * 0.5 + 0.5, coverage);
    fxMaterial = vec4(vMaterial.r, vMaterial.g, vMaterial.b, 1.0);
}
`;
}

function fshComposite(spec) {
  const P = pascal(spec.family);
  return `${header(spec.family + ' 体积场合成')}
#include veil:space_helper
#veil:buffer veil:camera VeilCamera

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D ${P}DataSampler;
uniform sampler2D ${P}MaterialSampler;
uniform sampler2D ${P}DepthSampler;
uniform float VeilRenderTime;

${glslUniforms(spec.uniforms)}

in vec2 texCoord;
out vec4 fragColor;

${NOISE_LIB}

void main() {
    vec4 base = texture(DiffuseSampler0, texCoord);
    vec4 field = texture(${P}DataSampler, texCoord);
    float progress = clamp(${P}Progress, 0.0, 1.0);
    float coverage = field.a * clamp(${P}Strength, 0.0, 1.0)
            * mix(0.35, 1.0, smoothstep(0.0, 0.30, progress));
    if (coverage < 0.003) {
        fragColor = base;
        return;
    }

    vec4 material = texture(${P}MaterialSampler, texCoord);
    vec3 normal = normalize(field.xyz * 2.0 - 1.0);
    vec3 viewNormal = normalize(mat3(VeilCamera.ViewMat) * normal);
    float sceneDepth = texture(DiffuseDepthSampler, texCoord).r;
    float fieldDepth = texture(${P}DepthSampler, texCoord).r;

    vec3 fieldPos = screenToLocalSpace(texCoord, fieldDepth).xyz;
    vec3 world = fieldPos + VeilCamera.CameraPosition;
    vec2 flowUv = world.xz * vec2(1.52, 1.17)
            + vec2(VeilRenderTime * 1.24, -VeilRenderTime * 0.88);
    vec2 turb = vec2(fbm(flowUv), fbm(flowUv.yx + vec2(23.8, -14.2))) - 0.5;

    vec2 offset = viewNormal.xy * (0.0014 + material.g * 0.0048)
                + turb * (0.0012 + ${P}Turbulence * 0.0022);

    // 准星安全区：中心偏移压到 42%
    vec2 safeDelta = (texCoord - vec2(0.5)) / vec2(0.27, 0.22);
    offset *= mix(0.42, 1.0, smoothstep(0.18, 0.72, length(safeDelta)));

    vec2 uv = clamp(texCoord + offset, vec2(0.001), vec2(0.999));
    float cand = texture(DiffuseDepthSampler, uv).r;
    if (sceneDepth + 0.0008 < fieldDepth || cand + 0.0008 < fieldDepth) {
        uv = texCoord;                       // 深度守卫：任一不满足就回退
    }

    vec3 refracted = texture(DiffuseSampler0, uv).rgb;
    vec3 color = mix(base.rgb, refracted, coverage * (0.40 + material.g * 0.34));
    float condensation = clamp(${P}Condensation, 0.0, 1.0);
    color = mix(color, ${P}Tint, clamp(material.b * coverage * 0.18, 0.0, 0.30));
    color += ${P}Tint * condensation * coverage * 0.05;
    color += ${P}Tint * material.g * coverage * 0.06;

    fragColor = vec4(max(color, vec3(0.0)), base.a);
}
`;
}

function fshSky(spec) {
  const P = pascal(spec.family);
  return `${header(spec.family + ' 程序化天空')}
${glslUniforms(spec.uniforms)}
uniform float VeilRenderTime;

in vec3 vDirection;
in vec4 vColor;
out vec4 fragColor;

${NOISE_LIB}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float starLayer(vec3 dir, float scale, float threshold, float size) {
    vec3 grid = dir * scale;
    vec3 cell = floor(grid);
    vec3 local = fract(grid);
    vec3 point = vec3(hash31(cell), hash31(cell + 17.3), hash31(cell + 41.7));
    float dist = length(local - point);
    float exists = smoothstep(threshold, 1.0, hash31(cell + 19.7));
    return (1.0 - smoothstep(size * 0.32, size, dist)) * exists;
}

void main() {
    float t = VeilRenderTime * max(0.08, ${P}MotionScale) + ${P}Phase;
    vec3 d = normalize(vDirection);
    d.xz = mat2(cos(t * 0.004), -sin(t * 0.004), sin(t * 0.004), cos(t * 0.004)) * d.xz;

    float horizon = exp(-abs(d.y) * 4.6);
    float zenith = smoothstep(-0.18, 0.92, d.y);
    float broad = fbm(d.xy * 1.65 + vec2(t * 0.008, -t * 0.006));
    float middle = fbm(d.zy * 4.20 + vec2(-t * 0.014, t * 0.011));
    float fine = fbm(d.xz * 11.0 + vec2(t * 0.031, -t * 0.022));

    float bandA = smoothstep(0.34, 0.82,
            broad * 0.70 + 0.30 * sin(atan(d.z, d.x) * 3.2 + d.y * 10.0 - t * 0.31));
    float bandB = smoothstep(0.42, 0.86,
            middle * 0.62 + 0.38 * sin(atan(d.x, d.z) * 7.0 - d.y * 15.0 + t * 0.19 + fine * 2.7));
    float cloud = bandA * (0.48 + bandB * 0.52);

    vec3 baseColor = ${P}Tint * 0.35;
    vec3 horizonColor = mix(${P}Tint, vec3(1.0, 0.82, 0.60), 0.45);
    vec3 accentColor = mix(${P}Tint, vec3(1.0), 0.55);

    vec3 color = mix(baseColor, horizonColor, horizon * 0.72);
    color = mix(color, baseColor, zenith * 0.30 + broad * 0.10);
    color = mix(color, accentColor, cloud * (0.12 + horizon * 0.24));
    color += horizonColor * pow(max(horizon, 0.0), 2.0) * (0.08 + bandB * 0.12);

    vec3 sunDir = normalize(vec3(0.36, 0.48, -0.80));
    float sun = pow(max(dot(d, sunDir), 0.0), 80.0);
    float halo = pow(max(dot(d, sunDir), 0.0), 8.0);
    color += accentColor * (sun * 0.80 + halo * 0.08);

    float stars = starLayer(d, 245.0, 1.0 - clamp(${P}StarDensity, 0.0, 0.22), 0.14);
    float small = starLayer(d + vec3(0.013, -0.019, 0.011), 420.0,
            1.0 - clamp(${P}StarDensity * 0.68, 0.0, 0.22), 0.10);
    color += mix(horizonColor, accentColor, 0.55) * stars * (0.18 + zenith * 0.72);
    color += horizonColor * small * 0.20 * zenith;

    color *= 0.94 + 0.06 * sin(t * 0.043 + broad * 4.0);
    color = color / (vec3(1.0) + color * 0.22);
    fragColor = vec4(max(color, vec3(0.0)), 0.995 * vColor.a);
}
`;
}

function fshCombat(spec) {
  const P = pascal(spec.family);
  return `${header(spec.family + ' 招式贴片')}
${glslUniforms(spec.uniforms)}
uniform float VeilRenderTime;

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

void main() {
    float progress = clamp(${P}Progress, 0.0, 1.0);
    float intensity = clamp(${P}Intensity, 0.0, 2.0);
    if (progress <= 0.001 || intensity <= 0.001) discard;

    // vUv 已在 vsh 中心化成 -1..1
    float across = abs(vUv.y);
    float along = vUv.x;

    float arc = 1.0 - smoothstep(0.34, 0.88, across);
    float leftEnd = smoothstep(-1.0, -0.72, along);
    float rightEnd = 1.0 - smoothstep(0.72, 1.0, along);
    float ends = leftEnd * rightEnd;
    float fracture = 0.72 + 0.28 * sin((along * 0.5 + 0.5) * 40.0 + VeilRenderTime * 5.0);
    float envelope = smoothstep(0.0, 0.12, progress) * (1.0 - smoothstep(0.82, 1.0, progress));

    float core = exp(-across * across * 38.0);
    float dirBias = 0.92 + 0.08 * sin((${P}DirectionX * along
            + ${P}DirectionZ * across) * 6.0);
    float alpha = arc * ends * fracture * envelope * vColor.a * dirBias
            * (0.32 + core * 0.92);
    if (alpha <= 0.004) discard;

    vec3 tint = ${P}Tint;
    vec3 color = mix(vColor.rgb * 0.62, tint, core * 0.72);
    color += tint * core * 0.65 * intensity;
    fragColor = vec4(color * (0.7 + intensity * 0.5), clamp(alpha, 0.0, 0.92));
}
`;
}

function fshAura(spec) {
  const P = pascal(spec.family);
  return `${header(spec.family + ' 实体挂件')}
${glslUniforms(spec.uniforms)}
uniform float VeilRenderTime;

in vec2 vUv;
in vec4 vColor;
in vec3 vNormal;
in vec3 vViewPos;
out vec4 fragColor;

void main() {
    float alphaScale = clamp(${P}Alpha, 0.0, 1.0);
    if (alphaScale <= 0.001) discard;

    vec3 normal = normalize(vNormal);
    vec3 viewDir = normalize(-vViewPos);
    float facing = clamp(abs(dot(normal, viewDir)), 0.0, 1.0);
    float fresnel = pow(1.0 - facing, 2.6);

    vec2 uv = vUv;
    float broadFlow = 0.5 + 0.5 * sin(uv.y * 16.0 - VeilRenderTime * 1.10
            + sin(uv.x * 10.0 + VeilRenderTime * 0.29));
    float crossFlow = 0.5 + 0.5 * sin((uv.x - uv.y) * 11.0 + VeilRenderTime * 0.47);
    float wisp = smoothstep(0.38, 0.88, broadFlow * 0.68 + crossFlow * 0.32);
    float sparkle = pow(max(0.0, sin(uv.x * 89.0 + uv.y * 37.0 + VeilRenderTime * 1.31)), 24.0)
                  * pow(max(0.0, sin(uv.x * 43.0 - uv.y * 79.0 - VeilRenderTime * 0.97)), 24.0);

    float palettePhase = VeilRenderTime * 0.085 + uv.y * 0.44 + ${P}Palette * 0.137;
    vec3 tint = mix(${P}Tint, vec3(1.0), 0.25);
    vec3 color = tint * (0.55 + wisp * 0.30 + fresnel * 0.35);
    color += vec3(1.0, 0.72, 0.22) * sparkle * 0.42;

    float breath = (0.90 + 0.10 * sin(VeilRenderTime * 1.18 + ${P}Palette * 0.71))
            * (0.85 + 0.15 * clamp(${P}Pulse, 0.0, 1.0));
    float alpha = vColor.a * alphaScale * breath
            * (0.12 + wisp * 0.58 + fresnel * 0.20 + sparkle * 0.16);
    if (alpha < 0.008) discard;
    fragColor = vec4(clamp(color, 0.0, 1.0), clamp(alpha, 0.0, 0.46));
}
`;
}

function fshItemGlow(spec) {
  const P = pascal(spec.family);
  return `${header(spec.family + ' 物品轮廓辉光')}
uniform sampler2D Sampler0;
${glslUniforms(spec.uniforms)}
uniform float VeilRenderTime;

in vec2 vUv;
in vec4 vColor;
in vec3 vNormal;
in vec3 vViewPos;
out vec4 fragColor;

vec3 cycleColors(vec3 a, vec3 b, vec3 c, float phase) {
    float seg = fract(phase) * 3.0;
    if (seg < 1.0) return mix(a, b, smoothstep(0.0, 1.0, seg));
    if (seg < 2.0) return mix(b, c, smoothstep(0.0, 1.0, seg - 1.0));
    return mix(c, a, smoothstep(0.0, 1.0, seg - 2.0));
}

void main() {
    vec4 texel = texture(Sampler0, vUv);
    if (texel.a < 0.035) discard;

    vec2 px = 1.0 / vec2(textureSize(Sampler0, 0));
    float neighbour = min(min(texture(Sampler0, vUv + vec2(px.x, 0.0)).a,
                              texture(Sampler0, vUv - vec2(px.x, 0.0)).a),
                          min(texture(Sampler0, vUv + vec2(0.0, px.y)).a,
                              texture(Sampler0, vUv - vec2(0.0, px.y)).a));
    float edge = smoothstep(0.025, 0.72, max(0.0, texel.a - neighbour));

    float palette = floor(${P}Palette + 0.5);
    float phase = VeilRenderTime * 0.085 + vUv.y * 0.44 + palette * 0.137;
    vec3 aura = cycleColors(${P}Tint, mix(${P}Tint, vec3(1.0), 0.55),
                            mix(${P}Tint, vec3(1.0, 0.55, 0.15), 0.5), phase);

    float fresnel = pow(1.0 - clamp(abs(dot(normalize(vNormal), normalize(vViewPos))), 0.0, 1.0), 2.0);
    float sparkle = pow(max(0.0, sin(vUv.x * 89.0 + vUv.y * 37.0 + VeilRenderTime * 1.31)), 24.0)
                  * pow(max(0.0, sin(vUv.x * 43.0 - vUv.y * 79.0 - VeilRenderTime * 0.97)), 24.0);

    vec3 color = mix(texel.rgb, aura, 0.72) * (0.78 + edge * 0.22 + fresnel * 0.18);
    color += vec3(1.0, 0.72, 0.22) * sparkle * 0.42;

    float glow = clamp(${P}Glow, 0.0, 1.0)
            * (0.85 + 0.15 * clamp(${P}Pulse, 0.0, 1.0));
    float alpha = vColor.a * texel.a * glow
            * (0.10 + edge * 0.62 + fresnel * 0.18 + sparkle * 0.16);
    if (alpha < 0.008) discard;
    fragColor = vec4(clamp(color, 0.0, 1.0), clamp(alpha, 0.0, 0.46));
}
`;
}

function fshWater(spec) {
  const P = pascal(spec.family);
  return `${header(spec.family + ' 水体')}
#veil:buffer veil:camera VeilCamera

${glslUniforms(spec.uniforms)}
uniform float VeilRenderTime;

in vec2 vUv;
in vec4 vColor;
in vec3 vWorldPos;
out vec4 fragColor;

${NOISE_LIB}

void main() {
    vec3 viewDir = normalize(VeilCamera.CameraPosition - vWorldPos);
    vec3 normal = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
    if (dot(normal, viewDir) < 0.0) normal = -normal;

    float facing = clamp(dot(normal, viewDir), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - facing, 5.0);
    float frontFace = gl_FrontFacing ? 1.0 : 0.0;
    float thickness = clamp((1.0 - facing) * 0.72
            + (1.0 - frontFace) * 0.52, 0.0, 1.0) * clamp(${P}Thickness, 0.0, 1.0);

    float flow = fbm(vWorldPos.xz * 0.72
            + vec2(-VeilRenderTime * 1.28 * ${P}Flow, VeilRenderTime * 0.31 * ${P}Flow));
    float crossing = fbm(vWorldPos.zx * 1.14
            + vec2(VeilRenderTime * 0.22, -VeilRenderTime * 0.76));
    float filament = pow(max(0.0, sin(dot(vWorldPos.xz, vec2(4.35, -2.64))
            - VeilRenderTime * 4.15 + flow * 7.4 + crossing * 2.2)), 9.0);

    vec3 shallow = ${P}Tint;
    vec3 deep = ${P}Tint * 0.18;
    vec3 color = mix(shallow, deep, thickness * 0.84);
    color += vec3(0.20, 0.55, 0.72) * fresnel * 0.30;
    color = mix(color, vec3(0.82, 0.96, 0.98), filament * ${P}Foam * 0.68);
    color *= vColor.rgb;

    float alpha = vColor.a * clamp(${P}Alpha, 0.0, 1.0)
            * (0.17 + thickness * 0.28 + fresnel * 0.12 + filament * ${P}Foam * 0.30);
    if (alpha < 0.008) discard;
    fragColor = vec4(color, clamp(alpha, 0.0, 0.86));
}
`;
}

// ---------- vsh ----------
function vshSky(spec) {
  return `#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

out vec3 vDirection;
out vec4 vColor;

void main() {
    // 只用视图矩阵的旋转部分：天空盒永远以相机为中心
    vec3 dir = mat3(VeilCamera.ViewMat) * Position;
    vec4 clip = VeilCamera.ProjMat * vec4(dir, 1.0);
    clip.z = clip.w * 0.9998;      // 钉在远平面，配合 depth_test lequal
    gl_Position = clip;
    vDirection = normalize(Position);
    vColor = Color;
}
`;
}

function vshField(spec) {
  return `#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

out vec2 vUv;
out vec4 vMaterial;
out vec3 vWorldPos;

void main() {
    gl_Position = VeilCamera.ProjMat * VeilCamera.ViewMat
            * vec4(Position - VeilCamera.CameraPosition, 1.0);
    vUv = UV0;
    vMaterial = Color;      // r=强度 g=种类 b=凝结 a=覆盖
    vWorldPos = Position;
}
`;
}

function vshCombat(spec) {
  return `#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in vec4 Color;

out vec2 vUv;
out vec4 vColor;

void main() {
    gl_Position = VeilCamera.ProjMat * VeilCamera.ViewMat
            * vec4(Position - VeilCamera.CameraPosition, 1.0);
    vUv = UV * 2.0 - 1.0;       // 中心化，片元里用对称软遮罩
    vColor = Color;
}
`;
}

function vshAura(spec) {
  return `layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in vec2 UV1;
layout(location = 4) in vec2 UV2;
layout(location = 5) in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
out vec4 vColor;
out vec3 vNormal;
out vec3 vViewPos;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    vViewPos = view.xyz;
    gl_Position = ProjMat * view;
    vUv = UV0;
    vColor = Color;
    vNormal = Normal;
}
`;
}

function vshWater(spec) {
  return `#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

out vec2 vUv;
out vec4 vColor;
out vec3 vWorldPos;

void main() {
    gl_Position = VeilCamera.ProjMat * VeilCamera.ViewMat
            * vec4(Position - VeilCamera.CameraPosition, 1.0);
    vUv = UV0;
    vColor = Color;
    vWorldPos = Position;
}
`;
}

// ---------- JSON ----------
const json = o => JSON.stringify(o, null, 2) + '\n';

function baseLayers(programName, opts = {}) {
  const {
    depth = 'lequal', cull = 'none', depthWrite = false,
    transparency = 'translucent', bloom = false, extra = []
  } = opts;
  const first = [
    { type: 'veil:shader', name: programName },
    ...extra,
    { type: 'minecraft:depth_test', mode: depth },
    { type: 'minecraft:cull', face: cull },
    { type: 'minecraft:write_mask', color: true, depth: depthWrite },
    { type: 'minecraft:transparency', mode: transparency }
  ];
  if (!bloom) return first;
  return [first, [
    { type: 'veil:shader', name: programName },
    { type: 'minecraft:depth_test', mode: depth },
    { type: 'minecraft:cull', face: cull },
    { type: 'minecraft:write_mask', color: true, depth: false },
    { type: 'minecraft:transparency', mode: 'additive' },
    { type: 'minecraft:output', framebuffer: 'veil:bloom' }
  ]];
}

export function buildEffect(input) {
  const spec = normalizeSpec(input);
  const P = pascal(spec.family);
  const nsName = `${spec.modid}:${spec.family}`;
  const files = [];
  const add = (rel, content) => files.push({ path: rel, content });

  const base = `assets/${spec.modid}/pinwheel`;
  const prog = (n) => `${base}/shaders/program/${n}`;
  const uni = glslUniforms(spec.uniforms);

  switch (spec.kind) {
    case 'screen_post': {
      add(`${base}/post/${spec.family}.json`, json({
        renderStage: spec.stage,
        priority: spec.priority,
        stages: [{ type: 'veil:blit', shader: `${nsName}_post`, in: 'minecraft:main', out: 'veil:post', clear: true }]
      }));
      add(prog(`${spec.family}_post.json`), json({ vertex: 'veil:blit_screen', fragment: `${nsName}_post` }));
      add(prog(`${spec.family}_post.fsh`), fshScreenPost(spec));
      break;
    }
    case 'domain_sphere': {
      add(`${base}/post/${spec.family}.json`, json({
        renderStage: spec.stage,
        priority: spec.priority,
        stages: [{ type: 'veil:blit', shader: `${nsName}_post`, in: 'minecraft:main', out: 'veil:post', clear: true }]
      }));
      add(prog(`${spec.family}_post.json`), json({ vertex: 'veil:blit_screen', fragment: `${nsName}_post` }));
      add(prog(`${spec.family}_post.fsh`), fshDomain(spec));
      break;
    }
    case 'volume_field': {
      const fb = `${spec.family}_field`;
      add(`${base}/framebuffers/${fb}.json`, json({
        width: 'q.screen_width', height: 'q.screen_height', autoClear: true,
        color_buffers: [
          { type: 'texture', format: 'RGBA16F', levels: 1, name: `${P}DataSampler` },
          { type: 'texture', format: 'RGBA8', levels: 1, name: `${P}MaterialSampler` }
        ],
        depth: { type: 'texture', format: 'DEPTH_COMPONENT', levels: 1, name: `${P}DepthSampler` }
      }));
      add(`${base}/rendertypes/${fb}.json`, json({
        format: 'POSITION_TEX_COLOR', mode: 'TRIANGLES', bufferSize: 'BIG', sort: true,
        layers: [
          { type: 'veil:shader', name: `${nsName}_field` },
          { type: 'minecraft:depth_test', mode: 'lequal' },
          { type: 'minecraft:cull', face: 'none' },
          { type: 'minecraft:write_mask', color: true, depth: false },
          { type: 'minecraft:transparency', mode: 'translucent' },
          { type: 'minecraft:output', framebuffer: nsName + '_field' }
        ]
      }));
      add(prog(`${spec.family}_field.json`), json({ vertex: `${nsName}_field`, fragment: `${nsName}_field` }));
      add(prog(`${spec.family}_field.vsh`), vshField(spec));
      add(prog(`${spec.family}_field.fsh`), fshField(spec));
      add(prog(`${spec.family}_composite.json`), json({
        vertex: 'veil:blit_screen',
        fragment: `${nsName}_composite`,
        textures: {
          [`${P}DataSampler`]: { type: 'framebuffer', name: nsName + '_field', sampler: 0 },
          [`${P}MaterialSampler`]: { type: 'framebuffer', name: nsName + '_field', sampler: 1 },
          [`${P}DepthSampler`]: { type: 'framebuffer', name: nsName + '_field:depth' }
        }
      }));
      add(prog(`${spec.family}_composite.fsh`), fshComposite(spec));
      add(`${base}/post/${spec.family}.json`, json({
        renderStage: spec.stage === 'after_level' ? 'after_translucent_blocks' : spec.stage,
        priority: spec.priority,
        stages: [{ type: 'veil:blit', shader: `${nsName}_composite`, in: 'minecraft:main', out: 'veil:post', clear: true }]
      }));
      break;
    }
    case 'skybox': {
      add(`${base}/rendertypes/${spec.family}_sky.json`, json({
        format: 'POSITION_COLOR', mode: 'QUADS', bufferSize: 'TRANSIENT', sort: false,
        layers: baseLayers(`${nsName}_sky`)
      }));
      add(prog(`${spec.family}_sky.json`), json({ vertex: `${nsName}_sky`, fragment: `${nsName}_sky` }));
      add(prog(`${spec.family}_sky.vsh`), vshSky(spec));
      add(prog(`${spec.family}_sky.fsh`), fshSky(spec));
      break;
    }
    case 'combat_quad': {
      add(`${base}/rendertypes/${spec.family}.json`, json({
        format: 'POSITION_TEX_COLOR', mode: 'QUADS', bufferSize: 'TRANSIENT', sort: true,
        layers: baseLayers(nsName, { bloom: spec.bloom })
      }));
      add(prog(`${spec.family}.json`), json({ vertex: nsName, fragment: nsName }));
      add(prog(`${spec.family}.vsh`), vshCombat(spec));
      add(prog(`${spec.family}.fsh`), fshCombat(spec));
      break;
    }
    case 'entity_aura': {
      add(`${base}/rendertypes/${spec.family}.json`, json({
        format: 'NEW_ENTITY', mode: 'QUADS', bufferSize: 'TRANSIENT', sort: true,
        layers: baseLayers(nsName, {
          bloom: spec.bloom,
          extra: [{ type: 'minecraft:lightmap' }, { type: 'minecraft:overlay' }]
        })
      }));
      add(prog(`${spec.family}.json`), json({ vertex: nsName, fragment: nsName }));
      add(prog(`${spec.family}.vsh`), vshAura(spec));
      add(prog(`${spec.family}.fsh`), fshAura(spec));
      break;
    }
    case 'item_glow': {
      add(`${base}/rendertypes/${spec.family}.json`, json({
        format: 'NEW_ENTITY', mode: 'QUADS', bufferSize: 'TRANSIENT', sort: true,
        layers: baseLayers(nsName, {
          bloom: spec.bloom,
          extra: [
            { type: 'minecraft:lightmap' },
            { type: 'minecraft:overlay' }
          ]
        })
      }));
      add(prog(`${spec.family}.json`), json({
        vertex: nsName, fragment: nsName,
        textures: { Sampler0: 'minecraft:textures/atlas/blocks.png' }
      }));
      add(prog(`${spec.family}.vsh`), vshAura(spec));
      add(prog(`${spec.family}.fsh`), fshItemGlow(spec));
      break;
    }
    case 'water_body': {
      add(`${base}/rendertypes/${spec.family}.json`, json({
        format: 'POSITION_TEX_COLOR', mode: 'TRIANGLES', bufferSize: 'BIG', sort: true,
        layers: baseLayers(nsName)
      }));
      add(prog(`${spec.family}.json`), json({ vertex: nsName, fragment: nsName }));
      add(prog(`${spec.family}.vsh`), vshWater(spec));
      add(prog(`${spec.family}.fsh`), fshWater(spec));
      break;
    }
    case 'model_pbr': {
      throw new Error('model_pbr 需要现成模型与贴图，不在脚手架内；请用 glTF/OBJ + PBR 三件套，或参考 sword_jiaolong_material。');
    }
    default:
      throw new Error('未实现的 kind：' + spec.kind);
  }

  // 4.5.2 的 rendertype codec 对 affectsCrumbling / outline 有默认值，显式写出来便于对照
  for (const f of files) {
    if (!f.path.includes('/rendertypes/') || !f.path.endsWith('.json')) continue;
    const o = JSON.parse(f.content);
    if (o.affectsCrumbling === undefined) o.affectsCrumbling = false;
    if (o.outline === undefined) o.outline = false;
    f.content = json(o);
  }

  return { spec, files, prefix: P, ns: nsName };
}
