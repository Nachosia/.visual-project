#version 150

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;

void main() {
    vec4 clip = ProjMat * ModelViewMat * vec4(Position, 1.0);
    clip.z -= 0.0001 * clip.w;
    gl_Position = clip;
}
