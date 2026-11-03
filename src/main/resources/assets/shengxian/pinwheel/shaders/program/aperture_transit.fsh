
in vec2 texCoord;
out vec4 fragColor;
uniform sampler2D DiffuseSampler;

uniform float Time;
uniform float Progress;
uniform vec2 Resolution;
uniform vec3 Accent;
uniform float Grade;
uniform float Opacity;
uniform float Reveal;
uniform float Style;

const float TAU = 6.28318530718;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int i = 0; i < 5; i++) {
        value += amplitude * noise(p);
        p = mat2(1.68, -1.12, 1.12, 1.68) * p + vec2(4.7, 8.3);
        amplitude *= 0.48;
    }
    return value;
}

float bubbleLayer(vec2 uv, float scale, float travel, float threshold) {
    vec2 grid = uv * scale;
    grid.y -= travel;
    vec2 cell = floor(grid);
    vec2 local = fract(grid) - 0.5;
    float seed = hash21(cell + vec2(17.0, 41.0));
    local.x += sin(travel * 0.47 + seed * TAU) * 0.16;
    float radius = 0.09 + seed * 0.19;
    float ring = exp(-abs(length(local) - radius) * 42.0);
    return ring * step(threshold, hash21(cell + 9.7));
}

vec3 waterTransit(vec2 uv, vec2 screenUv, float aspect, float t,
                  float progress, float gradePower, out float waterAlpha) {
    vec2 currentUv = uv;
    float broad = fbm(currentUv * 1.65 + vec2(t * 0.075, -t * 0.16));
    float fine = fbm(currentUv * 5.4 + vec2(-t * 0.21, t * 0.09));
    vec2 refraction = vec2(
        sin(currentUv.y * 7.0 + broad * 5.5 - t * 1.2),
        cos(currentUv.x * 8.0 - fine * 4.0 + t * 0.92)) * 0.038;
    currentUv += refraction * (0.55 + progress * 0.65);

    float radius = length(currentUv);
    float angle = atan(currentUv.y, currentUv.x);
    float depth = smoothstep(0.02, 1.08, radius);
    vec3 abyss = vec3(0.003, 0.018, 0.042);
    vec3 ocean = vec3(0.015, 0.17, 0.30);
    vec3 jade = vec3(0.055, 0.42, 0.50);
    vec3 color = mix(abyss, ocean, depth * 0.72 + broad * 0.18);

    // 三层水流带：粗流决定纵深，中层像水墨长绢，细层产生真实折射高光。
    float streamA = pow(max(0.0, sin(angle * 5.0 + broad * 5.0
        - t * 0.82 + radius * 10.0)), 8.0);
    float streamB = pow(max(0.0, sin(angle * 9.0 - fine * 4.0
        + t * 1.14 - radius * 17.0)), 12.0);
    float inkWash = smoothstep(0.43, 0.76, broad * 0.68 + fine * 0.38);
    color += mix(ocean, jade, fine) * streamA * 0.52;
    color += vec3(0.38, 0.82, 0.88) * streamB * (0.22 + progress * 0.28);
    color *= 1.0 - inkWash * 0.22;

    // 焦散不是平铺纹理，而是被流场弯曲的双尺度光网。
    vec2 causticUv = currentUv * (7.5 + Grade * 0.8);
    float causticA = abs(sin(causticUv.x + sin(causticUv.y * 1.4 + t * 1.6))
        + sin(causticUv.y * 1.17 - t * 1.3));
    float causticB = abs(sin(causticUv.x * 1.73 - t * 1.8)
        + sin(causticUv.y * 1.51 + broad * 4.0 + t * 1.1));
    float caustics = pow(max(0.0, 1.0 - abs(causticA - causticB)), 13.0);
    color += vec3(0.42, 0.92, 1.0) * caustics * (0.16 + progress * 0.34);

    float bubbles = bubbleLayer(currentUv + refraction, 7.0, t * 0.72, 0.72)
        + bubbleLayer(currentUv * 1.13 - refraction, 12.0, t * 1.18, 0.84) * 0.72;
    color += vec3(0.67, 0.94, 1.0) * bubbles * (0.42 + Grade * 0.08);

    // 出口是会呼吸的液态界膜：中心较暗，边缘水银般翻卷，随后揭开真实仙窍。
    float membraneRadius = 0.095 + sin(t * 2.4) * 0.009 + progress * 0.035;
    float membrane = exp(-abs(radius - membraneRadius - (broad - 0.5) * 0.018) * 72.0);
    float inner = 1.0 - smoothstep(0.025, membraneRadius * 0.72, radius);
    color += mix(jade, vec3(0.86, 0.985, 1.0), 0.76) * membrane * (1.55 + Grade * 0.18);
    color = mix(color, vec3(0.002, 0.016, 0.032), inner * 0.86);

    float vignette = 1.0 - smoothstep(0.54, 1.08,
        length((screenUv - 0.5) * vec2(1.08, 1.0)));
    color *= 0.46 + vignette * 0.72;
    color += Accent * pow(max(0.0, fine - 0.58), 2.3) * 0.32 * gradePower;

    float maxRevealRadius = 0.5 * length(vec2(aspect, 1.0)) + 0.22;
    float revealRadius = mix(-0.12, maxRevealRadius, smoothstep(0.0, 1.0, Reveal));
    waterAlpha = clamp(Opacity, 0.0, 1.0) * smoothstep(
        revealRadius - 0.10, revealRadius + 0.10,
        length((screenUv - 0.5) * vec2(aspect, 1.0)));
    return max(color, vec3(0.0));
}

