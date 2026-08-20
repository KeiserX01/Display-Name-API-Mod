package com.keiserx01.displaynameapi.event;

import com.keiserx01.displaynameapi.internal.NicknameManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * NeoForge event handlers for injecting composed nicknames into
 * tab list, nameplate, and chat.
 */
@EventBusSubscriber(modid = "displaynameapimod", bus = EventBusSubscriber.Bus.GAME)
public class EventHandler {
    
    /**
     * Handles tab list name formatting.
     * Fired when the tab list name is requested for a player.
     */
    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        
        NicknameManager manager = NicknameManager.getInstance();
        
        // Check if player has nickname data
        if (!manager.hasNicknameData(serverPlayer)) {
            return;
        }
        
        // Get composed nickname and apply it
        var composed = manager.getComposedNickname(serverPlayer);
        if (composed != null) {
            event.setDisplayName(composed);
        }
    }
    
    /**
     * Handles player name formatting (nameplate and chat).
     * Fired when the display name is requested for a player.
     */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        
        NicknameManager manager = NicknameManager.getInstance();
        
        // Check if player has nickname data
        if (!manager.hasNicknameData(serverPlayer)) {
            return;
        }
        
        // Get composed nickname and apply it
        var composed = manager.getComposedNickname(serverPlayer);
        if (composed != null) {
            event.setDisplayname(composed);
        }
    }
}