#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
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
    vec4 BackdropParams;
};

in vec3 Position;

out vec2 localPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localPos = Position.xy - Rect.xy;
}
