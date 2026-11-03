package net.guzhenren.shengxian.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Domain service for one repository. It is deliberately free of Minecraft classes so the
 * validation path can be tested without a server. World lifecycle binding, real item custody
 * and upstream writes are NOT implemented here.
 *
 * <p>All mutations reload the current record, let {@link SessionFlow} validate the identity
 * counters, enforce monotonic progress, and save with the expected state revision.</p>
 */
public final class AscensionService {
    private final SessionRepository repository;
    private final Set<RuleProfile.Capability> adapterCapabilities;
    private final java.util.function.Supplier<Set<RuleProfile.Capability>> requiredCapabilities;

    public AscensionService(SessionRepository repository, Set<RuleProfile.Capability> adapterCapabilities) {
        this(repository, adapterCapabilities, RuleProfile.ordinaryRequired(false));
    }

    /**
     * @param requiredCapabilities the route requirement set; a strict deployment passes
     *     {@link RuleProfile#ordinaryRequired(boolean)} with {@code true} so the audited upstream
     *     aperture field boundary keeps the gate closed (PROJECT-003)
     */
    public AscensionService(SessionRepository repository, Set<RuleProfile.Capability> adapterCapabilities,
                            Set<RuleProfile.Capability> requiredCapabilities) {
        this(repository, adapterCapabilities, () -> requiredCapabilities);
    }

