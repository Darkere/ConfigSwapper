package com.darkere.configswapper;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(ConfigSwapper.MODID)
public class ConfigSwapper {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final String MODID = "configswapper";
    public static List<String> modes = new ArrayList<>();
    private static final ResourceLocation CHANNELID = new ResourceLocation(MODID, "network");
    public static SimpleChannel INSTANCE;
    private int ID;
    public static boolean afterConstructor = false;

    public ConfigSwapper() {
        MinecraftForge.EVENT_BUS.addListener(ChangeModeCommand::registerCommand);
        MinecraftForge.EVENT_BUS.addListener(this::join);
        MinecraftForge.EVENT_BUS.addListener(this::serverStart);
        FMLJavaModLoadingContext.get().getModEventBus().register(this);
        updateMode(true);
        afterConstructor = true;
    }

    @SubscribeEvent
    public void common(FMLCommonSetupEvent event) {
        INSTANCE = NetworkRegistry.newSimpleChannel(CHANNELID, () -> "1", s -> true, s -> true);
        INSTANCE.registerMessage(ID++, ConfigChangeMessage.class, ConfigChangeMessage::encode, ConfigChangeMessage::decode, ConfigChangeMessage::handle);
    }

    @SubscribeEvent
    public void finishedLoading(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
               updateMode(false);
            }
        );
    }

    public void serverStart(FMLServerStartedEvent event) {
        updateMode(false);
    }

    private void updateMode(boolean limited) {
        modes = Utils.readAvailableModes();
        String mode = Utils.readWriteModeToJson(null);
        if (mode == null || !modes.contains(mode)) return;
        LOGGER.info("Applying configs for " + mode + " mode");
        if(limited) LOGGER.info("This (the first) swap is limited and does not log all errors or correct failed files");
        ModeConfig config = new ModeConfig(mode);
        config.applyMode();
    }

    public void join(PlayerEvent.PlayerLoggedInEvent event) {
        String currentMode = Utils.readWriteModeToJson(null);
        ConfigSwapper.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) event.getPlayer()), new ConfigChangeMessage(currentMode, false));
    }
}
