#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';

import {
  ROOT, FAMILIES, RECIPES, API, STATS,
  chain, search, familyList, stats, readResource, describeUniform,
  PROGRAMS, RENDER_TYPES, POSTS, FRAMEBUFFERS
} from './lib/knowledge.mjs';
import { buildEffect, normalizeSpec, pascal, KINDS } from './lib/templates.mjs';
import { validateFiles } from './lib/validate.mjs';
import { buildJava, buildJavaReadme } from './lib/java.mjs';
import { planEffect } from './lib/plan.mjs';
import { MECHANISM } from './data/mechanism.mjs';
import { SCHEMA, SCHEMA_BY_ID, SCHEMA_META } from './data/schema.mjs';
import { SPACE_CONVENTIONS, SPACE_SHAPES, SPACE_JAVA, SPACE_BACKEND } from './data/space.mjs';
import { SPACE_FORMULAS, SPACE_EFFECTS } from './data/space_effects.mjs';
import { readAsset, copyAsset, assetCatalog, compileStatus, COMPILE } from './data/jiye.mjs';

const server = new McpServer(
  { name: 'jiye-fx', version: '1.2.0' },
  {
    instructions: `几页特效工坊：把「想做什么效果」变成可运行的 Veil 特效包（JSON + GLSL + Java 胶水）。
推荐流程：fx_plan（出方案）→ fx_create（生成到 特效工坊/<name>）→ fx_validate（0 error）→ 按 README 接进客户端。
所有生成物遵循几页 469 个资产实测出的规则：早退 discard、深度守卫 0.0008、准星安全区、主题色只走 uniform、post 管线层不写 uniform（静态值放 stage.uniforms）。资源格式以 fx_schema 为准（Veil 4.5.2 实 jar）。`
  }
);

const text = (s) => ({ content: [{ type: 'text', text: typeof s === 'string' ? s : JSON.stringify(s, null, 2) }] });
const fail = (msg) => ({ isError: true, content: [{ type: 'text', text: '错误：' + msg }] });

function safeOutDir(outDir, name) {
  const def = path.join(ROOT, '特效工坊', name);
  const dir = outDir ? (path.isAbsolute(outDir) ? path.resolve(outDir) : path.resolve(ROOT, outDir)) : def;
  const rel = path.relative(ROOT, dir);
  if (rel.startsWith('..') || path.isAbsolute(rel)) {
    throw new Error('outDir 必须在工程目录内（' + ROOT + '），否则拒绝写入');
  }
  return { dir, def };
}

function writeFiles(dir, files) {
  const written = [];
  for (const f of files) {
    const p = path.join(dir, f.path);
    fs.mkdirSync(path.dirname(p), { recursive: true });
    fs.writeFileSync(p, f.content, 'utf8');
    written.push(path.relative(dir, p).split(path.sep).join('/'));
  }
  return written;
}

// ---------------------------------------------------------------- tools

/** 猜资产类型：按名字在几页目录里找。 */
function guessType(name) {
  for (const t of ['program', 'rendertype', 'post', 'framebuffer', 'include']) {
    if (readAsset(t, name)) {
      return t;
    }
  }
  return 'program';
}

/** show/copy 之后怎么用（人话提示）。 */
function usageOf(type) {
  if (type === 'program') {
    return [
      'program 不会自己跑：用 rendertype 提交几何，或放进 post 管线当全屏效果。',
      'Java：VeilRenderType.get(id) 取层链；VeilRenderSystem.setShader(id) 取 program；',
      'uniform 用 program.getUniform("X").isValid() 判断后 setFloat / setVector。'
    ];
  }
  if (type === 'rendertype') {
    return ['rendertype 是层链（格式/图元/混合/深度）：VeilRenderType.get(id) -> Tesselator 按 rt.mode()/rt.format() 建 MeshData -> rt.draw(mesh)。'];
  }
  if (type === 'post') {
    return ['post 管线必须 PostProcessingManager.add(priority, id) 才会执行（renderStage 只是过滤条件）。'];
  }
  if (type === 'framebuffer') {
    return ['framebuffer 只在 post 管线内可见（写在 post 的 framebuffers 字典里）。'];
  }
  return ['include 用 #include <modid>:<name> 引用。'];
}


