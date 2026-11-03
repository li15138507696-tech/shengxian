# 几页特效全表（由 `tools/scan_jiye_fx.py` 自动生成，勿手改）

> 来源：`素材/guzrlevelupdemoV5.4-特效与贴图/特效`｜共 **469** 个文件：core 4 组、program 122、rendertype 94、post 32、framebuffer 4、include 1、无 JSON 的 vsh 2。
>
> `uniforms` 只列名字（类型见 JSON 索引 `_tmp_reverse/jiye_fx_index.json`）；`标签` 为机械识别的技法特征。

## 1. shaders/core —— 原版 ShaderInstance 三件套（4 组）

| 名称 | .json | .vsh | .fsh | uniforms | attributes | samplers | 标签 |
|---|---|---|---|---|---|---|---|
| `aperture_transit` | ✔ | ✔ | ✔ | Time, Progress, Resolution, Accent, Grade, Opacity, Reveal, Style | Position | — | fbm, refraction, bloom, vignette |
| `crystal_ball` | ✔ | ✔ | ✔ | ProjMat, ViewMat, Center, CamPos, Time, Bright, Omen, Crack, Collapse, Fade | Position, Color | — | fresnel |
| `crystal_shard` | ✔ | ✔ | ✔ | ProjMat, ViewMat, Time | Position, Color | — | — |
| `zheng_tiger_earth_qi` | ✔ | ✔ | ✔ | ModelViewMat, ProjMat, U_ViewPort, SamplerCurve, SamplerGradient, SamplerSceneColor, SamplerSceneDepth | Position, Color, UV0, UV2, Normal | SamplerCurve, SamplerGradient, SamplerSceneColor, SamplerSceneDepth | depth-soft, refraction, palette, curve |

## 2. pinwheel/program —— 着色器程序（122 个）

