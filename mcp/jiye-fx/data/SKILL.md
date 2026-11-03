# 几页特效规范 · SKILL（全量解析版）

> 对象：`素材/guzrlevelupdemoV5.4-特效与贴图/特效`（几页《升仙 demo》V5.4 导出资产）
> 范围：**469 个文件全部入账**——不是抽样。数字由 `tools/scan_jiye_fx.py` 复算，全表见 `几页升先/几页特效全表.md`。
> 方法：脚本解析 + 逐族精读 110 个片元着色器（≥60 行的 30 个逐行读，其余读主函数全量），JSON 全部机器校验（0 断链）。
> 用途：给升仙模组「按图施工」——要复刻哪种效果，直接查 §4 家族、§5 配方、§6 参数。

---

## 0. 先看哪一节

| 你要做的事 | 直接跳 |
|---|---|
| 我要一个新特效，从零搭 | §3 管线四件套 → §4 找同族模板 → §5 配方 |
| 我要抄某段算法 | §5 配方库（15 条，带 GLSL 片段） |
| 我要知道某参数怎么调 | §6 参数与量纲 |
| 我要接进升仙（有/无光子） | §11 迁移路线 |
| 我要查某个文件是干什么的 | `几页特效全表.md`（469 行全表） |
| 我要读 gzsm 模型 | §8 格式逆向 |

**证据等级**：A＝逐行读并复算；B＝读主函数＋结构，参数表机器抽取；C＝仅结构登记（全表覆盖）。本文凡标「★」为实测/复算结论，不是推测。

---

## 1. 全景：三栈并联，不是一套系统

```
                    ┌──────────────── 几页 demo V5.4 ────────────────┐
Java 侧提交几何 ───► │ rendertype(94, JSON 层链) ─► program(122, JSON) │ ─► vsh/fsh(177)
                    │        │                        │               │
                    │        └─ output ─► FBO(4) ─► post(32, JSON) ──┼─► veil:post
                    └────────────────────────────────────────────────┘
另外两栈：shaders/core 4 组（原版 ShaderInstance，光子吃）｜geo+anim 24 个（GeckoLib）
零散：gzsm(2, 私有网格)｜lss(1, GUI 样式)｜贴图 177 张
```

### ★ 计数对账（469）

| 目录 | 文件数 | 说明 |
|---|---|---|
| `pinwheel/shaders/program/` | **299** | 122 个 program JSON + 110 个 `.fsh` + 67 个 `.vsh` |
| `pinwheel/rendertypes/` | 94 | 渲染类型（层链） |
| `pinwheel/post/` | 32 | 后处理管线 |
| `pinwheel/framebuffers/` | 4 | MRT 离屏目标 |
| `pinwheel/shaders/include/` | 1 | `nine_heavens_sky.glsl` |
| `shaders/core/` | 12 | 4 组 ×（json+vsh+fsh） |
| `gzsm模型/` | 2 | `sword_jiaolong`（3.10 MB）/ `mud_summoning_crab`（871 KB） |
| `lss/` | 1 | `aperture_management.lss` |
| `geo/` + `animations/` | 24 | 12 模型 + 12 动画（见《几页建模动画规范-SKILL.md》） |
| **合计** | **469** | ✔ |

### ★ 交叉校验（0 断链）

- 每个 program 的 `vertex`/`fragment` 都能落到实际 `.vsh`/`.fsh`（或 `veil:blit_screen` 内置）；
- 每个 rendertype 的 `veil:shader` 都能落到 program；
- 每个 post stage 的 shader 都能落到 program；
- 每个 program 引用的 framebuffer（含 `:depth`）都存在（或由 post 内联定义）。
- 唯一「无 JSON 的 vsh」= `heaven_post.vsh`（历史遗留，无人引用）；`heaven_post.fsh` 反被 10 个 program 共用。
- 6 个 program 没有 rendertype/post 直接引用：`ink_ribbon_bloom`、`sky_vortex_bloom`、`sword_jiaolong_material`、`sword_jiaolong_fallback`、`mud_summoning_crab_material`、`mud_summoning_crab_fallback`——这些是 **Java 侧按名直取**（bloom 变体、PBR 主材质/降级），不是死文件。

---

## 2. 命名法：名字就是架构

一个效果 = 一个**家族前缀** + 若干**部件词**。部件词是封闭词表，读名字就知道它在管线哪一层：

| 部件词 | 含义 | 典型文件 |
|---|---|---|
| `_sky` | 天空盒（方向输入，无深度） | `aperture_sky`、`*_heaven_sky` |
| `_field` | 体积场写入趟（渲进 FBO，写 MRT） | `fan_wind_field`、`hua_qi_field` |
| `_composite` | 体积场合成趟（全屏采样 FBO） | `fan_wind_composite` |
| `_post` | 屏幕后处理（after_level / after_translucent） | `di_xian_post`、`liu_feng_post` |
| `_fallback` | 特性缺失时的降级（无 PBR / 无 FBO 支持） | `sword_jiaolong_fallback` |
| `_impact` `_surface` `_stream` `_core` `_edge` `_tip` | 一个招式的几何拆件：冲击环/面片/流柱/芯/边/尖 | `di_xian_*`、`corrosive_dark_current_stream_*` |
| `_bloom` | 只写 `veil:bloom` 的提亮层（单趟或第二趟） | `water_clothing_aura_bloom`、`sky_vortex_bloom` |
| `_aura` `_shell` `_silk` `_ornament` | 角色/物件外壳的层次：气场/壳/绸/饰 | `water_clothing_*` |
| `_volume` `_wave` `_boundary` `_erosion` `_glint` | 体积/波/边界/侵蚀/闪点 | `wander_world_*`、`datong_wind_*` |
| `_portal` `_mist` `_residue` `_skull` `_bone` `_void` `_fog` | 门/雾/残渣/颅/骨/虚空/雾团 | `corrosive_dark_current_*` |
| `_vein` `_pressure` `_apparition` `_curtain` | 脉络/压迫/显形/幕 | `liu_feng_vein`、`menstrual_blood_curtain` |
| `_art` | 「艺术扩边」——只负责好看、可整体淡出的附加层 | `wander_world_mirror_art`、`_trail_art` |
| `_glow` `_wind` `_atmosphere` `_stone` `_storm` `_cage` | 发光/风/氛围/石/风暴/笼 | `wind_flower_glow`、`zheng_tiger_*` |
| `_transition` `_mirror` `_treasure` `_manifestation` | 转场/镜面/宝/显化 | `nine_heavens_transition`、`baohuangtian_mirror` |

**纪律**：`家族_部件` 一旦定名，program / rendertype / post / FBO 四处同名（`guzrlevelup:<家族>_<部件>`）；改名必须四处同改。全表里 94 个 rendertype 有 83 个与对应 program 同名，不同名的 11 个都是「一个 program 被多个 rendertype 复用」（如 `aperture_world_core/edge/shell` 共用 `aperture_manifestation`）。

---

## 2.5 空间效果：94 个 rendertype 的真实分布

★2026-10-25 补：之前把重点放在了 post 上，**这是偏的**。用 `tools/scan_jiye_space.py` 按代码记号重扫一遍，
94 个 rendertype 的真实构成是：

| 坐标约定 | 数量 | 判据（代码记号） | 提交侧怎么写 |
|---|---|---|---|
| 世界坐标 | **63** | vsh 里 `Position - CameraPosition` | CPU 直接写世界坐标 |
| 相机相对 | 18 | vsh 里 `Position` 直接用、`+CameraPosition` 还原世界点 | CPU 先减相机 |
| PoseStack | 9 | vsh 用 `ModelViewMat`（`NEW_ENTITY` 等） | 跟实体渲染器走 |
| 远平面 | 4 | `clip.z = clip.w * 0.9995` | 相机相对的球/全屏，钉远平面 |

十种几何形态（`fx_space` 里每条都有网格做法/法线来源/坑）：

| 形态 | 参考程序 | 要点 |
|---|---|---|
| 球壳 | `iron_wall_bell`、`aperture_manifestation` | 法线 = `normalize(Position - center)`，不占属性；cull 必须 none |
| 体积盒 | `fan_wind_field` 等四套 | field 写 MRT + composite 全屏，深度守卫 0.0008 |
| 贴片 | `combat_soft_shape` 系 | UV 中心化成 -1..1 才能做对称软遮罩 |
| 丝带 | `ink_ribbon`、`spring_autumn_time_river` | 无 UV，宽度/透明烘进顶点色 |
| 天穹 | `sky_vortex`、十个 `*_heaven_sky` | 相机相对 + `clip.z=clip.w*0.9995` |
| 地面环 | `zheng_tiger_vein`、`datong_wind_boundary` | 抬 0.02~0.05 防 z-fighting |
| 实体挂件 | `water_clothing_*`、`immortal_gu_item_aura` | NEW_ENTITY 必须带 lightmap+overlay 层 |
| 水体 | `wander_world_water`、`water_enclosure` | 带 Normal 才能扰动；水面要写 depth |
| 雾团 | `ink_mote`、`ascension_mist` | 每点一个朝相机 quad，量级几百 |
| PBR 模型 | `sword_jiaolong_material`、`mud_summoning_crab_material` | 要有 mesh，shader 救不了 |

**一句结论**：屏幕后处理是少数派（32/94），世界几何是主体。要做「实体的」效果，
先在这十种里选形态，再决定坐标约定 —— 别一上来写 post。

### 2.5.1 六条万能公式（立体效果的真正复用点）

逐个读完 79 个世界几何 fsh 后的结论：**它们不是 79 种算法，而是六条公式的排列组合**。

