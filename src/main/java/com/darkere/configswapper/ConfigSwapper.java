package com.darkere.configswapper;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
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

    public ConfigSwapper() {
        MinecraftForge.EVENT_BUS.addListener(ChangeModeCommand::registerCommand);
        FMLJavaModLoadingContext.get().getModEventBus().register(this);
    }

    @SubscribeEvent
    public void common(FMLCommonSetupEvent event) {
        INSTANCE = NetworkRegistry.newSimpleChannel(CHANNELID, () -> "1", s -> true, s -> true);
        INSTANCE.registerMessage(ID++, ConfigChangeMessage.class, ConfigChangeMessage::encode, ConfigChangeMessage::decode, ConfigChangeMessage::handle);
    }

    @SubscribeEvent
    public void complete(FMLLoadCompleteEvent event) {
        modes = Utils.readAvailableModes();
        String mode = Utils.readWriteModeToJson(null);
        if (mode == null || !modes.contains(mode)) return;
        LOGGER.info("Applying configs for " + mode + " mode");
        ModeConfig reloader = new ModeConfig(mode);
        reloader.applyMode();
    }
}
