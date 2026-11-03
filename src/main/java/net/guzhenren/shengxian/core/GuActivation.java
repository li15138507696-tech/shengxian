package net.guzhenren.shengxian.core;

import java.util.Objects;
import java.util.UUID;

/**
 * One observed gu activation at a real upstream entry point.
 *
 * <p><b>Evidence status:</b> CANON-008 and GAP-004 support only the qualitative claim that
 * activating Gu during ascension can backfire and cause losses. The current phase window,
 * cumulative threshold, one-stack loss and BenMingGu content-fingerprint protection are game
 * mappings. This event records an observed activation; a saved backlash receipt is required to
 * establish whether the mapping consumed pressure or changed an item.</p>
 */
public record GuActivation(UUID owner, String guId, Entry entry, Outcome outcome,
                           long serverTick, long sequence) {

    /** The upstream path associated with this observed activation. */
    public enum Entry {
        /** {@code Item#use} branch that passed silence, food, durability and true-essence checks. */
        RIGHT_CLICK_USE,
        /** {@code Item#hurtEnemy} target-hit branch. */
        ATTACK_HURT_ENEMY,
        /** {@code Item#useOn} block interaction branch. */
        BLOCK_USE_ON,
        /** {@code Item#inventoryTick} maintenance branch that passed its own conditions. */
        INVENTORY_TICK,
        /** Killer-move keybind path; request event/chunk checks alone do not prove acceptance. */
        KEYBIND,
        /** Procedure reached from an entry this module cannot classify further. */
        PROCEDURE
    }

    /**
     * How far the server observed the activation. ACCEPTED is "the branch took the cost";
     * COMPLETED is "the promised result exists" (for example the delayed projectile spawned).
     */
    public enum Outcome { ACCEPTED, COMPLETED, REJECTED }

    private static final int MAX_GU_ID_LENGTH = 128;

    public GuActivation {
        Objects.requireNonNull(owner, "activation owner");
        Objects.requireNonNull(guId, "activation gu id");
        Objects.requireNonNull(entry, "activation entry");
        Objects.requireNonNull(outcome, "activation outcome");
        if (guId.isBlank() || guId.length() > MAX_GU_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid gu id");
        }
        for (int i = 0; i < guId.length(); i++) {
            if (Character.isISOControl(guId.charAt(i))) {
                throw new IllegalArgumentException("Control character in gu id");
            }
        }
        if (serverTick < 0) throw new IllegalArgumentException("Negative server tick");
        if (sequence < 0) throw new IllegalArgumentException("Negative sequence");
    }

    /** Stable de-duplication key: the same observation must never be counted twice. */
    public String key() {
        return owner + "|" + entry + "|" + guId + "|" + serverTick + "|" + sequence;
    }

    /** True when the server observed the branch taking its cost, not merely an attempt. */
    public boolean accepted() {
        return outcome != Outcome.REJECTED;
    }
}
