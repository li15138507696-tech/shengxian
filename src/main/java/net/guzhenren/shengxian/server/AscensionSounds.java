package net.guzhenren.shengxian.server;

import net.guzhenren.shengxian.core.Phase;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Central server-side sound layer for the ascension flow.
 *
 * <p>Vanilla {@link SoundEvents} only: this mod ships no .ogg files and no sounds.json, so every
 * node here reuses a stock sound event. Call sites never carry mix numbers; they either use the
 * narrow {@link #play(ServerLevel, Vec3, SoundEvent, float, float)} facade or one of the named
 * ritual / immortal-gu helpers below.
 *
 * <p>The client already owns the foreground cues ({@code client.ClientFx} for FX payloads and
 * {@code client.RitualSoundscape} for phase transitions). This layer is the short-range depth
 * underneath them, so it deliberately picks <em>different</em> vanilla events than the client
 * layer and keeps them quiet: one event never appears twice in the same mix.
 *
 * <p>Noise guards, because every node sits on a tick, event or block-loop path:
 * <ul>
 *   <li>a hard budget of {@link #MAX_PER_TICK} started cues per server tick;</li>
 *   <li>per-cue cooldowns, either shared per 8-block cell or global per dimension;</li>
 *   <li>a per-cue minimum intensity, so weak or idle ticks stay silent.</li>
 * </ul>
 *
 * <p>Server thread only: the cooldown tables are plain {@link HashMap} state and every current
 * caller already runs on the main server thread (payload handlers use {@code enqueueWork}).
 *
 * <p>Wiring status: {@link #fx} is reached from {@link AscensionFx#send} and the 仙蛊 helpers from
 * {@link XianGuEffects}. {@link #phase} and {@link #harmony} are the two nodes that still need a
 * one-line call site outside this package - {@code AscensionLifecycleService} on a phase
 * transition and {@code AscensionControlService.onServerTick} on a qi step - so the ritual side
 * stays silent until those are added; the client {@code RitualSoundscape} already voices the eight
 * phases in the meantime.
 */
public final class AscensionSounds {
    /** Hard ceiling for cues started by this layer in one server tick. */
    private static final int MAX_PER_TICK = 8;
    /** Cooldown-table bound; entries older than {@link #KEY_TTL_TICKS} are pruned first. */
    private static final int KEY_LIMIT = 512;
    private static final int KEY_TTL_TICKS = 200;
    /** How long a {@link #phaseOnce} announcement is remembered before it may repeat. */
    private static final int ONCE_TTL_TICKS = 1200;
    /** Cell size in blocks for positional cooldowns. */
    private static final int CELL_SHIFT = 3;

    private static final Map<String, Long> LAST = new HashMap<>();
    private static long budgetTick = Long.MIN_VALUE;
    private static int budgetUsed;

    private AscensionSounds() {}

    /** One vanilla cue plus its mix settings and its noise guards. */
    private record Cue(SoundEvent event, SoundSource source, float volume, float pitch,
                       int cooldownTicks, boolean perCell, float minIntensity) {}

    private static Cue mix(SoundEvent event, SoundSource source, float volume, float pitch,
                           int cooldownTicks, boolean perCell, float minIntensity) {
        return new Cue(event, source, volume, pitch, cooldownTicks, perCell, minIntensity);
    }

    // ---- ritual nodes ---------------------------------------------------

    /** Ready: 备妥, a single ceremonial selection tone. */
    private static final Cue PREPARE = mix(SoundEvents.BEACON_POWER_SELECT, SoundSource.AMBIENT,
            0.55f, 0.70f, 80, false, 0.0f);
    /** 碎窍 start: dull rumble under the crack. */
    private static final Cue SHATTER_RUMBLE = mix(SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT,
            0.60f, 0.55f, 60, false, 0.0f);
    /** 碎窍 start: the shattering itself. */
    private static final Cue SHATTER_CRACK = mix(SoundEvents.GLASS_BREAK, SoundSource.BLOCKS,
            0.45f, 1.10f, 60, false, 0.0f);
    /** 碎窍 finished: the aperture settles. */
    private static final Cue BROKEN_SETTLE = mix(SoundEvents.SHULKER_CLOSE, SoundSource.AMBIENT,
            0.50f, 0.80f, 60, false, 0.0f);
    /** 纳气 begins: quiet intake. */
    private static final Cue ABSORB_BEGIN = mix(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT,
            0.40f, 0.90f, 80, false, 0.0f);
    /** 三气调和推进: slow pulse, pitch/volume follow the harmony strength. */
    private static final Cue HARMONY_PULSE = mix(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT,
            0.18f, 1.00f, 20, false, 0.25f);
    /** 纳气成功 -> OPENING. */
    private static final Cue OPENING_CUE = mix(SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.AMBIENT,
            0.65f, 0.70f, 80, false, 0.0f);
    /** FORMING 仙窍成型. */
    private static final Cue FORMING_CUE = mix(SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.AMBIENT,
            0.65f, 1.05f, 80, false, 0.0f);
    /** IMMORTAL: low foundation, then the rising note. */
    private static final Cue IMMORTAL_LOW = mix(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.AMBIENT,
            0.85f, 0.80f, 120, false, 0.0f);
    private static final Cue IMMORTAL_RISE = mix(SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT,
            0.90f, 0.60f, 120, false, 0.0f);
    /** FAILED: low close-out. */
    private static final Cue FAILED_CUE = mix(SoundEvents.ELDER_GUARDIAN_DEATH, SoundSource.AMBIENT,
            0.70f, 0.55f, 120, false, 0.0f);

    // ---- tribulation nodes ----------------------------------------------

    /** 灾劫载体出现: one deep presence under the client-side type-specific roar. */
    private static final Cue CARRIER_ARRIVE = mix(SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT,
            0.50f, 0.62f, 30, true, 0.0f);
    /** 载体被击退: dull impact, throttled hard because impacts can chain. */
    private static final Cue CARRIER_REPELLED = mix(SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE,
            0.70f, 1.00f, 10, true, 0.0f);
    private static final Cue FATAL_CUE = mix(SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE,
            0.80f, 0.45f, 40, false, 0.0f);
    private static final Cue APERTURE_OPEN = mix(SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS,
            0.50f, 0.80f, 40, false, 0.0f);
    private static final Cue APERTURE_CLOSE = mix(SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS,
            0.40f, 0.85f, 40, false, 0.0f);
    private static final Cue QI_STOP = mix(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT,
            0.25f, 0.80f, 60, false, 0.0f);

    // ---- 仙蛊 (three distinct timbres) ----------------------------------

    /** 定仙游 channel: called every other tick, so the pulse is on a 24-tick cooldown. */
    private static final Cue GU_CHANNEL = mix(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
            0.18f, 1.50f, 24, false, 0.0f);
    /** 人如故: restoration timbre. */
    private static final Cue GU_HEAL = mix(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
            0.45f, 1.30f, 20, false, 0.0f);
    /** 江山如故: called once per restored block, so the rustle is global and slow. */
    private static final Cue GU_TERRAIN = mix(SoundEvents.MOSS_PLACE, SoundSource.BLOCKS,
            0.28f, 0.75f, 12, false, 0.0f);

    // ---- narrow public facade -------------------------------------------

    /** Plays one vanilla event at a world position; returns {@code false} when the tick budget is spent. */
    public static boolean play(ServerLevel level, Vec3 pos, SoundEvent event, float volume, float pitch) {
        return play(level, pos, event, SoundSource.AMBIENT, volume, pitch);
    }

    /** Plays one vanilla event at a world position; returns {@code false} when the tick budget is spent. */
    public static boolean play(ServerLevel level, Vec3 pos, SoundEvent event, SoundSource source,
                               float volume, float pitch) {
        if (level == null || pos == null || event == null || source == null) return false;
        long now = level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            budgetUsed = 0;
        }
        if (budgetUsed >= MAX_PER_TICK) return false;
        budgetUsed++;
        level.playSound(null, pos.x, pos.y, pos.z, event, source, volume, pitch);
        return true;
    }

    /** Ritual phase transition; one call per transition, so each node keeps its own cooldown. */
    public static void phase(ServerLevel level, Vec3 pos, Phase phase) {
        if (phase == null) return;
        switch (phase) {
            case PREPARING -> cue(level, pos, "phase_preparing", PREPARE, 1.0f);
            case BREAKING -> {
                cue(level, pos, "phase_breaking_rumble", SHATTER_RUMBLE, 1.0f);
                cue(level, pos, "phase_breaking_crack", SHATTER_CRACK, 1.0f);
            }
            case BROKEN -> cue(level, pos, "phase_broken", BROKEN_SETTLE, 1.0f);
            case ABSORBING -> cue(level, pos, "phase_absorbing", ABSORB_BEGIN, 1.0f);
            case OPENING -> cue(level, pos, "phase_opening", OPENING_CUE, 1.0f);
            case FORMING -> cue(level, pos, "phase_forming", FORMING_CUE, 1.0f);
            case IMMORTAL -> {
                cue(level, pos, "phase_immortal_low", IMMORTAL_LOW, 1.0f);
                cue(level, pos, "phase_immortal_rise", IMMORTAL_RISE, 1.0f);
            }
            case FAILED -> cue(level, pos, "phase_failed", FAILED_CUE, 1.0f);
        }
    }

    /**
     * 三气调和推进 pulse. {@code strength} is the 0..1 harmony progress of the current step:
     * below {@link #HARMONY_PULSE}'s threshold nothing is played, and the pulse is on a 20-tick
     * cooldown so a per-tick caller can never turn this into a drone.
     */
    public static void harmony(ServerLevel level, Vec3 pos, float strength) {
        float clamped = Math.max(0.0f, Math.min(1.0f, strength));
        if (clamped < HARMONY_PULSE.minIntensity()) return;
        Cue pulse = mix(HARMONY_PULSE.event(), HARMONY_PULSE.source(),
                0.16f + clamped * 0.10f, 0.90f + clamped * 0.35f,
                HARMONY_PULSE.cooldownTicks(), HARMONY_PULSE.perCell(), HARMONY_PULSE.minIntensity());
        cue(level, pos, "harmony_pulse", pulse, clamped);
    }

    /**
     * Maps one FX payload cue name (see {@link AscensionFx}) to its server-side depth sound.
     * Unknown names are ignored on purpose: the FX channel also carries purely visual cues.
     */
    public static void fx(ServerLevel level, Vec3 pos, String effect, float intensity) {
        if (effect == null) return;
        switch (effect) {
            case "tribulation_willow_start" -> cue(level, pos, "carrier_arrive",
                    withPitch(CARRIER_ARRIVE, 0.55f), intensity);
            case "tribulation_tiger_start" -> cue(level, pos, "carrier_arrive",
                    withPitch(CARRIER_ARRIVE, 0.48f), intensity);
            case "tribulation_beast_start" -> cue(level, pos, "carrier_arrive",
                    withPitch(CARRIER_ARRIVE, 0.62f), intensity);
            case "tribulation_sound_start" -> cue(level, pos, "carrier_arrive",
                    withPitch(CARRIER_ARRIVE, 0.72f), intensity);
            case "tribulation_blood_rain_start" -> cue(level, pos, "carrier_arrive",
                    withPitch(CARRIER_ARRIVE, 0.50f), intensity);
            case "tribulation_impact", "tribulation_impact_sound",
                    "tribulation_impact_beast", "tribulation_impact_blood_rain" ->
                    cue(level, pos, "carrier_repelled", CARRIER_REPELLED, intensity);
            case "tribulation_fatal" -> cue(level, pos, "tribulation_fatal", FATAL_CUE, intensity);
            case "aperture_open" -> cue(level, pos, "aperture_open", APERTURE_OPEN, intensity);
            case "aperture_close" -> cue(level, pos, "aperture_close", APERTURE_CLOSE, intensity);
            case "qi" -> harmony(level, pos, intensity);
            case "qi_stop" -> cue(level, pos, "qi_stop", QI_STOP, 1.0f);
            default -> { }
        }
    }

    // ---- 仙蛊 (定仙游 / 人如故 / 江山如故) --------------------------------

    /** 定仙游 travel: departure and arrival share one timbre at two pitches. */
    public static void guTravel(ServerLevel level, Vec3 pos, boolean arriving) {
        play(level, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.45f, arriving ? 1.35f : 0.90f);
    }

    /** 定仙游 channel pulse; safe to call every tick. */
    public static void guChannel(ServerLevel level, Vec3 pos) {
        cue(level, pos, "gu_channel", GU_CHANNEL, 1.0f);
    }

    /** 人如故 restoration. */
    public static void guHeal(ServerLevel level, Vec3 pos) {
        cue(level, pos, "gu_heal", GU_HEAL, 1.0f);
    }

    /** 江山如故 terrain restoration; safe to call once per restored block. */
    public static void guTerrain(ServerLevel level, Vec3 pos) {
        cue(level, pos, "gu_terrain", GU_TERRAIN, 1.0f);
    }

    /**
     * Phase cue that must play once per player per phase, not once per poll.
     *
     * <p>Terminal phases are detected by a repeated heartbeat ({@code AscensionOutcomeService.send}
     * runs every visual heartbeat), so the plain {@link #phase} entry point would replay the
     * 成仙/失败 cue forever. This variant keeps a bounded per-player memory of the last phase it
     * announced and stays silent when the same phase is asked for again inside
     * {@link #ONCE_TTL_TICKS}. The memory is pruned by the same pass that bounds the cue table.</p>
     */
    public static void phaseOnce(ServerPlayer player, Phase phase) {
        if (player == null || phase == null) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        String key = player.getUUID() + "|phase|" + phase.name();
        Long last = LAST.get(key);
        if (last != null && now - last < ONCE_TTL_TICKS) return;
        if (LAST.size() >= KEY_LIMIT) prune(now);
        LAST.put(key, now);
        phase(level, player.position(), phase);
    }

    // ---- internals ------------------------------------------------------

    private static Cue withPitch(Cue base, float pitch) {
        return mix(base.event(), base.source(), base.volume(), pitch,
                base.cooldownTicks(), base.perCell(), base.minIntensity());
    }

    private static void cue(ServerLevel level, Vec3 pos, String id, Cue cue, float intensity) {
        if (level == null || pos == null || cue == null || intensity < cue.minIntensity()) return;
        long now = level.getGameTime();
        String key = cooldownKey(level, pos, id, cue);
        if (key != null) {
            Long last = LAST.get(key);
            if (last != null && now - last < cue.cooldownTicks()) return;
        }
        if (!play(level, pos, cue.event(), cue.source(), cue.volume(), cue.pitch())) return;
        if (key != null) {
            if (LAST.size() >= KEY_LIMIT) prune(now);
            LAST.put(key, now);
        }
    }

    private static String cooldownKey(ServerLevel level, Vec3 pos, String id, Cue cue) {
        if (cue.cooldownTicks() <= 0) return null;
        String dimension = level.dimension().location().toString();
        if (!cue.perCell()) return dimension + '|' + id;
        int cx = ((int) Math.floor(pos.x)) >> CELL_SHIFT;
        int cy = ((int) Math.floor(pos.y)) >> CELL_SHIFT;
        int cz = ((int) Math.floor(pos.z)) >> CELL_SHIFT;
        return dimension + '|' + id + '|' + cx + ',' + cy + ',' + cz;
    }

    private static void prune(long now) {
        Iterator<Map.Entry<String, Long>> it = LAST.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() >= KEY_TTL_TICKS) it.remove();
        }
        if (LAST.size() >= KEY_LIMIT) LAST.clear();
    }
}
