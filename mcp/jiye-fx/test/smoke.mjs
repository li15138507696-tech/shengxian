import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const mcpDir = path.resolve(here, '..');
const root = path.resolve(mcpDir, '..', '..');
const staging = path.join(root, '特效工坊', '_smoke');

const results = [];
function check(name, cond, detail = '') {
  results.push({ name, ok: !!cond, detail });
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${name}${detail ? '  ' + detail : ''}`);
}

const transport = new StdioClientTransport({
  command: process.execPath,
  args: [path.join(mcpDir, 'server.mjs')],
  env: { ...process.env, JIYE_FX_ROOT: root },
  stderr: 'pipe'
});
const client = new Client({ name: 'smoke', version: '1.0.0' }, { capabilities: {} });
await client.connect(transport);

try {
  const tools = await client.listTools();
  const names = tools.tools.map(t => t.name).sort();
  check('tools/list 数量 >= 10', tools.tools.length >= 10, String(tools.tools.length));
  for (const t of ['fx_plan', 'fx_create', 'fx_validate', 'fx_chain', 'fx_api', 'fx_recipes', 'fx_stats']) {
    check('存在工具 ' + t, names.includes(t));
  }

  const stats = await call('fx_stats', {});
  check('fx_stats 469 文件', stats.counts?.files_total === 469, JSON.stringify(stats.counts?.programs));
  check('fx_stats 家族覆盖 0 缺口', stats.coverage?.missing?.length === 0 && stats.coverage?.uncovered?.length === 0);

  const plan = await call('fx_plan', { goal: '血色领域，进入后屏幕泛红、远处血雾、有脉动', modid: 'shengxian' });
  check('fx_plan 选中 domain_sphere/screen_post', ['domain_sphere', 'screen_post'].includes(plan.kind), plan.kind);
  check('fx_plan 给了参考家族', !!plan.reference?.family, plan.reference?.family);

  const created = await call('fx_create', {
    name: 'smoke_blood_domain', kind: 'domain_sphere', modid: 'shengxian',
    outDir: path.join(staging, 'blood')
  });
  check('fx_create 成功', created.ok === true, JSON.stringify(created.validation?.errors?.slice(0, 2)));
  check('fx_create 写了 shader', created.files.some(f => f.endsWith('.fsh')));
  check('fx_create 写了 post', created.files.some(f => f.includes('/post/')));
  check('fx_create 写了 java', created.files.some(f => f.endsWith('.java')));

  const dir = path.join(staging, 'blood');
  const val = await call('fx_validate', { dir });
  check('fx_validate 0 error', val.ok === true, JSON.stringify(val.errors?.slice(0, 2)));
  check('fx_validate 有 summary', val.summary?.programs >= 1, JSON.stringify(val.summary));

  const field = await call('fx_create', {
    name: 'smoke_wind_field', kind: 'volume_field', modid: 'shengxian',
    outDir: path.join(staging, 'field')
  });
  check('volume_field 生成成功', field.ok === true, JSON.stringify(field.validation?.errors?.slice(0, 2)));
  check('volume_field 有 FBO', field.files.some(f => f.includes('/framebuffers/')));
  check('volume_field 有 composite', field.files.some(f => f.includes('_composite.fsh')));

  const fieldVal = await call('fx_validate', { dir: path.join(staging, 'field'), family: 'smoke_wind_field' });
  check('volume_field 校验通过', fieldVal.ok === true, JSON.stringify(fieldVal.errors?.slice(0, 2)));

  // 全量链路可查：122 个 program 每个都能出 chain
  const all = [];
  for (const p of (await import('../lib/knowledge.mjs')).PROGRAMS.keys()) all.push(p);
  const bad = [];
  for (const n of all) {
    const r = await client.callTool({ name: 'fx_chain', arguments: { name: n } });
    if (r.isError) { bad.push(n); continue; }
    const c = JSON.parse(r.content[0].text);
    if (!c.shaders?.fragmentFile) bad.push(n + ':no-fsh');
  }
  check('全部 program 链路可查', bad.length === 0, bad.slice(0, 3).join(', '));

  const ch = await call('fx_chain', { name: 'fan_wind_composite' });
  check('fx_chain 找到 post', ch.posts?.length === 1, ch.posts?.[0]?.name);
  check('fx_chain 找到 FBO', ch.framebuffers?.length === 3, String(ch.framebuffers?.length));
  check('fx_chain 有调用路径', ch.callPath?.length >= 2, String(ch.callPath?.length));

  const api = await call('fx_api', { id: 'veil-post' });
  check('fx_api 返回代码', typeof api.code === 'string' && api.code.includes('preVeilPostProcessing'));

  const sch = await call('fx_schema', { id: 'program' });
  check('fx_schema 给出 4.5.2 权威字段', Array.isArray(sch.fields) && sch.fields.some(f => f.name === 'textures'), String(sch.fields?.length));

  const sp = await call('fx_space', {});
  check('fx_space 列 4 约定 + 10 形态', sp.conventions?.length === 4 && sp.shapes?.length === 10,
    `${sp.conventions?.length}/${sp.shapes?.length}`);
  const shell = await call('fx_space', { id: 'sphere-shell' });
  check('fx_space 球壳条目含网格与法线做法', !!shell.mesh && !!shell.normal, shell.coords);
  const fx = await call('fx_space', { id: 'mirror-art' });
  check('fx_space 立体效果卡片含步骤/常量/复用', Array.isArray(fx.steps) && fx.steps.length >= 5
    && !!fx.constants && Array.isArray(fx.reuse), fx.programs?.join(','));
  const fm = await call('fx_space', { id: 'filament' });
  check('fx_space 公式库可查（细丝公式）', typeof fm.code === 'string' && fm.code.includes('pow('), fm.code);
  const list = await call('fx_space', {});
  check('fx_space 目录含 6 公式 + 16 卡片', list.formulas?.length === 6 && list.effects?.length === 16,
    `${list.formulas?.length}/${list.effects?.length}`);

  const be = await call('fx_space', { id: 'backend-screen' });
  check('fx_space 有后端选型条目（全屏 vs 几何）', !!be.when && Array.isArray(be.judge) && be.judge.length >= 2, be.id);
  const beList = await call('fx_space', {});
  check('fx_space 目录含 2 条后端选型', beList.backend?.length === 2, String(beList.backend?.length));

  const conv = await call('fx_space', { id: 'world' });
  check('fx_space 坐标约定含计数', conv.count === 63, String(conv.count));

  const mech = await call('fx_mechanism', { id: 'pipeline' });
  check('fx_mechanism 返回时序', Array.isArray(mech.body) && mech.body.length >= 5, String(mech.body?.length));

  const rec = await call('fx_recipes', { id: 'depth-guard' });
  check('fx_recipes 返回 GLSL', rec.code.includes('0.0008'));

  const recList = await call('fx_recipes', {});
  check('fx_recipes 15 条', recList.recipes?.length === 15, String(recList.recipes?.length));
  const trail = await call('fx_recipes', { id: 'per-frame-trail' });
  check('fx_recipes 刀光拖尾配方（daodao 实例）', typeof trail.code === 'string' && trail.code.includes('nanoTime'), trail.id);
  const eyes = await call('fx_recipes', { id: 'additive-eyes' });
  check('fx_recipes eyes 加法混合配方', typeof eyes.code === 'string' && eyes.code.includes('RenderType.eyes'), eyes.id);

  const demoDir = path.join(root, '特效工坊', 'demo_blade_trail');
  check('demo_blade_trail 实战实例存在',
    fs.existsSync(path.join(demoDir, 'README.md'))
    && fs.existsSync(path.join(demoDir, 'java', 'net', 'guzhenren', 'shengxian', 'vfx', 'BladeTrailFx.java'))
    && fs.existsSync(path.join(demoDir, 'assets', 'shengxian', 'textures', 'fx', 'blade_trail.png')));

  // 校验器回归：4.5.2 schema 的硬约束（每修一条就钉一条）
  const V = await import('../lib/validate.mjs');
  const mk = (sub, o) => ({ path: `assets/m/pinwheel/${sub}`, content: JSON.stringify(o) });
  const prog = [
    mk('shaders/program/p.json', { vertex: 'm:p', fragment: 'm:p' }),
    { path: 'assets/m/pinwheel/shaders/program/p.vsh', content: 'void main(){gl_Position=vec4(0.);}' },
    { path: 'assets/m/pinwheel/shaders/program/p.fsh', content: 'out vec4 fragColor; void main(){ if(false) discard; fragColor=vec4(1.); }' }
  ];
  const codes = files => V.validateFiles(files).errors.map(e => e.code);
  check('回归：program.uniforms 被拒（4.5.2 无此键）', codes([...prog,
    mk('shaders/program/q.json', { vertex: 'm:p', fragment: 'm:p', uniforms: { A: 1 } })]).includes('program-unknown-key'));
  check('回归：textures type=asset 被拒', codes([...prog,
    mk('shaders/program/r.json', { vertex: 'm:p', fragment: 'm:p', textures: { S: { type: 'asset', name: 'x' } } })]).includes('program-texture-legacy'));
  check('回归：单趟嵌套 layers 被拒', codes([...prog,
    mk('rendertypes/t.json', { format: 'POSITION_COLOR', mode: 'QUADS', bufferSize: 'BIG', layers: [[{ type: 'veil:shader', name: 'm:p' }]] })]).includes('rt-single-pass-nested'));
  check('回归：bufferSize 非法值被拒', codes([...prog,
    mk('rendertypes/t2.json', { format: 'POSITION_COLOR', mode: 'QUADS', bufferSize: 'HUGE', layers: [{ type: 'veil:shader', name: 'm:p' }] })]).includes('rt-buffer-size'));
  check('回归：post in==out 被拒', codes([...prog,
    mk('post/x.json', { renderStage: 'after_level', stages: [{ type: 'veil:blit', shader: 'm:p', in: 'minecraft:main', out: 'minecraft:main' }] })]).includes('post-stage-same-fbo'));
  check('回归：include 提供 out 不误报', codes([...prog.slice(0, 2),
    { path: 'assets/m/pinwheel/shaders/program/p.fsh', content: '#include m:c void main(){ fragColor = vec4(1.0); }' }]).length === 0);

  // 回归：生成的 post Java 必须包含 add()（2026-10-25 实机「指令没特效」的根因）
  const T = await import('../lib/templates.mjs');
  const J = await import('../lib/java.mjs');
  const postJava = J.buildJava(T.buildEffect({ name: 'reg_post', kind: 'screen_post' }).spec);
  check('回归：post Java 含 getPostProcessingManager().add', postJava.includes('getPostProcessingManager().add'));
  const fieldJava = J.buildJava(T.buildEffect({ name: 'reg_field', kind: 'volume_field' }).spec);
  check('回归：volume_field Java 也含 add', fieldJava.includes('getPostProcessingManager()'));

  // 几页资产库：目录 / 源码 / 拷贝（dryRun）
  const cat = await call('fx_jiye', { action: 'list', type: 'all', limit: 1000 });
  check('fx_jiye 列出全部几页资产（>=250）', cat.total >= 250, String(cat.total));
  const progList = await call('fx_jiye', { action: 'list', type: 'program', limit: 1000 });
  check('fx_jiye 能按类型过滤（program 122）', progList.total === 122, String(progList.total));
  const src = await call('fx_jiye', { action: 'show', name: 'iron_wall_bell' });
  const srcFile = (src.files || []).find((f) => f.rel.endsWith('.fsh'));
  check('fx_jiye show 拿到完整 fsh 源码', !!srcFile && srcFile.content.includes('void main'),
    String(srcFile && srcFile.content.length));
  const dry = await call('fx_jiye', { action: 'copy', name: 'aperture_world_core', as: 'tmp_alias_probe', dryRun: true });
  check('fx_jiye copy(dryRun) 解析出依赖闭包', dry.ok !== false && dry.files.length >= 3,
    `${dry.files && dry.files.length}`);
  const fnd = await call('fx_jiye', { action: 'find', keyword: '漩涡' });
  check('fx_jiye find 能按中文关键词命中家族', (fnd.families || []).length >= 1
    || (fnd.deepMatches || []).length >= 1, String((fnd.families || []).length));

  // 几页 122 个 program 的真 GL 编译状态要挂在目录里
  const comp = await call('fx_jiye', { action: 'list', type: 'program', limit: 5 });
  check('fx_jiye 附带真 GL 编译清单（122/122）',
    comp.compile && comp.compile.total === 122 && comp.compile.ok === 122,
    JSON.stringify(comp.compile));
  const withStatus = (comp.assets || []).find((a) => a.compile);
  check('fx_jiye 每条 program 带 compile 状态', !!withStatus && withStatus.compile.ok === true,
    withStatus && withStatus.name);

  const res = await call('fx_resource', { kind: 'skill', grep: '双趟' });
  check('fx_resource 能 grep', Array.isArray(res.hits) && res.hits.length > 0, String(res.hits?.length));
} finally {
  await client.close();
  fs.rmSync(staging, { recursive: true, force: true });
}

const failed = results.filter(r => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} PASS`);
if (failed.length) process.exitCode = 1;

async function call(name, args) {
  const r = await client.callTool({ name, arguments: args });
  const t = r.content?.[0]?.text || '';
  if (r.isError) throw new Error(name + ' failed: ' + t);
  try { return JSON.parse(t); } catch { return t; }
}
