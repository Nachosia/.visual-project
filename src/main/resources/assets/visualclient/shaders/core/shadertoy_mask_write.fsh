#version 150

layout(std140) uniform MaskParams {
    vec4 MaskColor;
};

out vec4 fragColor;

void main() {
    if (MaskColor.a <= 0.001) {
        discard;
    }
    fragColor = MaskColor;
}
