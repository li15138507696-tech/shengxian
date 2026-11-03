// 空间效果（世界几何）—— 几页 94 个 rendertype 的真实分类与做法。
//
// 数据来源：tools/scan_jiye_space.py 扫 94 个 rendertype + 64 个 vsh 得出，判据全部是代码记号
// （ModelViewMat / CameraPosition / clip.z=clip.w），不是主观归类；每条都带 refs 可回查。
//
// 一句话结论：**这批资源 94 个 rendertype 里只有 32 个 post，其余全是世界几何**；
// 几何里 63 个用「世界坐标、shader 自己减相机」，18 个用「相机相对坐标」，9 个走原版实体格式，
// 4 个把 z 钉在远平面做天空。屏幕后处理是少数派。

export const SPACE_CONVENTIONS = [
  {
    id: 'world',
    title: '世界坐标（shader 自己减相机）',
    count: 63,
    how: 'CPU 直接写世界坐标；vsh 里 `ProjMat * ViewMat * vec4(Position - CameraPosition, 1.0)`',
    refs: ['water_enclosure', 'ink_ribbon', 'combat_soft_shape', 'liu_feng_surface'],
    why: '最直观，且能直接复用同一份 CPU 网格（不必每帧重算相机偏移）。'
  },
  {
    id: 'camera-relative',
    title: '相机相对坐标（CPU 先减相机）',
    count: 18,
    how: 'CPU 写 `world - cameraPos`；vsh 里 `vWorldPos = Position + CameraPosition` 还原',
    refs: ['iron_wall_bell', 'sky_vortex', 'wander_world_volume', 'aperture_manifestation'],
    why: '远坐标转 float 会抖（十万格外），先减相机精度更好；天空/大尺度必用。'
  },
  {
    id: 'pose',
    title: 'PoseStack 坐标（ModelViewMat）',
    count: 9,
    how: '原版实体格式 `NEW_ENTITY` / 带 Normal；vsh 用 `ModelViewMat`',
    refs: ['water_clothing_shell', 'immortal_gu_item_aura', 'ink_ribbon(旧)'],
    why: '要跟随实体骨骼/物品模型，必须吃 PoseStack；代价是不能跨实体批处理。'
  },
  {
    id: 'sky',
    title: '远平面（天空）',
    count: 4,
    how: '`clip.z = clip.w * 0.9995`（几页用 0.9995，不少教程写 0.9998）',
    refs: ['sky_vortex', 'aperture_sky', 'baohuangtian_sky', 'red_heaven_sky'],
    why: '低渲染距离时天穹不被裁掉；已写入深度的山体仍能自然遮挡它。'
  }
];

