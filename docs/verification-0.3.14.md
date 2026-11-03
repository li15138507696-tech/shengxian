# 0.3.14 离线验证记录：四条本体催蛊观察钩子

日期：2026-10-28。基线保留 0.3.13；本次产物单独递增到 0.3.14。

## 变更范围

0.3.14 在四个真实上游 Procedure 中，于唯一的 PlayerVariables.zhenyuan 写入后观察接受催动：

| 蛊虫 / 入口 | 上游 Procedure |
| --- | --- |
| 月光蛊，右键 | YueGuangGuDangYouJiKongQiShiShiTiDeWeiZhiProcedure |
| 白象蛇蛊，右键 | BaiXiangSheGuYouJiKongQiShiShiTiDeWeiZhiProcedure |
| 哀别持，攻击 | AiBieChiDangShengWuShiTiBeiGaiWuPinJiZhongProcedure |
| 定蛊二号，方块使用 | DingGu2YouJiFangKuaiShiFangKuaiDeWeiZhiProcedure |

注入只在服务端 ServerPlayer、存在 first 升仙会话且处于 BREAKING、BROKEN、ABSORBING、OPENING、FORMING 时计入 ACCEPTED 事件。观察账本是服务端内存数据，不写玩家或会话存档。没有实现反噬、物品损失、损失概率、本命蛊保护或升仙结果变更。

## 静态安全校验

- 上游内层 JAR SHA256：b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc。
- tools/VerifyGuActivationHooks.java 用 ASM 核对四个目标 Procedure 的方法描述符和唯一 zhenyuan PUTFIELD，并检查编译后的 Mixin 目标类、完整 callback 描述符、Inject 方法描述符、require=1、字段类型/目标/opcode 与 AFTER 位移。
- 校验 Veil 共用的 required=true、JAVA_21 Mixin 配置；neoforge.mods.toml 对配置只注册一次。Build 与 Verify 还检查归档包含四个 Mixin 类、GuActivationObserver 与配置文件。
- common-side 客户端类引用隔离检查：134 个类通过。

## 执行结果

- FrameworkTest：157 passed，0 failed。
- 玩家持久化投影：22 项压缩 NBT 检查通过。
- SourceCheck：Java 21 全源码编译通过；四个精确本体目标校验通过。
- Build：通过，Jar-in-Jar Veil 4.5.2 指纹保持不变。
- Verify：通过。
- 升仙网络协议离线回环：8 个阶段与元数据/年龄边界通过；结果协议 23 项通过；压缩 NBT 22 项通过。
- 0.3.13 不可变 JAR 保留，SHA256 a298672f8ea0fed5f5a7033eb6fb996f28e4c23e7152403d603d189087ef20d7。

## 未验证与仍未完成

- 本轮没有启动 Minecraft、集成世界、专用服务器或 DSH；运行时 Mixin 加载与回调实际触发未验证。
- 未覆盖本体所有蛊虫、被动维护和杀招按键入口；异步效果完成点尚未统一观察。
- 未应用原著反噬或蛊虫损失规则。原著没有提供可直接用于通用路线的明确概率、本命蛊豁免或逐蛊销毁策略；继续保持 GU_BACKLASH 与普通升仙 readiness 关闭。
- 一般普通路线灾劫规则、本体仙窍数据映射、完整失败后果和多人运行验收仍未完成。

## 产物

- 路径：build/libs/shengxian-0.3.14.jar
- SHA256：78a57340e94c8254335d14d47a03802e730221833130eee08c70b0b6826c7508
- 内嵌 Veil 4.5.2 SHA256：e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66