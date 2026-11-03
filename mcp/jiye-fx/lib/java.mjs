import { pascal } from './templates.mjs';

const HEADER = `// 由几页特效工坊（jiye-fx）生成。
// API 依据 Veil 1.21 wiki + 源码（VeilRenderType.get / ShaderManager.getShader /
// ShaderUniformAccess / VeilEventPlatform.preVeilPostProcessing）。
// 如果你的 Veil 版本不同，以 jar 内方法签名为准；所有 getUniform 都必须判空。`;

function imports(kind, extra = []) {
  const base = ['import net.minecraft.resources.ResourceLocation;'];
  const geo = [
    'import com.mojang.blaze3d.vertex.PoseStack;',
    'import com.mojang.blaze3d.vertex.VertexConsumer;',
    'import foundry.veil.api.client.render.VeilRenderSystem;',
    'import foundry.veil.api.client.render.rendertype.VeilRenderType;',
    'import foundry.veil.api.client.render.shader.program.ShaderProgram;',
    'import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;',
    'import net.minecraft.client.renderer.MultiBufferSource;',
    'import net.minecraft.client.renderer.RenderType;'
  ];
  const post = [
    'import foundry.veil.api.client.render.VeilRenderSystem;',
    'import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;',
    'import foundry.veil.api.client.render.post.PostPipeline;'
  ];
  const isPost = kind === 'screen_post' || kind === 'domain_sphere';
  return [...(isPost ? [...base, ...post] : [...geo, ...base]), ...extra].join('\n');
}

function uniformPushBody(spec, targetVar = 'shader') {
  const lines = [];
  for (const u of spec.uniforms) {
    if (u.type === 'vec3') {
      lines.push(`        setVec3(${targetVar}, "${u.name}", ${u.name}X, ${u.name}Y, ${u.name}Z);`);
    } else if (u.type === 'int') {
      lines.push(`        setInt(${targetVar}, "${u.name}", ${u.name});`);
    } else {
      lines.push(`        setFloat(${targetVar}, "${u.name}", ${u.name});`);
    }
  }
  return lines.join('\n');
}

function stateFields(spec) {
  const f = v => {
    const n = Number(v);
    return Number.isFinite(n) ? `${n.toFixed(4)}f` : '0.0f';
  };
  return spec.uniforms.map(u => {
    if (u.type === 'vec3') {
      const d = Array.isArray(u.default) ? u.default : [0, 0, 0];
      return `    private static float ${u.name}X = ${f(d[0])}, ${u.name}Y = ${f(d[1])}, ${u.name}Z = ${f(d[2])};`;
    }
    if (u.type === 'int') return `    private static int ${u.name} = ${Math.trunc(Number(u.default) || 0)};`;
    return `    private static float ${u.name} = ${f(u.default)};`;
  }).join('\n');
}

function setters(spec) {
  const P = pascal(spec.family);
  const out = [];
  for (const u of spec.uniforms) {
    if (u.type === 'vec3') {
      out.push(`    public static void set${u.name}(float x, float y, float z) {
        ${u.name}X = x; ${u.name}Y = y; ${u.name}Z = z;
    }`);
    } else if (u.type === 'int') {
      out.push(`    public static void set${u.name}(int v) { ${u.name} = v; }`);
    } else {
      out.push(`    public static void set${u.name}(float v) { ${u.name} = v; }`);
    }
  }
  return out.join('\n\n');
}

function helpers() {
  return `    private static void setFloat(ShaderProgram s, String n, float v) {
        ShaderUniformAccess u = s.getUniform(n);
        if (u != null && u.isValid()) u.setFloat(v);
    }

    private static void setInt(ShaderProgram s, String n, int v) {
        ShaderUniformAccess u = s.getUniform(n);
        if (u != null && u.isValid()) u.setInt(v);
    }

    private static void setVec3(ShaderProgram s, String n, float x, float y, float z) {
        ShaderUniformAccess u = s.getUniform(n);
        if (u != null && u.isValid()) u.setVector(x, y, z);
    }`;
}

