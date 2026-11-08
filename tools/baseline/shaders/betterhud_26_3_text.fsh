#version 330
#extension GL_ARB_separate_shader_objects : require

#define HUD_USE_LOCATION



#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif

uniform sampler2D Sampler0;

layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

#if defined(VULKAN) || defined(HUD_USE_LOCATION)
layout(location = 5) in vec2 hudFill;
layout(location = 6) in vec2 hudQuadUV;
#else
in vec2 hudFill;
in vec2 hudQuadUV;
#endif
bool shouldDiscard(vec2 uv, float fill, int splitType) {
    if (fill <= 0.0) return true;
    if (fill >= 1.0) return false;
    if (splitType == 0) return uv.x > fill;
    if (splitType == 1) return uv.x < (1.0 - fill);
    if (splitType == 2) return uv.y < (1.0 - fill);
    if (splitType == 3) return uv.y > fill;
    float PI = 3.1415926535;
    float d = -(atan(uv.y - 0.5, uv.x - 0.5) + PI / 2.0);
    if (splitType == 5) d = -d;
    if (d < 0.0) d += 2.0 * PI;
    if (d > 2.0 * PI) d -= 2.0 * PI;
    return d > (fill * 2.0 * PI);
}

vec4 calculateFinalColor(vec4 color) {
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
#endif

#if !defined(IS_SEE_THROUGH) && !defined(IS_GUI)

#ifdef OIT_ACCUMULATE
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
#else
    vec4 fogColor = FogColor;
#endif

    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif

    return color;
}

void main() {
#ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
#else
    vec4 texColor = texture(Sampler0, texCoord0);
#endif

    vec4 color = texColor * vertexColor * ColorModulator;

    if (hudFill.x >= 0.0) {
    if (shouldDiscard(hudQuadUV, hudFill.x, int(hudFill.y + 0.5))) {
        discard;
    }
}

    if (color.a < 0.1) {
        discard;
    }

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
    fragColor = calculateFinalColor(color);
#endif
}