server.registerTool('fx_stats', {
  title: '工坊状态',
  description: '查看索引来源、469 文件计数、家族/配方/API 数量，以及家族覆盖缺口（应为 0）。',
  inputSchema: {}
}, async () => text(stats()));

server.registerTool('fx_families', {
  title: '列出特效家族',
  description: '列出几页的 43 个特效家族（可按 kind 过滤）：成员 program、角色、可复用点。用它找“同类效果是怎么做的”。',
  inputSchema: {
    kind: z.enum(Object.keys(KINDS)).optional().describe('按效果类型过滤'),
    query: z.string().optional().describe('按名称/角色模糊过滤')
  }
}, async ({ kind, query }) => {
  let list = familyList();
  if (kind) list = list.filter(f => f.kind === kind);
  if (query) {
    const q = query.toLowerCase();
    list = list.filter(f => f.id.includes(q) || f.title.includes(query) || f.role.includes(query));
  }
  return text({ count: list.length, families: list });
});

server.registerTool('fx_chain', {
  title: '查一个特效的完整链路',
  description: '给 program 名，返回：rendertype 层链（含多趟）、program 绑定、vsh/fsh、uniform、post stage、FBO，以及调用路径（先推 uniform 还是先提交几何）。这是“每个特效怎么被调用”的入口。',
  inputSchema: { name: z.string().describe('program 名，如 fan_wind_composite / di_xian_post') }
}, async ({ name }) => {
  const c = chain(name);
  if (!c) {
    const near = search(name, 5);
    return fail(`没有找到 program ${name}。相近：` + near.map(n => n.id).join(', '));
  }
  return text(c);
});

server.registerTool('fx_search', {
  title: '搜特效/技法/参数',
  description: '按关键词搜家族、program、技法标签、uniform（如 depth-soft、bloom、sky、DiXianStrength）。',
  inputSchema: {
    query: z.string(),
    limit: z.number().int().min(1).max(50).optional()
  }
}, async ({ query, limit }) => text({ query, hits: search(query, limit || 20) }));

server.registerTool('fx_api', {
  title: 'API 调用速查',
  description: 'Veil / 光子 / 原版回退的调用方式（已核对签名）：不传 id 列全部；传 id 返回代码与坑。',
  inputSchema: { id: z.string().optional() }
}, async ({ id }) => {
  if (!id) return text({ entries: API.map(a => ({ id: a.id, title: a.title, verified: a.verified })) });
  const a = API.find(x => x.id === id);
  if (!a) return fail('未知 API id：' + id + '；可选 ' + API.map(x => x.id).join(', '));
  return text(a);
});

server.registerTool('fx_space', {
  title: '空间效果（世界几何）目录',
  description: '几页 94 个 rendertype 的真实空间分类：4 种坐标约定（世界/相机相对/PoseStack/远平面）+ 10 种几何形态（球壳/体积盒/贴片/丝带/天穹/地面环/实体挂件/水体/雾团/模型）+ 提交骨架 + 6 条万能公式 + 15 张逐效果卡片。★先查 backend-screen / backend-geometry 决定「全屏还是几何」——判据是「观众在哪」，不是「哪个更高级」。',
  inputSchema: {
    id: z.string().optional().describe('形态 id / 约定 id / 骨架 id / 公式 id / 卡片 id，如 sphere-shell、world、ribbon、backend-screen'),
    kind: z.enum(['conventions', 'shapes', 'formulas', 'effects', 'java', 'backend']).optional()
  }
}, async ({ id, kind }) => {
  if (!id) {
    return text({
      note: '★先定后端（backend）：全屏还是几何，判据是「观众在哪」。94 个 rendertype 里只有 32 个 post，其余是世界几何。',
      backend: SPACE_BACKEND.map(b => ({ id: b.id, title: b.title, when: b.when })),
      conventions: SPACE_CONVENTIONS.map(c => ({ id: c.id, title: c.title, count: c.count })),
      shapes: SPACE_SHAPES.map(s => ({ id: s.id, title: s.title, coords: s.coords, format: s.format })),
      formulas: SPACE_FORMULAS.map(f => ({ id: f.id, title: f.title, code: f.code.split(String.fromCharCode(10))[0] })),
      effects: SPACE_EFFECTS.map(e => ({ id: e.id, title: e.title, programs: e.programs })),
      java: SPACE_JAVA.map(j => ({ id: j.id, title: j.title }))
    });
  }
  const backend = SPACE_BACKEND.find(b => b.id === id);
  if (backend) return text(backend);
  const conv = SPACE_CONVENTIONS.find(c => c.id === id);
  if (conv) return text(conv);
  const shape = SPACE_SHAPES.find(s => s.id === id);
  if (shape) return text(shape);
  const formula = SPACE_FORMULAS.find(f => f.id === id);
  if (formula) return text(formula);
  const effect = SPACE_EFFECTS.find(e => e.id === id);
  if (effect) return text(effect);
  const java = SPACE_JAVA.find(j => j.id === id);
  if (java) return text(java);
  if (kind === 'shapes') return text(SPACE_SHAPES.filter(s => s.id.includes(id)));
  if (kind === 'effects') return text(SPACE_EFFECTS.filter(e => e.id.includes(id)));
  if (kind === 'backend') return text(SPACE_BACKEND.filter(b => b.id.includes(id)));
  return fail('未知空间条目：' + id + '；可选 ' + [
    ...SPACE_BACKEND.map(b => b.id),
    ...SPACE_CONVENTIONS.map(c => c.id), ...SPACE_SHAPES.map(s => s.id),
    ...SPACE_FORMULAS.map(f => f.id), ...SPACE_EFFECTS.map(e => e.id), ...SPACE_JAVA.map(j => j.id)
  ].join(', '));
});