| 公式 | 写法 | 幂次/系数经验 |
|---|---|---|
| 细丝/电弧/刻度 | `pow(max(0, sin(a*x + b*y - t*s + noise*k)), N)` | N=7~9 丝带、12~14 细丝、18 刻度；k=4~8 让线弯 |
| 柔核/光束截面 | `exp(-d*d*k)` | k=5 宽柱、34~48 细芯、72~90 几乎一线 |
| 横向双边淡出 | `sin(clamp(u,0,1) * PI)` | 免贴图收边；单侧用 `1-smoothstep(0.72,1,u)` |
| 椭圆/圆裁切 | `p=uv*2-1; if(dot(p,p)>1) discard;` | 椭圆靠 `p.y*1.08`，不改半径 |
| 世界锚定颗粒 | `hash21(floor(world.xz*18))` | 用 UV 会随拉伸变形；尺度 10~32 |
| 一个 quad 装三种材质 | `uv.x` 分段 0.5/2.5/4.5 或 `vMode` | UV 被占用后不能再当 0~1 采样 |

配套的「造型/合成」小技巧：`1 - max(eyeL,eyeR,nose)*0.92` 挖洞造剪影（骷髅）、
`step(0.965, hash(cell)) * exp(-d²·mix(90,260,seed))` 点星、`step(n, fract(u*k))` 虚线（k 取整数）、
整体乘 `pulse = 0.72+0.28*sin(t*4.8 + world.y*3.4)` 让静态纹理活起来。

### 2.5.2 十六张逐效果卡片（`fx_space <id>`）

`mirror-art` 镜花水月（视差分层）｜`trail-art` 水痕丝带｜`star-flow` 星流（UV 三段）｜
`lei-wing` 雷翼｜`corrosive-skull` 骷髅剪影｜`corrosive-stream` 暗流（6 行）｜
`delight` 山水之乐（4 形态）｜`treasure` 宝光｜`man-as-before` 人如故（4 形态）｜
`blood-curtain` 血幕｜`zheng-atmosphere` 峥虎气层（屏幕格+世界格）｜`water-enclosure` 水牢（带法线）｜
`iron-wall-bell` 铁壁钟罩（双趟）｜`sky-vortex` 天涡（远平面）｜`volume-field` 体积场四套同构｜
`sanqi-converge` 多股能量汇聚（升仙吸收三气：天地双柱 + 人气外壳 + 三色涡眼）。

每张卡片含：视觉 / 几何与坐标 / **编号步骤**（按代码顺序）/ 关键常量 / 可复用点 / 坑。

### 2.5.3 ★先定后端：全屏 post 还是世界几何（升仙实机踩出来的判据）

同一批特效，升仙先做全屏 → 改几何 → 又改回全屏，两个方向都只对一半。
**判据不是「哪个更高级」，是「观众在哪」**（`fx_space backend-screen` / `backend-geometry`）：

| | 全屏后处理 | 世界几何 |
|---|---|---|
| 效果在描述 | 整个世界的状态变了（天变、闪白、扭曲、回溯） | 某个具体的东西（球、钟罩、结界、水墙、影子） |
| 观众是 | 正在经历这件事的人 | 远处看到的那个东西 |
| 需要屏幕形变 | 要（拉伸/裂纹/色散/旋涡） | 做不到 |
| 会被地形遮挡 | 不会 | 会 |
| 走近会变大 | 不会 | 会 |
| ★致命风险 | **会挡视野**（必须自己留中心安全区） | **壳上有雾而观众在壳内 = 贴在眼睛上的膜** |

升仙的最终分派（2026-10-25 实机定稿）：

- **灾劫 → 全屏**。上一版做成包住玩家的球壳，实机反馈「太挡视野」。
  全屏版把纪律写进 shader 头注释：
  1. 准星区（屏幕中心 r < 0.20）**几乎不动** —— 所有氛围层乘 `keep = smoothstep(0.20, 0.72, r)`；
  2. **雾只给远景**（按场景深度 `far = smoothstep(0.90, 0.9995, depth)`），近处方块不糊；
  3. 前景元素（雨丝/余烬/风纹）一律 1~2 像素宽、alpha ≤ 0.16；
  4. 调色只到「压色/偏色」，**不改亮度曲线**（夜里渡劫照样看得清）。
- **转场 → 全屏**。碎窍/飞升/入窍是「世界被改写的一瞬」，要屏幕空间的形变。
- **仙窍 / 炼蛊 → 几何**。球心在物体上、半径几格，观众看的是「那里有个东西」。



## 3. 管线四件套（严格 schema）

> **★ 2026-10-25 追加：本节的 schema 有两个来源，别混。**
> 下表里的「分布/实测」来自几页 demo V5.4 的 469 个文件；字段名/枚举则用
> `shengxian/veil-neoforge-1.21.1-4.5.2.jar` 的 codec 逐个核对过。**demo 本身在 4.5.2 下可解析**
> （它的 `textures` 用的是裸字符串与 `{type:framebuffer}`），真正要小心的是网上流传的 1.20 写法：
>
> | 位置 | 常见旧写法 | 4.5.2 写法（现行） |
> |---|---|---|
> | `program.textures` 值 | `{"type":"asset","name":"..."}` | 裸字符串 `"ns:path"`，或 `{"type":"location","location":"..."}` / `{"type":"framebuffer","name":"..."}` |
> | `program.uniforms`（默认值表） | demo 里 `sword_jiaolong_material` / `_fallback` 两个文件有 | **4.5.2 没有这个键**，会被静默忽略；默认值写 Java 字段初值，或 `post.stages[].uniforms` |
> | Java 取后处理管理器 | `getPostPipelineManager()` | `getPostProcessingManager()` |
> | `post.stages[].uniforms` | 一般认为不支持 | 支持，**静态值**：`{"type":"float","value":[0.8]}`（float/int/mat2/mat3/mat4） |
> | post 管线的**激活** | 以为写了 `renderStage` 就会跑 | **必须 `PostProcessingManager.add(priority, id)`**；`renderStage` 只是每帧的 stage 过滤条件 |
>
> 反向验证（2026-10-25）：把 demo 的 430 个 JSON/GLSL 按 4.5.2 白名单跑一遍，**只有 2 个 error**——
> 就是上面那条 `program.uniforms`（`sword_jiaolong_material` / `_fallback`）。其余 428 个文件全部可解析，
> 说明 demo 的结构本身没有过期，过期的是「照抄网上 1.20 写法」这一步。
>
> **★2026-10-25 实机踩坑（最贵的一条）**：`prepare()/apply()` 只把管线放进 `pipelines` 映射表，
> 每帧真正执行的是 `activePipelines`，而它<b>只能由 `add()` 写入</b>。
> 症状极具欺骗性：日志照常打 `Loaded N post pipelines`、shader 也编译好了，
> 但画面一辈子不动 —— 因为那条管线从来没被遍历到。判断方法：在 `preVeilPostProcessing`
> 里打一行日志，没有就说明没激活（不是 shader 的问题）。
>
> 另外三条硬约束（4.5.2 codec 直接卡）：`layers` 写嵌套数组时**必须 ≥2 趟**（单趟平铺）；
> `post.stages[].in` 与 `out` **不能相同**；`blit.out` 省略时默认 `veil:post`、`in` 省略时默认当前绘制 FBO。
>
> 机器可查版：MCP 的 `fx_schema` 工具（9 张表，逐字段带 required/默认值/枚举）。

### 3.1 rendertype —— 顶点格式 + 图元 + 层链

```json
{
  "format": "POSITION_TEX_COLOR",
  "mode": "TRIANGLES",
  "bufferSize": "BIG",
  "sort": true,
  "layers": [
    { "type": "veil:shader", "name": "guzrlevelup:lei_yi_wing" },
    { "type": "minecraft:depth_test", "mode": "lequal" },
    { "type": "minecraft:cull", "face": "none" },
    { "type": "minecraft:write_mask", "color": true, "depth": false },
    { "type": "minecraft:transparency", "mode": "translucent" }
  ]
}
```

★ 实测分布（94 个）：

| 字段 | 取值分布 |
|---|---|
| `format` | `POSITION_TEX_COLOR` 71｜`POSITION_COLOR` 12｜`NEW_ENTITY` 8｜`POSITION_TEX_COLOR_NORMAL` 3 |
| `mode` | `TRIANGLES` 65｜`QUADS` 29 |
| `bufferSize` | `BIG` 47（持续/大几何）｜`TRANSIENT` 44（每帧重建）｜`SMALL` 3 |
| `sort` | true 62｜false 32（天空/加色/不透明不需要排序） |
| 趟数 | 单趟 77｜**双趟 17** |
| `transparency` | `translucent` 75｜`additive` 30｜`none` 4 |

层类型全集（111 次 shader / 111 次 depth_test / 111 次 write_mask / 109 次 transparency / 106 次 cull / 25 次 output / 8 lightmap / 8 overlay / 2 texture）：

| 层 | 取值 | 用途 |
|---|---|---|
| `veil:shader` | program 名 | 唯一必需层 |
| `minecraft:depth_test` | `lequal` / `always` | `always`＝无视深度（天空、贴地圈、HUD 级） |
| `minecraft:cull` | `none` / `back` | 体积/薄片必须 `none` |
| `minecraft:write_mask` | `color`/`depth` 布尔 | 半透明几乎都 `depth:false` |
| `minecraft:transparency` | `translucent` / `additive` / `none` | 加色用于雷、金气、灵纹 |
| `minecraft:output` | `veil:bloom` 或自定义 FBO | ★21 次写 bloom、4 次写体积场 FBO |
| `minecraft:lightmap` / `overlay` / `texture` | — | 只出现在 `NEW_ENTITY` 格式（实体/物品） |

#### ★ 双趟（17 个）：主层 + 自发光进 bloom

```json
"layers": [
  [ {shader}, depth_test lequal, cull none, write_mask(color, depth:false), transparency translucent ],
  [ {同一个 shader}, depth_test lequal, cull none, write_mask(color, depth:false), transparency additive,
    { "type": "minecraft:output", "framebuffer": "veil:bloom" } ]
]
```

这是几页最容易被漏掉的技法：**同一份几何画两遍**——第一遍正常混进主画面，第二遍加色写进 bloom 缓冲，于是「轮廓自发光」不用改 shader、也不用后处理就能得到。17 个双趟成员：

