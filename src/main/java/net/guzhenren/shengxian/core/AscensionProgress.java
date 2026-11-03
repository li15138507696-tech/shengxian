package net.guzhenren.shengxian.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persisted progress that can change while the phase stays the same.
 *
 * <p>This is a bounded container. The simulation rules live in {@link QiEngine} and
 * {@link TribulationEngine}; this type stores absorbed/ambient qi, imbalance risk, the
 * insight window, the active tribulation, the finite insight budget and the idempotency
 * ledger. Effect keys may be added once and are never silently removed.</p>
 */
public record AscensionProgress(Qi qi, Risk risk, Window window, List<Tribulation> tribulations, Aperture aperture,
                                VitalGu vitalGu, int insightEarned, int insightSpent,
                                Set<String> appliedEffects, String apertureDraftId,
                                TribulationParameters.Profile tribulationProfile, double tribulationPressure,
                                long tribulationImpactSequence,
                                Optional<TribulationImpactFailure> tribulationImpactFailure, XianGuRuntime xianGu) {
    public static final double MAX_QI = 1_000_000_000.0;
    public static final double MAX_RISK = 1_000_000_000.0;
    public static final int MAX_WINDOW_TICKS = 1_000_000;
    public static final int MAX_INSIGHT = 1_000_000;
    public static final int MAX_EFFECTS = 512;
    public static final int MAX_TRIBULATIONS = 16;
    public static final double MAX_TRIBULATION_PRESSURE = 1_000_000_000.0;
    public static final long MAX_TRIBULATION_IMPACT_SEQUENCE = 1_000_000_000L;
    public static final int MAX_EFFECT_KEY_LENGTH = 96;
    public static final String RANK_SIX_SYNC_RECOVERY_EFFECT = "rank-six-sync-recovery";
    public static final String RANK_SIX_SYNC_ACKNOWLEDGED_EFFECT = "rank-six-sync-acknowledged";
    public static final String RANK_SIX_SYNC_PROJECTION_V1_EFFECT = "rank-six-sync-projection-v1";
    public static final String FAILURE_ACKNOWLEDGED_EFFECT = "failure:consequence-acknowledged";
    public static final String FAILURE_PREPARED_EFFECT = "failure:settlement:prepared";
    public static final String FAILURE_STARTED_EFFECT = "failure:settlement:started";
    public static final String FAILURE_UNCERTAIN_EFFECT = "failure:settlement:uncertain";
    public static final String FAILURE_RESPAWN_RECEIPT_EFFECT = "failure:settlement:respawn-receipt";
    private static final Set<String> FAILURE_SETTLEMENT_EFFECTS = Set.of(
            FAILURE_PREPARED_EFFECT, FAILURE_STARTED_EFFECT, FAILURE_UNCERTAIN_EFFECT,
            FAILURE_RESPAWN_RECEIPT_EFFECT, FAILURE_ACKNOWLEDGED_EFFECT);
    public static final int MAX_APERTURE_DRAFT_LENGTH = 64;

    public enum FailureCause { HEAVEN_OVERLOAD, EARTH_OVERLOAD, HUMAN_OVERLOAD, TRIBULATION_IMPACT, UNCLASSIFIED }
    public enum FailureSettlementState { NONE, PREPARED, STARTED, RESPAWN_RECEIPT, UNCERTAIN, ACKNOWLEDGED }

    public AscensionProgress {
        Objects.requireNonNull(xianGu, "immortal Gu state");
        Objects.requireNonNull(qi, "qi");
        Objects.requireNonNull(risk, "risk");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(tribulations, "tribulations");
        Objects.requireNonNull(aperture, "aperture");
        Objects.requireNonNull(vitalGu, "vitalGu");
        Objects.requireNonNull(appliedEffects, "appliedEffects");
        Objects.requireNonNull(apertureDraftId, "apertureDraftId");
        Objects.requireNonNull(tribulationProfile, "tribulation profile");
        Objects.requireNonNull(tribulationImpactFailure, "tribulation impact failure");
        if (!Double.isFinite(tribulationPressure) || tribulationPressure < 0
                || tribulationPressure > MAX_TRIBULATION_PRESSURE) {
            throw new IllegalArgumentException("Invalid tribulation pressure");
        }
        if (tribulationImpactSequence < 0 || tribulationImpactSequence > MAX_TRIBULATION_IMPACT_SEQUENCE) {
            throw new IllegalArgumentException("Invalid tribulation impact sequence");
        }
        if (tribulationProfile == TribulationParameters.Profile.NOT_FROZEN
                && (tribulationPressure != 0 || tribulationImpactSequence != 0 || tribulationImpactFailure.isPresent())) {
            throw new IllegalArgumentException("Unfrozen profile cannot have tribulation impacts");
        }
        tribulationImpactFailure.ifPresent(failure -> {
            if (failure.profile() != tribulationProfile || failure.impactSequence() != tribulationImpactSequence) {
                throw new IllegalArgumentException("Tribulation impact context does not match progress");
            }
        });
        if (insightEarned < 0 || insightSpent < 0 || insightSpent > insightEarned
                || insightEarned > MAX_INSIGHT) {
            throw new IllegalArgumentException("Invalid insight budget");
        }
        if (appliedEffects.size() > MAX_EFFECTS) {
            throw new IllegalArgumentException("Too many applied effects");
        }
        for (String key : appliedEffects) {
            validateEffectKey(key);
        }
        if (apertureDraftId.length() > MAX_APERTURE_DRAFT_LENGTH) {
            throw new IllegalArgumentException("Aperture draft id too long");
        }
        if (tribulations.size() > MAX_TRIBULATIONS) throw new IllegalArgumentException("Too many active tribulations");
        Set<String> tribulationIds = new LinkedHashSet<>();
        for (Tribulation active : tribulations) {
            Objects.requireNonNull(active, "active tribulation");
            if (!active.active() || !tribulationIds.add(active.id())) throw new IllegalArgumentException("Invalid active tribulation list");
        }
        tribulations = List.copyOf(tribulations);
        appliedEffects = Collections.unmodifiableSet(new LinkedHashSet<>(appliedEffects));
    }

    /** Compatibility constructor for records before schema 11. */
    public AscensionProgress(Qi qi, Risk risk, Window window, List<Tribulation> tribulations, Aperture aperture,
                             VitalGu vitalGu, int insightEarned, int insightSpent, Set<String> appliedEffects,
                             String apertureDraftId, TribulationParameters.Profile tribulationProfile,
                             double tribulationPressure, long tribulationImpactSequence,
                             Optional<TribulationImpactFailure> tribulationImpactFailure) {
        this(qi, risk, window, tribulations, aperture, vitalGu, insightEarned, insightSpent, appliedEffects,
                apertureDraftId, tribulationProfile, tribulationPressure, tribulationImpactSequence,
                tribulationImpactFailure, XianGuRuntime.empty());
    }

    public AscensionProgress withXianGu(XianGuRuntime runtime) {
        return new AscensionProgress(qi, risk, window, tribulations, aperture, vitalGu, insightEarned,
                insightSpent, appliedEffects, apertureDraftId, tribulationProfile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, runtime);
    }

    /** Compatibility constructor for schema versions and call sites predating external impact state. */
    public AscensionProgress(Qi qi, Risk risk, Window window, List<Tribulation> tribulations, Aperture aperture,
                             VitalGu vitalGu, int insightEarned, int insightSpent,
                             Set<String> appliedEffects, String apertureDraftId) {
        this(qi, risk, window, tribulations, aperture, vitalGu, insightEarned, insightSpent,
                appliedEffects, apertureDraftId, TribulationParameters.Profile.UNRESOLVED_ORDINARY,
                0, 0, Optional.empty());
    }

    public AscensionProgress(Qi qi, Risk risk, Window window, Tribulation tribulation, Aperture aperture,
                             VitalGu vitalGu, int insightEarned, int insightSpent,
                             Set<String> appliedEffects, String apertureDraftId) {
        this(qi, risk, window, tribulation != null && tribulation.active() ? List.of(tribulation) : List.of(),
                aperture, vitalGu, insightEarned, insightSpent, appliedEffects, apertureDraftId);
    }

    public static AscensionProgress empty() {
        return empty(0.0);
    }

    /** New session with a bounded, server-derived finite human-qi reserve. */
    public static AscensionProgress empty(double humanQiReserve) {
        Qi qi = new Qi(0, 0, 0, 0, 0, humanQiReserve);
        return new AscensionProgress(qi, Risk.empty(), Window.empty(), List.of(),
                Aperture.empty(), VitalGu.empty(), 0, 0, Set.of(), "",
                TribulationParameters.Profile.NOT_FROZEN, 0, 0, Optional.empty());
    }

    /** Freezes the server-selected profile once; changing a committed choice is rejected. */
    public AscensionProgress withTribulationProfile(TribulationParameters.Profile profile) {
        Objects.requireNonNull(profile, "tribulation profile");
        if (profile == TribulationParameters.Profile.NOT_FROZEN) {
            throw new IllegalArgumentException("Profile cannot be selected for a production session");
        }
        if (tribulationProfile != TribulationParameters.Profile.NOT_FROZEN) {
            if (tribulationProfile == profile) return this;
            throw new IllegalStateException("Tribulation profile is already frozen");
        }
        return new AscensionProgress(qi, risk, window, tribulations, aperture, vitalGu, insightEarned,
                insightSpent, appliedEffects, apertureDraftId, profile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    /**
     * Records one identified carrier impact in a ledger separate from the three qi risks.
     * When terminal, the returned context binds the profile, carrier and monotonic sequence.
     */
    public AscensionProgress withTribulationImpact(String carrierId, double pressureIncrease, boolean terminal) {
        Objects.requireNonNull(carrierId, "carrier id");
        if (!isWitnessedProfile(tribulationProfile)) {
            throw new IllegalStateException("Only a frozen witnessed profile can record impacts");
        }
        if (!Double.isFinite(pressureIncrease) || pressureIncrease <= 0) {
            throw new IllegalArgumentException("Tribulation pressure increase must be positive");
        }
        if (tribulationImpactFailure.isPresent()) {
            throw new IllegalStateException("Terminal tribulation impact is already recorded");
        }
        Tribulation carrier = tribulations.stream().filter(value -> value.id().equals(carrierId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Active carrier is missing"));
        if (tribulationImpactSequence >= MAX_TRIBULATION_IMPACT_SEQUENCE) {
            throw new IllegalStateException("Tribulation impact sequence is exhausted");
        }
        double nextPressure = tribulationPressure + pressureIncrease;
        if (!Double.isFinite(nextPressure) || nextPressure > MAX_TRIBULATION_PRESSURE) {
            throw new IllegalArgumentException("Tribulation pressure exceeds its bound");
        }
        long nextSequence = tribulationImpactSequence + 1;
        Optional<TribulationImpactFailure> failure = terminal
                ? Optional.of(new TribulationImpactFailure(tribulationProfile, carrier.type(), carrier.id(), nextSequence))
                : Optional.empty();
        return new AscensionProgress(qi, risk, window, tribulations, aperture, vitalGu, insightEarned,
                insightSpent, appliedEffects, apertureDraftId, tribulationProfile, nextPressure,
                nextSequence, failure, xianGu);
    }

    private static boolean isWitnessedProfile(TribulationParameters.Profile profile) {
        return profile == TribulationParameters.Profile.TAI_BAI_WITNESSED
                || profile == TribulationParameters.Profile.BLACK_LOU_LAN_WITNESSED
                || profile == TribulationParameters.Profile.FANG_ZHENG_WITNESSED;
    }

    private static final String SHATTER_ESSENCE_TARGET_PREFIX = "shatter-zhenyuan-target:";
    private static final String SHATTER_ESSENCE_INTENT_PREFIX = "shatter-zhenyuan-intent:";
    private static final String SHATTER_CLOCK_PREFIX = "shatter-clock:";

    /** Immutable world-side evidence for the first true-essence debit. */
    public record ShatterEssenceIntent(long phaseRevision, double before, double target) {
        public ShatterEssenceIntent {
            if (before == 0.0) before = 0.0;
            if (target == 0.0) target = 0.0;
            if (phaseRevision <= 0 || !Double.isFinite(before) || before < 0 || before > MAX_QI
                    || !Double.isFinite(target) || target < 0 || target > MAX_QI || target >= before) {
                throw new IllegalArgumentException("Invalid shatter essence intent");
            }
        }
    }

    /** Replaces the previous attempt target when PREPARING advances to BREAKING. */
    public AscensionProgress withShatterEssenceTarget(long phaseRevision, double target) {
        if (phaseRevision <= 0 || !Double.isFinite(target) || target < 0 || target > MAX_QI) {
            throw new IllegalArgumentException("Invalid shatter attempt or essence target");
        }
        Set<String> next = new LinkedHashSet<>(appliedEffects);
        next.removeIf(AscensionProgress::isShatterEssenceKey);
        next.add(SHATTER_ESSENCE_TARGET_PREFIX + phaseRevision + ":" + Double.toHexString(target));
        return new AscensionProgress(qi, risk, window, tribulations, aperture, vitalGu,
                insightEarned, insightSpent, next, apertureDraftId, tribulationProfile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    /** Stores both sides of the one-time debit before any player data is changed. */
    public AscensionProgress withShatterEssenceIntent(long phaseRevision, double before, double target) {
        ShatterEssenceIntent intent = new ShatterEssenceIntent(phaseRevision, before, target);
        Set<String> next = new LinkedHashSet<>(appliedEffects);
        next.removeIf(AscensionProgress::isShatterEssenceKey);
        next.add(encodeShatterEssenceIntent(intent));
        return new AscensionProgress(qi, risk, window, tribulations, aperture, vitalGu,
                insightEarned, insightSpent, next, apertureDraftId, tribulationProfile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    private static boolean isShatterEssenceKey(String key) {
        return key.startsWith(SHATTER_ESSENCE_TARGET_PREFIX) || key.startsWith(SHATTER_ESSENCE_INTENT_PREFIX);
    }

    private static String encodeShatterEssenceIntent(ShatterEssenceIntent intent) {
        return SHATTER_ESSENCE_INTENT_PREFIX + intent.phaseRevision() + ":"
                + Double.toHexString(intent.before()) + ":" + Double.toHexString(intent.target());
    }

    /** New intents include the debit baseline; legacy target-only entries deliberately return empty. */
    public java.util.Optional<ShatterEssenceIntent> shatterEssenceIntent(long phaseRevision) {
        if (phaseRevision <= 0) throw new IllegalArgumentException("Invalid shatter phase revision");
        Set<String> keys = new LinkedHashSet<>();
        for (String key : appliedEffects) {
            if (key.startsWith(SHATTER_ESSENCE_INTENT_PREFIX)) keys.add(key);
        }
        if (keys.isEmpty()) return java.util.Optional.empty();
        if (keys.size() != 1) throw new IllegalStateException("Ambiguous shatter essence intents");
        ShatterEssenceIntent intent = parseShatterEssenceIntent(keys.iterator().next());
        return intent.phaseRevision() == phaseRevision ? java.util.Optional.of(intent) : java.util.Optional.empty();
    }

    private static ShatterEssenceIntent parseShatterEssenceIntent(String key) {
        String[] parts = key.substring(SHATTER_ESSENCE_INTENT_PREFIX.length()).split(":", -1);
        if (parts.length != 3) throw new IllegalStateException("Malformed shatter essence intent");
        try {
            return new ShatterEssenceIntent(Long.parseLong(parts[0]),
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid shatter essence intent", ex);
        }
    }

    /** Persists the online-only timer for one irreversible shatter attempt. */
    public AscensionProgress withShatterClock(ShatterClock.State clock) {
        Objects.requireNonNull(clock, "shatter clock");
        ShatterClock.State previous = shatterClock(clock.phaseRevision()).orElse(null);
        if (previous != null && (clock.elapsedTicks() < previous.elapsedTicks()
                || clock.lastServerTick() < previous.lastServerTick())) {
            throw new IllegalStateException("Shatter clock cannot move backwards within an attempt");
        }
        for (String key : shatterClockKeys()) {
            long revision = parseShatterClock(key).phaseRevision();
            if (revision > clock.phaseRevision()) {
                throw new IllegalStateException("Shatter clock revision cannot move backwards");
            }
        }
        Set<String> next = new LinkedHashSet<>(appliedEffects);
        next.removeIf(key -> key.startsWith(SHATTER_CLOCK_PREFIX));
        next.add(encodeShatterClock(clock));
        return new AscensionProgress(qi, risk, window, tribulations, aperture, vitalGu,
                insightEarned, insightSpent, next, apertureDraftId, tribulationProfile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    /** Returns the current timer only when it belongs to the requested BREAKING phase revision. */
    public java.util.Optional<ShatterClock.State> shatterClock(long phaseRevision) {
        if (phaseRevision <= 0) throw new IllegalArgumentException("Invalid shatter phase revision");
        java.util.Optional<ShatterClock.State> current = shatterClock();
        if (current.isEmpty()) return current;
        ShatterClock.State clock = current.get();
        return clock.phaseRevision() == phaseRevision ? java.util.Optional.of(clock) : java.util.Optional.empty();
    }

    public java.util.Optional<ShatterClock.State> shatterClock() {
        Set<String> keys = shatterClockKeys();
        if (keys.isEmpty()) return java.util.Optional.empty();
        if (keys.size() != 1) throw new IllegalStateException("Ambiguous shatter clock records");
        return java.util.Optional.of(parseShatterClock(keys.iterator().next()));
    }

    public Set<String> shatterClockKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (String key : appliedEffects) {
            if (key.startsWith(SHATTER_CLOCK_PREFIX)) keys.add(key);
        }
        return Set.copyOf(keys);
    }

    private static String encodeShatterClock(ShatterClock.State clock) {
        return SHATTER_CLOCK_PREFIX + clock.phaseRevision() + ":" + clock.lastServerTick()
                + ":" + clock.elapsedTicks();
    }

    private static ShatterClock.State parseShatterClock(String key) {
        String[] parts = key.substring(SHATTER_CLOCK_PREFIX.length()).split(":", -1);
        if (parts.length != 3) throw new IllegalStateException("Malformed shatter clock record");
        try {
            return new ShatterClock.State(Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid shatter clock record", ex);
        }
    }

    public Set<String> shatterEssenceTargetKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (String key : appliedEffects) {
            if (isShatterEssenceKey(key)) keys.add(key);
        }
        return Set.copyOf(keys);
    }

    /** Reads the sole current target; multiple legacy targets are ambiguous and rejected. */
    public java.util.OptionalDouble shatterEssenceTarget() {
        return readShatterEssenceTarget(-1);
    }

    /** Reads a target for this BREAKING revision; one old unversioned target remains readable. */
    public java.util.OptionalDouble shatterEssenceTarget(long phaseRevision) {
        if (phaseRevision <= 0) throw new IllegalArgumentException("Invalid shatter phase revision");
        return readShatterEssenceTarget(phaseRevision);
    }

    public boolean hasVersionedShatterEssenceTarget(long phaseRevision) {
        Set<String> keys = shatterEssenceTargetKeys();
        return keys.size() == 1
                && (keys.iterator().next().startsWith(SHATTER_ESSENCE_TARGET_PREFIX + phaseRevision + ":")
                    || keys.iterator().next().startsWith(SHATTER_ESSENCE_INTENT_PREFIX + phaseRevision + ":"))
                && shatterEssenceTarget(phaseRevision).isPresent();
    }

    private java.util.OptionalDouble readShatterEssenceTarget(long expectedRevision) {
        Set<String> keys = shatterEssenceTargetKeys();
        if (keys.isEmpty()) return java.util.OptionalDouble.empty();
        if (keys.size() != 1) throw new IllegalStateException("Ambiguous shatter essence targets");
        String key = keys.iterator().next();
        if (key.startsWith(SHATTER_ESSENCE_INTENT_PREFIX)) {
            ShatterEssenceIntent intent = parseShatterEssenceIntent(key);
            if (expectedRevision >= 0 && intent.phaseRevision() != expectedRevision) {
                return java.util.OptionalDouble.empty();
            }
            return java.util.OptionalDouble.of(intent.target());
        }
        String value = key.substring(SHATTER_ESSENCE_TARGET_PREFIX.length());
        int separator = value.indexOf(':');
        if (separator >= 0) {
            long revision;
            try {
                revision = Long.parseLong(value.substring(0, separator));
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("Corrupt shatter phase revision", ex);
            }
            if (revision <= 0) throw new IllegalStateException("Invalid shatter phase revision");
            if (expectedRevision >= 0 && revision != expectedRevision) return java.util.OptionalDouble.empty();
            value = value.substring(separator + 1);
        }
        try {
            double target = Double.parseDouble(value);
            if (!Double.isFinite(target) || target < 0 || target > MAX_QI) {
                throw new IllegalStateException("Invalid shatter essence target");
            }
            return java.util.OptionalDouble.of(target);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Corrupt shatter essence target", ex);
        }
    }

    /** Returns a progress with one new idempotency key; duplicate keys are rejected. */
    public AscensionProgress withAppliedEffect(String key) {
        validateEffectKey(key);
        if (appliedEffects.contains(key)) {
            throw new IllegalStateException("Effect already applied: " + key);
        }
        if (appliedEffects.size() >= MAX_EFFECTS) {
            throw new IllegalStateException("Effect ledger is full");
        }
        Set<String> next = new LinkedHashSet<>(appliedEffects);
        next.add(key);
        return new AscensionProgress(qi, risk, window, tribulations, aperture, vitalGu, insightEarned, insightSpent,
                next, apertureDraftId, tribulationProfile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    public boolean rankSixSyncRecoveryAvailable() {
        return appliedEffects.contains(RANK_SIX_SYNC_RECOVERY_EFFECT)
                && !appliedEffects.contains(RANK_SIX_SYNC_ACKNOWLEDGED_EFFECT);
    }

    /** True when the pending intent was committed by the receipt-aware recovery protocol. */
    public boolean rankSixSyncProjectionV1() {
        return appliedEffects.contains(RANK_SIX_SYNC_PROJECTION_V1_EFFECT);
    }
    public boolean rankSixSyncAcknowledged() {
        return appliedEffects.contains(RANK_SIX_SYNC_ACKNOWLEDGED_EFFECT);
    }

    /** Appends a durable acknowledgement without deleting the original intent marker. */
    public AscensionProgress withRankSixSyncAcknowledged() {
        if (!appliedEffects.contains(RANK_SIX_SYNC_RECOVERY_EFFECT)) {
            throw new IllegalStateException("Rank-six sync intent is missing");
        }
        if (rankSixSyncAcknowledged()) return this;
        return withAppliedEffect(RANK_SIX_SYNC_ACKNOWLEDGED_EFFECT);
    }

    /** Records why the ascension failed before any world/player consequence is applied. */
    public AscensionProgress withFailurePending(Set<FailureCause> causes) {
        Objects.requireNonNull(causes, "failure causes");
        if (causes.isEmpty() || failureConsequenceAvailable() || appliedEffects.contains(FAILURE_ACKNOWLEDGED_EFFECT)) {
            throw new IllegalStateException("Failure consequence is already recorded or no cause was supplied");
        }
        boolean impact = causes.contains(FailureCause.TRIBULATION_IMPACT);
        if ((impact && !causes.equals(Set.of(FailureCause.TRIBULATION_IMPACT)))
                || (!impact && tribulationImpactFailure.isPresent())
                || (impact && isWitnessedProfile(tribulationProfile) && tribulationImpactFailure.isEmpty())) {
            throw new IllegalArgumentException("Tribulation impact requires its sole, witnessed failure context");
        }
        AscensionProgress next = this;
        for (FailureCause cause : causes) {
            next = next.withAppliedEffect(failureEffect(cause));
        }
        return next.withAppliedEffect(FAILURE_PREPARED_EFFECT);
    }

    public Set<FailureCause> failureCauses() {
        Set<FailureCause> causes = new LinkedHashSet<>();
        for (FailureCause cause : FailureCause.values()) {
            if (appliedEffects.contains(failureEffect(cause))) causes.add(cause);
        }
        return Set.copyOf(causes);
    }

    public boolean failureConsequenceAvailable() {
        return !failureCauses().isEmpty() && !appliedEffects.contains(FAILURE_ACKNOWLEDGED_EFFECT);
    }

    /** Old markerless pending failures are quarantined instead of being replayed after upgrade. */
    public FailureSettlementState failureSettlementState() {
        if (failureCauses().isEmpty()) return FailureSettlementState.NONE;
        if (appliedEffects.contains(FAILURE_ACKNOWLEDGED_EFFECT)) return FailureSettlementState.ACKNOWLEDGED;
        if (appliedEffects.contains(FAILURE_RESPAWN_RECEIPT_EFFECT)) return FailureSettlementState.RESPAWN_RECEIPT;
        if (appliedEffects.contains(FAILURE_UNCERTAIN_EFFECT)) return FailureSettlementState.UNCERTAIN;
        if (appliedEffects.contains(FAILURE_STARTED_EFFECT)) return FailureSettlementState.STARTED;
        if (appliedEffects.contains(FAILURE_PREPARED_EFFECT)) return FailureSettlementState.PREPARED;
        return FailureSettlementState.UNCERTAIN;
    }

    public AscensionProgress withFailureStarted() {
        if (failureSettlementState() != FailureSettlementState.PREPARED) {
            throw new IllegalStateException("Only PREPARED failure can start settlement");
        }
        return withAppliedEffect(FAILURE_STARTED_EFFECT);
    }

    public AscensionProgress withFailureUncertain() {
        FailureSettlementState state = failureSettlementState();
        if (state == FailureSettlementState.UNCERTAIN) return this;
        if (state != FailureSettlementState.PREPARED && state != FailureSettlementState.STARTED) {
            throw new IllegalStateException("Only unresolved failure can become UNCERTAIN");
        }
        return withAppliedEffect(FAILURE_UNCERTAIN_EFFECT);
    }

    /** Called only after the matching PlayerPersisted receipt was saved and verified. */
    public AscensionProgress withFailureRespawnReceipt() {
        FailureSettlementState state = failureSettlementState();
        if (state == FailureSettlementState.RESPAWN_RECEIPT || state == FailureSettlementState.ACKNOWLEDGED) {
            return this;
        }
        if (state != FailureSettlementState.STARTED && state != FailureSettlementState.UNCERTAIN) {
            throw new IllegalStateException("Only an attempted failure can receive a verified respawn receipt");
        }
        return withAppliedEffect(FAILURE_RESPAWN_RECEIPT_EFFECT);
    }

    public AscensionProgress withFailureAcknowledged() {
        if (failureCauses().isEmpty()) throw new IllegalStateException("Failure cause is missing");
        if (appliedEffects.contains(FAILURE_ACKNOWLEDGED_EFFECT)) return this;
        if (failureSettlementState() != FailureSettlementState.RESPAWN_RECEIPT) {
            throw new IllegalStateException("Verified player respawn receipt is required before acknowledgement");
        }
        return withAppliedEffect(FAILURE_ACKNOWLEDGED_EFFECT);
    }

    private static String failureEffect(FailureCause cause) {
        return "failure:cause:" + Objects.requireNonNull(cause, "failure cause").name().toLowerCase(java.util.Locale.ROOT);
    }

    public AscensionProgress withQi(Qi nextQi) {
        return new AscensionProgress(nextQi, risk, window, tribulations, aperture, vitalGu, insightEarned, insightSpent,
                appliedEffects, apertureDraftId, tribulationProfile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    public AscensionProgress withRisk(Risk nextRisk) {
        return new AscensionProgress(qi, nextRisk, window, tribulations, aperture, vitalGu, insightEarned, insightSpent,
                appliedEffects, apertureDraftId, tribulationProfile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    public AscensionProgress withWindow(Window nextWindow) {
        return new AscensionProgress(qi, risk, nextWindow, tribulations, aperture, vitalGu, insightEarned, insightSpent,
                appliedEffects, apertureDraftId, tribulationProfile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    public Tribulation tribulation() {
        return tribulations.isEmpty() ? Tribulation.empty() : tribulations.get(0);
    }

    /** Compatibility update for old single-carrier callers. */
    public AscensionProgress withTribulation(Tribulation nextTribulation) {
        Objects.requireNonNull(nextTribulation, "next tribulation");
        return withTribulations(nextTribulation.active() ? List.of(nextTribulation) : List.of());
    }

    public AscensionProgress withTribulations(List<Tribulation> nextTribulations) {
        return new AscensionProgress(qi, risk, window, nextTribulations, aperture, vitalGu,
                insightEarned, insightSpent, appliedEffects, apertureDraftId, tribulationProfile,
                tribulationPressure, tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    /** Adds a world anchor once, then allows only its deterministic entity identity to be confirmed. */
    public AscensionProgress withCarrierAnchor(String carrierId, CarrierAnchor anchor) {
        Objects.requireNonNull(carrierId, "carrier id");
        Objects.requireNonNull(anchor, "carrier anchor");
        List<Tribulation> next = new java.util.ArrayList<>(tribulations);
        for (int i = 0; i < next.size(); i++) {
            Tribulation current = next.get(i);
            if (!current.id().equals(carrierId)) continue;
            CarrierAnchor previous = current.carrierAnchor();
            if (previous.placed() && !previous.sameLocation(anchor)) {
                throw new IllegalStateException("Carrier location cannot change after placement");
            }
            if (previous.entityId() != null && !Objects.equals(previous.entityId(), anchor.entityId())) {
                throw new IllegalStateException("Carrier entity identity cannot change");
            }
            next.set(i, current.withCarrierAnchor(anchor));
            return withTribulations(next);
        }
        return this;
    }
    /** True when all progress except the aperture ledger matches another snapshot. */
    public boolean sameNonApertureState(AscensionProgress other) {
        return other != null
                && xianGu.equals(other.xianGu)
                && qi.equals(other.qi)
                && risk.equals(other.risk)
                && window.equals(other.window)
                && tribulations.equals(other.tribulations)
                && tribulationProfile == other.tribulationProfile
                && tribulationPressure == other.tribulationPressure
                && tribulationImpactSequence == other.tribulationImpactSequence
                && tribulationImpactFailure.equals(other.tribulationImpactFailure)
                && vitalGu.equals(other.vitalGu)
                && insightEarned == other.insightEarned
                && insightSpent == other.insightSpent
                && apertureDraftId.equals(other.apertureDraftId)
                && (appliedEffects.equals(other.appliedEffects)
                || sameEffectsExceptRankSixAcknowledgement(other));
    }

    private boolean sameEffectsExceptRankSixAcknowledgement(AscensionProgress other) {
        Set<String> left = new LinkedHashSet<>(appliedEffects);
        Set<String> right = new LinkedHashSet<>(other.appliedEffects);
        left.remove(RANK_SIX_SYNC_ACKNOWLEDGED_EFFECT);
        right.remove(RANK_SIX_SYNC_ACKNOWLEDGED_EFFECT);
        return left.equals(right);
    }

    /** Exact progress equality except the one terminal failure consequence acknowledgement. */
    public boolean sameExceptFailureAcknowledgement(AscensionProgress other) {
        if (other == null || !xianGu.equals(other.xianGu) || !qi.equals(other.qi) || !risk.equals(other.risk)
                || !window.equals(other.window) || !tribulations.equals(other.tribulations)
                || !aperture.equals(other.aperture) || !vitalGu.equals(other.vitalGu)
                || tribulationProfile != other.tribulationProfile
                || tribulationPressure != other.tribulationPressure
                || tribulationImpactSequence != other.tribulationImpactSequence
                || !tribulationImpactFailure.equals(other.tribulationImpactFailure)
                || insightEarned != other.insightEarned || insightSpent != other.insightSpent
                || !apertureDraftId.equals(other.apertureDraftId)) return false;
        Set<String> left = new LinkedHashSet<>(appliedEffects);
        Set<String> right = new LinkedHashSet<>(other.appliedEffects);
        left.remove(FAILURE_ACKNOWLEDGED_EFFECT);
        right.remove(FAILURE_ACKNOWLEDGED_EFFECT);
        return left.equals(right);
    }

    /** Exact equality apart from a settlement-state marker; cause and gameplay data stay fixed. */
    public boolean sameExceptFailureSettlement(AscensionProgress other) {
        if (other == null || !failureCauses().equals(other.failureCauses())
                || !qi.equals(other.qi) || !risk.equals(other.risk)
                || !window.equals(other.window) || !tribulations.equals(other.tribulations)
                || !aperture.equals(other.aperture) || !vitalGu.equals(other.vitalGu)
                || tribulationProfile != other.tribulationProfile
                || tribulationPressure != other.tribulationPressure
                || tribulationImpactSequence != other.tribulationImpactSequence
                || !tribulationImpactFailure.equals(other.tribulationImpactFailure)
                || insightEarned != other.insightEarned || insightSpent != other.insightSpent
                || !apertureDraftId.equals(other.apertureDraftId)) return false;
        Set<String> left = new LinkedHashSet<>(appliedEffects);
        Set<String> right = new LinkedHashSet<>(other.appliedEffects);
        left.removeAll(FAILURE_SETTLEMENT_EFFECTS);
        right.removeAll(FAILURE_SETTLEMENT_EFFECTS);
        return left.equals(right);
    }

    public AscensionProgress withAperture(Aperture nextAperture) {
        return new AscensionProgress(qi, risk, window, tribulations, nextAperture, vitalGu, insightEarned, insightSpent,
                appliedEffects, apertureDraftId, tribulationProfile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    public AscensionProgress withInsight(int earned, int spent) {
        return new AscensionProgress(qi, risk, window, tribulations, aperture, vitalGu, earned, spent,
                appliedEffects, apertureDraftId, tribulationProfile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    public AscensionProgress withApertureDraft(String draftId) {
        return new AscensionProgress(qi, risk, window, tribulations, aperture, vitalGu, insightEarned, insightSpent,
                appliedEffects, draftId, tribulationProfile, tribulationPressure,
                tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    /**
     * Verifies that an update kept every previously applied key and did not lower the earned
     * or spent insight. Qi/risk/window/tribulation may move in both directions because the
     * simulation is continuous; only the reward ledger is monotonic.
     */
    public void requireMonotonicFrom(AscensionProgress previous) {
        xianGu.validateFrom(previous.xianGu, previous.aperture.essence(), aperture.essence());
        if (previous.tribulationProfile != TribulationParameters.Profile.NOT_FROZEN
                && tribulationProfile != previous.tribulationProfile) {
            throw new IllegalStateException("Frozen tribulation profile cannot change");
        }
        if (tribulationPressure < previous.tribulationPressure
                || tribulationImpactSequence < previous.tribulationImpactSequence) {
            throw new IllegalStateException("Tribulation impact ledger cannot move backwards");
        }
        boolean pressureChanged = tribulationPressure != previous.tribulationPressure;
        boolean sequenceChanged = tribulationImpactSequence != previous.tribulationImpactSequence;
        if (pressureChanged != sequenceChanged
                || (sequenceChanged && tribulationPressure <= previous.tribulationPressure)) {
            throw new IllegalStateException("Tribulation pressure and sequence must advance together");
        }
        if (previous.tribulationImpactFailure.isPresent()
                && !tribulationImpactFailure.equals(previous.tribulationImpactFailure)) {
            throw new IllegalStateException("Terminal impact provenance cannot change");
        }
        if (tribulationImpactFailure.isPresent() && !previous.tribulationImpactFailure.isPresent()
                && !sequenceChanged) {
            throw new IllegalStateException("Terminal impact provenance requires a new impact sequence");
        }
        for (String key : previous.appliedEffects) {
            if (!key.startsWith(SHATTER_ESSENCE_TARGET_PREFIX)
                    && !key.startsWith(SHATTER_CLOCK_PREFIX) && !appliedEffects.contains(key)) {
                throw new IllegalStateException("Applied-effect ledger cannot shrink");
            }
        }
        if (previous.vitalGu.bound() && !vitalGu.equals(previous.vitalGu)) {
            throw new IllegalStateException("BenMingGu identity cannot change");
        }
        if (insightEarned < previous.insightEarned || insightSpent < previous.insightSpent) {
            throw new IllegalStateException("Insight budget cannot decrease");
        }
    }

    private static void validateEffectKey(String key) {
        Objects.requireNonNull(key, "effect key");
        if (key.isBlank() || key.length() > MAX_EFFECT_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid effect key");
        }
        for (int i = 0; i < key.length(); i++) {
            if (Character.isISOControl(key.charAt(i))) {
                throw new IllegalArgumentException("Control character in effect key");
            }
        }
    }

    /** Absorbed and ambient three-qi containers. All values are finite and non-negative. */
    public record Qi(double heaven, double earth, double human,
                     double ambientHeaven, double ambientEarth, double humanReserve) {
        public Qi {
            requireQi(heaven, "heaven");
            requireQi(earth, "earth");
            requireQi(human, "human");
            requireQi(ambientHeaven, "ambientHeaven");
            requireQi(ambientEarth, "ambientEarth");
            requireQi(humanReserve, "humanReserve");
        }

        public static Qi empty() {
            return new Qi(0, 0, 0, 0, 0, 0);
        }

        private static void requireQi(double value, String name) {
            if (!Double.isFinite(value) || value < 0 || value > MAX_QI) {
                throw new IllegalArgumentException("Invalid qi value: " + name);
            }
        }
    }

    /** Cumulative imbalance exposure for each direction; fatal at the rule limit. */
    public record Risk(double heaven, double earth, double human) {
        public Risk {
            requireRisk(heaven, "heaven");
            requireRisk(earth, "earth");
            requireRisk(human, "human");
        }

        public static Risk empty() {
            return new Risk(0, 0, 0);
        }

        public double maximum() {
            return Math.max(heaven, Math.max(earth, human));
        }

        public Risk plus(double amount) {
            return new Risk(heaven + amount, earth + amount, human + amount);
        }

        private static void requireRisk(double value, String name) {
            if (!Double.isFinite(value) || value < 0 || value > MAX_RISK) {
                throw new IllegalArgumentException("Invalid risk value: " + name);
            }
        }
    }

    /**
     * Active 交感 window. An empty window has {@code ticks=0} and an empty id; an open window
     * has {@code ticks>=1} and a non-empty id. The id makes insight rewards idempotent.
     */
    public record Window(int ticks, String id) {
        public Window {
            Objects.requireNonNull(id, "window id");
            if (ticks < 0 || ticks > MAX_WINDOW_TICKS) {
                throw new IllegalArgumentException("Invalid window ticks");
            }
            if (id.length() > MAX_EFFECT_KEY_LENGTH || id.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid window id");
            }
            if ((ticks == 0) != id.isEmpty()) {
                throw new IllegalArgumentException("Window ticks and id disagree");
            }
        }

        public static Window empty() {
            return new Window(0, "");
        }

        public Window advance(int cap) {
            return new Window(Math.min(cap, ticks + 1), id);
        }
    }

    /** Provenance for the terminal witnessed carrier impact that caused an ascension failure. */
    public record TribulationImpactFailure(TribulationParameters.Profile profile,
                                           Tribulation.Type carrierType,
                                           String carrierId,
                                           long impactSequence) {
        public TribulationImpactFailure {
            Objects.requireNonNull(profile, "impact profile");
            Objects.requireNonNull(carrierType, "impact carrier type");
            Objects.requireNonNull(carrierId, "impact carrier id");
            boolean validPair = switch (profile) {
                case TAI_BAI_WITNESSED -> carrierType == Tribulation.Type.JADE_WILLOW_WIND
                        || carrierType == Tribulation.Type.SINGLE_HORNED_TIGER;
                case BLACK_LOU_LAN_WITNESSED -> carrierType == Tribulation.Type.SOUND_CHIMES
                        || carrierType == Tribulation.Type.ANCIENT_BEAST;
                case FANG_ZHENG_WITNESSED -> carrierType == Tribulation.Type.BLOOD_RAIN;
                default -> false;
            };
            if (!validPair) throw new IllegalArgumentException("Impact carrier does not belong to witnessed profile");
            if (carrierId.isBlank() || carrierId.length() > MAX_EFFECT_KEY_LENGTH
                    || carrierId.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid impact carrier id");
            }
            if (impactSequence <= 0 || impactSequence > MAX_TRIBULATION_IMPACT_SEQUENCE) {
                throw new IllegalArgumentException("Invalid impact sequence");
            }
        }
    }

    /** Server-owned world identity for a calamity carrier; coordinates are block positions. */
    public record CarrierAnchor(String dimension, int x, int y, int z, UUID entityId) {
        public CarrierAnchor {
            Objects.requireNonNull(dimension, "carrier dimension");
            if (dimension.length() > 256 || dimension.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid carrier dimension");
            }
            if (dimension.isEmpty()) {
                if (x != 0 || y != 0 || z != 0 || entityId != null) {
                    throw new IllegalArgumentException("Unplaced carrier must not have a world identity");
                }
            } else if (x < -30_000_000 || x > 30_000_000 || z < -30_000_000 || z > 30_000_000
                    || y < -65_536 || y > 65_536) {
                throw new IllegalArgumentException("Carrier position is outside supported bounds");
            }
        }

        public static CarrierAnchor unplaced() {
            return new CarrierAnchor("", 0, 0, 0, null);
        }

        public boolean placed() {
            return !dimension.isEmpty();
        }

        public boolean sameLocation(CarrierAnchor other) {
            return other != null && dimension.equals(other.dimension)
                    && x == other.x && y == other.y && z == other.z;
        }
    }

    /**
     * Active tribulation. Type NONE is the empty state; an active tribulation needs an id and
     * positive remaining carrier units. The engine removes the state on survival/death.
     */
    public record Tribulation(String id, Type type, int remaining, int ticksUntilImpact, CarrierAnchor carrierAnchor) {
        public enum Type { NONE, JADE_WILLOW_WIND, SINGLE_HORNED_TIGER, SOUND_CHIMES, ANCIENT_BEAST, BLOOD_RAIN }

        public Tribulation(String id, Type type, int remaining, int ticksUntilImpact) {
            this(id, type, remaining, ticksUntilImpact, CarrierAnchor.unplaced());
        }

        public Tribulation {
            Objects.requireNonNull(id, "tribulation id");
            Objects.requireNonNull(type, "tribulation type");
            Objects.requireNonNull(carrierAnchor, "carrier anchor");
            if (type == Type.NONE) {
                if (!id.isEmpty() || remaining != 0 || ticksUntilImpact != 0 || !carrierAnchor.equals(CarrierAnchor.unplaced())) {
                    throw new IllegalArgumentException("Empty tribulation must be zeroed");
                }
            } else {
                if (id.isBlank() || id.length() > MAX_EFFECT_KEY_LENGTH
                        || id.chars().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException("Invalid tribulation id");
                }
                if (remaining <= 0 || ticksUntilImpact < 0) {
                    throw new IllegalArgumentException("Invalid tribulation carrier state");
                }
            }
        }

        public static Tribulation empty() {
            return new Tribulation("", Type.NONE, 0, 0, CarrierAnchor.unplaced());
        }

        public boolean active() {
            return type != Type.NONE;
        }

        public Tribulation withCarrierAnchor(CarrierAnchor anchor) {
            if (!active()) throw new IllegalStateException("Cannot place an empty tribulation");
            return new Tribulation(id, type, remaining, ticksUntilImpact, anchor);
        }
    }

    /**
     * Formed immortal aperture record. Area is kept in mu; time ratio and essence are game
     * mappings, not universal canon constants. An unformed aperture uses area 0 and plot -1.
     */
    public record Aperture(double areaMu, double timeRatio, double essence,
                           long internalTicks, int plotIndex) {
        public Aperture {
            if (!Double.isFinite(areaMu) || areaMu < 0 || areaMu > MAX_QI) {
                throw new IllegalArgumentException("Invalid aperture area");
            }
            if (!Double.isFinite(timeRatio) || timeRatio <= 0 || timeRatio > 1000) {
                throw new IllegalArgumentException("Invalid aperture time ratio");
            }
            if (!Double.isFinite(essence) || essence < 0 || essence > MAX_QI) {
                throw new IllegalArgumentException("Invalid aperture essence");
            }
            if (internalTicks < 0) throw new IllegalArgumentException("Invalid internal ticks");
            if (plotIndex < -1) throw new IllegalArgumentException("Invalid aperture plot");
            if (areaMu == 0 && plotIndex != -1) throw new IllegalArgumentException("Unformed aperture cannot own a plot");
        }

        public static Aperture empty() {
            return new Aperture(0, 1, 0, 0, -1);
        }

        public boolean formed() {
            return areaMu > 0;
        }
    }

    /** Binds the single BenMingGu identity; a different identity cannot replace it. */
    public AscensionProgress withVitalGu(VitalGu next) {
        Objects.requireNonNull(next, "next");
        if (vitalGu.bound() && !vitalGu.equals(next)) {
            throw new IllegalStateException("BenMingGu already bound");
        }
        return new AscensionProgress(qi, risk, window, tribulations, aperture, next,
                insightEarned, insightSpent, appliedEffects, apertureDraftId, tribulationProfile,
                tribulationPressure, tribulationImpactSequence, tribulationImpactFailure, xianGu);
    }

    public record VitalGu(String fingerprint, String name) {
        public VitalGu {
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(name, "name");
            if (!fingerprint.isEmpty() && !fingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid BenMingGu fingerprint");
            }
            if (name.length() > 128) throw new IllegalArgumentException("BenMingGu name too long");
            if (fingerprint.isEmpty() && !name.isEmpty()) {
                throw new IllegalArgumentException("Unbound BenMingGu cannot have a name");
            }
        }

        public static VitalGu empty() {
            return new VitalGu("", "");
        }

        public boolean bound() {
            return !fingerprint.isEmpty();
        }
    }}
