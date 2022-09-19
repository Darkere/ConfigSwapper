package com.darkere.configswapper;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ConfigChangeMessage {
    String mode;
    boolean force;

    public ConfigChangeMessage(String mode, boolean force) {
        this.mode = mode;
        this.force = force;
    }

    public static void encode(ConfigChangeMessage data, FriendlyByteBuf buf) {
        buf.writeUtf(data.mode);
        buf.writeBoolean(data.force);
    }

    public static ConfigChangeMessage decode(FriendlyByteBuf buf) {
        return new ConfigChangeMessage(buf.readUtf(), buf.readBoolean());
    }

    public static boolean handle(ConfigChangeMessage data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if(!data.force && data.mode.equals(Utils.readWriteModeToJson(null))){
                return;
            }

            if (!Utils.readAvailableModes().contains(data.mode)) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Missing data for config change!. Client configs will be out of sync."));
                return;
            }
            ModeConfig modeConfig = new ModeConfig(data.mode);
            modeConfig.applyMode();
            Utils.readWriteModeToJson(data.mode);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
