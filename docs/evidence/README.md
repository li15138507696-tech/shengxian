# 证据索引（阶段 1）

本目录记录本轮实际读取/执行的证据。结论分为：

- **【原著已核·译本】**：读到对应章节的完整上下文，但文本是可访问的日文全译本；中文原站被 403，原文措辞仍待核。
- **【待核】**：只有转载标题、摘要或前一轮文档，未读到本轮可复核的正文。
- **【本体已核】**：来自实际 JAR 的字节码、反编译、字段读写枚举或构建结果。
- **【游戏化参数】**：为操作、缩尺、性能或容错提出的实现选择，不冒充原著。

## 文件

- `gu-activation-api.md`：本体蛊的右键、攻击、背包维护、方块与快捷键入口静态审计。其统计是候选调用点，不能当成完整反噬覆盖或成功次数。

- `canon-rules.md`：CANON-001..014 与 GAP-001..008；GAP-001 的方正例证已核，推广范围和代码实现仍未完成。
- `upstream-audit.md`：重建后的 UPSTREAM-001..013 静态代码事实与能力边界；检查对象和哈希列在文首。
- `qi-parameters.md`：三气引擎参数（游戏映射）与证据编号对应。
- `tribulation-parameters.md`：灾劫领域参数（游戏映射）、载体与未实现边界。
- `tribulation-matrix.md`：CANON-007/009/011/012/013 与 GAP-001/002 的逐条矩阵（触发/外形/影响/应对/结束/个例或通则/代码位置/载体表达力）及未解决项 TRIB-*。
- `aperture-state.md`：仙窍账本字段、已实现行为与“尚未创建空间”的边界。
- `xian-gu.md`：仙蛊（CANON-009）已核事实、未核的炼成与世界唯一性、0.3.21 的实现边界。
- `upstream-reads-audit.md`：本体 `kongqiao`/`renqi`/`jieduan`/`shiqiao` 的**读取端**字节码审计（22371 类）；关闭 UPSTREAM-006/007 的读取端缺口，结论是升仙模块不应接入这两个字段。

## 依赖指纹

- 外层：`3733c20ffbd16d9068bb8188d45b24a0d61b279d7cb6fdc8ea341938a7f4cf04`（120,223,219 字节）
- 内层：`b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc`
- 内层条目：`META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar`
- 构建：Minecraft 1.21.1 / NeoForge 21.1.263 / Java 21 / 110 jars

## 可复现命令

~~~powershell
& ./build.ps1 -Action Test
& ./build.ps1 -Action Build
& ./build.ps1 -Action Verify
python build/scan_fields2.py
python build/keyword_scan2.py
~~~

`build/` 下的 Python 脚本与反编译产物是本地审计工具，不是模组交付物，也不进入 JAR。

## 版本与限制

- 原著规则/本体静态审计日期：2026-10-25 至 2026-10-27；上游静态证据页于 2026-10-27 重建。
- 0.3.7 在隔离服务端与真实 NeoForge 客户端验证加载和 FlowBot 登录；新形成门禁被拒且未创建 first 会话。Veil 管线已加载，但 UI 点击、成窍视觉和完整升仙流程仍未验收；用户要求暂停服务端调用后，本轮没有宣称 0.3.7 正常停服。
- 没有修改本体 JAR、真实存档或游戏实例。
- 没有逐章穷尽全书；未覆盖的路线在 canon-rules.md 中列为 GAP，不得当作已核。

- 0.3.9 仪式特效/权威结果：../ascension-ritual-effects.md；离线证据/指纹/实机缺项：../verification-0.3.9.md。

- 当前 0.3.18 失败结算状态、玩家回执与离线验证：../verification-0.3.18.md；CANON-002 失败依据、未知状态隔离及运行验收缺口：failure-settlement.md。中间版 0.3.17 的哈希记录在当前验证文档；0.3.16 原因白名单见 ../verification-0.3.16.md；0.3.15 催蛊账本见 ../verification-0.3.15.md；0.3.14 钩子接线记录见 ../verification-0.3.14.md。0.3.12 修为恢复兼容历史记录见 ../verification-0.3.12.md。0.3.10 云体遮挡证据仍见 ../verification-0.3.10.md。

- 2026-10-28 本体蛊激活调用点审计：见 gu-activation-api.md。