function geometryHelper(spec) {
  const fmt = spec.kind === 'entity_aura' || spec.kind === 'item_glow'
    ? 'NEW_ENTITY'
    : spec.kind === 'skybox' ? 'POSITION_COLOR' : 'POSITION_TEX_COLOR';
  if (spec.kind === 'entity_aura' || spec.kind === 'item_glow') {
    return `    /**
     * 提交一个面向相机的四边形（示例拓扑；真实模型请换成你的 mesh）。
     * NEW_ENTITY 顶点：pos + color + uv + light + overlay + normal。
     */
    public static void quad(PoseStack pose, MultiBufferSource buffers,
                            float x, float y, float z, float halfSize,
                            float r, float g, float b, float a, int light, int overlay) {
        RenderType rt = VeilRenderType.get(RENDER_TYPE);
        if (rt == null) return;
        VertexConsumer vc = buffers.getBuffer(rt);
        PoseStack.Pose p = pose.last();
        vc.addVertex(p, x - halfSize, y - halfSize, z).setColor(r, g, b, a)
          .setUv(0.0f, 1.0f).setLight(light).setOverlay(overlay)
          .setNormal(p, 0.0f, 0.0f, 1.0f);
        vc.addVertex(p, x - halfSize, y + halfSize, z).setColor(r, g, b, a)
          .setUv(0.0f, 0.0f).setLight(light).setOverlay(overlay)
          .setNormal(p, 0.0f, 0.0f, 1.0f);
        vc.addVertex(p, x + halfSize, y + halfSize, z).setColor(r, g, b, a)
          .setUv(1.0f, 0.0f).setLight(light).setOverlay(overlay)
          .setNormal(p, 0.0f, 0.0f, 1.0f);
        vc.addVertex(p, x + halfSize, y - halfSize, z).setColor(r, g, b, a)
          .setUv(1.0f, 1.0f).setLight(light).setOverlay(overlay)
          .setNormal(p, 0.0f, 0.0f, 1.0f);
    }`;
  }
  if (fmt === 'POSITION_COLOR') {
    return `    /** 天空盒：一般由 SkyRenderer 在天空阶段提交一个以相机为中心的 cube。 */
    public static void cube(PoseStack pose, MultiBufferSource buffers,
                            float r, float g, float b, float a, float radius) {
        RenderType rt = VeilRenderType.get(RENDER_TYPE);
        if (rt == null) return;
        VertexConsumer vc = buffers.getBuffer(rt);
        // TODO: 用 6 个面或一个 cube mesh，顶点写入 world 位置
        // vc.addVertex(pose.last(), x, y, z).setColor(r, g, b, a);
    }`;
  }
  return `    /** 提交一个世界坐标四边形（示例拓扑）。UV 由调用方给 0..1。 */
    public static void quad(PoseStack pose, MultiBufferSource buffers,
                            float x, float y, float z, float halfSize,
                            float r, float g, float b, float a, int light) {
        RenderType rt = VeilRenderType.get(RENDER_TYPE);
        if (rt == null) return;                 // 加载失败就当没画
        VertexConsumer vc = buffers.getBuffer(rt);
        PoseStack.Pose p = pose.last();
        vc.addVertex(p, x - halfSize, y - halfSize, z).setColor(r, g, b, a).setUv(0.0f, 1.0f).setLight(light);
        vc.addVertex(p, x - halfSize, y + halfSize, z).setColor(r, g, b, a).setUv(0.0f, 0.0f).setLight(light);
        vc.addVertex(p, x + halfSize, y + halfSize, z).setColor(r, g, b, a).setUv(1.0f, 0.0f).setLight(light);
        vc.addVertex(p, x + halfSize, y - halfSize, z).setColor(r, g, b, a).setUv(1.0f, 1.0f).setLight(light);
    }`;
}

