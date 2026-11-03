import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { FAMILIES, FAMILY_BY_ID } from '../data/families.mjs';
import { RECIPES, RECIPE_BY_ID } from '../data/recipes.mjs';
import { API, API_BY_ID } from '../data/api.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
export const MCP_DIR = path.resolve(__dirname, '..');
export const ROOT = process.env.JIYE_FX_ROOT
  ? path.resolve(process.env.JIYE_FX_ROOT)
  : path.resolve(MCP_DIR, '..', '..');

const IDX_CANDIDATES = [
  path.join(ROOT, '_tmp_reverse', 'jiye_fx_index.json'),
  path.join(MCP_DIR, 'data', 'index.json')
];

function loadIndex() {
  for (const p of IDX_CANDIDATES) {
    if (fs.existsSync(p)) {
      try {
        return { index: JSON.parse(fs.readFileSync(p, 'utf8')), source: p };
      } catch (e) {
        /* try next */
      }
    }
  }
  return {
    index: { programs: [], rendertypes: [], posts: [], framebuffers: [], core: [], includes: [], stats: {}, errors: [] },
    source: null
  };
}

const { index, source: INDEX_SOURCE } = loadIndex();

export const PROGRAMS = new Map((index.programs || []).map(p => [p.name, p]));
export const RENDER_TYPES = new Map((index.rendertypes || []).map(r => [r.name, r]));
export const POSTS = new Map((index.posts || []).map(p => [p.name, p]));
export const FRAMEBUFFERS = new Map((index.framebuffers || []).map(f => [f.name, f]));
export const STATS = index.stats || {};
export const SHARED_FSH = new Map((index.shared_fsh || []).map(x => [x.name, x.info]));
export const SHARED_VSH = new Map((index.shared_vsh || []).map(x => [x.name, x.info]));

// 反向索引：program -> rendertype / post / framebuffer
const RT_BY_PROGRAM = new Map();
for (const r of RENDER_TYPES.values()) {
  for (const layer of flatten(r.json?.layers)) {
    if (layer.type === 'veil:shader') {
      const n = stripNs(layer.name);
      if (!RT_BY_PROGRAM.has(n)) RT_BY_PROGRAM.set(n, []);
      RT_BY_PROGRAM.get(n).push(r.name);
    }
  }
}
const POST_BY_PROGRAM = new Map();
for (const p of POSTS.values()) {
  for (const st of p.json?.stages || []) {
    if (!st.shader) continue;
    const n = stripNs(st.shader);
    if (!POST_BY_PROGRAM.has(n)) POST_BY_PROGRAM.set(n, []);
    POST_BY_PROGRAM.get(n).push(p.name);
  }
}
const FBO_BY_PROGRAM = new Map();
for (const p of PROGRAMS.values()) {
  const fbs = [];
  for (const [sampler, spec] of Object.entries(p.textures || {})) {
    if (spec && spec.type === 'framebuffer') {
      const seg = String(spec.name).split(':');
      fbs.push({ sampler, framebuffer: seg[1] || seg[0], depth: seg[2] === 'depth' });
    }
  }
  if (fbs.length) FBO_BY_PROGRAM.set(p.name, fbs);
}

export function stripNs(s) {
  return String(s || '').split(':').pop();
}

export function flatten(layers) {
  const out = [];
  for (const x of layers || []) {
    if (Array.isArray(x)) out.push(...x);
    else if (x && typeof x === 'object') out.push(x);
  }
  return out;
}

export function passCount(layers) {
  let n = 0;
  for (const x of layers || []) {
    if (Array.isArray(x)) n += 1;
    else if (x && typeof x === 'object') return 1;
  }
  return Math.max(1, n);
}

