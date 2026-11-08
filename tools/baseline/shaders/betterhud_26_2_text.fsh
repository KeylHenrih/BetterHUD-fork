#version 330



#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#endif

#moj_import <minecraft:dynamictransforms.glsl>

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#endif

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

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

void main() {
#ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
#else
    vec4 texColor = texture(Sampler0, texCoord0);
#endif
#ifdef IS_SEE_THROUGH
    vec4 color = texColor * vertexColor;
#else
    vec4 color = texColor * vertexColor * ColorModulator;
#endif

    if (hudFill.x >= 0.0) {
    if (shouldDiscard(hudQuadUV, hudFill.x, int(hudFill.y + 0.5))) {
        discard;
    }
}

    if (color.a < 0.1) {
        discard;
    }
#ifdef IS_SEE_THROUGH
    fragColor = color * ColorModulator;
#elif defined(IS_GUI)
    fragColor = color;
#else
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif
}
