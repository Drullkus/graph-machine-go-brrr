#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <graphmachine:graphinfo.glsl>

// One instance per visible node; the cube itself comes from gl_VertexIndex (36 vertices).
layout(location = 0) in vec3 NodePos;
layout(location = 1) in float NodeScale;
layout(location = 2) in vec4 NodeColor;

layout(location = 0) out vec4 vertexColor;

const vec3 CORNERS[8] = vec3[](
    vec3(0.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0), vec3(1.0, 1.0, 0.0), vec3(0.0, 1.0, 0.0),
    vec3(0.0, 0.0, 1.0), vec3(1.0, 0.0, 1.0), vec3(1.0, 1.0, 1.0), vec3(0.0, 1.0, 1.0)
);

// Six faces, two triangles each: -Z, +Z, -Y, +Y, -X, +X.
const int FACE_INDICES[36] = int[](
    0, 1, 2, 0, 2, 3,
    5, 4, 7, 5, 7, 6,
    4, 5, 1, 4, 1, 0,
    3, 2, 6, 3, 6, 7,
    4, 0, 3, 4, 3, 7,
    1, 5, 6, 1, 6, 2
);

const vec3 NORMALS[6] = vec3[](
    vec3(0.0, 0.0, -1.0), vec3(0.0, 0.0, 1.0), vec3(0.0, -1.0, 0.0),
    vec3(0.0, 1.0, 0.0), vec3(-1.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0)
);

const vec3 LIGHT_DIR = normalize(vec3(0.4, 0.8, 0.3));

void main() {
    int vertex = gl_VertexIndex;
    vec3 corner = CORNERS[FACE_INDICES[vertex]] - 0.5;
    vec3 normal = NORMALS[vertex / 6];

    vec3 position = NodePos - CameraPosScale.xyz + corner * NodeScale;
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);

    float light = 0.55 + 0.15 * normal.y + 0.3 * max(dot(normal, LIGHT_DIR), 0.0);
    vec3 color = NodeColor.rgb * light;
    if (gl_InstanceIndex == GraphInts.x) {
        color = mix(color, vec3(1.0), GraphParams.x);
    }
    vertexColor = vec4(color, 1.0);
}
