package com.musicworld.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.musicworld.data.GenreProfile;
import com.musicworld.data.WorldGenConfig;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.stream.Stream;

/**
 * A BiomeSource that returns a single biome matching the active GenreProfile.
 * The biome is resolved via registry lookup at codec decode time.
 */
public class GenreBiomeSource extends BiomeSource {

    public static final Codec<GenreBiomeSource> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Biome.REGISTRY_CODEC.fieldOf("biome")
                            .forGetter(s -> s.biome)
            ).apply(instance, GenreBiomeSource::new));

    private final RegistryEntry<Biome> biome;

    public GenreBiomeSource(RegistryEntry<Biome> biome) {
        this.biome = biome;
    }

    @Override
    protected Codec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return Stream.of(biome);
    }

    @Override
    public RegistryEntry<Biome> getBiome(int biomeX, int biomeY, int biomeZ,
                                          MultiNoiseUtil.MultiNoiseSampler noise) {
        return biome;
    }
}