| program | 顶点 | 片元 | 行 | uniforms | 采样器 | 技法标签 |
|---|---|---|---|---|---|---|
| `aperture_manifestation` | aperture_manifestation | aperture_manifestation | 304 | VeilRenderTime, uMode, uProgress, uTurbulence, uIntensity, uSeed, uGrade, uPathColor, uPortalCutout, uPortalNormal, uPortalPlaneCenter, uPortalHalfWidth, uPortalHalfHeight | — | noise3, fresnel, bloom |
| `aperture_portal` | aperture_portal | aperture_portal | 80 | VeilRenderTime, uMode, uProgress, uTurbulence, uIntensity, uSeed, uPathColor | — | fbm |
| `aperture_sky` | aperture_sky | aperture_sky | 205 | VeilRenderTime, uWaterStyle, uGrade | — | fbm, noise3, tonemap, star, swirl |
| `aperture_world_core` | aperture_manifestation | aperture_manifestation |  | — | — | — |
| `aperture_world_edge` | aperture_manifestation | aperture_manifestation |  | — | — | — |
| `aperture_world_shell` | aperture_manifestation | aperture_manifestation |  | — | — | — |
| `baohuangtian_mirror` | veil:blit_screen | baohuangtian_mirror | 28 | DiffuseSampler0, VeilRenderTime, MirrorIntensity | — | vignette |
| `baohuangtian_sky` | baohuangtian_sky | baohuangtian_sky | 66 | VeilRenderTime | — | fbm, noise3, tonemap |
| `baohuangtian_treasure` | baohuangtian_treasure | baohuangtian_treasure | 53 | VeilRenderTime, uMode | — | bloom |
| `black_heaven_post` | veil:blit_screen | heaven_post |  | — | — | — |
| `black_heaven_sky` | black_heaven_sky | black_heaven_sky | 2 | — | — | — |
| `blue_heaven_post` | veil:blit_screen | heaven_post |  | — | — | — |
| `blue_heaven_sky` | blue_heaven_sky | blue_heaven_sky | 2 | — | — | — |
| `chi_tong_fire` | chi_tong_fire | chi_tong_fire | 48 | FireTime | — | fbm |
| `corrosive_dark_current_impact` | corrosive_dark_current_impact | corrosive_dark_current_impact | 9 | — | — | — |
| `corrosive_dark_current_portal` | corrosive_dark_current_portal | corrosive_dark_current_portal | 23 | VeilRenderTime | — | swirl |
| `corrosive_dark_current_post` | veil:blit_screen | corrosive_dark_current_post | 40 | DiffuseSampler0, DiffuseDepthSampler, CorrosiveDarkCurrentStrength, CorrosiveDarkCurrentPhase, CorrosiveDarkCurrentRecovery, CorrosiveDarkCurrentStreamProgress, CorrosiveDarkCurrentErosion, CorrosiveDarkCurrentFlash, CorrosiveDarkCurrentDirectionX, CorrosiveDarkCurrentDirectionZ, SkullVisibility, MouthCharge, StreamFront, StreamDirection, StreamTurbulence, ArenaPressure, Recovery | — | depth-soft |
| `corrosive_dark_current_residue` | corrosive_dark_current_residue | corrosive_dark_current_residue | 4 | — | — | — |
| `corrosive_dark_current_skull` | corrosive_dark_current_skull | corrosive_dark_current_skull | 15 | — | — | — |
| `corrosive_dark_current_skull_bone` | corrosive_dark_current_skull_bone | corrosive_dark_current_skull_bone | 18 | — | — | — |
| `corrosive_dark_current_skull_fog` | corrosive_dark_current_skull_fog | corrosive_dark_current_skull_fog | 11 | — | — | — |
| `corrosive_dark_current_skull_void` | corrosive_dark_current_skull_void | corrosive_dark_current_skull_void | 11 | — | — | — |
| `corrosive_dark_current_stream` | corrosive_dark_current_stream | corrosive_dark_current_stream | 11 | VeilRenderTime | — | — |
| `corrosive_dark_current_stream_core` | corrosive_dark_current_stream_core | corrosive_dark_current_stream_core | 6 | VeilRenderTime | — | — |
| `corrosive_dark_current_stream_edge` | corrosive_dark_current_stream_edge | corrosive_dark_current_stream_edge | 5 | VeilRenderTime | — | — |
| `corrosive_dark_current_stream_tip` | corrosive_dark_current_stream_tip | corrosive_dark_current_stream_tip | 11 | — | — | — |
| `cyan_heaven_post` | veil:blit_screen | heaven_post |  | — | — | — |
| `cyan_heaven_sky` | cyan_heaven_sky | cyan_heaven_sky | 2 | — | — | — |
| `dan_dao_slash` | combat_soft_shape | dan_dao_slash | 21 | VeilRenderTime | — | — |
| `datong_wind_boundary` | datong_wind_boundary | datong_wind_boundary | 10 | — | — | — |
| `datong_wind_core` | datong_wind_boundary | datong_wind_core | 9 | — | — | — |
| `datong_wind_erosion` | datong_wind_boundary | datong_wind_erosion | 9 | — | — | — |
| `datong_wind_post` | veil:blit_screen | datong_wind_post | 22 | DiffuseSampler0, VeilRenderTime, DatongWindStrength, DatongWindCollapse, DatongWindDistortion, DatongWindVignette, DatongWindFlash | — | vignette |
| `delight_in_mountain_and_water` | delight_in_mountain_and_water | delight_in_mountain_and_water | 50 | VeilRenderTime | — | — |
| `di_xian_impact` | liu_feng_surface | di_xian_impact | 9 | — | — | — |
| `di_xian_post` | veil:blit_screen | di_xian_post | 33 | DiffuseSampler0, DiffuseDepthSampler, VeilRenderTime, DiXianStrength, DiXianPhase, DiXianProgress, DiXianFogDensity, DiXianDisturbance, DiXianFlash, DiXianRecovery, DiXianVineProgress, DiXianWrappedVines, DiXianBindingStrength, DiXianExtraDamageProgress | — | depth-soft |
| `di_xian_pressure` | liu_feng_surface | di_xian_pressure | 9 | — | — | — |
| `di_xian_surface` | liu_feng_surface | di_xian_surface | 9 | — | — | — |
| `di_xian_vine_bloom` | liu_feng_surface | di_xian_vine_bloom | 9 | — | — | — |
| `di_xian_vine_surface` | liu_feng_surface | di_xian_surface |  | — | — | — |
| `fan_wind_composite` | veil:blit_screen | fan_wind_composite | 94 | DiffuseSampler0, DiffuseDepthSampler, FanWindDataSampler, FanWindMaterialSampler, FanWindDepthSampler, VeilRenderTime | FanWindDataSampler←guzrlevelup:fan_wind_field, FanWindMaterialSampler←guzrlevelup:fan_wind_field, FanWindDepthSampler←guzrlevelup:fan_wind_field:depth | depth-soft, refraction, crosshair-safe |
| `fan_wind_field` | fan_wind_field | fan_wind_field | 51 | VeilRenderTime | — | — |
| `formless_hand_aura` | formless_hand_aura | formless_hand_aura | 110 | VeilRenderTime | — | — |
| `formless_hand_volume` | formless_hand_volume | formless_hand_volume | 67 | VeilRenderTime | — | fresnel |
| `four_flame_cloud` | veil:blit_screen | four_flame_cloud | 189 | DiffuseSampler0, DiffuseDepthSampler, VeilRenderTime, CloudCenterRelative, CloudRadius, CloudSeed, CloudIntensity, CloudLayer, CloudPhase, CloudProgress, CloudPressure, CloudImpactPulse, CloudBurnPulse | — | fbm, depth-soft, refraction, tonemap |
| `fu_ren_xin_mist` | combat_soft_shape | fu_ren_xin_mist | 26 | VeilRenderTime | — | — |
| `green_heaven_post` | veil:blit_screen | heaven_post |  | — | — | — |
| `green_heaven_sky` | green_heaven_sky | green_heaven_sky | 2 | — | — | — |
| `hua_qi_composite` | veil:blit_screen | hua_qi_composite | 113 | DiffuseSampler0, DiffuseDepthSampler, HuaQiDataSampler, HuaQiMaterialSampler, HuaQiDepthSampler, VeilRenderTime | HuaQiDataSampler←guzrlevelup:hua_qi_field, HuaQiMaterialSampler←guzrlevelup:hua_qi_field, HuaQiDepthSampler←guzrlevelup:hua_qi_field:depth | depth-soft, refraction |
| `hua_qi_field` | hua_qi_field | hua_qi_field | 49 | VeilRenderTime | — | — |
| `immortal_gu_item_aura` | immortal_gu_item_aura | immortal_gu_item_aura | 128 | Sampler0, VeilRenderTime | Sampler0←minecraft:textures/atlas/blocks.png | fresnel, palette |
| `immortal_refinement` | veil:blit_screen | immortal_refinement | 77 | DiffuseSampler0, VeilRenderTime, RefinementIntensity, RefinementHue, RefinementStability, RefinementPattern | — | refraction, vignette, swirl |
| `ink_mote` | ink_mote | ink_mote | 16 | — | — | additive |
| `ink_ribbon` | ink_ribbon | ink_ribbon | 7 | — | — | — |
| `ink_ribbon_bloom` | ink_ribbon | ink_ribbon_bloom | 8 | — | — | bloom |
| `iron_wall_bell` | iron_wall_bell | iron_wall_bell | 62 | VeilRenderTime | — | fresnel |
| `iron_wall_composite` | veil:blit_screen | iron_wall_composite | 44 | DiffuseSampler0, DiffuseDepthSampler, IronWallDataSampler, IronWallMaterialSampler, IronWallDepthSampler, VeilRenderTime | IronWallDataSampler←guzrlevelup:iron_wall_field, IronWallMaterialSampler←guzrlevelup:iron_wall_field, IronWallDepthSampler←guzrlevelup:iron_wall_field:depth | depth-soft, refraction |
| `iron_wall_field` | iron_wall_field | iron_wall_field | 21 | — | — | — |
| `lang_jian_slash` | veil:blit_screen | lang_jian_slash | 30 | DiffuseSampler0, LangJianDirectionX, LangJianDirectionY, LangJianIntensity, LangJianProgress | — | — |
| `lang_jian_wave` | combat_soft_shape | lang_jian_wave | 26 | VeilRenderTime | — | — |
| `lei_yi_wing` | lei_yi_wing | lei_yi_wing | 60 | VeilRenderTime | — | — |
| `liu_feng_impact` | liu_feng_surface | liu_feng_impact | 9 | — | — | — |
| `liu_feng_post` | veil:blit_screen | liu_feng_post | 41 | DiffuseSampler0, DiffuseDepthSampler, VeilRenderTime, LiuFengStrength, LiuFengPhase, LiuFengProgress, LiuFengFogDensity, LiuFengDisturbance, LiuFengFlash, LiuFengDirectionX, LiuFengDirectionZ, LiuFengTurbulence, LiuFengRecovery | — | depth-soft, vignette |
| `liu_feng_surface` | liu_feng_surface | liu_feng_surface | 10 | — | — | — |
| `liu_feng_vein` | liu_feng_surface | liu_feng_vein | 8 | — | — | — |
| `man_as_before_apparition` | man_as_before_apparition | man_as_before_apparition | 49 | VeilRenderTime | — | — |
| `man_as_before_rewind` | veil:blit_screen | man_as_before_rewind | 35 | DiffuseSampler0, VeilRenderTime, ManAsBeforeIntensity, ManAsBeforeProgress, ManAsBeforeFatal | — | — |
| `menstrual_blood_curtain` | menstrual_blood_curtain | menstrual_blood_curtain | 51 | VeilRenderTime | — | — |
| `menstrual_blood_loss` | veil:blit_screen | menstrual_blood_loss | 25 | DiffuseSampler0, VeilRenderTime, BloodLossIntensity, BloodLossProgress | — | — |
| `mud_summoning_crab_fallback` | mud_summoning_crab_material | mud_summoning_crab_fallback | 19 | BaseColorSampler, DisplayLight, HurtAmount | BaseColorSampler←guzrlevelup:textures/entity/mud_summoning_crab_base_color.png | — |
| `mud_summoning_crab_material` | mud_summoning_crab_material | mud_summoning_crab_material | 31 | BaseColorSampler, AoSampler, EmissiveSampler, DisplayLight, HurtAmount | BaseColorSampler←guzrlevelup:textures/entity/mud_summoning_crab_base_color.png, AoSampler←guzrlevelup:textures/entity/mud_summoning_crab_ao.png, EmissiveSampler←guzrlevelup:textures/entity/mud_summoning_crab_emissive_mask.png | — |
| `nine_heavens_transition` | veil:blit_screen | nine_heavens_transition | 14 | DiffuseSampler0, TransitionColor, TransitionStrength, TransitionProgress | — | — |
| `orange_heaven_post` | veil:blit_screen | heaven_post |  | — | — | — |
| `orange_heaven_sky` | orange_heaven_sky | orange_heaven_sky | 2 | — | — | — |
| `piercing_rain` | piercing_rain | piercing_rain | 33 | VeilRenderTime | — | refraction |
| `purple_heaven_post` | veil:blit_screen | heaven_post |  | — | — | — |
| `purple_heaven_sky` | purple_heaven_sky | purple_heaven_sky | 2 | — | — | — |
| `qi_wash` | qi_wash | qi_wash | 85 | VeilRenderTime, uQiType, uQiIntensity, uQiSeed, uFlowSign, uContact, uPortalAngle | — | fbm, noise3 |
| `red_heaven_post` | veil:blit_screen | heaven_post |  | — | — | — |
| `red_heaven_sky` | red_heaven_sky | red_heaven_sky | 2 | — | — | — |
| `sky_vortex` | sky_vortex | sky_vortex | 172 | VeilRenderTime, uIntensity, uInnerSpeed, uOuterSpeed, uInkColor, uSilverColor, uAccentColor, uEyeStrength | — | fbm, noise3, refraction, swirl |
| `sky_vortex_bloom` | sky_vortex | sky_vortex_bloom | 109 | VeilRenderTime, uIntensity, uInnerSpeed, uOuterSpeed, uInkColor, uSilverColor, uAccentColor, uEyeStrength | — | noise3, bloom, swirl |
| `spring_autumn_cicada_composite` | veil:blit_screen | spring_autumn_cicada_composite | 38 | DiffuseSampler0, VeilRenderTime, RewindProgress, RewindIntensity, FlowSpeed, LandingPulse, MemoryOpacity, EffectSeed | — | chromatic, vignette |
| `spring_autumn_cicada_trail` | veil:blit_screen | spring_autumn_cicada_trail | 69 | DiffuseSampler0, DiffuseDepthSampler, VeilRenderTime, RewindProgress, RewindIntensity, FlowSpeed, LandingPulse, MemoryOpacity, EffectSeed | — | depth-soft |
| `spring_autumn_time_river` | spring_autumn_time_river | spring_autumn_time_river | 66 | VeilRenderTime | — | — |
| `star_flow` | star_flow | star_flow | 72 | VeilRenderTime | — | star |
| `star_flow_post` | veil:blit_screen | star_flow_post | 56 | DiffuseSampler0, DiffuseDepthSampler, VeilRenderTime, StarFlowStrength, StarFlowWarning, StarFlowPressure, StarFlowFlash, StarFlowRise, StarFlowWave | — | depth-soft, vignette |
| `sword_jiaolong_fallback` | sword_jiaolong_material | sword_jiaolong_fallback | 30 | BaseColorSampler, DisplayLight, GlobalAlpha, PulsePhase, EdgeIntensity, ShatterHeat | BaseColorSampler←guzrlevelup:textures/effect/sword_jiaolong_base_color.png | fresnel |
| `sword_jiaolong_material` | sword_jiaolong_material | sword_jiaolong_material | 49 | BaseColorSampler, NormalSampler, MetallicRoughnessSampler, DisplayLight, GlobalAlpha, PulsePhase, EdgeIntensity, ShatterHeat | BaseColorSampler←guzrlevelup:textures/effect/sword_jiaolong_base_color.png, NormalSampler←guzrlevelup:textures/effect/sword_jiaolong_normal.png, MetallicRoughnessSampler←guzrlevelup:textures/effect/sword_jiaolong_metallic_roughness.png | fresnel |
| `taiqing_empty_space` | taiqing_empty_space | taiqing_empty_space | 27 | VeilRenderTime | — | — |
| `taiqing_empty_space_post` | veil:blit_screen | taiqing_empty_space_post | 53 | DiffuseSampler0, DiffuseDepthSampler, TaiqingStrength, TaiqingJade, TaiqingWarning, TaiqingDive, TaiqingFlash, TaiqingRefraction, TaiqingFrontCount, TaiqingFront, TaiqingDirection | — | depth-soft, additive |
| `tianyuan_baohuang_lian` | veil:blit_screen | tianyuan_baohuang_lian | 50 | DiffuseSampler0, DiffuseDepthSampler, VeilRenderTime, BaohuangStrength, BaohuangPulse, BaohuangTime | — | depth-soft, bloom |
| `tou_sheng_domain` | veil:blit_screen | tou_sheng_domain | 102 | DiffuseSampler0, DiffuseDepthSampler, VeilRenderTime, TouShengCenter, TouShengRadius, TouShengPhase, TouShengActive | — | fbm, depth-soft |
| `tribulation_lightning` | tribulation_lightning | tribulation_lightning | 56 | VeilRenderTime | — | fbm |
| `wander_world_aura` | wander_world_volume | wander_world_aura | 54 | VeilRenderTime | — | noise3, fresnel |
| `wander_world_mirror_art` | wander_world_mirror_art | wander_world_mirror_art | 137 | VeilRenderTime | — | fbm, noise3, fresnel, swirl |
| `wander_world_trail_art` | wander_world_trail_art | wander_world_trail_art | 109 | VeilRenderTime | — | fbm, noise3, fresnel |
| `wander_world_volume` | wander_world_volume | wander_world_volume | 76 | VeilRenderTime | — | fbm, noise3, fresnel |
| `wander_world_volume_glint` | wander_world_volume | wander_world_volume_glint | 60 | VeilRenderTime | — | noise3, fresnel |
| `wander_world_water` | wander_world_water | wander_world_water | 64 | VeilRenderTime | — | — |
| `wander_world_wave_composite` | veil:blit_screen | wander_world_wave_composite | 101 | DiffuseSampler0, DiffuseDepthSampler, WaveDataSampler, WaveMaterialSampler, WaveDepthSampler, VeilRenderTime | WaveDataSampler←guzrlevelup:wander_world_wave_field, WaveMaterialSampler←guzrlevelup:wander_world_wave_field, WaveDepthSampler←guzrlevelup:wander_world_wave_field:depth | fresnel, depth-soft, refraction, additive |
| `wander_world_wave_fallback` | wander_world_wave_field | wander_world_wave_fallback | 79 | VeilRenderTime | — | fresnel, palette, additive |
| `wander_world_wave_field` | wander_world_wave_field | wander_world_wave_field | 107 | VeilRenderTime | — | palette, additive |
| `water_clothing_aura` | water_clothing_aura | water_clothing_aura | 22 | VeilRenderTime | — | fresnel |
| `water_clothing_ornament` | water_clothing_shell | water_clothing_ornament | 18 | VeilRenderTime | — | — |
| `water_clothing_shell` | water_clothing_shell | water_clothing_shell | 31 | VeilRenderTime | — | fresnel |
| `water_clothing_silk` | water_clothing_shell | water_clothing_silk | 23 | VeilRenderTime | — | — |
| `water_clothing_transform` | veil:blit_screen | water_clothing_transform | 20 | DiffuseSampler0, VeilRenderTime, WaterClothingIntensity | — | vignette |
| `water_enclosure` | water_enclosure | water_enclosure | 80 | VeilRenderTime | — | fbm, noise3, fresnel |
| `white_heaven_post` | veil:blit_screen | heaven_post |  | — | — | — |
| `white_heaven_sky` | white_heaven_sky | white_heaven_sky | 2 | — | — | — |
| `wind_flower` | veil:blit_screen | wind_flower | 139 | DiffuseSampler0, DiffuseDepthSampler, VeilRenderTime, FlowerStrength, FlowerGust, FlowerWarning, FlowerFlash, FlowerSeed, FlowerPhase, FlowerDirectionX, FlowerDirectionZ, FlowerTurbulence, FlowerRecovery, FlowerTintStrength, FlowerFogDensity, FlowerDisturbance, FlowerDesaturation, FlowerLineSuppression | — | depth-soft, additive |
| `wind_flower_surface` | wind_flower_surface | wind_flower_surface | 28 | VeilRenderTime | — | — |
| `wind_flower_wind` | wind_flower_wind | wind_flower_wind | 26 | VeilRenderTime | — | — |
| `wind_thunder_cage` | veil:blit_screen | wind_thunder_cage | 72 | DiffuseSampler0, DiffuseDepthSampler, VeilRenderTime, CageStrength, CageWarning, CageFlash, CageAftershock, CagePressure, CageRound, CageSeed | — | depth-soft, vignette, swirl |
| `wind_thunder_storm` | wind_thunder_storm | wind_thunder_storm | 54 | VeilRenderTime | — | — |
| `xing_mou_eye` | xing_mou_eye | xing_mou_eye | 28 | VeilRenderTime | — | — |
| `yellow_heaven_post` | veil:blit_screen | heaven_post |  | — | — | — |
| `yellow_heaven_sky` | yellow_heaven_sky | yellow_heaven_sky | 2 | — | — | — |
| `zheng_tiger_atmosphere` | zheng_tiger_atmosphere | zheng_tiger_atmosphere | 119 | VeilRenderTime | — | noise3 |
| `zheng_tiger_stone` | zheng_tiger_stone | zheng_tiger_stone | 59 | VeilRenderTime | — | — |
| `zheng_tiger_vein` | zheng_tiger_vein | zheng_tiger_vein | 31 | VeilRenderTime | — | — |

