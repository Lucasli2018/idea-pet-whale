package com.dsh.petwhale.state;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Render-decision snapshot emitted by {@PetStateMachine#render}. Pure data;
 * Swing consumers repaint off this struct without mutating it.
 */
public record PetStateSnapshot(
        @NotNull PetAnimation animation,
        @Nullable String bubble,
        long animationStartedAt,
        @NotNull PetActivityPhase phase,
        boolean sessionActive
) {
}