package com.dsh.petwhale.resource;

/** Two built-in themes — the original and the AI-refined whale-girl atlases. */
public enum PetTheme {
    WHALE("/images/whale/pet.json"),
    WHALE_REFINED("/images/whale-refined/pet.json");

    private final String manifestResource;

    PetTheme(String manifestResource) {
        this.manifestResource = manifestResource;
    }

    public String manifestResource() {
        return manifestResource;
    }
}