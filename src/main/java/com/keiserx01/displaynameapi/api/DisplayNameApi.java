package com.keiserx01.displaynameapi.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Public API interface for the DisplayName API mod.
 * <p>
 * This interface provides methods for registering and managing player prefixes and suffixes.
 * All methods must be called from the server thread.
 * </p>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // When player levels up
 * public void onPlayerLevelUp(ServerPlayer player, int newLevel) {
 *     Component levelPrefix = Component.literal("[Lvl " + newLevel + "]")
 *         .withStyle(ChatFormatting.GOLD);
 *     
 *     displayNameApi.setPrefix(player, "level", 10, levelPrefix);
 *     // API automatically refreshes tab, nameplate, and future chat messages
 * }
 * }</pre>
 */
public interface DisplayNameApi {
    
    /**
     * The namespace prefix applied to all registered IDs.
     */
    String NAMESPACE = "displayname-api:";
    
    /**
     * Registers or updates a prefix for a player.
     * 
     * @param player    Target player (must be online)
     * @param id        Unique identifier (will be namespaced as displayname-api:{id})
     * @param priority  Priority value (higher = closer to name; can be positive, zero, or negative)
     * @param value     Fully resolved Component (no placeholders)
     * @throws IllegalArgumentException     if player is null or offline, id is empty, or value is null
     * @throws PriorityCollisionException   if another prefix already has the same priority
     */
    void setPrefix(ServerPlayer player, String id, int priority, Component value);
    
    /**
     * Registers or updates a suffix for a player.
     * 
     * @param player    Target player (must be online)
     * @param id        Unique identifier (will be namespaced as displayname-api:{id})
     * @param priority  Priority value (higher = closer to name; can be positive, zero, or negative)
     * @param value     Fully resolved Component (no placeholders)
     * @throws IllegalArgumentException     if player is null or offline, id is empty, or value is null
     * @throws PriorityCollisionException   if another suffix already has the same priority
     */
    void setSuffix(ServerPlayer player, String id, int priority, Component value);
    
    /**
     * Removes a specific prefix from a player.
     * 
     * @param player  Target player
     * @param id      The identifier of the prefix to remove (without namespace)
     * @return true if the prefix existed and was removed
     * @throws IllegalArgumentException if player is null or offline, or id is empty
     */
    boolean removePrefix(ServerPlayer player, String id);
    
    /**
     * Removes a specific suffix from a player.
     * 
     * @param player  Target player
     * @param id      The identifier of the suffix to remove (without namespace)
     * @return true if the suffix existed and was removed
     * @throws IllegalArgumentException if player is null or offline, or id is empty
     */
    boolean removeSuffix(ServerPlayer player, String id);
    
    /**
     * Removes all prefixes and suffixes from a player.
     * After this call, the player's display name reverts to their vanilla username.
     * This operation cannot be canceled or denied by event listeners.
     * 
     * @param player  Target player
     * @throws IllegalArgumentException if player is null or offline
     */
    void resetNickname(ServerPlayer player);
    
    /**
     * Retrieves the current composed nickname for a player.
     * 
     * @param player  Target player
     * @return Composed Component, or null if no prefixes/suffixes are registered
     * @throws IllegalArgumentException if player is null or offline
     */
    @Nullable Component getComposedNickname(ServerPlayer player);
    
    /**
     * Checks if a player has any registered prefixes or suffixes.
     * 
     * @param player  Target player
     * @return true if the player has at least one prefix or suffix
     * @throws IllegalArgumentException if player is null or offline
     */
    boolean hasNicknameData(ServerPlayer player);
}