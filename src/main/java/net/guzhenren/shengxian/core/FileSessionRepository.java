package net.guzhenren.shengxian.core;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Single-record atomic storage, never a cross-world/player transaction.
 *
 * <p>Schema 2 stores the phase identity, the same-phase progress and the idempotency ledger
 * in one file. Schema 1 records are read through an explicit migration; they are not rewritten
 * until a successful save. Unknown schemas and malformed records are preserved and rejected.</p>
 */
public final class FileSessionRepository implements SessionRepository {
    private static final String SCHEMA_VERSION = "11";
    private static final long MAX_RECORD_BYTES = 2_097_152;

    private final Path directory;

    public FileSessionRepository(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
    }

    private Path path(ApertureKey key) {
        try {
            // Windows folds case in filenames; a fixed lowercase digest also bounds Unicode IDs.
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.apertureId().getBytes(StandardCharsets.UTF_8));
            return directory.resolve(key.owner() + "-" + HexFormat.of().formatHex(digest) + ".properties");
        } catch (NoSuchAlgorithmException ex) { throw new AssertionError("Java SHA-256 unavailable", ex); }
    }

    private static String required(Properties p, String key) throws IOException {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing session property: " + key);
        return value;
    }

    private static long parseLong(Properties p, String key) throws IOException {
        try { return Long.parseLong(required(p, key)); }
        catch (NumberFormatException ex) { throw new IOException("Invalid numeric property: " + key, ex); }
    }

    private static int parseInt(Properties p, String key) throws IOException {
        long value = parseLong(p, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IOException("Numeric property out of range: " + key);
        }
        return (int) value;
    }

    private static double parseDouble(Properties p, String key) throws IOException {
        try { return Double.parseDouble(required(p, key)); }
        catch (NumberFormatException ex) { throw new IOException("Invalid numeric property: " + key, ex); }
    }

    /**
     * Enumerates persisted session records for startup reconciliation. Every candidate must
     * be a regular file whose embedded identity matches its deterministic filename; unknown
     * schemas and malformed records are preserved and abort the scan.
     */
    public synchronized List<AscensionRecord> listAll() throws IOException {
        List<Path> candidates = new ArrayList<>();
        try (var entries = Files.newDirectoryStream(directory, "*.properties")) {
            for (Path entry : entries) candidates.add(entry);
        }
        candidates.sort(Comparator.comparing(entry -> entry.getFileName().toString()));
        List<AscensionRecord> records = new ArrayList<>();
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Session candidate is not a regular file: " + candidate.getFileName());
            }
            if (Files.size(candidate) > MAX_RECORD_BYTES) {
                throw new IOException("Oversized session record: " + candidate.getFileName());
            }
            Properties identity = new Properties();
            try (var in = Files.newInputStream(candidate)) {
                identity.load(in);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Malformed session properties: " + candidate.getFileName(), ex);
            }
            ApertureKey key;
            try {
                key = new ApertureKey(UUID.fromString(required(identity, "owner")),
                        required(identity, "aperture"));
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid session identity: " + candidate.getFileName(), ex);
            }
            if (!candidate.toAbsolutePath().normalize().equals(path(key))) {
                throw new IOException("Session filename does not match its identity: " + candidate.getFileName());
            }
            records.add(find(key).orElseThrow(
                    () -> new IOException("Session disappeared during scan: " + candidate.getFileName())));
        }
        return List.copyOf(records);
    }

    @Override public Optional<AscensionRecord> find(ApertureKey key) throws IOException {
        Path file = path(key);
        if (!Files.exists(file)) return Optional.empty();
        if (Files.size(file) > MAX_RECORD_BYTES) throw new IOException("Oversized session record");
        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) { p.load(in); }
        catch (IllegalArgumentException ex) { throw new IOException("Malformed session properties; record preserved", ex); }
        String schema = required(p, "schema");
        try {
            if ("1".equals(schema)) return Optional.of(readSchema1(p, key));
            if ("2".equals(schema)) return Optional.of(readSchema2(p, key, false, false, false, false, false, false, false));
            if ("3".equals(schema)) return Optional.of(readSchema2(p, key, true, false, false, false, false, false, false));
            if ("4".equals(schema)) return Optional.of(readSchema2(p, key, true, true, false, false, false, false, false));
            if ("5".equals(schema)) return Optional.of(readSchema2(p, key, true, true, true, false, false, false, false));
            if ("6".equals(schema)) return Optional.of(readSchema2(p, key, true, true, true, true, false, false, false));
            if ("7".equals(schema)) return Optional.of(readSchema2(p, key, true, true, true, true, true, false, false));
            if ("8".equals(schema)) return Optional.of(readSchema2(p, key, true, true, true, true, true, true, false));
            if ("9".equals(schema)) return Optional.of(readSchema2(p, key, true, true, true, true, true, true, true));
            if ("10".equals(schema)) return Optional.of(readSchema2(p, key, true, true, true, true, true, true, true));
            if (SCHEMA_VERSION.equals(schema)) {
                AscensionRecord record = readSchema2(p, key, true, true, true, true, true, true, true);
                XianGuRuntime runtime = XianGuStateCodec.read(p);
                if (!runtime.equals(XianGuRuntime.empty()) && (record.session().phase() != Phase.IMMORTAL
                        || !record.progress().aperture().formed())) {
                    throw new IOException("Reusable Gu state requires a formed immortal session; record preserved");
                }
                return Optional.of(new AscensionRecord(record.session(), record.progress().withXianGu(runtime)));
            }
            throw new IOException("Unsupported schema; record preserved");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new IOException("Invalid session; record preserved", ex);
        }
    }

    private static AscensionRecord readSchema1(Properties p, ApertureKey key) throws IOException {
        ApertureKey storedKey = new ApertureKey(UUID.fromString(required(p, "owner")), required(p, "aperture"));
        if (!key.equals(storedKey)) throw new IOException("Session identity mismatch");
        String marker = required(p, "broken");
        if (!marker.equals("true") && !marker.equals("false")) throw new IOException("Invalid broken marker");
        long revision = parseLong(p, "revision");
        long sequence = parseLong(p, "sequence");
        AscensionSession session = new AscensionSession(UUID.fromString(required(p, "id")), storedKey,
                Phase.valueOf(required(p, "phase")), Boolean.parseBoolean(marker),
                revision, revision, sequence, 0);
        AscensionProgress progress = AscensionProgress.empty();
        if (session.phase() == Phase.ABSORBING) {
            progress = progress.withTribulationProfile(TribulationParameters.Profile.UNRESOLVED_ORDINARY);
        }
        return new AscensionRecord(session, progress);
    }

    private static AscensionRecord readSchema2(Properties p, ApertureKey key, boolean withDynamics,
                                             boolean withTribulation, boolean withAperture, boolean withVitalGu,
                                             boolean withMultipleTribulations, boolean withCarrierAnchors,
                                             boolean withImpactState) throws IOException {
        ApertureKey storedKey = new ApertureKey(UUID.fromString(required(p, "owner")), required(p, "aperture"));
        if (!key.equals(storedKey)) throw new IOException("Session identity mismatch");
        String marker = required(p, "broken");
        if (!marker.equals("true") && !marker.equals("false")) throw new IOException("Invalid broken marker");
        AscensionSession session = new AscensionSession(UUID.fromString(required(p, "id")), storedKey,
                Phase.valueOf(required(p, "phase")), Boolean.parseBoolean(marker),
                parseLong(p, "phaseRevision"), parseLong(p, "stateRevision"),
                parseLong(p, "sequence"), parseLong(p, "serverTick"));

        AscensionProgress.Qi qi = new AscensionProgress.Qi(
                parseDouble(p, "heavenQi"), parseDouble(p, "earthQi"), parseDouble(p, "humanQi"),
                parseDouble(p, "ambientHeaven"), parseDouble(p, "ambientEarth"),
                parseDouble(p, "humanReserve"));
        int earned = parseInt(p, "insightEarned");
        int spent = parseInt(p, "insightSpent");
        int count = parseInt(p, "effect.count");
        if (count < 0 || count > AscensionProgress.MAX_EFFECTS) throw new IOException("Invalid effect count");
        for (String name : p.stringPropertyNames()) {
            if (!name.startsWith("effect.") || name.equals("effect.count")) continue;
            try {
                int index = Integer.parseInt(name.substring("effect.".length()));
                if (index < 0 || index >= count || !name.equals("effect." + index)) {
                    throw new IOException("Unexpected effect entry");
                }
            } catch (NumberFormatException ex) {
                throw new IOException("Malformed effect entry", ex);
            }
        }
        Set<String> effects = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            String effect = required(p, "effect." + i);
            if (!effects.add(effect)) throw new IOException("Duplicate effect key");
        }
        String draft = p.getProperty("apertureDraft");
        if (draft == null) throw new IOException("Missing session property: apertureDraft");
        AscensionProgress.Risk risk = AscensionProgress.Risk.empty();
        AscensionProgress.Window window = AscensionProgress.Window.empty();
        if (withDynamics) {
            risk = new AscensionProgress.Risk(
                    parseDouble(p, "riskHeaven"), parseDouble(p, "riskEarth"), parseDouble(p, "riskHuman"));
            int ticks = parseInt(p, "windowTicks");
            String windowId = p.getProperty("windowId");
            if (windowId == null) throw new IOException("Missing session property: windowId");
            window = new AscensionProgress.Window(ticks, windowId);
        }
        AscensionProgress.Aperture aperture = AscensionProgress.Aperture.empty();
        if (withAperture) {
            aperture = new AscensionProgress.Aperture(
                    parseDouble(p, "apertureAreaMu"), parseDouble(p, "apertureTimeRatio"),
                    parseDouble(p, "apertureEssence"), parseLong(p, "apertureInternalTicks"),
                    parseInt(p, "aperturePlot"));
        }
        AscensionProgress.VitalGu vitalGu = AscensionProgress.VitalGu.empty();
        if (withVitalGu) {
            vitalGu = new AscensionProgress.VitalGu(p.getProperty("vitalGuFingerprint", ""),
                    p.getProperty("vitalGuName", ""));
        }
        List<AscensionProgress.Tribulation> tribulations = new ArrayList<>();
        if (withTribulation) {
            if (withMultipleTribulations) {
                int tribulationCount = parseInt(p, "tribulation.count");
                if (tribulationCount < 0 || tribulationCount > AscensionProgress.MAX_TRIBULATIONS) {
                    throw new IOException("Invalid tribulation count");
                }
                for (int i = 0; i < tribulationCount; i++) {
                    String prefix = "tribulation." + i + ".";
                    AscensionProgress.CarrierAnchor anchor = AscensionProgress.CarrierAnchor.unplaced();
                    if (withCarrierAnchors) {
                        String entity = p.getProperty(prefix + "entity");
                        String dimension = p.getProperty(prefix + "dimension");
                        if (entity == null || dimension == null) throw new IOException("Missing carrier anchor property");
                        anchor = new AscensionProgress.CarrierAnchor(dimension,
                                parseInt(p, prefix + "x"), parseInt(p, prefix + "y"), parseInt(p, prefix + "z"),
                                entity.isEmpty() ? null : UUID.fromString(entity));
                    }
                    tribulations.add(new AscensionProgress.Tribulation(
                            required(p, prefix + "id"),
                            AscensionProgress.Tribulation.Type.valueOf(required(p, prefix + "type")),
                            parseInt(p, prefix + "remaining"),
                            parseInt(p, prefix + "ticks"), anchor));
                }
            } else {
                String type = required(p, "tribulationType");
                AscensionProgress.Tribulation tribulation = new AscensionProgress.Tribulation(
                        p.getProperty("tribulationId", ""),
                        AscensionProgress.Tribulation.Type.valueOf(type),
                        parseInt(p, "tribulationRemaining"),
                        parseInt(p, "tribulationTicks"));
                if (!tribulation.active() && !type.equals("NONE")) throw new IOException("Invalid tribulation state");
                if (tribulation.active()) tribulations.add(tribulation);
            }
        }
        TribulationParameters.Profile tribulationProfile = TribulationParameters.Profile.UNRESOLVED_ORDINARY;
        double tribulationPressure = 0;
        long tribulationImpactSequence = 0;
        java.util.Optional<AscensionProgress.TribulationImpactFailure> impactFailure = java.util.Optional.empty();
        if (withImpactState) {
            try {
                tribulationProfile = TribulationParameters.Profile.valueOf(required(p, "tribulationProfile"));
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid tribulation profile", ex);
            }
            tribulationPressure = parseDouble(p, "tribulationPressure");
            tribulationImpactSequence = parseLong(p, "tribulationImpactSequence");
            String failurePresent = required(p, "tribulationImpactFailurePresent");
            if (!failurePresent.equals("true") && !failurePresent.equals("false")) {
                throw new IOException("Invalid tribulation impact failure marker");
            }
            boolean hasImpactFailure = Boolean.parseBoolean(failurePresent);
            Set<String> impactFailureFields = Set.of(
                    "tribulationImpactFailure.profile",
                    "tribulationImpactFailure.carrierType",
                    "tribulationImpactFailure.carrierId",
                    "tribulationImpactFailure.sequence");
            for (String name : p.stringPropertyNames()) {
                if (name.startsWith("tribulationImpactFailure.")
                        && (!hasImpactFailure || !impactFailureFields.contains(name))) {
                    throw new IOException("Unexpected tribulation impact failure field");
                }
            }
            if (hasImpactFailure) {
                try {
                    impactFailure = java.util.Optional.of(new AscensionProgress.TribulationImpactFailure(
                            TribulationParameters.Profile.valueOf(required(p, "tribulationImpactFailure.profile")),
                            AscensionProgress.Tribulation.Type.valueOf(required(p, "tribulationImpactFailure.carrierType")),
                            required(p, "tribulationImpactFailure.carrierId"),
                            parseLong(p, "tribulationImpactFailure.sequence")));
                } catch (IllegalArgumentException ex) {
                    throw new IOException("Invalid tribulation impact failure context", ex);
                }
            }
        }
        AscensionProgress progress = new AscensionProgress(qi, risk, window, tribulations,
                aperture, vitalGu, earned, spent, effects, draft, tribulationProfile,
                tribulationPressure, tribulationImpactSequence, impactFailure);
        try {
            var shatterClock = progress.shatterClock();
            if (shatterClock.isPresent() && shatterClock.get().phaseRevision() > session.phaseRevision()) {
                throw new IOException("Shatter clock revision is newer than its session");
            }
            if (session.phase() == Phase.BREAKING && shatterClock.isPresent()
                    && progress.shatterClock(session.phaseRevision()).isEmpty()) {
                throw new IOException("Shatter clock does not match the current attempt");
            }
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new IOException("Malformed shatter clock record", ex);
        }
        if (session.phase() == Phase.BREAKING && !progress.shatterEssenceTargetKeys().isEmpty()
                && progress.shatterEssenceTarget(session.phaseRevision()).isEmpty()) {
            throw new IOException("Shatter target does not match the current attempt; record preserved");
        }
        return new AscensionRecord(session, progress);
    }

    @Override public synchronized void save(AscensionRecord record, long expectedStateRevision) throws IOException {
        try (var channel = FileChannel.open(directory.resolve(".writer.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            Optional<AscensionRecord> previous = find(record.session().key());
            if (expectedStateRevision == -1) {
                if (previous.isPresent()) throw new IOException("Session already exists");
                validateCreate(record);
            } else {
                AscensionRecord old = previous.orElseThrow(() -> new IOException("Session missing"));
                validateUpdate(old, record, expectedStateRevision);
            }
            write(record);
        }
    }

    private static void validateCreate(AscensionRecord record) throws IOException {
        AscensionSession s = record.session();
        if (s.phase() != Phase.PREPARING || s.broken() || s.phaseRevision() != 0
                || s.stateRevision() != 0 || s.lastActionSequence() != 0) {
            throw new IOException("Invalid initial state");
        }
        AscensionProgress initial = AscensionProgress.empty(record.progress().qi().humanReserve());
        if (record.progress().vitalGu().bound()) {
            initial = initial.withVitalGu(record.progress().vitalGu());
        }
        if (!record.progress().equals(initial)) {
            throw new IOException("Initial state can only carry its bounded human reserve and bound BenMingGu");
        }
    }

    private static void validateUpdate(AscensionRecord old, AscensionRecord next, long expected) throws IOException {
        AscensionSession before = old.session();
        AscensionSession after = next.session();
        if (before.stateRevision() != expected || expected == Long.MAX_VALUE
                || after.stateRevision() != expected + 1) {
            throw new IOException("Stale session write or invalid revision");
        }
        if (!before.id().equals(after.id()) || !before.key().equals(after.key())) {
            throw new IOException("Changed session identity");
        }
        try {
            if (before.phase() == Phase.FAILED && after.phase() == Phase.FAILED) {
                AscensionProgress.FailureSettlementState from = old.progress().failureSettlementState();
                AscensionProgress.FailureSettlementState to = next.progress().failureSettlementState();
                String requiredMarker = requiredFailureTransitionMarker(from, to);
                Set<String> addedEffects = new LinkedHashSet<>(next.progress().appliedEffects());
                addedEffects.removeAll(old.progress().appliedEffects());
                if (!SessionFlow.acknowledgeFailure(before, expected, after.lastActionSequence(), after.serverTick()).equals(after)
                        || old.progress().failureCauses().isEmpty()
                        || !old.progress().failureCauses().equals(next.progress().failureCauses())
                        || !old.progress().sameExceptFailureSettlement(next.progress())
                        || !addedEffects.equals(Set.of(requiredMarker))) {
                    throw new IOException("Failed session settlement transition is invalid");
                }
            } else if (before.phase() == after.phase()) {
                if (!SessionFlow.update(before, expected, after.lastActionSequence(), after.serverTick()).equals(after)) {
                    throw new IOException("Session update violates flow");
                }
            } else if (!SessionFlow.advance(before, after.phase(), expected,
                    after.lastActionSequence(), after.serverTick()).equals(after)) {
                throw new IOException("Session update violates flow");
            }
            if (!old.progress().shatterEssenceTargetKeys().equals(next.progress().shatterEssenceTargetKeys())
                    && (before.phase() != Phase.PREPARING || after.phase() != Phase.BREAKING
                    || !next.progress().hasVersionedShatterEssenceTarget(after.phaseRevision()))) {
                throw new IOException("Shatter target can change only when a new attempt begins");
            }
            if (!old.progress().shatterClockKeys().equals(next.progress().shatterClockKeys())) {
                boolean newAttempt = before.phase() == Phase.PREPARING && after.phase() == Phase.BREAKING
                        && next.progress().shatterClock(after.phaseRevision()).isPresent();
                boolean activeTimer = before.phase() == Phase.BREAKING && after.phase() == Phase.BREAKING
                        && next.progress().shatterClock(after.phaseRevision()).isPresent();
                if (!newAttempt && !activeTimer) {
                    throw new IOException("Shatter clock can change only when an attempt begins or resumes");
                }
            }
            if (before.phase() == Phase.IMMORTAL
                    && (!old.progress().aperture().formed()
                    || !next.progress().aperture().formed()
                    || !next.progress().withXianGu(old.progress().xianGu()).sameNonApertureState(old.progress()))) {
                throw new IOException("IMMORTAL session may update only a formed aperture ledger");
            }
            if (!old.progress().xianGu().equals(next.progress().xianGu()) && before.phase() != Phase.IMMORTAL) {
                throw new IOException("Reusable immortal Gu state requires a committed IMMORTAL session");
            }
            next.progress().requireMonotonicFrom(old.progress());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new IOException("Session update violates flow", ex);
        }
    }

    private static String requiredFailureTransitionMarker(AscensionProgress.FailureSettlementState from,
                                                           AscensionProgress.FailureSettlementState to) throws IOException {
        if (from == AscensionProgress.FailureSettlementState.PREPARED
                && to == AscensionProgress.FailureSettlementState.STARTED) {
            return AscensionProgress.FAILURE_STARTED_EFFECT;
        }
        if ((from == AscensionProgress.FailureSettlementState.PREPARED
                || from == AscensionProgress.FailureSettlementState.STARTED)
                && to == AscensionProgress.FailureSettlementState.UNCERTAIN) {
            return AscensionProgress.FAILURE_UNCERTAIN_EFFECT;
        }
        if ((from == AscensionProgress.FailureSettlementState.STARTED
                || from == AscensionProgress.FailureSettlementState.UNCERTAIN)
                && to == AscensionProgress.FailureSettlementState.RESPAWN_RECEIPT) {
            return AscensionProgress.FAILURE_RESPAWN_RECEIPT_EFFECT;
        }
        if (from == AscensionProgress.FailureSettlementState.RESPAWN_RECEIPT
                && to == AscensionProgress.FailureSettlementState.ACKNOWLEDGED) {
            return AscensionProgress.FAILURE_ACKNOWLEDGED_EFFECT;
        }
        throw new IOException("Illegal failure settlement transition: " + from + " -> " + to);
    }

    private void write(AscensionRecord record) throws IOException {
        AscensionSession session = record.session();
        AscensionProgress progress = record.progress();
        Properties p = new Properties();
        p.setProperty("schema", SCHEMA_VERSION);
        XianGuStateCodec.write(p, record.progress().xianGu());
        p.setProperty("id", session.id().toString());
        p.setProperty("owner", session.key().owner().toString());
        p.setProperty("aperture", session.key().apertureId());
        p.setProperty("phase", session.phase().name());
        p.setProperty("broken", Boolean.toString(session.broken()));
        p.setProperty("phaseRevision", Long.toString(session.phaseRevision()));
        p.setProperty("stateRevision", Long.toString(session.stateRevision()));
        p.setProperty("sequence", Long.toString(session.lastActionSequence()));
        p.setProperty("serverTick", Long.toString(session.serverTick()));
        p.setProperty("heavenQi", Double.toString(progress.qi().heaven()));
        p.setProperty("earthQi", Double.toString(progress.qi().earth()));
        p.setProperty("humanQi", Double.toString(progress.qi().human()));
        p.setProperty("ambientHeaven", Double.toString(progress.qi().ambientHeaven()));
        p.setProperty("ambientEarth", Double.toString(progress.qi().ambientEarth()));
        p.setProperty("humanReserve", Double.toString(progress.qi().humanReserve()));
        p.setProperty("riskHeaven", Double.toString(progress.risk().heaven()));
        p.setProperty("riskEarth", Double.toString(progress.risk().earth()));
        p.setProperty("riskHuman", Double.toString(progress.risk().human()));
        p.setProperty("windowTicks", Integer.toString(progress.window().ticks()));
        p.setProperty("windowId", progress.window().id());
        AscensionProgress.Tribulation primary = progress.tribulation();
        p.setProperty("tribulationId", primary.id());
        p.setProperty("tribulationType", primary.type().name());
        p.setProperty("tribulationRemaining", Integer.toString(primary.remaining()));
        p.setProperty("tribulationTicks", Integer.toString(primary.ticksUntilImpact()));
        p.setProperty("tribulation.count", Integer.toString(progress.tribulations().size()));
        for (int i = 0; i < progress.tribulations().size(); i++) {
            AscensionProgress.Tribulation carrier = progress.tribulations().get(i);
            String prefix = "tribulation." + i + ".";
            p.setProperty(prefix + "id", carrier.id());
            p.setProperty(prefix + "type", carrier.type().name());
            p.setProperty(prefix + "remaining", Integer.toString(carrier.remaining()));
            p.setProperty(prefix + "ticks", Integer.toString(carrier.ticksUntilImpact()));
            AscensionProgress.CarrierAnchor anchor = carrier.carrierAnchor();
            p.setProperty(prefix + "dimension", anchor.dimension());
            p.setProperty(prefix + "x", Integer.toString(anchor.x()));
            p.setProperty(prefix + "y", Integer.toString(anchor.y()));
            p.setProperty(prefix + "z", Integer.toString(anchor.z()));
            p.setProperty(prefix + "entity", anchor.entityId() == null ? "" : anchor.entityId().toString());
        }
        p.setProperty("apertureAreaMu", Double.toString(progress.aperture().areaMu()));
        p.setProperty("apertureTimeRatio", Double.toString(progress.aperture().timeRatio()));
        p.setProperty("apertureEssence", Double.toString(progress.aperture().essence()));
        p.setProperty("apertureInternalTicks", Long.toString(progress.aperture().internalTicks()));
        p.setProperty("aperturePlot", Integer.toString(progress.aperture().plotIndex()));
        p.setProperty("vitalGuFingerprint", progress.vitalGu().fingerprint());
        p.setProperty("vitalGuName", progress.vitalGu().name());
        p.setProperty("insightEarned", Integer.toString(progress.insightEarned()));
        p.setProperty("insightSpent", Integer.toString(progress.insightSpent()));
        List<String> effects = new ArrayList<>(progress.appliedEffects());
        Collections.sort(effects);
        p.setProperty("effect.count", Integer.toString(effects.size()));
        for (int i = 0; i < effects.size(); i++) p.setProperty("effect." + i, effects.get(i));
        p.setProperty("apertureDraft", progress.apertureDraftId());
        p.setProperty("tribulationProfile", progress.tribulationProfile().name());
        p.setProperty("tribulationPressure", Double.toString(progress.tribulationPressure()));
        p.setProperty("tribulationImpactSequence", Long.toString(progress.tribulationImpactSequence()));
        p.setProperty("tribulationImpactFailurePresent", Boolean.toString(progress.tribulationImpactFailure().isPresent()));
        progress.tribulationImpactFailure().ifPresent(failure -> {
            p.setProperty("tribulationImpactFailure.profile", failure.profile().name());
            p.setProperty("tribulationImpactFailure.carrierType", failure.carrierType().name());
            p.setProperty("tribulationImpactFailure.carrierId", failure.carrierId());
            p.setProperty("tribulationImpactFailure.sequence", Long.toString(failure.impactSequence()));
        });

        Path temp = Files.createTempFile(directory, "session-", ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) { p.store(out, "Shengxian framework schema 11"); }
            if (Files.size(temp) > MAX_RECORD_BYTES) throw new IOException("Oversized session record");
            try (var data = FileChannel.open(temp, StandardOpenOption.WRITE)) { data.force(true); }
            Files.move(temp, path(session.key()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