### 2.1 无 JSON 的 vsh（2 个；`combat_soft_shape` 被 3 个 program 引用，`heaven_post` 无人引用）

| .vsh | 行 | 输入 | uniforms | 标签 |
|---|---|---|---|---|
| `combat_soft_shape.vsh` | 16 | — | — | — |
| `heaven_post.vsh` | 3 | — | — | — |

## 3. pinwheel/rendertypes —— 数据驱动渲染类型（94 个）

| rendertype | format | mode | buffer | sort | 趟数 | 层链 |
|---|---|---|---|---|---|---|
| `aperture_manifestation` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=aperture_manifestation + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `aperture_portal` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=aperture_portal + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `aperture_sky` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=aperture_sky + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `aperture_world_core` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=aperture_world_core + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:1) |
| `aperture_world_edge` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✘ | 2 | v:shader=aperture_world_edge + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=aperture_world_edge + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `aperture_world_shell` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=aperture_world_shell + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `ascension_mist` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✔ | 1 | v:shader=ink_mote + mc:depth_test=always + mc:write_mask(d:0) + mc:transparency=translucent |
| `baohuangtian_sky` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=baohuangtian_sky + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `baohuangtian_treasure` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✔ | 2 | v:shader=baohuangtian_treasure + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=baohuangtian_treasure + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `black_heaven_sky` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=black_heaven_sky + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `blue_heaven_sky` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=blue_heaven_sky + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `chi_tong_fire` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=chi_tong_fire + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `corrosive_dark_current_impact` | POSITION_TEX_COLOR | TRIANGLES | SMALL | ✔ | 1 | v:shader=corrosive_dark_current_impact + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `corrosive_dark_current_portal` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=corrosive_dark_current_portal + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `corrosive_dark_current_residue` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=corrosive_dark_current_residue + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `corrosive_dark_current_skull` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=corrosive_dark_current_skull + mc:depth_test=never + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `corrosive_dark_current_skull_bone` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=corrosive_dark_current_skull_bone + mc:depth_test=never + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `corrosive_dark_current_skull_fog` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=corrosive_dark_current_skull_fog + mc:depth_test=never + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `corrosive_dark_current_skull_void` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=corrosive_dark_current_skull_void + mc:depth_test=never + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `corrosive_dark_current_stream` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=corrosive_dark_current_stream + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `corrosive_dark_current_stream_core` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=corrosive_dark_current_stream_core + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `corrosive_dark_current_stream_edge` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=corrosive_dark_current_stream_edge + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `corrosive_dark_current_stream_tip` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=corrosive_dark_current_stream_tip + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `cyan_heaven_sky` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=cyan_heaven_sky + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `dan_dao_slash` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=dan_dao_slash + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive |
| `datong_wind_boundary` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=datong_wind_boundary + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `datong_wind_core` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=datong_wind_core + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `datong_wind_erosion` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=datong_wind_erosion + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `delight_in_mountain_and_water` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 2 | v:shader=delight_in_mountain_and_water + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=delight_in_mountain_and_water + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `di_xian_impact` | POSITION_TEX_COLOR | TRIANGLES | SMALL | ✔ | 1 | v:shader=di_xian_impact + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `di_xian_pressure` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=di_xian_pressure + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `di_xian_surface` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=di_xian_surface + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `di_xian_vine_bloom` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=di_xian_vine_bloom + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `di_xian_vine_surface` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=di_xian_vine_surface + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `fan_wind_field` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✘ | 1 | v:shader=fan_wind_field + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:1) + mc:transparency=none + mc:output->fan_wind_field |
| `formless_hand_aura` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 2 | v:shader=formless_hand_aura + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=formless_hand_aura + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `formless_hand_volume` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=formless_hand_volume + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `fu_ren_xin_mist` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=fu_ren_xin_mist + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive |
| `green_heaven_sky` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=green_heaven_sky + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `hua_qi_field` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✘ | 1 | v:shader=hua_qi_field + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:1) + mc:transparency=none + mc:output->hua_qi_field |
| `immortal_gu_item_aura` | NEW_ENTITY | QUADS | TRANSIENT | ✘ | 1 | v:shader=immortal_gu_item_aura + mc:texture + mc:lightmap + mc:overlay + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive |
| `immortal_gu_item_aura_inner` | NEW_ENTITY | QUADS | TRANSIENT | ✔ | 1 | v:shader=immortal_gu_item_aura + mc:texture + mc:lightmap + mc:overlay + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `ink_cocoon` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✔ | 1 | v:shader=ink_mote + mc:depth_test=always + mc:write_mask(d:0) + mc:transparency=additive |
| `ink_fusion` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✔ | 1 | v:shader=ink_mote + mc:depth_test=always + mc:write_mask(d:0) + mc:transparency=additive |
| `ink_mote` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✔ | 1 | v:shader=ink_mote + mc:depth_test=always + mc:write_mask(d:0) + mc:transparency=additive |
| `ink_ribbon` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✔ | 1 | v:shader=ink_ribbon + mc:depth_test=always + mc:write_mask(d:0) + mc:transparency=translucent |
| `iron_wall_bell` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 2 | v:shader=iron_wall_bell + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=iron_wall_bell + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `iron_wall_field` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✘ | 1 | v:shader=iron_wall_field + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:1) + mc:transparency=none + mc:output->iron_wall_field |
| `jellyfish_habitat_mist` | POSITION_TEX_COLOR_NORMAL | TRIANGLES | BIG | ✔ | 2 | v:shader=water_enclosure + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=water_enclosure + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `lang_jian_wave` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=lang_jian_wave + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `lei_yi_wing` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=lei_yi_wing + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `liu_feng_impact` | POSITION_TEX_COLOR | QUADS | SMALL | ✔ | 1 | v:shader=liu_feng_impact + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `liu_feng_surface` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=liu_feng_surface + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `liu_feng_vein` | POSITION_TEX_COLOR | QUADS | BIG | ✔ | 1 | v:shader=liu_feng_vein + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `man_as_before_apparition` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 2 | v:shader=man_as_before_apparition + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=man_as_before_apparition + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `menstrual_blood_curtain` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✘ | 2 | v:shader=menstrual_blood_curtain + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=menstrual_blood_curtain + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `orange_heaven_sky` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=orange_heaven_sky + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `piercing_rain` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✘ | 2 | v:shader=piercing_rain + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=piercing_rain + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `purple_heaven_sky` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=purple_heaven_sky + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `qi_wash` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✔ | 1 | v:shader=qi_wash + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `red_heaven_sky` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=red_heaven_sky + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `sky_vortex` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✘ | 2 | v:shader=sky_vortex + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=sky_vortex_bloom + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `sky_vortex_no_bloom` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=sky_vortex + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `spring_autumn_time_river` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 2 | v:shader=spring_autumn_time_river + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=spring_autumn_time_river + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `star_flow` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=star_flow + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `taiqing_empty_space` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=taiqing_empty_space + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:1) + mc:transparency=translucent |
| `taiqing_empty_space_field` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✘ | 1 | v:shader=taiqing_empty_space + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `tribulation_lightning` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✘ | 2 | v:shader=tribulation_lightning + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive ‖ v:shader=tribulation_lightning + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `wander_world_aura` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 2 | v:shader=wander_world_aura + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=wander_world_aura + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `wander_world_mirror_art` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✔ | 2 | v:shader=wander_world_mirror_art + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=wander_world_mirror_art + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `wander_world_trail_art` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✔ | 2 | v:shader=wander_world_trail_art + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=wander_world_trail_art + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `wander_world_volume` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=wander_world_volume + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `wander_world_volume_glint` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 2 | v:shader=wander_world_volume_glint + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent ‖ v:shader=wander_world_volume_glint + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `wander_world_water` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✔ | 1 | v:shader=wander_world_water + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `wander_world_wave_fallback` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=wander_world_wave_fallback + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `wander_world_wave_field` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✘ | 1 | v:shader=wander_world_wave_field + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:1) + mc:transparency=none + mc:output->wander_world_wave_field |
| `water_clothing_aura` | NEW_ENTITY | QUADS | TRANSIENT | ✔ | 1 | v:shader=water_clothing_aura + mc:lightmap + mc:overlay + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `water_clothing_aura_bloom` | NEW_ENTITY | QUADS | TRANSIENT | ✔ | 1 | v:shader=water_clothing_aura + mc:lightmap + mc:overlay + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `water_clothing_ornament` | NEW_ENTITY | QUADS | TRANSIENT | ✔ | 1 | v:shader=water_clothing_ornament + mc:lightmap + mc:overlay + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `water_clothing_ornament_bloom` | NEW_ENTITY | QUADS | TRANSIENT | ✔ | 1 | v:shader=water_clothing_ornament + mc:lightmap + mc:overlay + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `water_clothing_shell` | NEW_ENTITY | QUADS | TRANSIENT | ✔ | 1 | v:shader=water_clothing_shell + mc:lightmap + mc:overlay + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `water_clothing_silk` | NEW_ENTITY | QUADS | TRANSIENT | ✘ | 1 | v:shader=water_clothing_silk + mc:lightmap + mc:overlay + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:1) + mc:transparency=translucent |
| `water_enclosure` | POSITION_TEX_COLOR_NORMAL | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=water_enclosure + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `water_enclosure_close` | POSITION_TEX_COLOR_NORMAL | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=water_enclosure + mc:depth_test=always + mc:cull=front + mc:write_mask(d:0) + mc:transparency=translucent |
| `white_heaven_sky` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=white_heaven_sky + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `wind_flower_glow` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=wind_flower_wind + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |
| `wind_flower_surface` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=wind_flower_surface + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `wind_flower_wind` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=wind_flower_wind + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `wind_thunder_storm` | POSITION_TEX_COLOR | TRIANGLES | BIG | ✔ | 1 | v:shader=wind_thunder_storm + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `xing_mou_eye` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✔ | 1 | v:shader=xing_mou_eye + mc:depth_test=always + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive |
| `yellow_heaven_sky` | POSITION_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=yellow_heaven_sky + mc:depth_test=lequal + mc:cull=back + mc:write_mask(d:0) + mc:transparency=translucent |
| `zheng_tiger_atmosphere` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✔ | 1 | v:shader=zheng_tiger_atmosphere + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=translucent |
| `zheng_tiger_stone` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✘ | 1 | v:shader=zheng_tiger_stone + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:1) |
| `zheng_tiger_vein` | POSITION_TEX_COLOR | TRIANGLES | TRANSIENT | ✘ | 2 | v:shader=zheng_tiger_vein + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive ‖ v:shader=zheng_tiger_vein + mc:depth_test=lequal + mc:cull=none + mc:write_mask(d:0) + mc:transparency=additive + mc:output->bloom |

