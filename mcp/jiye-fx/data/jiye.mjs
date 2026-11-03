/**
 * 几页（guzrlevelupdemoV5.4）特效资产库 —— 让 MCP 能**查源码、拷进工程**。
 *
 * <p>之前 MCP 只有「加工过的摘要」（`data/index.json`：uniform、行数、依赖关系），
 * 拿不到真正的 GLSL 源码，也没法把几页的现成效果搬进升仙。
 * 这个模块补上这两件事：</p>
 *
 * <ul>
 *   <li>{@link readAsset} —— 读出某个资产的<b>完整源码</b>（JSON + vsh + fsh + include）；</li>
 *   <li>{@link copyAsset} —— 连<b>依赖闭包</b>一起拷进工程（命名空间改写、改名、贴图跟随）。</li>
 * </ul>
 *
 * <p>几页的命名空间是 {@code guzrlevelup}，拷到升仙一律改成 {@code shengxian}。</p>
 *
 * <h3>★依赖解析必须按 JSON 结构来，不能扫字符串</h3>
 * <p>几页里 <b>program 与 framebuffer 常常同名</b>（{@code fan_wind_field} 两者都有）：
 * program 的 {@code vertex/fragment} 指的是 program，而 {@code textures} 里同名引用指的是
 * <b>framebuffer</b>。用一条正则扫字符串必然分错 —— 2026-10-25 端到端验证时正是这么错的
 * （FBO 没跟过来、include 被当成 program）。所以这里逐键解析。</p>
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { ROOT } from '../lib/knowledge.mjs';

export const JIYE_NS = 'guzrlevelup';

const exists = (p) => fs.existsSync(p);

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DEMO_DIR = path.join(ROOT, '素材', 'guzrlevelupdemoV5.4-特效与贴图');
/** 几页的 pinwheel 资源根（rendertypes / post / framebuffers / shaders）。 */
export const JIYE_ROOT = path.join(DEMO_DIR, '特效', 'pinwheel');
/** 几页的贴图根（textures/…）。 */
export const JIYE_TEX = path.join(DEMO_DIR, '贴图');

/** 加工过的资产摘要（扫描器产物：行数/uniform/tag/依赖）。源码不在这里，要源码走 readAsset。 */
export const INDEX = JSON.parse(fs.readFileSync(path.join(HERE, 'index.json'), 'utf8'));

/** 真 GL 编译清单（tools/scan_jiye_compile.py 产出）：哪些 program 在 4.5.2 下能编过。 */
export const COMPILE = (() => {
  const p = path.join(HERE, 'jiye_compile.json');
  return exists(p) ? JSON.parse(fs.readFileSync(p, 'utf8')) : { total: 0, ok: 0, fail: 0, results: {} };
})();


function walk(dir, out = []) {
  if (!exists(dir)) {
    return out;
  }
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      walk(p, out);
    } else {
      out.push(p);
    }
  }
  return out;
}

/** pinwheel 下的全部文件（相对路径，小写斜杠）。缓存。 */
let FILE_INDEX = null;
function fileIndex() {
  if (FILE_INDEX) {
    return FILE_INDEX;
  }
  FILE_INDEX = new Map();
  for (const p of walk(JIYE_ROOT)) {
    const rel = path.relative(JIYE_ROOT, p).split(path.sep).join('/');
    FILE_INDEX.set(rel.toLowerCase(), p);
  }
  return FILE_INDEX;
}

/** 在 pinwheel 里找一个文件（按相对路径，大小写不敏感）。 */
function findRel(rel) {
  const idx = fileIndex();
  return idx.get(rel.toLowerCase()) || null;
}

export function readText(abs) {
  return fs.readFileSync(abs, 'utf8');
}

/* ============================ 定位 ============================ */

/** 找某类资产的源文件（相对 pinwheel 根）。找不到返回 null。 */
export function locate(type, name) {
  switch (type) {
    case 'program': {
      const json = findRel(`shaders/program/${name}.json`);
      const vsh = findRel(`shaders/program/${name}.vsh`);
      const fsh = findRel(`shaders/program/${name}.fsh`);
      if (!json && !vsh && !fsh) {
        return null;
      }
      return { json, vsh, fsh };
    }
    case 'rendertype': {
      const json = findRel(`rendertypes/${name}.json`);
      return json ? { json } : null;
    }
    case 'post': {
      for (const [rel, abs] of fileIndex()) {
        if (rel.startsWith('post/') && rel.endsWith(`/${name}.json`)) {
          return { json: abs };
        }
      }
      return null;
    }
    case 'framebuffer': {
      const json = findRel(`framebuffers/${name}.json`);
      return json ? { json } : null;
    }
    case 'include': {
      const glsl = findRel(`shaders/include/${name}.glsl`);
      return glsl ? { glsl } : null;
    }
    default:
      return null;
  }
}

