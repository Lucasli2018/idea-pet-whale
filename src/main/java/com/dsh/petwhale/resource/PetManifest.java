package com.dsh.petwhale.resource;

import com.dsh.petwhale.state.PetAnimation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parsed pet manifest. Mirrors the dsh-pet {@pet.json} shape — atlas
 * geometry, per-row frame counts, and per-track frame durations. Two
 * built-in themes ship in {@resources/images/}: {@WHALE} (original) and
 * {@WHALE_REFINED} (AI-refined).
 *
 * <p>The class is intentionally minimal — fail-closed parsing rejects
 * missing or malformed manifests; nothing here holds an open resource.
 */
public final class PetManifest {

    public static final int CELL_WIDTH = 192;
    public static final int CELL_HEIGHT = 208;
    public static final int COLUMNS = 8;
    public static final int ROWS = 9;

    /** Default per-row frame counts from the dsh-pet hatch-pet contract. */
    public static final int[] DEFAULT_FRAMES = { 6, 8, 8, 4, 5, 8, 6, 6, 6 };

    /** Default per-track frame rhythm (mirror of the whale refined bundle). */
    public static final Map<PetAnimation, int[]> DEFAULT_DURATIONS;
    static {
        TreeMap<PetAnimation, int[]> map = new TreeMap<>();
        map.put(PetAnimation.IDLE,          new int[] { 500, 500, 600, 500, 500, 600 });
        map.put(PetAnimation.RUNNING_RIGHT, new int[] { 300, 300, 300, 300, 300, 300, 300, 400 });
        map.put(PetAnimation.RUNNING_LEFT,  new int[] { 300, 300, 300, 300, 300, 300, 300, 400 });
        map.put(PetAnimation.WAVING,        new int[] { 450, 450, 450, 450 });
        map.put(PetAnimation.JUMPING,       new int[] { 400, 400, 400, 450, 450 });
        map.put(PetAnimation.FAILED,        new int[] { 550, 550, 550, 600, 650, 700, 550, 550 });
        map.put(PetAnimation.WAITING,       new int[] { 550, 550, 600, 550, 550, 600 });
        map.put(PetAnimation.RUNNING,       new int[] { 330, 330, 330, 330, 330, 400 });
        map.put(PetAnimation.REVIEW,        new int[] { 650, 650, 650, 650, 650, 650 });
        DEFAULT_DURATIONS = Collections.unmodifiableMap(map);
    }

    private final String id;
    private final String displayName;
    private final String description;
    private final String spritesheetPath;
    private final int[] frames;
    private final Map<PetAnimation, int[]> durations;

    public PetManifest(
            @NotNull String id,
            @NotNull String displayName,
            @Nullable String description,
            @NotNull String spritesheetPath,
            @NotNull int[] frames,
            @NotNull Map<PetAnimation, int[]> durations
    ) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.spritesheetPath = spritesheetPath;
        this.frames = frames.clone();
        this.durations = new HashMap<>();
        for (Map.Entry<PetAnimation, int[]> entry : durations.entrySet()) {
            this.durations.put(entry.getKey(), entry.getValue().clone());
        }
    }

    @NotNull public String id() { return id; }
    @NotNull public String displayName() { return displayName; }
    @Nullable public String description() { return description; }
    @NotNull public String spritesheetPath() { return spritesheetPath; }

    @NotNull public int[] frames() { return frames.clone(); }

    public int frameCount(int row) {
        if (row < 0 || row >= ROWS) {
            throw new IndexOutOfBoundsException("row " + row + " out of [0," + ROWS + ")");
        }
        return frames[row];
    }

    @NotNull public int[] durations(@NotNull PetAnimation animation) {
        int[] stored = durations.get(animation);
        if (stored != null) return stored.clone();
        int[] fallback = DEFAULT_DURATIONS.get(animation);
        return fallback == null ? new int[0] : fallback.clone();
    }

    /**
     * Default durations that cycle to the row's frame count — exactly the
     * hatch-pet contract for animations whose track has no override.
     */
    @NotNull public int[] defaultDurationsForRow(int row) {
        int count = frameCount(row);
        int[] pattern = DEFAULT_DURATIONS.get(PetAnimation.IDLE);
        if (pattern == null || pattern.length == 0) return new int[count];
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = pattern[i % pattern.length];
        }
        return out;
    }
}