#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform PanelStyle {
    vec4 Rect;
    vec4 BorderRadius;
    vec4 InnerGlowParams;
    vec4 OuterGlowParams;
    vec4 BaseColor;
    vec4 BorderColor;
    vec4 InnerGlowColor;
    vec4 OuterGlowColor;
    vec4 NeonBorderParams;
    vec4 NeonBorderColor;
    vec4 ShadeTopColor;
    vec4 ShadeBottomColor;
    vec4 ClipRect;
};

in vec2 localPos;

out vec4 fragColor;

#moj_import <visualclient:sdf_shapes.glsl>
#moj_import <visualclient:sdf_lighting.glsl>

vec3 screenBlend(vec3 base, vec3 tint, float amount) {
    float factor = clamp(amount, 0.0, 1.0);
    vec3 screened = 1.0 - ((1.0 - base) * (1.0 - tint));
    return mix(base, screened, factor);
}

void main() {
    vec2 panelSize = Rect.zw;
    vec2 clipMin = ClipRect.xy;
    vec2 clipMax = ClipRect.xy + ClipRect.zw;
    vec2 fragPos = Rect.xy + localPos;
    if (fragPos.x < clipMin.x || fragPos.y < clipMin.y || fragPos.x > clipMax.x || fragPos.y > clipMax.y) {
        discard;
    }
    float radius = BorderRadius.x;
    float borderWidth = BorderRadius.y;

    float innerGlowRadius = InnerGlowParams.x;
    float innerGlowStrength = InnerGlowParams.y;
    float innerGlowOpacity = InnerGlowParams.z;

    float outerGlowRadius = OuterGlowParams.x;
    float outerGlowStrength = OuterGlowParams.y;
    float outerGlowOpacity = OuterGlowParams.z;

    float neonBorderWidth = NeonBorderParams.x;
    float neonBorderSoftness = NeonBorderParams.y;
    float neonBorderStrength = NeonBorderParams.z;

    vec2 px = localPos - (panelSize * 0.5);
    vec2 halfSize = (panelSize * 0.5) - vec2(max(borderWidth, 1.0));

    float dist = roundedBoxSDF(px, halfSize, radius);
    float aa = 1.0;
    float shapeMask = aaAlpha(dist, aa);
    float borderMask = borderBandMask(dist, borderWidth, aa);
    float innerMask = innerGlowMask(dist, innerGlowRadius) * innerGlowStrength * innerGlowOpacity;
    float outerMask = outerGlowMask(dist, outerGlowRadius) * outerGlowStrength * outerGlowOpacity;
    float neonCoreMask = neonBorderCoreMask(dist, borderWidth, neonBorderWidth, aa) * neonBorderStrength * NeonBorderColor.a;
    float neonGlowMask = neonBorderGlowMask(dist, borderWidth, neonBorderWidth, neonBorderSoftness, aa) * neonBorderStrength * NeonBorderColor.a;

    float shadeFactor = clamp(localPos.y / max(panelSize.y, 1.0), 0.0, 1.0);

    vec4 panel = BaseColor;
    vec4 currentShade = mix(ShadeTopColor, ShadeBottomColor, shadeFactor);
    panel.rgb = mix(panel.rgb, currentShade.rgb, currentShade.a);
    panel.rgb = screenBlend(panel.rgb, InnerGlowColor.rgb, innerMask * InnerGlowColor.a);
    panel.rgb = screenBlend(panel.rgb, BorderColor.rgb, borderMask * BorderColor.a);
    panel.rgb = screenBlend(panel.rgb, NeonBorderColor.rgb, neonCoreMask);
    panel.rgb = screenBlend(panel.rgb, NeonBorderColor.rgb, neonGlowMask * 0.55);
    panel.a *= shapeMask;

    float outerAlpha = OuterGlowColor.a * outerMask;
    float neonOuterAlpha = neonGlowMask * 0.85;
    float combinedOuterAlphaRaw = outerAlpha + neonOuterAlpha;
    float combinedOuterAlpha = clamp(combinedOuterAlphaRaw, 0.0, 1.0);
    vec3 outerRgb = vec3(0.0);
    if (combinedOuterAlphaRaw > 0.001) {
        outerRgb = clamp(
            ((OuterGlowColor.rgb * outerAlpha) + (NeonBorderColor.rgb * neonOuterAlpha)) / combinedOuterAlphaRaw,
            0.0,
            1.0
        );
    }
    vec4 outer = vec4(outerRgb, combinedOuterAlpha);
    vec4 color = mix(outer, panel, shapeMask) * ColorModulator;
    if (color.a <= 0.001) {
        discard;
    }

    fragColor = color;
}
