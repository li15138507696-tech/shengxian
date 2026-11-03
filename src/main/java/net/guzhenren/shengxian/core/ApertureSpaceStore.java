package net.guzhenren.shengxian.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * World-bound records for aperture plots, guest permissions and player return points.
 * Formed session claims are reconciled into reservations before allocation starts. Unclaimed
 * reservations and built plots remain occupied until an explicit safe recovery is implemented.
 */
public final class ApertureSpaceStore {
    private static final long MAX_RECORD_BYTES = 262_144;
    /** Last 16x16 plot whose origin stays within the vanilla world coordinate limit. */
    public static final int MAX_PLOT_INDEX = 7_500_031;

    private final Path file;
    private final Set<Integer> builtPlots = new TreeSet<>();
    private final Set<Integer> reservedPlots = new TreeSet<>();
    private final Map<Integer, PlotReservation> plotReservations = new HashMap<>();
    private final Map<UUID, ReturnPoint> returns = new HashMap<>();
    private final Map<Integer, Set<UUID>> guests = new HashMap<>();
    private final Map<Integer, Set<UUID>> guardians = new HashMap<>();
    private final Map<UUID, Set<UUID>> ascensionGuardians = new HashMap<>();

    public record ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch) {
        public ReturnPoint {
            if (dimension == null || dimension.isBlank()) throw new IllegalArgumentException("Invalid return dimension");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new IllegalArgumentException("Invalid return position");
            }
        }
    }

    /** Durable owner and session identity for a plot reserved before aperture commit. */
    public record PlotReservation(UUID owner, String apertureId, UUID sessionId, boolean committed) {
        public PlotReservation {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(apertureId, "apertureId");
            Objects.requireNonNull(sessionId, "sessionId");
            if (apertureId.isBlank() || apertureId.length() > 64) {
                throw new IllegalArgumentException("Invalid reservation aperture ID");
            }
        }

        private boolean belongsTo(ApertureKey key, UUID session) {
            return owner.equals(key.owner()) && apertureId.equals(key.apertureId()) && sessionId.equals(session);
        }
    }

    public ApertureSpaceStore(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        if (Files.exists(this.file)) {
            if (Files.size(this.file) > MAX_RECORD_BYTES) throw new IOException("Oversized aperture space record");
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(this.file)) { p.load(in); }
            catch (IllegalArgumentException ex) { throw new IOException("Malformed aperture space record", ex); }
            for (String name : p.stringPropertyNames()) {
                if (name.startsWith("built.")) {
                    builtPlots.add(readPlotEntry(p, name, "built."));
                } else if (name.startsWith("reserved.")) {
                    reservedPlots.add(readPlotEntry(p, name, "reserved."));
                } else if (name.startsWith("ascensionGuardian.")) {
                    String[] parts = name.split("\\.");
                    if (parts.length != 3 || !"true".equals(p.getProperty(name))) {
                        throw new IOException("Malformed ascension guardian entry");
                    }
                    try {
                        UUID owner = UUID.fromString(parts[1]);
                        UUID guardian = UUID.fromString(parts[2]);
                        if (owner.equals(guardian)) throw new IOException("Owner cannot guard their own ascension");
                        ascensionGuardians.computeIfAbsent(owner, k -> new TreeSet<>()).add(guardian);
                    } catch (IllegalArgumentException ex) {
                        throw new IOException("Invalid ascension guardian entry", ex);
                    }
                } else if (name.startsWith("guardian.")) {
                    String[] parts = name.split("\\.");
                    if (parts.length != 3) throw new IOException("Malformed guardian entry");
                    try {
                        int plot = Integer.parseInt(parts[1]);
                        if (!isValidPlot(plot) || !"true".equals(p.getProperty(name))) throw new IOException("Invalid guardian plot");
                        guardians.computeIfAbsent(plot, k -> new TreeSet<>()).add(UUID.fromString(parts[2]));
                    } catch (IllegalArgumentException ex) { throw new IOException("Invalid guardian entry", ex); }
                } else if (name.startsWith("guest.")) {
                    String[] parts = name.split("\\.");
                    if (parts.length != 3) throw new IOException("Malformed guest entry");
                    try {
                        int plot = Integer.parseInt(parts[1]);
                        if (!isValidPlot(plot) || !"true".equals(p.getProperty(name))) throw new IOException("Invalid guest plot");
                        guests.computeIfAbsent(plot, k -> new TreeSet<>()).add(UUID.fromString(parts[2]));
                    } catch (IllegalArgumentException ex) {
                        throw new IOException("Invalid guest entry", ex);
                    }
                }
            }
            readPlotReservations(p);
            for (String name : p.stringPropertyNames()) {
                if (!name.startsWith("return.")) continue;
                String[] parts = name.split("\\.");
                if (parts.length != 3) throw new IOException("Malformed return entry");
                UUID owner;
                try { owner = UUID.fromString(parts[1]); }
                catch (IllegalArgumentException ex) { throw new IOException("Invalid return owner", ex); }
                if (returns.containsKey(owner)) continue;
                String prefix = "return." + parts[1] + ".";
                String dimension = p.getProperty(prefix + "dimension");
                if (dimension == null) throw new IOException("Missing return dimension");
                try {
                    returns.put(owner, new ReturnPoint(dimension,
                            Double.parseDouble(required(p, prefix + "x")),
                            Double.parseDouble(required(p, prefix + "y")),
                            Double.parseDouble(required(p, prefix + "z")),
                            Float.parseFloat(required(p, prefix + "yaw")),
                            Float.parseFloat(required(p, prefix + "pitch"))));
                } catch (IllegalArgumentException ex) {
                    throw new IOException("Invalid return point", ex);
                }
            }
        } else {
            Files.createDirectories(this.file.getParent());
        }
    }

    private void readPlotReservations(Properties properties) throws IOException {
        Map<Integer, Map<String, String>> fieldsByPlot = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith("plotReservation.")) continue;
            String[] parts = name.split("\\.");
            if (parts.length != 3) throw new IOException("Malformed plot reservation entry");
            int plot;
            try {
                plot = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid plot reservation index", ex);
            }
            if (!isValidPlot(plot) || !reservedPlots.contains(plot)) {
                throw new IOException("Plot reservation is not backed by a reserved plot");
            }
            if (!Set.of("owner", "aperture", "session", "committed").contains(parts[2])) {
                throw new IOException("Unknown plot reservation field");
            }
            fieldsByPlot.computeIfAbsent(plot, ignored -> new HashMap<>())
                    .put(parts[2], properties.getProperty(name));
        }
        for (Map.Entry<Integer, Map<String, String>> entry : fieldsByPlot.entrySet()) {
            Map<String, String> fields = entry.getValue();
            if (!fields.keySet().equals(Set.of("owner", "aperture", "session", "committed"))) {
                throw new IOException("Incomplete plot reservation identity");
            }
            try {
                UUID owner = UUID.fromString(fields.get("owner"));
                UUID session = UUID.fromString(fields.get("session"));
                String apertureId = new String(Base64.getUrlDecoder().decode(fields.get("aperture")),
                        StandardCharsets.UTF_8);
                String committedValue = fields.get("committed");
                if (!"true".equals(committedValue) && !"false".equals(committedValue)) {
                    throw new IOException("Invalid plot reservation commit state");
                }
                PlotReservation reservation = new PlotReservation(owner, apertureId, session,
                        Boolean.parseBoolean(committedValue));
                boolean duplicateIdentity = plotReservations.values().stream().anyMatch(existing ->
                        existing.owner().equals(owner) && existing.sessionId().equals(session));
                if (duplicateIdentity) throw new IOException("Duplicate owner/session plot reservation");
                plotReservations.put(entry.getKey(), reservation);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid plot reservation identity", ex);
            }
        }
    }

    public static boolean isValidPlot(int plot) {
        return plot >= 0 && plot <= MAX_PLOT_INDEX;
    }

    private static int readPlotEntry(Properties properties, String name, String prefix) throws IOException {
        try {
            int plot = Integer.parseInt(name.substring(prefix.length()));
            if (!isValidPlot(plot) || !"true".equals(properties.getProperty(name))) {
                throw new IOException("Invalid plot entry: " + name);
            }
            return plot;
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid plot entry: " + name, ex);
        }
    }

    public synchronized boolean isBuilt(int plot) {
        return builtPlots.contains(plot);
    }

    public synchronized boolean isReserved(int plot) {
        return reservedPlots.contains(plot);
    }

    public synchronized Set<Integer> reservedPlots() {
        return Set.copyOf(reservedPlots);
    }

    public synchronized Set<Integer> builtPlots() {
        return Set.copyOf(builtPlots);
    }

    /** Adds formed session plots without ever discarding orphan reservations or built plots. */
    public synchronized void reconcileReservations(Set<Integer> claimedPlots) throws IOException {
        Objects.requireNonNull(claimedPlots, "claimedPlots");
        Set<Integer> additions = new TreeSet<>();
        for (Integer plot : claimedPlots) {
            if (plot == null || !isValidPlot(plot)) throw new IOException("Invalid claimed plot");
            if (!reservedPlots.contains(plot)) additions.add(plot);
        }
        if (additions.isEmpty()) return;
        reservedPlots.addAll(additions);
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            reservedPlots.removeAll(additions);
            throw ex;
        }
    }

    public synchronized void markBuilt(int plot) throws IOException {
        requireValidPlot(plot);
        if (!builtPlots.add(plot)) return;
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            builtPlots.remove(plot);
            throw ex;
        }
    }

    /** Reuses a pending plot for the same durable ascension session after a restart. */
    public synchronized int reservePlotForAscension(ApertureKey key, UUID sessionId) throws IOException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(sessionId, "sessionId");
        for (Map.Entry<Integer, PlotReservation> entry : plotReservations.entrySet()) {
            PlotReservation reservation = entry.getValue();
            if (reservation.owner().equals(key.owner()) && reservation.sessionId().equals(sessionId)) {
                if (!reservation.apertureId().equals(key.apertureId())) {
                    throw new IOException("Ascension session is already bound to another aperture");
                }
                if (reservation.committed()) {
                    throw new IOException("A committed aperture plot cannot be reserved again");
                }
                if (!reservedPlots.contains(entry.getKey())) {
                    throw new IOException("Pending plot reservation lost its reserved marker");
                }
                return entry.getKey();
            }
        }
        int plot = 0;
        while (builtPlots.contains(plot) || reservedPlots.contains(plot)) {
            if (plot == MAX_PLOT_INDEX) throw new IOException("No aperture plots available");
            plot++;
        }
        PlotReservation reservation = new PlotReservation(key.owner(), key.apertureId(), sessionId, false);
        reservedPlots.add(plot);
        plotReservations.put(plot, reservation);
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            reservedPlots.remove(plot);
            plotReservations.remove(plot);
            throw ex;
        }
        return plot;
    }

    /** Marks the reservation committed only after the durable formation claim exists. */
    public synchronized void commitPlotReservation(int plot, ApertureKey key, UUID sessionId) throws IOException {
        requireValidPlot(plot);
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(sessionId, "sessionId");
        if (!reservedPlots.contains(plot)) throw new IOException("Formed aperture plot is not reserved");
        for (Map.Entry<Integer, PlotReservation> entry : plotReservations.entrySet()) {
            PlotReservation other = entry.getValue();
            if (other.owner().equals(key.owner()) && other.sessionId().equals(sessionId)
                    && entry.getKey() != plot) {
                throw new IOException("Formation claim does not match its reserved plot");
            }
        }
        PlotReservation current = plotReservations.get(plot);
        if (current != null && !current.belongsTo(key, sessionId)) {
            throw new IOException("Plot reservation belongs to another ascension session");
        }
        if (current != null && current.committed()) return;
        PlotReservation committed = new PlotReservation(key.owner(), key.apertureId(), sessionId, true);
        plotReservations.put(plot, committed);
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            if (current == null) plotReservations.remove(plot);
            else plotReservations.put(plot, current);
            throw ex;
        }
    }

    public synchronized Optional<PlotReservation> reservation(int plot) {
        return Optional.ofNullable(plotReservations.get(plot));
    }

    /** Reserves the smallest unused plot; a crash leaves the reservation recorded. */
    public synchronized int reservePlot() throws IOException {
        int plot = 0;
        while (builtPlots.contains(plot) || reservedPlots.contains(plot)) {
            if (plot == MAX_PLOT_INDEX) throw new IOException("No aperture plots available");
            plot++;
        }
        reservedPlots.add(plot);
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            reservedPlots.remove(plot);
            throw ex;
        }
        return plot;
    }

    /** Explicit release only; formation failure must retain uncertain reservations. */
    public synchronized void releasePlot(int plot) throws IOException {
        requireValidPlot(plot);
        if (!reservedPlots.remove(plot)) return;
        PlotReservation previous = plotReservations.remove(plot);
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            reservedPlots.add(plot);
            if (previous != null) plotReservations.put(plot, previous);
            throw ex;
        }
    }

    public synchronized boolean isGuardian(int plot, UUID player) {
        Set<UUID> set = guardians.get(plot);
        return set != null && set.contains(player);
    }

    public synchronized void addGuardian(int plot, UUID player) throws IOException {
        requireValidPlot(plot);
        Objects.requireNonNull(player, "player");
        Set<UUID> set = guardians.computeIfAbsent(plot, k -> new TreeSet<>());
        if (!set.add(player)) return;
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            set.remove(player);
            if (set.isEmpty()) guardians.remove(plot, set);
            throw ex;
        }
    }

    public synchronized void removeGuardian(int plot, UUID player) throws IOException {
        Set<UUID> set = guardians.get(plot);
        if (set == null || !set.remove(player)) return;
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            set.add(player);
            throw ex;
        }
    }

    /** Owner-scoped invitations usable during ascension, before an aperture plot exists. */
    public synchronized boolean isAscensionGuardian(UUID owner, UUID player) {
        Set<UUID> set = ascensionGuardians.get(Objects.requireNonNull(owner, "owner"));
        return set != null && set.contains(Objects.requireNonNull(player, "player"));
    }

    public synchronized void addAscensionGuardian(UUID owner, UUID player) throws IOException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(player, "player");
        if (owner.equals(player)) throw new IllegalArgumentException("Owner cannot guard their own ascension");
        Set<UUID> set = ascensionGuardians.computeIfAbsent(owner, k -> new TreeSet<>());
        if (!set.add(player)) return;
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            set.remove(player);
            if (set.isEmpty()) ascensionGuardians.remove(owner, set);
            throw ex;
        }
    }

    public synchronized void removeAscensionGuardian(UUID owner, UUID player) throws IOException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(player, "player");
        Set<UUID> set = ascensionGuardians.get(owner);
        if (set == null || !set.remove(player)) return;
        try {
            writeAll();
            if (set.isEmpty()) ascensionGuardians.remove(owner, set);
        } catch (IOException | RuntimeException ex) {
            set.add(player);
            throw ex;
        }
    }

    /** Atomically grants the ascension invitation and, when present, its formed-plot permission. */
    public synchronized void addGuardianForAscension(UUID owner, int plot, UUID player) throws IOException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(player, "player");
        if (owner.equals(player)) throw new IllegalArgumentException("Owner cannot guard their own ascension");
        if (plot != -1) requireValidPlot(plot);
        Set<UUID> ownerSet = ascensionGuardians.computeIfAbsent(owner, k -> new TreeSet<>());
        boolean addedOwner = ownerSet.add(player);
        Set<UUID> plotSet = plot == -1 ? null : guardians.computeIfAbsent(plot, k -> new TreeSet<>());
        boolean addedPlot = plotSet != null && plotSet.add(player);
        if (!addedOwner && !addedPlot) return;
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            if (addedOwner) {
                ownerSet.remove(player);
                if (ownerSet.isEmpty()) ascensionGuardians.remove(owner, ownerSet);
            }
            if (addedPlot) {
                plotSet.remove(player);
                if (plotSet.isEmpty()) guardians.remove(plot, plotSet);
            }
            throw ex;
        }
    }

    /** Copies prior ascension invitations into a newly formed plot with one durable write. */
    public synchronized void reconcileAscensionGuardians(UUID owner, int plot) throws IOException {
        Objects.requireNonNull(owner, "owner");
        requireValidPlot(plot);
        Set<UUID> ownerSet = ascensionGuardians.get(owner);
        if (ownerSet == null || ownerSet.isEmpty()) return;
        Set<UUID> plotSet = guardians.computeIfAbsent(plot, k -> new TreeSet<>());
        Set<UUID> added = new TreeSet<>();
        for (UUID guardian : ownerSet) if (plotSet.add(guardian)) added.add(guardian);
        if (added.isEmpty()) return;
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            plotSet.removeAll(added);
            if (plotSet.isEmpty()) guardians.remove(plot, plotSet);
            throw ex;
        }
    }

    /** Atomically revokes both the ascension invitation and its formed-plot permission. */
    public synchronized void removeGuardianForAscension(UUID owner, int plot, UUID player) throws IOException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(player, "player");
        if (plot != -1) requireValidPlot(plot);
        Set<UUID> ownerSet = ascensionGuardians.get(owner);
        Set<UUID> plotSet = plot == -1 ? null : guardians.get(plot);
        boolean removedOwner = ownerSet != null && ownerSet.remove(player);
        boolean removedPlot = plotSet != null && plotSet.remove(player);
        if (!removedOwner && !removedPlot) return;
        try {
            writeAll();
            if (ownerSet != null && ownerSet.isEmpty()) ascensionGuardians.remove(owner, ownerSet);
            if (plotSet != null && plotSet.isEmpty()) guardians.remove(plot, plotSet);
        } catch (IOException | RuntimeException ex) {
            if (removedOwner) ownerSet.add(player);
            if (removedPlot) plotSet.add(player);
            throw ex;
        }
    }

    public synchronized boolean isGuest(int plot, UUID player) {
        Set<UUID> set = guests.get(plot);
        return set != null && set.contains(player);
    }

    public synchronized void addGuest(int plot, UUID player) throws IOException {
        requireValidPlot(plot);
        Objects.requireNonNull(player, "player");
        Set<UUID> set = guests.computeIfAbsent(plot, k -> new TreeSet<>());
        if (!set.add(player)) return;
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            set.remove(player);
            if (set.isEmpty()) guests.remove(plot, set);
            throw ex;
        }
    }

    public synchronized void removeGuest(int plot, UUID player) throws IOException {
        Set<UUID> set = guests.get(plot);
        if (set == null || !set.remove(player)) return;
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            set.add(player);
            throw ex;
        }
    }

    public synchronized ReturnPoint returnPoint(UUID owner) {
        return returns.get(owner);
    }

    public synchronized void rememberReturn(UUID owner, ReturnPoint point) throws IOException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(point, "point");
        ReturnPoint previous = returns.put(owner, point);
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            if (previous == null) returns.remove(owner);
            else returns.put(owner, previous);
            throw ex;
        }
    }

    private static void requireValidPlot(int plot) {
        if (!isValidPlot(plot)) throw new IllegalArgumentException("Invalid plot");
    }
    private void writeAll() throws IOException {
        Properties p = new Properties();
        for (int built : builtPlots) p.setProperty("built." + built, "true");
        for (int reserved : reservedPlots) p.setProperty("reserved." + reserved, "true");
        for (Map.Entry<Integer, PlotReservation> entry : plotReservations.entrySet()) {
            PlotReservation reservation = entry.getValue();
            String prefix = "plotReservation." + entry.getKey() + ".";
            p.setProperty(prefix + "owner", reservation.owner().toString());
            p.setProperty(prefix + "aperture", Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(reservation.apertureId().getBytes(StandardCharsets.UTF_8)));
            p.setProperty(prefix + "session", reservation.sessionId().toString());
            p.setProperty(prefix + "committed", Boolean.toString(reservation.committed()));
        }
        for (Map.Entry<UUID, Set<UUID>> e : ascensionGuardians.entrySet()) {
            for (UUID guardian : e.getValue()) {
                p.setProperty("ascensionGuardian." + e.getKey() + "." + guardian, "true");
            }
        }
        for (Map.Entry<Integer, Set<UUID>> e : guardians.entrySet()) {
            for (UUID guardian : e.getValue()) p.setProperty("guardian." + e.getKey() + "." + guardian, "true");
        }
        for (Map.Entry<Integer, Set<UUID>> e : guests.entrySet()) {
            for (UUID guest : e.getValue()) p.setProperty("guest." + e.getKey() + "." + guest, "true");
        }
        for (Map.Entry<UUID, ReturnPoint> e : returns.entrySet()) {
            String prefix = "return." + e.getKey() + ".";
            ReturnPoint point = e.getValue();
            p.setProperty(prefix + "dimension", point.dimension());
            p.setProperty(prefix + "x", Double.toString(point.x()));
            p.setProperty(prefix + "y", Double.toString(point.y()));
            p.setProperty(prefix + "z", Double.toString(point.z()));
            p.setProperty(prefix + "yaw", Float.toString(point.yaw()));
            p.setProperty(prefix + "pitch", Float.toString(point.pitch()));
        }
        Path temp = Files.createTempFile(this.file.getParent(), "aperture-space-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) { p.store(out, "shengxian aperture plots and returns"); }
            if (Files.size(temp) > MAX_RECORD_BYTES) throw new IOException("Oversized aperture space record");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temp, this.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String required(Properties p, String key) throws IOException {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing aperture space property: " + key);
        return value;
    }
}
