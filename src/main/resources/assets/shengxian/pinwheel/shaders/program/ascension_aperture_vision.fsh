// All landscape colors arrive in vColor; no screen-space scene sampling or world mutation.
uniform float VeilRenderTime;
in vec2 vUv;
in vec4 vColor;
in vec3 vViewPos;
in vec3 vNormal;
flat in int vLayer;
out vec4 fragColor;

float hash11(float p) {
    return fract(sin(p * 127.1 + 311.7) * 43758.5453);
}

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 cell = floor(p), f = fract(p);
    vec2 w = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), w.x),
               mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + vec2(1.0)), w.x), w.y);
}

float fbm(vec2 p) {
    float value = .52 * noise(p);
    p = mat2(1.7, 1.1, -1.1, 1.7) * p;
    value += .26 * noise(p);
    p = mat2(1.7, 1.1, -1.1, 1.7) * p;
    return value + .13 * noise(p);
}

// Linear peak interpolation preserves the distinct, sharp ridgelines of miniature mountains.
float peaks(float p, float seed) {
    float cell = floor(p), f = fract(p);
    return mix(hash11(cell + seed), hash11(cell + 1.0 + seed), f);
}

float ridge(float x, float seed) {
    return .36 * peaks(x * 2.6 + seed, seed)
         + .19 * peaks(x * 6.1, seed + 21.0)
         + .075 * peaks(x * 14.3, seed + 51.0);
}

vec4 stars(vec2 p, float time) {
    vec2 field = p * vec2(24.0, 22.0);
    vec2 cell = floor(field), local = fract(field) - .5;
    float seed = hash21(cell);
    vec2 center = vec2(hash21(cell + 9.2), hash21(cell + 17.8)) * .60 - .30;
    vec2 d = local - center;
    float small = exp(-dot(d, d) * 780.0);
    float crossLight = exp(-abs(d.x) * 130.0 - abs(d.y) * 19.0)
                     + exp(-abs(d.y) * 130.0 - abs(d.x) * 19.0);
    float twinkle = .62 + .38 * sin(time * .45 + seed * 93.0);
    float star = (small + crossLight * .35) * step(.953, seed) * twinkle;
    star *= smoothstep(-.12, .22, p.y);
    float nebula = pow(fbm(p * 2.8 + vec2(time * .006, 2.0)), 3.0)
                 * smoothstep(-.05, .60, p.y) * .11;
    return vec4(vColor.rgb * (1.1 + star * 1.7), clamp(star * .96 + nebula, 0.0, .97));
}

vec4 mountain(vec2 p, int layer, float aa) {
    float index = layer == 2 ? 0.0 : layer == 3 ? 1.0 : 2.0;
    float seed = 3.0 + index * 13.7;
    float height = ridge(p.x + .30 * index, seed) - .12 - index * .28;
    float silhouette = 1.0 - smoothstep(height - aa, height + aa, p.y);
    float below = max(0.0, height - p.y);
    float rock = fbm(vec2(p.x * 12.0, p.y * 8.0) + seed);
    float slopeLines = pow(.5 + .5 * sin(p.y * 74.0 + rock * 14.0 + p.x * 4.0), 8.0);
    float contour = exp(-below * 65.0);
    float mist = exp(-pow((p.y + .35 + index * .21) * 5.3, 2.0));
    float shade = .39 + rock * .34 + mist * .25 + contour * .62 + slopeLines * .075;
    float alpha = silhouette * (.87 - smoothstep(.24, .95, below) * .28);
    return vec4(vColor.rgb * shade, alpha);
}

vec4 clouds(vec2 p, float time) {
    vec2 drift = vec2(time * .012, -time * .003);
    float large = fbm(p * vec2(3.0, 5.5) + drift);
    float fine = fbm(p * vec2(11.0, 17.0) - drift * .7);
    float wave = sin(p.x * 5.0 + large * 5.0) * .045;
    float upper = exp(-pow((p.y + .19 + wave + (large - .45) * .25) / .12, 2.0));
    float lower = exp(-pow((p.y + .61 + wave) / .21, 2.0));
    float ribbons = .63 + .37 * sin(p.y * 68.0 + large * 14.0 + fine * 3.0);
    float shape = (upper * .72 + lower * .56) * (.49 + fine * .62);
    float silver = pow(max(0.0, ribbons), 5.0) * .36;
    return vec4(vColor.rgb * (.72 + large * .35 + silver), clamp(shape, 0.0, .67));
}