`aperture_world_edge`、`baohuangtian_treasure`、`delight_in_mountain_and_water`、`formless_hand_aura`、`iron_wall_bell`、`jellyfish_habitat_mist`、`man_as_before_apparition`、`menstrual_blood_curtain`、`piercing_rain`、`sky_vortex`、`spring_autumn_time_river`、`tribulation_lightning`、`wander_world_aura`、`wander_world_mirror_art`、`wander_world_trail_art`、`wander_world_volume_glint`、`zheng_tiger_vein`。

另有 `water_clothing_aura_bloom` / `water_clothing_ornament_bloom` 把第二趟拆成独立 rendertype（Java 侧两份都提交），`wind_flower_glow` 复用 `wind_flower` program 单独做 glow 层。

#### 四张现成配方（照抄即可）

| 用途 | format | mode | buffer | sort | 层链 |
|---|---|---|---|---|---|
| 世界半透明几何 | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | shader → depth lequal → cull none → write_mask(d:0) → translucent |
| 天空盒 | POSITION_COLOR | QUADS/TRIANGLES | TRANSIENT | ✘ | shader → depth lequal → cull none → write_mask(d:0) → translucent（vsh 里 `clip.z=clip.w*0.9998`） |
| 体积场写 FBO | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | shader → depth lequal → cull none → write_mask(d:0) → translucent → output=自定义 FBO |
| 实体饰物 | **NEW_ENTITY** | QUADS | TRANSIENT | ✔ | shader → depth lequal → cull none → **lightmap** → **overlay** → write_mask(d:0) → translucent |

### 3.2 program —— 顶点/片元 + 采样器绑定

```json
{ "vertex": "veil:blit_screen",
  "fragment": "guzrlevelup:fan_wind_composite",
  "textures": {
    "FanWindDataSampler":     { "type": "framebuffer", "name": "guzrlevelup:fan_wind_field", "sampler": 0 },
    "FanWindMaterialSampler": { "type": "framebuffer", "name": "guzrlevelup:fan_wind_field", "sampler": 1 },
    "FanWindDepthSampler":    { "type": "framebuffer", "name": "guzrlevelup:fan_wind_field:depth" }
  } }
```

- 顶点 4 种来源：① 内置 `veil:blit_screen`（33 个后处理程序）；② 共享 `.vsh`（见下表）；③ 自写独立 vsh；④ vsh/fsh 同名自包含。
- `textures` 三种值：`{type:framebuffer,...}`（MRT 附件，`sampler` 选附件号）、`{type:framebuffer,name:...:depth}`（深度）、字符串（`minecraft:textures/atlas/blocks.png` 这类原版资源）。★4.5.2 已无 `type:"asset"`；裸字符串等价 `{type:"location"}`。
- 本批 program JSON **没有** `definitions` / `blend` / `required_features` / `filter` 字段——全部走 rendertype 的层链控制状态。这比预想的简单，意味着「状态在 rendertype、算法在 shader」是硬分界。
- `di_xian_vine_surface` 是**别名程序**：`{"vertex":"liu_feng_surface","fragment":"di_xian_surface"}`——同一套代码，靠 rendertype/提交侧区分用途。同理 `aperture_world_core/edge/shell` 三个 program 与 `aperture_manifestation` 完全同源。

#### ★ 共享顶点表（一份 vsh 服务多个效果）

| vsh | 复用数 | 服务对象 |
|---|---|---|
| `liu_feng_surface` | **8** | `di_xian_impact/pressure/surface/vine_bloom/vine_surface` + `liu_feng_surface/vein/impact` |
| `aperture_manifestation` | 4 | `aperture_manifestation/world_core/world_edge/world_shell` |
| `combat_soft_shape` | 3 | `dan_dao_slash`、`lang_jian_wave`、`fu_ren_xin_mist` |
| `wander_world_volume` | 3 | `wander_world_volume/volume_glint/aura` |
| `datong_wind_boundary` | 3 | `datong_wind_boundary/core/erosion` |
| `water_clothing_shell` | 3 | `water_clothing_shell/silk/ornament` |
| `wander_world_wave_field` | 2 | `wander_world_wave_field/fallback` |
| `sword_jiaolong_material` | 2 | `sword_jiaolong_material/fallback` |
| `mud_summoning_crab_material` | 2 | `mud_summoning_crab_material/fallback` |
| `sky_vortex` | 2 | `sky_vortex/sky_vortex_bloom` |
| `ink_ribbon` | 2 | `ink_ribbon/ink_ribbon_bloom` |
| `heaven_post.fsh`（片元共享） | **10** | `*_heaven_post` 十个 |

**共享 vsh 的约定**：几何一律以世界坐标提交，vsh 里 `ProjMat * ViewMat * vec4(Position - CameraPosition, 1)`；`UV` 由提交侧给 0..1，需要中心化就在 vsh 里 `UV*2-1`（`combat_soft_shape` 明确注释了这一点）。

### 3.3 framebuffer —— 四套同构 MRT

```json
{ "width": "q.screen_width", "height": "q.screen_height", "autoClear": true,
  "color_buffers": [
    { "type":"texture", "format":"RGBA16F", "levels":1, "name":"FanWindDataSampler" },
    { "type":"texture", "format":"RGBA8",  "levels":1, "name":"FanWindMaterialSampler" } ],
  "depth": { "type":"texture", "format":"DEPTH_COMPONENT", "levels":1, "name":"FanWindDepthSampler" } }
```

四套（`fan_wind_field` / `hua_qi_field` / `iron_wall_field` / `wander_world_wave_field`）骨架完全一致：**RGBA16F 存数据（法线×0.5+0.5 + 覆盖率 a）、RGBA8 存材质（强度 r / 种类 g / 凝结 b / 标志 a）、自带深度**。尺寸用表达式 `q.screen_width/height` 跟随屏幕。

### 3.4 post —— 后处理

```json
{ "renderStage": "after_translucent_blocks", "priority": 1210,
  "stages": [ { "type":"veil:blit", "shader":"guzrlevelup:fan_wind_composite",
                "in":"minecraft:main", "out":"veil:post", "clear":true } ] }
```

★ 实测：`renderStage` = `after_level` 27 / `after_translucent_blocks` 5；stage 共 33 个 `veil:blit` + 1 个 `veil:copy`；只有 6 个声明 `priority`，且是**连续的 5 个 + 1 个特例**：1210 fan_wind、1215 iron_wall、1220 hua_qi、1225 tou_sheng_domain、1230 lang_jian_slash、1700 spring_autumn_cicada_rewind。同一 stage 内按 priority 定序，这五个体积/领域类必须按这个顺序叠。

**唯一的多段范本**（`spring_autumn_cicada_rewind`，全库最高优先级）：

```json
{ "renderStage": "after_level", "priority": 1700,
  "framebuffers": { "spring_autumn_scene": {"depth": true}, "spring_autumn_flow": {"depth": true} },
  "stages": [
    { "type":"veil:copy", "in":"minecraft:main", "out":"spring_autumn_scene", "color":true, "depth":true },
    { "type":"veil:blit", "shader":"guzrlevelup:spring_autumn_cicada_trail",    "in":"spring_autumn_scene", "out":"spring_autumn_flow", "clear":true },
    { "type":"veil:blit", "shader":"guzrlevelup:spring_autumn_cicada_composite","in":"spring_autumn_flow",  "out":"veil:post", "clear":true } ] }
```

★ **post 管线层没有 uniform 这个键**（4.5.2 的 `CompositePostPipeline` 无此字段）——所有颜色/进度/强度都是 Java 每帧推到 program 上的。最硬的证据：10 个 `*_heaven_post` 共用同一份 `heaven_post.fsh`（读 `HeavenTint`/`HeavenFog`），program JSON 完全一样，十种天色全靠代码推 uniform。

★ 但 4.5.2 的 **blit stage 自带 `uniforms`**（静态值，float/int/mat2~4），适合把「不动的常量」写进 JSON，把「每帧变的量」留给事件——两种都写时事件在 stage 之后执行，以事件为准。

### 3.5 GLSL 约定（Veil 侧）

```glsl
#veil:buffer veil:camera VeilCamera      // UBO 全量成员：ProjMat / ViewMat / IProjMat / IViewMat / CameraPosition / NearPlane / FarPlane / CameraBobOffset
#include veil:space_helper               // 提供 screenToLocalSpace(uv, depth) → 相机相对坐标
uniform float VeilRenderTime;            // 秒；110 个 fsh 里 71 个用
in vec2 texCoord;  out vec4 fragColor;   // 全屏 pass 标准出入口
flat in float vMode;                     // 枚举/标志用 flat，避免插值
```

- 法线优先用 `normalize(cross(dFdx(worldPos), dFdy(worldPos)))` 现算（省顶点属性），并用 `dot(n, viewDir)<0` 翻面。
- `gl_FrontFacing` 用于「只画正面」的闪点层（`wander_world_volume_glint`、`zheng_tiger_stone`）。
- 每个 fsh 都遵守同一条早退纪律：`if (alpha < 0.003~0.008) discard;`，全部在计算前先 `if (coverage < 0.003) { fragColor = base; return; }`。
- MRT 写法：`layout(location=0) out vec4 xxxData; layout(location=1) out vec4 xxxMaterial;`

---

## 4. 家族逐族解析（40 族，全量）

先给总表（成员数为 program 数；括号内是 rendertype / post / FBO 特有件）：

