package com.musicworld.worldgen;

/**
 * Pure math helpers for blending terrain parameters between two genre profiles
 * at a genre-switch boundary. No Minecraft dependencies — fully unit-testable.
 *
 * Blend model:
 *   - When the genre changes, we record the chunk-coordinate of the switch.
 *   - For each new column (worldX, worldZ) we compute a signed distance (in chunks)
 *     from that boundary line.
 *   - Within BLEND_RADIUS chunks of the boundary the parameters are lerped
 *     using a smooth-step curve so there is no hard cliff.
 */
public final class TerrainBlend {

    private TerrainBlend() {}

    /** Half-width of the transition zone, in chunks (16 blocks each). */
    public static final int BLEND_RADIUS = 4;

    /**
     * Smooth-step: maps t in [0,1] to a smooth S-curve.
     * t=0 → 0, t=1 → 1, zero derivative at both ends.
     */
    public static double smoothStep(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Linear interpolation.
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Compute the blend factor (0.0 = fully old genre, 1.0 = fully new genre)
     * for a world column at {@code worldX}, given that the genre switched at
     * chunk X = {@code switchChunkX}.
     *
     * Columns more than BLEND_RADIUS chunks west of the switch → factor 0 (old genre).
     * Columns more than BLEND_RADIUS chunks east of the switch → factor 1 (new genre).
     * Between → smooth-stepped blend.
     *
     * We only blend in X for now (direction of travel when /genworld is issued).
     */
    public static double blendFactor(int worldX, int switchChunkX) {
        // Convert world X to chunk X
        int columnChunkX = Math.floorDiv(worldX, 16);
        // Distance in chunks from the switch boundary
        int dist = columnChunkX - switchChunkX;
        // Map into [-BLEND_RADIUS, +BLEND_RADIUS] → [0, 1]
        double t = (dist + BLEND_RADIUS) / (double) (2 * BLEND_RADIUS);
        return smoothStep(t);
    }

    /**
     * Blend a single terrain parameter (e.g. baseHeight, terrainRoughness)
     * between old and new values using the blend factor for this column.
     */
    public static double blendParam(double oldVal, double newVal, int worldX, int switchChunkX) {
        double t = blendFactor(worldX, switchChunkX);
        return lerp(oldVal, newVal, t);
    }
}
