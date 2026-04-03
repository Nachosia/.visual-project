#version 150

layout(std140) uniform CompositeParams {
    vec4 ScreenAlpha;
};

uniform sampler2D ShadertoyFrame;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 uv = clamp(texCoord0, vec2(0.0), vec2(1.0));
    vec4 sampled = texture(ShadertoyFrame, uv);
    vec4 color = vec4(sampled.rgb, sampled.a * ScreenAlpha.z);
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
