float aaAlpha(float dist, float aaWidth) {
    return clamp(1.0 - smoothstep(-aaWidth, aaWidth, dist), 0.0, 1.0);
}

float innerGlowMask(float dist, float radiusPx) {
    return clamp(1.0 - smoothstep(-radiusPx, 0.0, -dist), 0.0, 1.0);
}

float outerGlowMask(float dist, float radiusPx) {
    return clamp(1.0 - smoothstep(0.0, radiusPx, dist), 0.0, 1.0);
}

float borderBandMask(float dist, float borderWidth, float aaWidth) {
    if (borderWidth <= 0.0) {
        return 0.0;
    }

    float outer = 1.0 - smoothstep(-aaWidth, aaWidth, dist);
    float inner = 1.0 - smoothstep(-borderWidth - aaWidth, -borderWidth + aaWidth, dist);
    return clamp(outer - inner, 0.0, 1.0);
}

float neonBorderCoreMask(float dist, float borderWidth, float widthPx, float aaWidth) {
    if (widthPx <= 0.0) {
        return 0.0;
    }

    float halfWidth = max(widthPx * 0.5, aaWidth * 0.5);
    float edgeDist = abs(dist + (borderWidth * 0.5));
    return clamp(1.0 - smoothstep(halfWidth - aaWidth, halfWidth + aaWidth, edgeDist), 0.0, 1.0);
}

float neonBorderGlowMask(float dist, float borderWidth, float widthPx, float softnessPx, float aaWidth) {
    if (widthPx <= 0.0 || softnessPx <= 0.0) {
        return 0.0;
    }

    float halfWidth = max(widthPx * 0.5, aaWidth * 0.5);
    float edgeDist = abs(dist + (borderWidth * 0.5));
    float glow = 1.0 - smoothstep(halfWidth, halfWidth + max(softnessPx, aaWidth), edgeDist);
    float core = neonBorderCoreMask(dist, borderWidth, widthPx, aaWidth);
    return clamp(glow * (1.0 - (core * 0.75)), 0.0, 1.0);
}
