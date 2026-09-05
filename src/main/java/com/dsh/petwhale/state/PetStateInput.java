package com.dsh.petwhale.state;

import org.jetbrains.annotations.Nullable;

/**
 * One input snapshot consumed by the state machine. Mirrors the DSH pet
 * host's projected event payload: current phase + optional status copy.
 */
public record PetStateInput(
        PetActivityPhase phase,
        @Nullable String line,
        @Nullable String phrase
) {
    public static PetStateInput of(PetActivityPhase phase) {
        return new PetStateInput(phase, null, null);
    }

    public static PetStateInput of(PetActivityPhase phase, String line, String phrase) {
        return new PetStateInput(phase, line, phrase);
    }
}