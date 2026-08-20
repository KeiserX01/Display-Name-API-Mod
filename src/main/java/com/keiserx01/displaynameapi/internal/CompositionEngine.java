package com.keiserx01.displaynameapi.internal;

import com.keiserx01.displaynameapi.api.Prefix;
import com.keiserx01.displaynameapi.api.Suffix;
import com.keiserx01.displaynameapi.api.exceptions.PriorityCollisionException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Composition engine responsible for building the final nickname from prefixes and suffixes.
 * Handles priority-based ordering, collision detection, and visual isolation.
 * Package-private for internal use only.
 */
final class CompositionEngine {
    
    private static final Component RESET_COMPONENT = Component.empty().withStyle(Style.EMPTY);
    
    /**
     * Private constructor to prevent instantiation.
     */
    private CompositionEngine() {}
    
    /**
     * Composes the final nickname from prefixes, player name, and suffixes.
     * 
     * @param playerName  The player's base name (username)
     * @param prefixes    Map of namespaced ID to Prefix
     * @param suffixes    Map of namespaced ID to Suffix
     * @return The composed Component, or null if no prefixes/suffixes exist
     * @throws PriorityCollisionException if duplicate priorities are detected
     */
    static Component compose(Component playerName, Map<String, Prefix> prefixes, Map<String, Suffix> suffixes) {
        if (prefixes.isEmpty() && suffixes.isEmpty()) {
            return null;
        }
        
        // Check for priority collisions before composing
        checkPriorityCollisions(prefixes, suffixes);
        
        // Build prefix list sorted by priority (highest first)
        List<Prefix> sortedPrefixes = new ArrayList<>(prefixes.values());
        sortedPrefixes.sort(Comparator.comparingInt((Prefix p) -> p.priority()).reversed());
        
        // Build suffix list sorted by priority (highest first, closest to name)
        List<Suffix> sortedSuffixes = new ArrayList<>(suffixes.values());
        sortedSuffixes.sort(Comparator.comparingInt((Suffix s) -> s.priority()).reversed());
        
        // Compose the final component
        MutableComponent result = Component.empty();
        
        // Add prefixes (highest priority first = closest to name)
        for (Prefix prefix : sortedPrefixes) {
            result.append(prefix.value());
            // Add visual isolation reset
            result.append(RESET_COMPONENT);
        }
        
        // Add player name with reset
        result.append(playerName);
        result.append(RESET_COMPONENT);
        
        // Add suffixes (highest priority first = closest to name)
        for (Suffix suffix : sortedSuffixes) {
            result.append(suffix.value());
            // Add visual isolation reset
            result.append(RESET_COMPONENT);
        }
        
        return result;
    }
    
    /**
     * Checks for priority collisions in prefixes and suffixes.
     * Throws exception if any duplicate priorities are found.
     * 
     * @param prefixes Map of prefixes
     * @param suffixes Map of suffixes
     * @throws PriorityCollisionException if duplicate priorities detected
     */
    private static void checkPriorityCollisions(Map<String, Prefix> prefixes, Map<String, Suffix> suffixes) {
        // Check prefixes
        Map<Integer, String> prefixPriorities = new java.util.HashMap<>();
        for (Map.Entry<String, Prefix> entry : prefixes.entrySet()) {
            int priority = entry.getValue().priority();
            String existingId = prefixPriorities.get(priority);
            if (existingId != null) {
                throw new PriorityCollisionException(priority, existingId, entry.getKey(), true);
            }
            prefixPriorities.put(priority, entry.getKey());
        }
        
        // Check suffixes
        Map<Integer, String> suffixPriorities = new java.util.HashMap<>();
        for (Map.Entry<String, Suffix> entry : suffixes.entrySet()) {
            int priority = entry.getValue().priority();
            String existingId = suffixPriorities.get(priority);
            if (existingId != null) {
                throw new PriorityCollisionException(priority, existingId, entry.getKey(), false);
            }
            suffixPriorities.put(priority, entry.getKey());
        }
    }
    
    /**
     * Creates a reset component for visual isolation.
     * Uses a reset style that clears all formatting.
     * 
     * @return A component with reset style
     */
    static Component createReset() {
        return RESET_COMPONENT;
    }
}