export const SPACE_SHAPES = [
  {
    id: 'sphere-shell',
    title: '球壳 / 钟罩（灾劫压迫、仙窍、护罩）',
    coords: 'world',
    format: 'POSITION_TEX_COLOR',
    attrs: ['Position vec3（世界）', 'UV0 vec2（0..1 绕球）', 'Color vec4'],
    mesh: '经纬球：SEG=24 绕 Y、RING=12 南北；每格 4 顶点，UV=(seg/SEG, ring/RING)。半径 6 格时每段约 1.5 格，够圆。',
    normal: 'vsh 里 `normalize(Position - ShellCenter)` —— 球壳的法线就是径向，**不占顶点属性**。',
    fresnel: '`pow(1.0 - abs(dot(normal, viewDir)), 2~3)` —— 掠射处亮，壳才有「厚度」。',
    refs: ['iron_wall_bell', 'aperture_manifestation(uMode=2/3/5 的球面位移)', 'water_enclosure'],
    pitfalls: [
      'cull 必须 none：玩家/相机常常在球内，背面被剔掉就什么也看不见。',
      'write_mask.depth=false：壳不该挡后面的东西；depth_test 留 lequal 才能被山挡住。',
      '别用 sort=true 去「修」透明排序 —— 单个壳只有一层，排序没用还费 CPU。'
    ]
  },
  {
    id: 'volume-box',
    title: '体积盒 / 场（风墙、气团、结界）',
    coords: 'world',
    format: 'POSITION_TEX_COLOR',
    attrs: ['Position vec3（世界）', 'UV0 vec2（截面坐标）', 'Color vec4'],
    mesh: '一个包住效果范围的盒（或圆柱），只要 8~32 个三角形；真正的「体积」由两趟渲染给。',
    pipeline: 'field 趟写 MRT（法线+覆盖 / 材质）→ composite 全屏按深度守卫采样。',
    refs: ['fan_wind_field', 'hua_qi_field', 'iron_wall_field', 'wander_world_wave_field'],
    pitfalls: [
      '场几何要写进 FBO（rendertype 带 output 层），不是画在屏幕上。',
      'fsh 必须有 layout(location=0/1) 两个 out，否则第二个附件永远是空的。',
      '深度守卫阈值 0.0008 是几页四套统一口径，别自创。'
    ]
  },
  {
    id: 'billboard-quad',
    title: '贴片 / 面片（斩击、雨、雷、环、眼）',
    coords: 'world',
    format: 'POSITION_TEX_COLOR',
    attrs: ['Position vec3（世界，四个角）', 'UV vec2（0..1 或已中心化）', 'Color vec4'],
    mesh: '一个四边形（QUADS/TRIANGLES）。朝向由 CPU 决定（朝相机、朝地面、沿速度方向）。',
    uv: '`combat_soft_shape` 把 UV 中心化成 -1..1（`UV*2-1`），fsh 才能用对称软遮罩；不中心化就只能用 0..1 硬边。',
    refs: ['dan_dao_slash', 'lang_jian_wave', 'piercing_rain', 'tribulation_lightning', 'xing_mou_eye'],
    pitfalls: [
      'billboard 的朝向要在 CPU 算好（拿 Camera 的 right/up 叉乘），别指望 shader 里补。',
      '`discard` + 半透明：先 discard 再混合，能省大量 overdraw。',
      '一个面片只画一层，发光边靠第二趟 additive → veil:bloom，不要在同 shader 里叠两次。'
    ]
  },
  {
    id: 'ribbon-strip',
    title: '丝带 / 拖尾（剑气、墨线、时间河）',
    coords: 'world',
    format: 'POSITION_COLOR（无 UV）',
    attrs: ['Position vec3（世界）', 'Color vec4（含沿路径的宽度/透明渐变）'],
    mesh: 'TRIANGLE_STRIP：沿路径每点按切线×相机方向外扩成左右两点；宽度与 alpha 烘进顶点色。',
    refs: ['ink_ribbon', 'spring_autumn_time_river', 'wander_world_trail_art'],
    pitfalls: [
      '没有 UV：所有「纹理」都得从 Position/顶点色里造，别在 fsh 里找 vUv。',
      'strip 的拐点要限制最小段长，否则急转弯处会自交成尖刺。',
      '顶点色 alpha 已经承担了「沿路径淡出」，fsh 再乘一次会过快消失。'
    ]
  },
  {
    id: 'sky-dome',
    title: '天穹 / 天象（九重天、天涡、宝黄天）',
    coords: 'camera-relative',
    format: 'POSITION_COLOR（多数）/ POSITION_TEX_COLOR',
    attrs: ['Position vec3（相机相对）', 'Color vec4', 'UV（可选）'],
    mesh: '以相机为心的球/半球，或用 clip.z 钉远平面的全屏四边形 + 射线反算。',
    refs: ['sky_vortex', 'aperture_sky', 'baohuangtian_sky', 'red_heaven_sky', 'white_heaven_sky'],
    pitfalls: [
      'Position 用相机相对坐标（远坐标 float 精度不够，天穹会抖）。',
      '`clip.z = clip.w * 0.9995`：写 0.9998 也常见，但**必须 < 1.0**，等于 1 会被近裁剪吃掉。',
      '十个 `*_heaven_sky` 共用同一份 fsh，颜色全靠 uniform —— 别为每种天色复制 shader。'
    ]
  },
  {
    id: 'ground-ring',
    title: '地面环 / 法阵 / 领地边线',
    coords: 'world',
    format: 'POSITION_TEX_COLOR',
    attrs: ['Position vec3（世界，贴地 y+0.02）', 'UV vec2（极坐标扫过）', 'Color vec4'],
    mesh: '圆环用三角带：内外两圈点交替；或用一个 quad + 极坐标 UV。',
    refs: ['zheng_tiger_vein', 'datong_wind_boundary', 'xing_mou_eye'],
    pitfalls: [
      '贴地要抬 0.02~0.05，否则和方块面 z-fighting。',
      'depth_test 用 lequal，别用 always —— always 会画穿地形。',
      '环的粗细随距离变化要按世界尺度算，别用屏幕像素。'
    ]
  },
  {
    id: 'entity-attach',
    title: '实体挂件（翅膀、衣饰、光环、物品辉光）',
    coords: 'pose',
    format: 'NEW_ENTITY（或 POSITION_TEX_COLOR_NORMAL）',
    attrs: ['Position', 'Color', 'UV0', 'UV1(overlay)', 'UV2(lightmap)', 'Normal（部分）'],
    mesh: '挂在实体渲染器里：用 PoseStack 变换到骨骼/身体位置，再写本地坐标。',
    refs: ['water_clothing_shell', 'water_clothing_aura', 'immortal_gu_item_aura', 'lei_yi_wing'],
    pitfalls: [
      'NEW_ENTITY 必须带 lightmap 与 overlay 两个层（rendertype 里），否则实体会亮得发白。',
      '物品轮廓辉光要用 Sampler0=blocks atlas，再用 alpha 邻域差取轮廓，不是画一个更大的 quad。',
      '同一实体多件挂件要合成一次提交，分开画会互相盖。'
    ]
  },
  {
    id: 'water-body',
    title: '水体 / 水牢 / 水衣',
    coords: 'world',
    format: 'POSITION_TEX_COLOR_NORMAL',
    attrs: ['Position vec3（世界）', 'UV vec2', 'Color vec4', 'Normal vec3'],
    mesh: '包住水域的盒/球，带真实法线（水面要折射/反射方向）。',
    refs: ['wander_world_water', 'water_enclosure', 'water_enclosure_close', 'jellyfish_habitat_mist'],
    pitfalls: [
      '有 Normal 的格式才能做法线扰动；只靠 dFdx 求法线在水面上会一片噪点。',
      '水面要写 depth（write_mask.depth=true），否则水下的东西会盖在水上。',
      '折射偏移必须乘深度差并夹住上限，否则近处会拉出长条。'
    ]
  },
  {
    id: 'mote-cloud',
    title: '墨点 / 尘埃 / 雾团（小粒子群）',
    coords: 'world',
    format: 'POSITION_TEX_COLOR',
    attrs: ['Position vec3', 'UV vec2（quad 内）', 'Color vec4'],
    mesh: '每个「点」是一个朝相机的小 quad（4 顶点）；几十到几百个，CPU 端算位置。',
    refs: ['ink_mote', 'ascension_mist', 'ink_cocoon', 'ink_fusion'],
    pitfalls: [
      '点位每帧变就别缓存 MeshData，直接重建；量级控制在几百以内。',
      '用 additive 混合，别开 sort（几百个点的排序不值）。',
      '想「跟着实体飘」就在 CPU 端叠一个世界偏移，不要改 vsh。'
    ]
  },
  {
    id: 'pbr-model',
    title: 'PBR 模型（剑蛟、泥蟹）',
    coords: 'pose',
    format: '独立 gzsm/glTF + 三张图',
    attrs: ['Position', 'Normal', 'UV0', 'Color', '骨骼/材质通道'],
    mesh: '外部模型（本仓库是私有 .gzsm；公开方案用 glTF/OBJ）。',
    refs: ['sword_jiaolong_material', 'mud_summoning_crab_material'],
    pitfalls: [
      '这不是 shader 能解决的：没有 mesh 就做不出来，工坊只给指导不生成模型。',
      'PBR 要 baseColor / normal / metallic-roughness 三张图，缺一张就会像塑料。'
    ]
  }
];

