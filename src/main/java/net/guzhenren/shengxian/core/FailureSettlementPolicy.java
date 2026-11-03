package net.guzhenren.shengxian.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure recovery decisions; ambiguous player/world evidence can never request another damage attempt. */
public final class FailureSettlementPolicy {
    public enum ReceiptStage { STARTED, RESPAWN_RECEIPT }
    public enum Action { ATTEMPT_ONCE, MARK_UNCERTAIN, ACKNOWLEDGE, NOOP }
    public record PlayerReceipt(UUID operationId, AscensionProgress.FailureCause cause, ReceiptStage stage) {
        public PlayerReceipt {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(cause, "cause");
            Objects.requireNonNull(stage, "stage");
        }
    }
    public record TribulationImpactPlayerReceipt(
            UUID operationId, AscensionProgress.TribulationImpactFailure context, ReceiptStage stage) {
        public TribulationImpactPlayerReceipt {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(context, "impact context");
            Objects.requireNonNull(stage, "stage");
        }
    }

    private FailureSettlementPolicy() {}

    public static Action decide(AscensionProgress.FailureSettlementState worldState,
                                UUID expectedOperationId,
                                AscensionProgress.FailureCause expectedCause,
                                Optional<PlayerReceipt> playerReceipt,
                                boolean receiptKeyPresent) {
        Objects.requireNonNull(worldState, "worldState");
        Objects.requireNonNull(expectedOperationId, "expectedOperationId");
        Objects.requireNonNull(expectedCause, "expectedCause");
        Objects.requireNonNull(playerReceipt, "playerReceipt");
        boolean completion = playerReceipt.filter(receipt -> receipt.operationId().equals(expectedOperationId)
                && receipt.cause() == expectedCause && receipt.stage() == ReceiptStage.RESPAWN_RECEIPT).isPresent();
        if (worldState == AscensionProgress.FailureSettlementState.ACKNOWLEDGED) return Action.NOOP;
        if (worldState == AscensionProgress.FailureSettlementState.NONE) return Action.NOOP;
        if (completion) return Action.ACKNOWLEDGE;
        return switch (worldState) {
            case PREPARED -> !receiptKeyPresent ? Action.ATTEMPT_ONCE : Action.MARK_UNCERTAIN;
            case STARTED -> Action.MARK_UNCERTAIN;
            case UNCERTAIN, RESPAWN_RECEIPT -> Action.NOOP;
            case NONE, ACKNOWLEDGED -> Action.NOOP;
        };
    }

    /** Independent impact policy: only matching profile/carrier/sequence evidence can complete. */
    public static Action decideTribulationImpact(
            AscensionProgress.FailureSettlementState worldState,
            UUID expectedOperationId,
            AscensionProgress.TribulationImpactFailure expectedContext,
            Optional<TribulationImpactPlayerReceipt> playerReceipt,
            boolean receiptKeyPresent) {
        Objects.requireNonNull(worldState, "worldState");
        Objects.requireNonNull(expectedOperationId, "expectedOperationId");
        Objects.requireNonNull(expectedContext, "expected impact context");
        Objects.requireNonNull(playerReceipt, "playerReceipt");
        boolean completion = playerReceipt.filter(receipt ->
                receipt.operationId().equals(expectedOperationId)
                        && receipt.context().equals(expectedContext)
                        && receipt.stage() == ReceiptStage.RESPAWN_RECEIPT).isPresent();
        if (worldState == AscensionProgress.FailureSettlementState.ACKNOWLEDGED
                || worldState == AscensionProgress.FailureSettlementState.NONE) return Action.NOOP;
        if (completion) return Action.ACKNOWLEDGE;
        return switch (worldState) {
            case PREPARED -> !receiptKeyPresent ? Action.ATTEMPT_ONCE : Action.MARK_UNCERTAIN;
            case STARTED -> Action.MARK_UNCERTAIN;
            case UNCERTAIN, RESPAWN_RECEIPT -> Action.NOOP;
            case NONE, ACKNOWLEDGED -> Action.NOOP;
        };
    }
}
