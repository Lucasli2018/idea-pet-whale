package com.dsh.petwhale.state;

/**
 * Machine configuration knobs. Defaults match the DSH pet host so the whale
 * behaves identically across both runtimes.
 */
public record PetStateConfig(long celebrateMs, long failureMs) {
    public static final PetStateConfig DEFAULT = new PetStateConfig(2400L, 2400L);
}