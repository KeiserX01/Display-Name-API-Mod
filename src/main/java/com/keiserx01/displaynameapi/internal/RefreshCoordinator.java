package com.keiserx01.displaynameapi.internal;

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
        
        // Refresh display name - affects nameplate and future chat messages
        player.refreshDisplayName();
        
        // FIX: Manually sync custom name for nameplate (above head)
        // In 1.21.1, refreshDisplayName() does not update entity metadata (DATA_CUSTOM_NAME)
        // with the value from NameFormat event, so we set it explicitly.
        Component composed = NicknameManager.getInstance().getComposedNickname(player);
        if (composed != null) {
            player.setCustomName(composed);
            player.setCustomNameVisible(true);
        } else {
            player.setCustomName(null);
            player.setCustomNameVisible(false);
        }
        
        // Note: Chat does not need explicit refresh.
        // Future chat messages will automatically use the updated display name
        // via ChatType.Bound which derives from getDisplayName().
    }
}