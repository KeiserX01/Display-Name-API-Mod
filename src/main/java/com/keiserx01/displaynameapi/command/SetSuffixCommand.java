package com.keiserx01.displaynameapi.command;

import com.keiserx01.displaynameapi.internal.NicknameManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;

/**
 * Command for setting player suffixes.
 * Syntax: /setsuffix <target> <id> <priority> <value>
 */
public class SetSuffixCommand {
    
    /**
     * Registers the command with the dispatcher.
     * 
     * @param dispatcher The command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("setsuffix")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("target", EntityArgument.players())
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("priority", IntegerArgumentType.integer())
                        .then(Commands.argument("value", StringArgumentType.string())
                            .executes(SetSuffixCommand::execute)))));
        
        dispatcher.register(command);
    }
    
    /**
     * Executes the setsuffix command.
     * 
     * @param context The command context
     * @return Number of affected players
     * @throws CommandSyntaxException if command fails
     */
    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "target");
        String id = StringArgumentType.getString(context, "id");
        int priority = IntegerArgumentType.getInteger(context, "priority");
        String value = StringArgumentType.getString(context, "value");
        
        MinecraftServer server = source.getServer();
        NicknameManager manager = NicknameManager.getInstance();
        
        // Parse the value using legacy parser
        Component component;
        try {
            component = com.keiserx01.displaynameapi.parser.LegacyParser.parse(value);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid legacy formatting: " + e.getMessage()));
            return 0;
        }
        
        int successCount = 0;
        int errorCount = 0;
        
        for (ServerPlayer target : targets) {
            try {
                manager.setSuffix(target, id, priority, component);
                successCount++;
            } catch (com.keiserx01.displaynameapi.api.exceptions.PriorityCollisionException e) {
                source.sendFailure(Component.literal("Player '" + target.getName().getString() + "': Priority collision - another suffix already has priority " + priority));
                errorCount++;
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.literal("Player '" + target.getName().getString() + "': " + e.getMessage()));
                errorCount++;
            }
        }
        
        if (successCount > 0) {
            String namespacedId = "displayname-api:" + id;
            if (targets.size() == 1) {
                ServerPlayer target = targets.iterator().next();
                source.sendSuccess(() -> Component.literal("Set suffix '" + namespacedId + "' for player '" + target.getName().getString() + "'"), false);
            } else {
                int finalSuccessCount = successCount;
                source.sendSuccess(() -> Component.literal("Set suffix '" + namespacedId + "' for " + finalSuccessCount + " player(s)"), false);
            }
        }
        
        return successCount;
    }
}