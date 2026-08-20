package com.keiserx01.displaynameapi.internal;

import com.keiserx01.displaynameapi.api.DisplayNameApi;
import com.keiserx01.displaynameapi.api.Prefix;
import com.keiserx01.displaynameapi.api.Suffix;
import com.keiserx01.displaynameapi.api.exceptions.PriorityCollisionException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for player nickname data.
 * Handles registration, updates, removal, and composition of nicknames.
 * Public implementation of DisplayNameApi for other mods to use.
 */
public final class NicknameManager implements DisplayNameApi {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("DisplayNameAPI/NicknameManager");
    
    // Map of player UUID to their NicknameData
    private final Map<UUID, NicknameData> playerData = new ConcurrentHashMap<>();
    
    // Reference to the server for getting player instances
    private MinecraftServer server;
    
    /**
     * Private constructor. Use getInstance() to access.
     */
    private NicknameManager() {}
    
    /**
     * Singleton instance.
     */
    private static final NicknameManager INSTANCE = new NicknameManager();
    
    /**
     * @return The singleton instance
     */
    public static NicknameManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Sets the server reference (called during mod initialization).
     * 
     * @param server The Minecraft server instance
     */
    public void setServer(MinecraftServer server) {
        this.server = server;
    }
    
    /**
     * Gets the NicknameData for a player, creating it if it doesn't exist.
     * 
     * @param playerId The player's UUID
     * @return The NicknameData instance
     */
    private NicknameData getOrCreateData(UUID playerId) {
        return playerData.computeIfAbsent(playerId, NicknameData::new);
    }
    
    /**
     * Gets the NicknameData for a player without creating it.
     * 
     * @param playerId The player's UUID
     * @return The NicknameData instance, or null if not found
     */
    private NicknameData getData(UUID playerId) {
        return playerData.get(playerId);
    }
    
    /**
     * Removes a player's data when they disconnect.
     * 
     * @param playerId The player's UUID
     */
    void onPlayerDisconnect(UUID playerId) {
        playerData.remove(playerId);
    }
    
    /**
     * Validates that a player is online and returns the ServerPlayer instance.
     * 
     * @param player The player to validate
     * @return The validated ServerPlayer
     * @throws IllegalArgumentException if player is null or offline
     */
    private ServerPlayer validatePlayer(ServerPlayer player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (!player.isAlive() || player.hasDisconnected()) {
            throw new IllegalArgumentException("Player is offline or disconnected");
        }
        return player;
    }
    
    /**
     * Applies the namespace prefix to an ID.
     * 
     * @param id The raw ID
     * @return The namespaced ID
     */
    private String namespace(String id) {
        return DisplayNameApi.NAMESPACE + id;
    }
    
    /**
     * Removes the namespace prefix from an ID.
     * 
     * @param namespacedId The namespaced ID
     * @return The raw ID, or the input if it doesn't have the namespace
     */
    String unnamespace(String namespacedId) {
        if (namespacedId.startsWith(DisplayNameApi.NAMESPACE)) {
            return namespacedId.substring(DisplayNameApi.NAMESPACE.length());
        }
        return namespacedId;
    }
    
    /**
     * Registers or updates a prefix for a player.
     * 
     * @param player   Target player
     * @param id       Raw ID (will be namespaced)
     * @param priority Priority value
     * @param value    Component value
     * @throws IllegalArgumentException     if validation fails
     * @throws PriorityCollisionException   if priority collision detected
     */
    @Override
    public void setPrefix(ServerPlayer player, String id, int priority, Component value) {
        validatePlayer(player);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Prefix id cannot be null or empty");
        }
        if (value == null) {
            throw new IllegalArgumentException("Prefix value cannot be null");
        }
        
        String namespacedId = namespace(id);
        UUID playerId = player.getUUID();
        NicknameData data = getOrCreateData(playerId);
        
        // Check for collision before modifying
        for (Prefix existing : data.getPrefixes().values()) {
            if (existing.priority() == priority && !existing.id().equals(namespacedId)) {
                throw new PriorityCollisionException(priority, unnamespace(existing.id()), id, true);
            }
        }
        
        Prefix prefix = new Prefix(namespacedId, priority, value);
        data.putPrefix(namespacedId, prefix);
        
        // Trigger refresh
        RefreshCoordinator.refreshAll(player);
        
