# 几页特效工坊（jiye-fx MCP）

把「想做一个什么特效」变成**可运行的 Veil 特效包**：rendertype / program / vsh / fsh / post / framebuffer + Java 调用代码，并当场静态校验。

它不是"素材搬运工"——所有规则来自对几页 demo V5.4 **469 个文件**的实测（见 `几页升先/几页特效规范-SKILL.md`），生成物遵循同一套纪律：

- 早退 `discard` / `return`（强度 0 时零开销）
- 屏幕折射必须带 **深度守卫三条件 + 0.0008**
- 全屏扭曲必须留**准星安全区**
- 主题色只走 uniform，不写进 GLSL
- post 管线层不写 uniform（静态值写在 blit stage 的 `uniforms`，动值由 Java 事件推）
- 自发光边用**双趟 rendertype**（第二趟 additive → `veil:bloom`）
- 发光拖尾（无 Veil 时）：`RenderType.eyes` 加法混合 + **每帧采样**丝带 + 幂次淡出（见 `fx_recipes` 的 `per-frame-trail` / `additive-eyes`）

## 工具

| 工具 | 作用 |
|---|---|
| `fx_stats` | 索引来源、469 文件计数、43 家族覆盖缺口（应为 0） |
| `fx_families` | 按 kind/关键词列家族，看同类效果怎么做 |
| `fx_chain` | **给 program 名，返回完整链路**：rendertype 层链（含多趟）→ program → vsh/fsh → uniform → post stage → FBO → 调用路径 |
| `fx_search` | 按技法标签/uniform/名字搜 |
| `fx_jiye` | **几页资产库**：253 个 pinwheel 资产（122 program / 94 rendertype / 32 post / 4 FBO / 1 include）。`list` 列目录（带真 GL 编译状态）、`show` 拿**完整源码**、`find` 按关键词找同款、`copy` 连依赖闭包拷进升仙工程 |
| `fx_space` | **空间效果目录**：**后端选型**（全屏 vs 几何，判据＝「观众在哪」）+ 4 坐标约定 + 10 几何形态 + **6 条万能公式** + **16 张逐效果卡片**（步骤/常量/复用/坑）+ 5 段提交骨架 |
| `fx_schema` | **Veil 4.5.2 权威资源格式**（rendertype/layer/program/textureSource/post/post-stage/framebuffer/ubo/java），按真实 jar 核对 |
| `fx_api` | Veil / 光子 / 原版回退的调用方式（签名逐个 javap 核对 + 实编译通过） |
| `fx_mechanism` | **底层机制**：一帧时序、层链状态机、uniform 绑定时机、MRT、post 顺序、热重载、性能、两栈选型 |
| `fx_recipes` | 15 条可抄配方（体积壳、深度守卫、云平面、双趟 bloom… + 每帧拖尾丝带、零依赖加法混合刀光） |
| `fx_plan` | **一句话出方案**：选 kind、起 ASCII 名、色板、uniform 默认值、Java 步骤、风险 |
| `fx_create` | **生成完整特效包**（含 Java + README），自动校验 |
| `fx_validate` | 静态校验目录：JSON / 交叉引用 / GLSL / 命名 / bloom 趟 |
| `fx_java` | 只生成 Java 胶水 |
| `fx_uniforms` | 参数语义与量纲 |
| `fx_resource` | 读规范全文或 grep 段落 |

## 安装 / 启动

```bash
cd mcp/jiye-fx
npm install            # 已装好可跳过
node server.mjs        # stdio 模式
node test/smoke.mjs    # 31 项自检
python ../../tools/javac_fx.py   # 用真 jar 编译 特效工坊/*/java（0 error 才算通过）
```

注册到 WorkBuddy（`~/.workbuddy/mcp.json`）：

```json
{
  "mcpServers": {
    "jiye-fx": {
      "command": "node",
      "args": ["E:/MOD-mc/任务/guzhenren-shengxianfushudefushu/mcp/jiye-fx/server.mjs"],
      "env": { "JIYE_FX_ROOT": "E:/MOD-mc/任务/guzhenren-shengxianfushudefushu" }
    }
  }
}
```

写完配置后，在 WorkBuddy 连接器管理页右上角的「自定义」入口点 **Trust**，否则不会启动。

## 典型流程（AI 照这个走）

1. `fx_plan({ goal: "血色领域，进入后屏幕泛红、远处血雾、脉动" })`
   → 得到 kind、名字、色板、uniform 默认值、参考家族。
2. `fx_create({ name, kind, modid, colors, outDir? })`
   → 生成到 `特效工坊/<name>/`，返回校验结果。
3. `fx_validate({ dir })`（`fx_create` 已自动跑一次）
   → 必须 0 error；warning 逐条决定改不改。
4. 按 `README.md` 把 `assets/` 合进 `shengxian/src/main/resources/`，Java 放进源码树。
   —— `shengxian/build.gradle` 已挂 `compileOnly` Veil（4.5.2），生成物里的 `foundry.veil.api.*` **可以直接编译**，不用再改依赖。
5. 进游戏 `F3+T` 热重载；先把 Strength 设 0 确认零开销，再拉满调层次。

## 生成目录

