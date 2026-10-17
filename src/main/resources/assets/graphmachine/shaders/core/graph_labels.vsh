#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <graphmachine:graphinfo.glsl>

// One instance per glyph; the quad comes from gl_VertexIndex (6 vertices).
// Offsets and sizes are in font pixels: x grows to the right, y grows upward from the label baseline.
layout(location = 0) in vec3 NodePos;
layout(location = 1) in float NodeScale;
layout(location = 2) in vec2 GlyphOffset;
layout(location = 3) in vec2 GlyphSize;
layout(location = 4) in vec4 GlyphUV;
layout(location = 5) in vec4 GlyphColor;
layout(location = 6) in float LineOffset;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;

void main() {
    int vertex = gl_VertexIndex;
    vec2 corner = vec2(
        (vertex == 1 || vertex == 2 || vertex == 4) ? 1.0 : 0.0,
        (vertex == 2 || vertex == 4 || vertex == 5) ? 1.0 : 0.0
    );

    // ModelViewMat is the camera rotation only, so its rows are the camera axes in world space.
    vec3 right = vec3(ModelViewMat[0][0], ModelViewMat[1][0], ModelViewMat[2][0]);
    vec3 up = vec3(ModelViewMat[0][1], ModelViewMat[1][1], ModelViewMat[2][1]);

    float scale = CameraPosScale.w;
    vec2 pixel = GlyphOffset + corner * GlyphSize;
    vec3 relative = NodePos - CameraPosScale.xyz;
    vec3 position = relative
        + up * (NodeScale * 0.5 + (LineOffset + pixel.y) * scale)
        + right * (pixel.x * scale);
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);

    texCoord0 = vec2(mix(GlyphUV.x, GlyphUV.z, corner.x), mix(GlyphUV.w, GlyphUV.y, corner.y));

    float distance = length(relative);
    float fade = 1.0 - smoothstep(GraphParams.z, GraphParams.w, distance);
    vertexColor = vec4(GlyphColor.rgb, GlyphColor.a * fade);
}