/* ============================ 依赖解析（按 JSON 结构） ============================ */

function emptyDeps() {
  return {
    program: new Set(),
    rendertype: new Set(),
    post: new Set(),
    framebuffer: new Set(),
    include: new Set(),
    texture: new Set(),
  };
}

/**
 * 记一条引用。
 * @param kind program / framebuffer / location（贴图或原版资源）
 */
function addRef(deps, ref, kind) {
  if (typeof ref !== 'string' || !ref) {
    return;
  }
  if (ref.startsWith('minecraft:') || ref.startsWith('veil:')) {
    return;   // 原版 / Veil 自带，不属于几页
  }
  if (!ref.startsWith(JIYE_NS + ':')) {
    return;
  }
  const bare = ref.slice(JIYE_NS.length + 1);
  if (bare.startsWith('textures/')) {
    deps.texture.add(bare);
    return;
  }
  // ':depth' 之类后缀要剥掉（FBO 的深度附件）
  const name = bare.split(':')[0];
  if (kind === 'framebuffer') {
    deps.framebuffer.add(name);
  } else if (kind === 'location') {
    // 原版/外部贴图：忽略（只有几页自己的 textures/ 才处理）
  } else {
    deps.program.add(name);
  }
}

/** 逐键解析一个 JSON（program / rendertype / post 各有各的键）。 */
function collectJsonDeps(j, deps) {
  if (!j || typeof j !== 'object') {
    return;
  }
  // ---- program ----
  if (typeof j.vertex === 'string') {
    addRef(deps, j.vertex, 'program');
  }
  if (typeof j.fragment === 'string') {
    addRef(deps, j.fragment, 'program');
  }
  if (j.textures && typeof j.textures === 'object') {
    for (const v of Object.values(j.textures)) {
      if (typeof v === 'string') {
        addRef(deps, v, 'location');
      } else if (v && typeof v === 'object') {
        if (v.type === 'framebuffer' && v.name) {
          addRef(deps, v.name, 'framebuffer');
        } else if (v.name) {
          addRef(deps, v.name, 'location');
        } else if (v.location) {
          addRef(deps, v.location, 'location');
        }
      }
    }
  }
  // ---- rendertype ----
  if (Array.isArray(j.layers)) {
    for (const l of j.layers) {
      if (l && l.type === 'veil:shader' && l.name) {
        addRef(deps, l.name, 'program');
      }
    }
  }
  // ---- post ----
  if (Array.isArray(j.stages)) {
    for (const s of j.stages) {
      if (s && s.shader) {
        addRef(deps, s.shader, 'program');
      }
      if (s && s.in) {
        addRef(deps, s.in, 'framebuffer');
      }
      if (s && s.out) {
        addRef(deps, s.out, 'framebuffer');
      }
    }
  }
}

const INC_RE = /#include\s+([A-Za-z0-9_]+):([A-Za-z0-9_]+)/g;

/**
 * 从一批文件里抽依赖（按文件类型分别处理）。
 * @param entries [{kind, content}]，kind 为 'json' / 'glsl'
 */
export function extractDeps(entries) {
  const deps = emptyDeps();
  for (const e of entries) {
    if (e.kind === 'json') {
      let j = null;
      try {
        j = JSON.parse(e.content);
      } catch (err) {
        continue;
      }
      collectJsonDeps(j, deps);
    } else {
      INC_RE.lastIndex = 0;
      let m;
      while ((m = INC_RE.exec(e.content)) !== null) {
        if (m[1] === JIYE_NS) {
          deps.include.add(m[2]);
        }
      }
    }
  }
  return deps;
}

function depsToPlain(deps) {
  const o = {};
  for (const k of Object.keys(deps)) {
    o[k] = [...deps[k]].sort();
  }
  return o;
}

/* ============================ 读取 ============================ */

/**
 * 读一个资产的完整源码 + 它的依赖清单。
 *
 * @param type program / rendertype / post / framebuffer / include
 * @param name 资产名（不含命名空间）
 */
export function readAsset(type, name) {
  const loc = locate(type, name);
  if (!loc) {
    return null;
  }
  const files = [];
  for (const [kind, abs] of Object.entries(loc)) {
    if (!abs) {
      continue;
    }
    const rel = path.relative(JIYE_ROOT, abs).split(path.sep).join('/');
    files.push({
      kind: rel.endsWith('.json') ? 'json' : 'glsl',
      ext: path.extname(rel),
      rel,
      content: readText(abs),
    });
  }
  const deps = extractDeps(files);
  return {
    type,
    name,
    files,
    deps: depsToPlain(deps),
    depsRaw: deps,
  };
}

/* ============================ 拷贝 ============================ */

