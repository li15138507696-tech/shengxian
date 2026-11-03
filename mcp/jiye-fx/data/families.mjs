// 几页 40 个特效家族的知识卡。members 为 program 名（不含命名空间）。
// kind 用于 fx_plan / fx_create 选模板；api 指向 data/api.mjs 的条目。
export const FAMILIES = [
  {
    id: 'aperture', title: '仙窍显化 / 门户 / 天空', kind: 'skybox',
    members: ['aperture_manifestation', 'aperture_world_core', 'aperture_world_edge', 'aperture_world_shell', 'aperture_portal', 'aperture_sky'],
    role: '仙窍世界从无到有的显化、传送门、仙窍天空。',
    mechanism: '一个 304 行 shader 用 uMode 0..6 吃完整条生命周期：丝带/涟漪门/整颗星球/地幔/飘带/云海/刻纹。星球模式先用球面法线做 3D 噪声域扭曲当“地形法线”，再 fbm3 三层切海/滩/林/山/雪/仙池/道纹；门户用平面+椭圆判断 discard 挖洞。',
    uniforms: ['uMode', 'uProgress', 'uTurbulence', 'uIntensity', 'uSeed', 'uGrade', 'uPathColor', 'uPortalCutout', 'uPortalNormal', 'uPortalPlaneCenter', 'uPortalHalfWidth', 'uPortalHalfHeight'],
    api: ['veil-rendertype', 'veil-shader', 'veil-post'],
    reuse: '任何“世界/位面显形”都能套 uMode 分段 + 球面域扭曲；天空盒 vsh 的 mat3(ViewMat)+clip.z 可直接搬。'
  },
  {
    id: 'nine_heavens', title: '九重天变色', kind: 'skybox',
    members: ['black_heaven_sky', 'blue_heaven_sky', 'cyan_heaven_sky', 'green_heaven_sky', 'orange_heaven_sky', 'purple_heaven_sky', 'red_heaven_sky', 'white_heaven_sky', 'yellow_heaven_sky', 'black_heaven_post', 'blue_heaven_post', 'cyan_heaven_post', 'green_heaven_post', 'orange_heaven_post', 'purple_heaven_post', 'red_heaven_post', 'white_heaven_post', 'yellow_heaven_post'],
    role: '九色天象 + 对应远景分级。',
    mechanism: '算法只在 include/nine_heavens_sky.glsl 里写一份（fbm 云带 + 太阳光晕 + 双层星点 + tonemap）；10 个 sky fsh 各 2 行调用；10 个 post program 共用 heaven_post.fsh。十种颜色全靠 6 个 uniform（uBaseColor/uHorizonColor/uAccentColor/uStarDensity/uMotionScale/uPhase）+ post 侧 HeavenTint/HeavenFog，由 Java 推。',
    uniforms: ['uBaseColor', 'uHorizonColor', 'uAccentColor', 'uStarDensity', 'uMotionScale', 'uPhase', 'HeavenTint', 'HeavenFog'],
    api: ['veil-shader', 'veil-post', 'veil-rendertype'],
    reuse: '“一套算法 × N 套配色”的标准做法：公共函数进 include，颜色只走 uniform，post JSON 不写死任何值。'
  },
  {
    id: 'baohuangtian', title: '宝黄天天象 / 镜面 / 宝物', kind: 'skybox',
    members: ['baohuangtian_sky', 'baohuangtian_mirror', 'baohuangtian_treasure'],
    role: '宝黄天的青金天、镜面涟漪、宝物三形态。',
    mechanism: '天空用 horizon+zenith+云带+尘点+fbm 道纹；镜面是屏幕空间横向翻转 + 柠檬色 rim + 暗角；宝物 uMode 0/1/2 = 飘带（flow+filament）/光带（spark）/圆光（radial glow）。',
    uniforms: ['uMode', 'MirrorIntensity'],
    api: ['veil-shader', 'veil-post', 'veil-rendertype'],
    reuse: '屏幕镜面配方（texCoord.x 翻转 + 切向微扰）与“三形态单 shader”都很好复用。'
  },
  {
    id: 'sky_vortex', title: '天道竖瞳巨涡', kind: 'skybox',
    members: ['sky_vortex', 'sky_vortex_bloom'],
    role: '覆盖天空的墨色巨涡，中心是竖瞳。',
    mechanism: '两层 fbm 域扭曲 → 极坐标对数螺旋臂（sin(θ*arms - log(r)*twist + phase)）→ 反向次级涡 → 墨云墙；中心叠加高斯事件视界环、虹膜纤维、竖向瞳孔、角膜高光、曼陀罗刻度、8 道天柱；bloom 版本只输出 HDR 能量。',
    uniforms: ['uIntensity', 'uInnerSpeed', 'uOuterSpeed', 'uInkColor', 'uSilverColor', 'uAccentColor', 'uEyeStrength'],
    api: ['veil-shader', 'veil-rendertype'],
    reuse: '对数螺旋 + 高斯环 + 极坐标刻度是“巨构/漩涡/法阵”的通用骨架；bloom 变体证明双趟输出。'
  },
  {
    id: 'fan_wind', title: '罡风体积场', kind: 'volume_field',
    members: ['fan_wind_field', 'fan_wind_composite'],
    role: '贴地的罡风热浪，带折射与准星保护。',
    mechanism: 'field 趟把薄壳渲进 MRT：RGBA16F=法线*0.5+0.5 + 覆盖 a，RGBA8=强度/种类/凝结；composite 趟全屏采样场+场景色深，按法线偏移做三采样色散折射，深度守卫三条件回退，中心 0.27×0.22 椭圆内偏移压到 42%。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-framebuffer', 'veil-rendertype', 'veil-post', 'veil-shader'],
    reuse: '四套体积场共用的黄金骨架：换噪声/颜色即可做热浪/毒雾/结界/水墙。'
  },
  {
    id: 'hua_qi', title: '化气体积场', kind: 'volume_field',
    members: ['hua_qi_field', 'hua_qi_composite'],
    role: '化气过程的吸入/成形/剑/发射/冲击。',
    mechanism: '与 fan_wind 同构。种类 g 分段：<0.2 吸入、0.2-0.5 成形、0.62-0.88 剑、0.88+ 冲击；composite 用场纹理邻域差取边缘 → 青/紫边缘辉光，三采样按通道拼接做色散。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-framebuffer', 'veil-rendertype', 'veil-post', 'veil-shader'],
    reuse: '“用覆盖率邻域差取轮廓”比额外画边省一个 pass，适合所有场类特效。'
  },
  {
    id: 'iron_wall', title: '铁壁钟罩', kind: 'volume_field',
    members: ['iron_wall_field', 'iron_wall_bell', 'iron_wall_composite'],
    role: '铁壁结界：场 + 铜钟壳 + 折射。',
    mechanism: 'field 写 MRT；bell 是壳体 shader（锤纹/拉丝/12 根肋/铭文带/热金脉冲）；composite 用 viewNormal 偏移 + 候选深度三条件（场景<钟深、候选<钟深、深度差>0.015）回退。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-framebuffer', 'veil-rendertype', 'veil-post', 'veil-shader'],
    reuse: '“壳 + 场 + composite”三层结构适合任何可进入的结界/护罩。'
  },
  {
    id: 'wander_world_wave', title: '忘川水墙', kind: 'volume_field',
    members: ['wander_world_wave_field', 'wander_world_wave_fallback', 'wander_world_wave_composite'],
    role: '可穿越的水墙，带真实厚度吸收。',
    mechanism: 'field 写 MRT 并做扰动法线（perturbNormal）；composite 用 screenToLocalSpace 反算世界点算几何厚度，Beer-Lambert exp(-absorption*thickness) 做透射+散射，菲涅尔 0.02+0.98*pow(1-NdotV,5)，首人称按相机距离淡出，准星安全区 0.24×0.20。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-framebuffer', 'veil-rendertype', 'veil-post', 'veil-shader'],
    reuse: '所有“水体/玻璃/琥珀”类体积都该用厚度吸收，而不是简单 alpha。'
  },
  {
    id: 'spring_autumn', title: '春秋蝉时间回溯', kind: 'screen_post',
    members: ['spring_autumn_time_river', 'spring_autumn_cicada_trail', 'spring_autumn_cicada_composite'],
    role: '时间倒流的拖影与时间河。',
    mechanism: '唯一 3-stage post：copy 主画面到私有 FBO（含深度）→ blit trail（11 次沿反向向量采样，权重 exp(-i/10*2.35)）→ blit composite（径向色散+去饱和+暗角+叶脉+秋色）。time_river 是几何层。',
    uniforms: ['RewindProgress', 'RewindIntensity', 'FlowSpeed', 'LandingPulse', 'MemoryOpacity', 'EffectSeed'],
    api: ['veil-post', 'veil-shader', 'veil-rendertype'],
    reuse: '需要“保留原场景做对比/拖影”的效果，照这个 copy→blit→blit 抄。'
  },
  {
    id: 'wander_world', title: '忘川水体', kind: 'water_body',
    members: ['wander_world_volume', 'wander_world_volume_glint', 'wander_world_aura', 'wander_world_water', 'wander_world_trail_art', 'wander_world_mirror_art'],
    role: '水体壳、闪光、气场、水面、尾迹、镜面。',
    mechanism: 'volume 用 apparentThickness=(1-facing)*0.72+(1-frontFace)*0.52 做厚度，背面更暗；water 同一 shader 服务圆盘与条带（vIsStrip），圆边重映射成 0..1；art 层用 seamFade/throatFade 保证接缝由基础水体承担；mirror_art 用视差 viewDir.xz/|viewDir.y| + 三层 fbm。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '“基础水体 + 可整体淡出的艺术扩边”是接缝不露馅的关键；厚度公式可直接搬。'
  },
  {
    id: 'water_clothing', title: '水衣', kind: 'entity_aura',
    members: ['water_clothing_aura', 'water_clothing_shell', 'water_clothing_silk', 'water_clothing_ornament', 'water_clothing_transform'],
    role: '穿在实体身上的水：气场/壳/绸/饰 + 两个 bloom 层 + 穿上瞬间的屏幕涟漪。',
    mechanism: '全部 NEW_ENTITY 格式（quad + lightmap + overlay），vsh 用 ModelViewMat/ProjMat；shell 做 fresnel+filament，silk 最平最柔，ornament 最亮；bloom 独立 rendertype 输出 veil:bloom；transform 是屏幕径向涟漪。',
    uniforms: ['VeilRenderTime', 'WaterClothingIntensity'],
    api: ['veil-rendertype', 'veil-shader', 'veil-post'],
    reuse: '角色饰品四层（壳/绸/饰/气场）+ 独立 bloom 层的组织方式；NEW_ENTITY 是实体挂件首选格式。'
  },
  {
    id: 'di_xian', title: '地先灾劫', kind: 'screen_post',
    members: ['di_xian_impact', 'di_xian_pressure', 'di_xian_surface', 'di_xian_vine_bloom', 'di_xian_vine_surface', 'di_xian_post'],
    role: '地气上涌、压迫、藤蔓缠身、屏幕土黄分级。',
    mechanism: '5 件几何（impact/pressure/surface/vine_bloom/vine_surface，全用共享 liu_feng_surface.vsh）+ 11 参数 post：去饱和→远景土黄→中心压力→边缘束缚→闪白→恢复。',
    uniforms: ['DiXianStrength', 'DiXianPhase', 'DiXianProgress', 'DiXianFogDensity', 'DiXianDisturbance', 'DiXianFlash', 'DiXianRecovery', 'DiXianVineProgress', 'DiXianWrappedVines', 'DiXianBindingStrength', 'DiXianExtraDamageProgress'],
    api: ['veil-post', 'veil-rendertype', 'veil-shader'],
    reuse: '灾劫 post 的标准八步公式；几何拆件（冲击/压迫/面/藤）可复用到任何“领域压制”。'
  },
  {
    id: 'liu_feng', title: '流风灾劫', kind: 'screen_post',
    members: ['liu_feng_surface', 'liu_feng_vein', 'liu_feng_impact', 'liu_feng_post'],
    role: '流风的气刃、脉络、冲击与青玉分级。',
    mechanism: '3 件几何共享 liu_feng_surface.vsh + 10 参数 post：沿风向 gustWave 采样偏移 + 去饱和 + 远景青玉 + 地平线雾 + 暗角 + 闪白 + 恢复。',
    uniforms: ['LiuFengStrength', 'LiuFengPhase', 'LiuFengProgress', 'LiuFengFogDensity', 'LiuFengDisturbance', 'LiuFengFlash', 'LiuFengDirectionX', 'LiuFengDirectionZ', 'LiuFengTurbulence', 'LiuFengRecovery'],
    api: ['veil-post', 'veil-rendertype', 'veil-shader'],
    reuse: '带方向的风类 post：把风向作为 vec2 uniform，所有噪声/偏移都投到该方向。'
  },
  {
    id: 'datong_wind', title: '大同风', kind: 'screen_post',
    members: ['datong_wind_boundary', 'datong_wind_core', 'datong_wind_erosion', 'datong_wind_post'],
    role: '大同风的边界、核心、侵蚀与屏幕扭曲。',
    mechanism: '3 件几何共享 datong_wind_boundary.vsh + 5 参数 post：sin(uv.y*48) 波 + 去饱和 + 暗角 + 闪白。',
    uniforms: ['DatongWindStrength', 'DatongWindCollapse', 'DatongWindDistortion', 'DatongWindVignette', 'DatongWindFlash'],
    api: ['veil-post', 'veil-rendertype'],
    reuse: '最精简的灾劫 post 模板（5 个旋钮），适合先跑通再接复杂算法。'
  },
  {
    id: 'wind_flower', title: '风花灾劫', kind: 'screen_post',
    members: ['wind_flower', 'wind_flower_surface', 'wind_flower_wind'],
    role: '风花：压天、缕状风幕、扫过的暗前、冲击裂缝。',
    mechanism: '15 参数 post：两组长条噪声（3.2×5.8 与 8.6×2.7）+ 横向扫掠 + 方向性峰值 + 色散 + 远景青灰幕 + 暗角 + 闪白；lineSuppression 保 HUD 可读；glow 是复用主 program 的独立 rendertype。',
    uniforms: ['FlowerStrength', 'FlowerGust', 'FlowerWarning', 'FlowerFlash', 'FlowerSeed', 'FlowerPhase', 'FlowerDirectionX', 'FlowerDirectionZ', 'FlowerTurbulence', 'FlowerRecovery', 'FlowerTintStrength', 'FlowerFogDensity', 'FlowerDisturbance', 'FlowerDesaturation', 'FlowerLineSuppression'],
    api: ['veil-post', 'veil-rendertype', 'veil-shader'],
    reuse: '“远景才上色、近景保持可读”的深度分级写法；风向驱动 + 扫掠是风灾通用节奏。'
  },
  {
    id: 'wind_thunder', title: '风雷暴 / 风雷笼', kind: 'screen_post',
    members: ['wind_thunder_storm', 'wind_thunder_cage'],
    role: '云面/柱体双形态的雷暴，与螺旋雷笼。',
    mechanism: 'storm 用 vUv.x>1.5 切云面/柱体，world 噪声 + rolling + shredded；cage 是屏幕后处理：螺旋 sin + 噪声扭曲 + 冷色压暗 + 暗角 + 预警脉 + 回击闪 + 余震。',
    uniforms: ['CageStrength', 'CageWarning', 'CageFlash', 'CageAftershock', 'CagePressure', 'CageRound', 'CageSeed'],
    api: ['veil-post', 'veil-rendertype'],
    reuse: '回击闪 = flash*(0.74+0.26*|sin(t*31+seed)|)，闪电类通用。'
  },
  {
    id: 'corrosive_dark_current', title: '蚀暗流', kind: 'screen_post',
    members: ['corrosive_dark_current_impact', 'corrosive_dark_current_portal', 'corrosive_dark_current_post', 'corrosive_dark_current_residue', 'corrosive_dark_current_skull', 'corrosive_dark_current_skull_bone', 'corrosive_dark_current_skull_fog', 'corrosive_dark_current_skull_void', 'corrosive_dark_current_stream', 'corrosive_dark_current_stream_core', 'corrosive_dark_current_stream_edge', 'corrosive_dark_current_stream_tip'],
    role: '暗流冲击、门、颅骨、流柱、残渣与屏级腐蚀。',
    mechanism: '12 件套拆得最细：颅骨用 5 个 2D SDF（头/颌/眼/鼻/虚空）拼；流柱拆 core/edge/tip；post 15 参数做流向偏移、远景去色、暗角、闪、恢复。',
    uniforms: ['CorrosiveDarkCurrentStrength', 'CorrosiveDarkCurrentPhase', 'CorrosiveDarkCurrentRecovery', 'CorrosiveDarkCurrentStreamProgress', 'CorrosiveDarkCurrentErosion', 'CorrosiveDarkCurrentFlash', 'CorrosiveDarkCurrentDirectionX', 'CorrosiveDarkCurrentDirectionZ', 'SkullVisibility', 'MouthCharge', 'StreamFront', 'StreamDirection', 'StreamTurbulence', 'ArenaPressure', 'Recovery'],
    api: ['veil-post', 'veil-rendertype', 'veil-shader'],
    reuse: '“同族多件套”命名与拆件的教科书；2D SDF 拼复杂剪影比贴图更可控。'
  },
  {
    id: 'tou_sheng', title: '偷生领域', kind: 'domain_sphere',
    members: ['tou_sheng_domain'],
    role: '世界空间的血色球域。',
    mechanism: 'post 里用 screenToLocalSpace 反算世界点，算到球心距离；天空像素用射线-球最近距离补 skyMask，保证仰头也看到穹顶；三层雾（大/滚/缕）+ 酒红/血红/猩红分级 + 边缘猩红 rim。',
    uniforms: ['TouShengCenter', 'TouShengRadius', 'TouShengPhase', 'TouShengActive'],
    api: ['veil-post', 'veil-shader'],
    reuse: '球域/领域类必抄：世界点 + 天空射线双 mask，绝不整屏贴色。'
  },
  {
    id: 'taiqing', title: '太清空境', kind: 'screen_post',
    members: ['taiqing_empty_space', 'taiqing_empty_space_post'],
    role: '玉质空境与剑痕折射。',
    mechanism: '几何层做玉的内光/条纹/边缘；post 9 参数：把最多 16 道“剑痕前沿”（位置+方向+半宽）传进 shader，在世界空间算到前沿的距离，命中才做屏幕折射。',
    uniforms: ['TaiqingStrength', 'TaiqingJade', 'TaiqingWarning', 'TaiqingDive', 'TaiqingFlash', 'TaiqingRefraction', 'TaiqingFrontCount', 'TaiqingFront', 'TaiqingDirection'],
    api: ['veil-post', 'veil-shader'],
    reuse: '“把 N 条线段传进 shader 做局部折射”是空间特效与屏幕特效的桥；数组 uniform 上限即特效上限。'
  },
  {
    id: 'tianyuan', title: '天元宝皇莲', kind: 'screen_post',
    members: ['tianyuan_baohuang_lian'],
    role: '屏幕中心的玉莲异象。',
    mechanism: '纯屏幕极坐标：8 宽瓣（cos(8θ)^10）+ 12 细瓣（cos(12θ)^20）+ 光环/叶环/珠核/露珠/金屑 + 强度缩放；全部加法叠到场景上。',
    uniforms: ['BaohuangStrength', 'BaohuangPulse', 'BaohuangTime'],
    api: ['veil-post'],
    reuse: '极坐标花瓣 = pow(max(0,cos(angle*N+phase)),K)，法阵/莲花/瞳类通用。'
  },
  {
    id: 'star_flow', title: '星流灾劫', kind: 'screen_post',
    members: ['star_flow', 'star_flow_post'],
    role: '星流：星带、星雾、星流、符文四形态 + 屏级星压。',
    mechanism: '几何 vUv.x 分段 0/2/4/6 切四形态；post 6 参数：径向涟漪扭曲 + 冷色分级 + 远景星点/星流 + 地平线辉 + 闪 + 升腾。',
    uniforms: ['StarFlowStrength', 'StarFlowWarning', 'StarFlowPressure', 'StarFlowFlash', 'StarFlowRise', 'StarFlowWave'],
    api: ['veil-post', 'veil-rendertype'],
    reuse: '屏幕空间“星点”= step(0.994, hash(floor(uv*vec2(240,135))))，比粒子便宜。'
  },
  {
    id: 'four_flame_cloud', title: '四焰云灾劫', kind: 'screen_post',
    members: ['four_flame_cloud'],
    role: '世界空间的火焰云顶 + 地面焦痕。',
    mechanism: 'post 里先用 screenToLocalSpace 求视线与世界云平面交点，深度剔除（云在地形后则不画）；三层 fbm 密度 + 边缘撕裂 + 热浪折射 + 云底煤/炉色 + 裂隙余烬 + 冲击环；无云时仍做去饱和/远景热烟。',
    uniforms: ['CloudCenterRelative', 'CloudRadius', 'CloudSeed', 'CloudIntensity', 'CloudLayer', 'CloudPhase', 'CloudProgress', 'CloudPressure', 'CloudImpactPulse', 'CloudBurnPulse'],
    api: ['veil-post', 'veil-shader'],
    reuse: 'ray-plane 云顶配方：仰角<=0 直接返回、云距>=场景距-0.35 直接返回，两条早退保证不穿屋顶。'
  },
  {
    id: 'chi_tong_fire', title: '赤瞳火', kind: 'combat_quad',
    members: ['chi_tong_fire'],
    role: '火焰四形态：云/焰舌/条/球。',
    mechanism: 'vMode 切四形态；fbm(q + fbm(q±t)) 双域扭曲；热度=vColor.r，颜色 red→orange→yellow 三段 smoothstep。',
    uniforms: ['FireTime'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '双域扭曲 fbm（q += fbm(q±t)*1.5）是所有火焰/烟的起点。'
  },
  {
    id: 'zheng_tiger', title: '峥虎领地', kind: 'entity_aura',
    members: ['zheng_tiger_atmosphere', 'zheng_tiger_stone', 'zheng_tiger_vein'],
    role: '山影/尘墙/地气/盘绕 + 岩石 + 地脉。',
    mechanism: 'atmosphere 用 vMode 四形态（尘墙/地气/山影/盘绕）；山影用负 UV 标记跳过上下裁切；石用 3D 噪声分层（烟煤/板岩/花岗/金脉/沟壑）+ 顶点色 + 边缘光；地脉是高斯芯+琥珀金三段。',
    uniforms: ['uAnchorRelative'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '“负 UV 标记”让一个 shader 服务多件套且不互相裁切；岩石分层配色可复用。'
  },
  {
    id: 'dan_dao_slash', title: '丹道斩击', kind: 'combat_quad',
    members: ['dan_dao_slash'],
    role: '单件弧形斩击。',
    mechanism: '共享 combat_soft_shape.vsh（UV 居中）；arc=1-smoothstep(0.34,0.88,|uv.y|)，两端 smoothstep 收口，40Hz 断裂；颜色用 vColor.g 编码穿透（银 vs 红）。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '“顶点色通道编码玩法状态”比多加 uniform 更省；弧带+两端收口是斩击通用形。'
  },
  {
    id: 'lang_jian', title: '浪剑', kind: 'combat_quad',
    members: ['lang_jian_wave', 'lang_jian_slash'],
    role: '水刃波 + 全屏刀光。',
    mechanism: 'wave：宽/体/芯/缘四层 + leading/trailing + grain/facets/shimmer 三层细纹；slash：屏幕空间沿方向投影 along/across，blade=smoothstep(0.11,0,across)*smoothstep(0.44,0.62,|along|)，中心留 guard。',
    uniforms: ['LangJianDirectionX', 'LangJianDirectionY', 'LangJianIntensity', 'LangJianProgress'],
    api: ['veil-rendertype', 'veil-post', 'veil-shader'],
    reuse: '全屏刀光配方（方向投影 + 中心 guard + progress 包络）可直接用于任何“屏幕斩”。'
  },
  {
    id: 'fu_ren_xin_mist', title: '缚人心毒雾', kind: 'combat_quad',
    members: ['fu_ren_xin_mist'],
    role: '带状毒雾。',
    mechanism: '轴向 axial + 侧向 lateral + 边缘噪声 + 卷曲 + 毒素条带 + 触须；中心更暗更绿，外缘亮绿。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '带状雾 = body*edgeNoise + 中心核 + 条带，三步即可。'
  },
  {
    id: 'xing_mou_eye', title: '星眸之眼', kind: 'combat_quad',
    members: ['xing_mou_eye'],
    role: '单个发光的眼。',
    mechanism: '极坐标：iris=smoothstep(0.80,0.18,r)，pupil=1-smoothstep(0.08,0.24,r)，星点=step(0.965,hash)，射线=sin(θ*18-t*2.4+r*12)。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '眼睛/瞳类通用：虹膜环 + 瞳孔 + 星点 + 放射纹。'
  },
  {
    id: 'piercing_rain', title: '穿心雨', kind: 'combat_quad',
    members: ['piercing_rain'],
    role: '雨丝/水针。',
    mechanism: '横向高斯芯 + 鞘 + tip 端收 + 折射抖动 + glint；双色 deepWater/silverWater 按 core 混合。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '雨/针/丝类：core=exp(-x²*82)，sheath=exp(-x²*7)，tip 两端 smoothstep。'
  },
  {
    id: 'tribulation_lightning', title: '劫雷', kind: 'combat_quad',
    members: ['tribulation_lightning'],
    role: '单道天雷。',
    mechanism: 'fbm 抖动芯（filamentOffset=(fbm-0.5)*0.26）+ 等离子鞘 + thread 螺纹 + 回击（0.76+0.24*|sin(t*23+seed)|）；冷辉+热芯双色。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '雷 = 白芯 + 冷鞘 + 回击闪；把 fbm 塞进芯的 x 偏移即可。'
  },
  {
    id: 'ink', title: '墨点 / 墨带', kind: 'combat_quad',
    members: ['ink_mote', 'ink_ribbon', 'ink_ribbon_bloom'],
    role: '最简三件：软圆点、顶点色线、×2.5 提亮层。',
    mechanism: 'mote 用 quad 局部坐标做径向软边并 discard 方角；ribbon 直接输出 vColor；bloom 版 ×2.5 保证过 bloom 阈值。',
    uniforms: [],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '“软圆点 + 顶点色 + 提亮层”是粒子/拖尾的最低成本方案。'
  },
  {
    id: 'immortal_gu_item_aura', title: '仙蛊物品辉光', kind: 'item_glow',
    members: ['immortal_gu_item_aura'],
    role: '物品栏/掉落物的仙蛊辉光。',
    mechanism: 'NEW_ENTITY + Sampler0=blocks atlas；alpha 邻域差取轮廓；10 套三色循环（vColor.r*9 选）；vColor.g 切内外层；星点=两个方向高次正弦乘积；呼吸。',
    uniforms: ['Sampler0'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '物品轮廓发光必须用 alpha 邻域差，不能靠 UV 形状；调色板循环适合任何“仙蛊/法宝”。'
  },
  {
    id: 'lei_yi_wing', title: '雷翼', kind: 'entity_aura',
    members: ['lei_yi_wing'],
    role: '翅膀三形态：羽翼/电流/菱形。',
    mechanism: '羽枝 pow(sin,12) + 电弧 exp(-|uv.x-boltCenter|*42) + 电流 pow(sin,10) + 金羽；vMode 切形态。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '羽翼 = 羽轴 exp + 羽枝 pow(sin) + 电弧，三件套。'
  },
  {
    id: 'delight', title: '山水之乐', kind: 'entity_aura',
    members: ['delight_in_mountain_and_water'],
    role: '四形态：水脉/墨/纸/宝珠。',
    mechanism: 'vMode 四段；水脉用 world.y 流动，墨用 hash 颗粒，纸用矩形距离 + 笔触，宝珠用径向 + 8 道射线。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '同一饰物按状态切“水/墨/纸/珠”，适合法宝多阶段。'
  },
  {
    id: 'man_as_before', title: '人如故', kind: 'entity_aura',
    members: ['man_as_before_apparition', 'man_as_before_rewind'],
    role: '显形四形态 + 屏幕回溯。',
    mechanism: 'apparition 四形态（脉/刻度/裂纹/扫描）；rewind 是屏幕反向波 + RGB 三通道分离 + 时间色环 + 暗角。',
    uniforms: ['ManAsBeforeIntensity', 'ManAsBeforeProgress', 'ManAsBeforeFatal'],
    api: ['veil-rendertype', 'veil-post', 'veil-shader'],
    reuse: '“显形”= 扫描线 + 裂纹 + 透明；屏幕回溯 = 反向波 + 色散。'
  },
  {
    id: 'menstrual_blood', title: '月经血', kind: 'entity_aura',
    members: ['menstrual_blood_curtain', 'menstrual_blood_loss'],
    role: '血幕/血柱 + 屏幕失血。',
    mechanism: 'curtain 三形态：血管场（veinField）/内层/血柱；loss 用心跳 sin(Progress*2π+t*1.8) 驱动径向偏移 + 血管纹 + 血红 tint。',
    uniforms: ['BloodLossIntensity', 'BloodLossProgress'],
    api: ['veil-rendertype', 'veil-post', 'veil-shader'],
    reuse: '心跳驱动径向偏移是“生命流失”类屏幕效果的通用手法。'
  },
  {
    id: 'formless_hand', title: '无相之手', kind: 'entity_aura',
    members: ['formless_hand_aura', 'formless_hand_volume'],
    role: '掌/雨/拳/抓 + 体积手掌。',
    mechanism: '屏幕空间手形 SDF（handDistance/fistDistance）+ 外辉/内边/雨尾；体积版用菲涅尔 + 噪声 + 掌心内脉 + 双光。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '“在 shader 里定义形状 SDF”适合任何招式剪影（掌/爪/印）。'
  },
  {
    id: 'water_enclosure', title: '水牢', kind: 'water_body',
    members: ['water_enclosure'],
    role: '球形水牢（jellyfish 雾复用同一 program）。',
    mechanism: '经度环流 + 逆流 + 焦散 pow(sin,12) + 泡沫 + 菲涅尔；法线来自顶点属性（POSITION_TEX_COLOR_NORMAL）。',
    uniforms: ['VeilRenderTime'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '球体水牢：经度+纬度双频流 + 焦散，成本低。'
  },
  {
    id: 'qi_wash', title: '洗气', kind: 'combat_quad',
    members: ['qi_wash'],
    role: '长条墨气。',
    mechanism: '三组 fbm 域扭曲（slow/cross/macro）+ 边缘阈值 + inkBody/vein/filament 三层 + world 颗粒。',
    uniforms: ['uQiType', 'uQiIntensity', 'uQiSeed', 'uFlowSign', 'uContact', 'uPortalAngle'],
    api: ['veil-rendertype', 'veil-shader'],
    reuse: '长条墨流 = 域扭曲 + 边缘 + 体积，三步；worldGrain 防贴图拉伸。'
  },
  {
    id: 'immortal_refinement', title: '炼蛊屏效', kind: 'screen_post',
    members: ['immortal_refinement'],
    role: '炼蛊过程的屏幕扭曲与六种图案。',
    mechanism: '6 种图案（水涡/内吸/齿轮/横波/晶体辐条/默认），每种自定义 themedDirection+themedPulse；不稳定度驱动色散与失败红；暗角。',
    uniforms: ['RefinementIntensity', 'RefinementHue', 'RefinementStability', 'RefinementPattern'],
    api: ['veil-post'],
    reuse: '“图案枚举”用 int uniform 分支，一个 shader 出六种炼法视觉。'
  },
  {
    id: 'nine_heavens_transition', title: '九重天转场', kind: 'screen_post',
    members: ['nine_heavens_transition'],
    role: '位面切换的全屏闪光 + 环。',
    mechanism: 'scene mix 主题色 + ring=1-smoothstep(0,0.95,|Progress*2-1|)。',
    uniforms: ['TransitionColor', 'TransitionStrength', 'TransitionProgress'],
    api: ['veil-post'],
    reuse: '最少参数的转场（3 个），适合先接通 post 管线。'
  },
  {
    id: 'mud_summoning_crab', title: '泥蟹 PBR 模型', kind: 'model_pbr',
    members: ['mud_summoning_crab_material', 'mud_summoning_crab_fallback'],
    role: '蒙皮 PBR 模型材质与降级。',
    mechanism: 'Position/Normal/Tangent/UV0/Joints/Weights + BoneMatrices；BaseColor/ao/emissive 三贴图 + 双方向光 + 高光；fallback 去贴图留单光+红闪。',
    uniforms: ['BaseColorSampler', 'AoSampler', 'EmissiveSampler', 'DisplayLight', 'HurtAmount'],
    api: ['veil-shader', 'veil-rendertype'],
    reuse: 'PBR 三件套读法：base_color=反照率，metallic_roughness 的 G=粗糙度/B=金属度；AO 在 R，发光在 emissive_mask。'
  },
  {
    id: 'sword_jiaolong', title: '剑蛟 PBR 模型', kind: 'model_pbr',
    members: ['sword_jiaolong_material', 'sword_jiaolong_fallback'],
    role: '剑蛟蒙皮 PBR 与降级。',
    mechanism: 'TBN 法线贴图 + 金属粗糙度 + 菲涅尔青边 + 扫描线（vScanPhase+PulsePhase）+ 碎裂热 + 脉冲；fallback 只留 base+菲涅尔+扫描线。',
    uniforms: ['BaseColorSampler', 'NormalSampler', 'MetallicRoughnessSampler', 'GlobalAlpha', 'PulsePhase', 'EdgeIntensity', 'ShatterHeat'],
    api: ['veil-shader', 'veil-rendertype'],
    reuse: '“主材质 + fallback”双 program 是 Veil 侧保证不黑模的标准做法。'
  }
];

export const FAMILY_BY_ID = Object.fromEntries(FAMILIES.map(f => [f.id, f]));
