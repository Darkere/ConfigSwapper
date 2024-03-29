package com.darkere.configswapper;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
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

    public void serverStart(ServerStartedEvent event) {
        updateMode(false);
    }

    private void updateMode(boolean limited) {
        modes = Utils.readAvailableModes();
        String mode = Utils.readWriteModeToJson(null);
        String defaultMode = Utils.readDefaultMode();
        if (mode == null || !modes.contains(mode)) {
            if (modes.contains(defaultMode)) {
                mode = defaultMode;
                Utils.readWriteModeToJson(defaultMode);
            }
        }
        if (mode == null || !modes.contains(mode)){
            LOGGER.info("Default Mode is set to " + defaultMode + ". " + defaultMode + " is not a valid mode.");
            return;
        }
        LOGGER.info("Applying configs for " + mode + " mode");
        if (limited)
            LOGGER.info("This (the first) swap is limited and does not log all errors or correct failed files");
        ModeConfig config = new ModeConfig(mode);
        config.applyMode();
    }

    public void join(PlayerEvent.PlayerLoggedInEvent event) {
        String currentMode = Utils.readWriteModeToJson(null);
        ConfigSwapper.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.getEntity()), new ConfigChangeMessage(currentMode, false));
    }
}