void main() {
    float aspect = Resolution.x / max(1.0, Resolution.y);
    vec2 uv = (texCoord - 0.5) * vec2(aspect, 1.0);
    float t = Time;
    float gradePower = 1.0 + Grade * 0.16;

    if (Style > 0.5) {
        float waterAlpha;
        vec3 waterColor = waterTransit(uv, texCoord, aspect, t, Progress, gradePower, waterAlpha);
        vec4 scene = texture(DiffuseSampler, texCoord);
        fragColor = vec4(mix(scene.rgb, waterColor, clamp(waterAlpha, 0.0, 1.0)), 1.0);
        return;
    }

    float ink = fbm(uv * 2.4 + vec2(t * 0.11, -t * 0.16));
    float warp = (ink - 0.5) * 0.24;
    float angle = atan(uv.y, uv.x) + warp + t * 0.08;
    float wrappedAngle = fract(angle / TAU + 1.0) * TAU;
    float radius = length(uv) + warp * 0.11;
    vec2 direction = uv / max(0.018, length(uv));

    vec3 midnight = vec3(0.006, 0.008, 0.026);
    vec3 deep = mix(vec3(0.018, 0.028, 0.090), Accent * 0.16, 0.56);
    vec3 color = mix(midnight, deep, smoothstep(0.02, 0.92, radius));

    // 纵深星流：极坐标网格随时间向镜头高速喷涌，形成真正的前进感。
    float travel = t * (1.55 + Grade * 0.18);
    float inverseRadius = 0.085 / max(0.018, radius);
    float ringCoordinate = inverseRadius * 46.0 - travel * 8.0;
    float ringCell = floor(ringCoordinate);
    float ringPhase = fract(ringCoordinate);
    float angularCells = 54.0 + Grade * 10.0;
    float angularCoordinate = wrappedAngle / TAU * angularCells;
    float angularIndex = mod(floor(angularCoordinate), angularCells);
    vec2 starCell = vec2(angularIndex, ringCell);
    float starSeed = hash21(starCell);
    float starGate = step(0.76 - Grade * 0.025, starSeed);
    float angularDistance = abs(fract(angularCoordinate) - 0.5);
    float starCore = exp(-angularDistance * angularDistance * 150.0)
            * exp(-abs(ringPhase - 0.56) * 13.0) * starGate;
    float starTail = exp(-angularDistance * angularDistance * 72.0)
            * (1.0 - smoothstep(0.12, 0.95, ringPhase)) * starGate;
    color += mix(vec3(0.62, 0.76, 1.0), Accent, starSeed) * (starCore * 2.8 + starTail * 0.72) * gradePower;

    // 水墨破界纱：不是规则科幻隧道，而是被速度拉成长绢的流动墨层。
    // atan 在负 X 轴有 -PI/+PI 分支。非周期噪声改用圆周方向向量采样，消除左侧接缝。
    float silkNoise = fbm(direction * 3.15
            + vec2(inverseRadius * 0.36 - t * 0.82, inverseRadius * 0.24 + t * 0.19));
    float silk = pow(max(0.0, 1.0 - abs(sin(angle * 7.0 + silkNoise * 4.2
            + inverseRadius * 1.8 - t * 1.7))), 7.0);
    silk *= smoothstep(0.05, 0.32, radius) * (1.0 - smoothstep(0.72, 1.05, radius));
    color += mix(Accent * 0.22, vec3(0.76, 0.86, 1.0), silkNoise) * silk * 1.35;

    // 多层破界光环被噪声侵蚀，避免廉价、均匀的同心圆。
    float rings = 0.0;
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float target = 0.16 + fi * 0.13 + sin(t * (0.38 + fi * 0.07) + fi) * 0.018;
        float broken = noise(direction * (2.6 + fi * 0.72)
                + vec2(t * 0.22 + fi * 9.0, inverseRadius * 0.18 - t * 0.13));
        float ring = exp(-abs(radius - target - (broken - 0.5) * 0.032) * (82.0 - fi * 8.0));
        rings += ring * (0.52 + broken * 0.66);
    }
    color += mix(Accent, vec3(0.91, 0.96, 1.0), 0.58) * rings * (0.68 + Progress * 0.48);

    float iris = 1.0 - smoothstep(0.035, 0.19, radius + warp * 0.22);
    float halo = exp(-abs(radius - 0.105 - warp * 0.08) * 55.0);
    float pupil = 1.0 - smoothstep(0.018, 0.068, radius);
    color += mix(Accent, vec3(0.96, 0.985, 1.0), 0.72) * halo * (1.6 + Grade * 0.22);
    color = mix(color, vec3(0.012, 0.014, 0.038), pupil * 0.92);
    color += vec3(0.92, 0.96, 1.0) * iris * iris * (0.25 + Progress * 0.62);

    float vignette = 1.0 - smoothstep(0.52, 1.04, length((texCoord - 0.5) * vec2(1.1, 1.0)));
    color *= 0.48 + vignette * 0.68;
    color += Accent * pow(max(0.0, ink - 0.61), 2.2) * 0.44;
    // 到达仙窍时由中心向外开启出口，而不是整张全屏贴图突然消失。
    float maxRevealRadius = 0.5 * length(vec2(aspect, 1.0)) + 0.22;
    float revealRadius = mix(-0.12, maxRevealRadius, smoothstep(0.0, 1.0, Reveal));
    float revealMask = smoothstep(revealRadius - 0.095, revealRadius + 0.095,
            length((texCoord - 0.5) * vec2(aspect, 1.0)));
    vec4 scene = texture(DiffuseSampler, texCoord);
    float effectAlpha = clamp(Opacity, 0.0, 1.0) * revealMask;
    fragColor = vec4(mix(scene.rgb, color, effectAlpha), 1.0);
}