| # | 家族 | 成员 | 干什么 | 关键点 |
|---|---|---|---|---|
| 1 | aperture | manifestation / world_core / world_edge / world_shell / portal / sky（6） | 仙窍世界显化、门户、天空 | `uMode` 0..6 一大 shader 吃全阶段；world_* 是同一 program 的三种 rendertype |
| 2 | nine_heavens | 10 sky + 10 post（20） | 九重天变色 | 2 行 fsh + 共享 include；10 post 共用一份 fragment，颜色靠 Java |
| 3 | baohuangtian | sky / mirror / treasure（3） | 宝黄天天象、镜面、宝物 | 镜面＝屏幕反射 + 柠檬色 rim；宝物 `uMode` 0/1/2 三形态 |
| 4 | sky_vortex | sky_vortex / bloom（2） | 天道竖瞳巨涡 | 对数螺旋 + 高斯环 + 竖瞳 + 双速反向涡 |
| 5 | fan_wind | field / composite（2 + 1 post + 1 FBO） | 罡风体积场 | 双层场骨架；准星安全区；深度守卫折射 |
| 6 | hua_qi | field / composite（2 + 1 post + 1 FBO） | 化气体积场 | 同上 + 色散三采样 + 边缘辉光（场纹理邻域差） |
| 7 | iron_wall | field / bell / composite（3 + 1 post + 1 FBO） | 铁壁钟罩 | 场 + 铜钟壳 + 折射；`candidateDepth` 三条件回退 |
| 8 | wander_world_wave | field / fallback / composite（3 + 1 post + 1 FBO） | 忘川水墙 | 厚度吸收 `exp(-absorption*thickness)`；首人称淡出 |
| 9 | spring_autumn | time_river / cicada_trail / cicada_composite（3） | 春秋蝉时间回溯 | 11 采样加权 + 3-stage copy；唯一 copy 用法 |
| 10 | wander_world | volume / volume_glint / aura / water / trail_art / mirror_art（6） | 忘川水体全家桶 | 壳体积 + 镜面 + 尾迹 + 水面；`vIsTrail`/`vIsStrip` 切形态 |
| 11 | water_clothing | aura / aura_bloom / shell / silk / ornament / ornament_bloom / transform（7） | 水衣（穿在身上的水） | NEW_ENTITY 格式 + 双趟 bloom + 屏幕涟漪 |
| 12 | di_xian | impact / pressure / surface / vine_bloom / vine_surface / post（6） | 地先灾劫 | 6 件几何 + 11 参数 post；`liu_feng_surface` 共享顶点 |
| 13 | liu_feng | surface / vein / impact / post（4） | 流风灾劫 | 10 参数 post（去饱和+远景分级+雾+暗角+闪白+恢复） |
| 14 | datong_wind | boundary / core / erosion / post（4） | 大同风 | 3 件几何 + 扭曲+暗角 post |
| 15 | wind_flower | wind_flower / surface / wind（3；`wind_flower_glow` 是 rendertype，复用主 program） | 风花灾劫 | 15 参数 post；两组长条噪声；`lineSuppression` 保 HUD |
| 16 | wind_thunder | storm / cage（2） | 风雷暴 + 风雷笼 | 云面/柱体双形态；笼＝螺旋扭曲 + 回击闪 |
| 17 | corrosive_dark_current | impact / portal / post / residue / skull×4 / stream×4（12） | 蚀暗流 | 12 件套拆件最细；颅骨用 5 个 SDF 形拼 |
| 18 | tou_sheng | domain（1） | 偷生领域 | 世界空间球域：`screenToLocalSpace` 反算 + 天空射线补 mask |
| 19 | taiqing | empty_space / field / post（3） | 太清空境 | 玉质薄片 + 屏幕折射（射线-平面求交） |
| 20 | tianyuan | baohuang_lian（1） | 天元宝皇莲 | 8 瓣 + 12 细瓣 + 露珠 + 金屑，全在屏幕极坐标 |
| 21 | star_flow | star_flow / post（2） | 星流灾劫 | 4 种几何形态 + 星空/星流/地平线三层 |
| 22 | four_flame_cloud | four_flame_cloud（1） | 四焰云灾劫 | 世界空间云平面 ray-plane + 深度剔除 + 热浪折射 |
| 23 | chi_tong_fire | chi_tong_fire（1） | 赤瞳火 | 4 种 `vMode`：云/焰舌/条/球；fbm 域扭曲 |
| 24 | zheng_tiger | atmosphere / stone / vein（3） | 峥虎领地（山影/石/地脉） | 4 种 atmosphere 形态；石用 3D 噪声分层 |
| 25 | dan_dao_slash | 1 | 丹道斩击 | 弧带 + 两端收口 + 银/红双色（绿通道编码穿透） |
| 26 | lang_jian | wave / slash（2） | 浪剑（剑气/屏幕斩） | 波＝多层水刃；斩＝屏幕空间刀光 |
| 27 | fu_ren_xin_mist | 1 | 缚人心毒雾 | 轴向 + 侧向软边 + 毒素条带 |
| 28 | xing_mou_eye | 1 | 星眸之眼 | 极坐标虹膜/瞳孔/星点 |
| 29 | piercing_rain | 1 | 穿心雨 | 雨丝 = 高斯芯 + 鞘 + 闪点 |
| 30 | tribulation_lightning | 1 | 劫雷 | fbm 抖动芯 + 等离子鞘 + 回击 |
| 31 | ink | mote / ribbon / ribbon_bloom（3） | 墨点/墨带 | 最简三件：软圆点、顶点色、×2.5 进 bloom |
| 32 | immortal_gu_item_aura | aura / inner（2） | 仙蛊物品辉光 | 10 套三色循环调色板；内外两层 |
| 33 | lei_yi_wing | 1 | 雷翼 | 3 形态：羽翼/电流/菱形脉冲 |
| 34 | delight | 1 | 山水之乐 | 4 形态：水脉/墨/纸/宝珠 |
| 35 | man_as_before | apparition / rewind（2） | 人如故（显形/回溯） | 4 形态显形 + 屏幕反向波色散 |
| 36 | menstrual_blood | curtain / loss（2） | 月经血（幕/流失） | 血管场 + 悬浮细胞；屏幕心跳 |
| 37 | formless_hand | aura / volume（2） | 无相之手 | 手掌 SDF（`handDistance`/`fistDistance`）+ 体积壳 |
| 38 | water_enclosure | enclosure（1，被 jellyfish 复用） | 水牢 | 经度环流 + 焦散 + 泡沫 |
| 39 | qi_wash | 1 | 洗气 | 长条墨流：域扭曲 + 边缘 + 体积 |
| 40 | misc | immortal_refinement / nine_heavens_transition / baohuangtian_mirror / mud×2 / sword×2（7） | 炼蛊/转场/镜面/两套 PBR 模型 | 见 §4.10 |

下面挑 10 个最有复用价值的家族展开。

### 4.1 aperture（仙窍世界）——一个 shader 吃全生命周期

`aperture_manifestation.fsh` 304 行，是全库最大的单文件。`uMode`：

| uMode | 画面 | 关键手法 |
|---|---|---|
| 0 | 路径/丝带（细丝 + 断裂） | fbm2 双层 + `pow(abs(sin),3.5)` 压力线 |
| 1 | 圆形涟漪门 | 极坐标 + 三重波纹 + 边缘 `1-smoothstep(0.58,1,r)` |
| 2 | **整颗仙窍星球** | 球面法线域扭曲 → 大陆/海洋/河/山/雪/道纹/仙池 七层；`uIntensity` 兼作覆盖率（<0.9 时按像素哈希 discard，做「显化中」的抖动） |
| 3 | 内壁/地幔 | 低模 fbm + 内光 + 内脉 |
| 4 | 飘带 | `sin(uv.y*π)^0.72` 软边 + 呼吸 |
| 5 | 云海/极光 | fbm + 菲涅尔 + `uTurbulence` 撕碎 |
| 6 | 刻纹带 | 条纹 + 篆刻（`pow(sin(uv.x*53))`）+ 余烬 |
| else | 汇流 | 向心汇聚 + 白/主题色 |

★ 星球模式（mode 2）是**程序化星球**的完整配方：先用 `rotateY` + 3D 噪声把球面法线扭曲成「地形法线」，再用 `fbm3` 取大/中/细三层拼大陆度，用 `smoothstep` 阈值切海/滩/林/山/雪，用「到某轴的距离」画仙池环，用 `abs(fbm-0.505)` 画河道与道纹。全程无贴图、无 UV 展开。

`aperture_portal` 是它的轻量版（3 模式：椭圆世界/飘带/土带），`aperture_sky` 是天空版（两风格：星云 + 水墨海天，见 §5.7）。

### 4.2 体积场四套（fan_wind / hua_qi / iron_wall / wander_world_wave）——最值钱的骨架

四套完全同构，只有算法参数不同：

```
第一趟 field（rendertype/*_field → FBO）：
  几何：贴地/贴墙的薄壳或体积网格
  vsh  ：输出 vMaterial（r 强度 / g 种类 / b 凝结）、vWorldPos
  fsh  ：dFdx×dFdy 现算法线 → 面向度 edgeFade → 覆盖率 = vMaterial.a*edgeFade*噪声
         写 xxxData = vec4(normal*0.5+0.5, coverage)
         写 xxxMaterial = vec4(强度, 种类, 凝结, 1)
第二趟 composite（post/*.json → program/*_composite.fsh）：
  全屏 blit，采样 场景色/场景深/场数据/场材质/场深度
  coverage<0.003 → 原样返回（零开销）
  法线 → viewNormal → 偏移 = viewNormal.xy*(基础+种类项) + fbm 扰动
  深度守卫：candidateDepth + 0.0008 < fieldDepth 或 sceneDepth + 0.0008 < fieldDepth → 放弃偏移
  三采样色散（A/B/C 各取不同通道）→ 混场景 → 叠加凝结/边缘辉光
```

四套差异表：

| 场 | 种类 g 的语义 | 偏移强度 | 特色 |
|---|---|---|---|
| fan_wind | 0..1 风刃程度 | 0.0014+0.0048g+0.0034blade | **准星安全区**：中心 0.27×0.22 椭圆内偏移降到 42% |
| hua_qi | 0.2 吸入 / 0.42 成形 / 0.62 剑 / 0.7 发射 / 0.88 冲击 | 0.0015+0.0068sword | 场纹理**邻域差**取边缘 → 边缘辉光；紫/青双色 |
| iron_wall | 0.0 壳 / 0.8 波 | 0.0012+0.0038strength | 第二趟用铜色 `vec3(0.90,0.54,0.16)`；钟体另有 `iron_wall_bell` |
| wave | 0..1 泡沫/亲和 | 0.0012+0.0008thickness | 真·厚度：`screenToLocalSpace` 反算世界点，`exp(-absorption*thickness)` 做水体吸收 |

