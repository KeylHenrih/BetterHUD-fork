#version 330

#define HEIGHT_BIT 13
#define MAX_BIT 10
#define ADD_OFFSET 4095
#define DEFAULT_OFFSET 10
#define L 10.0

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#endif

#moj_import <dynamictransforms.glsl>
#moj_import <projection.glsl>
#moj_import <globals.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
#endif

out vec4 vertexColor;
out vec2 texCoord0;

out float applyColor;

bool range(float t, float m1, float m2) {
    return t >= m1 && t <= m2;
}

bool range(vec2 t, vec2 m1, vec2 m2) {
    return range(t.x, m1.x, m2.x) && range(t.y, m1.y, m2.y);
}

bool range(vec3 t, vec3 m1, vec3 m2) {
    return range(t.x, m1.x, m2.x) && range(t.y, m1.y, m2.y) && range(t.z, m1.z, m2.z);
}

#if defined(VULKAN) || defined(HUD_USE_LOCATION)
#ifndef gl_VertexID
#define gl_VertexID gl_VertexIndex
#endif
layout(location = 5) out vec2 hudFill;
layout(location = 6) out vec2 hudQuadUV;
#else
out vec2 hudFill;
out vec2 hudQuadUV;
#endif
float jmod(float x, float y) { float m = x - y * floor(x / y); return m >= y ? m - y : (m < 0.0 ? m + y : m); }
float getElapsedTicks(float tStart, float gameTimeTicks) {
    return (jmod(gameTimeTicks - tStart + L, 240.0) - L);
}
float getElapsedTicks(float tStart, float gameTimeTicks, float M) {
    return (jmod(gameTimeTicks - tStart + L, M) - L);
}
float getElapsedTicks(float tStart, float gameTimeTicks, float M, float lookaheadWindow) {
    return (jmod(gameTimeTicks - tStart + lookaheadWindow, M) - lookaheadWindow);
}
float easeLinear(float x) { return x; }
float easeOutCubic(float x) { float f = x - 1.0; return f * f * f + 1.0; }
float easeInOutCubic(float x) { if (x < 0.5) return 4.0 * x * x * x; float f = 2.0 * x - 2.0; return (x - 1.0) * f * f + 1.0; }
float easeOutQuad(float x) { return x * (2.0 - x); }
float easeInOutQuad(float x) { if (x < 0.5) return 2.0 * x * x; return -1.0 + (4.0 - 2.0 * x) * x; }
float easeOutSine(float x) { return sin((x * 3.1415926535) / 2.0); }
float easeInOutSine(float x) { return -(cos(3.1415926535 * x) - 1.0) / 2.0; }
float bh_pow(float b, float e) {
    if (b == 0.0) return 0.0;
    if (floor(e) == e) {
        if (floor(e * 0.5) * 2.0 == e) return pow(abs(b), e);
        else return sign(b) * pow(abs(b), e);
    }
    return b >= 0.0 ? pow(b, e) : 0.0;
}

void main() {
    vec3 pos = Position;
    vec2 ui = ceil(2 / vec2(ProjMat[0][0], -ProjMat[1][1]));
    vec2 uiScreen = ui / ScreenSize;
    vec3 color = Color.xyz;
    applyColor = 0;
    vertexColor = Color;
    hudFill = vec2(-1.0, -1.0);
int hudCorner = int(gl_VertexID) & 3;
hudQuadUV = vec2(float((hudCorner == 2 || hudCorner == 3) ? 1.0 : 0.0), float((hudCorner == 1 || hudCorner == 2) ? 1.0 : 0.0));
    if (pos.y >= ui.y && ProjMat[3].x == -1) {
        int bit = int(pos.y) >> HEIGHT_BIT;

        if (((bit >> MAX_BIT) & 1) == 1) {

            int id = bit - (1 << MAX_BIT);

            if (pos.x < -10000.0) {
                int vid = gl_VertexID % 4;
                float isBottom = (vid == 1 || vid == 2) ? 1.0 : 0.0;
                pos.x += 0.25 * (pos.y - float(DEFAULT_OFFSET) - isBottom * 8.0) + 1.75;
            }

            pos.x -= 0.5 * ui.x;
            pos.y -= (bit << HEIGHT_BIT) + ADD_OFFSET + DEFAULT_OFFSET;

            float xGui = 0.0;
            float yGui = 0.0;
            float layer = 0.0;
            float opacity = 1.0;
            int property = 0;

            switch (id) {
                case 0: {
    // Reserved safe static fallback case for overflown layouts
    break;
}
case 1: {
    layer = 1;
    break;
}
default: {
    break;
}
            }

            vertexColor *= vec4(1.0, 1.0, 1.0, opacity);

            //Wave
            if ((property & 1) > 0) {
                pos.y += 4 * sin((GameTime * 1200 + pos.x / ui.x) * 3.1415 * 2);
            }
            //Rainbow
            if ((property & 2) > 0) {
                int hash = int(pos.x) * int(pos.y);
                float time = GameTime * 1200;
                hash = 31 * (hash + int(vertexColor.x + time));
                float r = float(hash % 224 + 32) / 255;
                hash = 31 * (hash + int(vertexColor.y + time));
                float g = float(hash % 224 + 32) / 255;
                hash = 31 * (hash + int(vertexColor.z + time));
                float b = float(hash % 224 + 32) / 255;
                float maxValue = max(max(r, g), b);
                vertexColor = vec4(pow(r / maxValue, 3), pow(g / maxValue, 3), pow(b / maxValue, 3), vertexColor.w);
            }
            //Tiny rainbow
            if ((property & 4) > 0) {
                int hash = int(pos.x) * int(pos.y);
                float time = GameTime * 1200;
                hash = 31 * (hash + int(vertexColor.x + time));
                float r = vertexColor.x + float(hash % 64) / 255;
                hash = 31 * (hash + int(vertexColor.y + time));
                float g = vertexColor.y + float(hash % 64) / 255;
                hash = 31 * (hash + int(vertexColor.z + time));
                float b = vertexColor.z + float(hash % 64) / 255;
                vertexColor = vec4(r, g, b, vertexColor.w);
            }

            pos.x += xGui;
            pos.y += yGui;
            pos.z += layer;

        }
    } else {
//HideExp        vec3 exp = vec3(128.0, 255.0, 32.0);
//HideExp        if (ProjMat[3].x == -1 && range(pos.y, ui.y - 60, ui.y - 20) && range(pos.x, ui.x / 2 - 60, ui.x / 2 + 60) && (range(color, exp / 256, exp / 254) || color == vec3(0))) {
//HideExp            vertexColor = vec4(0);
//HideExp        }
    }
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor *= sample_lightmap(Sampler2, UV2);
#endif

    

    texCoord0 = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
}
