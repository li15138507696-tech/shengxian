package net.guzhenren.shengxian.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only status text for the two durable sides of failure settlement. */
public final class FailureSettlementDiagnostic {
    private FailureSettlementDiagnostic() {}

    public static String format(AscensionProgress.FailureSettlementState worldState,
                                UUID expectedOperationId,
                                Optional<AscensionProgress.FailureCause> expectedCause,
                                Optional<FailureSettlementPolicy.PlayerReceipt> receipt,
                                boolean receiptKeyPresent) {
        Objects.requireNonNull(worldState, "worldState");
        Objects.requireNonNull(expectedOperationId, "expectedOperationId");
        Objects.requireNonNull(expectedCause, "expectedCause");
        Objects.requireNonNull(receipt, "receipt");
        String playerState;
        if (!receiptKeyPresent) {
            playerState = "NONE";
        } else if (receipt.isEmpty()) {
            playerState = "MALFORMED";
        } else {
            FailureSettlementPolicy.PlayerReceipt value = receipt.get();
            playerState = value.operationId().equals(expectedOperationId)
                    && expectedCause.filter(cause -> value.cause() == cause).isPresent()
                    ? value.stage().name() : "MISMATCH";
        }
        return "失败结算：世界=" + worldState + "，操作ID=" + expectedOperationId + "，玩家收据=" + playerState;
    }

    /** Status for the separate impact receipt, including the carrier provenance identity. */
    public static String formatTribulationImpact(
            AscensionProgress.FailureSettlementState worldState,
            UUID expectedOperationId,
            Optional<AscensionProgress.TribulationImpactFailure> expectedContext,
            Optional<FailureSettlementPolicy.TribulationImpactPlayerReceipt> receipt,
            boolean receiptKeyPresent) {
        Objects.requireNonNull(worldState, "worldState");
        Objects.requireNonNull(expectedOperationId, "expectedOperationId");
        Objects.requireNonNull(expectedContext, "expectedContext");
        Objects.requireNonNull(receipt, "receipt");
        String profile = expectedContext.map(context -> context.profile().name()).orElse("MISSING");
        String carrier = expectedContext.map(context -> context.carrierType().name() + "/" + context.carrierId())
                .orElse("MISSING");
        String impactSequence = expectedContext.map(context -> Long.toString(context.impactSequence()))
                .orElse("MISSING");
        String playerState;
        if (!receiptKeyPresent) {
            playerState = "NONE";
        } else if (receipt.isEmpty()) {
            playerState = "MALFORMED";
        } else {
            FailureSettlementPolicy.TribulationImpactPlayerReceipt value = receipt.get();
            playerState = value.operationId().equals(expectedOperationId)
                    && expectedContext.filter(value.context()::equals).isPresent()
                    ? value.stage().name() : "MISMATCH";
        }
        return "灾劫冲击结算：世界=" + worldState + "，操作ID=" + expectedOperationId
                + "，profile=" + profile + "，carrier=" + carrier + "，冲击序号=" + impactSequence
                + "，玩家回执=" + playerState;
    }
}