server.registerTool('fx_jiye', {
  title: '几页特效资产库（查源码 / 拷进工程）',
  description: '几页 guzrlevelupdemoV5.4 的 253 个 pinwheel 资产（122 program / 94 rendertype / 32 post / 4 framebuffer / 1 include）。'
    + 'action=list 列目录；action=show 拿**完整源码**（JSON + vsh + fsh + include + 依赖清单）；'
    + 'action=find 按关键词找最接近的现成效果；action=copy 把资产连同依赖闭包拷进升仙工程'
    + '（命名空间 guzrlevelup->shengxian、可改名、贴图跟随、默认不覆盖已有文件）。'
    + '★要做新效果但一时写不出来：先 show 一个同款当基底，再 fx_java 改 uniform。',
  inputSchema: {
    action: z.enum(['list', 'show', 'find', 'copy']).optional().describe('默认 list'),
    type: z.enum(['program', 'rendertype', 'post', 'framebuffer', 'include', 'all']).optional(),
    name: z.string().optional().describe('资产名（不含命名空间），如 iron_wall_bell'),
    as: z.string().optional().describe('copy 时的目标新名字'),
    modid: z.string().optional().describe('目标命名空间，默认 shengxian'),
    keyword: z.string().optional().describe('find 的关键词（中英文皆可）'),
    overwrite: z.boolean().optional().describe('copy 时允许覆盖已有同名文件'),
    dryRun: z.boolean().optional().describe('copy 时只报告不写盘'),
    limit: z.number().optional().describe('list/find 的条数上限（默认 200）')
  }
}, async ({ action, type, name, as, modid, keyword, overwrite, dryRun, limit }) => {
  const act = action || 'list';
  const cap = Math.max(1, Math.min(1000, limit || 200));

  if (act === 'list') {
    const want = !type || type === 'all' ? null : type;
    const rows = assetCatalog().filter((r) => !want || r.type === want);
    return text({
      note: '几页 pinwheel 资产共 ' + rows.length + ' 条（' + (want || '全部') + '）。'
        + '用 action=show <name> 拿源码；action=copy <name> 拷进升仙工程。',
      total: rows.length,
      compile: { total: COMPILE.total, ok: COMPILE.ok, fail: COMPILE.fail },
      assets: rows.slice(0, cap).map((r) => ({
        type: r.type, name: r.name, lines: r.lines, uniforms: r.uniforms,
        compile: r.type === 'program' ? (compileStatus(r.name) || null) : null
      }))
    });
  }

  if (act === 'find') {
    if (!keyword) {
      return fail('find 需要 keyword');
    }
    const kw = keyword.toLowerCase();
    const hits = [];
    for (const f of FAMILIES) {
      const reuse = Array.isArray(f.reuse) ? f.reuse.join(' ')
        : (typeof f.reuse === 'string' ? f.reuse : '');
      const blob = [f.id, f.title, f.role, f.mechanism, reuse].join(' ').toLowerCase();
      if (blob.includes(kw)) {
        hits.push({
          family: f.id, title: f.title, kind: f.kind, role: f.role,
          members: f.members, mechanism: f.mechanism, uniforms: f.uniforms, api: f.api
        });
      }
    }
    const byName = assetCatalog()
      .filter((r) => r.name.toLowerCase().includes(kw) || (r.tags || []).some((t) => String(t).toLowerCase().includes(kw)))
      .slice(0, 40);
    if (!hits.length && !byName.length) {
      const deep = [];
      for (const p of PROGRAMS.values()) {
        const blob = [p.name, ...(p.fsh?.tags || []), ...(p.fsh?.uniforms || []).map((u) => u[0])].join(' ').toLowerCase();
        if (blob.includes(kw)) {
          deep.push({ name: p.name, tags: p.fsh?.tags || [] });
        }
      }
      return text({ keyword, families: [], assets: byName, deepMatches: deep.slice(0, 40),
        note: '没命中家族与资产名，改用 uniform / tag 深搜' });
    }
    return text({ keyword, families: hits, assets: byName });
  }

  if (act === 'show') {
    if (!name) {
      return fail('show 需要 name');
    }
    const t = type && type !== 'all' ? type : guessType(name);
    const a = readAsset(t, name);
    if (!a) {
      const alt = ['program', 'rendertype', 'post', 'framebuffer', 'include']
        .map((x) => (readAsset(x, name) ? x : null)).filter(Boolean);
      if (alt.length) {
        return fail('「' + name + '」存在但类型是 ' + alt.join('/') + '，请用 type 指定');
      }
      return fail('找不到资产：' + name);
    }
    return text({
      type: a.type, name: a.name,
      files: a.files.map((f) => ({ kind: f.kind, rel: f.rel, content: f.content })),
      includeFiles: a.includeFiles,
      deps: a.deps,
      compile: t === 'program' ? (compileStatus(a.name) || null) : null,
      usage: usageOf(t),
      note: '★几页的命名空间是 guzrlevelup；拷进升仙要用 action=copy（会自动改命名空间）。'
    });
  }

  if (act === 'copy') {
    if (!name) {
      return fail('copy 需要 name');
    }
    const t = type && type !== 'all' ? type : guessType(name);
    const r = copyAsset({
      name, type: t, as: as || null, modid: modid || 'shengxian',
      overwrite: !!overwrite, dryRun: !!dryRun
    });
    if (!r.ok) {
      return fail(r.error);
    }
    return text({
      action: 'copy', dryRun: !!dryRun, renamed: r.renamed,
      files: r.files, textures: r.textures, written: r.written,
      conflicts: r.conflicts, notes: r.notes, usage: r.usage,
      compile: r.compile,
      next: '资源已进 shengxian/src/main/resources/assets/<ns>/pinwheel/；F3+T 热重载后，用 fx_java 拿 Java 胶水接上。'
    });
  }

  return fail('未知 action：' + act);
});

