# 升仙三气汇聚光效

本效果由几页特效工坊 `fx_plan` → `fx_create` 生成并用 `fx_validate` 校验，采用 `entity_aura` / `NEW_ENTITY` / 双趟 bloom 结构。运行资源位于模组 `assets/shengxian/pinwheel/`。

## 视觉与触发

- 天元气用淡金、地气用青绿、人气用赤霞；三条细流从脚边绕身上升，向胸口收束。
- 三道玉色环带代表仙窍轮廓；顶点色承载气的色相，Veil `VeilRenderTime` 驱动流纹、星点和呼吸。
- 服务端仅在纳气阶段发送实体号与强度；客户端挂到对应玩家模型上，收到停止消息或超时后在 12 tick 内淡出。
- 只有局部世界几何和 bloom，不做全屏滤镜，也不替代升仙规则、物品消耗或状态提交。

## 项目接入

- 客户端运行时由升仙模组通过 NeoForge Jar-in-Jar 携带 Veil 4.5.2；专用服务器按客户端依赖声明跳过 Veil。
- 实际触发代码在 `src/main/java/net/guzhenren/shengxian/client/VeilSanqiAuraRenderer.java` 和 `ClientFx.java`。渲染类型仍使用反射桥接，Veil 原始 JAR 作为独立嵌套模组保留。
- 工坊生成的 `java/.../AscensionSanqiConvergeFx.java` 是 API 接入示例；本项目使用更小的实体几何提交器。
- 同名 GLSL、rendertype 和 program JSON 已复制到 `src/main/resources/assets/shengxian/pinwheel/`。

## 调色板

| 通道 | 颜色 | 用途 |
|---|---|---|
| 淡金 `#E3BA69` | 天元气流 | 外层流线 |
| 青绿 `#63C7B5` | 地气流 | 中层流线 |
| 赤霞 `#CF6B7A` | 人气流 | 内层流线 |
| 玉白 `#C2F2D6` | 仙窍环 | 三道收束光环 |

这些颜色是游戏视觉映射，不作为原著定色依据。