export function buildJava(spec) {
  const P = pascal(spec.family);
  const cls = P + 'Fx';
  if (spec.kind === 'screen_post' || spec.kind === 'domain_sphere') {
    return `package ${spec.javaPackage};

${HEADER}

${imports(spec.kind, [
  'import foundry.veil.platform.VeilEventPlatform;'
])}

/**
 * ${spec.title}（${spec.kind}）—— 屏幕后处理。
 * JSON 管线必须在客户端初始化时 add() 一次（见下），之后在 ${spec.stage} 阶段执行；这里只负责推 uniform。
 */
public final class ${cls} {
    public static final String MODID = "${spec.modid}";
    public static final ResourceLocation PIPELINE = ResourceLocation.fromNamespaceAndPath(MODID, "${spec.family}");

${stateFields(spec)}

    private ${cls}() {}

    /** 在客户端初始化时调用一次。 */
    public static void register() {
        // ★ 必须 add()：Veil 每帧只遍历 activePipelines，JSON 里写 renderStage **不会**自动激活，
        //   它只是「在哪个阶段跑」的过滤条件。漏了这一步 = 日志显示已加载、画面永远不动。
        VeilRenderSystem.renderer().getPostProcessingManager().add(${spec.priority}, PIPELINE);

        VeilEventPlatform.INSTANCE.preVeilPostProcessing((pipelineName, pipeline, context) -> {
            if (!PIPELINE.equals(pipelineName)) return;
${spec.uniforms.map(u => u.type === 'vec3'
      ? `            setVec3(pipeline, "${u.name}", ${u.name}X, ${u.name}Y, ${u.name}Z);`
      : u.type === 'int'
        ? `            setInt(pipeline, "${u.name}", ${u.name});`
        : `            setFloat(pipeline, "${u.name}", ${u.name});`).join('\n')}
        });
    }

${setters(spec)}

    private static void setFloat(foundry.veil.api.client.render.post.PostPipeline p, String n, float v) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null && u.isValid()) u.setFloat(v);
    }

    private static void setInt(foundry.veil.api.client.render.post.PostPipeline p, String n, int v) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null && u.isValid()) u.setInt(v);
    }

    private static void setVec3(foundry.veil.api.client.render.post.PostPipeline p, String n, float x, float y, float z) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null && u.isValid()) u.setVector(x, y, z);
    }
}
`;
  }

  if (spec.kind === 'volume_field') {
    return `package ${spec.javaPackage};

${HEADER}

${imports(spec.kind)}

/**
 * ${spec.title}（volume_field）—— 两趟：
 * 1) 几何用 ${spec.family}_field rendertype 渲进 FBO（写 MRT）；
 * 2) composite 由 post JSON 自动全屏合成，这里只推 uniform。
 */
public final class ${cls} {
    public static final String MODID = "${spec.modid}";
    public static final ResourceLocation FIELD_RT = ResourceLocation.fromNamespaceAndPath(MODID, "${spec.family}_field");
    public static final ResourceLocation COMPOSITE_SHADER = ResourceLocation.fromNamespaceAndPath(MODID, "${spec.family}_composite");

${stateFields(spec)}

    private ${cls}() {}

${geometryHelper(spec).replace(/RENDER_TYPE/g, 'FIELD_RT')}

    /** 在客户端初始化时调用一次：把 composite 那条管线加进活跃表（否则它永远不会执行）。 */
    public static void register() {
        VeilRenderSystem.renderer().getPostProcessingManager()
                .add(${spec.priority}, ResourceLocation.fromNamespaceAndPath(MODID, "${spec.family}"));
    }

    /** 每帧在提交场几何之后调用（composite 会用到）。 */
    public static void pushUniforms() {
        ShaderProgram shader = VeilRenderSystem.renderer().getShaderManager().getShader(COMPOSITE_SHADER);
        if (shader == null) return;
${uniformPushBody(spec)}
    }

${setters(spec)}

${helpers()}
}
`;
  }

  return `package ${spec.javaPackage};

${HEADER}

${imports(spec.kind)}

/**
 * ${spec.title}（${spec.kind}）—— 几何提交 + uniform 推送。
 */
public final class ${cls} {
    public static final String MODID = "${spec.modid}";
    public static final ResourceLocation RENDER_TYPE = ResourceLocation.fromNamespaceAndPath(MODID, "${spec.family}");
    public static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(MODID, "${spec.family}");

${stateFields(spec)}

    private ${cls}() {}

${geometryHelper(spec)}

    /** 每帧在提交几何之前推 uniform。 */
    public static void pushUniforms() {
        ShaderProgram shader = VeilRenderSystem.renderer().getShaderManager().getShader(SHADER);
        if (shader == null) return;
${uniformPushBody(spec)}
    }

${setters(spec)}

${helpers()}
}
`;
}

export function buildJavaReadme(spec) {
  const P = pascal(spec.family);
  const cls = P + 'Fx';
  const call = spec.kind === 'screen_post' || spec.kind === 'domain_sphere'
    ? `${cls}.register();   // 客户端初始化一次`
    : `// 每帧：先推 uniform，再提交几何\n${cls}.pushUniforms();\n${cls}.quad(pose, buffers, x, y, z, size, r, g, b, a, light);`;
  return `# ${spec.family} 接入步骤

1. 资源包路径（已生成）：\`assets/${spec.modid}/pinwheel/...\`
2. Java 文件：\`${spec.javaPackage.replace(/\./g, '/')}/${cls}.java\`
3. 调用点：
   \`\`\`java
   ${call}
   \`\`\`
4. 验证：
   - F3+T 热重载后${spec.kind === 'volume_field' ? '先 \`/veilc buffers enable\` 看 FBO，再看画面' : '直接看画面（post 类会立刻叠上去）'}；
   - 强度先设为 0 确认零开销早退，再逐步拉满；
   - 若画面全黑：${spec.kind === 'volume_field' ? '先查 FBO 是否写入、composite 的 in/out 是否对' : '先查 post stage 的 shader 名与 program 文件是否一致'}。
`;
}
