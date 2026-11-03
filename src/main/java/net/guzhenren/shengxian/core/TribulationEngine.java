package net.guzhenren.shengxian.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Domain scheduler for independently sourced, persistent calamity carriers. */
public final class TribulationEngine {
    private TribulationEngine() {}

    public enum EventType {
        STARTED_WILLOW, STARTED_TIGER, STARTED_SOUND, STARTED_BEAST, STARTED_BLOOD_RAIN,
        IMPACT, STRUCK, SURVIVED
    }

    public record Event(EventType type, String detail) {}

    public record Result(AscensionProgress progress, List<Event> events, boolean fatal,
                         long impactSequence,
                         Optional<AscensionProgress.TribulationImpactFailure> impactFailure) {
        public Result(AscensionProgress progress, List<Event> events, boolean fatal) {
            this(progress, events, fatal, progress.tribulationImpactSequence(), progress.tribulationImpactFailure());
        }
        public Result {
            events = List.copyOf(events);
            Objects.requireNonNull(impactFailure, "impact failure");
        }
    }

    /**
     * Starts carriers from their own qi sources. The Taibai witnessed profile may create both
     * carriers in the same step; it never chooses one by comparing heaven and earth qi.
     */
    public static Result startIfDue(AscensionProgress current, long serverTick, TribulationParameters p) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(p, "parameters");
        if (serverTick < 0) throw new IllegalArgumentException("Negative server tick");

        if (current.tribulationProfile() == TribulationParameters.Profile.NOT_FROZEN) {
            TribulationParameters.Profile selected = p.profile() == TribulationParameters.Profile.NOT_FROZEN
                    ? TribulationParameters.Profile.UNRESOLVED_ORDINARY : p.profile();
            current = current.withTribulationProfile(selected);
        }
        if (current.tribulationProfile() != p.profile()) {
            p = current.tribulationProfile() == TribulationParameters.Profile.LEGACY_TEST_FIXTURE
                    ? p : TribulationParameters.forProfile(current.tribulationProfile());
        }