        LOGGER.debug("Set prefix '{}' for player '{}' (priority: {})", namespacedId, player.getName().getString(), priority);
    }
    
    /**
     * Registers or updates a suffix for a player.
     * 
     * @param player   Target player
     * @param id       Raw ID (will be namespaced)
     * @param priority Priority value
     * @param value    Component value
     * @throws IllegalArgumentException     if validation fails
     * @throws PriorityCollisionException   if priority collision detected
     */
    @Override
    public void setSuffix(ServerPlayer player, String id, int priority, Component value) {
        validatePlayer(player);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Suffix id cannot be null or empty");
        }
        if (value == null) {
            throw new IllegalArgumentException("Suffix value cannot be null");
        }
        
        String namespacedId = namespace(id);
        UUID playerId = player.getUUID();
        NicknameData data = getOrCreateData(playerId);
        
        // Check for collision before modifying
        for (Suffix existing : data.getSuffixes().values()) {
            if (existing.priority() == priority && !existing.id().equals(namespacedId)) {
                throw new PriorityCollisionException(priority, unnamespace(existing.id()), id, false);
            }
        }
        
        Suffix suffix = new Suffix(namespacedId, priority, value);
        data.putSuffix(namespacedId, suffix);
        
        // Trigger refresh
        RefreshCoordinator.refreshAll(player);
        
        LOGGER.debug("Set suffix '{}' for player '{}' (priority: {})", namespacedId, player.getName().getString(), priority);
    }
    
    /**
     * Removes a prefix from a player.
     * 
     * @param player Target player
     * @param id     Raw ID (will be namespaced)
     * @return true if the prefix existed and was removed
     * @throws IllegalArgumentException if validation fails
     */
    @Override
    public boolean removePrefix(ServerPlayer player, String id) {
        validatePlayer(player);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Prefix id cannot be null or empty");
        }
        
        String namespacedId = namespace(id);
        UUID playerId = player.getUUID();
        NicknameData data = getData(playerId);
        
        if (data == null) {
            return false;
        }
        
        Prefix removed = data.removePrefix(namespacedId);
        
        if (removed != null) {
            RefreshCoordinator.refreshAll(player);
            LOGGER.debug("Removed prefix '{}' for player '{}'", namespacedId, player.getName().getString());
            return true;
        }
        
        return false;
    }
    
    /**
     * Removes a suffix from a player.
     * 
     * @param player Target player
     * @param id     Raw ID (will be namespaced)
     * @return true if the suffix existed and was removed
     * @throws IllegalArgumentException if validation fails
     */
    @Override
    public boolean removeSuffix(ServerPlayer player, String id) {
        validatePlayer(player);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Suffix id cannot be null or empty");
        }
        
        String namespacedId = namespace(id);
        UUID playerId = player.getUUID();
        NicknameData data = getData(playerId);
        
        if (data == null) {
            return false;
        }
        
        Suffix removed = data.removeSuffix(namespacedId);
        
        if (removed != null) {
            RefreshCoordinator.refreshAll(player);
            LOGGER.debug("Removed suffix '{}' for player '{}'", namespacedId, player.getName().getString());
            return true;
        }
        
        return false;
    }
    
    /**
     * Removes all prefixes and suffixes from a player.
     * 
     * @param player Target player
     * @throws IllegalArgumentException if validation fails
     */
    @Override
    public void resetNickname(ServerPlayer player) {
        validatePlayer(player);
        
        UUID playerId = player.getUUID();
        NicknameData data = getData(playerId);
        
        if (data != null) {
            data.clear();
            // Clean up empty data
            if (data.isEmpty()) {
                playerData.remove(playerId);
            }
            RefreshCoordinator.refreshAll(player);
            LOGGER.debug("Reset nickname for player '{}'", player.getName().getString());
        }
    }
    
    /**
     * Gets the composed nickname for a player.
     * 
     * @param player Target player
     * @return The composed Component, or null if no prefixes/suffixes
     * @throws IllegalArgumentException if validation fails
     */
    @Override
    public Component getComposedNickname(ServerPlayer player) {
        validatePlayer(player);
        
        UUID playerId = player.getUUID();
        NicknameData data = getData(playerId);
        
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        // Get the player's base name
        Component playerName = Component.literal(player.getName().getString());
        
        return CompositionEngine.compose(playerName, data.getPrefixes(), data.getSuffixes());
    }
    
    /**
     * Checks if a player has any registered prefixes or suffixes.
     * 
     * @param player Target player
     * @return true if the player has at least one prefix or suffix
     * @throws IllegalArgumentException if validation fails
     */
    @Override
    public boolean hasNicknameData(ServerPlayer player) {
        validatePlayer(player);
        
        UUID playerId = player.getUUID();
        NicknameData data = getData(playerId);
        
        return data != null && !data.isEmpty();
    }
    
    /**
     * Gets the NicknameData for a player (for event handlers).
     * 
     * @param playerId The player's UUID
     * @return The NicknameData, or null if not found
     */
    NicknameData getNicknameData(UUID playerId) {
        return getData(playerId);
    }
    
    /**
     * Clears all player data (used on server shutdown).
     */
    public void clearAll() {
        playerData.clear();
    }
}