**结论**：以后做「热浪/毒雾/结界/水墙/领域」直接换 field 与 composite 的噪声/颜色，骨架一个字不用改。

### 4.3 灾劫屏级 post——统一公式

`di_xian_post`（11 参数）、`liu_feng_post`（10）、`wind_flower`（15）、`wind_thunder_cage`（7）、`star_flow_post`（6）、`datong_wind_post`（5）、`corrosive_dark_current_post`（15）、`four_flame_cloud`（10）、`tou_sheng_domain`（4）、`taiqing_empty_space_post`（9）、`tianyuan_baohuang_lian`（3）全部遵守同一条流水线：

```
1 取场景色 + 深度
2 去饱和：mix(scene, luminance, strength*k)
3 远景分级：smoothstep(0.7,0.995,depth) → 乘主题色/加雾
4 压力：exp(-len(centered)*k) 或 edge = smoothstep(0.3,0.8,r) → 压暗
5 暗角：smoothstep(0.3,0.8,r) * 强度
6 闪白：mix(scene, 白, clamp(Flash*k,0,0.6~0.92))
7 恢复：scene = mix(scene, 原图, Recovery) 或 *= 0.985+Recovery*0.015
8 输出 alpha=1（后处理永远不透明）
```

**这就是「一套灾劫天象」的模板**：把 2/3/4 的主题色和强度换成自己的，7 保留。所有 `<家族>Strength/Phase/Progress/FogDensity/Disturbance/Flash/Recovery` 都是这条流水线的旋钮。

`tou_sheng_domain` 与 `four_flame_cloud` 是这条公式的**世界空间**变体：先用 `screenToLocalSpace` 把像素反算成世界点（或射线），再做球域/云平面的空间判断，因此「离开领域/看向别处」会自然消失，而不是全屏贴一张色纸。这是屏级特效不廉价的关键。

### 4.4 天空系

- `aperture_sky`（205 行）：两风格。`uWaterStyle<0.5` 星云（fbm 星云 + 双层星点 + 尘埃带 + tonemap `c/(1+0.18c)`）；`>0.5` 水墨海天（深海体积 / 双潮带 / 油彩笔锋 / 双旋涡 / 泡沫焦散金屑 五层）。★ 所有纹理都采样**单位球方向**，跨越立方体六面仍连续；`clip.z=clip.w*0.9998` 钉在远平面。
- 九重天：`include/nine_heavens_sky.glsl` 一个 `renderNineHeavensSky()`，10 个 `*_heaven_sky.fsh` 只有 2 行（调用它），10 个 `*_heaven_post` 共用 `heaven_post.fsh`。十色的差异全在 `uBaseColor/uHorizonColor/uAccentColor/uStarDensity/uMotionScale/uPhase` 六个 uniform。
- `baohuangtian_sky`：青金 + 古金 + 云带 + 尘点，`c/(1+0.16c)`。
- `sky_vortex`（172 行）：先两层域扭曲，再在扭曲后的极坐标里做**对数螺旋** `sin(θ*arms - log(r+0.035)*twist + phase)`；主涡 5 宽臂 + 银锋线，反向次级涡，外层墨云墙；中心是「天道竖瞳」（黑球 + 虹膜纤维 + 竖向瞳孔 + 角膜高光 + 事件视界环 + 曼陀罗刻度 + 8 道天柱）。`sky_vortex_bloom` 是它的纯能量版（109 行，只输出 HDR 亮度给 bloom）。

### 4.5 水体/领域家族（wander_world / water_enclosure / water_clothing / formless_hand）

- `wander_world_volume`：壳体积。`apparentThickness = (1-facing)*0.72 + (1-frontFace)*0.52 + max(0,-topness)*0.18` → 背面更暗、正面清亮，靠透明排序出厚度。
- `wander_world_mirror_art` / `_trail_art`：**艺术扩边层**。`seamFade`/`throatFade` 保证「真正接缝永远由基础水体承担」，动态层只从内侧长出来；三层 fbm + 视差 `viewDir.xz/|viewDir.y|`。
- `wander_world_water`：同一 shader 服两种几何——圆盘（`diskRimCoordinate` 把圆边重映射成 0..1）与条带（`stripEdge`），靠 `vIsStrip` 切换。★「圆盘和条带对边的定义不同，硬套一个 UV 会露馅」的注释就写在文件里。
- `water_enclosure`：经度环流 + 逆流 + 焦散 + 泡沫，`fresnel` 用顶点法线（POSITION_TEX_COLOR_NORMAL 格式）。
- `water_clothing` 七件套：`shell`（外壳）/`silk`（绸，最平最柔）/`ornament`（饰，最亮）/`aura`（气场）/两套 bloom/`transform`（穿上瞬间的屏幕涟漪）。
- `formless_hand`：`handDistance(p)`/`fistDistance(p)` 是**手形 SDF**（在 fsh 里定义，屏幕空间），`vMode` 0 掌/1 雨/2 拳/4 抓；体积版用菲涅尔 + 噪声 + 掌心内脉。

### 4.6 实体/物品层

- `immortal_gu_item_aura` + `_inner`：NEW_ENTITY 格式，`Sampler0=blocks atlas`；用 alpha 邻域差取**物品轮廓**（`edge = smoothstep(0.025,0.72, texel.a - neighborMin)`）；10 套三色循环（`dreamPalette`）按 `vColor.r*9` 选；内层/外层由 `vColor.g` 切；星点 = 两个方向的高次正弦乘积。
- `lei_yi_wing`：3 形态（羽翼：羽枝 `pow(sin,12)` + 电弧 `exp(-|uv.x-boltCenter|*42)`；电流；菱形）。
- `delight_in_mountain_and_water`：4 形态（水脉/墨/纸/宝珠）。
- `man_as_before_apparition`：4 形态（脉/横纹刻度/裂纹/扫描）。
- `menstrual_blood_curtain`：3 形态（血管场/内层/血柱）。
- `zheng_tiger_atmosphere`：4 形态（尘墙/地气/山影/盘绕），**负 UV 标记**（`vUv.x<-0.5` 表示山影不做上下裁切）——一个很有用的「同 shader 多件套」技巧。

### 4.7 杀招/招式几何

- `combat_soft_shape.vsh`（共享）：`vUv = UV*2-1`，让所有片元都用对称软遮罩。
- `dan_dao_slash`：`arc = 1-smoothstep(0.34,0.88,|uv.y|)` + 两端 `smoothstep(-1,-0.72,uv.x)`；颜色 `mix(silver, red, 1-smoothstep(0.18,0.42,vColor.g))`——**用绿通道标记穿透**。
- `lang_jian_wave`：宽/体/芯/缘四层 + 轴向 leading/trailing + 三层细纹（grain/facets/shimmer）。
- `lang_jian_slash`：全屏刀光，沿 `LangJianDirection` 投影到 along/across，`blade = smoothstep(0.11,0,across)*smoothstep(0.44,0.62,|along|)`，中心留 `centerGuard` 防止糊准星。
- `tribulation_lightning` / `piercing_rain` / `xing_mou_eye` / `chi_tong_fire` / `zheng_tiger_vein` / `star_flow` / `ink_*`：都是「一根/一片/一球」的 2D 图元，靠 `exp(-k*x²)` 做芯、`smoothstep` 做鞘、`pow(sin)` 做节律。

### 4.8 屏幕镜头级

- `man_as_before_rewind`：极坐标反向波 + RGB 三通道分离（`direction*chroma`）+ 时间色环。
- `menstrual_blood_loss`：心跳 `sin(Progress*2π + t*1.8)` 驱动径向偏移 + 血管纹。
- `spring_autumn_cicada_trail`：11 次沿反向向量采样、权重 `exp(-i/10*2.35)`，每层轻微偏绿 → 「时间倒流」的拖影；`composite` 做径向色散 + 去饱和 + 暗角 + 落叶脉 + 秋色。
- `immortal_refinement`：6 种炼蛊图案（水涡/内吸/齿轮/横波/晶体辐条/默认），每种自定义 `themedDirection` 与 `themedPulse`，再叠加不稳定度色散与暗角。
- `water_clothing_transform` / `baohuangtian_mirror`：屏幕涟漪/镜面，都是「按半径取反/横向翻转 + 微扰 + 主题色 + 暗角」。
- `nine_heavens_transition`：转场，`ring = 1-smoothstep(0,0.95,|Progress*2-1|)`。

### 4.9 PBR 模型两套（sword / mud）

- `sword_jiaolong_material`：Position/Normal/Tangent/UV0/Joints/Weights/FragmentCenter/FragmentMeta，`BoneMatrices` 蒙皮；法线贴图 → TBN；金属粗糙度贴图；Fresnel 青边 + 扫描线 + 碎裂热 + 脉冲。`_fallback` 去掉贴图只留 BaseColor + 菲涅尔 + 扫描线。
- `mud_summoning_crab_material`：BaseColor/ao/emissive 三贴图 + 双方向光 + 高光；`_fallback` 只留 BaseColor + 单方向光 + Hurt 红闪。
- ★ 这两个是**唯一带蒙皮（Joints/Weights/BoneMatrices）**的 program，其余全部是无骨骼几何。

### 4.10 杂项

`qi_wash`（洗气长条墨流）、`immortal_gu_item_aura_inner`（内层）、`water_enclosure`（水牢）、`jellyfish_habitat_mist`（复用 water_enclosure program，换 rendertype）、`ink_ribbon_bloom`（×2.5 提亮）、`sky_vortex_bloom`（纯 HDR）。

---

## 5. 配方库（15 条，可直接抄结构）

### 5.1 体积壳菲涅尔（最常用）

```glsl
vec3 n = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
if (dot(n, viewDir) < 0.0) n = -n;
float fres = pow(1.0 - clamp(dot(n, viewDir), 0.0, 1.0), 3.2);
float thickness = clamp((1.0-facing)*0.72 + (1.0-frontFace)*0.52, 0.0, 1.0);
color = mix(clearWater, abyss, thickness*0.76);
alpha = vColor.a * (0.12 + thickness*0.13 + fres*0.12);
```

