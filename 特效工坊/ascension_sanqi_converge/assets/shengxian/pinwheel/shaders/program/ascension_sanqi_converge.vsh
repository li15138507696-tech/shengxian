layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in vec2 UV1;
layout(location = 4) in vec2 UV2;
layout(location = 5) in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
out vec4 vColor;
out vec3 vNormal;
out vec3 vViewPos;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    vViewPos = view.xyz;
    gl_Position = ProjMat * view;
    vUv = UV0;
    vColor = Color;
    vNormal = normalize(mat3(ModelViewMat) * Normal);
}
