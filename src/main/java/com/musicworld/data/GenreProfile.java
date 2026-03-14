package com.musicworld.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable data record describing the world generation parameters for a music genre.
 */
public class GenreProfile {

    public final float  terrainRoughness;
    public final int    baseHeight;
    public final float  mountainFrequency;
    public final float  caveFrequency;
    public final String primaryBlock;
    public final String secondaryBlock;
    public final String surfaceBlock;
    public final float  treeFrequency;
    public final int    waterLevel;
    public final String structureType;

    public GenreProfile(
            float  terrainRoughness,
            int    baseHeight,
            float  mountainFrequency,
            float  caveFrequency,
            String primaryBlock,
            String secondaryBlock,
            String surfaceBlock,
            float  treeFrequency,
            int    waterLevel,
            String structureType) {
        this.terrainRoughness  = terrainRoughness;
        this.baseHeight        = baseHeight;
        this.mountainFrequency = mountainFrequency;
        this.caveFrequency     = caveFrequency;
        this.primaryBlock      = primaryBlock;
        this.secondaryBlock    = secondaryBlock;
        this.surfaceBlock      = surfaceBlock;
        this.treeFrequency     = treeFrequency;
        this.waterLevel        = waterLevel;
        this.structureType     = structureType;
    }

    // -------------------------------------------------------------------------
    // Genre registry — keys are lowercase for case-insensitive lookup
    // -------------------------------------------------------------------------

    public static final Map<String, GenreProfile> GENRES = new HashMap<>();

    static {
        GENRES.put("metal", new GenreProfile(
                0.9f, 80, 0.8f, 0.7f,
                "BLACKSTONE", "OBSIDIAN", "GRAVEL",
                0.1f, 20, "PILLARS"));

        GENRES.put("jazz", new GenreProfile(
                0.5f, 65, 0.3f, 0.4f,
                "SMOOTH_STONE", "STONE", "GRASS_BLOCK",
                0.5f, 55, "BUILDINGS"));

        GENRES.put("classical", new GenreProfile(
                0.3f, 70, 0.2f, 0.2f,
                "STONE", "QUARTZ_BLOCK", "GRASS_BLOCK",
                0.6f, 60, "COLUMNS"));

        GENRES.put("hiphop", new GenreProfile(
                0.6f, 68, 0.4f, 0.6f,
                "GRAY_CONCRETE", "STONE", "COARSE_DIRT",
                0.2f, 50, "PLATFORMS"));

        GENRES.put("electronic", new GenreProfile(
                0.95f, 75, 0.9f, 0.8f,
                "PURPUR_BLOCK", "END_STONE", "SOUL_SAND",
                0.0f, 30, "NONE"));

        GENRES.put("pop", new GenreProfile(
                0.2f, 64, 0.1f, 0.2f,
                "STONE", "DIORITE", "GRASS_BLOCK",
                0.8f, 62, "GAZEBOS"));

        GENRES.put("ambient", new GenreProfile(
                0.1f, 62, 0.05f, 0.1f,
                "STONE", "CLAY", "MOSS_BLOCK",
                0.4f, 58, "RUINS"));
    }
}
