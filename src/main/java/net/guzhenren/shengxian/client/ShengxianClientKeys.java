package net.guzhenren.shengxian.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.guzhenren.shengxian.ShengxianMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = ShengxianMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShengxianClientKeys {
    static {
        net.guzhenren.shengxian.network.ShengxianNetwork.setClientFxSink(ClientFx::play);
        net.guzhenren.shengxian.network.ShengxianNetwork.setClientVisualSink(ClientAscensionEffects::accept);
        net.guzhenren.shengxian.network.ShengxianNetwork.setClientOutcomeSink(AscensionOutcomeClient::accept);
    }

    public static final KeyMapping OPEN = new KeyMapping("key.shengxian.qi",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.shengxian");

    private ShengxianClientKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }
}