package net.guzhenren.shengxian.core;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Executes the tested receipt order around the pure shatter recovery decision. */
public final class ShatterEssenceReconciler {
    private ShatterEssenceReconciler() { }

    /** Small persistence port so the side-effect order can be verified without starting Minecraft. */
    public interface Port {
        Optional<ShatterEssenceTransaction.Receipt> readReceipt() throws IOException;
        double currentTrueEssence() throws IOException;
        /** Saves and verifies the requested receipt against the supplied current field value. */
        void persist(ShatterEssenceTransaction.Receipt receipt, Double expectedEssence) throws IOException;
        /** Writes target only when the exact matching PREPARED receipt and baseline are present. */
        void applyTarget(ShatterEssenceTransaction.Receipt prepared) throws IOException;
        /** Read-only verification of one receipt stage and the exact field value. */
        void verify(ShatterEssenceTransaction.Receipt receipt, double expectedEssence) throws IOException;
        /** Read-only receipt verification that leaves later legitimate true-essence changes alone. */
        void verifyCommitted(ShatterEssenceTransaction.Receipt receipt) throws IOException;
        /** Best-effort durable quarantine; implementations must not replace a conflicting receipt. */
        void markUncertain(UUID sessionId, long phaseRevision, Double worldBefore, double target);
    }

    public static void reconcile(UUID sessionId, long phaseRevision, Double worldBefore,
                                 double target, Port port) throws IOException {
        Objects.requireNonNull(port, "shatter port");
        Optional<ShatterEssenceTransaction.Receipt> saved = port.readReceipt();
        double current;
        try {
            current = port.currentTrueEssence();
        } catch (IOException invalidCurrent) {
            port.markUncertain(sessionId, phaseRevision, worldBefore, target);
            throw invalidCurrent;
        }

        ShatterEssenceTransaction.Decision decision = ShatterEssenceTransaction.decide(
                saved, sessionId, phaseRevision, worldBefore, target, current);
        switch (decision) {
            case WRITE_PREPARED -> {
                ShatterEssenceTransaction.Receipt prepared = ShatterEssenceTransaction.Receipt.prepared(
                        sessionId, phaseRevision, worldBefore, target);
                try {
                    port.persist(prepared, worldBefore);
                    port.applyTarget(prepared);
                    commit(port, prepared);
                } catch (IOException failure) {
                    port.markUncertain(sessionId, phaseRevision, worldBefore, target);
                    throw failure;
                } catch (RuntimeException failure) {
                    port.markUncertain(sessionId, phaseRevision, worldBefore, target);
                    throw new IOException("Shatter true-essence transaction failed", failure);
                }
            }
            case APPLY_TARGET -> {
                ShatterEssenceTransaction.Receipt prepared = saved.orElseThrow();
                try {
                    port.verify(prepared, prepared.before());
                    port.applyTarget(prepared);
                    commit(port, prepared);
                } catch (IOException failure) {
                    port.markUncertain(sessionId, phaseRevision, worldBefore, target);
                    throw failure;
                } catch (RuntimeException failure) {
                    port.markUncertain(sessionId, phaseRevision, worldBefore, target);
                    throw new IOException("Shatter true-essence recovery failed", failure);
                }
            }
            case COMMIT_ALREADY_APPLIED -> {
                ShatterEssenceTransaction.Receipt prepared = saved.orElseThrow();
                try {
                    port.verify(prepared, prepared.target());
                    commit(port, prepared);
                } catch (IOException failure) {
                    port.markUncertain(sessionId, phaseRevision, worldBefore, target);
                    throw failure;
                } catch (RuntimeException failure) {
                    port.markUncertain(sessionId, phaseRevision, worldBefore, target);
                    throw new IOException("Shatter true-essence confirmation failed", failure);
                }
            }
            case ALREADY_COMMITTED -> port.verifyCommitted(saved.orElseThrow());
            case QUARANTINE -> {
                port.markUncertain(sessionId, phaseRevision, worldBefore, target);
                throw new IOException("Shatter true essence and its durable receipt cannot be reconciled");
            }
        }
    }

    private static void commit(Port port, ShatterEssenceTransaction.Receipt prepared) throws IOException {
        ShatterEssenceTransaction.Receipt committed = ShatterEssenceTransaction.Receipt.committed(
                prepared.sessionId(), prepared.phaseRevision(), prepared.before(), prepared.target());
        port.persist(committed, prepared.target());
    }
}