### 5.2 深度守卫折射（四套体积场共用）

```glsl
vec2 offset = viewNormal.xy * (0.0012 + strength*0.0038) + fbm2(worldXZ*1.5 + t) * 0.0013;
vec2 uv = clamp(texCoord + offset, 0.001, 0.999);
float cand = texture(DiffuseDepthSampler, uv).r;
if (sceneDepth + 0.0008 < fieldDepth || cand + 0.0008 < fieldDepth
    || abs(cand - sceneDepth) > 0.015) uv = texCoord;   // 三个条件任一不满足就放弃
vec3 refr = texture(DiffuseSampler0, uv).rgb;
```

### 5.3 准星安全区（防止屏幕中心糊住准星）

```glsl
vec2 d = (texCoord - 0.5) / vec2(0.27, 0.22);
float safe = mix(0.42, 1.0, smoothstep(0.18, 0.72, length(d)));
offset *= safe;
```

### 5.4 云平面 ray-plane（世界空间云，不被屋顶穿透）

```glsl
float cloudY = centerRelative.y + mix(52.0, 23.0, layer/3.0);
float dist = cloudY / rayDir.y;                 // rayDir.y<=0 → 直接返回
if (dist <= 0.0 || dist >= sceneDistance - 0.35) return;   // 被地形挡住
vec3 p = rayDir * dist;
vec2 uv = (p.xz - center.xz) * 0.072 + drift;
float density = smoothstep(0.30, 0.69, fbm(uv*0.36)*0.55 + fbm(uv*0.78)*0.37 + fbm(uv*1.48)*0.20);
```

### 5.5 多层 fbm 域扭曲（天空/墨/水通用）

```glsl
float warpA = fbm(uv*1.85 + vec2(t*0.024, -t*0.019));
float warpB = fbm(uv*2.75 + vec2(-t*0.016, t*0.022) + vec2(warpA,-warpA)*1.45);
vec2 warped = uv + vec2(warpA-0.5, warpB-0.5)*0.16;
```

### 5.6 对数螺旋臂 + 高斯环（涡旋/曼陀罗）

```glsl
float spiralRibbon(float th, float r, float arms, float twist, float phase, float soft) {
    float w = abs(sin(th*arms - log(r+0.035)*twist + phase));
    return 1.0 - smoothstep(0.06, soft, w);
}
float gaussianRing(float r, float c, float w){ float x=(r-c)/w; return exp(-x*x); }
```

### 5.7 一套 shader 十种天色

1. `include/nine_heavens_sky.glsl` 写死算法，暴露 6 个 uniform；
2. 10 个 `*_heaven_sky.fsh` 各 2 行调用；
3. 10 个 `*_heaven_post` program 共用 `heaven_post.fsh`；
4. Java 按当前天/维度推 `uBaseColor/uHorizonColor/uAccentColor/uStarDensity/uMotionScale/uPhase`（post 侧推 `HeavenTint/HeavenFog`）。

### 5.8 双趟 bloom（不改 shader 的发光边）

rendertype 写两层（见 §3.1），第二层 `additive` + `output: veil:bloom`。**适合所有「轮廓要发光」的贴片/壳体**。

### 5.9 时间回溯 11 采样

```glsl
vec3 acc = vec3(0.0); float wsum = 0.0;
for (int i = 0; i < 11; i++) {
    float s = float(i)/10.0;
    float w = exp(-s*2.35);
    vec2 uv = clamp(texCoord + reverseVector*s*(1.0+s*2.8), 0.001, 0.999);
    acc += texture(DiffuseSampler0, uv).rgb * w;
    wsum += w;
}
scene = acc / wsum;
```

### 5.10 屏幕震动/色散（三件套）

```glsl
float chroma = instability * intensity * (0.0012 + edge*0.0024);
scene.r = texture(S, uv + dir*chroma).r;
scene.b = texture(S, uv - dir*chroma).b;
```

### 5.11 2D 图元软遮罩（杀招/雨/雷/眼）

```glsl
float core   = exp(-x*x*58.0);          // 芯
float sheath = 1.0 - smoothstep(0.34,1.0,abs(x));  // 鞘
float tip    = smoothstep(0.0,0.12,uv.y)*(1.0-smoothstep(0.72,1.0,uv.y));
float alpha  = vColor.a * tip * sheath * (0.32 + core*0.92);
```

### 5.12 数据驱动粒子（光子 core）

```glsl
#moj_import <photon:particle_utils.glsl>
float radial = getCurveValue(SamplerCurve, 0, clamp(radius,0.0,1.0));
vec4 earth  = getGradientValue(SamplerGradient, 0, clamp(uv.y*0.58 + grain*0.42, 0.0, 1.0));
```

### 5.13 阶段参数组（一个效果一套 uniform）

`di_xian` 12 个、`wind_flower` 17 个、`four_flame_cloud` 11 个、`corrosive_dark_current` 16 个——命名统一 `<家族><语义>`，Java 侧按灾劫进度推。**不要一个阶段一个 shader**；一个 shader 吃整条时间线。

### 5.14 拖尾丝带：每帧采样 + 面向相机（daodao 实例）

实例：`guzhenren-ui` 刀道附属「巨剑斩 / 刀灵」的刀光（`DaodaoJuJianRenderer` / `DaodaoLingRenderer`），零 Veil、零光子，只有原版 `RenderType.eyes` + 一张软边贴图。

```java
// ① 每帧采样进环形缓冲（nanoTime 计时，超窗出队）——不要按 tick 采，20Hz 会一顿一顿
trail.addLast(new long[]{System.nanoTime(),
    Double.doubleToLongBits(p.x), Double.doubleToLongBits(p.y), Double.doubleToLongBits(p.z)});
while (trail.size() > MAX_POINTS) trail.pollFirst();
long windowNanos = (long) (WINDOW_S * 1_000_000_000L);
while (!trail.isEmpty() && now - trail.peekFirst()[0] > windowNanos) trail.pollFirst();
// ② 每点宽度方向 = 前后段平均切线 × 指向相机 ⇒ 相邻段共用端点，接缝连续
Vec3 prev = k > 0 ? pts[k].subtract(pts[k - 1]) : pts[1].subtract(pts[0]);
Vec3 next = k < n - 1 ? pts[k + 1].subtract(pts[k]) : pts[n - 1].subtract(pts[n - 2]);
Vec3 side = prev.add(next).cross(cam.subtract(pts[k])).normalize();
// ③ 顶点按「相对实体原点」写（poseStack 原点 = 实体，世界坐标必须减实体位置）
vc.addVertex(pose.last(), (float)(p.x - ent.x), (float)(p.y - ent.y), (float)(p.z - ent.z))
  .setColor(r, g, b, (int)(Mth.clamp(alpha, 0, 1) * 255)).setUv(0.5F, v)
  .setLight(LightTexture.FULL_BRIGHT);
```

实测参数：窗 0.3s（刀灵）/ 0.55s（巨剑）、段数 10~32、慢速按段长 `segLen/0.25` 收细（环绕游动只留一丝尾迹）。**挥击角度 / 命中判定 / 拖尾必须同一个规则层取数**（`DaodaoJuJianRules` / `DaodaoLingRules`）——三处各写一份数学必错位：横切刀光挥反过、竖切扫过面侧对镜头第一人称只看一条线（改成刃尖轨迹光带才可见）。

### 5.15 零依赖加法混合刀光：RenderType.eyes + 软边贴图

```java
// 材质：原版 eyes = 加法混合 + 全亮 + 贴图，不需要任何前置库
VertexConsumer vc = buffers.getBuffer(RenderType.eyes(TRAIL_TEX));
// 贴图约定：u=0.5（对称软边）；v=0 透明内缘 → v=1 亮刃缘 ⇒ 宽鞘 + 窄芯两层叠
// 淡出：年龄 0=最新最亮 → 1=最老透明；幂次比线性收得自然
static float fade(float age) {
    float f = Mth.clamp(1.0F - age, 0.0F, 1.0F);
    return f * f * (0.35F + 0.65F * f);
}
```

两层结构：刃尖光带（面向镜头双面，α≈0.85）+ 扫过面大鞘（α≈0.30，内缘随年龄收窄）。颜色走数据（`trailColor` 0xRRGGBB）。原版粒子一律不用——拖尾即全部特效。

---

## 6. 参数与量纲速查

### 6.1 全局

| uniform | 含义 | 量纲/范围 | 谁用 |
|---|---|---|---|
| `VeilRenderTime` | 客户端秒 | 秒，持续增长 | 71 个 fsh |
| `DiffuseSampler0` | 主画面 | 屏幕 UV | 25 个 |
| `DiffuseDepthSampler` | 场景深度 | 0..1 非线性 | 16 个 |
| `VeilCamera.CameraPosition` | 相机世界坐标 | 格 | 体积/世界类 |
| `gl_FragCoord.z` | 当前片元深度 | 0..1 | 软交界 |

### 6.2 各家族（节选；全表见机器索引）