## 4. pinwheel/post —— 后处理管线（32 个）

| post | renderStage | priority | stages |
|---|---|---|---|
| `baohuangtian_mirror` | after_level | — | veil:blit→baohuangtian_mirror(minecraft:main→veil:post) |
| `black_heaven_post` | after_level | — | veil:blit→black_heaven_post(minecraft:main→veil:post) |
| `blue_heaven_post` | after_level | — | veil:blit→blue_heaven_post(minecraft:main→veil:post) |
| `corrosive_dark_current` | after_level | — | veil:blit→corrosive_dark_current_post(minecraft:main→veil:post) |
| `cyan_heaven_post` | after_level | — | veil:blit→cyan_heaven_post(minecraft:main→veil:post) |
| `datong_wind` | after_level | — | veil:blit→datong_wind_post(minecraft:main→veil:post) |
| `di_xian` | after_level | — | veil:blit→di_xian_post(minecraft:main→veil:post) |
| `fan_wind` | after_translucent_blocks | 1210 | veil:blit→fan_wind_composite(minecraft:main→veil:post) |
| `four_flame_cloud` | after_level | — | veil:blit→four_flame_cloud(minecraft:main→veil:post) |
| `green_heaven_post` | after_level | — | veil:blit→green_heaven_post(minecraft:main→veil:post) |
| `hua_qi` | after_translucent_blocks | 1220 | veil:blit→hua_qi_composite(minecraft:main→veil:post) |
| `immortal_refinement` | after_level | — | veil:blit→immortal_refinement(minecraft:main→veil:post) |
| `iron_wall` | after_translucent_blocks | 1215 | veil:blit→iron_wall_composite(minecraft:main→veil:post) |
| `lang_jian_slash` | after_level | 1230 | veil:blit→lang_jian_slash(minecraft:main→veil:post) |
| `liu_feng` | after_level | — | veil:blit→liu_feng_post(minecraft:main→veil:post) |
| `man_as_before_rewind` | after_level | — | veil:blit→man_as_before_rewind(minecraft:main→veil:post) |
| `menstrual_blood_loss` | after_level | — | veil:blit→menstrual_blood_loss(minecraft:main→veil:post) |
| `nine_heavens_transition` | after_level | — | veil:blit→nine_heavens_transition(minecraft:main→veil:post) |
| `orange_heaven_post` | after_level | — | veil:blit→orange_heaven_post(minecraft:main→veil:post) |
| `purple_heaven_post` | after_level | — | veil:blit→purple_heaven_post(minecraft:main→veil:post) |
| `red_heaven_post` | after_level | — | veil:blit→red_heaven_post(minecraft:main→veil:post) |
| `spring_autumn_cicada_rewind` | after_level | 1700 | veil:copy→(minecraft:main→spring_autumn_scene); veil:blit→spring_autumn_cicada_trail(spring_autumn_scene→spring_autumn_flow); veil:blit→spring_autumn_cicada_composite(spring_autumn_flow→veil:post) |
| `star_flow` | after_level | — | veil:blit→star_flow_post(minecraft:main→veil:post) |
| `taiqing_empty_space` | after_level | — | veil:blit→taiqing_empty_space_post(minecraft:main→veil:post) |
| `tianyuan_baohuang_lian` | after_level | — | veil:blit→tianyuan_baohuang_lian(minecraft:main→veil:post) |
| `tou_sheng_domain` | after_translucent_blocks | 1225 | veil:blit→tou_sheng_domain(minecraft:main→veil:post) |
| `wander_world_wave` | after_translucent_blocks | — | veil:blit→wander_world_wave_composite(minecraft:main→veil:post) |
| `water_clothing_transform` | after_level | — | veil:blit→water_clothing_transform(minecraft:main→veil:post) |
| `white_heaven_post` | after_level | — | veil:blit→white_heaven_post(minecraft:main→veil:post) |
| `wind_flower` | after_level | — | veil:blit→wind_flower(minecraft:main→veil:post) |
| `wind_thunder_cage` | after_level | — | veil:blit→wind_thunder_cage(minecraft:main→veil:post) |
| `yellow_heaven_post` | after_level | — | veil:blit→yellow_heaven_post(minecraft:main→veil:post) |

