# SDD ledger — plan: 2026-10-29-native-body-field-transaction.md

Spec: `docs/superpowers/specs/2026-10-29-native-body-field-transaction-design.md` (user approved; legacy baseline boundary approved).
Execution: inline in the existing checkout because the user requested continuation and this project has no Git repository.

Ruling: use the current checkout without worktree isolation — `git rev-parse --show-toplevel` confirmed there is no Git repository, and the project plan documents this project as non-Git. Cost if wrong: changes cannot be isolated or commit-tracked; no shared Git state is modified.

Pre-flight: Tasks 1→2 share the new `ShatterEssenceTransaction` stage/identity model; Task 2's NBT codec must preserve the same validation. Task 3 consumes those interfaces from Tasks 1–2 and gates the lifecycle timer on COMMITTED. Task 4 depends on the final receipt/verification counts.

Task 1: complete — schema 10 world intent stores before/target; schema 9 target-only migration test proves the baseline stays absent; recovery decision tests cover PREPARED, COMMITTED, UNCERTAIN and conflicting values.
Task 2: complete — PlayerPersisted receipt codec and `VerifyShatterEssenceReceipt` are wired into NbtCheck; player and singleplayer host source readers reject mismatched receipt/body state before updates.
Task 3: complete — `ShatterEssenceReconciler` enforces PREPARED → exact debit → COMMITTED; BREAKING tick and login only verify a matching commit; legacy target-only sessions do not reconstruct a debit.
Task 4: complete — existing `zhuanshu/jieduan` and `shiqiao` receipt paths were reviewed against their regressions; progress/evidence/verification docs were updated. Readiness remains closed.

Final verification (2026-10-30): `FrameworkTest` 192 passed / 0 failed; `NbtCheck` passed the five receipt verifiers including 30 shatter compressed-NBT checks; `SourceCheck` completed Java 21 source compilation, 8 Gu activation hook checks, and all receipt verifiers.
Runtime boundary: no Minecraft client/server was launched, no real save was opened, and no version bump, package, or commit was made.
