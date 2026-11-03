package net.guzhenren.shengxian.core;

import java.util.Objects;

/**
 * The explicit rule boundary for gu backlash.
 *
 * <p>The numeric threshold and item-loss mapping are server game parameters. Only a completed
 * event from one of the specifically observed sources can enter this rule, and only during
 * {@link Phase#ABSORBING}. This does not make ordinary ascension readiness available.</p>
 */
public final class GuActivationPolicy {
    private GuActivationPolicy() {}

    /**
     * Feeding, holding and mere inventory ticks are not activation. A tick branch counts only
     * when upstream itself accepted the activation and the caller says so.
     */
    public static boolean countsAsActivation(GuActivation activation) {
        Objects.requireNonNull(activation, "activation");
        if (!activation.accepted()) return false;
        return switch (activation.entry()) {
            case RIGHT_CLICK_USE, ATTACK_HURT_ENEMY, BLOCK_USE_ON, INVENTORY_TICK, KEYBIND, PROCEDURE -> true;
        };
    }

    /**
     * Entries whose upstream branch only schedules a later result. Moonlight Gu queues a
     * projectile and Bai Xiang She Gu spawns its snake afterwards, so ACCEPTED is not the same
     * as the effect existing.
     */
    public static boolean requiresCompletion(GuActivation.Entry entry) {
        Objects.requireNonNull(entry, "entry");
        return switch (entry) {
            case RIGHT_CLICK_USE, ATTACK_HURT_ENEMY, BLOCK_USE_ON, KEYBIND -> true;
            case INVENTORY_TICK, PROCEDURE -> false;
        };
    }

        /** Records accepted Gu use only after the one-way ascension attempt has begun. */
    public static boolean mayObserveDuringAscension(Phase phase) {
        Objects.requireNonNull(phase, "phase");
        return switch (phase) {
            case BREAKING, BROKEN, ABSORBING, OPENING, FORMING -> true;
            case PREPARING, IMMORTAL, FAILED -> false;
        };
    }
    /** A result point, exact source branch and the narrow Nascent Qi window are all required. */
    public static boolean mayApplyBacklash(Phase phase, GuActivation activation) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(activation, "activation");
        if (phase != Phase.ABSORBING || activation.outcome() != GuActivation.Outcome.COMPLETED) return false;
        return switch (activation.entry()) {
            case RIGHT_CLICK_USE, ATTACK_HURT_ENEMY, BLOCK_USE_ON, KEYBIND -> true;
            case INVENTORY_TICK, PROCEDURE -> false;
        };
    }

    /** Human-readable game-rule boundary; the value is not presented as canon. */
    public static String backlashBlockedReason() {
        return "蛊反噬仅按服务端纳气期游戏参数处理；未确认效果或未核实入口不累计。";
    }
}