/**
 * 提交侧骨架 —— 直接可抄。所有骨架都遵守同一条：
 * **Tesselator + RenderType.draw(MeshData)**，不需要 PostProcessingManager.add()。
 */
export const SPACE_JAVA = [
  {
    id: 'world-quad',
    title: '世界坐标四边形（贴片/斩击）',
    code: `RenderType rt = VeilRenderType.get(RT_ID);         // 每帧取，可空
if (rt == null) return;
ShaderProgram program = VeilRenderSystem.setShader(PROGRAM_ID);  // 先绑再写 uniform
if (program != null) { /* setUniform... */ }

BufferBuilder b = Tesselator.getInstance().begin(rt.mode(), rt.format());
// 世界坐标直接写；vsh 里会减 CameraPosition
b.addVertex(x0, y0, z0).setColor(r, g, b, a).setUv(0f, 0f);
b.addVertex(x1, y1, z1).setColor(r, g, b, a).setUv(0f, 1f);
b.addVertex(x2, y2, z2).setColor(r, g, b, a).setUv(1f, 1f);
b.addVertex(x3, y3, z3).setColor(r, g, b, a).setUv(1f, 0f);
MeshData mesh = b.build();
if (mesh != null) rt.draw(mesh);`
  },
  {
    id: 'sphere',
    title: '球壳（经纬球）',
    code: `// 半径 r、球心 (cx,cy,cz)，SEG=24 / RING=12
BufferBuilder b = Tesselator.getInstance().begin(rt.mode(), rt.format());
for (int ring = 0; ring < RING; ring++) {
    double p0 = Math.PI * ring / RING, p1 = Math.PI * (ring + 1) / RING;
    float v0 = (float) ring / RING, v1 = (float) (ring + 1) / RING;
    for (int seg = 0; seg < SEG; seg++) {
        double t0 = 2 * Math.PI * seg / SEG, t1 = 2 * Math.PI * (seg + 1) / SEG;
        float u0 = (float) seg / SEG, u1 = (float) (seg + 1) / SEG;
        put(b, cx, cy, cz, r, p0, t0, u0, v0, a);
        put(b, cx, cy, cz, r, p1, t0, u0, v1, a);
        put(b, cx, cy, cz, r, p1, t1, u1, v1, a);
        put(b, cx, cy, cz, r, p0, t1, u1, v0, a);
    }
}
// put(): (cx + r*sin(phi)*cos(theta), cy + r*cos(phi), cz + r*sin(phi)*sin(theta))`
  },
  {
    id: 'ribbon',
    title: '丝带（TRIANGLE_STRIP）',
    code: `// 路径点 pts[]，每点切线 tangent；side = normalize(cross(tangent, toCamera)) * halfWidth
BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, rt.format());
for (int i = 0; i < pts.size(); i++) {
    Vec3 p = pts.get(i);
    Vec3 side = sides.get(i);          // CPU 端算好
    float fade = 1f - (float) i / pts.size();   // 沿路径淡出 → 顶点色
    b.addVertex(p.x - side.x, p.y - side.y, p.z - side.z).setColor(1f, 1f, 1f, fade);
    b.addVertex(p.x + side.x, p.y + side.y, p.z + side.z).setColor(1f, 1f, 1f, fade);
}`
  },
  {
    id: 'sky',
    title: '天穹（钉远平面）',
    code: `// 以相机为心的球，vsh 里：
//   vec4 clip = ProjMat * ViewMat * vec4(Position, 1.0);   // Position 已是相机相对
//   clip.z = clip.w * 0.9995;
// CPU 端不需要减相机 —— 但要把球心放到相机位置，否则球会偏出视野。`
  },
  {
    id: 'uv-as-data',
    title: '把 UV 当数据通道（几页的小技巧）',
    code: `// wander_world_volume.vsh：
//   vIsTrail = step(1.5, UV0.x);            // UV.x >= 1.5 表示"这一段是拖尾"
//   vUv = vec2(UV0.x - vIsTrail * 2.0, UV0.y);
// 用法：CPU 端把同一个网格的两段（本体 / 拖尾）用同一个 draw 提交，
//       靠 UV.x 偏移 2.0 打标记，shader 里再分开处理。
// 好处：少一次 draw、少一个 uniform；代价：UV 不再是 0..1，别拿它直接采样贴图。`
  }
];

