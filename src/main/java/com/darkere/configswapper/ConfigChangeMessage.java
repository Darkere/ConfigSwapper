package com.darkere.configswapper;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ConfigChangeMessage {
    String mode;
    String backup;

    public ConfigChangeMessage(String mode, String backup) {
        this.mode = mode;
        this.backup = backup;
    }

    public static void encode(ConfigChangeMessage data, PacketBuffer buf) {
        buf.writeString(data.mode);
        buf.writeString(data.backup);
    }

    public static ConfigChangeMessage decode(PacketBuffer buf) {
        return new ConfigChangeMessage(buf.readString(), buf.readString());
    }

    public static boolean handle(ConfigChangeMessage data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (!Utils.readAvailableModes().contains(data.mode)) {
                Minecraft.getInstance().player.sendMessage(new StringTextComponent("Missing data for config change!. Client configs will be out of sync."), new UUID(0, 0));
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
