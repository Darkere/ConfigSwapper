package com.darkere.configswapper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.network.PacketDistributor;

public class ChangeModeCommand {

    public static void registerCommand(RegisterCommandsEvent event) {
        ConfigSwapper.modes = Utils.readAvailableModes();

        SuggestionProvider<CommandSource> modeSuggestions = (ctx, builder) ->
            ISuggestionProvider.suggest(ConfigSwapper.modes, builder);

        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSource>literal("mode")
            .requires(player -> player.hasPermissionLevel(4))
            .then(Commands.argument("configmode", StringArgumentType.word())
                .suggests(modeSuggestions)
                .executes((ctx -> changeMode(ctx, StringArgumentType.getString(ctx, "configmode"), null)))
                .then(Commands.literal("optionalBackup")
                    .then(Commands.argument("foldername", StringArgumentType.word())
                        .executes(ctx -> changeMode(ctx, StringArgumentType.getString(ctx, "configmode"), StringArgumentType.getString(ctx, "foldername"))
                        )))));
    }

    private static int changeMode(CommandContext<CommandSource> context, String mode, String backup) throws CommandSyntaxException {
        if (!ConfigSwapper.modes.contains(mode)) {
            Message message = new LiteralMessage(mode + " is not an available mode");
            throw new CommandSyntaxException(new SimpleCommandExceptionType(message), message);
        }

        context.getSource().sendFeedback(new StringTextComponent("Changing mode to " + mode + ". Expect a large Lag spike"), true);

        ModeConfig modeConfig = new ModeConfig(mode);
        modeConfig.applyMode();
        Utils.readWriteModeToJson(mode);

        if (context.getSource().getServer() instanceof DedicatedServer) {
            context.getSource().getServer().getPlayerList().getPlayers().forEach(
                player -> ConfigSwapper.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ConfigChangeMessage(mode, backup)));
        }

        context.getSource().getServer().getCommandManager().handleCommand(context.getSource(), "reload");
        return Command.SINGLE_SUCCESS;
    }
}
