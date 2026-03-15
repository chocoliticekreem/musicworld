package com.musicworld.worldgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainBlendTest {

    // -------------------------------------------------------------------------
    // smoothStep
    // -------------------------------------------------------------------------

    @Test
    void smoothStep_atZero_returnsZero() {
        assertEquals(0.0, TerrainBlend.smoothStep(0.0), 1e-9);
    }

    @Test
    void smoothStep_atOne_returnsOne() {
        assertEquals(1.0, TerrainBlend.smoothStep(1.0), 1e-9);
    }

    @Test
    void smoothStep_atHalf_returnsHalf() {
        assertEquals(0.5, TerrainBlend.smoothStep(0.5), 1e-9);
    }

    @Test
    void smoothStep_clampsBelowZero() {
        assertEquals(0.0, TerrainBlend.smoothStep(-5.0), 1e-9);
    }

    @Test
    void smoothStep_clampsAboveOne() {
        assertEquals(1.0, TerrainBlend.smoothStep(5.0), 1e-9);
    }

    @Test
    void smoothStep_isMonotonicallyIncreasing() {
        double prev = 0.0;
        for (int i = 1; i <= 10; i++) {
            double t = i / 10.0;
            double val = TerrainBlend.smoothStep(t);
            assertTrue(val > prev, "Expected monotonic increase at t=" + t);
            prev = val;
        }
    }

    // -------------------------------------------------------------------------
    // blendFactor
    // -------------------------------------------------------------------------

    @Test
    void blendFactor_farWestOfSwitch_returnsZero() {
        // A column many chunks west of the switch → fully old genre
        int switchChunkX = 100;
        int worldX = (switchChunkX - TerrainBlend.BLEND_RADIUS - 5) * 16;
        assertEquals(0.0, TerrainBlend.blendFactor(worldX, switchChunkX), 1e-9);
    }

    @Test
    void blendFactor_farEastOfSwitch_returnsOne() {
        // A column many chunks east of the switch → fully new genre
        int switchChunkX = 100;
        int worldX = (switchChunkX + TerrainBlend.BLEND_RADIUS + 5) * 16;
        assertEquals(1.0, TerrainBlend.blendFactor(worldX, switchChunkX), 1e-9);
    }

    @Test
    void blendFactor_exactlyAtSwitch_returnsHalf() {
        // A column exactly at the switch chunk boundary → 0.5
        int switchChunkX = 50;
        int worldX = switchChunkX * 16;
        assertEquals(0.5, TerrainBlend.blendFactor(worldX, switchChunkX), 1e-9);
    }

    @Test
    void blendFactor_increasesFromWestToEast() {
        int switchChunkX = 0;
        double prev = -1.0;
        for (int chunkOffset = -TerrainBlend.BLEND_RADIUS - 1; chunkOffset <= TerrainBlend.BLEND_RADIUS + 1; chunkOffset++) {
            int worldX = (switchChunkX + chunkOffset) * 16;
            double factor = TerrainBlend.blendFactor(worldX, switchChunkX);
            assertTrue(factor >= prev, "Expected non-decreasing blend factor at chunkOffset=" + chunkOffset);
            prev = factor;
        }
    }

    // -------------------------------------------------------------------------
    // blendParam
    // -------------------------------------------------------------------------

    @Test
    void blendParam_farFromSwitch_returnsOldValue() {
        int switchChunkX = 100;
        int worldX = (switchChunkX - TerrainBlend.BLEND_RADIUS - 5) * 16;
        double result = TerrainBlend.blendParam(64.0, 80.0, worldX, switchChunkX);
        assertEquals(64.0, result, 1e-6);
    }

    @Test
    void blendParam_farPastSwitch_returnsNewValue() {
        int switchChunkX = 100;
        int worldX = (switchChunkX + TerrainBlend.BLEND_RADIUS + 5) * 16;
        double result = TerrainBlend.blendParam(64.0, 80.0, worldX, switchChunkX);
        assertEquals(80.0, result, 1e-6);
    }

    @Test
    void blendParam_atSwitch_returnsMidpoint() {
        int switchChunkX = 50;
        int worldX = switchChunkX * 16;
        double result = TerrainBlend.blendParam(60.0, 80.0, worldX, switchChunkX);
        assertEquals(70.0, result, 1e-6);
    }

    @Test
    void blendParam_staysWithinOldAndNewBounds() {
        int switchChunkX = 0;
        double oldVal = 64.0, newVal = 80.0;
        for (int chunkOffset = -10; chunkOffset <= 10; chunkOffset++) {
            int worldX = chunkOffset * 16;
            double result = TerrainBlend.blendParam(oldVal, newVal, worldX, switchChunkX);
            assertTrue(result >= oldVal && result <= newVal,
                "blendParam out of bounds at chunkOffset=" + chunkOffset + ": " + result);
        }
    }
}
