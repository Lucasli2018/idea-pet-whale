package com.dsh.petwhale.state;

/**
 * The 9-state animation contract that maps to the spritesheet rows.
 * Row indices are fixed by the whale-girl atlas: see {@rowOf}.
 */
public enum PetAnimation {
    IDLE,            // 0
    RUNNING_RIGHT,  // 1
    RUNNING_LEFT,   // 2
    WAVING,         // 3
    JUMPING,        // 4
    FAILED,         // 5
    WAITING,        // 6
    RUNNING,        // 7
    REVIEW;         // 8

    public static PetAnimation parse(String raw) {
        if (raw == null) return IDLE;
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return IDLE;
        }
    }
}