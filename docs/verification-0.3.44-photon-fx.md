# 0.3.42–0.3.44 灾劫特效工坊（Photon 为主，原版粒子兜底）

日期：2026-11-01。

## 结论

灾劫特效改走 **Photon 特效工坊**：装了 Photon 的客户端用它的粒子发射器演出，没装的客户端继续用服务端的原版粒子编排，功能不受影响。

**已验证**（客户端控制台日志，硬证据）：

```
[Photon/]: Photon is initializing on platform: NeoForge
Photon 2.2.9 (photon)
[shengxian/]: Tribulation Photon workshop active
[shengxian/]: Tribulation Photon effect started: BLOOD_RAIN for carrier e2732378-…
[shengxian/]: Tribulation Photon effect started: SOUND_CHIMES for carrier e5794a38-…
```

**未验证**：实际画面观感。本会话的窗口截图两次返回同一帧冻结的 NeoForge 加载画面（sha256 相同），像素级确认没拿到。

## 为什么是 Photon

反编译参考实现《升仙免授权5.4-可碎窍.jar》后确认：它的特效是 **Photon**（`com.lowdragmc.photon`）做的，依赖声明为 `photon (required: [2.2.6.a, 2.3.0))` 加 `ldlib2 [2.2.39,)`。它的效果（剑蛟爆裂、残光等）都是 Java 里配置 `ParticleEmitter`（生命周期/速度/大小/颜色/球形发射/爆发）再挂到 `FX` 上播放。

我们沿用同一路线，但把依赖降级为 **optional**，以保留兜底。

## 实现

| 文件 | 作用 |
| --- | --- |
| `client/TribulationFxWorkshop.java` | 门面，**不含任何 Photon import**；用反射解析桥接，Photon 缺席则全部 no-op |
| `client/PhotonTribulationFx.java` | 真正碰 Photon 的实现，只在探测成功后加载；每类灾劫一套发射器参数 |
| `client/CarrierTagReader.java` | 从**已同步的定制名**识别载体类型 |
| `core/CarrierLabels.java` | 服务端与客户端共用的灾劫名表，避免字符串重复 |
| `client/ShengxianClientTick.java` | 每 tick 扫描附近载体，启动/跟随/回收特效 |

五类灾劫的发射器：柳风（叶幕螺旋，青绿渐变，半径 1.5）、峥虎（地面尘环 + 独角迸光，含 Burst）、音磬（金环扩散，半径 0.7）、荒兽（幽蓝魂火，半径 1.1）、血雨（半径 2.2 下落血幕）。

## 依赖与构建

- `photon-neoforge-1.21.1-2.2.9-all.jar`（7.3MB）与 `ldlib2-neoforge-1.21.1-2.2.45-all.jar`（8.0MB）放入 `build/optional-deps/`，**仅编译期**；
- `build.ps1` 新增 `optional-deps` 扫描并加入编译 classpath；
- `neoforge.mods.toml` 声明 `photon` 为 `optional`（`[2.2.6,2.3.0)`，CLIENT）；
- 产物 jar **不打包**这两个库，运行时缺失也不影响加载。

## 顺带修掉的构建缺陷

`build.ps1` 里 `& $javac ... 2>$null` 在 Windows PowerShell 5.1 下**压不住原生命令的 stderr**：javac 即使加了 `-nowarn` 仍会把弃用提示写到 stderr，被 `$ErrorActionPreference='Stop'` 变成终止性错误，导致整个构建在 `Prepare-Classpath` 之后**静默中断**（日志尾部只有 "Dependencies: … 112 local jars"，连报错都没有）。

修复：局部把 `$ErrorActionPreference` 降为 `Continue`，只用退出码判定成功。同时把 `FXRuntime.emmit(...)`（已弃用）换成 `emit(...)`，消除提示源。

## 已知取舍

1. Photon 在跑时，服务端 `emitAura` 的原版粒子**仍在发**（客户端无法低成本告知服务端），两者叠加——更密，但不是重复浪费；
2. 实体记分板 tag 在本环境实测**能**同步到客户端（原本假设不同步），改用定制名识别后两种方式都能工作，未回退；
3. 特效参数未经美术调优，只是"每类有辨识度"，离参考实现那种成品观感还有距离。