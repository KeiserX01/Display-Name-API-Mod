package com.keiserx01.displaynameapi.internal;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.lang.reflect.Field;

/**
 * Coordinates refresh operations across the three display destinations:
 * Tab list, nameplate, and chat.
 * Package-private for internal use only.
 */
final class RefreshCoordinator {
    
    // Cached reflection fields for Entity's private DATA_CUSTOM_NAME accessors
    private static Field DATA_CUSTOM_NAME_FIELD;
    private static Field DATA_CUSTOM_NAME_VISIBLE_FIELD;
    private static boolean reflectionInitialized = false;
    
    /**
     * Private constructor to prevent instantiation.
     */
    private RefreshCoordinator() {}
    
    /**
     * Initializes reflection access to Entity's private custom name data accessors.
     */
    private static void initReflection() {
        if (reflectionInitialized) return;
        try {
            DATA_CUSTOM_NAME_FIELD = Entity.class.getDeclaredField("DATA_CUSTOM_NAME");
            DATA_CUSTOM_NAME_FIELD.setAccessible(true);
            DATA_CUSTOM_NAME_VISIBLE_FIELD = Entity.class.getDeclaredField("DATA_CUSTOM_NAME_VISIBLE");
            DATA_CUSTOM_NAME_VISIBLE_FIELD.setAccessible(true);
            reflectionInitialized = true;
        } catch (NoSuchFieldException e) {
            // Fallback: fields might have different names in this version
            reflectionInitialized = true;
        }
    }
    
    /**
     * Refreshes all display destinations for a player after their nickname data changes.
     * 
     * @param player The player to refresh
     */
    static void refreshAll(ServerPlayer player) {
        // Refresh tab list name - sends ClientboundPlayerInfoUpdatePacket with UPDATE_DISPLAY_NAME
        player.refreshTabListName();
        
        // Get composed nickname BEFORE refreshDisplayName
        Component composed = NicknameManager.getInstance().getComposedNickname(player);
        
        // Set custom name on entity (this updates entity metadata for nameplate)
        // Must be done before refreshDisplayName() so the event sees the correct value
        if (composed != null) {
            player.setCustomName(composed);
            player.setCustomNameVisible(true);
        } else {
            player.setCustomName(null);
            player.setCustomNameVisible(false);
        }
        
        // Refresh display name - affects nameplate (via custom name) and future chat messages
        player.refreshDisplayName();
        
        // Note: Chat does not need explicit refresh.
        // Future chat messages will automatically use the updated display name
        // via ChatType.Bound which derives from getDisplayName().
    }
}