// ---------- 参数字典 ----------
const UNIFORM_DOCS = {
  'VeilRenderTime': ['客户端秒计时（每小时回绕）', '秒'],
  'DiffuseSampler0': ['输入画面颜色', 'sampler'],
  'DiffuseDepthSampler': ['输入画面深度', 'sampler'],
  'Sampler0': ['纹理 0（实体/物品常用 blocks atlas）', 'sampler'],
  'ScreenSize': ['屏幕像素尺寸', 'px'],
  'uMode': ['形态/模式枚举（整数）', '0..N'],
  'uProgress': ['阶段进度', '0..1'],
  'uIntensity': ['总强度；部分 shader 兼作覆盖率', '0..1'],
  'uTurbulence': ['湍流/撕碎程度', '0..1'],
  'uSeed': ['随机种子', '任意'],
  'uGrade': ['品阶/等级，微调频率与细节', '0..3'],
  'uPathColor': ['主题色', 'vec3 0..1'],
  'Strength': ['总强度', '0..1'],
  'Phase': ['相位（通常 = t * 速度）', '0..20'],
  'Progress': ['玩法进度', '0..1'],
  'FogDensity': ['远景雾密度', '0..0.28'],
  'Disturbance': ['扰动强度', '0..1'],
  'Flash': ['闪白强度', '0..1'],
  'Recovery': ['恢复比例（mix 回原图）', '0..1'],
  'DirectionX': ['方向 X 分量', '-1..1'],
  'DirectionZ': ['方向 Z 分量', '-1..1'],
  'Turbulence': ['湍流', '0..1'],
  'Radius': ['半径（世界格）', '1..128'],
  'Center': ['世界中心', 'vec3'],
  'Active': ['开关（0/1）', '0..1'],
  'Tint': ['主题色（乘色）', 'vec3'],
  'Palette': ['调色板索引', '0..9'],
  'Pulse': ['脉动强度', '0..1'],
  'Glow': ['辉光强度', '0..1'],
  'Thickness': ['厚度/吸收系数', '0..1'],
  'Foam': ['泡沫量', '0..1'],
  'Flow': ['流速', '0..2'],
  'Intensity': ['强度', '0..2'],
  'BaseColorSampler': ['反照率贴图', 'sampler'],
  'NormalSampler': ['切线空间法线贴图', 'sampler'],
  'MetallicRoughnessSampler': ['G=粗糙度，B=金属度', 'sampler'],
  'AoSampler': ['AO（R 通道）', 'sampler'],
  'EmissiveSampler': ['自发光遮罩', 'sampler'],
  'DisplayLight': ['显示亮度（0.38..1）', '0..1'],
  'HurtAmount': ['受击红闪', '0..1'],
  'GlobalAlpha': ['整体透明度', '0..1'],
  'PulsePhase': ['脉冲相位', '0..1'],
  'EdgeIntensity': ['边缘强度', '0..1'],
  'ShatterHeat': ['碎裂热', '0..1']
};

export function describeUniform(name) {
  if (UNIFORM_DOCS[name]) return UNIFORM_DOCS[name];
  // 家族前缀（PascalCase）推断
  const m = /^([A-Z][a-z]+(?:[A-Z][a-z]+)*?)(Strength|Phase|Progress|Fog|Flash|Recovery|Direction|Turbulence|Seed|Tint)$/.exec(name);
  if (m) {
    const tail = UNIFORM_DOCS[m[2]] || [m[2], ''];
    return [`${m[1]} 家族的${tail[0]}`, tail[1]];
  }
  return ['自定义参数（见所属家族）', ''];
}

// ---------- 链路 ----------
export function chain(nameRaw) {
  const name = stripNs(nameRaw);
  const program = PROGRAMS.get(name);
  if (!program) return null;
  // 别名 program（如 di_xian_vine_surface）自己没有 fsh，回溯到 fragment 指向的 program
  const fragName = stripNs(program.fragment);
  const fragProg = PROGRAMS.get(fragName);
  const fshInfo = program.fsh || fragProg?.fsh || SHARED_FSH.get(fragName) || null;
  const vshName = stripNs(program.vertex);
  const vshProg = PROGRAMS.get(vshName);
  const vshInfo = program.vsh || vshProg?.vsh || SHARED_VSH.get(vshName) || null;
  const rts = (RT_BY_PROGRAM.get(name) || []).map(n => RENDER_TYPES.get(n)).filter(Boolean);
  const posts = (POST_BY_PROGRAM.get(name) || []).map(n => POSTS.get(n)).filter(Boolean);
  const fbs = (FBO_BY_PROGRAM.get(name) || []).map(x => ({
    ...x,
    spec: FRAMEBUFFERS.get(x.framebuffer) || null
  }));
  const uniforms = (fshInfo?.uniforms || []).map(([n, t]) => ({
    name: n, type: t, doc: describeUniform(n)[0], unit: describeUniform(n)[1]
  }));
  const fam = FAMILIES.find(f => f.members.includes(name)) || null;
  return {
    name,
    family: fam ? { id: fam.id, title: fam.title, kind: fam.kind } : null,
    summary: fam ? fam.mechanism : '',
    aliasOf: fragName !== name ? fragName : null,
    shaders: {
      vertex: program.vertex,
      fragment: program.fragment,
      vertexFile: vshInfo ? `${vshName}.vsh` : null,
      fragmentFile: fshInfo ? `${fragName}.fsh` : null,
      lines: fshInfo?.lines ?? null,
      tags: fshInfo?.tags || [],
      varings: (vshInfo?.outs || []).map(x => x[1] || x[0])
    },
    rendertypes: rts.map(r => ({
      name: r.name,
      format: r.json.format,
      mode: r.json.mode,
      bufferSize: r.json.bufferSize,
      sort: !!r.json.sort,
      passes: passCount(r.json.layers),
      layers: r.json.layers
    })),
    posts: posts.map(p => ({
      name: p.name,
      renderStage: p.json.renderStage || null,
      priority: p.json.priority ?? null,
      stages: p.json.stages || []
    })),
    framebuffers: fbs,
    textures: (program.textures && Object.keys(program.textures).length) ? program.textures : (fragProg?.textures || {}),
    uniforms,
    companionPrograms: rts.length > 1 ? rts.map(r => r.name).filter(n => n !== name) : [],
    callPath: buildCallPath(fshInfo || program, rts, posts, fbs),
    notes: fam ? [fam.reuse] : []
  };
}