```
特效工坊/<name>/
├── assets/<modid>/pinwheel/
│   ├── rendertypes/<family>.json
│   ├── shaders/program/<family>{,_field,_composite,_post}.{json,vsh,fsh}
│   ├── post/<family>.json
│   └── framebuffers/<family>_field.json
├── java/<package>/<Family>Fx.java
├── README.md
└── fx.json                 # spec 快照（可重放）
```

## 实战实例（非 Veil，零新依赖）

| 目录 | 内容 |
|---|---|
| `特效工坊/demo_blade_trail/` | **刀光拖尾**（guzhenren-ui 刀道附属实战）：每帧采样 + 面向相机 + `RenderType.eyes` 加法混合。Java 参考实现 + 贴图 + 踩坑表；技法卡见 `fx_recipes`（`per-frame-trail` / `additive-eyes`），规范见 SKILL.md §5.14/§5.15 |

## 支持的 kind

`screen_post`（灾劫/滤镜）、`domain_sphere`（球域）、`volume_field`（两趟体积场）、`skybox`、`combat_quad`（斩/雨/雷）、`entity_aura`、`item_glow`、`water_body`。

## 版本锚点（重要）

本工坊以 `shengxian/veil-neoforge-1.21.1-4.5.2.jar` 为**唯一事实来源**：

- 资源格式（`fx_schema`）：从 jar 的 codec 常量池 + 官方 1.21 源码逐字段核对；
- Java 签名（`fx_api`）：`javap` 逐个方法核对，`tools/javac_fx.py` 用真 jar 实编译生成的 `Fx.java`（当前 0 error）；
- 校验器（`fx_validate`）的枚举白名单与 jar 一致：`format` 12 种、`bufferSize` 3 种、`renderStage` 11 个、layer 19 个、stage 4 种。

**旧 demo（guzrlevelupdemo V5.4）没有错**：它的 `textures` 用的是裸字符串与 `{type:framebuffer}`，4.5.2 都认。
真正会踩的是网上流传的 1.20 时代写法 `{"type":"asset"}`（4.5.2 只认 location / framebuffer 两种 type），
以及把管理器写成 `getPostPipelineManager()`（真名 `getPostProcessingManager()`）。

## 几页资产库（fx_jiye）

几页 demo（guzrlevelupdemoV5.4）的 253 个 pinwheel 资产已经**整库进 MCP**：

- `action=list` —— 目录，每条带「真 OpenGL 4.6 编译状态」（122 个 program **全部 122/122 通过**）
- `action=show <name>` —— **完整源码**（program json + vsh + fsh + include + 依赖清单 + 怎么用）
- `action=find <关键词>` —— 按中文/英文找最接近的现成效果（家族 → 资产名 → uniform/tag 三级深搜）
- `action=copy <name> [--as 新名]` —— **连依赖闭包一起拷进升仙工程**：
  命名空间 `guzrlevelup → shengxian`、主资产可改名（自引用一并改）、
  依赖的 program / rendertype / post / FBO / include / 贴图按需跟随、默认**不覆盖**已有文件。

**★拷贝时自动做两件清洗**（不洗就编不过）：

1. **UBO 重复**：几页常写「`#include veil:space_helper` + `#veil:buffer veil:camera VeilCamera`」两行都有，
   展开后是两个同名 UBO ⇒ 驱动报 `C1038 conflicts`。拷贝时删掉重复的那行。
2. **命名空间**：所有 `guzrlevelup:` 引用改成目标命名空间。

依赖解析**按 JSON 结构逐键走**，不扫字符串 —— 因为几页里 program 与 framebuffer 常常同名
（`fan_wind_field` 两者都有）：program 的 `vertex/fragment` 指 program，`textures` 里的同名引用指 framebuffer。

## 验证链（本工坊的生成物怎么才算「对」）

| 层 | 工具 | 抓什么 |
|---|---|---|
| 资源结构 | `fx_validate`（本 MCP 内） | JSON 字段、交叉引用、uniform 未用、命名不同族 |
| GLSL 合法性 | `python tools/glsl_check.py`（工程侧） | **真 OpenGL 驱动编译+链接**：语法、varying 对不上、UBO 写法 |
| Java 签名 | `python tools/javac_fx.py`（工程侧） | 用真 Veil jar 编译生成的 `Fx.java` |
| 格式常量 | `python tools/jar_strings.py <jar> --grep` | codec 字段名、注册 id、枚举值 |

`glsl_check.py` 会补齐 Veil 的预处理（内联 `#include veil:*`、把 `#veil:buffer veil:camera VeilCamera`
展开成等价的 std140 UBO），再交给 GLFW 隐藏窗口 + lwjgl 4.6 真驱动编译。
**静态检查抓不到「这行 GLSL 合不合法」** —— 升仙的 `xian_shell.vsh` 就是靠它才发现
「UBO 块名与实例名同名」这类只有驱动才报的问题。

## 边界

- 换 Veil 版本必须重跑 `python tools/jar_strings.py <新jar> ...` 再对一遍；jar 是事实，文档不是。
- 生成物是**骨架 + 高质量默认算法**，不是成品美术；数值要按你的视觉再调。
- `gzsm` 私有模型不生成（用 glTF/OBJ）；`model_pbr` 只给指导。
- 只写工程内目录（`特效工坊/` 或显式 `outDir` 且必须在工程内），不会碰模组源码。
