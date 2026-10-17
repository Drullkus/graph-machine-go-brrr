#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <graphmachine:graphinfo.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    // A grayscale page keeps coverage in red; a colored page keeps it in alpha with white color.
    vec4 sample = texture(Sampler0, texCoord0);
    if (GraphInts.y == 1) {
        sample = sample.rrrr;
    }
    vec4 color = sample * vertexColor;
    if (color.a < 0.5) {
        discard;
    }
    fragColor = vec4(color.rgb, 1.0) * ColorModulator;
}