/**
 * 后端选型：同一个效果，是做成「全屏后处理」还是「世界空间几何」？
 *
 * <p>这是升仙自己踩出来的（2026-10-25）：同一批特效先全屏 → 改几何 → 再改回全屏，
 * 两个方向都只对一半。<b>判据不是「哪个更高级」，而是「观众在哪」。</b></p>
 */
export const SPACE_BACKEND = [
  {
    id: 'backend-screen',
    title: '全屏后处理（观众在「体验」里）',
    when: '效果描述的是**整个世界的状态变了**：天变暗、闪白、画面被扭曲、时间回溯、色彩分级。',
    judge: [
      '观众是「正在经历这件事的人」，不是「远处看到的那个东西」。',
      '需要屏幕空间的形变（径向拉伸、裂纹、色散、旋涡）—— 几何做不到。',
      '需要跨元素统一处理（原版方块/水/粒子/天空一起变）。'
    ],
    how: [
      'post/{名字}.json：renderStage=after_level，stages 里一条 veil:blit（in=minecraft:main, out=veil:post）。',
      'program：vertex 用 veil:blit_screen（自带全屏三角形，输出 texCoord 0..1），fragment 是自己的 fsh。',
      '★必须在 Java 里 PostProcessingManager.add(priority, id) 才会执行；renderStage 只是过滤条件。',
      'uniform 用 preVeilPostProcessing 事件推（pipeline.getUniform(name) + isValid()）。'
    ],
    cost: [
      '没有体积感、不会被地形遮挡、镜头转动时纹丝不动 ⇒ 看着像「贴了层纸」。',
      '★**它会挡视野** —— 这是全屏的固有风险，必须自己设计「中心安全区」。',
      '每帧全屏跑：采样数要省（实测 8 次缩放采样 + 3 次色散可接受）。'
    ],
    lesson: '灾劫（天变了）用它。2026-10-25 实机反馈「球壳版太挡视野」后改回全屏，'
      + '并把「中心 r<0.20 完全不动 + 雾只给远景」写成 shader 里的硬纪律。'
  },
  {
    id: 'backend-geometry',
    title: '世界空间几何（观众在「世界」里）',
    when: '效果描述的是**某个具体的东西**：球、钟罩、结界、水墙、影子、拖尾、翅膀。',
    judge: [
      '有明确的空间位置与尺寸（半径几格、贴在哪个物体上）。',
      '应该被地形遮挡、应该随镜头转动改变视角、走近要变大。',
      '规模不大（几格到几十格），不会把玩家整个包住。'
    ],
    how: [
      'rendertypes/{名字}.json + program + vsh/fsh；vsh 用世界坐标（Position - CameraPosition）。',
      'RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS 里 Tesselator 建几何 → RenderType.draw(MeshData)。',
      '不走 post ⇒ 没有 add() 这一步。',
      '法线常常不占属性：球壳用 normalize(Position - center) 推径向。'
    ],
    cost: [
      '★**壳一旦有雾/半透明，而观众在壳内，就是贴在眼睛上的膜** —— 压迫类效果（大灾劫/天罚）绝对不能这么包。',
      '要自己管深度、混合、排序。'
    ],
    lesson: '仙窍 / 炼蛊球用它（半径几格、贴在物体上，观众看的是「那里有个东西」）。'
  }
];
