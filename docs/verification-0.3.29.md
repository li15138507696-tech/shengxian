# 0.3.29 验证记录：音效层 + 进度成就（两个并行代理交付后由 Lead 收口）

日期：2026-11-01。基线 0.3.28。本轮用两个并行代理分别做音效与成就，Lead 负责接线、合并构建与实机验收。

## 一、音效层（代理 A 交付 + Lead 补接线）

### 代理纠正了一个错误前提

我派活时说"模组完全没有声音"，代理核代码后指出：客户端**已有**音效——`client/ClientFx` 为全部灾劫/仙窍 FX cue 播 `playLocalSound`，`client/RitualSoundscape` 为 8 个 phase 转场发声，二者受 `ShengxianVisualConfig` 与视距门控。缺的是 **.ogg 资源**与**服务端厚度**，不是"没接线"。

因此实现改为：服务端近距底层音，**刻意选与客户端不同的原版 SoundEvents**（同一段采样不会在混音里出现两遍），并让关掉视觉配置的玩家也能听到。

### 交付内容

新文件 `server/AscensionSounds.java`（280 行）：cue 表 + 窄接口 + 节流器。

| 节点 | 原版事件 | 节流 |
| --- | --- | --- |
| 灾劫载体出现 ×5 型 | `END_PORTAL_SPAWN` 变调 0.48–0.72 | 30 tick / 8 格同格冷却 |
| 载体被击退 | `WARDEN_HEARTBEAT` | 10 tick 同格 |
| 致死灾劫 | `SCULK_SHRIEKER_SHRIEK` 低沉 | 40 tick |
| 仙窍开/关 | `PORTAL_TRIGGER` / `PORTAL_TRAVEL` | 40 tick |
| 调气中断（客户端静音，真补空） | `AMETHYST_BLOCK_RESONATE` | 60 tick |
| 定仙游（往来/蓄力） | `ENDERMAN_TELEPORT` / `AMETHYST_BLOCK_RESONATE` | 蓄力 24 tick |
| 人如故 | `ENCHANTMENT_TABLE_USE` | 20 tick |
| 江山如故 | `MOSS_PLACE` | 全局 12 tick |
| 8 个 phase 转场 | `BEACON_POWER_SELECT` / `GLASS_BREAK` / `SHULKER_CLOSE` / `AMETHYST_BLOCK_CHIME` / `END_PORTAL_FRAME_FILL` 等 | 60–80 tick |
| 三气调和脉冲 | `AMETHYST_BLOCK_CHIME` 强度 ≥0.25 | 20 tick |

节流框架：每 tick 硬上限 8 条；同格/维度级冷却（表 512 项、200 tick 过期）；最小强度阈值；仅主线程。

**顺手修掉一个真实刷屏 bug**：`XianGuEffects.terrain` 原来按 `terrainParticles % 16 == 1` 播 `GRASS_PLACE`，而该函数每复原一格调用一次、单 tick 预算 512 格 → 单 tick 可播放几十次。现改为全局 12 tick 一次。

### Lead 补的两处接线

代理留了 4 个节点没接（唯一插桩点在其禁改文件里）：

1. `AscensionLifecycleService` 的 7 个阶段转移点 → `AscensionSounds.phaseOnce(player, phase)`
2. `AscensionControlService.onServerTick` 每次 `stepSimulation` 后 → `AscensionSounds.harmony(level, pos, harmonyOf(record))`，其中 `harmonyOf` 用与开窍门禁同一个 `balanceTolerance` 度量三气离散度
3. `AscensionOutcomeService.send`（IMMORTAL/FAILED 终态）→ `phaseOnce`

为第 3 点新增了 `phaseOnce`：终态由视觉心跳每 `HEARTBEAT_TICKS` 重复检测，直接调用 `phase` 会让成仙/失败音效无限重播；`phaseOnce` 按 (玩家, 阶段) 记忆 1200 tick，同一阶段不重复。

## 二、进度成就（代理 B 交付）

8 个成就，独立进度页，`minecraft:impossible` 触发 + 代码授予（数据包无法自证，只能服务端观察到提交后的阶段才发）。

主线 `root → shatter → absorb → opening → forming → immortal`（终点 frame=`challenge`），`failed` 挂 root 下作分支，`xian_gu` 挂 immortal 下（goal）。

授予点：`prepare()`、tick 里 `advance(BROKEN)` 之后（BROKEN 与 ABSORBING 同 tick 提交，必须在推进前授予）、`open()`、`offerVitalGu()`、`XianGuUseService.complete()`（只授予施术者，失败/隔离的 use 不发），以及 IMMORTAL/FAILED 的 20 tick 对账兜底。授予幂等，即时点与轮询可共存。

## 三、验证

```
Framework tests: 200 passed, 0 failed
PASS exact Gu activation hooks: 8 verified targets
PASS representative Gu explosion hooks and assets: 67 checks
PASS saved rank projection / failure receipt / tribulation impact receipt: 22 / 22 / 22
PASS saved Gu backlash: 31
PASS saved shatter essence receipt: 30
PASS reusable immortal Gu receipt: 23        ← 见下方更正
PASS static common-side dependency boundary: 237 class files
SHA256: b3243f1eca150aed1cc7c23c86d8b807c07f4748af0af585f05b92c5587ac6b1
```

隔离专用服务器：`shengxian (version 0.3.28 -> 0.3.29)`，`Done (2.306s)`，shengxian ERROR 0 条，sound 相关错误 0 条。

**成就实机验证**（客户端 + RCON）：

| 命令 | 结果 |
| --- | --- |
| `advancement grant FlowBot only shengxian:ascension/root` | “已经拥有” → **自动授予路径已生效**（该角色会话为 IMMORTAL，对账补授了主线全部节点） |
| 同上 `/shatter`、`/immortal` | “已经拥有” |
| `… /xian_gu` | `Granted [仙蛊初鸣]`（尚未赚取，手动授予成功） |
| `… /nonexistent` | `Unknown advancement`（正确拒绝） |

日志里唯一一条 advancement 错误是 **本体 mod 的 `guzhenren:zijingsheligu`**（既有问题，与本模组无关）。

### 更正：回执检查项数字

0.3.27/0.3.28 文档里写的“reusable immortal Gu receipt: 19”是**陈旧数字**（沿用了 0.3.21 时期校验器的输出）。校验器在 10-30 更新后实际为 **23**，两版文档已改。

## 四、未验证

- **音效的实际听感**：编译期能保证 SoundEvents 常量存在（编译通过），运行期日志无 sound 错误；但"好不好听、音量是否合适"必须真人戴耳机判断。
- 成就 toast 与进度页 tab 的**视觉呈现**：只能靠 JSON 结构校验 + 服务端授予成功推断，未截图。
- 完整的成就链（碎窍→纳气→开窍→成型→成仙）需要一次真实的升仙流程才能逐个点开；本轮只有 IMMORTAL 终态的对账补授证据。
