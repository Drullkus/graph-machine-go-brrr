#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <graphmachine:graphinfo.glsl>

// One instance per edge, drawn as a screen-space quad of EdgeWidth pixels (6 vertices).
layout(location = 0) in vec3 EdgeA;
layout(location = 1) in vec3 EdgeB;
layout(location = 2) in vec4 ColorA;
layout(location = 3) in vec4 ColorB;
layout(location = 4) in ivec2 EdgeNodes;
layout(location = 5) in float EdgeWidth;

layout(location = 0) out vec4 vertexColor;

const float NEAR_W = 0.05;

void main() {
    int vertex = gl_VertexIndex;
    // Corners: 0 = A left, 1 = A right, 2 = B left, 3 = B right. Triangles (0, 1, 2) and (2, 1, 3).
    int corner = (vertex == 0) ? 0 : (vertex == 1 || vertex == 4) ? 1 : (vertex == 5) ? 3 : 2;
    float end = (corner >= 2) ? 1.0 : 0.0;
    float side = ((corner & 1) == 1) ? 1.0 : -1.0;

    mat4 viewProj = ProjMat * ModelViewMat;
    vec4 clipA = viewProj * vec4(EdgeA - CameraPosScale.xyz, 1.0);
    vec4 clipB = viewProj * vec4(EdgeB - CameraPosScale.xyz, 1.0);

    // Pull an endpoint that sits behind the camera onto the near plane so the segment stays straight.
    if (clipA.w < NEAR_W && clipB.w >= NEAR_W) {
        clipA = mix(clipA, clipB, (NEAR_W - clipA.w) / (clipB.w - clipA.w));
    } else if (clipB.w < NEAR_W && clipA.w >= NEAR_W) {
        clipB = mix(clipB, clipA, (NEAR_W - clipB.w) / (clipA.w - clipB.w));
    }

    vec2 ndcA = clipA.xy / clipA.w;
    vec2 ndcB = clipB.xy / clipB.w;
    vec2 direction = (ndcB - ndcA) * ScreenSize;
    float lengthPx = length(direction);
    vec2 normal = lengthPx > 0.0001 ? vec2(-direction.y, direction.x) / lengthPx : vec2(0.0, 1.0);

    bool hovered = GraphInts.x >= 0 && (EdgeNodes.x == GraphInts.x || EdgeNodes.y == GraphInts.x);
    float width = EdgeWidth * GraphParams2.x * (hovered ? GraphParams.y : 1.0);

    vec4 clip = mix(clipA, clipB, end);
    // Half the width to each side: width / 2 pixels is width / ScreenSize in NDC, times w for clip space.
    clip.xy += normal * (width / ScreenSize) * clip.w * side;
    gl_Position = clip;

    vec4 color = mix(ColorA, ColorB, end);
    vertexColor = vec4(color.rgb, hovered ? 1.0 : color.a);
}
