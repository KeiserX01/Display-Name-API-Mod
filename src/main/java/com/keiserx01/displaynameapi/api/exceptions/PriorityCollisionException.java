package com.keiserx01.displaynameapi.api.exceptions;

/**
 * Thrown when a prefix or suffix registration would create a priority collision.
 * The operation is aborted, and no changes are applied to the player's nickname.
 */
public class PriorityCollisionException extends RuntimeException {
    
    private final int conflictingPriority;
    private final String existingId;
    private final String newId;
    private final boolean isPrefix;
    
    /**
     * Creates a new PriorityCollisionException.
     * 
     * @param conflictingPriority The priority value that caused the collision
     * @param existingId          The ID of the existing prefix/suffix with that priority
     * @param newId               The ID of the prefix/suffix being registered
     * @param isPrefix            True if the collision is for prefixes, false for suffixes
     */
    public PriorityCollisionException(int conflictingPriority, String existingId, String newId, boolean isPrefix) {
        super(String.format(
            "Priority collision for %s: both '%s' and '%s' have priority %d",
            isPrefix ? "prefix" : "suffix", existingId, newId, conflictingPriority
        ));
        this.conflictingPriority = conflictingPriority;
        this.existingId = existingId;
        this.newId = newId;
        this.isPrefix = isPrefix;
    }
    
    /**
     * @return The priority value that caused the collision
     */
    public int getConflictingPriority() {
        return conflictingPriority;
    }
    
    /**
     * @return The ID of the existing prefix/suffix with that priority
     */
    public String getExistingId() {
        return existingId;
    }
    
    /**
     * @return The ID of the prefix/suffix being registered
     */
    public String getNewId() {
        return newId;
    }
    
    /**
     * @return True if the collision is for prefixes, false for suffixes
     */
    public boolean isPrefix() {
        return isPrefix;
    }
}