        return switch (p.profile()) {
            case NOT_FROZEN -> new Result(current, List.of(), false);
            // No individual-case gate: the ambient-qi rule is the same engine for every
            // ascension; the witnessed examples are instances of it, not permissions.
            case UNRESOLVED_ORDINARY, TAI_BAI_WITNESSED -> startFromAmbientQi(current, serverTick, p);
            case BLACK_LOU_LAN_WITNESSED -> startHeiLoulan(current, serverTick, p);
            case FANG_ZHENG_WITNESSED -> startFangZheng(current, serverTick, p);
            case LEGACY_TEST_FIXTURE -> startLegacyFixture(current, serverTick, p);
        };
    }

    /**
     * The general rule: dense ambient heaven qi condenses a weather carrier, dense ambient earth
     * qi condenses an earth carrier, each consuming half the trigger threshold.
     *
     * <p>This is the mechanism CANON-012 witnessed for one ascension (柳风/峥虎). It is applied to
     * every ascension as a project decision: the text describes the mechanism, and the witnessed
     * carriers are the verified vocabulary, so no individual case is privileged. See
     * `docs/evidence/canon-rules.md` 项目决策.</p>
     */
    private static Result startFromAmbientQi(AscensionProgress current, long serverTick, TribulationParameters p) {
        double threshold = p.triggerAmbientTotal() / 2.0;
        double heaven = current.qi().ambientHeaven();
        double earth = current.qi().ambientEarth();
        List<AscensionProgress.Tribulation> active = new ArrayList<>(current.tribulations());
        List<Event> events = new ArrayList<>();
        boolean hasWillow = active.stream().anyMatch(t ->
                t.type() == AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND);
        boolean hasTiger = active.stream().anyMatch(t ->
                t.type() == AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER);

        if (!hasWillow && heaven >= threshold) {
            AscensionProgress.Tribulation carrier = new AscensionProgress.Tribulation(
                    "heaven-" + serverTick, AscensionProgress.Tribulation.Type.JADE_WILLOW_WIND,
                    p.heavenCarrierUnits(), p.heavenImpactInterval());
            active.add(carrier);
            heaven -= threshold;
            events.add(new Event(EventType.STARTED_WILLOW, carrier.id()));
        }
        if (!hasTiger && earth >= threshold) {
            AscensionProgress.Tribulation carrier = new AscensionProgress.Tribulation(
                    "earth-" + serverTick, AscensionProgress.Tribulation.Type.SINGLE_HORNED_TIGER,
                    p.earthCarrierUnits(), p.earthImpactInterval());
            active.add(carrier);
            earth -= threshold;
            events.add(new Event(EventType.STARTED_TIGER, carrier.id()));
        }
        if (events.isEmpty()) return new Result(current, List.of(), false);

        AscensionProgress.Qi qi = current.qi();
        AscensionProgress.Qi consumed = new AscensionProgress.Qi(qi.heaven(), qi.earth(), qi.human(),
                heaven, earth, qi.humanReserve());
        return new Result(current.withQi(consumed).withTribulations(active), events, false);
    }

    /** Hei Loulan's witnessed sequence: heavenly chimes first, earth beast after they are cleared. */
    private static Result startHeiLoulan(AscensionProgress current, long serverTick, TribulationParameters p) {
        if (!current.tribulations().isEmpty()) return new Result(current, List.of(), false);
        String completedChimePrefix = "tribulation:black-loulan-sound-";
        boolean chimesCompleted = current.appliedEffects().stream()
                .anyMatch(key -> key.startsWith(completedChimePrefix) && key.endsWith(":survived"));
        double threshold = p.triggerAmbientTotal() / 2.0;
        AscensionProgress.Qi qi = current.qi();
        AscensionProgress.Tribulation carrier;
        Event event;
        AscensionProgress.Qi consumed;
        if (!chimesCompleted) {
            if (qi.ambientHeaven() < threshold) return new Result(current, List.of(), false);
            carrier = new AscensionProgress.Tribulation("black-loulan-sound-" + serverTick,
                    AscensionProgress.Tribulation.Type.SOUND_CHIMES,
                    p.heavenCarrierUnits(), p.heavenImpactInterval());
            consumed = new AscensionProgress.Qi(qi.heaven(), qi.earth(), qi.human(),
                    qi.ambientHeaven() - threshold, qi.ambientEarth(), qi.humanReserve());
            event = new Event(EventType.STARTED_SOUND, carrier.id());
        } else {
            if (qi.ambientEarth() < threshold) return new Result(current, List.of(), false);
            carrier = new AscensionProgress.Tribulation("black-loulan-earth-" + serverTick,
                    AscensionProgress.Tribulation.Type.ANCIENT_BEAST,
                    p.earthCarrierUnits(), p.earthImpactInterval());
            consumed = new AscensionProgress.Qi(qi.heaven(), qi.earth(), qi.human(),
                    qi.ambientHeaven(), qi.ambientEarth() - threshold, qi.humanReserve());
            event = new Event(EventType.STARTED_BEAST, carrier.id());
        }
        return new Result(current.withQi(consumed).withTribulations(List.of(carrier)),
                List.of(event), false);
    }

    /**
     * Fang Zheng's witnessed blood-rain example (CANON-013). The text converts heaven and earth
     * qi together into blood qi and blood rain, so the carrier starts only while both ambient
     * sources are dense, and one survived blood rain is never restarted. This remains an
     * individual profile: the ordinary route still has no verified general catalogue (GAP-001).
     */
    private static Result startFangZheng(AscensionProgress current, long serverTick, TribulationParameters p) {
        if (!current.tribulations().isEmpty()) return new Result(current, List.of(), false);
        String survivedPrefix = "tribulation:fang-zheng-blood-";
        boolean alreadySurvived = current.appliedEffects().stream()
                .anyMatch(key -> key.startsWith(survivedPrefix) && key.endsWith(":survived"));
        if (alreadySurvived) return new Result(current, List.of(), false);
        double threshold = p.triggerAmbientTotal() / 2.0;
        AscensionProgress.Qi qi = current.qi();
        if (qi.ambientHeaven() < threshold || qi.ambientEarth() < threshold) {
            return new Result(current, List.of(), false);
        }
        AscensionProgress.Tribulation carrier = new AscensionProgress.Tribulation(
                "fang-zheng-blood-" + serverTick, AscensionProgress.Tribulation.Type.BLOOD_RAIN,
                p.earthCarrierUnits(), p.earthImpactInterval());
        AscensionProgress.Qi consumed = new AscensionProgress.Qi(qi.heaven(), qi.earth(), qi.human(),
                qi.ambientHeaven() - threshold, qi.ambientEarth() - threshold, qi.humanReserve());
        return new Result(current.withQi(consumed).withTribulations(List.of(carrier)),
                List.of(new Event(EventType.STARTED_BLOOD_RAIN, carrier.id())), false);
    }

    /** Historical selector is isolated from any production route and exists for old fixtures only. */
    private static Result startLegacyFixture(AscensionProgress current, long serverTick, TribulationParameters p) {
        if (!current.tribulations().isEmpty()) return new Result(current, List.of(), false);
        double ambient = current.qi().ambientHeaven() + current.qi().ambientEarth();
        if (ambient < p.triggerAmbientTotal()) return new Result(current, List.of(), false);
        boolean sound = current.qi().ambientHeaven() >= current.qi().ambientEarth();
        AscensionProgress.Tribulation carrier = sound
                ? new AscensionProgress.Tribulation("t" + serverTick,
                        AscensionProgress.Tribulation.Type.SOUND_CHIMES,
                        p.heavenCarrierUnits(), p.heavenImpactInterval())
                : new AscensionProgress.Tribulation("t" + serverTick,
                        AscensionProgress.Tribulation.Type.ANCIENT_BEAST,
                        p.earthCarrierUnits(), p.earthImpactInterval());
        Event event = new Event(sound ? EventType.STARTED_SOUND : EventType.STARTED_BEAST, carrier.id());
        return new Result(current.withTribulations(List.of(carrier)), List.of(event), false);
    }

    /** Advances every active named-profile carrier once; external pressure is separate from qi risk. */
    public static Result tick(AscensionProgress current, TribulationParameters p, double pressureLimit) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(p, "parameters");
        if (!Double.isFinite(pressureLimit) || pressureLimit <= 0) {
            throw new IllegalArgumentException("Impact pressure limit must be positive");
        }
        if (current.tribulations().isEmpty()) return new Result(current, List.of(), false);
        boolean witnessed = isWitnessedProfile(current.tribulationProfile());
        if (current.tribulationProfile() != p.profile()) {
            p = TribulationParameters.forProfile(current.tribulationProfile());
        }

        AscensionProgress progress = current;
        List<AscensionProgress.Tribulation> next = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        for (AscensionProgress.Tribulation carrier : current.tribulations()) {
            int until = carrier.ticksUntilImpact() - 1;
            if (until > 0) {
                next.add(copy(carrier, carrier.remaining(), until));
                continue;
            }

            events.add(new Event(EventType.IMPACT, carrier.id()));
            if (!witnessed) {
                next.add(copy(carrier, carrier.remaining(), impactInterval(carrier, p)));
                continue;
            }
            boolean fatal = progress.tribulationPressure() + p.impactRisk() >= pressureLimit;
            progress = progress.withTribulationImpact(carrier.id(), p.impactRisk(), fatal);
            if (fatal) {
                progress = progress.withTribulations(List.of());
                return new Result(progress, events, true, progress.tribulationImpactSequence(),
                        progress.tribulationImpactFailure());
            }
            next.add(copy(carrier, carrier.remaining(), impactInterval(carrier, p)));
        }
        return new Result(progress.withTribulations(next), events, false);
    }

    private static boolean isWitnessedProfile(TribulationParameters.Profile profile) {
        return profile == TribulationParameters.Profile.TAI_BAI_WITNESSED
                || profile == TribulationParameters.Profile.BLACK_LOU_LAN_WITNESSED
                || profile == TribulationParameters.Profile.FANG_ZHENG_WITNESSED;
    }

    /** Compatibility action targets the first carrier. Game interactions should pass its id. */
    public static Result strike(AscensionProgress current, int units) {
        Objects.requireNonNull(current, "current");
        if (units <= 0) throw new IllegalArgumentException("Strike units must be positive");
        if (current.tribulations().isEmpty()) return new Result(current, List.of(), false);
        return strike(current, current.tribulations().get(0).id(), units);
    }

    /** Guardian action removes only the identified carrier and never changes qi or balancing risk. */
    public static Result strike(AscensionProgress current, String carrierId, int units) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(carrierId, "carrierId");
        if (units <= 0) throw new IllegalArgumentException("Strike units must be positive");
        int index = -1;
        for (int i = 0; i < current.tribulations().size(); i++) {
            if (current.tribulations().get(i).id().equals(carrierId)) { index = i; break; }
        }
        if (index < 0) return new Result(current, List.of(), false);

        AscensionProgress.Tribulation target = current.tribulations().get(index);
        int remaining = Math.max(0, target.remaining() - units);
        List<AscensionProgress.Tribulation> next = new ArrayList<>(current.tribulations());
        List<Event> events = new ArrayList<>();
        if (remaining == 0) {
            next.remove(index);
            String key = "tribulation:" + target.id() + ":survived";
            AscensionProgress updated = current.withTribulations(next);
            if (!updated.appliedEffects().contains(key)) updated = updated.withAppliedEffect(key);
            events.add(new Event(EventType.SURVIVED, key));
            return new Result(updated, events, false);
        }
        next.set(index, copy(target, remaining, target.ticksUntilImpact()));
        events.add(new Event(EventType.STRUCK, target.id()));
        return new Result(current.withTribulations(next), events, false);
    }

    private static AscensionProgress.Tribulation copy(AscensionProgress.Tribulation carrier,
                                                       int remaining, int ticksUntilImpact) {
        return new AscensionProgress.Tribulation(carrier.id(), carrier.type(), remaining, ticksUntilImpact, carrier.carrierAnchor());
    }

    private static int impactInterval(AscensionProgress.Tribulation carrier, TribulationParameters p) {
        return switch (carrier.type()) {
            case JADE_WILLOW_WIND, SOUND_CHIMES -> p.heavenImpactInterval();
            case SINGLE_HORNED_TIGER, ANCIENT_BEAST, BLOOD_RAIN -> p.earthImpactInterval();
            case NONE -> throw new IllegalArgumentException("Inactive carrier cannot impact");
        };
    }

}
