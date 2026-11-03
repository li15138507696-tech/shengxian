uniform vec3 CameraLocal;
uniform float Strength;
uniform float Storm;
uniform float Time;
uniform int Steps;
uniform float LowQuality;
uniform float VolumeScale;
uniform sampler2D SceneDepthSampler;
uniform vec2 FrameSize;
uniform mat4 InverseProjection;

in vec3 vLocal;
in vec4 vColor;
out vec4 fragColor;

const vec3 BOX_MIN = vec3(-10.0, 10.0, -10.0);
const vec3 BOX_MAX = vec3(10.0, 16.0, 10.0);

float hash31(vec3 p) {
    p = fract(p * .1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    vec3 w = f * f * (3.0 - 2.0 * f);
    float lo = mix(mix(hash31(cell), hash31(cell + vec3(1, 0, 0)), w.x),
                   mix(hash31(cell + vec3(0, 1, 0)), hash31(cell + vec3(1, 1, 0)), w.x), w.y);
    float hi = mix(mix(hash31(cell + vec3(0, 0, 1)), hash31(cell + vec3(1, 0, 1)), w.x),
                   mix(hash31(cell + vec3(0, 1, 1)), hash31(cell + vec3(1, 1, 1)), w.x), w.y);
    return mix(lo, hi, w.z);
}

vec2 boxInterval(vec3 origin, vec3 direction) {
    vec3 safeSign = vec3(direction.x < 0.0 ? -1.0 : 1.0,
                         direction.y < 0.0 ? -1.0 : 1.0,
                         direction.z < 0.0 ? -1.0 : 1.0);
    vec3 invDirection = safeSign / max(abs(direction), vec3(.00001));
    vec3 a = (BOX_MIN - origin) * invDirection;
    vec3 b = (BOX_MAX - origin) * invDirection;
    vec3 lo = min(a, b), hi = max(a, b);
    return vec2(max(max(lo.x, lo.y), lo.z), min(min(hi.x, hi.y), hi.z));
}

// The domain curls around a real toroidal field; rotating it changes density throughout the box.
float density(vec3 p, out float light, out float mist) {
    float radius = length(p.xz);
    float angle = atan(p.z, p.x);
    float curl = Time * .040 + radius * .12;
    mat2 rotation = mat2(cos(curl), -sin(curl), sin(curl), cos(curl));
    vec2 spun = rotation * p.xz;
    vec3 q = vec3(spun.x, p.y * 1.8, spun.y) * .48;
    q += vec3(Time * .018, -Time * .011, Time * .008);
    float coarse = noise3(q);
    float detail = LowQuality > .5 ? .5 : noise3(q * 2.17 + 9.3);
    float billow = coarse * .74 + detail * .26;
    float centerY = 12.85 + sin(angle * 3.0 + radius * .55 - Time * .035) * .32;
    float torusDistance = length(vec2((radius - 6.0) * .82, (p.y - centerY) * 1.12));
    float torus = 1.0 - smoothstep(.90, 2.85, torusDistance);
    float edge = smoothstep(10.0, 10.75, p.y) * (1.0 - smoothstep(15.1, 16.0, p.y));
    edge *= 1.0 - smoothstep(8.6, 9.85, radius);
    float cloud = smoothstep(.28, .70, torus * (.48 + billow * .92)) * edge;

    // Sparse illuminated air in the eye makes the opening and shafts genuinely volumetric.
    float eye = exp(-radius * radius * .22);
    float beam = pow(.5 + .5 * sin(angle * 9.0 + radius * .7 + Time * .025), 6.0);
    mist = eye * edge * (.018 + beam * .026);
    float sunward = noise3(q + vec3(-.32, .95, .21));
    float softShadow = exp(-max(0.0, sunward - billow + torus * .25) * 2.6);
    float upperLight = smoothstep(10.4, 15.5, p.y);
    light = (.32 + upperLight * .53 + softShadow * .42) * (1.0 - Storm * .48);
    light += eye * (.8 + beam * 1.25);
    return (cloud * (.74 + Storm * .48) + mist) * edge;
}

void main() {
    // Strength is also carried on the box vertices; use the lesser value without squaring the fade.
    float strength = min(clamp(Strength, 0.0, 1.0), clamp(vColor.a, 0.0, 1.0));
    if (strength <= .001) discard;
    vec3 delta = vLocal - CameraLocal;
    float rayLength = length(delta);
    if (rayLength < .0001) discard;
    vec3 direction = delta / rayLength;
    vec2 interval = boxInterval(CameraLocal, direction);
    float start = max(interval.x, 0.0), finish = interval.y;
    // Integrate only the volume before the first opaque surface, rather than depth-testing the exit face.
    vec2 depthUv=clamp(gl_FragCoord.xy/FrameSize,vec2(0),vec2(1));
    float sceneDepth=texture(SceneDepthSampler,depthUv).r;
    if(sceneDepth<.999999) {
        vec4 view=InverseProjection*vec4(depthUv*2.0-1.0,sceneDepth*2.0-1.0,1.0);
        float sceneDistance=length(view.xyz/max(abs(view.w),.000001));
        finish=min(finish,sceneDistance / max(VolumeScale,1.0));
    }
    if (finish <= start) discard;
    int count = clamp(Steps, 16, 24);
    float stepLength = (finish - start) / float(count);
    // Stable source-local jitter softens banding without frame-to-frame temporal noise.
    float jitter = .30 + .40 * hash31(floor(vLocal * 7.0));
    float distanceAlongRay = start + stepLength * jitter;
    float transmittance = 1.0;
    vec3 scattering = vec3(0.0);
    vec3 lightDirection = normalize(vec3(-.30, .91, .24));
    float forwardLight = pow(clamp(dot(direction, lightDirection) * .5 + .5, 0.0, 1.0), 5.0);
    for (int i = 0; i < 24; i++) {
        if (i >= count || transmittance < .025) break;
        vec3 samplePosition = CameraLocal + direction * distanceAlongRay;
        float illumination, mist;
        float sampleDensity = density(samplePosition, illumination, mist);
        float opacity = 1.0 - exp(-sampleDensity * stepLength * .78);
        float radiance = illumination + forwardLight * (.28 + mist * 18.0);
        scattering += transmittance * opacity * vColor.rgb * radiance;
        transmittance *= 1.0 - opacity;
        distanceAlongRay += stepLength;
    }
    float integratedOpacity = 1.0 - transmittance;
    float alpha = integratedOpacity * strength;
    if (alpha < .003) discard;
    // Convert integrated premultiplied scattering to the RenderType's ordinary translucent blend.
    vec3 color = scattering / max(integratedOpacity, .0001);
    fragColor = vec4(min(color, vec3(2.0)), min(alpha, .94));
}
