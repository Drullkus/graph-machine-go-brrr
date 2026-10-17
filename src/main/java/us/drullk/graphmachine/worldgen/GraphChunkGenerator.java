package us.drullk.graphmachine.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;

/**
 * Generates a flat floor and nothing else. The class graph above it is drawn by the client, not placed
 * in the world; see {@code us.drullk.graphmachine.client.render.GraphRenderer}.
 *
 * <p>Worlds created with earlier versions of this mod still hold the block and text display entities
 * that the generator placed back then. They are not removed; create a new world instead.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public class GraphChunkGenerator extends ChunkGenerator {
    public static final int DEFAULT_FLOOR_Y = 64;
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;

    public static final MapCodec<GraphChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome),
            BlockState.CODEC.optionalFieldOf("floor", Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState()).forGetter(g -> g.floor),
            Codec.INT.optionalFieldOf("floor_y", DEFAULT_FLOOR_Y).forGetter(g -> g.floorY)
    ).apply(i, i.stable(GraphChunkGenerator::new)));

    private final Holder<Biome> biome;
    private final BlockState floor;
    private final int floorY;

    public GraphChunkGenerator(Holder<Biome> biome, BlockState floor, int floorY) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
        this.floor = floor;
        this.floorY = floorY;
    }

    /** A generator with the default floor and height. */
    public GraphChunkGenerator(Holder<Biome> biome) {
        this(biome, Blocks.CONCRETE.black().defaultBlockState(), DEFAULT_FLOOR_Y);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor heightAccessor) {
        return this.floorY + 1;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(
            ChunkAccess chunk,
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            BiomeManager biomeManager,
            @Nullable WorldGenRegion carverBiomeRegion,
            Set<Holder<Biome>> possibleBiomes
    ) {
        if (this.floorY >= chunk.getMinY() && this.floorY <= chunk.getMaxY()) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
            Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.setBlockState(pos.set(x, this.floorY, z), this.floor);
                    oceanFloor.update(x, this.floorY, z, this.floor);
                    worldSurface.update(x, this.floorY, z, this.floor);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return type.isOpaque().test(this.floor) ? this.floorY + 1 : heightAccessor.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        int count = Math.max(0, this.floorY - heightAccessor.getMinY() + 1);
        BlockState[] column = new BlockState[count];
        for (int i = 0; i < count; i++) {
            column[i] = i == count - 1 ? this.floor : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(heightAccessor.getMinY(), column);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext samplerContext) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return this.floorY;
    }
}
