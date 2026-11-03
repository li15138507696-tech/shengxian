import { KINDS, normalizeSpec } from './templates.mjs';
import { FAMILIES } from '../data/families.mjs';

const KIND_HINTS = [
  { kind: 'screen_post', words: ['屏幕', '全屏', '灾劫', '天变', '滤镜', '泛红', '血色', '暗角', '压迫', '雾霾', '色调', '失血', '炼蛊', '转场'], w: 3 },
  { kind: 'domain_sphere', words: ['领域', '球域', '范围', '场内', '穹顶', '结界内部'], w: 4 },
  { kind: 'volume_field', words: ['体积', '热浪', '毒雾', '雾墙', '水墙', '结界', '折射', '气体', '烟', '浪'], w: 3 },
  { kind: 'skybox', words: ['天空', '天象', '星空', '云海', '位面', '九重天', '星辰', '日月'], w: 4 },
  { kind: 'combat_quad', words: ['斩', '剑气', '刀光', '刃', '冲击', '爆炸', '雷', '雨', '针', '环', '眼', '印', '拳'], w: 3 },
  { kind: 'entity_aura', words: ['光环', '气场', '饰', '翅膀', '衣', '护体', '缠绕', '显形', '残影'], w: 3 },
  { kind: 'item_glow', words: ['物品', '图标', '蛊虫', '法宝', '掉落物', '发光'], w: 3 },
  { kind: 'water_body', words: ['水', '河', '海', '波', '水牢', '水衣', '潮'], w: 2 }
];

const COLOR_HINTS = [
  { words: ['血', '红', '猩', '朱'], primary: '#6e0d16', secondary: '#c1121f', accent: '#ff6b5e' },
  { words: ['青', '玉', '翠'], primary: '#0f5a4e', secondary: '#2fd0b0', accent: '#d8fff4' },
  { words: ['金', '黄', '宝'], primary: '#6b4a10', secondary: '#e0a92e', accent: '#ffe6a3' },
  { words: ['紫', '幽', '冥'], primary: '#2a1450', secondary: '#7a4bd0', accent: '#d9c2ff' },
  { words: ['雷', '电', '蓝'], primary: '#0d2a5a', secondary: '#2f7bff', accent: '#bfe4ff' },
  { words: ['墨', '黑', '暗'], primary: '#0a0d12', secondary: '#2b3440', accent: '#9fb4c8' },
  { words: ['白', '冰', '霜'], primary: '#1a2a38', secondary: '#8fd8ff', accent: '#ffffff' }
];

const NAME_HINTS = [
  ['血', 'blood'], ['红', 'blood'], ['火', 'flame'], ['焰', 'flame'], ['雷', 'thunder'],
  ['电', 'thunder'], ['冰', 'frost'], ['霜', 'frost'], ['风', 'wind'], ['水', 'water'],
  ['海', 'ocean'], ['河', 'river'], ['剑', 'sword'], ['刀', 'blade'], ['影', 'shadow'],
  ['星', 'star'], ['毒', 'poison'], ['玉', 'jade'], ['金', 'gold'], ['木', 'wood'],
  ['土', 'earth'], ['领域', 'domain'], ['天', 'sky'], ['雾', 'mist'], ['眼', 'eye'],
  ['斩', 'slash'], ['环', 'ring'], ['蚀', 'corrosive'], ['墨', 'ink'], ['莲', 'lotus'],
  ['时间', 'time'], ['回溯', 'rewind'], ['炼', 'refine'], ['气', 'qi'], ['光', 'light'],
  ['魂', 'soul'], ['梦', 'dream'], ['虚空', 'void'], ['月', 'moon'], ['日', 'sun']
];

function pickKind(goal) {
  const text = String(goal || '');
  let best = { kind: 'screen_post', score: 0 };
  for (const h of KIND_HINTS) {
    let score = 0;
    for (const w of h.words) if (text.includes(w)) score += h.w;
    if (score > best.score) best = { kind: h.kind, score };
  }
  return best;
}

function pickColors(goal, kind) {
  for (const h of COLOR_HINTS) {
    if (h.words.some(w => String(goal).includes(w))) {
      return { primary: h.primary, secondary: h.secondary, accent: h.accent };
    }
  }
  return undefined;
}

