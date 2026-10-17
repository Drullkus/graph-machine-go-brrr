package us.drullk.graphmachine.datagen;

import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.biome.OverworldBiomes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TimelineTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.attribute.AmbientSounds;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.timeline.Timeline;
import us.drullk.graphmachine.worldgen.GraphDimensionTypes;

/**
 * Builds the {@code graphmachine:graph} dimension type: the overworld settings copied field by field from
 * vanilla's {@code DimensionTypes} bootstrap, under a new id.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphDimensionTypeBootstrap {
    private GraphDimensionTypeBootstrap() {
    }

    public static void bootstrap(BootstrapContext<DimensionType> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);
        HolderGetter<Timeline> timelines = context.lookup(Registries.TIMELINE);
        HolderGetter<WorldClock> clocks = context.lookup(Registries.WORLD_CLOCK);
        EnvironmentAttributeMap attributes = EnvironmentAttributeMap.builder()
                .set(EnvironmentAttributes.FOG_COLOR, ARGB.vector3fFromRGB24(0xC0D8FF))
                .set(EnvironmentAttributes.SKY_COLOR, ARGB.vector3fFromRGB24(OverworldBiomes.calculateSkyColor(0.8F)))
                .set(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, ARGB.vector3fFromRGB24(0x0A0A0A))
                .set(EnvironmentAttributes.CLOUD_COLOR, ARGB.vector4fFromARGB32(ARGB.white(0.8F)))
                .set(EnvironmentAttributes.CLOUD_HEIGHT, 192.33F)
                .set(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.OVERWORLD)
                .set(EnvironmentAttributes.BED_RULE, BedRule.CAN_SLEEP_WHEN_DARK)
                .set(EnvironmentAttributes.STRAW_BED_RULE, BedRule.DESTROY_ON_LEAVE)
                .set(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, false)
                .set(EnvironmentAttributes.NETHER_PORTAL_SPAWNS_PIGLINS, true)
                .set(EnvironmentAttributes.AMBIENT_SOUNDS, AmbientSounds.LEGACY_CAVE_SETTINGS)
                .build();
        context.register(GraphDimensionTypes.GRAPH, new DimensionType(
                false,
                true,
                false,
                false,
                1.0,
                -64,
                384,
                384,
                blocks.getOrThrow(BlockTags.INFINIBURN_OVERWORLD),
                0.0F,
                new DimensionType.MonsterSettings(UniformInt.of(0, 7), 0),
                DimensionType.Skybox.OVERWORLD,
                CardinalLighting.Type.DEFAULT,
                attributes,
                timelines.getOrThrow(TimelineTags.IN_OVERWORLD),
                Optional.of(clocks.getOrThrow(WorldClocks.OVERWORLD))
        ));
    }
}
