package net.guzhenren.shengxian.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * World-bound session cache around one repository directory.
 *
 * <p>The cache is populated only by explicit lookups and is dropped on logout/stop. Every
 * mutation is still validated and persisted by {@link AscensionService}; the cache never
 * becomes the authority. This class has no Minecraft imports so it can be tested headlessly;
 * the server event binding lives in {@code net.guzhenren.shengxian.server}.</p>
 */
public final class SessionStore {
    private final Path directory;
    private final FileSessionRepository repository;
    private final AscensionService service;
    private final Map<ApertureKey, AscensionRecord> cache = new HashMap<>();

    public SessionStore(Path directory, Set<RuleProfile.Capability> adapterCapabilities) throws IOException {
        this(directory, adapterCapabilities, RuleProfile.ordinaryRequired(false));
    }

    /** @param requiredCapabilities route requirements; strict mode passes the boundary-inclusive set */
    public SessionStore(Path directory, Set<RuleProfile.Capability> adapterCapabilities,
                        Set<RuleProfile.Capability> requiredCapabilities) throws IOException {
        this(directory, adapterCapabilities, () -> requiredCapabilities);
    }

    /**
     * @param requiredCapabilities evaluated per start attempt so a config reload cannot desync this
     *     gate from {@code AscensionLifecycleService.readiness()}, which re-reads the config live
     */
    public SessionStore(Path directory, Set<RuleProfile.Capability> adapterCapabilities,
                        java.util.function.Supplier<Set<RuleProfile.Capability>> requiredCapabilities)
            throws IOException {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.repository = new FileSessionRepository(this.directory);
        this.service = new AscensionService(this.repository, adapterCapabilities, requiredCapabilities);
    }

    public Path directory() { return directory; }

    public synchronized Optional<AscensionRecord> find(ApertureKey key) throws IOException {
        AscensionRecord cached = cache.get(key);
        if (cached != null) return Optional.of(cached);
        Optional<AscensionRecord> loaded = repository.find(key);
        loaded.ifPresent(record -> cache.put(key, record));
        return loaded;
    }

    /**
     * Reads every persisted session and indexes formed plots. Duplicate plot ownership or a
     * formed aperture outside IMMORTAL is ambiguous and must block allocation.
     */
    public synchronized Map<Integer, ApertureKey> formedPlotClaims() throws IOException {
        return collectFormedPlotClaims(true);
    }

    /** Startup variant: keep valid claims, log and skip ambiguous records instead of failing the world. */
    public synchronized Map<Integer, ApertureKey> formedPlotClaimsLenient() throws IOException {
        return collectFormedPlotClaims(false);
    }

    private Map<Integer, ApertureKey> collectFormedPlotClaims(boolean strict) throws IOException {
        Map<Integer, ApertureKey> claims = new HashMap<>();
        for (AscensionRecord record : repository.listAll()) {
            AscensionProgress.Aperture aperture = record.progress().aperture();
            if (!aperture.formed()) continue;
            if (record.session().phase() != Phase.IMMORTAL
                    || !ApertureSpaceStore.isValidPlot(aperture.plotIndex())) {
                if (strict) throw new IOException("Invalid formed aperture in session " + record.session().key());
                Logger.getLogger(SessionStore.class.getName()).fine(
                        "Skipping invalid formed aperture in session " + record.session().key());
                if (ApertureSpaceStore.isValidPlot(aperture.plotIndex())) {
                    claims.putIfAbsent(aperture.plotIndex(), record.session().key());
                }
                continue;
            }
            ApertureKey previous = claims.putIfAbsent(aperture.plotIndex(), record.session().key());
            if (previous != null) {
                if (strict) {
                    throw new IOException("Duplicate aperture plot " + aperture.plotIndex()
                            + " claimed by " + previous + " and " + record.session().key());
                }
                Logger.getLogger(SessionStore.class.getName()).warning(
                        "Skipping duplicate aperture plot " + aperture.plotIndex() + " for "
                                + record.session().key() + "; kept " + previous);
            }
        }
        return Map.copyOf(claims);
    }

    public synchronized AscensionRecord load(ApertureKey key) throws IOException {
        return find(key).orElseThrow(() -> new IOException("Session missing"));
    }

    public synchronized AscensionRecord start(ApertureKey key, boolean verifiedFivePeak,
                                              Optional<RuleProfile> profile, long serverTick) throws IOException {
        return start(key, verifiedFivePeak, profile, serverTick, 0.0, AscensionProgress.VitalGu.empty());
    }

    public synchronized AscensionRecord start(ApertureKey key, boolean verifiedFivePeak,
                                              Optional<RuleProfile> profile, long serverTick,
                                              double humanQiReserve, AscensionProgress.VitalGu vitalGu) throws IOException {
        AscensionRecord record = service.start(key, verifiedFivePeak, profile, serverTick,
                humanQiReserve, vitalGu);
        cache.put(key, record);
        return record;
    }

