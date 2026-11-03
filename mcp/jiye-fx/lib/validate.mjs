import fs from 'node:fs';
import path from 'node:path';

export const SCHEMA_VERSION = 'veil 4.5.2';

// 4.5.2：RenderTypeLayerRegistry 注册的 19 个层 id（同一注册表另有 minecraft: 的 vanilla 视图）
const LAYER_IDS = [
  'shader', 'texture', 'multi_texture', 'transparency', 'depth_test', 'cull', 'lightmap',
  'overlay', 'layering', 'output', 'texturing', 'write_mask', 'line', 'color_logic',
  'patches', 'depth_clamp', 'multisample', 'seamless_cubemap'
];
const LAYER_TYPES = new Set([
  ...LAYER_IDS.map(n => `veil:${n}`),
  ...LAYER_IDS.map(n => `minecraft:${n}`),
  'minecraft:shader' // VanillaShaderLayer
]);
const TRANSPARENCY = new Set(['none', 'additive', 'lightning', 'glint', 'crumbling', 'translucent']);
const DEPTH_TEST = new Set(['always', 'equal', 'lequal', 'less', 'gequal', 'greater', 'notequal', 'never']);
const CULL_FACE = new Set(['none', 'back', 'front', 'front_and_back']);
// VertexFormatCodec.DEFAULT_FORMATS / DEFAULT_BUFFER_SIZES
const RT_FORMATS = new Set([
  'BLIT_SCREEN', 'BLOCK', 'NEW_ENTITY', 'PARTICLE', 'POSITION_COLOR', 'POSITION_COLOR_NORMAL',
  'POSITION_COLOR_LIGHTMAP', 'POSITION_TEX', 'POSITION_TEX_COLOR',
  'POSITION_COLOR_TEX_LIGHTMAP', 'POSITION_TEX_LIGHTMAP_COLOR', 'POSITION_TEX_COLOR_NORMAL'
]);
const RT_MODES = new Set(['POINTS', 'LINES', 'LINE_STRIP', 'TRIANGLE_STRIP', 'TRIANGLE_FAN', 'QUADS', 'TRIANGLES']);
const RT_BUFFER_SIZES = new Set(['BIG', 'SMALL', 'TRANSIENT']);
const STAGE_TYPES = new Set(['veil:blit', 'veil:copy', 'veil:mask', 'veil:depth_function']);
const UNIFORM_TYPES = new Set(['float', 'int', 'mat2', 'mat3', 'mat4']);
const RENDER_STAGES = new Set([
  'after_sky', 'after_solid_blocks', 'after_cutout_mipped_blocks', 'after_cutout_blocks',
  'after_entities', 'after_block_entities', 'after_translucent_blocks', 'after_tripwire_blocks',
  'after_particles', 'after_weather', 'after_level'
]);
// FramebufferAttachmentDefinition.Format（常用子集；全量 90 个，不做穷举）
const FBO_FORMATS = new Set([
  'RED', 'RG', 'RGB', 'BGR', 'RGBA', 'BGRA', 'DEPTH_COMPONENT', 'DEPTH_STENCIL',
  'R8', 'R16', 'RG8', 'RG16', 'RGB4', 'RGB5', 'RGB565', 'RGB8', 'RGB10', 'RGB12', 'RGB16',
  'RGBA2', 'RGBA4', 'RGB5_A1', 'RGBA8', 'RGB10_A2', 'RGBA12', 'RGBA16',
  'SRGB', 'SRGB8', 'SRGB_ALPHA', 'SRGB8_ALPHA8',
  'R16F', 'RG16F', 'RGB16F', 'RGBA16F', 'R32F', 'RG32F', 'RGB32F', 'RGB9_E5', 'RGBA32F',
  'R11F_G11F_B10F', 'DEPTH_COMPONENT16', 'DEPTH_COMPONENT24', 'DEPTH_COMPONENT32',
  'DEPTH_COMPONENT32F', 'DEPTH24_STENCIL8', 'DEPTH32F_STENCIL8'
]);
const PROGRAM_KEYS = new Set([
  'vertex', 'tesselation_control', 'tesselation_evaluation', 'geometry', 'fragment', 'compute',
  'definitions', 'textures', 'blend', 'required_features'
]);
const POST_KEYS = new Set(['stages', 'textures', 'framebuffers', 'renderStage', 'dynamicBuffers', 'priority', 'replace']);

