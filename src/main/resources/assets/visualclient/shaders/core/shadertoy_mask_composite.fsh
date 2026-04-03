#version 150

in vec2 texCoord;

uniform sampler2D ShadertoyFrame;
uniform sampler2D MaskTexture;

out vec4 fragColor;

void main() {
    vec4 mask = texture(MaskTexture, texCoord);
    if (mask.a <= 0.001) {
        discard;
    }

    vec4 sampled = texture(ShadertoyFrame, texCoord);
    vec4 color = vec4(sampled.rgb * mask.a, sampled.a * mask.a);
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
