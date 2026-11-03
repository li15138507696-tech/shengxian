# 本体字段事务与恢复校验

日期：2026-10-30  
范围：碎窍 `zhenyuan` 一次性扣费的世界意图、玩家回执、双存档读回和恢复顺序。源码仍为 0.3.21 基线，本轮未改版本。

## 已实现

- 世界会话 schema 10 的碎窍意图同时记录 `phaseRevision`、扣费前值和固定目标。schema 9 继续可读，但旧 target-only 记录不会获得推算出的 baseline。
- 玩家 `PlayerPersisted/shengxian:shatter_essence` 回执绑定 `sessionId + phaseRevision + before + target`，阶段为 PREPARED、COMMITTED 或 UNCERTAIN。PREPARED 必须带 before 且尚无扣费确认值；COMMITTED 必须记录与目标相等的确认值。
- 首次写入先保存世界意图，再保存并核验 PREPARED；只在实时 `zhenyuan` 仍精确等于 before 时写入目标，随后保存并核验 COMMITTED。
- 恢复时，PREPARED + before 才应用目标；PREPARED + target 只补 COMMITTED。COMMITTED 后仅核对回执，不再把真元钳回旧目标。
- 写回任何新回执前，先检查玩家 UUID 文件与单人 `level.dat/Data/Player` 各自的旧回执和 `zhenyuan`。保存后两处分别读回。来源冲突时不通过保存把一方覆盖成另一方。
- schema 9 target-only 会话只有在玩家侧存在身份与目标匹配的 COMMITTED 回执时才能确认；其他情况隔离并停止计时，不推算 baseline、不补扣。若来源冲突使 UNCERTAIN 也无法安全写回，世界会话仍保持 BREAKING，后续动作继续被拒绝。
- 未审计的 `kongqiao`、`renqi`、`shouyuan` 以及不存在的本体仙窍/仙元字段仍只读；`AscensionReadiness.implementedCapabilities()` 仍为空。

## 回归结果

- RED/GREEN 覆盖世界 schema 10 意图、schema 9 target-only 读取、第三值与身份冲突、PREPARED 两个崩溃窗口、COMMITTED 后后续真元值不被重写，以及准备保存失败时不执行扣费。
- `FrameworkTest`：192 项通过，0 项失败。
- `NbtCheck`：5 类 receipt verifier 全通过：rank 22、失败结算 22、具名灾劫冲击 22、蛊反噬 31、碎窍真元 30 项压缩 NBT 检查；8 个蛊激活钩子校验也通过。
- `SourceCheck`：Java 21 完整源码编译通过，8 个蛊激活钩子校验及 5 类 receipt verifier 通过。
- 复核了现有 `zhuanshu/jieduan` rank projection 与 `shiqiao` failure receipt 顺序和门禁；已有专项回归覆盖合法降转、玩家/host 回执核验及未就绪失败，本轮未改这两条专用路径。

## 验收边界

本轮没有启动 Minecraft 客户端或服务器，没有打开真实存档，也没有做掉线/崩溃实机演练。没有升版本、打包或提交。通过的是领域恢复决策、隔离压缩 NBT、源码编译和静态钩子校验；实机端行为仍需之后在隔离世界验收。普通升仙 readiness 继续关闭。