const BUILTIN_UNIFORMS = new Set([
  'VeilRenderTime', 'DiffuseSampler0', 'DiffuseDepthSampler', 'Sampler0', 'Sampler1', 'Sampler2',
  'Sampler3', 'Sampler4', 'Sampler5', 'Sampler6', 'Sampler7', 'Sampler8', 'Sampler9', 'Sampler10',
  'Sampler11', 'ModelViewMat', 'ProjMat', 'TextureMat', 'ScreenSize', 'ColorModulator',
  'GameTime', 'NormalMat', 'ChunkOffset', 'FogStart', 'FogEnd', 'FogColor', 'FogShape',
  'LineWidth', 'GlintAlpha', 'Light0_Direction', 'Light1_Direction'
]);

function walk(dir) {
  const out = [];
  const rec = d => {
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) rec(p);
      else out.push(p);
    }
  };
  rec(dir);
  return out;
}

function stripNs(s) {
  return String(s || '').split(':').pop();
}

function relOf(p, dir) {
  return path.relative(dir, p).split(path.sep).join('/');
}

function glslScan(text) {
  const names = [];
  const decl = new Map();
  const re = /^\s*uniform\s+(?:lowp\s+|mediump\s+|highp\s+)?(sampler2D|samplerCube|float|int|vec2|vec3|vec4|mat2|mat3|mat4|bool)\s+([A-Za-z_][A-Za-z0-9_]*)/gm;
  let m;
  while ((m = re.exec(text))) {
    decl.set(m[2], m[1]);
    names.push(m[2]);
  }
  const body = text.replace(re, '');
  const used = new Set();
  for (const n of names) {
    if (new RegExp('\\b' + n + '\\b').test(body)) used.add(n);
  }
  return { declared: names, types: decl, used };
}

