package com.dsh.petwhale.state;

/**
 * The seven activity phases the pet understands. Mirrors the DSH pet host
 * vocabulary so the same whale-girl contract lights up identically across the
 * web client and the IDEA plugin.
 *
 * <ul>
 *   <li>{@IDLE} — no active session / passive idle</li>
 *   <li>{@WAITING} — expecting user input (e.g. question prompt)</li>
 *   <li>{@THINKING} — model is reasoning</li>
 *   <li>{@TOOL} — model is invoking a tool</li>
 *   <li>{@REVIEW} — model is streaming output back</li>
 *   <li>{@DONE} — turn completed successfully (celebrate)</li>
 *   <li>{@FAILED} — turn ended in failure</li>
 * </ul>
 */
public enum PetActivityPhase {
    IDLE,
    WAITING,
    THINKING,
    TOOL,
    REVIEW,
    DONE,
    FAILED;

    public static PetActivityPhase parse(String raw) {
        if (raw == null) return IDLE;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return IDLE;
        }
    }
}