package net.guzhenren.shengxian.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.guzhenren.shengxian.core.BenMingGuStore;

import java.io.IOException;

/** Re-checks the live mirrored BenMingGu against a recorded fingerprint. */
public final class BenMingGuCustody {
    private BenMingGuCustody() {}

    /** Direct mirror match; legacy damage-only changes require the inventory check in holds. */
    public static boolean matches(ServerPlayer player, String fingerprint) {
        ItemStack stack = new GuzhenrenPlayerBridge().vitalGu(player);
        return !stack.isEmpty() && (BenMingGuFingerprint.of(stack, player.registryAccess()).equals(fingerprint)
                || BenMingGuFingerprint.legacyOf(stack).equals(fingerprint));
    }

    /**
     * Requires an inventory item matching the mirror apart from damage. A legacy binding is
     * accepted only if that inventory item still matches its original full fingerprint.
     */
    public static boolean holds(ServerPlayer player, String fingerprint) {
        ItemStack mirror = new GuzhenrenPlayerBridge().vitalGu(player);
        if (mirror.isEmpty()) return false;
        boolean normalizedBinding = BenMingGuFingerprint.of(mirror, player.registryAccess()).equals(fingerprint);
        for (ItemStack stack : player.getInventory().items) {
            if (matchesCandidate(stack, mirror, fingerprint, normalizedBinding)) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (matchesCandidate(stack, mirror, fingerprint, normalizedBinding)) return true;
        }
        return false;
    }

    /** Removes exactly one matching stack; returns false without changing inventory. */
    public static boolean consumeOne(ServerPlayer player, String fingerprint) {
        ItemStack mirror = new GuzhenrenPlayerBridge().vitalGu(player);
        if (mirror.isEmpty()) return false;
        boolean normalizedBinding = BenMingGuFingerprint.of(mirror, player.registryAccess()).equals(fingerprint);
        for (ItemStack stack : player.getInventory().items) {
            if (matchesCandidate(stack, mirror, fingerprint, normalizedBinding)) {
                stack.shrink(1);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (matchesCandidate(stack, mirror, fingerprint, normalizedBinding)) {
                stack.shrink(1);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                return true;
            }
        }
        return false;
    }

    /**
     * Persists a full stack copy before removing the real inventory item. The player inventory
     * is saved before the escrow intent is marked complete, so a crash on either side can be
     * reconciled without losing or duplicating the Gu.
     */
    public static boolean ensureOffered(ServerPlayer player, BenMingGuStore store,
                                        String fingerprint, String displayName) throws IOException {
        BenMingGuStore.Escrow escrow = store.findEscrow(player.getUUID()).orElse(null);
        if (escrow != null) {
            if (!escrow.fingerprint().equals(fingerprint)) throw new IOException("Escrowed Gu fingerprint mismatch");
            if (escrow.state() == BenMingGuStore.EscrowState.STORED) return true;
        }
        ItemStack mirror = new GuzhenrenPlayerBridge().vitalGu(player);
        if (mirror.isEmpty()) return false;
        boolean normalized = BenMingGuFingerprint.of(mirror, player.registryAccess()).equals(fingerprint);
        if (escrow == null) {
            ItemStack candidate = findCandidate(player, mirror, fingerprint, normalized);
            if (candidate == null) return false;
            ItemStack single = candidate.copyWithCount(1);
            String snbt = single.save(player.registryAccess()).toString();
            escrow = store.beginOffer(player.getUUID(), fingerprint, displayName, snbt);
        }
        if (!escrow.fingerprint().equals(fingerprint)) throw new IOException("Escrowed Gu fingerprint mismatch");
        if (escrow.state() == BenMingGuStore.EscrowState.STORED) return true;
        if (escrow.state() != BenMingGuStore.EscrowState.OFFER_PENDING) {
            throw new IOException("BenMingGu is already in its return transaction");
        }
        if (consumeOne(player, fingerprint)) {
            player.server.getPlayerList().saveAll();
        }
        store.markOfferStored(player.getUUID());
        return true;
    }

    /** Restores a prepared/held Gu after formation, or cancels an uncommitted offer on restart. */
    public static boolean restoreAfterFormation(ServerPlayer player, BenMingGuStore store) throws IOException {
        BenMingGuStore.Escrow escrow = store.findEscrow(player.getUUID()).orElse(null);
        if (escrow == null) return false;
        if (escrow.state() == BenMingGuStore.EscrowState.RETURNED) return true;
        if (escrow.state() == BenMingGuStore.EscrowState.OFFER_PENDING) {
            consumeOne(player, escrow.fingerprint());
            player.server.getPlayerList().saveAll();
            store.markOfferStored(player.getUUID());
        }
        escrow = store.beginReturn(player.getUUID());
        if (holds(player, escrow.fingerprint())) {
            store.markReturned(player.getUUID());
            return true;
        }
        ItemStack restored = decode(escrow, player);
        if (!player.getInventory().add(restored)) return false;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.server.getPlayerList().saveAll();
        store.markReturned(player.getUUID());
        return true;
    }

    private static ItemStack decode(BenMingGuStore.Escrow escrow, ServerPlayer player) throws IOException {
        try {
            Tag serialized = TagParser.parseTag(escrow.itemSnbt());
            ItemStack restored = ItemStack.parse(player.registryAccess(), serialized)
                    .orElseThrow(() -> new IOException("Cannot decode escrowed BenMingGu"));
            String normalized = BenMingGuFingerprint.of(restored, player.registryAccess());
            String legacy = BenMingGuFingerprint.legacyOf(restored);
            if (restored.isEmpty() || restored.getCount() != 1
                    || (!escrow.fingerprint().equals(normalized) && !escrow.fingerprint().equals(legacy))) {
                throw new IOException("Escrowed BenMingGu fingerprint verification failed");
            }
            return restored;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            throw new IOException("Malformed escrowed BenMingGu data", ex);
        }
    }

    private static ItemStack findCandidate(ServerPlayer player, ItemStack mirror,
                                           String fingerprint, boolean normalized) {
        for (ItemStack stack : player.getInventory().items) {
            if (matchesCandidate(stack, mirror, fingerprint, normalized)) return stack;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (matchesCandidate(stack, mirror, fingerprint, normalized)) return stack;
        }
        return null;
    }

    private static boolean matchesCandidate(ItemStack stack, ItemStack mirror,
                                            String fingerprint, boolean normalizedBinding) {
        if (!BenMingGuFingerprint.sameExceptDamage(stack, mirror)) return false;
        return normalizedBinding || BenMingGuFingerprint.legacyOf(stack).equals(fingerprint);
    }
}