    /**
     * @param requiredCapabilities evaluated on every start attempt. A server deployment must pass a
     *     supplier instead of a captured set: {@code readiness()} re-reads the operator config on
     *     each call, so a captured set would let the reported gate state and this gate diverge after
     *     a config reload.
     */
    public AscensionService(SessionRepository repository, Set<RuleProfile.Capability> adapterCapabilities,
                            java.util.function.Supplier<Set<RuleProfile.Capability>> requiredCapabilities) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.adapterCapabilities = Set.copyOf(Objects.requireNonNull(adapterCapabilities, "adapterCapabilities"));
        this.requiredCapabilities = Objects.requireNonNull(requiredCapabilities, "requiredCapabilities");
    }

    /** Creates a session only when the start gate passes and no record exists yet. */
    public AscensionRecord start(ApertureKey key, boolean verifiedFivePeak,
                                 Optional<RuleProfile> profile, long serverTick) throws IOException {
        return start(key, verifiedFivePeak, profile, serverTick, 0.0,
                AscensionProgress.VitalGu.empty());
    }

    /** Ordinary-route start with the server-derived reserve and the already verified vital Gu. */
    public AscensionRecord start(ApertureKey key, boolean verifiedFivePeak,
                                 Optional<RuleProfile> profile, long serverTick,
                                 double humanQiReserve, AscensionProgress.VitalGu vitalGu) throws IOException {
        return start(key, verifiedFivePeak, profile, serverTick, humanQiReserve, vitalGu, null);
    }

    /**
     * As above, but freezes the calamity profile at creation when one is supplied.
     *
     * <p>CANON-007: ten-extremes calamities are worse, and the trigger is the player's physique
     * rather than an operator setting. Freezing it at creation persists the choice with the session,
     * and {@code TribulationEngine} later re-derives its parameters from the frozen value instead of
     * overwriting it with the configured ordinary profile.</p>
     *
     * @param frozenTribulationProfile {@code null} leaves the profile unfrozen as before
     */
    public AscensionRecord start(ApertureKey key, boolean verifiedFivePeak,
                                 Optional<RuleProfile> profile, long serverTick,
                                 double humanQiReserve, AscensionProgress.VitalGu vitalGu,
                                 TribulationParameters.Profile frozenTribulationProfile) throws IOException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(vitalGu, "vitalGu");
        if (!Double.isFinite(humanQiReserve) || humanQiReserve < 0
                || humanQiReserve > AscensionProgress.MAX_QI) {
            throw new IllegalArgumentException("Invalid initial human-qi reserve");
        }
        if (repository.find(key).isPresent()) throw new IOException("Session already exists");
        StartGate.Decision decision = StartGate.check(verifiedFivePeak, false, profile,
                adapterCapabilities, requiredCapabilities.get());
        if (!decision.allowed()) {
            throw new IOException("Start gate denied: " + String.join(",", decision.reasons()));
        }
        AscensionProgress progress = AscensionProgress.empty(humanQiReserve);
        if (vitalGu.bound()) progress = progress.withVitalGu(vitalGu);
        if (frozenTribulationProfile != null) {
            progress = progress.withTribulationProfile(frozenTribulationProfile);
        }
        AscensionRecord record = new AscensionRecord(AscensionSession.prepare(key, serverTick), progress);
        repository.save(record, -1);
        return record;
    }

    public AscensionRecord load(ApertureKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        return repository.find(key).orElseThrow(() -> new IOException("Session missing"));
    }

    /** Same-phase update with an explicit progress transformation. */
    public AscensionRecord mutate(ApertureKey key, long expectedStateRevision, long sequence,
                                  long serverTick, UnaryOperator<AscensionProgress> mutation) throws IOException {
        Objects.requireNonNull(mutation, "mutation");
        AscensionRecord current = load(key);
        AscensionSession nextSession = SessionFlow.update(current.session(), expectedStateRevision,
                sequence, serverTick);
        AscensionProgress nextProgress = Objects.requireNonNull(mutation.apply(current.progress()), "progress");
        nextProgress.requireMonotonicFrom(current.progress());
        AscensionRecord next = new AscensionRecord(nextSession, nextProgress);
        repository.save(next, expectedStateRevision);
        return next;
    }

    /** Applies one idempotent effect key; a repeated key fails before any save. */
    public AscensionRecord applyEffect(ApertureKey key, long expectedStateRevision, long sequence,
                                       long serverTick, String effectKey) throws IOException {
        return mutate(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withAppliedEffect(effectKey));
    }

    public record InsightOutcome(AscensionRecord record, InsightCatalog.Entry entry) {}

    /** Grants one fixed catalog entry during an active sustained insight window. */
    public InsightOutcome seekInsight(ApertureKey key, long expectedStateRevision, long sequence,
                                      long serverTick, String insightId, int requiredWindowTicks) throws IOException {
        AscensionRecord current = load(key);
        if (current.session().phase() != Phase.ABSORBING) {
            throw new IllegalStateException("Insight is only available while absorbing");
        }
        InsightCatalog.Entry entry = InsightCatalog.find(insightId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown insight"));
        AscensionProgress progress = InsightCatalog.grant(current.progress(), insightId, requiredWindowTicks);
        AscensionSession nextSession = SessionFlow.update(current.session(), expectedStateRevision, sequence, serverTick);
        AscensionRecord next = new AscensionRecord(nextSession, progress);
        repository.save(next, expectedStateRevision);
        return new InsightOutcome(next, entry);
    }
    /**
     * Advances the continuous three-qi simulation. Only ABSORBING may be stepped. A lethal
     * imbalance transitions the session to FAILED in the same save; otherwise it is a normal
     * same-phase update. The client can only supply fractions, never absolute qi.
     */
    public AscensionRecord stepQi(ApertureKey key, long expectedStateRevision, long sequence,
                                  long serverTick, QiIntent intent, QiParameters parameters) throws IOException {
        AscensionRecord current = load(key);
        if (current.session().phase() != Phase.ABSORBING) {
            throw new IllegalStateException("Qi simulation is only valid while absorbing");
        }
        QiEngine.Result result = QiEngine.step(current.progress(), intent, serverTick, parameters);
        AscensionProgress progress = result.fatal()
                ? result.progress().withFailurePending(failureCauses(result.events()))
                        .withTribulation(AscensionProgress.Tribulation.empty())
                : result.progress();
        AscensionSession nextSession = result.fatal()
                ? SessionFlow.advance(current.session(), Phase.FAILED, expectedStateRevision, sequence, serverTick)
                : SessionFlow.update(current.session(), expectedStateRevision, sequence, serverTick);
        AscensionRecord next = new AscensionRecord(nextSession, progress);
        repository.save(next, expectedStateRevision);
        return next;
    }

    /** Combined qi + tribulation outcome; events are for server messages, not persisted. */
    public record SimulationOutcome(AscensionRecord record, List<QiEngine.Event> qiEvents,
                                    List<TribulationEngine.Event> tribulationEvents, boolean fatal) {
        public SimulationOutcome {
            qiEvents = List.copyOf(qiEvents);
            tribulationEvents = List.copyOf(tribulationEvents);
        }
    }

    /**
     * One server simulation tick: qi first, then tribulation start/impact. Both share one
     * expected state revision and one save. A fatal result (imbalance or tribulation shock)
     * commits FAILED atomically.
     */
    public SimulationOutcome stepSimulation(ApertureKey key, long expectedStateRevision, long sequence,
                                            long serverTick, QiIntent intent, QiParameters qiParameters,
                                            TribulationParameters tribulationParameters) throws IOException {
        return stepSimulation(key, expectedStateRevision, sequence, serverTick, intent, qiParameters,
                tribulationParameters, AscensionProgress.CarrierAnchor.unplaced());
    }

    public SimulationOutcome stepSimulation(ApertureKey key, long expectedStateRevision, long sequence,
                                            long serverTick, QiIntent intent, QiParameters qiParameters,
                                            TribulationParameters tribulationParameters,
                                            AscensionProgress.CarrierAnchor spawnOrigin) throws IOException {
        AscensionRecord current = load(key);
        if (current.session().phase() != Phase.ABSORBING) {
            throw new IllegalStateException("Ascension simulation is only valid while absorbing");
        }
        AscensionProgress initialProgress = current.progress();
        if (initialProgress.tribulationProfile() == TribulationParameters.Profile.NOT_FROZEN) {
            TribulationParameters.Profile selected = tribulationParameters.profile()
                    == TribulationParameters.Profile.NOT_FROZEN
                    ? TribulationParameters.Profile.UNRESOLVED_ORDINARY : tribulationParameters.profile();
            initialProgress = initialProgress.withTribulationProfile(selected);
        }
        QiEngine.Result qiResult = QiEngine.step(initialProgress, intent, serverTick, qiParameters);
        AscensionProgress progress = qiResult.progress();
        boolean fatal = qiResult.fatal();
        List<TribulationEngine.Event> tribulationEvents = new ArrayList<>();
        if (!fatal) {
            TribulationEngine.Result started = TribulationEngine.startIfDue(progress, serverTick, tribulationParameters);
            progress = started.progress();
            tribulationEvents.addAll(started.events());
            if (!progress.tribulations().isEmpty()) {
                TribulationEngine.Result ticked = TribulationEngine.tick(progress, tribulationParameters,
                        qiParameters.riskLimit());
                progress = ticked.progress();
                tribulationEvents.addAll(ticked.events());
                fatal = ticked.fatal();
            }
        }
        if (!fatal) progress = anchorUnplacedCarriers(key, progress, spawnOrigin);
        if (fatal) {
            Set<AscensionProgress.FailureCause> causes = qiResult.fatal()
                    ? failureCauses(qiResult.events())
                    : Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT);
            progress = progress.withFailurePending(causes)
                    .withTribulation(AscensionProgress.Tribulation.empty());
        }
        AscensionSession nextSession = fatal
                ? SessionFlow.advance(current.session(), Phase.FAILED, expectedStateRevision, sequence, serverTick)
                : SessionFlow.update(current.session(), expectedStateRevision, sequence, serverTick);
        AscensionRecord next = new AscensionRecord(nextSession, progress);
        repository.save(next, expectedStateRevision);
        return new SimulationOutcome(next, qiResult.events(), tribulationEvents, fatal);
    }

    private static AscensionProgress anchorUnplacedCarriers(ApertureKey key, AscensionProgress progress,
                                                              AscensionProgress.CarrierAnchor spawnOrigin) {
        if (spawnOrigin == null || !spawnOrigin.placed()) return progress;
        if (spawnOrigin.entityId() != null) throw new IllegalArgumentException("Spawn origin cannot carry an entity UUID");
        List<AscensionProgress.Tribulation> carriers = progress.tribulations();
        for (int i = 0; i < carriers.size(); i++) {
            AscensionProgress.Tribulation carrier = carriers.get(i);
            AscensionProgress.CarrierAnchor current = carrier.carrierAnchor();
            if (current.placed() && current.entityId() != null) continue;
            AscensionProgress.CarrierAnchor location = current.placed() ? current
                    : new AscensionProgress.CarrierAnchor(spawnOrigin.dimension(),
                    spawnOrigin.x() + horizontalOffset(i), spawnOrigin.y(), spawnOrigin.z(), null);
            UUID entityId = current.entityId() != null ? current.entityId()
                    : UUID.nameUUIDFromBytes((key.owner() + "|" + key.apertureId() + "|" + carrier.id())
                    .getBytes(StandardCharsets.UTF_8));
            progress = progress.withCarrierAnchor(carrier.id(),
                    new AscensionProgress.CarrierAnchor(location.dimension(), location.x(), location.y(),
                            location.z(), entityId));
        }
        return progress;
    }

    private static int horizontalOffset(int index) {
        int distance = index / 2 + 1;
        return index % 2 == 0 ? -distance : distance;
    }
    /** Guardian strike on the active carrier; never touches qi, risk or the insight window. */
    public AscensionRecord strikeTribulation(ApertureKey key, long expectedStateRevision, long sequence,
                                             long serverTick, int units) throws IOException {
        AscensionRecord current = load(key);
        String target = current.progress().tribulation().id();
        return strikeTribulation(key, expectedStateRevision, sequence, serverTick, target, units);
    }

    public AscensionRecord strikeTribulation(ApertureKey key, long expectedStateRevision, long sequence,
                                             long serverTick, String carrierId, int units) throws IOException {
        AscensionRecord current = load(key);
        if (current.session().phase() != Phase.ABSORBING) {
            throw new IllegalStateException("Tribulation strikes are only valid while absorbing");
        }
        TribulationEngine.Result result = TribulationEngine.strike(current.progress(), carrierId, units);
        if (result.progress().equals(current.progress())) return current;
        AscensionSession nextSession = SessionFlow.update(current.session(), expectedStateRevision, sequence, serverTick);
        AscensionRecord next = new AscensionRecord(nextSession, result.progress());
        repository.save(next, expectedStateRevision);
        return next;
    }

    private static Set<AscensionProgress.FailureCause> failureCauses(List<QiEngine.Event> events) {
        Set<AscensionProgress.FailureCause> causes = new java.util.LinkedHashSet<>();
        for (QiEngine.Event event : events) {
            switch (event.type()) {
                case IMBALANCE_HEAVEN -> causes.add(AscensionProgress.FailureCause.HEAVEN_OVERLOAD);
                case IMBALANCE_EARTH -> causes.add(AscensionProgress.FailureCause.EARTH_OVERLOAD);
                case IMBALANCE_HUMAN -> causes.add(AscensionProgress.FailureCause.HUMAN_OVERLOAD);
                default -> { }
            }
        }
        if (causes.isEmpty()) causes.add(AscensionProgress.FailureCause.UNCLASSIFIED);
        return Set.copyOf(causes);
    }

    public AscensionRecord startFailure(ApertureKey key, long expectedStateRevision,
                                        long sequence, long serverTick) throws IOException {
        AscensionRecord current = load(key);
        requireCanonicalFailure(current);
        return updateFailureSettlement(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withFailureStarted());
    }

    /** Starts only a source-verified named-profile impact settlement. */
    public AscensionRecord startTribulationImpactFailure(
            ApertureKey key, long expectedStateRevision, long sequence, long serverTick, UUID operationId,
            AscensionProgress.TribulationImpactFailure context) throws IOException {
        AscensionRecord current = load(key);
        requireTribulationImpactFailure(current, operationId, context);
        return updateFailureSettlement(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withFailureStarted());
    }

    /** Writes an explicit quarantine marker when impact execution or evidence is ambiguous. */
    public AscensionRecord markTribulationImpactUncertain(
            ApertureKey key, long expectedStateRevision, long sequence, long serverTick, UUID operationId,
            AscensionProgress.TribulationImpactFailure context) throws IOException {
        AscensionRecord current = load(key);
        requireTribulationImpactFailure(current, operationId, context);
        if (current.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.UNCERTAIN) {
            return current;
        }
        return updateFailureSettlement(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withFailureUncertain());
    }

    /** Quarantines an old impact record that has no provenance, without allowing it to apply damage. */
    public AscensionRecord quarantineUnverifiedTribulationImpact(
            ApertureKey key, long expectedStateRevision, long sequence, long serverTick) throws IOException {
        AscensionRecord current = load(key);
        if (current.session().phase() != Phase.FAILED
                || !current.progress().failureCauses().equals(Set.of(AscensionProgress.FailureCause.TRIBULATION_IMPACT))
                || current.progress().tribulationImpactFailure().isPresent()) {
            throw new IOException("Only a source-missing impact failure can use legacy quarantine");
        }
        if (current.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.UNCERTAIN) {
            return current;
        }
        return updateFailureSettlement(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withFailureUncertain());
    }

    /** Records verified player respawn evidence for the exact impact operation and source. */
    public AscensionRecord recordTribulationImpactRespawnReceipt(
            ApertureKey key, long expectedStateRevision, long sequence, long serverTick, UUID operationId,
            AscensionProgress.TribulationImpactFailure context) throws IOException {
        AscensionRecord current = load(key);
        requireTribulationImpactFailure(current, operationId, context);
        AscensionProgress.FailureSettlementState state = current.progress().failureSettlementState();
        if (state == AscensionProgress.FailureSettlementState.RESPAWN_RECEIPT
                || state == AscensionProgress.FailureSettlementState.ACKNOWLEDGED) return current;
        return updateFailureSettlement(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withFailureRespawnReceipt());
    }

    /** Acknowledges an impact only after the matching persisted respawn receipt is verified. */
    public AscensionRecord acknowledgeTribulationImpact(
            ApertureKey key, long expectedStateRevision, long sequence, long serverTick, UUID operationId,
            AscensionProgress.TribulationImpactFailure context) throws IOException {
        AscensionRecord current = load(key);
        requireTribulationImpactFailure(current, operationId, context);
        if (current.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.ACKNOWLEDGED) {
            return current;
        }
        return updateFailureSettlement(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withFailureAcknowledged());
    }

    public AscensionRecord markFailureUncertain(ApertureKey key, long expectedStateRevision,
                                                long sequence, long serverTick) throws IOException {
        AscensionRecord current = load(key);
        if (current.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.UNCERTAIN) {
            return current;
        }
        return updateFailureSettlement(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withFailureUncertain());
    }

    public AscensionRecord recordFailureRespawnReceipt(ApertureKey key, long expectedStateRevision,
                                                       long sequence, long serverTick,
                                                       UUID operationId) throws IOException {
        AscensionRecord current = load(key);
        requireFailureOperation(current, operationId);
        AscensionProgress.FailureSettlementState state = current.progress().failureSettlementState();
        if (state == AscensionProgress.FailureSettlementState.RESPAWN_RECEIPT
                || state == AscensionProgress.FailureSettlementState.ACKNOWLEDGED) return current;
        return updateFailureSettlement(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withFailureRespawnReceipt());
    }

    public AscensionRecord acknowledgeFailure(ApertureKey key, long expectedStateRevision,
                                               long sequence, long serverTick,
                                               UUID operationId) throws IOException {
        AscensionRecord current = load(key);
        requireFailureOperation(current, operationId);
        if (current.progress().failureSettlementState() == AscensionProgress.FailureSettlementState.ACKNOWLEDGED) {
            return current;
        }
        return updateFailureSettlement(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withFailureAcknowledged());
    }

    private AscensionRecord updateFailureSettlement(ApertureKey key, long expectedStateRevision,
                                                      long sequence, long serverTick,
                                                      UnaryOperator<AscensionProgress> update) throws IOException {
        AscensionRecord current = load(key);
        if (current.session().phase() != Phase.FAILED || current.progress().failureCauses().isEmpty()) {
            throw new IOException("No failed session settlement is available");
        }
        AscensionSession nextSession;
        AscensionProgress nextProgress;
        try {
            nextSession = SessionFlow.acknowledgeFailure(current.session(),
                    expectedStateRevision, sequence, serverTick);
            nextProgress = update.apply(current.progress());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new IOException("Failure settlement transition rejected", ex);
        }
        AscensionRecord next = new AscensionRecord(nextSession, nextProgress);
        repository.save(next, expectedStateRevision);
        return next;
    }

    private static void requireFailureOperation(AscensionRecord record, UUID operationId) throws IOException {
        if (record.session().phase() != Phase.FAILED || record.progress().failureCauses().isEmpty()) {
            throw new IOException("No failed session settlement is available");
        }
        requireCanonicalFailure(record);
        UUID expected = FailureSettlementIdentity.operationId(record.session().id(),
                record.session().phaseRevision());
        if (!expected.equals(operationId)) throw new IOException("Failure operation ID does not match session");
    }

    private static void requireCanonicalFailure(AscensionRecord record) throws IOException {
        if (record.session().phase() != Phase.FAILED
                || !AscensionReadiness.hasCanonicalFatalQiCause(record.progress().failureCauses())) {
            throw new IOException("Failure settlement requires exactly one canonical fatal qi cause");
        }
    }

    private static void requireTribulationImpactFailure(
            AscensionRecord record, UUID operationId,
            AscensionProgress.TribulationImpactFailure context) throws IOException {
        if (record.session().phase() != Phase.FAILED
                || !AscensionReadiness.hasVerifiedTribulationImpactCause(record.progress().failureCauses(), context)
                || record.progress().tribulationImpactFailure().filter(context::equals).isEmpty()) {
            throw new IOException("Impact settlement requires the exact witnessed impact source");
        }
        UUID expected = FailureSettlementIdentity.operationId(record.session().id(),
                record.session().phaseRevision());
        if (operationId == null || !expected.equals(operationId)) {
            throw new IOException("Impact operation ID does not match failed session");
        }
    }

    /** Starts the irreversible route with the original and target essence values fixed durably. */
    public AscensionRecord beginShatter(ApertureKey key, long expectedStateRevision, long sequence,
                                        long serverTick, double trueEssenceBefore,
                                        double trueEssenceTarget) throws IOException {
        AscensionRecord current = load(key);
        if (current.session().phase() != Phase.PREPARING) {
            throw new IOException("Shattering can only begin in PREPARING");
        }
        if (current.session().phaseRevision() != 0 || !current.progress().shatterEssenceTargetKeys().isEmpty()) {
            throw new IOException("A shatter attempt has already begun and cannot be restarted");
        }
        AscensionSession session = SessionFlow.advance(current.session(), Phase.BREAKING,
                expectedStateRevision, sequence, serverTick);
        AscensionProgress progress = current.progress().withShatterEssenceIntent(
                session.phaseRevision(), trueEssenceBefore, trueEssenceTarget)
                .withShatterClock(ShatterClock.start(session.phaseRevision(), serverTick));
        AscensionRecord next = new AscensionRecord(session, progress);
        repository.save(next, expectedStateRevision);
        return next;
    }

    /**
     * CANON-007: a ten-extremes aperture ruptures by itself once the gu seal inside the body is
     * released, so this entry advances to BREAKING without the true-essence shock intent that
     * {@link #beginShatter} records. Nothing downstream requires that intent, so the irreversible
     * step keeps its normal durability trail while spending no true essence.
     */
    public AscensionRecord beginSelfRupture(ApertureKey key, long expectedStateRevision,
                                            long sequence, long serverTick) throws IOException {
        AscensionRecord current = load(key);
        if (current.session().phase() != Phase.PREPARING) {
            throw new IOException("Self-rupture can only begin in PREPARING");
        }
        if (current.session().phaseRevision() != 0 || !current.progress().shatterEssenceTargetKeys().isEmpty()) {
            throw new IOException("A shatter attempt has already begun and cannot be restarted");
        }
        AscensionSession session = SessionFlow.advance(current.session(), Phase.BREAKING,
                expectedStateRevision, sequence, serverTick);
        AscensionProgress progress = current.progress()
                .withShatterClock(ShatterClock.start(session.phaseRevision(), serverTick));
        AscensionRecord next = new AscensionRecord(session, progress);
        repository.save(next, expectedStateRevision);
        return next;
    }

    /** Resets the shatter clock baseline on login without counting offline world ticks. */
    public AscensionRecord rebaseShatterClock(ApertureKey key, long expectedStateRevision,
                                              long sequence, long serverTick) throws IOException {
        AscensionRecord current = load(key);
        if (current.session().phase() != Phase.BREAKING) {
            throw new IOException("Shatter clock can only resume while BREAKING");
        }
        long phaseRevision = current.session().phaseRevision();
        ShatterClock.State previous = current.progress().shatterClock(phaseRevision)
                .orElseGet(() -> ShatterClock.start(phaseRevision, serverTick));
        ShatterClock.State rebased = ShatterClock.rebase(previous, serverTick);
        if (rebased.equals(previous) && current.progress().shatterClock(phaseRevision).isPresent()) return current;
        return mutate(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withShatterClock(rebased));
    }

    /** Advances only the persisted online interval for a BREAKING session. */
    public AscensionRecord advanceShatterClock(ApertureKey key, long expectedStateRevision,
                                               long sequence, long serverTick, int durationTicks) throws IOException {
        AscensionRecord current = load(key);
        if (current.session().phase() != Phase.BREAKING) {
            throw new IOException("Shatter clock can only advance while BREAKING");
        }
        long phaseRevision = current.session().phaseRevision();
        ShatterClock.State previous = current.progress().shatterClock(phaseRevision)
                .orElseGet(() -> ShatterClock.start(phaseRevision, serverTick));
        ShatterClock.State advanced = ShatterClock.advance(previous, serverTick, durationTicks);
        return mutate(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withShatterClock(advanced));
    }

    /** Phase transition; the progress payload is carried through unchanged. */
    public AscensionRecord advance(ApertureKey key, Phase target, long expectedStateRevision,
                                   long sequence, long serverTick) throws IOException {
        AscensionRecord current = load(key);
        AscensionSession nextSession = SessionFlow.advance(current.session(), target,
                expectedStateRevision, sequence, serverTick);
        AscensionRecord next = new AscensionRecord(nextSession, current.progress());
        repository.save(next, expectedStateRevision);
        return next;
    }

    /**
     * Forms the aperture at the end of FORMING and commits the domain IMMORTAL state in one
     * save. Callers must supply the aperture parameters and plot reservation; this method does
     * not create the world space, write upstream cultivation, or refill lifespan.
     */
    /** Records the single BenMingGu identity; a different identity is rejected. */
    public AscensionRecord bindVitalGu(ApertureKey key, long expectedStateRevision, long sequence,
                                       long serverTick, String fingerprint, String name) throws IOException {
        return mutate(key, expectedStateRevision, sequence, serverTick,
                progress -> progress.withVitalGu(new AscensionProgress.VitalGu(fingerprint, name)));
    }

    public AscensionRecord formAperture(ApertureKey key, long expectedStateRevision, long sequence,
                                        long serverTick, double areaMu, double timeRatio,
                                        double initialEssence, int plotIndex) throws IOException {
        return formAperture(key, expectedStateRevision, sequence, serverTick, areaMu, timeRatio,
                initialEssence, plotIndex, null);
    }

    /** Overload with a live BenMingGu fingerprint check; null means the caller already checked. */
    public AscensionRecord formAperture(ApertureKey key, long expectedStateRevision, long sequence,
                                        long serverTick, double areaMu, double timeRatio,
                                        double initialEssence, int plotIndex, String liveFingerprint) throws IOException {
        AscensionRecord current = load(key);
        if (current.session().phase() != Phase.FORMING) {
            throw new IOException("Aperture can only form in FORMING");
        }
        if (current.progress().aperture().formed()) {
            throw new IOException("Aperture already formed");
        }
        if (!current.progress().tribulations().isEmpty()) {
            throw new IOException("Active tribulation");
        }
        if (!current.progress().vitalGu().bound()) {
            throw new IOException("BenMingGu not bound");
        }
        if (liveFingerprint != null && !liveFingerprint.equals(current.progress().vitalGu().fingerprint())) {
            throw new IOException("BenMingGu fingerprint mismatch");
        }
        if (plotIndex < 0) {
            throw new IllegalArgumentException("A formed aperture needs a plot");
        }
        AscensionProgress nextProgress = ApertureEngine.form(current.progress(), areaMu, timeRatio,
                initialEssence, plotIndex).withAppliedEffect(AscensionProgress.RANK_SIX_SYNC_RECOVERY_EFFECT)
                .withAppliedEffect(AscensionProgress.RANK_SIX_SYNC_PROJECTION_V1_EFFECT);
        AscensionSession nextSession = SessionFlow.advance(current.session(), Phase.IMMORTAL,
                expectedStateRevision, sequence, serverTick);
        AscensionRecord next = new AscensionRecord(nextSession, nextProgress);
        repository.save(next, expectedStateRevision);
        return next;
    }

    /** Confirms the upstream rank-six projection while leaving the phase and formed aperture intact. */
    public AscensionRecord acknowledgeRankSixSync(ApertureKey key, long expectedStateRevision,
                                                   long sequence, long serverTick) throws IOException {
        AscensionRecord current = load(key);
        if (current.progress().rankSixSyncAcknowledged()) return current;
        if (current.session().phase() != Phase.IMMORTAL || !current.progress().aperture().formed()
                || !current.progress().rankSixSyncRecoveryAvailable()) {
            throw new IOException("No pending rank-six sync acknowledgement");
        }
        AscensionSession nextSession = SessionFlow.update(current.session(), expectedStateRevision,
                sequence, serverTick);
        AscensionProgress nextProgress;
        try {
            nextProgress = current.progress().withRankSixSyncAcknowledged();
        } catch (IllegalStateException ex) {
            throw new IOException("Invalid rank-six sync intent", ex);
        }
        AscensionRecord next = new AscensionRecord(nextSession, nextProgress);
        repository.save(next, expectedStateRevision);
        return next;
    }
}
