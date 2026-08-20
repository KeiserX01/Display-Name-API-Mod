package com.keiserx01.displaynameapi.command;

import com.keiserx01.displaynameapi.internal.NicknameManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Command for resetting player nicknames.
 * Syntax: /resetnickname <target>
 */
public class ResetNicknameCommand {
    
    /**
     * Registers the command with the dispatcher.
     * 
     * @param dispatcher The command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("resetnickname")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("target", EntityArgument.players())
                .executes(ResetNicknameCommand::execute));
        
        dispatcher.register(command);
    }
    
    /**
     * Executes the resetnickname command.
     * 
     * @param context The command context
     * @return Number of affected players
     * @throws CommandSyntaxException if command fails
     */
    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "target");
        
        NicknameManager manager = NicknameManager.getInstance();
        
        int successCount = 0;
        
        for (ServerPlayer target : targets) {
            try {
                manager.resetNickname(target);
                successCount++;
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.literal("Player '" + target.getName().getString() + "': " + e.getMessage()));
            }
        }
        
        if (successCount > 0) {
            if (targets.size() == 1) {
                ServerPlayer target = targets.iterator().next();
                source.sendSuccess(() -> Component.literal("Reset nickname for player '" + target.getName().getString() + "'"), false);
            } else {
                int finalSuccessCount = successCount;
                source.sendSuccess(() -> Component.literal("Reset nickname for " + finalSuccessCount + " player(s)"), false);
            }
        }
        
        return successCount;
    }
}