export function validateFiles(files, opts = {}) {
  const errors = [];
  const warnings = [];
  const map = new Map(files.map(f => [f.path.replace(/\\/g, '/'), f.content]));
  const get = p => map.get(p);
  const programJsons = [...map.keys()].filter(p => p.includes('/pinwheel/shaders/program/') && p.endsWith('.json'));
  const rendertypeJsons = [...map.keys()].filter(p => p.includes('/pinwheel/rendertypes/') && p.endsWith('.json'));
  const postJsons = [...map.keys()].filter(p => p.includes('/pinwheel/post/') && p.endsWith('.json'));
  const fboJsons = [...map.keys()].filter(p => p.includes('/pinwheel/framebuffers/') && p.endsWith('.json'));

  const progNames = new Set(programJsons.map(p => path.basename(p, '.json')));
  const fboNames = new Set(fboJsons.map(p => path.basename(p, '.json')));

  const parse = (p, label) => {
    try {
      return JSON.parse(get(p));
    } catch (e) {
      errors.push({ code: 'json-parse', file: p, message: `${label} JSON 解析失败：${e.message}` });
      return null;
    }
  };

  // ---------- programs ----------
  for (const p of programJsons) {
    const dir = path.dirname(p);
    const j = parse(p, 'program');
    if (!j) continue;

    for (const k of Object.keys(j)) {
      if (!PROGRAM_KEYS.has(k)) {
        if (k === 'attributes' || k === 'uniforms') {
          errors.push({
            code: 'program-unknown-key', file: p,
            message: `4.5.2 的 program JSON 没有 "${k}" 这个键（多余键会被忽略、静默不生效）`
          });
        } else {
          warnings.push({ code: 'program-unknown-key', file: p, message: `未知键 ${k}（4.5.2 不认，会被忽略）` });
        }
      }
    }

    for (const which of ['vertex', 'fragment']) {
      const ref = j[which];
      if (!ref) {
        errors.push({ code: 'program-missing-stage', file: p, message: `缺少 ${which}` });
        continue;
      }
      const ns = String(ref).split(':')[0];
      if (ns !== 'veil' && ns !== 'minecraft') {
        const local = `${dir}/${stripNs(ref)}.${which === 'vertex' ? 'vsh' : 'fsh'}`;
        if (!map.has(local)) {
          errors.push({
            code: 'program-missing-file', file: p,
            message: `${which} 指向 ${ref}，但包里没有 ${path.basename(local)}`
          });
        }
      }
    }

    for (const [sampler, spec] of Object.entries(j.textures || {})) {
      if (typeof spec === 'string') continue; // ShaderTextureSource 允许裸 ResourceLocation
      if (!spec || typeof spec !== 'object') {
        errors.push({ code: 'program-texture', file: p, message: `采样器 ${sampler} 的值既不是字符串也不是对象` });
        continue;
      }
      if (spec.type === 'asset') {
        errors.push({
          code: 'program-texture-legacy', file: p,
          message: `采样器 ${sampler} 用了旧版 type:"asset"，4.5.2 只认 location / framebuffer（裸字符串等价于 location）`
        });
      } else if (spec.type && !['location', 'framebuffer'].includes(spec.type)) {
        errors.push({ code: 'program-texture', file: p, message: `采样器 ${sampler} 的 type 非法：${spec.type}` });
      }
      if (spec.type === 'framebuffer') {
        if (!spec.name) errors.push({ code: 'program-texture', file: p, message: `采样器 ${sampler} 是 framebuffer 但没有 name` });
        const seg = String(spec.name || '').split(':');
        const fb = (seg[1] || seg[0]).replace(/:depth$/, '');
        if (!fboNames.has(fb) && !['main', 'post', 'bloom', 'translucent', 'first_person', 'light_inscattering'].includes(fb)) {
          warnings.push({ code: 'program-fbo-missing', file: p, message: `采样器 ${sampler} 指向未在包内定义的 framebuffer ${spec.name}` });
        }
      }
    }

    // 着色器静态检查
    const fsh = j.fragment ? map.get(`${dir}/${stripNs(j.fragment)}.fsh`) : null;
    const vsh = j.vertex ? map.get(`${dir}/${stripNs(j.vertex)}.vsh`) : null;
    for (const [label, src] of [['fsh', fsh], ['vsh', vsh]]) {
      if (!src) continue;
      if (/#version\s+\d/.test(src)) {
        warnings.push({ code: 'glsl-version', file: p, message: `${label} 里出现 #version：Veil 会自己注入版本，建议删掉` });
      }
      if (/\bgl_FragColor\b/.test(src)) {
        errors.push({ code: 'glsl-legacy-out', file: p, message: `${label} 使用 gl_FragColor，Veil 需要 out vec4 fragColor` });
      }
      if (src.includes('#include veil:camera')) {
        errors.push({ code: 'glsl-camera-include', file: p, message: `${label} 用了已废弃的 #include veil:camera，改成 #veil:buffer veil:camera VeilCamera` });
      }
      if (src.includes('VeilCamera.')) {
        // space_helper.glsl 第一行就是 #veil:buffer veil:camera VeilCamera，include 即等于声明
        const cameraDeclared = /#veil:buffer\s+veil:camera\s+VeilCamera/.test(src)
          || /#include\s+veil:space_helper/.test(src);
        if (!cameraDeclared) {
          errors.push({ code: 'glsl-missing-camera', file: p, message: `${label} 使用 VeilCamera 但没有 #veil:buffer veil:camera VeilCamera` });
        }
      }
      if (src.includes('screenToLocalSpace') && !/#include\s+veil:space_helper/.test(src)) {
        errors.push({ code: 'glsl-missing-space-helper', file: p, message: `${label} 使用 screenToLocalSpace 但没有 #include veil:space_helper` });
      }
      const scan = glslScan(src);
      for (const u of scan.declared) {
        if (!scan.used.has(u)) {
          warnings.push({ code: 'uniform-unused', file: p, message: `${label} 声明了 uniform ${u} 但没用到` });
        }
      }
      if (label === 'fsh') {
        const hasOut = /out\s+vec4\s+fragColor/.test(src) || /layout\s*\(\s*location\s*=\s*\d+\s*\)\s*out\s+vec4/.test(src);
        // 输出可能来自 #include 的公共片段（几页的 10 个 *_heaven_sky 就是 2 行，out 在 include 里）
        const viaInclude = /^\s*#include\s+\S+/m.test(src);
        if (!hasOut && !viaInclude) {
          errors.push({ code: 'glsl-no-out', file: p, message: 'fsh 缺少 out vec4 fragColor（或 MRT 的 layout out）' });
        }
        if (/DiffuseDepthSampler|SamplerSceneDepth/.test(src)) {
          // 只有"用深度做折射/软交界"才需要守卫；单纯按深度分级、屏幕抖动都不需要
          const usesRefraction = /\boffset\b|\brefract/i.test(src);
          if (usesRefraction && !/0\.0008/.test(src)) {
            warnings.push({ code: 'depth-no-epsilon', file: p, message: '采样场景深度做折射但没有 0.0008 量级的守卫阈值' });
          }
          if (usesRefraction && !/candidate|cand|separation|softIntersection|sceneDepth/i.test(src)) {
            warnings.push({ code: 'depth-no-guard', file: p, message: '采样场景深度做折射但没有深度守卫/软交界判断' });
          }
        }
        if (!/\bdiscard\b|\breturn\s*;/.test(src)) {
          warnings.push({ code: 'no-early-out', file: p, message: 'fsh 没有 discard/return 早退，全屏效果会白算' });
        }
      }
    }
  }

  // ---------- rendertypes ----------
  const mrtPrograms = new Set();
  for (const p of rendertypeJsons) {
    const j = parse(p, 'rendertype');
    if (!j) continue;
    for (const k of ['format', 'mode', 'bufferSize', 'layers']) {
      if (j[k] === undefined) errors.push({ code: 'rt-missing', file: p, message: `缺少字段 ${k}` });
    }
    if (j.format !== undefined && !RT_FORMATS.has(String(j.format))) {
      errors.push({ code: 'rt-format', file: p, message: `format 非法：${j.format}（4.5.2 只认 ${[...RT_FORMATS].join(' / ')}）` });
    }
    if (j.mode !== undefined && !RT_MODES.has(String(j.mode))) {
      errors.push({ code: 'rt-mode', file: p, message: `mode 非法：${j.mode}` });
    }
    if (j.bufferSize !== undefined && !RT_BUFFER_SIZES.has(String(j.bufferSize))) {
      errors.push({ code: 'rt-buffer-size', file: p, message: `bufferSize 非法：${j.bufferSize}（BIG / SMALL / TRANSIENT）` });
    }
    for (const k of ['affectsCrumbling', 'sort', 'outline']) {
      if (j[k] !== undefined && typeof j[k] !== 'boolean') {
        errors.push({ code: 'rt-bool', file: p, message: `${k} 必须是布尔值` });
      }
    }
    const nested = Array.isArray(j.layers) && j.layers.length && Array.isArray(j.layers[0]);
    if (nested && j.layers.length < 2) {
      errors.push({ code: 'rt-single-pass-nested', file: p, message: 'layers 写成嵌套数组时至少要 2 趟；单趟请写平铺数组' });
    }
    const passes = nested ? j.layers : [j.layers];
    for (let i = 0; i < passes.length; i++) {
      for (const layer of passes[i] || []) {
        if (!layer || !layer.type) {
          errors.push({ code: 'rt-layer', file: p, message: `第 ${i + 1} 趟存在无 type 的层` });
          continue;
        }
        if (!LAYER_TYPES.has(layer.type)) {
          errors.push({
            code: 'rt-layer-unknown', file: p,
            message: `未知层类型 ${layer.type}（可用：veil:shader / minecraft:depth_test / minecraft:cull / minecraft:write_mask / minecraft:transparency / minecraft:output / minecraft:texture / minecraft:lightmap / minecraft:overlay ...）`
          });
        }
        if (layer.type === 'veil:shader') {
          if (!layer.name) errors.push({ code: 'rt-shader-name', file: p, message: 'veil:shader 层缺少 name' });
          const n = stripNs(layer.name);
          if (n && !progNames.has(n)) {
            errors.push({ code: 'rt-shader-missing', file: p, message: `层引用 program ${layer.name}，但包内没有 ${n}.json` });
          }
        }
        if (layer.type === 'minecraft:transparency' && !TRANSPARENCY.has(layer.mode)) {
          errors.push({ code: 'rt-transparency', file: p, message: `transparency mode 非法：${layer.mode}` });
        }
        if (layer.type === 'minecraft:depth_test' && layer.mode !== undefined && !DEPTH_TEST.has(String(layer.mode))) {
          errors.push({ code: 'rt-depth-test', file: p, message: `depth_test mode 非法：${layer.mode}` });
        }
        if (layer.type === 'minecraft:cull' && layer.face !== undefined && !CULL_FACE.has(String(layer.face))) {
          errors.push({ code: 'rt-cull', file: p, message: `cull face 非法：${layer.face}` });
        }
        if (layer.type === 'minecraft:output' && layer.framebuffer === 'veil:bloom') {
          const pass = passes[i];
          const additive = pass.some(l => l.type === 'minecraft:transparency' && l.mode === 'additive');
          if (!additive) warnings.push({ code: 'bloom-not-additive', file: p, message: 'bloom 趟建议用 additive，否则会重复压暗' });
        }
        if (layer.type === 'minecraft:output' && layer.framebuffer && layer.framebuffer !== 'veil:bloom') {
          const pass = passes[i];
          const shaderLayer = pass.find(l => l.type === 'veil:shader' || l.type === 'minecraft:shader');
          if (shaderLayer) mrtPrograms.add(stripNs(shaderLayer.name));
        }
      }
    }
    const hasShader = passes.some(pass => (pass || []).some(l => l.type === 'veil:shader' || l.type === 'minecraft:shader'));
    if (!hasShader) errors.push({ code: 'rt-no-shader', file: p, message: '没有任何 shader 层' });
  }

  // 写 MRT 的 program 必须有 location 0/1 输出
  for (const n of mrtPrograms) {
    const pj = programJsons.find(x => path.basename(x, '.json') === n);
    if (!pj) continue;
    const j = JSON.parse(get(pj));
    const fsh = j.fragment ? map.get(`${path.dirname(pj)}/${stripNs(j.fragment)}.fsh`) : null;
    if (fsh && !/layout\s*\(\s*location\s*=\s*1\s*\)\s*out\s+vec4/.test(fsh)) {
      errors.push({ code: 'glsl-mrt', file: pj, message: '这个 program 的 rendertype 写多附件 FBO，但 fsh 没有 layout(location=1) out' });
    }
  }

  // ---------- posts ----------
  for (const p of postJsons) {
    const j = parse(p, 'post');
    if (!j) continue;
    for (const k of Object.keys(j)) {
      if (!POST_KEYS.has(k)) {
        warnings.push({ code: 'post-unknown-key', file: p, message: `4.5.2 的 post JSON 没有 "${k}" 这个键` });
      }
    }
    if (!Array.isArray(j.stages) || !j.stages.length) {
      errors.push({ code: 'post-no-stages', file: p, message: '缺少 stages' });
      continue;
    }
    for (const st of j.stages) {
      if (!STAGE_TYPES.has(st.type)) {
        errors.push({ code: 'post-stage-unknown', file: p, message: `未知 stage 类型 ${st.type}（veil:blit / veil:copy / veil:mask / veil:depth_function）` });
        continue;
      }
      if (st.type === 'veil:blit') {
        if (!st.shader) errors.push({ code: 'post-stage-no-shader', file: p, message: 'veil:blit 缺少 shader' });
        else if (!progNames.has(stripNs(st.shader))) {
          errors.push({ code: 'post-shader-missing', file: p, message: `stage 引用 program ${st.shader}，但包内没有` });
        }
        if (st.in && st.out && st.in === st.out) {
          errors.push({ code: 'post-stage-same-fbo', file: p, message: `in 与 out 不能相同（${st.in}）` });
        }
        for (const [un, uv] of Object.entries(st.uniforms || {})) {
          if (!uv || !UNIFORM_TYPES.has(String(uv.type))) {
            errors.push({ code: 'post-uniform-type', file: p, message: `stage uniform ${un} 的 type 非法（float/int/mat2/mat3/mat4）` });
          } else if (uv.value === undefined) {
            errors.push({ code: 'post-uniform-value', file: p, message: `stage uniform ${un} 缺少 value` });
          }
        }
      }
      if (st.type === 'veil:mask') {
        for (const k of ['red', 'green', 'blue', 'alpha', 'depth']) {
          if (st[k] !== undefined && typeof st[k] !== 'boolean') {
            errors.push({ code: 'post-mask', file: p, message: `veil:mask 的 ${k} 必须是布尔值` });
          }
        }
      }
    }
    if (j.renderStage !== undefined && !RENDER_STAGES.has(String(j.renderStage))) {
      errors.push({ code: 'post-stage-name', file: p, message: `renderStage 非法：${j.renderStage}` });
    }
    if (!j.renderStage) {
      warnings.push({ code: 'post-no-stage', file: p, message: '没有 renderStage：管线只在 add() 后、且在默认时机执行（需要在 Java 里 add）' });
    }
    for (const [name, def] of Object.entries(j.framebuffers || {})) {
      if (!def || typeof def !== 'object') {
        errors.push({ code: 'post-fbo', file: p, message: `framebuffers.${name} 不是对象` });
      }
    }
  }

  // ---------- framebuffers ----------
  for (const p of fboJsons) {
    const j = parse(p, 'framebuffer');
    if (!j) continue;
    const full = Array.isArray(j.color_buffers);
    if (full) {
      if (!j.color_buffers.length) {
        errors.push({ code: 'fbo-no-color', file: p, message: 'color_buffers 为空（至少 1 个）' });
      }
      const names = j.color_buffers.map(c => c && c.name).filter(Boolean);
      if (new Set(names).size !== names.length) {
        errors.push({ code: 'fbo-dup-name', file: p, message: 'color_buffers 名字重复' });
      }
      for (const c of j.color_buffers) {
        if (c && c.format !== undefined && !FBO_FORMATS.has(String(c.format))) {
          errors.push({ code: 'fbo-format', file: p, message: `附件 format 非法：${c.format}` });
        }
        if (c && c.type !== undefined && !['texture', 'render_buffer'].includes(String(c.type).toLowerCase())) {
          errors.push({ code: 'fbo-type', file: p, message: `附件 type 非法：${c.type}（texture / render_buffer）` });
        }
      }
    } else {
      // COMPACT：单附件，字段直接写在顶层
      if (j.format !== undefined && !FBO_FORMATS.has(String(j.format))) {
        errors.push({ code: 'fbo-format', file: p, message: `format 非法：${j.format}` });
      }
      warnings.push({ code: 'fbo-compact', file: p, message: '这是 COMPACT 单附件写法（没有 color_buffers）；多附件请用 color_buffers 数组' });
    }
    const depth = j.depth;
    if (depth !== undefined && typeof depth === 'object' && depth !== null) {
      if (depth.format !== undefined && !FBO_FORMATS.has(String(depth.format))) {
        errors.push({ code: 'fbo-depth-format', file: p, message: `depth.format 非法：${depth.format}` });
      }
    } else if (depth !== undefined && typeof depth !== 'boolean') {
      errors.push({ code: 'fbo-depth', file: p, message: 'depth 只能是 true / false / 附件对象' });
    }
  }

  if (opts.family) {
    const fam = opts.family;
    for (const p of [...rendertypeJsons, ...programJsons, ...postJsons]) {
      const b = path.basename(p, '.json');
      if (!b.startsWith(fam)) {
        warnings.push({ code: 'naming', file: p, message: `文件名 ${b} 不以家族名 ${fam} 开头` });
      }
    }
  }

  return {
    ok: errors.length === 0,
    schema: SCHEMA_VERSION,
    errors,
    warnings,
    summary: {
      files: map.size,
      programs: programJsons.length,
      rendertypes: rendertypeJsons.length,
      posts: postJsons.length,
      framebuffers: fboJsons.length
    }
  };
}

export function validateDir(dir, opts = {}) {
  if (!fs.existsSync(dir)) throw new Error('目录不存在：' + dir);
  const all = walk(dir);
  const files = all
    .filter(p => /\.(json|fsh|vsh|glsl)$/i.test(p))
    .map(p => ({ path: relOf(p, dir), content: fs.readFileSync(p, 'utf8') }));
  return validateFiles(files, opts);
}