function suggestName(goal, kind) {
  const parts = [];
  for (const [cn, en] of NAME_HINTS) {
    if (String(goal).includes(cn) && !parts.includes(en)) parts.push(en);
    if (parts.length >= 2) break;
  }
  const suffix = {
    screen_post: 'domain', domain_sphere: 'field', volume_field: 'field',
    skybox: 'sky', combat_quad: 'strike', entity_aura: 'aura',
    item_glow: 'glow', water_body: 'water'
  }[kind] || 'fx';
  if (!parts.length) parts.push('fx');
  let name = parts.join('_') + '_' + suffix;
  if (name.length > 40) name = name.slice(0, 40);
  return name;
}

function nearestFamily(kind, goal) {
  const pool = FAMILIES.filter(f => f.kind === kind);
  const text = String(goal || '');
  let best = null;
  let bestScore = -1;
  for (const f of pool) {
    let s = 0;
    for (const ch of f.title) if (text.includes(ch)) s += 0.2;
    for (const m of f.members) if (text.toLowerCase().includes(m.split('_')[0])) s += 1;
    if (s > bestScore) { bestScore = s; best = f; }
  }
  return best || pool[0] || FAMILIES[0];
}

export function planEffect({ goal, kind, name, modid = 'shengxian', target = 'veil' }) {
  const picked = kind && KINDS[kind] ? { kind, score: 99 } : pickKind(goal);
  const chosen = picked.kind;
  const specName = (name && /^[a-z][a-z0-9_]{1,40}$/.test(name)) ? name : suggestName(goal, chosen);
  const family = nearestFamily(chosen, goal);
  const colors = pickColors(goal, chosen) || undefined;
  const meta = KINDS[chosen];

  const risks = [];
  if (meta.requires === 'veil') risks.push('需要 Veil 依赖；无光子/无 Veil 的发布形态要先决定是否引 foundry.veil。');
  if (chosen === 'volume_field') risks.push('体积场是两趟 + 一个 FBO，移动端/低配机要先测 overdraw；强度 0 必须早退。');
  if (chosen === 'screen_post' || chosen === 'domain_sphere') risks.push('全屏 post 每帧都跑；用 Strength/Active 做 0 早退，不要动态删管线。');
  if (chosen === 'entity_aura' || chosen === 'item_glow') risks.push('NEW_ENTITY 顶点要带 light/overlay，否则会全黑或全亮。');
  if (chosen === 'skybox') risks.push('天空盒必须用 mat3(ViewMat) + clip.z=clip.w*0.9998，否则远离原点会穿插。');

  const javaSteps = [
    `资源写到 assets/${modid}/pinwheel/{${meta.pieces.join(',')}}`,
    chosen === 'screen_post' || chosen === 'domain_sphere'
      ? '客户端初始化时调用 register()，用 preVeilPostProcessing 推 uniform'
      : '每帧先 pushUniforms()，再提交几何（RenderType 每帧取）',
    chosen === 'volume_field' ? '场几何用 <family>_field rendertype 提交，composite 由 post 自动跑' : '几何用世界坐标提交，vsh 里减 CameraPosition',
    'F3+T 热重载；先把 Strength 设 0 确认零开销，再拉满看层次',
    '最后才调颜色：主题色只走 uniform，不写进 GLSL'
  ];

  return {
    goal: goal || '',
    kind: chosen,
    kindTitle: meta.title,
    why: picked.score > 0
      ? `命中了 ${chosen} 的关键词（score=${picked.score}）`
      : '没有明显关键词，默认按屏幕后处理起步',
    name: specName,
    modid,
    target,
    colors,
    pieces: meta.pieces,
    description: meta.desc,
    reference: {
      family: family.id,
      title: family.title,
      members: family.members.slice(0, 6),
      mechanism: family.mechanism,
      reuse: family.reuse
    },
    uniformsPreview: normalizeSpec({ name: specName, kind: chosen, modid, colors }).uniforms,
    javaSteps,
    risks,
    checks: [
      'fx_validate 必须 0 error',
      'fsh 有 discard/return 早退',
      '采样深度时有 0.0008 守卫',
      'post JSON 里没有 uniform（值全部由 Java 推）',
      'rendertype/program/post 名字同族'
    ],
    next: `调用 fx_create，参数：{ name: "${specName}", kind: "${chosen}", modid: "${modid}" }`
  };
}
