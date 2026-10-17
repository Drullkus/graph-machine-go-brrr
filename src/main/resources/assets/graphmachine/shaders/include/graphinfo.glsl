#ifndef GRAPHMACHINE_GRAPHINFO_GLSL
#define GRAPHMACHINE_GRAPHINFO_GLSL

// Per-frame data of the graph renderer. Written by GraphRenderer with Std140Builder in the same order.
layout(std140) uniform GraphInfo {
    // xyz: camera position in world space; w: world units per label pixel.
    vec4 CameraPosScale;
    // x: how much a hovered node mixes toward white; y: width multiplier of a hovered node's edges;
    // z, w: distance in blocks where labels start and finish fading out.
    vec4 GraphParams;
    // x: pixel width scale of edges (the window pixel density).
    vec4 GraphParams2;
    // x: compacted index of the hovered node, or -1; y: 1 when the label atlas keeps coverage in its red channel.
    ivec4 GraphInts;
};

#endif
