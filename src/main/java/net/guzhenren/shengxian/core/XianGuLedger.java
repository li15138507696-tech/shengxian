package net.guzhenren.shengxian.core;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Ownership of immortal gu, stored inside the ascension session's append-only effect ledger.
 *
 * <p>The ledger may only grow, so ownership is a pair of counters per gu: every grant appends
 * {@code xiangu:owned:<id>:<n>} and every use appends {@code xiangu:used:<id>:<n>}. A player
 * holds the gu while the owned count exceeds the used count, which keeps the monotonic
 * invariant intact and still lets an operator hand the same gu out again later.</p>
 *
 * <p>Uniqueness is per session only: GAP-004 records that world-wide immortal gu uniqueness is
 * not verified, so this class must not be presented as a world uniqueness rule.</p>
 */
public final class XianGuLedger {
    private static final String OWNED_PREFIX = "xiangu:owned:";
    private static final String USED_PREFIX = "xiangu:used:";

    private XianGuLedger() {}

    /** Appends one grant; repeated grants stack so a used gu can be handed out again. */
    public static AscensionProgress hold(AscensionProgress current, XianGu gu) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(gu, "gu");
        if (gu.reusable()) return current.withXianGu(current.xianGu().grant(gu.id()));
        return current.withAppliedEffect(OWNED_PREFIX + gu.id() + ":" + (owned(current, gu.id()) + 1));
    }

    /** Appends one consumption; requires the gu to be held right now. */
    public static AscensionProgress consume(AscensionProgress current, XianGu gu) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(gu, "gu");
        if (gu.reusable()) throw new IllegalStateException("Reusable immortal Gu is not consumed by activation");
        if (!holds(current, gu.id())) throw new IllegalStateException("Not held: " + gu.id());
        return current.withAppliedEffect(USED_PREFIX + gu.id() + ":" + (used(current, gu.id()) + 1));
    }

    public static boolean holds(AscensionProgress current, String guId) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(guId, "gu id");
        if (XianGuRuntime.IDS.contains(guId)) return current.xianGu().held().contains(guId);
        return owned(current, guId) > used(current, guId);
    }

    public static int owned(AscensionProgress current, String guId) {
        if (XianGuRuntime.IDS.contains(guId)) return current.xianGu().held().contains(guId) ? 1 : 0;
        return count(current, OWNED_PREFIX + guId + ":");
    }

    public static int used(AscensionProgress current, String guId) {
        if (XianGuRuntime.IDS.contains(guId)) return 0;
        return count(current, USED_PREFIX + guId + ":");
    }

    /** Ids the player currently holds, with used copies already subtracted. */
    public static Set<String> held(AscensionProgress current) {
        Objects.requireNonNull(current, "current");
        Set<String> ids = new LinkedHashSet<>(current.xianGu().held());
        for (String key : current.appliedEffects()) {
            if (!key.startsWith(OWNED_PREFIX)) continue;
            String rest = key.substring(OWNED_PREFIX.length());
            int split = rest.lastIndexOf(':');
            if (split <= 0) continue;
            String id = rest.substring(0, split);
            if (holds(current, id)) ids.add(id);
        }
        return Set.copyOf(ids);
    }

    private static int count(AscensionProgress current, String prefix) {
        int total = 0;
        for (String key : current.appliedEffects()) {
            if (key.startsWith(prefix)) total++;
        }
        return total;
    }
}