    public synchronized AscensionRecord mutate(ApertureKey key, long expectedStateRevision, long sequence,
                                               long serverTick, UnaryOperator<AscensionProgress> mutation) throws IOException {
        AscensionRecord record = service.mutate(key, expectedStateRevision, sequence, serverTick, mutation);
        cache.put(key, record);
        return record;
    }

    public synchronized AscensionRecord applyEffect(ApertureKey key, long expectedStateRevision,
                                                    long sequence, long serverTick, String effectKey) throws IOException {
        AscensionRecord record = service.applyEffect(key, expectedStateRevision, sequence, serverTick, effectKey);
        cache.put(key, record);
        return record;
    }

    public synchronized AscensionRecord beginShatter(ApertureKey key, long expectedStateRevision,
                                                       long sequence, long serverTick,
                                                       double trueEssenceBefore,
                                                       double trueEssenceTarget) throws IOException {
        AscensionRecord record = service.beginShatter(key, expectedStateRevision, sequence, serverTick,
                trueEssenceBefore, trueEssenceTarget);
        cache.put(key, record);
        return record;
    }

    public synchronized AscensionRecord rebaseShatterClock(ApertureKey key, long expectedStateRevision,
                                                            long sequence, long serverTick) throws IOException {
        AscensionRecord record = service.rebaseShatterClock(key, expectedStateRevision, sequence, serverTick);
        cache.put(key, record);
        return record;
    }

    public synchronized AscensionRecord advanceShatterClock(ApertureKey key, long expectedStateRevision,
                                                             long sequence, long serverTick,
                                                             int durationTicks) throws IOException {
        AscensionRecord record = service.advanceShatterClock(key, expectedStateRevision, sequence,
                serverTick, durationTicks);
        cache.put(key, record);
        return record;
    }

    public synchronized AscensionRecord advance(ApertureKey key, Phase target, long expectedStateRevision,
                                                long sequence, long serverTick) throws IOException {
        AscensionRecord record = service.advance(key, target, expectedStateRevision, sequence, serverTick);
        cache.put(key, record);
        return record;
    }

    public synchronized AscensionService.InsightOutcome seekInsight(ApertureKey key, long expectedStateRevision,
                                                                     long sequence, long serverTick,
                                                                     String insightId, int requiredWindowTicks) throws IOException {
        var outcome = service.seekInsight(key, expectedStateRevision, sequence, serverTick,
                insightId, requiredWindowTicks);
        cache.put(key, outcome.record());
        return outcome;
    }
    public synchronized AscensionRecord stepQi(ApertureKey key, long expectedStateRevision, long sequence,
                                               long serverTick, QiIntent intent, QiParameters parameters) throws IOException {
        AscensionRecord record = service.stepQi(key, expectedStateRevision, sequence, serverTick, intent, parameters);
        cache.put(key, record);
        return record;
    }

    public synchronized AscensionService.SimulationOutcome stepSimulation(ApertureKey key, long expectedStateRevision,
                                                                          long sequence, long serverTick,
                                                                          QiIntent intent, QiParameters qiParameters,
                                                                          TribulationParameters tribulationParameters) throws IOException {
        return stepSimulation(key, expectedStateRevision, sequence, serverTick, intent, qiParameters,
                tribulationParameters, AscensionProgress.CarrierAnchor.unplaced());
    }

    public synchronized AscensionService.SimulationOutcome stepSimulation(ApertureKey key, long expectedStateRevision,
                                                                          long sequence, long serverTick,
                                                                          QiIntent intent, QiParameters qiParameters,
                                                                          TribulationParameters tribulationParameters,
                                                                          AscensionProgress.CarrierAnchor spawnOrigin) throws IOException {
        var outcome = service.stepSimulation(key, expectedStateRevision, sequence, serverTick,
                intent, qiParameters, tribulationParameters, spawnOrigin);
        cache.put(key, outcome.record());
        return outcome;
    }
    public synchronized AscensionRecord strikeTribulation(ApertureKey key, long expectedStateRevision,
                                                          long sequence, long serverTick, int units) throws IOException {
        AscensionRecord record = service.strikeTribulation(key, expectedStateRevision, sequence, serverTick, units);
        cache.put(key, record);
        return record;
    }

    public synchronized AscensionRecord strikeTribulation(ApertureKey key, long expectedStateRevision,
                                                          long sequence, long serverTick,
                                                          String carrierId, int units) throws IOException {
        AscensionRecord record = service.strikeTribulation(key, expectedStateRevision, sequence, serverTick,
                carrierId, units);
        cache.put(key, record);
        return record;
    }
    public synchronized AscensionRecord startFailure(ApertureKey key, long expectedStateRevision,
                                                      long sequence, long serverTick) throws IOException {
        return cacheFailure(key, service.startFailure(key, expectedStateRevision, sequence, serverTick));
    }