server.registerTool('fx_schema', {
  title: 'Veil 4.5.2 资源格式权威表',
  description: '按真实 jar 核对过的 schema：rendertype / layer / program / texture-source / post / post-stage / framebuffer / ubo / java。写 JSON 前查这个，别凭记忆。不传 id 列目录。',
  inputSchema: { id: z.string().optional() }
}, async ({ id }) => {
  if (!id) return text({ meta: SCHEMA_META, topics: SCHEMA.map(s => ({ id: s.id, title: s.title })) });
  const s = SCHEMA_BY_ID[id];
  if (!s) return fail('未知 schema id：' + id + '；可选 ' + SCHEMA.map(x => x.id).join(', '));
  return text(s);
});

server.registerTool('fx_mechanism', {
  title: '底层机制',
  description: '讲清一帧的时序、层链状态机、uniform 绑定时机、MRT 为什么是两张颜色+深度、post 顺序、热重载失败模式、性能与两栈选型。做特效前先读这个，少走弯路。',
  inputSchema: { id: z.string().optional() }
}, async ({ id }) => {
  if (!id) return text({ topics: MECHANISM.map(m => ({ id: m.id, title: m.title })) });
  const m = MECHANISM.find(x => x.id === id);
  if (!m) return fail('未知机制 id：' + id + '；可选 ' + MECHANISM.map(x => x.id).join(', '));
  return text(m);
});

