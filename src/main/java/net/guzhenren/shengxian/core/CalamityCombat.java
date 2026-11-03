package net.guzhenren.shengxian.core;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 灾劫战斗的权威规则：招式表、攻击状态机、命中身份。
 *
 * <p>All numbers here are game parameters, not canon: the witnessed texts name the calamities and
 * describe their attacks, but never give damage values. Damage is applied by the server after it
 * confirms range, line of sight and hit identity, so client visuals can never cause damage.</p>
 *
 * <p>Every attack has a telegraph window (the spec's 0.8-1.5 s warning) followed by a single
 * strike tick and a cooldown; the cycle then advances to the next attack.</p>
 */
public final class CalamityCombat {
    private CalamityCombat() {}

    /** Attack stage; STRIKE lasts exactly one tick and is when damage may be applied. */
    public enum Stage { TELEGRAPH, STRIKE, COOLDOWN }

    /** Every attack the five calamities can perform. */
    public enum AttackKind {
        WIND_BLADE,
        WILLOW_SWEEP,
        EYE_COLLAPSE,
        CLAW_POUNCE,
        HORN_CHARGE,
        GROUND_ROAR,
        CHIME_SINGLE,
        CHIME_DOUBLE,
        CHIME_EIGHT,
        BEAST_STOMP,
        BEAST_SWEEP,
        BEAST_RAM,
        EARTH_ERUPTION,
        BLOOD_BLADE,
        BLOOD_PILLAR
    }

    /**
     * One attack definition.
     *
     * @param damage          half-hearts dealt on a confirmed hit
     * @param radius          horizontal reach in blocks
     * @param knockback       knockback strength applied on hit
     * @param telegraphTicks  warning length before the strike lands
     * @param cooldownTicks   rest after the strike before the next attack starts
     * @param jumpDodgeable   true when leaving the ground avoids the strike
     */
    public record Attack(AttackKind kind, double damage, double radius, double knockback,
                         int telegraphTicks, int cooldownTicks, boolean jumpDodgeable) {
        public Attack {
            Objects.requireNonNull(kind, "kind");
            if (!Double.isFinite(damage) || damage <= 0) throw new IllegalArgumentException("Invalid damage");
            if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("Invalid radius");
            if (!Double.isFinite(knockback) || knockback < 0) throw new IllegalArgumentException("Invalid knockback");
            if (telegraphTicks <= 0 || cooldownTicks <= 0) throw new IllegalArgumentException("Invalid timing");
        }
    }

    /** Position inside the attack rotation. */
    public record Cycle(int attackIndex, int tickInStage, Stage stage) {
        public Cycle {
            Objects.requireNonNull(stage, "stage");
            if (attackIndex < 0 || tickInStage < 0) throw new IllegalArgumentException("Invalid cycle position");
        }
    }

    /** One hit resolution identity: a target can be struck at most once by one attack instance. */
    public record HitId(UUID carrier, int attackSequence, UUID target) {
        public HitId {
            Objects.requireNonNull(carrier, "carrier");
            Objects.requireNonNull(target, "target");
            if (attackSequence < 0) throw new IllegalArgumentException("Invalid attack sequence");
        }
    }

    private static final List<Attack> WILLOW = List.of(
            attack(AttackKind.WIND_BLADE, 6, 1.6, 0.1, 20, 40, false),
            attack(AttackKind.WILLOW_SWEEP, 8, 4.0, 0.6, 24, 50, false),
            attack(AttackKind.EYE_COLLAPSE, 10, 6.0, 0.4, 30, 80, false));

    private static final List<Attack> TIGER = List.of(
            attack(AttackKind.CLAW_POUNCE, 12, 3.0, 0.5, 24, 60, false),
            attack(AttackKind.HORN_CHARGE, 16, 2.4, 1.2, 20, 80, false),
            attack(AttackKind.GROUND_ROAR, 6, 8.0, 0.2, 16, 50, true));

    private static final List<Attack> CHIMES = List.of(
            attack(AttackKind.CHIME_SINGLE, 6, 5.0, 0.15, 20, 45, false),
            attack(AttackKind.CHIME_DOUBLE, 6, 6.0, 0.2, 24, 60, false),
            attack(AttackKind.CHIME_EIGHT, 12, 12.0, 0.8, 30, 120, false));

    private static final List<Attack> BEAST = List.of(
            attack(AttackKind.BEAST_STOMP, 10, 8.0, 0.4, 24, 60, true),
            attack(AttackKind.BEAST_SWEEP, 12, 5.0, 0.7, 20, 55, false),
            attack(AttackKind.BEAST_RAM, 16, 3.0, 1.2, 22, 80, false),
            attack(AttackKind.EARTH_ERUPTION, 8, 4.0, 0.3, 26, 70, false));

    private static final List<Attack> BLOOD = List.of(
            attack(AttackKind.BLOOD_BLADE, 6, 2.0, 0.2, 18, 40, false),
            attack(AttackKind.BLOOD_PILLAR, 10, 3.0, 0.5, 24, 70, false));

    /** Attack rotation for one calamity; an unknown type performs nothing. */
    public static List<Attack> attacksFor(AscensionProgress.Tribulation.Type type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case JADE_WILLOW_WIND -> WILLOW;
            case SINGLE_HORNED_TIGER -> TIGER;
            case SOUND_CHIMES -> CHIMES;
            case ANCIENT_BEAST -> BEAST;
            case BLOOD_RAIN -> BLOOD;
            case NONE -> List.of();
        };
    }

    /** Continuous blood-rain and blood-pool damage share one tick interval. */
    public static final double BLOOD_RAIN_DAMAGE_PER_SECOND = CalamityDamage.scaled(2.0);

    /** Furious phase threshold for the tiger and ancient beast (half health). */
    public static final double FURIOUS_HEALTH_FRACTION = 0.5;

    /** Furious phase shortens telegraph windows by this factor, never below one tick. */
    public static final double FURIOUS_TELEGRAPH_FACTOR = 0.7;

    /** Builds one attack, applying the global calamity damage scale to the base figure. */
    private static Attack attack(AttackKind kind, double baseDamage, double radius, double knockback,
                                 int telegraphTicks, int cooldownTicks, boolean jumpDodgeable) {
        return new Attack(kind, CalamityDamage.scaled(baseDamage), radius, knockback,
                telegraphTicks, cooldownTicks, jumpDodgeable);
    }

    /** Starts a rotation at the first telegraph. */
    public static Cycle start() {
        return new Cycle(0, 0, Stage.TELEGRAPH);
    }

    /**
     * Advances one tick. TELEGRAPH runs for its warning length, STRIKE occupies exactly one tick,
     * and COOLDOWN runs before the rotation moves to the next attack.
     */
    public static Cycle advance(Cycle cycle, List<Attack> attacks) {
        Objects.requireNonNull(cycle, "cycle");
        Objects.requireNonNull(attacks, "attacks");
        if (attacks.isEmpty()) throw new IllegalStateException("No attacks to advance");
        Attack current = attacks.get(Math.min(cycle.attackIndex(), attacks.size() - 1));
        int tick = cycle.tickInStage() + 1;
        return switch (cycle.stage()) {
            case TELEGRAPH -> tick >= current.telegraphTicks()
                    ? new Cycle(cycle.attackIndex(), 0, Stage.STRIKE)
                    : new Cycle(cycle.attackIndex(), tick, Stage.TELEGRAPH);
            case STRIKE -> new Cycle(cycle.attackIndex(), 0, Stage.COOLDOWN);
            case COOLDOWN -> tick >= current.cooldownTicks()
                    ? new Cycle((cycle.attackIndex() + 1) % attacks.size(), 0, Stage.TELEGRAPH)
                    : new Cycle(cycle.attackIndex(), tick, Stage.COOLDOWN);
        };
    }

    /** The attack currently being performed. */
    public static Attack currentAttack(Cycle cycle, List<Attack> attacks) {
        if (attacks.isEmpty()) throw new IllegalStateException("No attacks defined");
        return attacks.get(Math.min(cycle.attackIndex(), attacks.size() - 1));
    }

    /** True only the first time a target is hit by one attack instance. */
    public static boolean applyOnce(Set<HitId> ledger, HitId id) {
        Objects.requireNonNull(ledger, "ledger");
        return ledger.add(Objects.requireNonNull(id, "id"));
    }

    /** Shortens a telegraph for the furious phase without ever reaching zero. */
    public static int telegraphFor(Attack attack, boolean furious) {
        if (!furious) return attack.telegraphTicks();
        return Math.max(1, (int) Math.round(attack.telegraphTicks() * FURIOUS_TELEGRAPH_FACTOR));
    }
}
