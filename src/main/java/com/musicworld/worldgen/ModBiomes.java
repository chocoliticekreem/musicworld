package com.musicworld.worldgen;

import com.musicworld.MusicWorldMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.Map;

public final class ModBiomes {

    public static final RegistryKey<Biome> METAL = key("metal");
    public static final RegistryKey<Biome> JAZZ = key("jazz");
    public static final RegistryKey<Biome> CLASSICAL = key("classical");
    public static final RegistryKey<Biome> HIPHOP = key("hiphop");
    public static final RegistryKey<Biome> ELECTRONIC = key("electronic");
    public static final RegistryKey<Biome> POP = key("pop");
    public static final RegistryKey<Biome> AMBIENT = key("ambient");

    private static final Map<String, RegistryKey<Biome>> GENRE_TO_KEY = Map.of(
            "metal",      METAL,
            "jazz",       JAZZ,
            "classical",  CLASSICAL,
            "hiphop",     HIPHOP,
            "electronic", ELECTRONIC,
            "pop",        POP,
            "ambient",    AMBIENT
    );

    public static RegistryKey<Biome> getKeyForGenre(String genre) {
        return GENRE_TO_KEY.getOrDefault(genre.toLowerCase(), CLASSICAL);
    }

    private static RegistryKey<Biome> key(String name) {
        return RegistryKey.of(RegistryKeys.BIOME, new Identifier(MusicWorldMod.MOD_ID, name));
    }

    private ModBiomes() {}
}