server.registerTool('fx_recipes', {
  title: '技法配方库',
  description: '15 条可抄配方（体积壳、深度守卫、云平面、双趟 bloom、时间回溯、每帧拖尾丝带、零依赖加法混合刀光…）。13 条 GLSL/JSON + 2 条 Java 世界几何（daodao 实战）。不传 id 列表，传 id 出代码与坑。',
  inputSchema: { id: z.string().optional() }
}, async ({ id }) => {
  if (!id) return text({ recipes: RECIPES.map(r => ({ id: r.id, title: r.title, when: r.when })) });
  const r = RECIPES.find(x => x.id === id);
  if (!r) return fail('未知配方 id：' + id);
  return text(r);
});

server.registerTool('fx_plan', {
  title: '从一句话出特效方案',
  description: '把中文需求（如“血色领域，进入后屏幕泛红、远处血雾、脉动”）变成制作方案：选 kind、给 ASCII 名、色板、pieces、uniform 默认值、Java 步骤、风险与自检项，并指一个几页同类家族作参考。',
  inputSchema: {
    goal: z.string().describe('效果描述（中文可以）'),
    kind: z.enum(Object.keys(KINDS)).optional().describe('强制指定类型'),
    name: z.string().optional().describe('ASCII 蛇形名；不传会自动起名'),
    modid: z.string().optional(),
    target: z.enum(['veil', 'photon', 'vanilla']).optional()
  }
}, async (args) => text(planEffect(args)));

server.registerTool('fx_create', {
  title: '生成特效包',
  description: '按方案生成完整 Veil 资源包（rendertype / program / vsh / fsh / post / framebuffer）+ Java 胶水 + README，写到 特效工坊/<name>。生成后自动跑一遍校验。默认只写暂存目录，不动模组源码。',
  inputSchema: {
    name: z.string().describe('ASCII 蛇形名，如 blood_domain'),
    kind: z.enum(Object.keys(KINDS)),
    modid: z.string().optional(),
    family: z.string().optional(),
    colors: z.object({
      primary: z.string().optional(),
      secondary: z.string().optional(),
      accent: z.string().optional()
    }).optional(),
    uniforms: z.array(z.object({
      name: z.string(),
      type: z.enum(['float', 'int', 'vec3']).optional(),
      default: z.union([z.number(), z.array(z.number())]).optional(),
      desc: z.string().optional()
    })).optional(),
    bloom: z.boolean().optional().describe('几何类是否加第二趟 bloom'),
    stage: z.enum(['after_level', 'after_translucent_blocks']).optional(),
    priority: z.number().optional(),
    javaPackage: z.string().optional(),
    outDir: z.string().optional(),
    includeJava: z.boolean().optional(),
    dryRun: z.boolean().optional()
  }
}, async (args) => {
  try {
    const built = buildEffect(args);
    const { spec, files } = built;
    const java = args.includeJava === false ? null : buildJava(spec);
    const readme = buildJavaReadme(spec);
    const all = [...files];
    if (java) {
      const cls = pascal(spec.family) + 'Fx';
      all.push({ path: `java/${spec.javaPackage.replace(/\./g, '/')}/${cls}.java`, content: java });
    }
    all.push({ path: 'README.md', content: readme });
    all.push({
      path: 'fx.json',
      content: JSON.stringify({ spec, generatedBy: 'jiye-fx@1.0.0', fileCount: all.length }, null, 2) + '\n'
    });

    const validation = validateFiles(files, { family: spec.family });
    const { dir } = safeOutDir(args.outDir, spec.name);
    let written = [];
    if (!args.dryRun) written = writeFiles(dir, all);
    return text({
      ok: validation.ok,
      spec,
      outDir: dir,
      dryRun: !!args.dryRun,
      files: written.length ? written : all.map(f => f.path),
      validation,
      next: [
        `看一眼 ${path.join(dir, 'README.md')}`,
        '把 assets 合进模组：shengxian/src/main/resources/',
        'Java 文件放进 shengxian/src/main/java/... 后编译',
        '进游戏 F3+T 热重载，先把 Strength 设 0 再拉满'
      ]
    });
  } catch (e) {
    return fail(e.message);
  }
});

