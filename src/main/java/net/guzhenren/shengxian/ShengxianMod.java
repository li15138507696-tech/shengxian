package net.guzhenren.shengxian;

import net.guzhenren.shengxian.command.ApertureGuestCommand;
import net.guzhenren.shengxian.command.AscensionActionCommand;
import net.guzhenren.shengxian.command.BindBenMingCommand;
import net.guzhenren.shengxian.command.CalamityTestCommand;
import net.guzhenren.shengxian.command.FormApertureCommand;
import net.guzhenren.shengxian.command.InsightCommand;
import net.guzhenren.shengxian.command.ShengxianCommands;
import net.guzhenren.shengxian.command.XianGuCommand;
import net.guzhenren.shengxian.server.ApertureProtection;
import net.guzhenren.shengxian.server.ApertureTickService;
import net.guzhenren.shengxian.server.AscensionControlService;
import net.guzhenren.shengxian.server.AscensionLifecycleService;
import net.guzhenren.shengxian.server.AscensionServer;
import net.guzhenren.shengxian.server.ImmortalEssenceService;
import net.guzhenren.shengxian.server.TribulationWorldService;
import net.guzhenren.shengxian.server.XianGuEvents;
import net.guzhenren.shengxian.entity.ShengxianEntities;
import net.guzhenren.shengxian.item.ShengxianItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ShengxianMod.MOD_ID)
public final class ShengxianMod {
    public static final String MOD_ID = "shengxian";

    public ShengxianMod(IEventBus modBus, ModContainer container) {
        ShengxianItems.ITEMS.register(modBus);
        ShengxianEntities.ENTITIES.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, ShengxianConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ShengxianVisualConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(ShengxianCommands::register);
        NeoForge.EVENT_BUS.addListener(AscensionActionCommand::register);
        NeoForge.EVENT_BUS.addListener(BindBenMingCommand::register);
        NeoForge.EVENT_BUS.addListener(ApertureGuestCommand::register);
        NeoForge.EVENT_BUS.addListener(FormApertureCommand::register);
        NeoForge.EVENT_BUS.addListener(InsightCommand::register);
        NeoForge.EVENT_BUS.addListener(XianGuCommand::register);
        NeoForge.EVENT_BUS.addListener(CalamityTestCommand::register);
        NeoForge.EVENT_BUS.addListener(AscensionServer::onServerStarted);
        NeoForge.EVENT_BUS.addListener(XianGuEvents::started);
        NeoForge.EVENT_BUS.addListener(XianGuEvents::stopping);
        NeoForge.EVENT_BUS.addListener(AscensionServer::onServerStopping);
        NeoForge.EVENT_BUS.addListener(XianGuEvents::logout);
        NeoForge.EVENT_BUS.addListener(AscensionServer::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(AscensionServer::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(XianGuEvents::login);
        NeoForge.EVENT_BUS.addListener(XianGuEvents::clone);
        NeoForge.EVENT_BUS.addListener(XianGuEvents::unload);
        NeoForge.EVENT_BUS.addListener(XianGuEvents::tick);
        NeoForge.EVENT_BUS.addListener(AscensionServer::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(AscensionServer::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(AscensionServer::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(ApertureProtection::onServerTick);
        NeoForge.EVENT_BUS.addListener(ApertureProtection::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(ApertureProtection::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(ApertureProtection::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ApertureProtection::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(ApertureProtection::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(ApertureProtection::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ApertureProtection::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(ApertureProtection::onItemToss);
        NeoForge.EVENT_BUS.addListener(ApertureProtection::onItemPickup);
        NeoForge.EVENT_BUS.addListener(ApertureProtection::onItemTick);
        NeoForge.EVENT_BUS.addListener(ApertureTickService::onServerTick);
        NeoForge.EVENT_BUS.addListener(AscensionLifecycleService::onServerTick);
        NeoForge.EVENT_BUS.addListener(AscensionControlService::onServerTick);
        NeoForge.EVENT_BUS.addListener(ImmortalEssenceService::onServerTick);
        NeoForge.EVENT_BUS.addListener(net.guzhenren.shengxian.server.AscensionVisualService::onServerTick);
        NeoForge.EVENT_BUS.addListener(TribulationWorldService::onServerTick);
        NeoForge.EVENT_BUS.addListener(TribulationWorldService::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(TribulationWorldService::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(TribulationWorldService::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(TribulationWorldService::onEntityTick);
    }
}