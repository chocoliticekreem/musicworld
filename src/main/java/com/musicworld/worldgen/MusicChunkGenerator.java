package com.musicworld.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.musicworld.data.GenreProfile;
import com.musicworld.data.WorldGenConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class MusicChunkGenerator extends ChunkGenerator {

    // -------------------------------------------------------------------------
    // CODEC
    // -------------------------------------------------------------------------

    public static final Codec<MusicChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
            ).apply(instance, MusicChunkGenerator::new)
    );

    public MusicChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    // -------------------------------------------------------------------------
    // World dimensions
    // -------------------------------------------------------------------------

    @Override
    public int getMinimumY() {
        return -64;
    }

    @Override
    public int getSeaLevel() {
        return WorldGenConfig.getActive().waterLevel;
    }

    @Override
    public int getWorldHeight() {
        return 384;
    }

    // -------------------------------------------------------------------------
    // Terrain height helpers
    // -------------------------------------------------------------------------

    private int computeHeight(int x, int z, GenreProfile p) {
        double s1 = OpenSimplex2S.noise2(42L, x * 0.005 * p.terrainRoughness, z * 0.005 * p.terrainRoughness)
                * p.terrainRoughness * 40.0;
        double s2 = OpenSimplex2S.noise2(99L, x * 0.02 * p.terrainRoughness, z * 0.02 * p.terrainRoughness)
                * p.terrainRoughness * 15.0;
        int h = (int) (p.baseHeight + s1 + s2);
        return Math.max(5, Math.min(230, h));
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        return computeHeight(x, z, WorldGenConfig.getActive());
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        GenreProfile p = WorldGenConfig.getActive();
        int minY = world.getBottomY();
        int height = world.getHeight();
        BlockState[] states = new BlockState[height];
        int finalH = computeHeight(x, z, p);

        for (int i = 0; i < height; i++) {
            int y = minY + i;
            states[i] = getBlockAt(x, y, z, finalH, p, Random.create(x * 31L + z));
        }
        return new VerticalBlockSample(minY, states);
    }

    // -------------------------------------------------------------------------
    // Main terrain population
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<Chunk> populateNoise(
            Executor executor, Blender blender, NoiseConfig noiseConfig,
            StructureAccessor structureAccessor, Chunk chunk) {
        return CompletableFuture.supplyAsync(() -> {
            generateTerrain(chunk);
            return chunk;
        }, executor);
    }

    private void generateTerrain(Chunk chunk) {
        GenreProfile p = WorldGenConfig.getActive();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        int minY = chunk.getBottomY();
        int maxY = chunk.getTopY();

        BlockPos.Mutable mpos = new BlockPos.Mutable();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                int finalH = computeHeight(worldX, worldZ, p);
                Random colRand = Random.create(worldX * 31L + worldZ);

                for (int y = minY; y < maxY; y++) {
                    mpos.set(worldX, y, worldZ);
                    BlockState state = getBlockAt(worldX, y, worldZ, finalH, p, colRand);
                    if (state != null && state != Blocks.AIR.getDefaultState()) {
                        chunk.setBlockState(mpos, state, false);
                    }
                }
            }
        }

        // Cave pass — 3D noise carving
        carveCaves(chunk, p, startX, startZ, minY, maxY);
    }

    private BlockState getBlockAt(int x, int y, int z, int finalH, GenreProfile p, Random colRand) {
        int waterLevel = p.waterLevel;

        if (y > finalH && y <= waterLevel) {
            return Blocks.WATER.getDefaultState();
        } else if (y == finalH) {
            return resolveBlock(p.surfaceBlock);
        } else if (y >= finalH - 3 && y < finalH) {
            return Blocks.DIRT.getDefaultState();
        } else if (y < finalH - 3 && y >= getMinimumY()) {
            // 20% chance to use secondary block — use a fresh draw per column per depth
            // colRand is seeded per column so it's deterministic
            boolean useSecondary = (Math.abs((x * 1000003L + y * 999983L + z) % 5) == 0);
            return useSecondary ? resolveBlock(p.secondaryBlock) : resolveBlock(p.primaryBlock);
        }
        return Blocks.AIR.getDefaultState();
    }

    // -------------------------------------------------------------------------
    // Cave carving
    // -------------------------------------------------------------------------

    private void carveCaves(Chunk chunk, GenreProfile p, int startX, int startZ, int minY, int maxY) {
        BlockPos.Mutable mpos = new BlockPos.Mutable();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                int finalH = computeHeight(worldX, worldZ, p);

                for (int y = minY; y < maxY; y++) {
                    if (y < 8) continue;                      // never carve bedrock zone
                    if (y >= finalH - 1) continue;            // never carve surface layer

                    double noise = OpenSimplex2S.noise3_ImproveXZ(
                            7L,
                            worldX * 0.04,
                            y * 0.04,
                            worldZ * 0.04
                    );

                    if (noise > (1.0 - p.caveFrequency * 0.6)) {
                        mpos.set(worldX, y, worldZ);
                        if (y < 16) {
                            chunk.setBlockState(mpos, Blocks.LAVA.getDefaultState(), false);
                        } else {
                            chunk.setBlockState(mpos, Blocks.AIR.getDefaultState(), false);
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Surface building (no-op — we handle surface in populateNoise)
    // -------------------------------------------------------------------------

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures,
                             NoiseConfig noiseConfig, Chunk chunk) {
        // Surface blocks are placed in populateNoise
    }

    // -------------------------------------------------------------------------
    // Features: trees + ores + structures
    // -------------------------------------------------------------------------

    @Override
    public void generateFeatures(StructureWorldAccess world, Chunk chunk,
                                 StructureAccessor structureAccessor) {
        GenreProfile p = WorldGenConfig.getActive();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        Random chunkRand = Random.create(chunkX * 341873128712L + chunkZ * 132897987541L);

        // Vanilla trees via PlacedFeature
        placeVanillaTrees(world, chunk, p, chunkRand);

        // Vanilla ores
        placeOres(world, chunk, p, chunkRand);

        // Custom genre structures (10% chance per chunk)
        placeStructure(world, p, startX, startZ, chunkX, chunkZ, chunkRand);
    }

    // -------------------------------------------------------------------------
    // Trees — directly placed, no biome/surface dependency
    // -------------------------------------------------------------------------

    private void placeVanillaTrees(StructureWorldAccess world, Chunk chunk,
                                   GenreProfile p, Random rand) {
        if (p.treeFrequency <= 0) return;

        int attempts = Math.max(1, (int) (p.treeFrequency * 12));
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        for (int i = 0; i < attempts; i++) {
            if (rand.nextFloat() > p.treeFrequency) continue;
            int wx = startX + rand.nextInt(16);
            int wz = startZ + rand.nextInt(16);
            int groundY = computeHeight(wx, wz, p);
            placeTree(world, wx, groundY + 1, wz, p, rand);
        }
    }

    /** Place a genre-appropriate tree at the given base position. */
    private void placeTree(StructureWorldAccess world, int x, int y, int z,
                           GenreProfile p, Random rand) {
        switch (p.structureType) {
            case "PILLARS"   -> placeSpruceTree(world, x, y, z, rand);   // metal: dark spruce
            case "BUILDINGS" -> placeOakTree(world, x, y, z, rand);      // jazz: oak
            case "COLUMNS"   -> {                                          // classical: mix oak + birch
                if (rand.nextBoolean()) placeOakTree(world, x, y, z, rand);
                else placeBirchTree(world, x, y, z, rand);
            }
            case "PLATFORMS" -> placeAcaciaTree(world, x, y, z, rand);   // hiphop: acacia
            case "GAZEBOS"   -> placeBirchTree(world, x, y, z, rand);    // pop: birch
            case "RUINS"     -> placeSpruceTree(world, x, y, z, rand);   // ambient: spruce
            default          -> placeOakTree(world, x, y, z, rand);
        }
    }

    /** Oak tree: trunk 4-6 high, round leaf blob */
    private void placeOakTree(StructureWorldAccess world, int x, int y, int z, Random rand) {
        int height = 4 + rand.nextInt(3);
        BlockState log = Blocks.OAK_LOG.getDefaultState();
        BlockState leaves = Blocks.OAK_LEAVES.getDefaultState();
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dy = 0; dy < height; dy++) {
            m.set(x, y + dy, z);
            world.setBlockState(m, log, 3);
        }
        // Round leaf blob around top
        int top = y + height;
        for (int dy = -1; dy <= 2; dy++) {
            int radius = (dy <= 0) ? 2 : (dy == 1 ? 2 : 1);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy < 0) continue; // log position
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && rand.nextBoolean()) continue;
                    m.set(x + dx, top + dy, z + dz);
                    if (world.getBlockState(m).isAir()) world.setBlockState(m, leaves, 3);
                }
            }
        }
    }

    /** Birch tree: trunk 5-7 high, slightly narrower leaf blob */
    private void placeBirchTree(StructureWorldAccess world, int x, int y, int z, Random rand) {
        int height = 5 + rand.nextInt(3);
        BlockState log = Blocks.BIRCH_LOG.getDefaultState();
        BlockState leaves = Blocks.BIRCH_LEAVES.getDefaultState();
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dy = 0; dy < height; dy++) {
            m.set(x, y + dy, z);
            world.setBlockState(m, log, 3);
        }
        int top = y + height;
        for (int dy = -1; dy <= 2; dy++) {
            int radius = (dy <= 0) ? 2 : (dy == 1 ? 1 : 1);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy < 0) continue;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue; // clip corners
                    m.set(x + dx, top + dy, z + dz);
                    if (world.getBlockState(m).isAir()) world.setBlockState(m, leaves, 3);
                }
            }
        }
    }

    /** Spruce tree: trunk 6-10 high, layered cone leaves */
    private void placeSpruceTree(StructureWorldAccess world, int x, int y, int z, Random rand) {
        int height = 6 + rand.nextInt(5);
        BlockState log = Blocks.SPRUCE_LOG.getDefaultState();
        BlockState leaves = Blocks.SPRUCE_LEAVES.getDefaultState();
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dy = 0; dy < height; dy++) {
            m.set(x, y + dy, z);
            world.setBlockState(m, log, 3);
        }
        // Cone: wide at bottom, tip at top
        int top = y + height;
        for (int layer = 0; layer < 5; layer++) {
            int radius = Math.max(0, 2 - layer / 2);
            int ly = top - layer;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && rand.nextBoolean()) continue;
                    m.set(x + dx, ly, z + dz);
                    if (world.getBlockState(m).isAir()) world.setBlockState(m, leaves, 3);
                }
            }
        }
        // Top cap
        m.set(x, top + 1, z);
        world.setBlockState(m, leaves, 3);
    }

    /** Acacia tree: forked trunk, flat leaf canopy */
    private void placeAcaciaTree(StructureWorldAccess world, int x, int y, int z, Random rand) {
        int height = 5 + rand.nextInt(3);
        BlockState log = Blocks.ACACIA_LOG.getDefaultState();
        BlockState leaves = Blocks.ACACIA_LEAVES.getDefaultState();
        BlockPos.Mutable m = new BlockPos.Mutable();

        // Main trunk
        for (int dy = 0; dy < height - 1; dy++) {
            m.set(x, y + dy, z);
            world.setBlockState(m, log, 3);
        }
        // Fork: two branches at angle
        int forkY = y + height - 1;
        int[][] forks = {{1, 0}, {-1, 0}};
        if (rand.nextBoolean()) forks = new int[][]{{0, 1}, {0, -1}};
        for (int[] fork : forks) {
            m.set(x + fork[0], forkY, z + fork[1]);
            world.setBlockState(m, log, 3);
            m.set(x + fork[0], forkY + 1, z + fork[1]);
            world.setBlockState(m, log, 3);
            // Flat leaf pad at fork top
            int lx = x + fork[0], lz = z + fork[1], topY = forkY + 1;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    m.set(lx + dx, topY + 1, lz + dz);
                    if (world.getBlockState(m).isAir()) world.setBlockState(m, leaves, 3);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Vanilla ores via PlacedFeature registry
    // -------------------------------------------------------------------------

    private void placeOres(StructureWorldAccess world, Chunk chunk,
                           GenreProfile p, Random rand) {
        Registry<PlacedFeature> registry = world.getRegistryManager().get(RegistryKeys.PLACED_FEATURE);
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        BlockPos origin = new BlockPos(startX + 8, 0, startZ + 8);

        for (RegistryKey<PlacedFeature> oreKey : getOreKeysForGenre(p)) {
            registry.getOrEmpty(oreKey).ifPresent(f ->
                    f.generate(world, this, rand, origin));
        }
    }

    private List<RegistryKey<PlacedFeature>> getOreKeysForGenre(GenreProfile p) {
        return switch (p.structureType) {
            case "PILLARS" ->   // metal: iron, coal, gold heavy
                    List.of(OrePlacedFeatures.ORE_IRON_UPPER, OrePlacedFeatures.ORE_IRON_MIDDLE,
                            OrePlacedFeatures.ORE_COAL_UPPER, OrePlacedFeatures.ORE_GOLD);
            case "BUILDINGS" -> // jazz: standard mix
                    List.of(OrePlacedFeatures.ORE_IRON_UPPER, OrePlacedFeatures.ORE_COAL_UPPER,
                            OrePlacedFeatures.ORE_COPPER);
            case "COLUMNS" ->   // classical: quartz feel — gold, diamond
                    List.of(OrePlacedFeatures.ORE_GOLD, OrePlacedFeatures.ORE_DIAMOND,
                            OrePlacedFeatures.ORE_EMERALD);
            case "PLATFORMS" -> // hiphop: coal, iron, redstone
                    List.of(OrePlacedFeatures.ORE_COAL_UPPER, OrePlacedFeatures.ORE_IRON_MIDDLE,
                            OrePlacedFeatures.ORE_REDSTONE);
            case "GAZEBOS" ->   // pop: light ores
                    List.of(OrePlacedFeatures.ORE_COAL_UPPER, OrePlacedFeatures.ORE_COPPER,
                            OrePlacedFeatures.ORE_IRON_UPPER);
            case "RUINS" ->     // ambient: clay, lapis, diamond rare
                    List.of(OrePlacedFeatures.ORE_LAPIS, OrePlacedFeatures.ORE_DIAMOND,
                            OrePlacedFeatures.ORE_IRON_MIDDLE);
            default ->          // electronic: redstone, lapis, gold
                    List.of(OrePlacedFeatures.ORE_REDSTONE, OrePlacedFeatures.ORE_LAPIS,
                            OrePlacedFeatures.ORE_GOLD);
        };
    }

    // -------------------------------------------------------------------------
    // Structures — 10% of chunks
    // -------------------------------------------------------------------------

    private void placeStructure(StructureWorldAccess world, GenreProfile p,
                                int startX, int startZ, int chunkX, int chunkZ, Random rand) {
        // Seeded 10% trigger
        Random triggerRand = Random.create(chunkX * 1234567891L ^ chunkZ * 987654321L);
        if (triggerRand.nextInt(10) != 0) return;

        // Place at chunk centre
        int cx = startX + 8;
        int cz = startZ + 8;
        int groundY = computeHeight(cx, cz, p);

        switch (p.structureType) {
            case "PILLARS"    -> buildPillar(world, cx, groundY, cz);
            case "BUILDINGS"  -> buildBuilding(world, cx, groundY, cz);
            case "COLUMNS"    -> buildColumns(world, cx, groundY, cz);
            case "PLATFORMS"  -> buildPlatform(world, cx, groundY, cz);
            case "GAZEBOS"    -> buildGazebo(world, cx, groundY, cz, rand);
            case "RUINS"      -> buildRuin(world, cx, groundY, cz, rand);
            // NONE — no structure
        }
    }

    // PILLARS (Metal): 5x5 BLACKSTONE base, OBSIDIAN pillar 15-25 high, LAVA on top
    private void buildPillar(StructureWorldAccess world, int cx, int groundY, int cz) {
        Random r = Random.create(cx * 31L + cz);
        int height = 15 + r.nextInt(11);
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                m.set(cx + dx, groundY, cz + dz);
                world.setBlockState(m, Blocks.BLACKSTONE.getDefaultState(), 3);
            }

        for (int y = 1; y <= height; y++) {
            m.set(cx, groundY + y, cz);
            world.setBlockState(m, Blocks.OBSIDIAN.getDefaultState(), 3);
        }
        m.set(cx, groundY + height + 1, cz);
        world.setBlockState(m, Blocks.LAVA.getDefaultState(), 3);
    }

    // BUILDINGS (Jazz): 7x7 stone bricks floor, 4-high walls, oak planks roof, south entrance
    private void buildBuilding(StructureWorldAccess world, int cx, int groundY, int cz) {
        BlockPos.Mutable m = new BlockPos.Mutable();

        // Floor
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                m.set(cx + dx, groundY, cz + dz);
                world.setBlockState(m, Blocks.STONE_BRICKS.getDefaultState(), 3);
            }

        // Walls (4 high), south wall has 2-block gap for entrance (dz == 3, dx in -1..1)
        for (int y = 1; y <= 4; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    boolean isWall = (dx == -3 || dx == 3 || dz == -3 || dz == 3);
                    if (!isWall) continue;
                    boolean isSouthEntrance = (dz == 3 && dx >= -1 && dx <= 1 && y <= 2);
                    if (isSouthEntrance) continue;
                    m.set(cx + dx, groundY + y, cz + dz);
                    world.setBlockState(m, Blocks.STONE_BRICKS.getDefaultState(), 3);
                }
            }
        }

        // Roof
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                m.set(cx + dx, groundY + 5, cz + dz);
                world.setBlockState(m, Blocks.OAK_PLANKS.getDefaultState(), 3);
            }
    }

    // COLUMNS (Classical): QUARTZ_PILLAR columns at corners, 12 high, QUARTZ_SLAB beams
    private void buildColumns(StructureWorldAccess world, int cx, int groundY, int cz) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        int[][] corners = {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}};

        for (int[] c : corners) {
            for (int y = 0; y <= 12; y++) {
                m.set(cx + c[0], groundY + y, cz + c[1]);
                world.setBlockState(m, Blocks.QUARTZ_PILLAR.getDefaultState(), 3);
            }
        }

        // Beams connecting tops
        int topY = groundY + 12;
        for (int dx = -3; dx <= 3; dx++) {
            m.set(cx + dx, topY, cz - 3);
            world.setBlockState(m, Blocks.QUARTZ_SLAB.getDefaultState(), 3);
            m.set(cx + dx, topY, cz + 3);
            world.setBlockState(m, Blocks.QUARTZ_SLAB.getDefaultState(), 3);
        }
        for (int dz = -3; dz <= 3; dz++) {
            m.set(cx - 3, topY, cz + dz);
            world.setBlockState(m, Blocks.QUARTZ_SLAB.getDefaultState(), 3);
            m.set(cx + 3, topY, cz + dz);
            world.setBlockState(m, Blocks.QUARTZ_SLAB.getDefaultState(), 3);
        }
    }

    // PLATFORMS (Hiphop): 10x10 GRAY_CONCRETE elevated 8 blocks on IRON_BARS, GLOWSTONE border
    private void buildPlatform(StructureWorldAccess world, int cx, int groundY, int cz) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        int platformY = groundY + 8;

        // Stilts
        for (int dx = -4; dx <= 4; dx += 4)
            for (int dz = -4; dz <= 4; dz += 4)
                for (int y = 1; y < 8; y++) {
                    m.set(cx + dx, groundY + y, cz + dz);
                    world.setBlockState(m, Blocks.IRON_BARS.getDefaultState(), 3);
                }

        // Platform floor
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                boolean isBorder = (dx == -4 || dx == 4 || dz == -4 || dz == 4);
                m.set(cx + dx, platformY, cz + dz);
                world.setBlockState(m,
                        isBorder ? Blocks.GLOWSTONE.getDefaultState()
                                 : Blocks.GRAY_CONCRETE.getDefaultState(), 3);
            }
    }

    // GAZEBOS (Pop): 5x5 OAK_PLANKS floor, OAK_FENCE corners 3 high, OAK_SLAB roof, flowers
    private void buildGazebo(StructureWorldAccess world, int cx, int groundY, int cz, Random rand) {
        BlockPos.Mutable m = new BlockPos.Mutable();

        // Floor
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                m.set(cx + dx, groundY, cz + dz);
                world.setBlockState(m, Blocks.OAK_PLANKS.getDefaultState(), 3);
            }

        // Fence corners 3 high
        int[][] corners = {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}};
        for (int[] c : corners)
            for (int y = 1; y <= 3; y++) {
                m.set(cx + c[0], groundY + y, cz + c[1]);
                world.setBlockState(m, Blocks.OAK_FENCE.getDefaultState(), 3);
            }

        // Roof
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                m.set(cx + dx, groundY + 4, cz + dz);
                world.setBlockState(m, Blocks.OAK_SLAB.getDefaultState(), 3);
            }

        // Flowers within 3 block radius
        BlockState[] flowers = {
                Blocks.DANDELION.getDefaultState(),
                Blocks.POPPY.getDefaultState(),
                Blocks.BLUE_ORCHID.getDefaultState(),
                Blocks.ALLIUM.getDefaultState()
        };
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (rand.nextInt(3) == 0) {
                    int fx = cx + dx;
                    int fz = cz + dz;
                    int fy = computeHeight(fx, fz, WorldGenConfig.getActive()) + 1;
                    m.set(fx, fy, fz);
                    if (world.getBlockState(m).isAir()) {
                        world.setBlockState(m, flowers[rand.nextInt(flowers.length)], 3);
                    }
                }
            }
    }

    // RUINS (Ambient): partial 6x6 MOSSY_COBBLESTONE, 40% blocks removed, interior WATER
    private void buildRuin(StructureWorldAccess world, int cx, int groundY, int cz, Random rand) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        Random ruinRand = Random.create(cx * 77777L + cz);

        // Walls 3 high
        for (int y = 0; y <= 3; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    boolean isWall = (dx == -3 || dx == 3 || dz == -3 || dz == 3);
                    if (!isWall) continue;
                    if (ruinRand.nextFloat() < 0.4f) continue; // remove 40%
                    m.set(cx + dx, groundY + y, cz + dz);
                    world.setBlockState(m, Blocks.MOSSY_COBBLESTONE.getDefaultState(), 3);
                }
            }
        }

        // Interior water
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                m.set(cx + dx, groundY, cz + dz);
                world.setBlockState(m, Blocks.WATER.getDefaultState(), 3);
            }
    }

    // -------------------------------------------------------------------------
    // Carve / populateEntities (no-ops — handled elsewhere)
    // -------------------------------------------------------------------------

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig,
                      BiomeAccess biomeAccess, StructureAccessor structureAccessor,
                      Chunk chunk, GenerationStep.Carver carverStep) {
        // Cave carving is done in populateNoise
    }

    @Override
    public void populateEntities(ChunkRegion region) {
        // No entity spawning
    }

    // -------------------------------------------------------------------------
    // Debug HUD
    // -------------------------------------------------------------------------

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        GenreProfile p = WorldGenConfig.getActive();
        text.add("MusicWorld genre | base=" + p.baseHeight
                + " rough=" + p.terrainRoughness
                + " struct=" + p.structureType);
    }

    // -------------------------------------------------------------------------
    // Block name -> BlockState resolver
    // -------------------------------------------------------------------------

    private static BlockState resolveBlock(String name) {
        return switch (name) {
            case "BLACKSTONE"       -> Blocks.BLACKSTONE.getDefaultState();
            case "OBSIDIAN"         -> Blocks.OBSIDIAN.getDefaultState();
            case "GRAVEL"           -> Blocks.GRAVEL.getDefaultState();
            case "SMOOTH_STONE"     -> Blocks.SMOOTH_STONE.getDefaultState();
            case "STONE"            -> Blocks.STONE.getDefaultState();
            case "GRASS_BLOCK"      -> Blocks.GRASS_BLOCK.getDefaultState();
            case "QUARTZ_BLOCK"     -> Blocks.QUARTZ_BLOCK.getDefaultState();
            case "GRAY_CONCRETE"    -> Blocks.GRAY_CONCRETE.getDefaultState();
            case "COARSE_DIRT"      -> Blocks.COARSE_DIRT.getDefaultState();
            case "PURPUR_BLOCK"     -> Blocks.PURPUR_BLOCK.getDefaultState();
            case "END_STONE"        -> Blocks.END_STONE.getDefaultState();
            case "SOUL_SAND"        -> Blocks.SOUL_SAND.getDefaultState();
            case "DIORITE"          -> Blocks.DIORITE.getDefaultState();
            case "CLAY"             -> Blocks.CLAY.getDefaultState();
            case "MOSS_BLOCK"       -> Blocks.MOSS_BLOCK.getDefaultState();
            default                 -> Blocks.STONE.getDefaultState();
        };
    }

    // =========================================================================
    // Embedded OpenSimplex2S noise (public domain — KdotJPG)
    // Trimmed to the two methods we use: noise2 and noise3_ImproveXZ
    // =========================================================================

    static final class OpenSimplex2S {

        private static final int PSIZE = 2048;
        private static final int PMASK = 2047;

        private static final double N2 = 0.05481866495625118;
        private static final double N3 = 0.2781926117527186;

        private static final Grad2[] GRADIENTS_2D;
        private static final Grad3[] GRADIENTS_3D;

        static {
            // 2D gradients
            Grad2[] grad2 = {
                    new Grad2( 0.130526192220052,  0.99144486137381),
                    new Grad2( 0.38268343236509,   0.923879532511287),
                    new Grad2( 0.608761429008721,  0.793353340291235),
                    new Grad2( 0.793353340291235,  0.608761429008721),
                    new Grad2( 0.923879532511287,  0.38268343236509),
                    new Grad2( 0.99144486137381,   0.130526192220051),
                    new Grad2( 0.99144486137381,  -0.130526192220051),
                    new Grad2( 0.923879532511287, -0.38268343236509),
                    new Grad2( 0.793353340291235, -0.608761429008721),
                    new Grad2( 0.608761429008721, -0.793353340291235),
                    new Grad2( 0.38268343236509,  -0.923879532511287),
                    new Grad2( 0.130526192220052, -0.99144486137381),
                    new Grad2(-0.130526192220052, -0.99144486137381),
                    new Grad2(-0.38268343236509,  -0.923879532511287),
                    new Grad2(-0.608761429008721, -0.793353340291235),
                    new Grad2(-0.793353340291235, -0.608761429008721),
                    new Grad2(-0.923879532511287, -0.38268343236509),
                    new Grad2(-0.99144486137381,  -0.130526192220051),
                    new Grad2(-0.99144486137381,   0.130526192220051),
                    new Grad2(-0.923879532511287,  0.38268343236509),
                    new Grad2(-0.793353340291235,  0.608761429008721),
                    new Grad2(-0.608761429008721,  0.793353340291235),
                    new Grad2(-0.38268343236509,   0.923879532511287),
                    new Grad2(-0.130526192220052,  0.99144486137381)
            };
            GRADIENTS_2D = new Grad2[PSIZE];
            for (int i = 0; i < PSIZE; i++) GRADIENTS_2D[i] = grad2[i % grad2.length];

            // 3D gradients
            Grad3[] grad3 = {
                    new Grad3(-2.22474487139,  -2.22474487139, -1.0),
                    new Grad3(-2.22474487139,  -2.22474487139,  1.0),
                    new Grad3(-3.0862664687972017, -1.1721513422464978, 0.0),
                    new Grad3(-1.1721513422464978, -3.0862664687972017, 0.0),
                    new Grad3(-2.22474487139,  -1.0,           -2.22474487139),
                    new Grad3(-2.22474487139,   1.0,           -2.22474487139),
                    new Grad3(-1.1721513422464978, 0.0,        -3.0862664687972017),
                    new Grad3(-3.0862664687972017, 0.0,        -1.1721513422464978),
                    new Grad3(-2.22474487139,  -1.0,            2.22474487139),
                    new Grad3(-2.22474487139,   1.0,            2.22474487139),
                    new Grad3(-3.0862664687972017, 0.0,         1.1721513422464978),
                    new Grad3(-1.1721513422464978, 0.0,         3.0862664687972017),
                    new Grad3(-2.22474487139,   2.22474487139, -1.0),
                    new Grad3(-2.22474487139,   2.22474487139,  1.0),
                    new Grad3(-1.1721513422464978, 3.0862664687972017, 0.0),
                    new Grad3(-3.0862664687972017, 1.1721513422464978, 0.0),
                    new Grad3(-1.0,            -2.22474487139, -2.22474487139),
                    new Grad3( 1.0,            -2.22474487139, -2.22474487139),
                    new Grad3( 0.0,            -3.0862664687972017, -1.1721513422464978),
                    new Grad3( 0.0,            -1.1721513422464978, -3.0862664687972017),
                    new Grad3(-1.0,            -2.22474487139,  2.22474487139),
                    new Grad3( 1.0,            -2.22474487139,  2.22474487139),
                    new Grad3( 0.0,            -1.1721513422464978,  3.0862664687972017),
                    new Grad3( 0.0,            -3.0862664687972017,  1.1721513422464978),
                    new Grad3(-1.0,             2.22474487139, -2.22474487139),
                    new Grad3( 1.0,             2.22474487139, -2.22474487139),
                    new Grad3( 0.0,             1.1721513422464978, -3.0862664687972017),
                    new Grad3( 0.0,             3.0862664687972017, -1.1721513422464978),
                    new Grad3(-1.0,             2.22474487139,  2.22474487139),
                    new Grad3( 1.0,             2.22474487139,  2.22474487139),
                    new Grad3( 0.0,             3.0862664687972017,  1.1721513422464978),
                    new Grad3( 0.0,             1.1721513422464978,  3.0862664687972017),
                    new Grad3( 2.22474487139,  -2.22474487139, -1.0),
                    new Grad3( 2.22474487139,  -2.22474487139,  1.0),
                    new Grad3( 1.1721513422464978, -3.0862664687972017, 0.0),
                    new Grad3( 3.0862664687972017, -1.1721513422464978, 0.0),
                    new Grad3( 2.22474487139,  -1.0,           -2.22474487139),
                    new Grad3( 2.22474487139,   1.0,           -2.22474487139),
                    new Grad3( 3.0862664687972017, 0.0,        -1.1721513422464978),
                    new Grad3( 1.1721513422464978, 0.0,        -3.0862664687972017),
                    new Grad3( 2.22474487139,  -1.0,            2.22474487139),
                    new Grad3( 2.22474487139,   1.0,            2.22474487139),
                    new Grad3( 1.1721513422464978, 0.0,         3.0862664687972017),
                    new Grad3( 3.0862664687972017, 0.0,         1.1721513422464978),
                    new Grad3( 2.22474487139,   2.22474487139, -1.0),
                    new Grad3( 2.22474487139,   2.22474487139,  1.0),
                    new Grad3( 3.0862664687972017, 1.1721513422464978, 0.0),
                    new Grad3( 1.1721513422464978, 3.0862664687972017, 0.0)
            };
            GRADIENTS_3D = new Grad3[PSIZE];
            for (int i = 0; i < PSIZE; i++) GRADIENTS_3D[i] = grad3[i % grad3.length];
        }

        // Permutation table seeded per call
        private static int[] buildPerm(long seed) {
            int[] perm = new int[PSIZE];
            int[] source = new int[PSIZE];
            for (int i = 0; i < PSIZE; i++) source[i] = i;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            for (int i = PSIZE - 1; i >= 0; i--) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int r = (int) ((seed + 31) % (i + 1));
                if (r < 0) r += (i + 1);
                perm[i] = source[r];
                source[r] = source[i];
            }
            return perm;
        }

        // 2D OpenSimplex2S
        public static double noise2(long seed, double x, double y) {
            int[] perm = buildPerm(seed);
            double s = 0.366025403784439 * (x + y);
            double xs = x + s, ys = y + s;
            return noise2_Base(perm, xs, ys);
        }

        private static double noise2_Base(int[] perm, double xs, double ys) {
            double value = 0;
            int xsb = fastFloor(xs), ysb = fastFloor(ys);
            double xsi = xs - xsb, ysi = ys - ysb;
            double a = xsi + ysi;
            int xsvp = xsb * 1723 + ysb * 3609;  // simple hash seed
            // unskew
            double t = (a - 1) * 0.5;
            double xi = xsi + t, yi = ysi + t;

            double attn = 2.0 / 3.0 - xi * xi - yi * yi;
            if (attn > 0) {
                int pxm = (xsvp + perm[ysb & PMASK]) & PMASK;
                Grad2 g = GRADIENTS_2D[perm[pxm]];
                value += attn * attn * attn * attn * (g.dx * xi + g.dy * yi);
            }

            // second point
            double t2 = t + (2.0 * (1.0 - 0.366025403784439) - 1.0);
            double xi2 = xi - (1.0 - 2.0 * 0.366025403784439);
            double yi2 = yi - (1.0 - 2.0 * 0.366025403784439);
            double attn2 = 2.0 / 3.0 - xi2 * xi2 - yi2 * yi2;
            if (attn2 > 0) {
                int pxm2 = (xsvp + 1723 + perm[(ysb + 1) & PMASK]) & PMASK;
                Grad2 g2 = GRADIENTS_2D[perm[pxm2]];
                value += attn2 * attn2 * attn2 * attn2 * (g2.dx * xi2 + g2.dy * yi2);
            }

            return value / N2;
        }

        // 3D OpenSimplex2S — XZ-improved orientation
        public static double noise3_ImproveXZ(long seed, double x, double y, double z) {
            int[] perm = buildPerm(seed);
            double xz = x + z;
            double s2 = xz * -0.211324865405187;
            double yy = y * 0.577350269189626;
            double xr = x + s2 + yy;
            double zr = z + s2 + yy;
            double yr = xz * -0.577350269189626 + yy;
            return noise3_Base(perm, xr, yr, zr);
        }

        private static double noise3_Base(int[] perm, double xr, double yr, double zr) {
            int xrb = fastFloor(xr), yrb = fastFloor(yr), zrb = fastFloor(zr);
            double xi = xr - xrb, yi = yr - yrb, zi = zr - zrb;
            double value = 0;

            // Evaluate 4 vertices of the simplex
            double t = (xi + yi + zi) * (1.0 / 6.0);
            double[] dxs = {xi - t, xi - t + (1.0/6.0), xi - t + (1.0/6.0), xi - t + (1.0/3.0)};
            double[] dys = {yi - t, yi - t + (1.0/6.0), yi - t - (1.0/3.0) + (1.0/6.0), yi - t + (1.0/3.0)};
            double[] dzs = {zi - t, zi - t - (1.0/3.0) + (1.0/6.0), zi - t + (1.0/6.0), zi - t + (1.0/3.0)};

            for (int i = 0; i < 4; i++) {
                double attn = 0.6 - dxs[i] * dxs[i] - dys[i] * dys[i] - dzs[i] * dzs[i];
                if (attn > 0) {
                    int idx = perm[(perm[(perm[xrb & PMASK] ^ (yrb & PMASK)) & PMASK]
                            ^ (zrb & PMASK)) & PMASK];
                    Grad3 g = GRADIENTS_3D[idx];
                    value += attn * attn * attn * attn * (g.dx * dxs[i] + g.dy * dys[i] + g.dz * dzs[i]);
                }
            }
            return value / N3;
        }

        private static int fastFloor(double x) {
            int xi = (int) x;
            return x < xi ? xi - 1 : xi;
        }

        private record Grad2(double dx, double dy) {}
        private record Grad3(double dx, double dy, double dz) {}
    }
}