function buildCallPath(program, rts, posts, fbs) {
  const steps = [];
  if (rts.length) {
    steps.push({
      n: 1,
      what: '提交几何',
      how: `RenderType rt = VeilRenderType.get(ResourceLocation.fromNamespaceAndPath(MODID, "${rts[0].name}"));`
    });
    if (rts[0].json.layers && passCount(rts[0].json.layers) > 1) {
      steps.push({ n: 2, what: '第二趟自动进 bloom', how: 'rendertype 的第二层带 output=veil:bloom；不需要额外代码' });
    }
  }
  if (program.fsh?.uniforms?.length) {
    steps.push({
      n: steps.length + 1,
      what: '每帧推 uniform',
      how: `ShaderProgram p = VeilRenderSystem.renderer().getShaderManager().getShader(id);\nShaderUniformAccess u = p.getUniform("${program.fsh.uniforms[0][0]}");\nif (u != null) u.${uniformSetter(program.fsh.uniforms[0][1])};`
    });
  }
  if (posts.length) {
    steps.push({
      n: steps.length + 1,
      what: '后处理自动执行',
      how: `post JSON 声明 renderStage=${posts[0].json.renderStage || 'after_level'}；Java 只在 preVeilPostProcessing 里推 uniform`
    });
  }
  if (fbs.length) {
    steps.push({
      n: steps.length + 1,
      what: '场先写 FBO',
      how: `${fbs.map(f => f.framebuffer + (f.depth ? ':depth' : '')).join(', ')} 由 field 趟写；composite 趟再采样`
    });
  }
  return steps;
}

function uniformSetter(type) {
  if (type === 'int') return 'setInt(value)';
  if (type === 'vec3') return 'setVector(x, y, z)';
  if (type === 'vec4') return 'setVector(x, y, z, w)';
  return 'setFloat(value)';
}

// ---------- 检索 ----------
export function search(query, limit = 20) {
  const q = String(query || '').toLowerCase().trim();
  if (!q) return [];
  const hits = [];
  for (const fam of FAMILIES) {
    let score = 0;
    if (fam.id.includes(q) || fam.title.toLowerCase().includes(q)) score += 5;
    if (fam.mechanism.toLowerCase().includes(q)) score += 2;
    if (fam.uniforms.some(u => u.toLowerCase().includes(q))) score += 2;
    if (fam.members.some(m => m.includes(q))) score += 3;
    if (score) hits.push({ kind: 'family', id: fam.id, title: fam.title, kindOfEffect: fam.kind, score, members: fam.members.filter(m => m.includes(q)).slice(0, 6) });
  }
  for (const p of PROGRAMS.values()) {
    let score = 0;
    if (p.name.toLowerCase().includes(q)) score += 4;
    if ((p.fsh?.tags || []).some(t => t.includes(q))) score += 2;
    if ((p.fsh?.uniforms || []).some(([n]) => n.toLowerCase().includes(q))) score += 1;
    if (score) hits.push({ kind: 'program', id: p.name, title: p.name, tags: p.fsh?.tags || [], score });
  }
  return hits.sort((a, b) => b.score - a.score).slice(0, limit);
}

// ---------- 家族与覆盖 ----------
export function familyList() {
  return FAMILIES.map(f => ({
    id: f.id,
    title: f.title,
    kind: f.kind,
    memberCount: f.members.length,
    members: f.members,
    role: f.role
  }));
}

export function familyCoverage() {
  const covered = new Set();
  const missing = [];
  for (const f of FAMILIES) {
    for (const m of f.members) {
      if (!PROGRAMS.has(m)) missing.push({ family: f.id, member: m });
      else covered.add(m);
    }
  }
  const uncovered = [...PROGRAMS.keys()].filter(n => !covered.has(n));
  return { familyCount: FAMILIES.length, missing, uncovered };
}

// ---------- 资源 ----------
export function readResource(kind) {
  const map = {
    skill: ['几页升先/几页特效规范-SKILL.md', 'data/SKILL.md'],
    table: ['几页升先/几页特效全表.md', 'data/table.md']
  };
  const [live, bundled] = map[kind] || [];
  for (const rel of [live, bundled]) {
    if (!rel) continue;
    const p = path.join(ROOT, rel);
    if (fs.existsSync(p)) return { path: p, text: fs.readFileSync(p, 'utf8') };
    const q = path.join(MCP_DIR, rel);
    if (fs.existsSync(q)) return { path: q, text: fs.readFileSync(q, 'utf8') };
  }
  return null;
}

export function stats() {
  const cov = familyCoverage();
  return {
    root: ROOT,
    indexSource: INDEX_SOURCE,
    counts: STATS,
    families: FAMILIES.length,
    recipes: RECIPES.length,
    apiEntries: API.length,
    coverage: { missing: cov.missing, uncovered: cov.uncovered }
  };
}

export { FAMILIES, FAMILY_BY_ID, RECIPES, RECIPE_BY_ID, API, API_BY_ID };
