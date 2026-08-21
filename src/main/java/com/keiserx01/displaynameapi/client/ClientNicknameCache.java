package com.keiserx01.displaynameapi.client;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for player nicknames received from the server.
 * Used by the RenderNameplateEvent handler to render custom nameplates.
 */
public final class ClientNicknameCache {

    private static final ClientNicknameCache INSTANCE = new ClientNicknameCache();
    private final Map<UUID, Component> nicknameCache = new ConcurrentHashMap<>();

    private ClientNicknameCache() {}

    public static ClientNicknameCache getInstance() {
        return INSTANCE;
    }

    /**
     * Sets the nickname for a player in the client cache.
     *
     * @param playerId The player's UUID
     * @param nickname The composed nickname component, or null to reset
     */
    public void setNickname(UUID playerId, @Nullable Component nickname) {
        if (nickname == null) {
            nicknameCache.remove(playerId);
        } else {
            nicknameCache.put(playerId, nickname);
        }
    }

    /**
     * Gets the nickname for a player from the client cache.
     *
     * @param playerId The player's UUID
     * @return The composed nickname component, or null if not found
     */
    @Nullable
    public Component getNickname(UUID playerId) {
        return nicknameCache.get(playerId);
    }

    /**
     * Checks if a player has a custom nickname in the cache.
     *
     * @param playerId The player's UUID
     * @return true if the player has a custom nickname
     */
    public boolean hasNickname(UUID playerId) {
        return nicknameCache.containsKey(playerId);
    }

    /**
     * Clears all cached nicknames.
     * Called when the client disconnects from a server.
     */
    public void clear() {
        nicknameCache.clear();
    }
}