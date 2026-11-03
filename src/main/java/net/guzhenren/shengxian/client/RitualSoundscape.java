package net.guzhenren.shengxian.client;

import net.guzhenren.shengxian.ShengxianVisualConfig;
import net.guzhenren.shengxian.core.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Short local transition cues only; no loops, global volume edits or gameplay actions. */
final class RitualSoundscape {
    private RitualSoundscape() {}
    static void transition(Phase phase) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!ShengxianVisualConfig.enabled())return;
        var sound=switch(phase) {
            case PREPARING -> SoundEvents.BEACON_AMBIENT;
            case BREAKING -> SoundEvents.AMETHYST_BLOCK_BREAK;
            case BROKEN -> SoundEvents.GLASS_BREAK;
            case ABSORBING, IMMORTAL -> SoundEvents.BEACON_ACTIVATE;
            case OPENING, FORMING -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case FAILED -> SoundEvents.BEACON_DEACTIVATE;
        };
        mc.level.playLocalSound(mc.player.getX(),mc.player.getY(),mc.player.getZ(),sound,SoundSource.AMBIENT,
                ShengxianVisualConfig.reduced()?.20f:.45f,phase==Phase.IMMORTAL?.85f:.70f,false);
    }
}