    public synchronized AscensionRecord startTribulationImpactFailure(
            ApertureKey key, long expectedStateRevision, long sequence, long serverTick, UUID operationId,
            AscensionProgress.TribulationImpactFailure context) throws IOException {
        return cacheFailure(key, service.startTribulationImpactFailure(key, expectedStateRevision,
                sequence, serverTick, operationId, context));
    }

    public synchronized AscensionRecord markTribulationImpactUncertain(
            ApertureKey key, long expectedStateRevision, long sequence, long serverTick, UUID operationId,
            AscensionProgress.TribulationImpactFailure context) throws IOException {
        return cacheFailure(key, service.markTribulationImpactUncertain(key, expectedStateRevision,
                sequence, serverTick, operationId, context));
    }

    public synchronized AscensionRecord quarantineUnverifiedTribulationImpact(
            ApertureKey key, long expectedStateRevision, long sequence, long serverTick) throws IOException {
        return cacheFailure(key, service.quarantineUnverifiedTribulationImpact(key, expectedStateRevision,
                sequence, serverTick));
    }

    public synchronized AscensionRecord recordTribulationImpactRespawnReceipt(
            ApertureKey key, long expectedStateRevision, long sequence, long serverTick, UUID operationId,
            AscensionProgress.TribulationImpactFailure context) throws IOException {
        return cacheFailure(key, service.recordTribulationImpactRespawnReceipt(key, expectedStateRevision,
                sequence, serverTick, operationId, context));
    }

    public synchronized AscensionRecord acknowledgeTribulationImpact(
            ApertureKey key, long expectedStateRevision, long sequence, long serverTick, UUID operationId,
            AscensionProgress.TribulationImpactFailure context) throws IOException {
        return cacheFailure(key, service.acknowledgeTribulationImpact(key, expectedStateRevision,
                sequence, serverTick, operationId, context));
    }

    public synchronized AscensionRecord markFailureUncertain(ApertureKey key, long expectedStateRevision,
                                                               long sequence, long serverTick) throws IOException {
        return cacheFailure(key, service.markFailureUncertain(key, expectedStateRevision, sequence, serverTick));
    }

    public synchronized AscensionRecord recordFailureRespawnReceipt(ApertureKey key, long expectedStateRevision,
                                                                      long sequence, long serverTick,
                                                                      UUID operationId) throws IOException {
        return cacheFailure(key, service.recordFailureRespawnReceipt(key, expectedStateRevision,
                sequence, serverTick, operationId));
    }

    public synchronized AscensionRecord acknowledgeFailure(ApertureKey key, long expectedStateRevision,
                                                            long sequence, long serverTick,
                                                            UUID operationId) throws IOException {
        return cacheFailure(key, service.acknowledgeFailure(key, expectedStateRevision,
                sequence, serverTick, operationId));
    }

    private AscensionRecord cacheFailure(ApertureKey key, AscensionRecord record) {
        cache.put(key, record);
        return record;
    }

    /** Drops one player's cached records; persisted files are untouched. */
    public synchronized void evict(UUID owner) {
        cache.keySet().removeIf(key -> key.owner().equals(owner));
    }

    /** Drops the whole cache; every successful mutation is already persisted. */
    public synchronized void flush() {
        cache.clear();
    }

    public synchronized AscensionRecord bindVitalGu(ApertureKey key, long expectedStateRevision,
                                                    long sequence, long serverTick, String fingerprint,
                                                    String name) throws IOException {
        AscensionRecord record = service.bindVitalGu(key, expectedStateRevision, sequence, serverTick,
                fingerprint, name);
        cache.put(key, record);
        return record;
    }
    public synchronized AscensionRecord formAperture(ApertureKey key, long expectedStateRevision,
                                                     long sequence, long serverTick, double areaMu,
                                                     double timeRatio, double initialEssence,
                                                     int plotIndex) throws IOException {
        AscensionRecord record = service.formAperture(key, expectedStateRevision, sequence, serverTick,
                areaMu, timeRatio, initialEssence, plotIndex);
        cache.put(key, record);
        return record;
    }

    public synchronized AscensionRecord acknowledgeRankSixSync(ApertureKey key, long expectedStateRevision,
                                                                 long sequence, long serverTick) throws IOException {
        AscensionRecord current = load(key);
        if (current.progress().rankSixSyncAcknowledged()) return current;
        AscensionRecord record = service.acknowledgeRankSixSync(key, expectedStateRevision, sequence, serverTick);
        cache.put(key, record);
        return record;
    }

    public synchronized AscensionRecord formAperture(ApertureKey key, long expectedStateRevision,
                                                     long sequence, long serverTick, double areaMu,
                                                     double timeRatio, double initialEssence,
                                                     int plotIndex, String liveFingerprint) throws IOException {
        AscensionRecord record = service.formAperture(key, expectedStateRevision, sequence, serverTick,
                areaMu, timeRatio, initialEssence, plotIndex, liveFingerprint);
        cache.put(key, record);
        return record;
    }
}