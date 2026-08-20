package com.keiserx01.displaynameapi;

import com.keiserx01.displaynameapi.api.DisplayNameApi;
import com.keiserx01.displaynameapi.command.ResetNicknameCommand;
import com.keiserx01.displaynameapi.command.SetPrefixCommand;
import com.keiserx01.displaynameapi.command.SetSuffixCommand;
import com.keiserx01.displaynameapi.internal.NicknameManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod class for DisplayName API.
 * <p>
 * This mod provides a server-side API for composing player nicknames
 * from multiple prefixes and suffixes with priority-based ordering.
 * </p>
 */
@Mod(DisplayNameApiMod.MOD_ID)
public class DisplayNameApiMod {
    
    public static final String MOD_ID = "displaynameapimod";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    /**
     * Creates the mod and registers event listeners.
     */
    public DisplayNameApiMod() {
        // Register this class on the MOD event bus to receive RegisterCommandsEvent
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("DisplayName API Mod initialized");
    }
    
    /**
     * Registers commands when the server starts.
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        SetPrefixCommand.register(dispatcher);
        SetSuffixCommand.register(dispatcher);
        ResetNicknameCommand.register(dispatcher);
        
        LOGGER.debug("DisplayName API commands registered");
    }
    
    /**
     * Sets the server reference in NicknameManager when the server starts.
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        NicknameManager.getInstance().setServer(server);
        LOGGER.info("DisplayName API server started");
    }
    
    /**
     * Cleans up all player data when the server stops.
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        NicknameManager.getInstance().clearAll();
        LOGGER.info("DisplayName API server stopped, data cleared");
    }
    
    /**
     * Provides access to the public API for other mods.
     * 
     * @return The DisplayNameApi implementation
     */
    public static DisplayNameApi api() {
        return NicknameManager.getInstance();
    }
}