## 5. pinwheel/framebuffers —— 离屏目标（4 个）

| framebuffer | size | autoClear | color buffers | depth |
|---|---|---|---|---|
| `fan_wind_field` | q.screen_width×q.screen_height | True | FanWindDataSampler:RGBA16F, FanWindMaterialSampler:RGBA8 | FanWindDepthSampler:DEPTH_COMPONENT |
| `hua_qi_field` | q.screen_width×q.screen_height | True | HuaQiDataSampler:RGBA16F, HuaQiMaterialSampler:RGBA8 | HuaQiDepthSampler:DEPTH_COMPONENT |
| `iron_wall_field` | q.screen_width×q.screen_height | True | IronWallDataSampler:RGBA16F, IronWallMaterialSampler:RGBA8 | IronWallDepthSampler:DEPTH_COMPONENT |
| `wander_world_wave_field` | q.screen_width×q.screen_height | True | WaveDataSampler:RGBA16F, WaveMaterialSampler:RGBA8 | WaveDepthSampler:DEPTH_COMPONENT |

## 6. 共享 GLSL 库

- `nine_heavens_sky.glsl`（107 行）函数：nineHash, nineNoise, nineFbm, nineRotateY, nineStars, renderNineHeavensSky

## 7. 其它资产

| 目录 | 文件数 |
|---|---|
| `animations/` | 12 |
| `geo/` | 12 |
| `gzsm模型/` | 2 |
| `lss/` | 1 |
