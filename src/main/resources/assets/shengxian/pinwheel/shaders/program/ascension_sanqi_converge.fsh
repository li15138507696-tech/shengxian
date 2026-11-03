// Broad ink-silk, fine luminous thread and moving highlights. Palette arrives in vertex color.
uniform float VeilRenderTime;
in vec2 vUv;
in vec4 vColor;
in vec3 vNormal;
in vec3 vViewPos;
out vec4 fragColor;
void main() {
    float sourceAlpha=clamp(vColor.a,0.0,1.0);
    if(sourceAlpha<=0.006) discard;
    vec3 normal=vNormal/max(length(vNormal),0.0001);
    vec3 eye=-vViewPos/max(length(vViewPos),0.0001);
    float rim=pow(1.0-clamp(abs(dot(normal,eye)),0.0,1.0),2.0);
    float across=abs(vUv.y*2.0-1.0);
    float silk=pow(max(0.0,1.0-across),0.75);
    float flow=0.5+0.5*sin(vUv.x*26.0-VeilRenderTime*.8+sin(vUv.y*8.0));
    float fibers=0.5+0.5*sin(vUv.y*41.0+vUv.x*17.0+VeilRenderTime*.22);
    float thread=exp(-across*18.0);
    float traveling=pow(max(0.0,sin(vUv.x*15.0-VeilRenderTime*.7)),12.0);
    float alpha=sourceAlpha*silk*(.42+flow*.36+fibers*.14);
    if(alpha<0.008) discard;
    float radiance=(.72+flow*.24+thread*.95+traveling*.95+rim*.22)*.78;
    vec3 color=vColor.rgb*radiance;
    // Values above one feed the existing Veil bloom pass; the main pass remains translucent.
    fragColor=vec4(min(color,vec3(2.0)),min(alpha,.72));
}
