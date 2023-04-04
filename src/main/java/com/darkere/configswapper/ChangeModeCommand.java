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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collections;

public class ChangeModeCommand {

    public static void registerCommand(RegisterCommandsEvent event) {
        ConfigSwapper.modes = Utils.readAvailableModes();

        SuggestionProvider<CommandSourceStack> modeSuggestions = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ConfigSwapper.modes, builder);

        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSourceStack>literal("mode")
            .executes(ctx -> {
                String mode = Utils.readWriteModeToJson(null);
                ctx.getSource().sendSuccess(Component.literal("You are currently in mode: " + mode), true);
                ctx.getSource().sendSuccess(Component.literal("Available modes are: " + ConfigSwapper.modes.toString()), true);

                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.argument("configmode", StringArgumentType.word())
                .suggests(modeSuggestions)
                .executes((ctx -> changeMode(ctx, StringArgumentType.getString(ctx, "configmode"), false)))
                .then(Commands.argument("noreload", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Collections.singleton("noreload"), builder))
                    .executes((ctx -> changeMode(ctx, StringArgumentType.getString(ctx, "configmode"), true))))));
    }

    private static int changeMode(CommandContext<CommandSourceStack> context, String mode, boolean noreload) throws CommandSyntaxException {
        try {
            if (!ConfigSwapper.modes.contains(mode)) {
                Message message = new LiteralMessage(mode + " is not an available mode");
                throw new CommandSyntaxException(new SimpleCommandExceptionType(message), message);
            }

            if (context.getSource().getServer().isDedicatedServer()) {
                if (!context.getSource().hasPermission(2)) {
                    context.getSource().sendSuccess(Component.literal("Mode changing requires at least Permission Level 2"), true);
                    return Command.SINGLE_SUCCESS;
                }

            } else {

                if (context.getSource().isPlayer() && !context.getSource().getServer().getSingleplayerProfile().getName().equals(context.getSource().getPlayerOrException().getName().getString())) {
                    context.getSource().sendSuccess(Component.literal("Mode changing can only be done by the host"), true);
                    return Command.SINGLE_SUCCESS;
                }
            }

            context.getSource().sendSuccess(Component.literal("Changing mode to " + mode), true);

            if (!noreload)
                context.getSource().sendSuccess(Component.literal("Expect a large Lag spike. A restart may be required apply all changes."), true);

            ModeConfig modeConfig = new ModeConfig(mode);
            modeConfig.applyMode();
            Utils.readWriteModeToJson(mode);

            if (context.getSource().getServer().getPlayerList().getPlayerCount() > 1) {
                context.getSource().getServer().getPlayerList().getPlayers().forEach(
                    player -> ConfigSwapper.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ConfigChangeMessage(mode, true)));
            }
            if (!noreload)
                context.getSource().getServer().getCommands().performPrefixedCommand(context.getSource().getServer().createCommandSourceStack(), "reload");

            return Command.SINGLE_SUCCESS;

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

}
