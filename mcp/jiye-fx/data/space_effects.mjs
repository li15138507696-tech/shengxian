// 立体效果逐个拆解 —— 15 张卡片 + 6 条万能公式。
//
// 来源：tools/extract_space_cards.py 抽 79 个世界几何 fsh 的算子签名，再逐个人读主函数。
// 结论先说：这批效果**不是 16 种算法，而是 6 条公式的排列组合**；
// 真正的差异在「用哪几个 vMode / 哪些常量」，不在数学结构。

export const SPACE_FORMULAS = [
  {
    id: 'filament',
    title: '细丝 / 电弧 / 刻度（最万能的一条）',
    code: 'pow(max(0.0, sin(a * x + b * y - t * speed + noise * k)), N)',
    notes: [
      'N 决定锐度：7~9 = 丝带，12~14 = 细丝，18 = 几乎只有一线（刻度/扫描线）。',
      '把 noise 乘进相位（k=4~8），线才会弯、才会「有机」；纯 sin 是均匀条纹。',
      '同一式换轴（x→y、x+y→x-y）就是另一族纹理，不用新函数。'
    ],
    refs: ['wander_world_trail_art (silkA/B/C 用 10/14/18)', 'lei_yi_wing (barbs 12)', 'zheng_tiger (horizontal 6.2)', 'man_as_before (vein 12, scan 18)']
  },
  {
    id: 'soft-core',
    title: '柔核 / 光束截面',
    code: 'exp(-d * d * k)',
    notes: [
      'k=5 → 宽而软的柱；k=34~48 → 细芯；k=72~90 → 几乎一条亮线。',
      'd 用 across（横向归一化距离）就是光柱；用 length(p) 就是圆核。',
      '比 smoothstep 更「自然衰减」，且天然不会有硬边。'
    ],
    refs: ['delight_in_mountain_and_water (5.0)', 'lei_yi_wing (34 / 72)', 'man_as_before (48)', 'menstrual_blood_curtain (38)']
  },
  {
    id: 'edge-fade',
    title: '横向双边淡出（quad 不变黑边）',
    code: 'sin(clamp(u, 0.0, 1.0) * 3.14159265)',
    notes: [
      '直接把 quad 的两条边收到 0，不用额外做渐变贴图。',
      'u 取 vUv.y 是上下淡出；取 vUv.x 是左右淡出。',
      '要「只有一侧淡」就换成 1.0 - smoothstep(0.72, 1.0, u)。'
    ],
    refs: ['baohuangtian_treasure (edge)', 'aperture_portal (edge/ band)', 'zheng_tiger_atmosphere (edge × sideEdge)']
  },
  {
    id: 'ellipse-mask',
    title: '椭圆 / 圆形裁切',
    code: 'vec2 p = vUv * 2.0 - 1.0;\nif (dot(p, p) > 1.0) discard;\nfloat mask = smoothstep(1.12, 0.05, length(p));',
    notes: [
      '先 `p = uv*2-1` 把 UV 中心化，再做长度判断 —— 这是所有「圆/椭圆」的前提。',
      '反向 smoothstep（大→小）得到的是「越靠中心越亮」的遮罩。',
      '要椭圆就把 p.y 乘个系数（如 `p.y * 1.08`），**不要**改 radius。'
    ],
    refs: ['aperture_portal (uMode=0 椭圆 discard)', 'baohuangtian_treasure (uMode=2 discard)', 'man_as_before (body)', 'delight (jewel)']
  },
  {
    id: 'world-grain',
    title: '世界锚定颗粒（不随 UV 拉伸）',
    code: 'float grain = hash21(floor(worldPos.xz * 18.0));',
    notes: [
      '用 UV 做 hash 会随网格拉伸而变形；用**世界坐标**做 hash，颗粒才是固定的。',
      'zheng_tiger 更狠：`floor(gl_FragCoord.xy * 0.55) + floor(vRelative.xz * 0.37)` —— 屏幕格 + 世界格混合，远看有噪点、近看有结构。',
      '尺度 10~32 是常用区间；再小就成雪花点。'
    ],
    refs: ['zheng_tiger_atmosphere', 'man_as_before (grain 10)', 'delight (paper 32, ink 18)', 'menstrual_blood_curtain']
  },
  {
    id: 'segmented-quad',
    title: '一个 quad 装三种材质（UV.x 分段 / vMode）',
    code: 'if (vUv.x < 1.5) { /* 线 */ }\nelse if (vUv.x < 3.5) { /* 星 */ }\nelse if (vUv.x < 5.5) { /* 云 */ }',
    notes: [
      'CPU 端把三段分别写到 UV.x = 0.5 / 2.5 / 4.5，一次 draw 画三样东西。',
      '等价做法是 `vMode`（顶点色或 uniform）：几页两种都用，UV.x 更省 uniform，vMode 更好读。',
      '代价：UV 不再是 0..1，**不能**直接拿它采样贴图，得先减去段基。'
    ],
    refs: ['star_flow (0/1/2 段)', 'wander_world_volume (step(1.5, UV.x) 判拖尾)', 'delight (vMode 0-3)', 'man_as_before (vMode 0-3)']
  }
];