function targetRel(type, name) {
  switch (type) {
    case 'program':
      return `shaders/program/${name}`;
    case 'rendertype':
      return `rendertypes/${name}.json`;
    case 'post':
      return `post/${name}.json`;
    case 'framebuffer':
      return `framebuffers/${name}.json`;
    case 'include':
      return `shaders/include/${name}.glsl`;
    default:
      return null;
  }
}

/** 文本改写：命名空间替换 +（可选）主资产改名。 */
function rewrite(content, modid, renameFrom, renameTo) {
  let out = content.split(JIYE_NS + ':').join(modid + ':');
  if (renameFrom && renameTo && renameFrom !== renameTo) {
    out = out.split(`"${modid}:${renameFrom}"`).join(`"${modid}:${renameTo}"`);
    out = out.split(`${modid}:${renameFrom}.`).join(`${modid}:${renameTo}.`);
  }
  return out;
}

/**
 * 清洗 GLSL：include 与本文件都声明 camera UBO 时（几页常见：
 * `#include veil:space_helper` + `#veil:buffer veil:camera VeilCamera`），
 * 展开后是两个同名 UBO ⇒ 驱动报 C1038。拷贝时把重复的那行删掉。
 */
function cleanGlsl(src) {
  const incRe = /#include\s+([A-Za-z0-9_]+):([A-Za-z0-9_]+)/g;
  let hasCamera = false;
  let m;
  while ((m = incRe.exec(src)) !== null) {
    const ns = m[1];
    const nm = m[2];
    if (ns === 'veil') {
      if (nm === 'space_helper' || nm === 'camera') {
        hasCamera = true;
      }
    } else {
      const loc = locate('include', nm);
      if (loc && loc.glsl && /#veil:buffer\s+veil:camera/.test(readText(loc.glsl))) {
        hasCamera = true;
      }
    }
  }
  if (hasCamera) {
    src = src.replace(/^\s*#veil:buffer\s+veil:camera\s+VeilCamera\s*$/m, '');
  }
  return src;
}

/**
 * 把几页的一个资产（连同依赖闭包）拷进目标工程。
 *
 * <p>做的事：命名空间 {@code guzrlevelup → modid}；主资产可改名（自引用一并改）；
 * 依赖的 program / rendertype / post / FBO / include / 贴图按需跟随；
 * 共享资源（被多个 program 共用的 vsh）<b>不改名</b>，避免破坏共享关系；
 * 已存在的文件默认<b>不覆盖</b>（除非 {@code overwrite}）。</p>
 */
export function copyAsset(opts) {
  const { name, type } = opts;
  const modid = opts.modid || 'shengxian';
  const as = opts.as || null;
  const destRoot = opts.destRoot
    || path.join(ROOT, 'src', 'main', 'resources', 'assets');
  const dryRun = !!opts.dryRun;

  const main = readAsset(type, name);
  if (!main) {
    return { ok: false, error: `找不到 ${type}：${name}` };
  }

  const out = [];
  const notes = [];

  /* ---- 1. 主资产 ---- */
  const mainName = as || name;
  for (const f of main.files) {
    let rel;
    if (type === 'program') {
      rel = `shaders/program/${mainName}${f.ext}`;
    } else {
      rel = targetRel(type, mainName);
    }
    const body = rewrite(f.content, modid, name, mainName);
    out.push({
      rel: `${modid}/pinwheel/${rel}`,
      content: f.ext === '.glsl' ? cleanGlsl(body) : body,
      kind: type,
    });
  }
  // 改名时：主 program json 里的自引用要改成新名
  if (as && type === 'program') {
    for (const o of out) {
      if (o.rel.endsWith('.json')) {
        try {
          const j = JSON.parse(o.content);
          if (typeof j.vertex === 'string' && j.vertex.endsWith(':' + name)) {
            j.vertex = modid + ':' + mainName;
          }
          if (typeof j.fragment === 'string' && j.fragment.endsWith(':' + name)) {
            j.fragment = modid + ':' + mainName;
          }
          o.content = JSON.stringify(j, null, 2) + '\n';
        } catch (err) {
          notes.push('主 program json 解析失败，未改自引用：' + err.message);
        }
      }
    }
  }

  /* ---- 2. 依赖闭包 ---- */
  const seen = new Set([`${type}:${name}`]);
  const queue = [];
  const pushDeps = (d) => {
    for (const n of d.program) {
      queue.push({ type: 'program', name: n });
    }
    for (const n of d.rendertype) {
      queue.push({ type: 'rendertype', name: n });
    }
    for (const n of d.post) {
      queue.push({ type: 'post', name: n });
    }
    for (const n of d.framebuffer) {
      queue.push({ type: 'framebuffer', name: n });
    }
    for (const n of d.include) {
      queue.push({ type: 'include', name: n });
    }
  };
  pushDeps(main.depsRaw);

  let guard = 0;
  const texSeen = new Set(main.depsRaw.texture);
  while (queue.length && guard < 300) {
    guard++;
    const item = queue.shift();
    const key = `${item.type}:${item.name}`;
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    const dep = readAsset(item.type, item.name);
    if (!dep) {
      notes.push(`依赖缺失：${key}`);
      continue;
    }
    for (const f of dep.files) {
      const rel = item.type === 'program'
        ? `shaders/program/${item.name}${f.ext}`
        : targetRel(item.type, item.name);
      const body = rewrite(f.content, modid, null, null);
      out.push({
        rel: `${modid}/pinwheel/${rel}`,
        content: f.ext === '.glsl' ? cleanGlsl(body) : body,
        kind: item.type,
        dep: key,
      });
    }
    for (const t of dep.depsRaw.texture) {
      texSeen.add(t);
    }
    pushDeps(dep.depsRaw);
  }

  /* ---- 3. 贴图 ---- */
  const textures = [];
  for (const bare of texSeen) {
    const src = path.join(JIYE_TEX, bare.replace(/\//g, path.sep));
    if (!exists(src)) {
      notes.push(`贴图缺失：${bare}`);
      continue;
    }
    textures.push({ rel: `${modid}/${bare}`, content: fs.readFileSync(src), binary: true });
  }

  /* ---- 4. 写盘 ---- */
  const written = [];
  const conflicts = [];
  if (!dryRun) {
    for (const f of out.concat(textures)) {
      const p = path.join(destRoot, f.rel.split('/').join(path.sep));
      if (exists(p) && !opts.overwrite) {
        conflicts.push(f.rel);
        continue;
      }
      fs.mkdirSync(path.dirname(p), { recursive: true });
      fs.writeFileSync(p, f.content);
      written.push(f.rel);
    }
  }

  return {
    ok: true,
    dryRun,
    modid,
    renamed: as && as !== name ? `${name} → ${as}` : null,
    files: out.map((f) => ({ rel: f.rel, kind: f.kind, dep: f.dep || null })),
    textures: textures.map((t) => t.rel),
    written,
    conflicts,
    notes,
    deps: depsToPlain(main.depsRaw),
    usage: usageHint(type),
    compile: COMPILE.results[name] || null,
  };
}

/** 某 program 的真 GL 编译状态（null = 没扫过）。 */
export function compileStatus(name) {
  return COMPILE.results[name] || null;
}

/** 拷贝之后怎么用（给人看的提示）。 */
function usageHint(type) {
  if (type === 'program') {
    return [
      'program 不会自己跑：用 rendertype 提交几何，或放进 post 管线当全屏效果。',
      'Java：VeilRenderType.get(id) 取层链；VeilRenderSystem.setShader(id) 取 program；',
      'uniform 用 program.getUniform("X").isValid() 判断后 setFloat / setVector。'
    ];
  }
  if (type === 'rendertype') {
    return [
      'rendertype 是层链（格式/图元/混合/深度）：VeilRenderType.get(id) → '
      + 'Tesselator 按 rt.mode()/rt.format() 建 MeshData → rt.draw(mesh)。'
    ];
  }
  if (type === 'post') {
    return ['post 管线必须 PostProcessingManager.add(priority, id) 才会执行（renderStage 只是过滤条件）。'];
  }
  if (type === 'framebuffer') {
    return ['framebuffer 只在 post 管线内可见（写在 post 的 framebuffers 字典里）。'];
  }
  return ['include 用 #include <modid>:<name> 引用。'];
}

/* ============================ 目录 ============================ */

/** 全部资产的清单（从 index.json 读加工过的摘要，够做目录用）。 */
export function assetCatalog(index = INDEX) {
  const rows = [];
  for (const p of index.programs || []) {
    rows.push({
      type: 'program',
      name: p.name,
      lines: (p.fsh && p.fsh.lines) || (p.vsh && p.vsh.lines) || 0,
      uniforms: (p.fsh && p.fsh.uniforms ? p.fsh.uniforms.length : 0)
        + (p.vsh && p.vsh.uniforms ? p.vsh.uniforms.length : 0),
      tags: (p.fsh && p.fsh.tags) || [],
    });
  }
  for (const r of index.rendertypes || []) {
    rows.push({ type: 'rendertype', name: r.name, lines: 0, uniforms: 0, tags: [] });
  }
  for (const q of index.posts || []) {
    rows.push({ type: 'post', name: q.name, lines: 0, uniforms: 0, tags: [] });
  }
  for (const f of index.framebuffers || []) {
    rows.push({ type: 'framebuffer', name: f.name, lines: 0, uniforms: 0, tags: [] });
  }
  for (const i of index.includes || []) {
    rows.push({ type: 'include', name: i.name, lines: i.info && i.info.lines, uniforms: 0, tags: [] });
  }
  return rows;
}
