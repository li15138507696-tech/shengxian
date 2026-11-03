layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in vec2 UV1;
layout(location = 4) in vec2 UV2;
layout(location = 5) in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CameraLocal;
uniform float VolumeScale;

out vec3 vLocal;
out vec4 vColor;

void main() {
    // The submitted pose has already translated source-local coordinates relative to the camera.
    vLocal = Position / max(VolumeScale, 1.0) + CameraLocal;
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
