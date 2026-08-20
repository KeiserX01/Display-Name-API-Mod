package com.keiserx01.displaynameapi.internal;

import com.keiserx01.displaynameapi.api.Prefix;
import com.keiserx01.displaynameapi.api.Suffix;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player container holding all registered prefixes and suffixes.
 * Package-private for internal use only.
 */
final class NicknameData {
    
    private final UUID playerId;
    private final Map<String, Prefix> prefixes;
    private final Map<String, Suffix> suffixes;
    
    /**
     * Creates a new NicknameData for the given player.
     * 
     * @param playerId The player's UUID
     */
    NicknameData(UUID playerId) {
        this.playerId = playerId;
        this.prefixes = new HashMap<>();
        this.suffixes = new HashMap<>();
    }
    
    /**
     * @return The player's UUID
     */
    UUID getPlayerId() {
        return playerId;
    }
    
    /**
     * @return Unmodifiable view of registered prefixes
     */
    Map<String, Prefix> getPrefixes() {
        return Collections.unmodifiableMap(prefixes);
    }
    
    /**
     * @return Unmodifiable view of registered suffixes
     */
    Map<String, Suffix> getSuffixes() {
        return Collections.unmodifiableMap(suffixes);
    }
    
    /**
     * Adds or updates a prefix.
     * 
     * @param id       The namespaced ID
     * @param prefix   The prefix to add
     * @return The previous prefix if one existed, null otherwise
     */
    Prefix putPrefix(String id, Prefix prefix) {
        return prefixes.put(id, prefix);
    }
    
    /**
     * Adds or updates a suffix.
     * 
     * @param id      The namespaced ID
     * @param suffix  The suffix to add
     * @return The previous suffix if one existed, null otherwise
     */
    Suffix putSuffix(String id, Suffix suffix) {
        return suffixes.put(id, suffix);
    }
    
    /**
     * Removes a prefix by ID.
     * 
     * @param id  The namespaced ID
     * @return The removed prefix, or null if not found
     */
    Prefix removePrefix(String id) {
        return prefixes.remove(id);
    }
    
    /**
     * Removes a suffix by ID.
     * 
     * @param id  The namespaced ID
     * @return The removed suffix, or null if not found
     */
    Suffix removeSuffix(String id) {
        return suffixes.remove(id);
    }
    
    /**
     * Clears all prefixes and suffixes.
     */
    void clear() {
        prefixes.clear();
        suffixes.clear();
    }
    
    /**
     * @return true if there are any prefixes or suffixes registered
     */
    boolean isEmpty() {
        return prefixes.isEmpty() && suffixes.isEmpty();
    }
    
    /**
     * @return The number of registered prefixes
     */
    int prefixCount() {
        return prefixes.size();
    }
    
    /**
     * @return The number of registered suffixes
     */
    int suffixCount() {
        return suffixes.size();
    }
}