server.registerTool('fx_validate', {
  title: '校验特效包',
  description: '静态校验一个目录：JSON 字段、program↔文件↔rendertype↔post↔FBO 的交叉引用、GLSL（out/UBO/space_helper/深度守卫/未用 uniform/MRT location）、bloom 趟是否 additive、命名是否同族。',
  inputSchema: {
    dir: z.string().describe('特效包目录（绝对路径或工程内相对路径）'),
    family: z.string().optional()
  }
}, async ({ dir, family }) => {
  try {
    const abs = path.isAbsolute(dir) ? dir : path.join(ROOT, dir);
    const files = [];
    const walk = d => {
      for (const e of fs.readdirSync(d, { withFileTypes: true })) {
        const p = path.join(d, e.name);
        if (e.isDirectory()) walk(p);
        else if (/\.(json|fsh|vsh|glsl)$/i.test(e.name)) {
          files.push({ path: path.relative(abs, p).split(path.sep).join('/'), content: fs.readFileSync(p, 'utf8') });
        }
      }
    };
    walk(abs);
    return text(validateFiles(files, { family }));
  } catch (e) {
    return fail(e.message);
  }
});

server.registerTool('fx_java', {
  title: '只生成 Java 胶水',
  description: '给定 name/kind 生成调用代码（Veil）：几何提交 + 每帧推 uniform + post 注册。也可只打印不落盘。',
  inputSchema: {
    name: z.string(),
    kind: z.enum(Object.keys(KINDS)),
    modid: z.string().optional(),
    javaPackage: z.string().optional(),
    outDir: z.string().optional(),
    write: z.boolean().optional()
  }
}, async ({ name, kind, modid, javaPackage, outDir, write }) => {
  try {
    const spec = normalizeSpec({ name, kind, modid, javaPackage });
    const code = buildJava(spec);
    let file = null;
    if (write) {
      const { dir } = safeOutDir(outDir, spec.name);
      const cls = pascal(spec.family) + 'Fx';
      const rel = `java/${spec.javaPackage.replace(/\./g, '/')}/${cls}.java`;
      writeFiles(dir, [{ path: rel, content: code }]);
      file = path.join(dir, rel);
    }
    return text({ spec, file, code });
  } catch (e) {
    return fail(e.message);
  }
});

server.registerTool('fx_uniforms', {
  title: '参数语义速查',
  description: '解释一个 uniform 的含义/量纲（内置 174 个实测名 + 家族前缀推断）。',
  inputSchema: { name: z.string() }
}, async ({ name }) => {
  const [doc, unit] = describeUniform(name);
  const owners = [...PROGRAMS.values()].filter(p => (p.fsh?.uniforms || []).some(([n]) => n === name)).map(p => p.name);
  return text({ name, doc, unit, usedBy: owners.slice(0, 20), count: owners.length });
});

server.registerTool('fx_resource', {
  title: '读规范全文/检索',
  description: '读《几页特效规范-SKILL.md》或《几页特效全表.md》。传 grep 时只返回命中段落（±context 行），避免一次灌太多。',
  inputSchema: {
    kind: z.enum(['skill', 'table']),
    grep: z.string().optional(),
    context: z.number().int().min(0).max(10).optional(),
    maxChars: z.number().int().min(500).max(60000).optional()
  }
}, async ({ kind, grep, context, maxChars }) => {
  const r = readResource(kind);
  if (!r) return fail('没有找到资源文件；确认 JIYE_FX_ROOT 指向工程根，或 data/ 下有副本');
  if (!grep) {
    const cap = maxChars || 20000;
    return text({ path: r.path, truncated: r.text.length > cap, text: r.text.slice(0, cap) });
  }
  const lines = r.text.split('\n');
  const ctx = context ?? 2;
  const hits = [];
  const seen = new Set();
  lines.forEach((l, i) => {
    if (!l.includes(grep)) return;
    for (let k = Math.max(0, i - ctx); k <= Math.min(lines.length - 1, i + ctx); k++) {
      if (!seen.has(k)) { seen.add(k); hits.push(`${k + 1}: ${lines[k]}`); }
    }
  });
  return text({ path: r.path, query: grep, hits: hits.slice(0, 400) });
});

// ---------------------------------------------------------------- run
const transport = new StdioServerTransport();
await server.connect(transport);
console.error('[jiye-fx] ready, root=' + ROOT);
