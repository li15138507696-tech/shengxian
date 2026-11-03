package net.guzhenren.shengxian.core;

import java.io.IOException;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.BooleanSupplier;

/** Safe recovery policy and ordered commit for the upstream rank projection. */
public final class RankSixSyncDecision {
    public enum Action { APPLY_RANK_SIX, ACKNOWLEDGE_ONLY, DEFER }
    @FunctionalInterface public interface CheckedAction { void run() throws IOException; }
    private RankSixSyncDecision() {}

    public static Action evaluate(double currentRank) {
        if (!Double.isFinite(currentRank)) return Action.DEFER;
        if (Double.compare(currentRank, 5.0) == 0) return Action.APPLY_RANK_SIX;
        if (currentRank >= 6.0) return Action.ACKNOWLEDGE_ONLY;
        return Action.DEFER;
    }

    /**
     * The aperture's pending intent already exists. Acknowledge only after a confirmed
     * in-memory projection has been saved and verified in the authoritative player file.
     * A retry at rank six or above persists again without repeating the rank write.
     */
    public static Action commit(DoubleSupplier readRank, CheckedAction applyRank,
                                CheckedAction persistAndVerify, CheckedAction acknowledge) throws IOException {
        return commit(readRank,()->false,applyRank,persistAndVerify,acknowledge);
    }

    /** A receipt in the same saved player NBT proves this session already applied its projection. */
    public static Action commit(DoubleSupplier readRank,BooleanSupplier projectionRecorded,
                                CheckedAction applyRank,CheckedAction persistAndVerify,
                                CheckedAction acknowledge) throws IOException {
        return commit(readRank,projectionRecorded,true,applyRank,persistAndVerify,acknowledge);
    }

    /** Legacy intents may confirm an existing rank six, but cannot safely replay a rank-five write. */
    public static Action commit(DoubleSupplier readRank,BooleanSupplier projectionRecorded,
                                boolean intentSupportsRankReplay,CheckedAction applyRank,
                                CheckedAction persistAndVerify,CheckedAction acknowledge) throws IOException {
        Objects.requireNonNull(projectionRecorded);
        Objects.requireNonNull(readRank);
        Objects.requireNonNull(applyRank);
        Objects.requireNonNull(persistAndVerify);
        Objects.requireNonNull(acknowledge);
        double currentRank=readRank.getAsDouble();
        if(projectionRecorded.getAsBoolean() && evaluate(currentRank)!=Action.ACKNOWLEDGE_ONLY) return Action.DEFER;
        Action action=evaluate(currentRank);
        if(action==Action.DEFER || (!projectionRecorded.getAsBoolean() && !intentSupportsRankReplay
                && action==Action.APPLY_RANK_SIX)) return Action.DEFER;
        if(action==Action.APPLY_RANK_SIX) applyRank.run();
        double confirmedRank=readRank.getAsDouble();
        if(evaluate(confirmedRank)!=Action.ACKNOWLEDGE_ONLY)
            throw new IOException("Upstream rank write was not confirmed; recovery remains pending");
        persistAndVerify.run();
        if(Double.compare(readRank.getAsDouble(),confirmedRank)!=0)
            throw new IOException("Upstream rank changed during persistence; recovery remains pending");
        acknowledge.run();
        return action;
    }
}