vec4 heavenlyLight(vec2 p, float radius, float angle, float time) {
    vec2 sun = p - vec2(-.27, .42);
    float distanceToSun = length(sun);
    float disc = 1.0 - smoothstep(.055, .067, distanceToSun);
    float corona = exp(-distanceToSun * 13.0) * .39;
    float sunAngle = atan(sun.y, sun.x);
    float rays = pow(max(0.0, sin(sunAngle * 13.0 + time * .025)), 18.0);
    rays *= exp(-distanceToSun * 4.5) * smoothstep(.055, .14, distanceToSun) * .20;
    float shafts = exp(-pow((p.x + .27 + (p.y - .42) * .10) * 27.0, 2.0));
    shafts *= smoothstep(-.60, -.04, p.y) * (1.0 - smoothstep(.27, .47, p.y)) * .10;
    float orbit = exp(-pow((radius - .997) * 135.0, 2.0));
    float arc = pow(max(0.0, sin(angle * 5.0 - time * .055)), 12.0);
    float alpha = disc * .90 + corona + rays + shafts + orbit * (.13 + arc * .62);
    float radiance = 1.1 + disc * .80 + orbit * arc * .70;
    return vec4(vColor.rgb * radiance, clamp(alpha, 0.0, .96));
}

void main() {
    float sourceAlpha = clamp(vColor.a, 0.0, 1.0);
    if (sourceAlpha <= .001) discard;
    vec2 p = (vUv * 2.0 - 1.0) * 1.08;
    float radius = length(p);
    if (radius > 1.075) discard;
    float aa = max(fwidth(radius), .0014);
    float time = VeilRenderTime;
    float angle = atan(p.y, p.x);
    float interior = 1.0 - smoothstep(.965, 1.015, radius);
    vec4 landscape;
    if (vLayer == 0) {
        float horizon = exp(-pow((p.y + .20) * 3.4, 2.0));
        float heavens = smoothstep(-.50, .80, p.y);
        float skyVeil = fbm(p * 2.8 + vec2(time * .004, 0.0));
        float glow = exp(-length(p - vec2(-.27, .42)) * 3.5);
        float shade = .36 + horizon * .82 + (1.0 - heavens) * .25 + skyVeil * .10 + glow * .20;
        landscape = vec4(vColor.rgb * shade, .96);
    } else if (vLayer == 1) {
        landscape = stars(p, time);
    } else if (vLayer == 2 || vLayer == 3 || vLayer == 5) {
        landscape = mountain(p, vLayer, max(fwidth(p.y) * 1.1, .002));
    } else if (vLayer == 4) {
        landscape = clouds(p, time);
    } else {
        landscape = heavenlyLight(p, radius, angle, time);
    }
    landscape.a *= interior;

    // Each world-space slice carries its own thin rim and broad halo at a distinct depth.
    float filament = exp(-pow((radius - .991) / max(.0045, aa), 2.0));
    float halo = exp(-pow((radius - .994) / .035, 2.0));
    float breathing = .76 + .24 * sin(angle * 3.0 + float(vLayer) * .83 - time * .07);
    float rim = filament * (.22 + breathing * .21) + halo * .075;
    vec3 eye = -vViewPos / max(length(vViewPos), .0001);
    vec3 normal = vNormal / max(length(vNormal), .0001);
    float grazing = pow(1.0 - abs(dot(normal, eye)), 2.0);
    rim *= 1.0 - grazing * .35;
    float combinedAlpha = landscape.a + rim * (1.0 - landscape.a);
    if (combinedAlpha * sourceAlpha < .004) discard;
    vec3 color = (landscape.rgb * landscape.a + vColor.rgb * (1.35 + filament * .5)
                 * rim * (1.0 - landscape.a)) / max(combinedAlpha, .0001);
    fragColor = vec4(min(color, vec3(2.5)), clamp(combinedAlpha * sourceAlpha, 0.0, .98));
}
