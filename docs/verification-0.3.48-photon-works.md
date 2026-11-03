# 0.3.44–0.3.48 灾劫特效：Photon 特效工坊打通

日期：2026-11-01。承接 `verification-0.3.44-photon-fx.md`（当时只有日志、没有画面）。

## 结果

**特效已实际可见**。截图 `build/test-client/screenshots/2026-11-01_21.28.13.png`：五类灾劫同时在场，满屏五色粒子（红=血雨、青=荒兽魂火、绿=柳风、金=音磬、土黄=峥虎尘）。

硬指标：`IParticleEmitter.getParticleAmount()` 从 **3–4** 提升到 **240–320**（打满 `maxParticles`）。

## 三个真 bug（逐个由实机数据定位）

### 1. 用错了 executor，Photon 从不 tick 它

之前继承泛型 `FXEffectExecutor` 并自己覆写 `start()`。Photon 的世界内特效是靠
`EntityEffectExecutor` / `BlockEffectExecutor` 的静态 `CACHE` 表逐 tick 驱动的，
泛型基类不在任何注册表里 → 只发射了最初几个粒子就冻结（3–4 个，永远不动）。

**修复**：改用 `EntityEffectExecutor(FX, Level, Entity, AutoRotate.NONE)`，载体跟随也由 Photon 自己做，删掉了手写的 follow 逻辑。

### 2. 发射器没有材质

`renderer.getMaterials()` 为空时，粒子会被模拟但不绘制。
**修复**：显式加入 `MaterialSetting(new TextureMaterial(photon:textures/particle/circle.png))`。

### 3. `RandomConstant` 参数写反了

我按降序传 `RandomConstant(0.95, 0.55)`，而它要 **min,max 升序**（参考实现均为 `RandomConstant(12, 24)`）。降序会把寿命算成负区间。
**修复**：全部改为升序，并把寿命/尺寸/速率调大到可见量级。

另外补齐了 `emission.setEmissionMode(Mode.Random)`（参考实现显式设置，默认 `Exacting` 发射量极低）。

## 方法教训：先拿硬数据，再猜画面

黑屏/无效果排查过程中，最有价值的不是截图，而是 `getParticleAmount()` 日志：
- 3–4 → "模拟了但没被 tick"或"根本没发射"
- 240+ → 发射正常

窗口截图在本会话一度返回**冻结帧**（两次间隔 4 秒全屏截图 SHA256 相同），最后靠**游戏内 F2 截图**（`build/test-client/screenshots/`）绕过，才拿到真实画面。

## 当前观感（诚实评价）

能看见、很密，但还不够"工坊级"：
- 五种灾劫同时叠加时颜色混杂，辨识度靠颜色而非形状；
- 粒子是统一的柔和圆点，缺少参考实现那种拖尾/光束/时序编排；
- 半径和速率偏大，近距离有"撒彩纸"感。

要继续提升，需要在 Photon 的游戏内编辑器里逐个精调（拖尾、生命周期曲线、burst 时序），或改用 `.fx` 资源文件承载成品特效。

## 产物

`shengxian-0.3.48.jar`，SHA256 `dc1ec56a61e249212c1989f22294013cd1de5972767082c7f55de35ca88e601d`，Framework tests 204/204。