| 家族 | uniform 组 | 语义 |
|---|---|---|
| aperture | `uMode,uProgress,uTurbulence,uIntensity,uSeed,uGrade,uPathColor,uPortalCutout,uPortalNormal,uPortalPlaneCenter,uPortalHalfWidth,uPortalHalfHeight` | 模式 0..6 / 进度 / 湍流 / 强度兼覆盖率 / 种子 / 品阶 / 主题色 / 门户挖洞（法线+平面中心+半宽高） |
| di_xian | `DiXianStrength,Phase,Progress,FogDensity,Disturbance,Flash,Recovery,VineProgress,WrappedVines,BindingStrength,ExtraDamageProgress` | 强度/相位/进度/雾/扰动/闪/恢复/藤进度/缠藤数/束缚/额外伤害 |
| liu_feng | `LiuFengStrength,Phase,Progress,FogDensity,Disturbance,Flash,DirectionX,Z,Turbulence,Recovery` | 同上 + 风向 |
| wind_flower | `FlowerStrength,Gust,Warning,Flash,Seed,Phase,DirectionX,Z,Turbulence,Recovery,TintStrength,FogDensity,Disturbance,Desaturation,LineSuppression` | 阵风/预警/冲击/线抑制（保 HUD） |
| four_flame_cloud | `CloudCenterRelative,Radius,Seed,Intensity,Layer,Phase,Progress,Pressure,ImpactPulse,BurnPulse` | 世界中心/半径/层 0..3/阶段/压力/冲击/燃烧 |
| wind_thunder_cage | `CageStrength,Warning,Flash,Aftershock,Pressure,Round,Seed` | 笼强度/预警/回击/余震/压迫/圈数 |
| star_flow | `StarFlowStrength,Warning,Pressure,Flash,Rise,Wave` | 星流六参数 |
| corrosive | `CorrosiveDarkCurrentStrength,Phase,Recovery,StreamProgress,Erosion,Flash,DirectionX,Z,SkullVisibility,MouthCharge,StreamFront,StreamDirection,StreamTurbulence,ArenaPressure,Recovery` | 暗流 + 颅骨 + 竞技场压迫 |
| baohuangtian | `BaohuangStrength,Pulse,Time,MirrorIntensity` | 宝皇莲/镜面 |
| tou_sheng | `TouShengCenter,Radius,Phase,Active` | 世界球域 |
| taiqing | `TaiqingStrength,Jade,Warning,Dive,Flash,Refraction,FrontCount,Front[16],Direction[16]` | 玉境 + 最多 16 道「剑痕前沿」用于屏幕折射 |
| spring_autumn | `RewindProgress,RewindIntensity,FlowSpeed,LandingPulse,MemoryOpacity,EffectSeed` | 回溯 |
| refinement | `RefinementIntensity,Hue,Stability,Pattern` | 炼蛊 6 图案 + 失败色 |
| water_clothing | `WaterClothingIntensity`、`GlobalAlpha/PulsePhase/EdgeIntensity/ShatterHeat`、`DisplayLight/HurtAmount` | 水衣/剑蛟/泥蟹 |
| item aura | `Sampler0`（atlas） | 物品轮廓 |

### 6.3 量纲经验（从全库反推，照这个给数值不会翻车）

| 场景 | 数值 |
|---|---|
| 屏幕 UV 偏移（折射） | 0.0012 ~ 0.0068；闪烁级 0.0005 |
| 深度比较 epsilon | **0.0008**（统一） |
| 世界距离/厚度 | 0.55 ~ 7.5（水吸收）；42~120（细节淡出） |
| 时间频率 | 慢呼吸 0.04~0.4 /s；中速 1~6 /s；闪烁 8~23 /s |
| 高斯核 | `exp(-x*x*k)`，k=16（宽鞘）~150（星点） |
| 噪声频率 | 大 0.03~0.3；中 1~5；细 8~30 |
| alpha 阈值 | 0.003 / 0.004 / 0.006 / 0.008（四种，不要混用同一份代码里的两个） |

---

## 7. 光子栈：`shaders/core` 四组（原版 ShaderInstance）

这四组是**原版 `ShaderInstance` JSON**（声明 `samplers`/`attributes`/`uniforms`），不是 Veil program。光子 `CustomShaderMaterial` 按名引用；也可进光子 postfx 图。

### 7.1 crystal_ball（碎窍晶球）——阶段参数化教科书

- uniforms：`ProjMat/ViewMat/Center/CamPos/Time/Bright/Omen/Crack/Collapse/Fade`
- 顶点：世界坐标直写，透传 `vWorldPos/vColor`
- 片元：`n=normalize(vWorldPos-Center)`（球面坐标当法线）；`stripeSpeed=2+Omen*5+Crack*7`；三组球面正弦取 min 得裂纹，`crackSpread=smoothstep(0.12,0.92,Crack+stripe*0.22)` 控制扩散；裂纹处压暗到 `(0.008,0.012,0.022)`；`Collapse` 整体提亮 `(0.85,0.92,1.0)*pow(1-fres,2)*1.8`；`alpha=(0.45+fres*0.8)*(1-Collapse*0.72)`，颜色与 alpha 同乘 `Fade`。

### 7.2 crystal_shard（碎片）

`vColor.rgb` 携带碎片色相，`vColor.a` 携带寿命；片元只有 `col=vColor.rgb*1.15`。

### 7.3 aperture_transit（升仙转场）

- uniforms：`Time/Progress/Resolution/Accent/Grade/Opacity/Reveal/Style`
- 两风格：`Style>0.5` 水道（fbm 域扭曲 + 三层流带 + 双尺度焦散 + `bubbleLayer` 气泡 + 液态界膜 + 圆形 `Reveal` 揭幕）；否则星流隧道（极坐标网格 + 反半径 `0.085/r` 做纵深喷涌 + 水墨长绢 + 4 层破界环 + 竖瞳）。
- ★ 接缝修正：`atan` 在负 X 轴有 ±π 分支，作者把周期噪声改成**用圆周方向向量采样**（`fbm(direction*3.15 + ...)`），消除左侧接缝——这是全库最值得学的一行注释。
- `Reveal` 用 `smoothstep(revealRadius-0.095, +0.095, |(uv-0.5)*(aspect,1)|)`，从中心向外揭开，而不是整屏淡出。

### 7.4 zheng_tiger_earth_qi（乙土之气）

- `#moj_import <photon:particle_utils.glsl>` → `getCurveValue(SamplerCurve,0,t)`、`getGradientValue(SamplerGradient,0,t)`
- `#moj_import <photon:particle.glsl>`（vsh）→ `getParticleData()` 取 Position/UV/Color
- 深度软化：`separation = texture(SamplerSceneDepth, screenUv).r - gl_FragCoord.z; softIntersection = max(0.38, smoothstep(0.0,0.0032,separation));`
- 微折射：`vec2(grain-0.5, 0.5-grain)*0.0011*alphaShape`
- `U_ViewPort` 换 screenUv：`(gl_FragCoord.xy - U_ViewPort.xy)/max(U_ViewPort.zw,1)`

---

## 8. GZSM 二进制模型（本次把能证的都证了）

### 8.1 已证实（A 级）

```
0   "GZSM"
4   u32 version          2 | 3
8   u32 vertexCount      13702 | 44015
12  u32 indexCount       13776 | 150006
16  u32 vertexStride     40 | 56      ★不是 headerSize
20  u32 meshCount?       1 | 1
24  float[7]             未知参数（1.0, -0.3531, 0.0, -0.266, 0.6253, 0.7374, 0.268 / v2: 1.0, -4.0002, 0, -2.172, 3.99, 4.47, 4.47）
52  u32 boneCount        45 | 18
56  u32（=1）            meshCount / 标志
60  u16 nameLen + name   "hurt"（v2）| "sword_shape"（v3）
```

- **索引缓冲在文件末尾**：`indexStart = len - indexCount*4`，uint32；样本 min/max = `0..vertexCount-1`（44014 / 13701），三连即为三角形。★ 用这条可以反推 stride：`vertexStart = indexStart - vertexCount*stride`，两个样本都整除。
- **顶点记录**：`+0` 起 3 个 float = 位置（样本值 0.19~2.33，符合模型坐标）；v3 的 `+44` 起两枚 0..1 浮点（0.4963 / 0.3867，相邻顶点相同 → 更像材质或光图参数，未必是 UV）；其余字段为压缩/打包（非 float）。
- **骨骼**：`boneCount` 后是一串 4×4 单位矩阵（绑定姿态），紧随名称与少量计数之后（数十字节处）。
- **动画段落**：v2 在网格段之后能读到 ASCII 剪辑名 `idle`(63436)、`special`(66330)、`walk`(204587)，每段前有 u16 长度与 u16/u32 计数、后跟浮点关键帧；v3 无剪辑名（只有骨骼）。
- **仍未知**：法线/切线/骨骼权重/关节索引的打包方式（尝试过半浮点与 snorm，未达到 93% 以上匹配，不做断言）；动画曲线的字段序；材质表（PBR 贴图是按命名约定找的，不在文件里）。

### 8.2 结论与用法

- 这是**本体私有网格格式**，不是 GeckoLib，也不兼容 glTF。**不建议花时间逆全**：升仙要做剑蛟级材质，直接走 glTF/OBJ（光子 `IModelSource` 支持），或者用「程序化剑体 + PBR 三件套贴图」。
- 若只需要碰撞/包围盒：按 header 读 `vertexCount/indexCount/vertexStride`，跳过 `vertexStart` 直接读位置 float3 与末尾索引，足够算 AABB。
- PBR 资产：`textures/effect/sword_jiaolong_{base_color,metallic_roughness,normal}.png`；泥蟹另有 `_ao`/`_emissive_mask`（见 §10）。

---

## 9. LSS：GUI 样式表（CSS 子集）

`aperture_management.lss` 只有选择器 + 两条属性：

```css
#aperture-management-root, #management-header, #management-list, #management-detail { overflow: hidden; }
#management-close, #management-refresh, #management-back { transition: opacity .12s, transform .12s; }
.management-list-row { transition: opacity .12s, transform .12s; }
.management-detail-panel { transition: opacity .14s, transform .14s; }
```

**范式**：状态变化交给 `transition`，不要手写补间；面板/列表一律 `overflow:hidden` 防溢出。做升仙仙窍管理界面时可直接照这个思路（ldlib2 侧用动画器等价实现）。

---

## 10. 贴图（177 张）

| 目录 | 数量 | 内容 |
|---|---|---|
| `item/` | 112 | 蛊虫/方子/残方（主体，不是特效） |
| `block/` | 33 | 作物/炉子 |
| `entity/immortal_gu/` | 12 | 12 仙蛊（与 geo/anim 三件套同名） |
| `entity/`（根） | 5 | 泥蟹/剑蛟等模型贴图 |
| `gui/killermove/` | 6 | 杀招盘图标 |
| `effect/` | 3 | 剑蛟 PBR 三件套：`base_color` / `metallic_roughness` / `normal` |
| `mob_effect/` | 6 | 药水效果图标 |

