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

import java.util.Collections;

public class ChangeModeCommand {

    public static void registerCommand(RegisterCommandsEvent event) {
        ConfigSwapper.modes = Utils.readAvailableModes();

        SuggestionProvider<CommandSource> modeSuggestions = (ctx, builder) ->
            ISuggestionProvider.suggest(ConfigSwapper.modes, builder);

        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSource>literal("mode")
            .executes(ctx -> {
                ctx.getSource().sendFeedback(new StringTextComponent("Available modes are: " + ConfigSwapper.modes.toString()), true);
                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.argument("configmode", StringArgumentType.word())
                .suggests(modeSuggestions)
                .executes((ctx -> changeMode(ctx, StringArgumentType.getString(ctx, "configmode"), false)))
                .then(Commands.argument("noreload", StringArgumentType.word())
                    .suggests((ctx, builder) -> ISuggestionProvider.suggest(Collections.singleton("noreload"), builder))
                    .executes((ctx -> changeMode(ctx, StringArgumentType.getString(ctx, "configmode"), true))))));
    }

    private static int changeMode(CommandContext<CommandSource> context, String mode, boolean noreload) throws CommandSyntaxException {
        try {
            if (!ConfigSwapper.modes.contains(mode)) {
                Message message = new LiteralMessage(mode + " is not an available mode");
                throw new CommandSyntaxException(new SimpleCommandExceptionType(message), message);
            }

            if (context.getSource().getServer().isDedicatedServer()) {
                if (!context.getSource().hasPermissionLevel(4)) {
                    context.getSource().sendFeedback(new StringTextComponent("Mode changing requires at least Permission Level 2"), true);
                    return Command.SINGLE_SUCCESS;
                }

            } else {

                if (!context.getSource().getServer().getServerOwner().equals(context.getSource().asPlayer().getName().getString())) {
                    context.getSource().sendFeedback(new StringTextComponent("Mode changing can only be done by the host"), true);
                    return Command.SINGLE_SUCCESS;
                }
            }

            context.getSource().sendFeedback(new StringTextComponent("Changing mode to " + mode), true);

            if (!noreload)
                context.getSource().sendFeedback(new StringTextComponent("Expect a large Lag spike. A restart may be required apply all changes."), true);

            ModeConfig modeConfig = new ModeConfig(mode);
            modeConfig.applyMode(false);
            Utils.readWriteModeToJson(mode);

            if (context.getSource().getServer() instanceof DedicatedServer) {
                context.getSource().getServer().getPlayerList().getPlayers().forEach(
                    player -> ConfigSwapper.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ConfigChangeMessage(mode, true)));
            }
            if (!noreload)
                context.getSource().getServer().getCommandManager().handleCommand(context.getSource(), "reload");

            return Command.SINGLE_SUCCESS;

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

}
