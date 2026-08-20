package com.keiserx01.displaynameapi.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable value object representing a single prefix.
 * 
 * @param id       Unique identifier within the API namespace
 * @param priority Priority value (higher = closer to player name)
 * @param value    Fully resolved Component (no placeholders)
 */
public record Prefix(@NotNull String id, int priority, @NotNull Component value) {
    
    /**
     * Creates a new Prefix with validation.
     * 
     * @param id       Unique identifier (will be namespaced as displayname-api:{id})
     * @param priority Priority value (higher = closer to name)
     * @param value    Fully resolved Component
     * @throws IllegalArgumentException if id or value is null/empty
     */
    public Prefix {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Prefix id cannot be null or empty");
        }
        if (value == null) {
            throw new IllegalArgumentException("Prefix value cannot be null");
        }
    }
}