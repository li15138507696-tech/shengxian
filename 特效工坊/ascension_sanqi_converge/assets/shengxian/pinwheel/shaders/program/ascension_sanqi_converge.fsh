// 升仙三气汇聚：顶点色区分三气，Veil 时间驱动流光与薄壳呼吸。
uniform float VeilRenderTime;

in vec2 vUv;
in vec4 vColor;
in vec3 vNormal;
in vec3 vViewPos;
out vec4 fragColor;

void main() {
    float sourceAlpha = clamp(vColor.a, 0.0, 1.0);
    if (sourceAlpha <= 0.006) discard;

    vec3 normal = normalize(vNormal);
    vec3 viewDir = normalize(-vViewPos);
    float facing = clamp(abs(dot(normal, viewDir)), 0.0, 1.0);
    float fresnel = pow(1.0 - facing, 2.4);

    float flow = 0.5 + 0.5 * sin(vUv.y * 18.0 - VeilRenderTime * 1.15
            + sin(vUv.x * 11.0 + VeilRenderTime * 0.24));
    float filament = smoothstep(0.37, 0.90, flow);
    float sparkle = pow(max(0.0, sin(vUv.x * 91.0 + vUv.y * 39.0
            + VeilRenderTime * 1.35)), 22.0)
            * pow(max(0.0, sin(vUv.x * 47.0 - vUv.y * 83.0
            - VeilRenderTime * 0.93)), 20.0);
    float breath = 0.86 + 0.14 * sin(VeilRenderTime * 1.08 + vUv.y * 2.5);

    vec3 source = clamp(vColor.rgb, 0.0, 1.0);
    vec3 color = source * (0.56 + filament * 0.31 + fresnel * 0.36) * breath;
    color += mix(source, vec3(1.0), 0.58) * sparkle * 0.32;

    float alpha = sourceAlpha * (0.10 + filament * 0.46 + fresnel * 0.26 + sparkle * 0.18);
    if (alpha < 0.008) discard;
    fragColor = vec4(clamp(color, 0.0, 1.0), clamp(alpha, 0.0, 0.44));
}
