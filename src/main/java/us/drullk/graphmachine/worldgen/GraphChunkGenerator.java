package us.drullk.graphmachine.worldgen;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.neoforged.neoforge.common.util.ConcatenatedListView;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import us.drullk.graphmachine.graph.GraphData;
import us.drullk.graphmachine.graph.GraphDataLoader;
import us.drullk.graphmachine.graph.GraphNode;

/**
 * Generates a flat floor and places the class graph above it as display entities.
 *
 * <p>Each graph node becomes a block display, colored by its cluster and scaled by its degree, and a
 * text display with the simple class name that always faces the player. Nodes are placed during the
 * biome decoration step, so they are saved with the chunk like any other entity. The node positions
 * come from {@link GraphDataLoader}; the generator only adds {@code graph_y} to their height.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_07-43_2aec7285-4ea5-4d89-ba3e-f516a9a7bde0.md}.
 */
public class GraphChunkGenerator extends ChunkGenerator {
    public static final int DEFAULT_FLOOR_Y = 64;
    public static final int DEFAULT_GRAPH_Y = 128;
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;

    public static final MapCodec<GraphChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome),
            BlockState.CODEC.optionalFieldOf("floor", Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState()).forGetter(g -> g.floor),
            Codec.INT.optionalFieldOf("floor_y", DEFAULT_FLOOR_Y).forGetter(g -> g.floorY),
            Codec.INT.optionalFieldOf("graph_y", DEFAULT_GRAPH_Y).forGetter(g -> g.graphY)
    ).apply(i, i.stable(GraphChunkGenerator::new)));

    /** A variety of blocks, in dye order */
    private static final List<Block> CLUSTER_BLOCKS = ConcatenatedListView.of(
            Blocks.WOOL.asList(),
            Blocks.CONCRETE.asList(),
            Blocks.CONCRETE_POWDER.asList(),
            Blocks.DYED_TERRACOTTA.asList(),
            Blocks.GLAZED_TERRACOTTA.asList()
    );

    private final Holder<Biome> biome;
    private final BlockState floor;
    private final int floorY;
    private final int graphY;

    public GraphChunkGenerator(Holder<Biome> biome, BlockState floor, int floorY, int graphY) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
        this.floor = floor;
        this.floorY = floorY;
        this.graphY = graphY;
    }

    /** A generator with the default floor and heights. */
    public GraphChunkGenerator(Holder<Biome> biome) {
        this(biome, Blocks.CONCRETE.black().defaultBlockState(), DEFAULT_FLOOR_Y, DEFAULT_GRAPH_Y);
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
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        GraphData data = GraphDataLoader.current();
        if (data == null) {
            return;
        }
        List<GraphNode> nodes = data.nodesInChunk(chunk.getPos());
        if (nodes.isEmpty()) {
            return;
        }
        ServerLevel serverLevel = level.getLevel();
        for (GraphNode node : nodes) {
            this.placeNode(level, serverLevel, node);
        }
    }

    private void placeNode(WorldGenLevel level, ServerLevel serverLevel, GraphNode node) {
        double x = node.x();
        double y = node.y() + this.graphY;
        double z = node.z();
        float scale = nodeScale(node.degree());

        Display.BlockDisplay block = EntityTypes.BLOCK_DISPLAY.create(serverLevel, EntitySpawnReason.CHUNK_GENERATION);
        if (block != null) {
            block.setPos(x, y, z);
            block.setBlockState(clusterBlock(node.cluster()).defaultBlockState());
            // A block display draws from its position to position + scale, so pull it back by half to center it.
            float half = scale / 2.0F;
            block.setTransformation(new Transformation(new Vector3f(-half, -half, -half), null, new Vector3f(scale, scale, scale), null));
            level.addFreshEntity(block);
        }

        Display.TextDisplay text = EntityTypes.TEXT_DISPLAY.create(serverLevel, EntitySpawnReason.CHUNK_GENERATION);
        if (text != null) {
            text.setPos(x, y + scale / 2.0F + 0.25, z);
            text.setText(Component.literal(node.simpleName()).withStyle(node.side() == GraphNode.Side.CLIENT ? ChatFormatting.AQUA : ChatFormatting.WHITE));
            text.setBillboardConstraints(Display.BillboardConstraints.CENTER);
            level.addFreshEntity(text);
        }
    }

    /** Block edge length for a node: 0.3 for a leaf, about 1.3 for the largest hubs. */
    static float nodeScale(int degree) {
        return (float) Mth.clamp(0.3 + 0.1 * Math.log(degree + 1.0) / Math.log(2.0), 0.3, 1.6);
    }

    /** The block for a cluster id; bedrock for a class with no edges. */
    static Block clusterBlock(int cluster) {
        return cluster < 0 ? Blocks.BEDROCK : CLUSTER_BLOCKS.get(Math.floorMod(cluster, CLUSTER_BLOCKS.size()));
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
        GraphData data = GraphDataLoader.current();
        result.add("Graph nodes: " + (data == null ? "none loaded" : data.nodes().size()));
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