export const SPACE_EFFECTS = [
  {
    id: 'sanqi-converge',
    title: '多股能量汇聚（升仙「吸收三气」）',
    programs: ['sanqi_flow', 'sanqi_ribbon', 'sanqi_vortex', 'xian_shell'],
    look: '天地两根光柱夹住人（白青自上贯下 / 土黄自下翻涌），外层裹着红尘烟雾，中心三色螺旋在赤道处拧成一个涡眼。',
    geometry: '七件几何：两根锥形管（各两层）+ 三条螺旋气带 + 两个球壳（经纬球 24×12）。球壳复用同一个 vsh（法线 = normalize(Position - center)）。',
    steps: [
      '★**柱 ≠ 气**：管面是旋转对称的，怎么调都像烟囱。要「气」必须 ① 角向分股（各股独立螺旋中心线）② 沿高度断续 ③ 细丝截面 ④ 加螺旋气带（带会绕着卷，一眼就看出在流）。',
      '**气带几何**：螺旋线 P(u) = (r(u)cosθ(u), y(u), r(u)sinθ(u))，带宽方向 W = Y 在垂直于切线 T 的平面上的投影（切线近竖直时退化为水平切向）⇒ 带面水平，俯视是盘旋的弧、侧视是水平气带。',
      '**柱心热线不能靠 UV**：管面的 UV 中心不是视线中心。要用「视线在柱截面上的投影偏移」——`tangent = normalize(cross(normal, viewDir))`，`off = dot(worldPos - axis, tangent) / radius`，`core = exp(-off*off*5.5)`。',
      '沿轴条纹三频叠加：`sin((uv.y*9 - t*speed)*PI + noise*5)` 取 7 次幂 + 17 次幂各一层，噪声混进相位 ⇒ 条纹会弯。',
      '**两根柱不同相**：给每根柱一个 `SanQiFlow` 常量偏移，进条纹相位也进顶点呼吸相位；共用一个时间源会看起来像同一股气。',
      '球面雾：把经纬球 UV 当平面坐标用 fbm（域扭曲一次），再乘菲涅尔 ⇒ 壳的边缘更浓。',
      '**三色螺旋同频 + 120° 相位差**：`base = ang*2 - t*spin + h*7`，`sin(base)`/`sin(base+2.09)`/`sin(base+4.19)` 各取 14 次幂 —— 三股互不重合的螺旋，越靠近两极越密。',
      '涡眼：赤道处（|h|→0）三股交汇最亮，`eye = exp(-h*h*5.5)`；半径随进度收缩。',
      '顶点呼吸：`displaced = Position + normal * (sin(t*1.35 + flow + uv.y*5.2)*0.045 + ...) * radius` —— 幅度要按半径缩放，否则大小两套的柱子动得一样。'
    ],
    constants: { columnSegs: 24, columnRings: 10, coreFalloff: 5.5, spiralPow: 14, spiralTwist: 7, phaseGap: '2.094 / 4.189' },
    reuse: [
      '「一个效果 = 多件几何」比「一个效果 = 一个巨大的 shader」更好调：每件独立一个 uniform 家族。',
      '柱心热线的投影偏移算法可以直接搬到任何管状/杆状几何（光束、锁链、藤蔓）。',
      '同频 + 固定相位差 = 天然的多股交织（螺旋、绳索、编发、电流）。',
      '进度同时驱动「半径收缩 + 转速加快 + 强度提升」，一个 uniform 家族就能做出「越来越急」。'
    ],
    pitfalls: [
      '★fsh 也要自己声明 `#veil:buffer veil:camera VeilCamera` —— Veil 按文件注入，vsh 里声明的不算数。',
      '同一个 program 里 vsh 与 fsh 的 uniform 名相同是允许的（共享一张表），但**类型必须一致**。',
      '柱是「包住」还是「贯穿」会彻底改变观感：贯穿（观众在柱内）才有「被贯通」的雄伟感。',
      '半透明靠提交顺序叠：先大壳、再柱、最后中心涡，谁压谁就后画。',
      '★实心面的 alpha 上限要压低（气是稀薄的，0.5~0.55）；压不住就变回烟囱。'
    ]
  },
  {
    id: 'mirror-art',
    title: '镜花水月（水面倒影里的另一个世界）',
    programs: ['wander_world_mirror_art'],
    look: '一块贴着水面的圆盘，里面有慢慢旋转的深空、丝状光带、向外扩散又回来的环。',
    geometry: '单 quad（POSITION_TEX_COLOR，QUADS），UV 当极坐标用；双趟：第二趟 additive → veil:bloom。',
    steps: [
      '`local = (uv - 0.5) * 2`；`radial = length(local)`；`angle = atan(local.y, local.x)`。',
      '有机边缘：`edgeNoise = fbm(worldPos.xz * 1.08 + t*0.05)`；`organicEdge = 0.982 + (edgeNoise-0.5)*0.038`；再用 `1 - smoothstep(organicEdge-0.03, organicEdge+0.014, radial)` 得到带毛边的圆。',
      '**视差**：`viewDir = normalize(vViewDirection)`；`grazing = max(0.15, abs(viewDir.y))`；`parallax = viewDir.xz / grazing` —— 掠射角越大位移越大。',
      '三层 fbm 用不同尺度（0.27 / 0.58 / 1.26）× 不同 parallax 系数（0.25 / -0.14 / 0.062）叠加成 depthField。',
      '螺旋丝：`sin(radial*29 - angle*4 - t*4 + deepA*8)`，再 `pow(max(0,·), 9)`。',
      '外扩环 `sin(radial*34 - t*5.1)` 与回流环 `cos(radial*21 + t*2.9)` 分别 `pow(...,12/14)`。'
    ],
    constants: { radialFreq: 29, spiralArms: 4, ringOutFreq: 34, ringBackFreq: 21, edgeBase: 0.982, edgeAmp: 0.038 },
    reuse: ['视差分层 = 任何「有深度的平面」通用解（水面、传送门、镜面）', 'organicEdge 让圆不再是完美圆', '两个反向环（外扩 + 回流）比单向扩散更像活物'],
    pitfalls: ['`grazing` 必须夹下限（0.15），否则俯视时 parallax 爆掉、纹理拉成条', '双趟第二趟要 additive，不然压暗']
  },
  {
    id: 'trail-art',
    title: '水痕 / 拖尾（沿路径的丝带）',
    programs: ['wander_world_trail_art', 'ink_ribbon'],
    look: '一条沿路径流动的丝带，边缘有泡沫，末端收细并淡出。',
    geometry: 'TRIANGLE_STRIP 或长条 quad；UV.x = 沿路径 0..1，UV.y = 横向。',
    steps: [
      '`along = uv.x`，`across = uv.y * 2 - 1`，`edge = abs(across)`。',
      '首尾收口：`tailBirth = smoothstep(0, 0.11, along)`，`seamFade = 1 - smoothstep(0.80, 0.995, along)`。',
      '三层丝：`flowCoordinate = along*19 - t*3.4 + worldLarge*4.8 + across*1.8`，三条 sin 分别取 10/14/18 次幂，加权 0.56/0.34/0.42。',
      '边缘泡沫：`bankNoise = fbm(vec2(along*9 - t*0.82, edge*8 + worldFine*2))`；`bankFoam = smoothstep(0.56,0.91,edge) * smoothstep(0.43,0.78, bankNoise + silk*0.28)`。',
      '冠脉：`crestPulse = pow(max(0, sin(along*27 - t*5.7 + worldFine*5.2)), 12) * bodyMask`。'
    ],
    constants: { flowFreq: 19, flowSpeed: 3.4, silkPow: '10/14/18', crestFreq: 27, crestSpeed: 5.7 },
    reuse: ['along/across 是丝带的标准参数化', '三层不同幂次 = 粗细混合，比单层有层次', 'worldLarge 混进相位让丝带随世界位置变化'],
    pitfalls: ['沿路径必须留 tailBirth，否则起点是个硬头', '丝带用 strip 时拐点要限最小段长，否则自交']
  },
  {
    id: 'star-flow',
    title: '星流（一个 quad 里塞三种材质）',
    programs: ['star_flow'],
    look: '同一张片子上同时有流光线、星点、星云。',
    geometry: '单 quad；**UV.x 分段**：0.5 = 流光、2.5 = 星空、4.5 = 星云。',
    steps: [
      '`if (vUv.x < 1.5)` 线：`softEdge = 1 - smoothstep(0.18,1.0,abs(uv.x))`；`caps = smoothstep(0, 0.055, min(uv.y, 1-uv.y) + (1-across)*0.055)`；`shimmer = 0.72 + 0.28*sin(uv.y*34 - t*4.8)`。',
      '`< 3.5` 星：`cell = floor(world.xz*0.31)`；`star = step(0.965, hash21(cell)) * exp(-dot(local,local) * mix(90, 260, seed))` —— 只有 3.5% 的格子有星，且亮度随种子变。',
      '`< 5.5` 云：三层 noise（0.58/0.28/0.14 权重）+ `sin(world.x*0.055 + world.z*0.041 - t*0.72 + broad*5)`。'
    ],
    constants: { starCell: 0.31, starThreshold: 0.965, starFalloff: '90..260', nebulaScale: 0.012 },
    reuse: ['UV.x 分段 = 省 draw 的常用手段', '`step(阈值, hash)` + `exp(-d²·k)` 是最省的点星模型'],
    pitfalls: ['UV 被占用后不能再当 0..1 用', '星点用 mix(90,260) 做大小差，否则一片一样大']
  },
  {
    id: 'lei-wing',
    title: '雷翼（翅骨 + 电弧 + 金羽）',
    programs: ['lei_yi_wing'],
    look: '半透明翅面，中间一道蓝白电弧游走，羽枝末端泛金。',
    geometry: '两个 quad（左右翼）或一个 quad + `vMode`；POSITION_TEX_COLOR。',
    steps: [
      '`across = abs(uv.x*2-1)`；`edge = 1 - smoothstep(0.72,1.0,across)`。',
      '翅骨：`shaft = exp(-across*across*72)`。',
      '羽枝：`barbPhase = uv.y*21 + across*7.5 - t*0.72 + sin(world.y*3.1)*0.42`；`barbs = pow(max(0,sin(barbPhase)),12) * smoothstep(0.05,0.32,across)`。',
      '游走电弧：`boltCenter = 0.5 + sin(uv.y*31 + world.x*2.7 - world.z*2.3 + t*4.8) * 0.095`；`bolt = exp(-abs(uv.x - boltCenter) * 42)`。',
      '四色调色：deep(0.012,0.09,0.46) / electric(0.015,0.42,1) / pale(0.12,0.68,1) / gold(1,0.42,0.025)，按 edge / shaft / barbs / bolt 加权。'
    ],
    constants: { shaftK: 72, barbFreq: 21, barbPow: 12, boltAmp: 0.095, boltK: 42 },
    reuse: ['`boltCenter` 用 sin 沿 y 摆动 + `exp(-|x-center|·k)` = 会游走的电弧', '四色调色（深/亮/淡/金）比两色有层次'],
    pitfalls: ['电弧幅值 0.095 超过 0.1 会跑到翅膀外面', 'alpha 要乘 edge，否则 quad 的方角会露出来']
  },
  {
    id: 'corrosive-skull',
    title: '骷髅剪影（五个圆拼出来）',
    programs: ['corrosive_dark_current_skull', 'corrosive_dark_current_skull_bone'],
    look: '暗流里浮出一个骷髅头，眼窝与鼻腔是镂空的。',
    geometry: '单 quad，纯 fragment 造型，**不需要模型**。',
    steps: [
      '`p = uv*2-1`。',
      '颅：`head = 1 - smoothstep(0.78, 1.0, length(vec2(p.x, p.y*1.08)))`（乘 1.08 把圆压成椭圆颅）。',
      '颌：`jaw = 1 - smoothstep(0.55, 0.92, length(vec2(p.x, (p.y+0.48)*1.35)))`。',
      '眼窝/鼻：三个小圆 `1 - smoothstep(0.17, 0.31, length(p - vec2(±0.32, 0.18)))`、`length(p - vec2(0, -0.08))`。',
      '合成：`silhouette = max(head, jaw) * (1 - max(max(eyeL,eyeR),nose) * 0.92)` —— **乘法挖洞**。'
    ],
    constants: { headR: 0.78, jawR: 0.55, eyeR: 0.17, eyeX: 0.32, noseR: 0.09 },
    reuse: ['几个 smoothstep 圆 + max/min 就能拼剪影，省一张贴图', '挖洞用 `1 - max(...)*0.92`，留 8% 让它不是全透明'],
    pitfalls: ['这类造型别用 discard 挖洞（会破边），用乘 alpha', '椭圆靠乘坐标，不靠改半径']
  },
  {
    id: 'corrosive-stream',
    title: '暗流（6 行写完的极简层）',
    programs: ['corrosive_dark_current_stream', 'corrosive_dark_current_stream_core', '_edge', '_tip'],
    look: '一道暗绿近黑的水流，中间有缓慢的横向涌动。',
    geometry: '单 quad 或条带。',
    steps: [
      '`edge = 1 - smoothstep(0.68, 1.0, abs(uv.x*2-1))` —— 横向收边。',
      '`body = 0.66 + 0.34 * sin(uv.y*38 - t*0.22 + uv.x*8)` —— 沿流向的低频涌动（速度只有 0.22，很慢）。',
      '`alpha = vColor.a * edge * body * 0.78`。'
    ],
    constants: { edgeInner: 0.68, bodyFreq: 38, bodySpeed: 0.22 },
    reuse: ['整族 10 个程序都是「一个简单公式 + 不同颜色/速度」—— 加效果先问能不能复用这层', '极慢的速度（0.2）比快更像「暗流」'],
    pitfalls: ['别给它加噪声：这一族的辨识度就来自「干净的渐变」']
  },
  {
    id: 'delight',
    title: '山水之乐（vMode 四形态：气/墨/纸/宝珠）',
    programs: ['delight_in_mountain_and_water'],
    look: '同一套几何，按 vMode 变成青气流、墨迹、宣纸、绿宝珠。',
    geometry: '单 quad；双趟（第二趟 additive → bloom）。',
    steps: [
      'mode0 气：`current = 0.58+0.42*sin(world.y*8 - t*9 + world.x*2.5)`；`core = exp(-across²*5)`。',
      'mode1 墨：`ink = 0.62+0.38*sin((world.x+world.z)*5.5 + t*1.4)`；`grain = hash21(floor(world.xz*18))`。',
      'mode2 纸：`paper = 0.78 + hash(floor(world.xy*32))*0.12`；`script = pow(max(0,sin(world.x*31+world.y*17 - t*1.2)), 18)` 当作字迹。',
      'mode3 珠：`jewel = smoothstep(1.12, 0.05, length(p))`；`rays = pow(max(0, sin(atan(p.y,p.x)*8 + t*3.2)), 10)`。'
    ],
    constants: { qiCoreK: 5, inkFreq: 5.5, paperGrain: 32, scriptPow: 18, rayArms: 8 },
    reuse: ['一个 rendertype 用 vMode 演四种东西 —— 省资源、省注册', '纸纹 = 世界坐标 hash，不用贴图'],
    pitfalls: ['vMode 必须来自 uniform 或顶点色，**不能**来自 uv.x（会被插值糊掉）']
  },
  {
    id: 'treasure',
    title: '宝光（三形态：光带 / 金纹 / 宝珠）',
    programs: ['baohuangtian_treasure'],
    look: '宝物外面一层旋转光带、一圈金纹、一颗会呼吸的珠子。',
    geometry: '单 quad；双趟 bloom。',
    steps: [
      'mode0 光带：`edge = sin(uv.x*π)`；`flow = noise(vec2(uv.x*9 + t*0.025, uv.y*5 - t*0.065))`；`filament = pow(max(0, sin(uv.y*30 - t*0.42 + flow*7)), 7)`。',
      'mode1 金纹：`band = sin(uv.y*π)`；`spark = pow(max(0, sin(uv.x*58 - t*1.8)), 12)`；`ripple = 0.66+0.34*sin(uv.x*18 + t*0.45)`。',
      'mode2 宝珠：`if (length(p) > 1) discard`；`glow = pow(max(0, 1-radius), 1.8)`；`shimmer = 0.72+0.28*sin(t*2.4 + worldPos.y*7)`。',
      '末了统一 `clamp(alpha, 0, 0.95)`。'
    ],
    constants: { stripeFreq: 58, stripePow: 12, glowPow: 1.8, alphaCap: 0.95 },
    reuse: ['`pow(1-radius, 1.8)` 是「球形发光」最省的写法（不用法线）', 'alpha 上限按材质给：光带 0.95、烟 0.56、血 0.86'],
    pitfalls: ['clamp 上限别省：叠几层后 additive 会一片死白']
  },
  {
    id: 'man-as-before',
    title: '人如故（四形态：脉纹 / 金线 / 裂 / 扫描）',
    programs: ['man_as_before_apparition'],
    look: '一个人形残影，身上有脉纹、横向金线、裂纹、或从上往下扫的光。',
    geometry: '单 quad；双趟 bloom。',
    steps: [
      'mode0 脉纹：`vein = pow(max(0, sin((p.x+p.y)*18 - t*3.2)), 12)`；`edge = smoothstep(1.12, 0.22, length(p))`。',
      'mode1 金线：`transverse = abs(uv.y*2-1)`；`ticks = step(0.72, fract(uv.x*18))`；`core = exp(-transverse²*48)`。',
      'mode2 裂：`fracture = pow(max(0, sin(uv.x*47 + uv.y*23 + t*5)), 14)`；`grain = hash21(floor(world.xy*10))`。',
      'mode3 扫描：`body = smoothstep(1.0, 0.10, length(p))`；`scan = pow(max(0, sin((p.y + t*0.75)*31)), 18)`。',
      '全程乘一个 `pulse = 0.72 + 0.28*sin(t*4.8 + world.y*3.4)`。'
    ],
    constants: { veinFreq: 18, veinPow: 12, tickStep: 0.72, scanFreq: 31, scanPow: 18, pulseFreq: 4.8 },
    reuse: ['`step(n, fract(u*k))` = 虚线/刻度，比画贴图省', '整体乘 pulse 让静态纹理「活」起来'],
    pitfalls: ['`fract(u*k)` 的 k 要整数，否则接缝会跳']
  },
  {
    id: 'blood-curtain',
    title: '血幕（veinField + 膜 + 悬浮细胞）',
    programs: ['menstrual_blood_curtain'],
    look: '一层半透的血色膜，里面有血管在爬，还有悬浮的细胞点。',
    geometry: '单 quad；双趟 bloom。',
    steps: [
      '`veins = veinField(world, t)` —— 世界坐标域扭曲出的血管场（同 fbm-warp 配方）。',
      '`suspendedCells = 0.5 + 0.5*sin(world.x*2.37 + world.y*1.91 + world.z*2.13 - t*0.42)` —— 三轴不同频率，得到不规则的悬浮斑。',
      '`membrane = 0.78 + 0.22*sin(world.y*0.58 - t*0.9 + sin(world.x*0.24)*1.8)` —— 膜在起伏。',
      '合成：颜色乘膜、加 veins、再加一层「密集细胞」`smoothstep(0.78,0.98,suspendedCells)*0.45`。'
    ],
    constants: { cellFreq: '2.37/1.91/2.13', cellSpeed: 0.42, membraneFreq: 0.58, alphaCap: 0.86 },
    reuse: ['三轴不同频率的 sin 之和 = 廉价但有效的「不规则分布」', '膜/细胞/血管分三层叠，比单层 fbm 有生物感'],
    pitfalls: ['三轴频率不能成整数比，否则会出现规律网格']
  },
  {
    id: 'zheng-atmosphere',
    title: '峥虎气层（屏幕格 + 世界格混合颗粒）',
    programs: ['zheng_tiger_atmosphere', 'zheng_tiger_stone', 'zheng_tiger_vein'],
    look: '领地上一层层横向流动的沙尘与烟，边缘柔和，不会糊死画面。',
    geometry: '相机相对坐标的大 quad / 圆柱面；`vMode` 分形态。',
    steps: [
      '颗粒：`grain = hash(floor(gl_FragCoord.xy*0.55) + floor(vRelative.xz*0.37))` —— **屏幕格 + 世界格**。',
      '`broad = hash(floor(vRelative.xz*0.12 + vec2(t*1.7, -t*0.42)))`（慢），`detail = hash(floor(gl_FragCoord.xy*vec2(0.18,0.34) + vec2(t*5.4,0)))`（快）。',
      '横向条带：`horizontal = 0.52 + 0.48*sin(uv.y*31 + rel.x*0.13 - rel.z*0.08 - t*6.2)`。',
      '`brokenBands = smoothstep(0.18, 0.92, broad*0.58 + detail*0.25 + horizontal*0.36)`。',
      '双边淡出 `edge = smoothstep(0,0.12,uv.y) * (1 - smoothstep(0.82,1.0,uv.y))`，`sideEdge` 同理。',
      '`alpha` 末了 `clamp(alpha, 0, 0.56)`。'
    ],
    constants: { screenGrain: 0.55, worldGrain: 0.37, bandFreq: 31, alphaCap: 0.56 },
    reuse: ['屏幕格 + 世界格混合是「近处有结构、远处不闪」的关键', 'alpha 封顶 0.56 让多层叠起来仍是「气」而不是「墙」'],
    pitfalls: ['只用屏幕格会随镜头滑动（像贴纸）；只用世界格远处会摩尔纹']
  },
  {
    id: 'water-enclosure',
    title: '水牢（带法线的水体）',
    programs: ['water_enclosure', 'water_enclosure_close', 'wander_world_water'],
    look: '一个包住目标的水球/水墙，表面有波纹，边缘有菲涅尔亮边。',
    geometry: '**带 Normal 属性**的盒/球（POSITION_TEX_COLOR_NORMAL）；水面要写 depth。',
    steps: [
      '法线来自顶点属性（`vNormal = normalize(Normal)`），**不要**用 dFdx 现算（水面会一片噪点）。',
      '波纹：用 `fbm(world.xz*k + t*speed)` 扰动法线或采样偏移。',
      '菲涅尔：`pow(1 - abs(dot(normal, viewDir)), n)` 提亮边缘。',
      '折射偏移要乘深度差并夹上限，否则近处拉出长条。'
    ],
    constants: { fresnelPow: '2~4', depthGuard: 0.0008 },
    reuse: ['水体/玻璃/果冻都走这条：真实法线 + 菲涅尔 + 深度守卫折射'],
    pitfalls: ['水面 write_mask.depth 必须 true，否则水下的东西会盖到水面上', '折射阈值 0.0008 是四套体积场的统一口径']
  },
  {
    id: 'iron-wall-bell',
    title: '铁壁钟罩（球壳 + 双趟自发光）',
    programs: ['iron_wall_bell', 'iron_wall_field'],
    look: '扣在地上的一口半透明钟罩，边缘厚、表面有金属纹理，外圈在发光。',
    geometry: '球壳（相机相对坐标，`vWorldPos = Position + CameraPosition`）；双趟：第二趟 additive → bloom。',
    steps: [
      '`dFdx(vWorldPos)` / `dFdy(vWorldPos)` 叉乘得到**屏幕空间法线**（球壳没给 Normal 属性时的替代）。',
      '边缘用菲涅尔加厚；表面用 `noise` 沿 uv 做金属拉丝。',
      '第二趟同一份几何、additive、output=veil:bloom → 外圈自发光。'
    ],
    constants: { fresnelPow: '2~3' },
    reuse: ['`cross(dFdx(P), dFdy(P))` 是「没有法线属性时」的标准补法', '双趟 bloom 不改 shader 就有发光边'],
    pitfalls: ['dFdx 法线在退化三角形处会闪，壳足够细分（24×12）就没事', 'cull 必须 none（人在罩子里）']
  },
  {
    id: 'sky-vortex',
    title: '天涡（远平面 + 对数螺旋）',
    programs: ['sky_vortex', 'sky_vortex_no_bloom', 'aperture_sky', 'baohuangtian_sky'],
    look: '天顶一个缓慢旋转的巨大漩涡，云被卷成螺旋臂。',
    geometry: '相机相对的半球/全屏 quad，`clip.z = clip.w * 0.9995` 钉远平面。',
    steps: [
      '用视线方向（或 uv 中心化）算极坐标 `r, θ`。',
      '对数螺旋：`θ + log(r) * k` 作为相位，再取 `sin` 的若干次幂得到臂。',
      '多层 fbm 加域扭曲，`pow(..., 8~12)` 收成细臂。',
      '`clip.z=clip.w*0.9995` 保证低渲染距离下不被裁，同时被山体正常遮挡。'
    ],
    constants: { farPlane: 0.9995, arms: '2~5', twist: 'k≈3~6' },
    reuse: ['对数螺旋 = 星系/漩涡/天涡通用', '远平面技巧对任何「无限远背景」都适用'],
    pitfalls: ['0.9995 与 1.0 只差 0.0005，但写 1.0 会被近裁剪吃掉', '相机相对的顶点别忘了球心跟着相机']
  },
  {
    id: 'volume-field',
    title: '体积场（四套同构：风 / 气 / 墙 / 浪）',
    programs: ['fan_wind_field', 'hua_qi_field', 'iron_wall_field', 'wander_world_wave_field'],
    look: '一片有厚度的风墙/气流，能挡住后面的东西，边缘有体积感。',
    geometry: '一个盒子；两趟：field 写 MRT（RGBA16F 法线+覆盖、RGBA8 材质、深度）→ composite 全屏按深度守卫合成。',
    steps: [
      'field 趟：盒子的 fsh 写 `layout(location=0) out vec4`（法线*0.5+0.5, coverage）与 `location=1`（材质）。',
      'composite 趟：采样三张附件 + 场景深度；`0.0008` 守卫决定「这个像素上有没有东西」。',
      '厚度吸收：`1 - exp(-thickness * k)`；边缘用菲涅尔补。',
      '四套只换噪声与色板，骨架一字不改。'
    ],
    constants: { depthGuard: 0.0008, format: 'RGBA16F + RGBA8 + DEPTH' },
    reuse: ['「换算法不换管线」的最佳范例：四套共用同一套 rendertype/post 骨架'],
    pitfalls: ['composite 采样场深度要用 `screenToLocalSpace` 反算世界点，直接当距离用会错', 'RGBA16F 在 4K 下很吃带宽，不需要法线就降 RGBA8']
  }
];