★ PBR 约定（sword/mud shader 读法）：`base_color` = 反照率 + alpha；`metallic_roughness`：**G=粗糙度，B=金属度**；`normal` 为切线空间；泥蟹另有 `ao`（R）与 `emissive_mask`（RGB 眼睛发光）。

---

## 11. 接进升仙：三层迁移路线（不拷资产，只学结构）

### 11.1 现状约束

- 发布双份：`有光子版`（12 jar）/`无光子版`（10 jar）；视觉必须走「可分支的桥」，业务代码禁硬写特效实现。
- 红线：不抄商业模组资产；只重写算法与结构。
- 所以这份文档的定位是**技法与 schema 参考**，不是素材来源。

### 11.2 A 层：零新依赖（现在就能做）

| 素材 | 接法 |
|---|---|
| 菲涅尔壳 + 厚度（§5.1） | 写进升仙自己的 `RenderType` + 自制 shader（原版 `ShaderInstance`），或退化成多层粒子 |
| 软遮罩 2D 图元（§5.11） | 杀招/剑气/雷/雨全部适用；`combat_soft_shape` 的 UV 居中约定直接照抄 |
| 双趟 bloom（§5.8） | 无 Veil 时退化为「第二层加色 + 原版发光纹理」，或光子 bloom 后处理 |
| 阶段参数组（§5.13） | 升仙的灾劫/升仙劫按 `Omen→Crack→Collapse→Fade` 四段推 uniform |
| **刀光拖尾（§5.14/§5.15）** | **daodao 实战实例：零新依赖，`RenderType.eyes` + 每帧采样丝带；参考实现见 `特效工坊/demo_blade_trail/`** |
| `aperture_transit` 的接缝修正 | 任何极坐标噪声都适用（用方向向量采样替代 atan 角） |
| `zheng_tiger_earth_qi` 的深度软化两行 | 三气圈/雷云贴场景不再硬切（光子材质里可用 `SamplerSceneDepth`） |
| LSS transition 范式 | 仙窍面板动效 |

### 11.3 B 层：有光子（已具备）

- 把 §7 四组思路做成 `assets/shengxian/shaders/core/*`（原版 JSON），用光子 `CustomShaderMaterial` 按名引用；uniform 每帧推（`Time/Omen/Crack/Collapse/Fade/Bright`）。
- 粒子颜色/形状走 `SamplerCurve`/`SamplerGradient`（光子 `NumberFunction`），不写死在 GLSL——这是几页 shader 与光子体系的正确接法。
- 与现有 `ShengxianFx` 桥一致：有光子走材质，无光子走原版粒子。

### 11.4 C 层：引 Veil（要改发布形态）

只有需要「屏幕级 + 体积级」时才值得：天空盒、体积场、灾劫 post。最小落地清单：

1. 依赖 `foundry.veil:veil-neoforge-1.21.1:4.x`（版本以 Modrinth 为准），发布从双份变三份或并入构建开关；
2. 拷 **schema 不拷文件**：`rendertype` 层链 / `program` 纹理绑定 / `framebuffer` MRT / `post` stage，四份 JSON 各写一个最小样例；
3. 先做一套体积场（`fan_wind` 骨架）验证管线，再扩家族；
4. Java 侧只需三件事：提交几何到 rendertype、每帧推 uniform、注册 post。

### 11.5 不建议

- 不要逆 gzsm 做剑蛟（成本 ≫ 收益，glTF 更稳）。
- 不要照抄 94 个 rendertype 的数值（那是几页的美术口径）；抄**层链结构**与**算法骨架**，数值按升仙的视觉重调。

---

## 12. 自检

### 12.1 脚本断言（可重复）

```bash
python tools/scan_jiye_fx.py --check
```

期望：`errors 0`，且 counts = `files_total 469 / programs 122 / rendertypes 94 / posts 32 / framebuffers 4 / core 4 / shared_vsh 2 / includes 1`。
生成全表与摘要：`python tools/scan_jiye_fx.py`（写 `几页升先/几页特效全表.md`、`_tmp_reverse/jiye_fx_index.json`、`_tmp_reverse/jiye_shader_digest.txt`）。

工坊 MCP 自检：

```bash
cd mcp/jiye-fx && node test/smoke.mjs
```

期望：`30/30 PASS`（含 122 个 program 全量链路可查、8 种 kind 生成后 0 error/0 warning）。


### 12.2 人工清单（8 条）

- [ ] 新特效命名符合 `家族_部件`，四处同名；
- [ ] 需要自发光边 → 用双趟（第二趟 additive→bloom），不是改 shader；
- [ ] 屏幕折射必须带深度守卫三条件 + `0.0008`；
- [ ] 全屏后处理必须在 `coverage<0.003` 时原样返回；
- [ ] 所有片元有 `discard` 早退，阈值统一（0.003/0.004/0.006/0.008 选一个并全家族一致）；
- [ ] 天空盒 vsh 用 `mat3(ViewMat)` + `clip.z=clip.w*0.9998`；
- [ ] 共享 vsh 的几何以世界坐标提交、UV 0..1、需要中心化就在 vsh 做；
- [ ] post JSON 不写 uniform（颜色由代码推），同族多色共用一份 fragment。

---

## 13. 附录

### 13.1 证据等级

- **A（逐行）**：`aperture_manifestation`、`aperture_sky`、`aperture_transit`、`crystal_ball`、`zheng_tiger_earth_qi`、`four_flame_cloud`、`sky_vortex(+bloom)`、`wind_flower`、`wander_world_mirror_art/trail_art`、`immortal_gu_item_aura`、`zheng_tiger_atmosphere`、`hua_qi_composite`、`fan_wind_composite`、`wave_composite/field/fallback`、`tou_sheng_domain`、`qi_wash`、`formless_hand_aura/volume`、`wander_world_volume/water`、`water_enclosure`、`immortal_refinement`、`aperture_portal`、`star_flow`、`wind_thunder_cage`、`spring_autumn_cicada_trail`、`baohuangtian_sky`、`spring_autumn_time_river`、`iron_wall_bell/field`、`heaven_post`、`nine_heavens_sky.glsl`、`lss`、`gzsm` 头部与索引。
- **B（主函数全量）**：其余 80 个 ≤60 行 fsh（`jiye_small_bodies.txt` 逐条过）。
- **C（结构登记）**：全部 94 rendertype / 32 post / 4 framebuffer / 67 vsh 的字段与引用关系（机器校验）。

### 13.2 生成物

| 文件 | 内容 |
|---|---|
| `几页升先/几页特效全表.md` | 469 个文件的全表（262 条表格行；program 含 uniforms/采样器/标签；rendertype 含层链；post 含链路） |
| `_tmp_reverse/jiye_fx_index.json` | 机器索引（可再生成） |
| `_tmp_reverse/jiye_shader_digest.txt` | 122 program 摘要（uniforms/纹理/标签/注释） |
| `_tmp_reverse/jiye_small_bodies.txt` / `jiye_mid_bodies.txt` / `jiye_big1/2.txt` | 精读中间稿 |
| `tools/scan_jiye_fx.py` | 扫描器 + 交叉校验（0 断链） |

### 13.3 与旧文《guzrlevelupdemoV5.4-特效制作学习.md》的勘误

| 旧说法 | 实测 |
|---|---|
| 「program 299 个」 | 299 是**文件数**：122 JSON + 110 fsh + 67 vsh；program 是 **122** |
| 「demo 的 32 个 post 里 31 个是单 stage」 | 32 个 post 共 33 blit + 1 copy；多 stage 只有 1 个（3 段）——结论对，计数应为 31 单段 + 1 三段 |
| 「94 个 rendertype 各一套层链」 | **17 个是双趟**（主层 + bloom 层），另有 2 个独立 `*_bloom` rendertype |
| 「gzsm 未逆」 | 已证 header/stride/索引/位置/UV/骨骼/动画名；仅属性打包与动画编码未解 |
| 「program 全用 veil:blit_screen / 自写 vsh」 | 有 **10 个 vsh 被多 program 复用**（最多 8 个），`heaven_post.fsh` 被 10 个 program 共用 |
| 「贴图 177：entity 12」 | entity 根 5 + immortal_gu 12 = 17 |

---

## 14. 配套工具：几页特效工坊 MCP

位置 `mcp/jiye-fx/`（Node + 官方 MCP SDK，已注册到 `~/.workbuddy/mcp.json`，在连接器页 Trust 后生效）。

它把本文从「读」变成「做」：AI 用工具查链路、出方案、生成资源包，并当场静态校验。

| 工具 | 回答本文哪一节 |
|---|---|
| `fx_chain` | §1/§3：任意 program 的 rendertype 层链 → program → shader → uniform → post → FBO → 调用路径 |
| `fx_mechanism` | §3 的机制版：一帧时序、层链状态机、uniform 绑定时机、MRT、post 顺序、热重载、性能 |
| `fx_api` | §7 + §11：Veil / 光子 / 原版回退的**已核对签名**与坑 |
| `fx_recipes` | §5 的 15 条配方（13 条 GLSL/JSON + 2 条 Java 拖尾/刀光，daodao 实例） |
| `fx_families` / `fx_search` / `fx_uniforms` | §4 / §6：家族卡（43 张，比本文 40 族拆得更细）、技法检索、参数量纲 |
| `fx_plan` → `fx_create` → `fx_validate` | §5/§11：一句话 → 资源包 + Java 胶水 → 0 error |
| `fx_resource` | 直接 grep 本文或全表 |

生成物落在 `特效工坊/<name>/`：`assets/<modid>/pinwheel/...` + `java/<package>/<Family>Fx.java` + `README.md` + `fx.json`（spec 快照，可重放）。

**边界**：Veil 没有装进本机工程，Java 签名取自 Veil 1.21 wiki 与源码（`VeilRenderType.get` / `ShaderManager.getShader` / `ShaderUniformAccess` / `VeilEventPlatform.preVeilPostProcessing`）；换版本以 jar 为准。生成物是「骨架 + 高质量默认算法」，美术数值仍需按项目调。

---

*本文与全表由 `tools/scan_jiye_fx.py` 对 469 个文件复算生成；精读日期 2026-10-25。*
