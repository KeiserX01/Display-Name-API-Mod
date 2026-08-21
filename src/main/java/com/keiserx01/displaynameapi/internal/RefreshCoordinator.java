package com.keiserx01.displaynameapi.internal;

import com.keiserx01.displaynameapi.network.NetworkHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Coordinates refresh operations across the three display destinations:
 * Tab list, nameplate, and chat.
 * Package-private for internal use only.
 */
final class RefreshCoordinator {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private RefreshCoordinator() {}
    
    /**
     * Refreshes all display destinations for a player after their nickname data changes.
     * 
     * @param player The player to refresh
     */
    static void refreshAll(ServerPlayer player) {
        // Refresh tab list name - sends ClientboundPlayerInfoUpdatePacket with UPDATE_DISPLAY_NAME
        player.refreshTabListName();
        
        // Get composed nickname FIRST
        Component composed = NicknameManager.getInstance().getComposedNickname(player);
        
        // Set custom name BEFORE refreshDisplayName() for chat/tab
        if (composed != null) {
            player.setCustomName(composed);
            player.setCustomNameVisible(true);
        } else {
            player.setCustomName(null);
            player.setCustomNameVisible(false);
        }
        
        // Refresh display name - affects chat messages
        player.refreshDisplayName();
        
        // Send network packet to clients for nameplate rendering
        NetworkHandler.sendNicknameSync(player, composed);
    }
}