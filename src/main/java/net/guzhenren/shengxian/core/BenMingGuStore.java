package net.guzhenren.shengxian.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * World-bound index with one BenMingGu fingerprint per owner. Fingerprints describe stack
 * type and components, not globally unique Gu instances; this index never moves or mutates
 * the upstream item.
 */
public final class BenMingGuStore {
    private final Path file;
    private final Map<UUID, Binding> bindings = new HashMap<>();
    private final Map<UUID, Consumption> consumed = new HashMap<>();
    private final Map<UUID, Escrow> escrows = new HashMap<>();

    public enum EscrowState { OFFER_PENDING, STORED, EMBEDDED, RETURN_PENDING, RETURNED }

    /** Durable copy and transfer state for the one real vital-Gu stack. */
    public record Escrow(String fingerprint, String displayName, String itemSnbt, EscrowState state) {
        public Escrow {
            if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid escrowed BenMingGu fingerprint");
            }
            if (displayName == null || displayName.isBlank() || displayName.length() > 128) {
                throw new IllegalArgumentException("Invalid escrowed BenMingGu name");
            }
            if (itemSnbt == null || itemSnbt.isBlank() || itemSnbt.length() > 131_072) {
                throw new IllegalArgumentException("Invalid escrowed BenMingGu stack");
            }
            if (state == null) throw new IllegalArgumentException("Missing BenMingGu escrow state");
        }
    }

    public record Consumption(String fingerprint, String displayName) {
        public Consumption {
            if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid consumed BenMingGu fingerprint");
            }
            if (displayName == null || displayName.isBlank() || displayName.length() > 128) {
                throw new IllegalArgumentException("Invalid consumed BenMingGu name");
            }
        }
    }

    public record Binding(String fingerprint, String displayName) {
        public Binding {
            if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid BenMingGu fingerprint");
            }
            if (displayName == null || displayName.isBlank() || displayName.length() > 128) {
                throw new IllegalArgumentException("Invalid BenMingGu name");
            }
        }
    }

    public BenMingGuStore(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        if (Files.exists(this.file)) {
            if (Files.size(this.file) > 2_097_152) throw new IOException("Oversized BenMingGu record");
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(this.file)) { p.load(in); }
            catch (IllegalArgumentException ex) { throw new IOException("Malformed BenMingGu record", ex); }
            for (String name : p.stringPropertyNames()) {
                if (name.startsWith("escrow.")) {
                    String[] parts = name.split("\\.");
                    if (parts.length != 3) throw new IOException("Malformed BenMingGu escrow entry");
                    UUID owner;
                    try { owner = UUID.fromString(parts[1]); }
                    catch (IllegalArgumentException ex) { throw new IOException("Invalid escrow owner", ex); }
                    if (escrows.containsKey(owner)) continue;
                    String prefix = "escrow." + parts[1] + ".";
                    try {
                        escrows.put(owner, new Escrow(required(p, prefix + "fingerprint"),
                                required(p, prefix + "name"), required(p, prefix + "item"),
                                EscrowState.valueOf(required(p, prefix + "state"))));
                    } catch (IllegalArgumentException ex) {
                        throw new IOException("Invalid BenMingGu escrow", ex);
                    }
                    continue;
                }
                if (name.startsWith("consumed.")) {
                    String[] parts = name.split("\\.");
                    if (parts.length != 3) throw new IOException("Malformed consumed BenMingGu entry");
                    UUID owner;
                    try { owner = UUID.fromString(parts[1]); }
                    catch (IllegalArgumentException ex) { throw new IOException("Invalid consumed BenMingGu owner", ex); }
                    if (consumed.containsKey(owner)) continue;
                    String prefix = "consumed." + parts[1] + ".";
                    try {
                        consumed.put(owner, new Consumption(required(p, prefix + "fingerprint"),
                                required(p, prefix + "name")));
                    } catch (IllegalArgumentException ex) {
                        throw new IOException("Invalid consumed BenMingGu", ex);
                    }
                    continue;
                }
                if (!name.startsWith("binding.")) continue;
                String[] parts = name.split("\\.");
                if (parts.length != 3) throw new IOException("Malformed BenMingGu entry");
                UUID owner;
                try { owner = UUID.fromString(parts[1]); }
                catch (IllegalArgumentException ex) { throw new IOException("Invalid BenMingGu owner", ex); }
                if (bindings.containsKey(owner)) continue;
                String prefix = "binding." + parts[1] + ".";
                try {
                    Binding binding = new Binding(required(p, prefix + "fingerprint"),
                            required(p, prefix + "name"));

                    bindings.put(owner, binding);
                } catch (IllegalArgumentException ex) {
                    throw new IOException("Invalid BenMingGu binding", ex);
                }
            }
        } else {
            Files.createDirectories(this.file.getParent());
        }
    }

    public synchronized Optional<Binding> find(UUID owner) {
        return Optional.ofNullable(bindings.get(owner));
    }

    public synchronized Optional<Escrow> findEscrow(UUID owner) {
        return Optional.ofNullable(escrows.get(owner));
    }

    /** Writes a durable transfer intent and item copy before the inventory stack is removed. */
    public synchronized Escrow beginOffer(UUID owner, String fingerprint, String displayName,
                                          String itemSnbt) throws IOException {
        Escrow next = new Escrow(fingerprint, displayName, itemSnbt, EscrowState.OFFER_PENDING);
        Binding binding = bindings.get(owner);
        if (binding == null || !binding.fingerprint().equals(fingerprint)) {
            throw new IOException("BenMingGu binding does not match offered stack");
        }
        Escrow existing = escrows.get(owner);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) throw new IOException("Different BenMingGu is already escrowed");
            if (existing.state() == EscrowState.RETURNED) return existing;
            if (!existing.itemSnbt().equals(itemSnbt)) throw new IOException("Escrowed BenMingGu payload changed");
            return existing;
        }
        return updateEscrow(owner, null, next);
    }

    public synchronized Escrow markOfferStored(UUID owner) throws IOException {
        Escrow current = requireEscrow(owner);
        if (current.state() != EscrowState.OFFER_PENDING) return current;
        return updateEscrow(owner, current, new Escrow(current.fingerprint(), current.displayName(),
                current.itemSnbt(), EscrowState.STORED));
    }

    /** Records that the original Gu was committed into the formed aperture. */
    public synchronized Escrow markEmbedded(UUID owner) throws IOException {
        Escrow current = requireEscrow(owner);
        if (current.state() == EscrowState.EMBEDDED) return current;
        if (current.state() == EscrowState.RETURN_PENDING || current.state() == EscrowState.RETURNED) {
            throw new IOException("BenMingGu return transaction already started; preserving its legacy outcome");
        }
        if (current.state() != EscrowState.STORED) {
            throw new IOException("BenMingGu must be stored before aperture formation commits");
        }
        return updateEscrow(owner, current, new Escrow(current.fingerprint(), current.displayName(),
                current.itemSnbt(), EscrowState.EMBEDDED));
    }

    /** Begins idempotent delivery after aperture formation. */
    public synchronized Escrow beginReturn(UUID owner) throws IOException {
        Escrow current = requireEscrow(owner);
        if (current.state() == EscrowState.RETURN_PENDING || current.state() == EscrowState.RETURNED) return current;
        if (current.state() == EscrowState.EMBEDDED) {
            throw new IOException("Embedded BenMingGu cannot be returned without a verified canon outcome");
        }
        return updateEscrow(owner, current, new Escrow(current.fingerprint(), current.displayName(),
                current.itemSnbt(), EscrowState.RETURN_PENDING));
    }

    public synchronized Escrow markReturned(UUID owner) throws IOException {
        Escrow current = requireEscrow(owner);
        if (current.state() == EscrowState.RETURNED) return current;
        if (current.state() != EscrowState.RETURN_PENDING) throw new IOException("BenMingGu return was not prepared");
        return updateEscrow(owner, current, new Escrow(current.fingerprint(), current.displayName(),
                current.itemSnbt(), EscrowState.RETURNED));
    }

    private Escrow requireEscrow(UUID owner) throws IOException {
        Escrow current = escrows.get(owner);
        if (current == null) throw new IOException("BenMingGu is not in escrow");
        return current;
    }

    private Escrow updateEscrow(UUID owner, Escrow previous, Escrow next) throws IOException {
        escrows.put(owner, next);
        try { writeAll(); }
        catch (IOException | RuntimeException ex) {
            if (previous == null) escrows.remove(owner);
            else escrows.put(owner, previous);
            if (ex instanceof IOException io) throw io;
            throw ex;
        }
        return next;
    }

    /** Binds once; the same fingerprint is idempotent, a different one is rejected. */
    public synchronized Binding bind(UUID owner, String fingerprint, String displayName) throws IOException {
        Binding next = new Binding(fingerprint, displayName);
        Binding existing = bindings.get(owner);
        if (existing != null) {
            if (!existing.fingerprint().equals(next.fingerprint())) {
                throw new IOException("Different BenMingGu already bound");
            }
            return existing;
        }

        bindings.put(owner, next);
        try {
            writeAll();
        } catch (IOException ex) {
            bindings.remove(owner);
            throw ex;
        }
        return next;
    }

    /**
     * Replaces an uncommitted binding. The caller must first establish that the relevant
     * ascension session does not exist or has not bound a vital Gu yet.
     */
    public synchronized Binding bindOrReplaceUncommitted(UUID owner, String fingerprint,
                                                          String displayName) throws IOException {
        if (owner == null) throw new IllegalArgumentException("Missing BenMingGu owner");
        Binding next = new Binding(fingerprint, displayName);
        Binding previous = bindings.get(owner);
        if (next.equals(previous)) return previous;
        bindings.put(owner, next);
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            if (previous == null) bindings.remove(owner);
            else bindings.put(owner, previous);
            throw ex;
        }
        return next;
    }

    public synchronized boolean isConsumed(UUID owner, String fingerprint) {
        Consumption record = consumed.get(owner);
        return record != null && record.fingerprint().equals(fingerprint);
    }

    /** Records that the offered stack was removed; the same fingerprint is idempotent. */
    public synchronized void markConsumed(UUID owner, String fingerprint, String displayName) throws IOException {
        Consumption next = new Consumption(fingerprint, displayName);
        Consumption previous = consumed.get(owner);
        if (previous != null) {
            if (!previous.fingerprint().equals(next.fingerprint())) {
                throw new IOException("Different BenMingGu already consumed");
            }
            return;
        }
        consumed.put(owner, next);
        try {
            writeAll();
        } catch (IOException | RuntimeException ex) {
            if (previous == null) consumed.remove(owner);
            else consumed.put(owner, previous);
            throw ex;
        }
    }

    private void writeAll() throws IOException {
        Properties p = new Properties();
        for (Map.Entry<UUID, Binding> e : bindings.entrySet()) {
            String prefix = "binding." + e.getKey() + ".";
            p.setProperty(prefix + "fingerprint", e.getValue().fingerprint());
            p.setProperty(prefix + "name", e.getValue().displayName());
        }
        for (Map.Entry<UUID, Consumption> e : consumed.entrySet()) {
            String prefix = "consumed." + e.getKey() + ".";
            p.setProperty(prefix + "fingerprint", e.getValue().fingerprint());
            p.setProperty(prefix + "name", e.getValue().displayName());
        }
        for (Map.Entry<UUID, Escrow> e : escrows.entrySet()) {
            String prefix = "escrow." + e.getKey() + ".";
            p.setProperty(prefix + "fingerprint", e.getValue().fingerprint());
            p.setProperty(prefix + "name", e.getValue().displayName());
            p.setProperty(prefix + "item", e.getValue().itemSnbt());
            p.setProperty(prefix + "state", e.getValue().state().name());
        }
        Path temp = Files.createTempFile(this.file.getParent(), "benminggu-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) { p.store(out, "shengxian BenMingGu custody"); }
            Files.move(temp, this.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String required(Properties p, String key) throws IOException {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing BenMingGu property: " + key);
        return value;
    }
}
