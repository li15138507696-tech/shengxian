# 0.3.28 验证记录：账面清账

日期：2026-10-31。基线 0.3.27。本版**无玩法变更**，只做工程账面清理。

## 变更

1. **`build.ps1` 默认版本跟随最新**：默认 `$ModVersion` 从 `0.3.18` 改为当前最新（0.3.27）。原默认值是一处脚枪——忘记传 `-ModVersion` 时会去重建早已存在的 0.3.18 产物并撞上不可变产物锁；现在默认值指向最新版，误用会立刻失败而不是静默出错。已加注释说明"每次发版需同步改这一行"。
2. **补档两份历史验证记录**：
   - `verification-0.3.17.md`：失败结算持久化的中间版本，正文依据计划第 66 行与 0.3.18 档，标注为补档、不新增事实。
   - `verification-0.3.11.md`：六转同步恢复崩溃窗口修复，依据计划第 71 行与 `SavedRankProjection` 现有校验。
3. **证据索引补登**：`docs/evidence/README.md` 增加 `upstream-reads-audit.md`（0.3.27 的读取端审计）。
4. **Task 5 销账确认**：`docs/superpowers/plans/2026-10-30-representative-immortal-gu-progress.md` 的 "最新静态 verifier 待合并重跑" 实际已完成——`Build` 现在固定执行全部 9 类静态校验。

## 验证（0.3.28 全量）

```
Framework tests: 200 passed, 0 failed
PASS exact Gu activation hooks: 8 verified targets
PASS representative Gu explosion hooks and assets: 67 checks
PASS saved rank projection: 22 compressed-NBT checks
PASS saved failure receipt: 22 compressed-NBT checks
PASS saved tribulation impact receipt: 22 compressed-NBT checks
PASS saved Gu backlash: 31 compressed-NBT checks
PASS saved shatter essence receipt: 30 compressed-NBT checks
PASS reusable immortal Gu receipt: 23 compressed-NBT checks
PASS static common-side dependency boundary: 237 class files
SHA256: 35a7c31c7a71214e083c084b8d1cac7005472fc10722942eb9f4d42e9f7f404d
```

隔离专用服务器：`shengxian (version 0.3.27 -> 0.3.28)`，`Done (2.317s)`，shengxian 相关 ERROR 0 条。

产物：`build/libs/shengxian-0.3.28.jar`，三端（server / client / client-guest）已部署。

## 账面剩余

- `docs/verification-0.3.1.md`..`0.3.28` 现已连续无缺档（0.3.11 与 0.3.17 为补档）。
- 玩法侧欠账不变：三只仙蛊完整效果、深水区多人、反噬阈值实机，均需真人参与；GAP-001/004/006 需原著文本。