// Water-and-ink phase imagery. All palette values are supplied by the client, never lore constants.
uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform float Strength;
uniform float Stage;
uniform float Progress;
uniform float Age;
uniform float Time;
uniform float Instability;
uniform float Insight;
uniform vec2 Resolution;
uniform vec3 Weights;
uniform vec3 HeavenColor;
uniform vec3 EarthColor;
uniform vec3 HumanColor;
uniform vec3 JadeColor;
in vec2 texCoord;
out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float noise2(vec2 p) {
    vec2 i=floor(p), f=fract(p);
    f=f*f*(3.0-2.0*f);
    return mix(mix(hash21(i),hash21(i+vec2(1,0)),f.x),
               mix(hash21(i+vec2(0,1)),hash21(i+vec2(1,1)),f.x),f.y);
}
float ink(vec2 p) {
    return noise2(p)*0.57 + noise2(p*2.03+7.7)*0.28 + noise2(p*4.11+13.1)*0.15;
}
float band(float value,float center,float width) {
    return 1.0-smoothstep(width*0.3,width,abs(value-center));
}
void main() {
    vec4 base=texture(DiffuseSampler0,texCoord);
    if(Strength<=0.001) { fragColor=base; return; }
    float depth=texture(DiffuseDepthSampler,texCoord).r;
    if(depth<=0.0008) { fragColor=base; return; }
    vec2 p=texCoord-0.5;
    p.x*=Resolution.x/max(Resolution.y,1.0);
    float r=length(p), angle=atan(p.y,p.x);
    float safe=smoothstep(0.14,0.35,r);
    float edge=smoothstep(0.22,0.72,r);
    float grain=ink(p*6.0+vec2(Time*0.075,-Time*0.055));
    float farField=smoothstep(0.65,0.99,depth);
    vec3 tint=HumanColor;
    float mask=0.0, light=0.0;

    if(Stage<0.5) {
        mask=edge*grain*0.12;
    } else if(Stage<1.5) {
        // Soft peripheral shell fractures, without camera shake or flashing.
        float crack=pow(max(0.0,cos(angle*9.0+sin(r*19.0)*Progress)),36.0);
        mask=edge*(0.05+grain*0.14);
        light=crack*safe*band(r,0.52+Progress*0.09,0.19)*Progress*0.4;
        tint=HeavenColor;
    } else if(Stage<2.5 || Stage>6.5) {
        mask=edge*grain*0.30;
        light=band(r,0.3+min(Age,2.0)*0.18,0.018)*safe*0.18;
        tint=mix(EarthColor,HumanColor,grain);
    } else if(Stage<3.5) {
        float heaven=smoothstep(0.55,0.98,texCoord.y)*band(fract(angle*0.8+Time*0.055+r),0.5,0.19);
        float earth=(1.0-smoothstep(0.05,0.44,texCoord.y))*band(fract(angle*0.8-Time*0.055-r),0.5,0.19);
        float human=band(r,0.47+sin(angle*3.0+Time*0.4)*0.025,0.10);
        vec3 weights=vec3(heaven,earth,human)*(0.22+Weights*0.78);
        tint=(HeavenColor*weights.x+EarthColor*weights.y+HumanColor*weights.z)/max(dot(weights,vec3(1)),0.001);
        mask=(dot(weights,vec3(1))*0.25+edge*grain*0.11)*safe;
        light=Insight*band(r,0.37+sin(Time*0.3)*0.008,0.006)*0.22;
        mask+=Instability*edge*grain*0.11;
    } else if(Stage<5.5) {
        float grow=Stage>4.5?min(Age/2.5,1.0):0.0;
        float ripple=band(fract(r*3.0-Time*0.09),0.5,0.12);
        vec3 qi=mix(HeavenColor,EarthColor,0.5+sin(angle*3.0+Time*0.2)*0.5);
        tint=mix(qi,HumanColor,grain*0.5);
        mask=safe*(band(r,0.35+grow*0.22,0.06)*0.25+edge*grain*0.16);
        light=safe*ripple*edge*grow*0.07;
    } else {
        tint=JadeColor;
        mask=safe*edge*grain*0.15;
        light=safe*band(r,0.25+Age*0.12,0.012)*0.22;
    }
    float blend=clamp(mask*Strength*(0.7+farField*0.3),0.0,0.28);
    float lum=dot(base.rgb,vec3(0.2126,0.7152,0.0722));
    vec3 wash=mix(base.rgb*mix(vec3(1),tint,0.35),tint*(0.32+lum*0.6),0.32);
    vec3 result=mix(base.rgb,wash,blend)+tint*min(light*Strength,0.12);
    fragColor=vec4(clamp(result,0.0,1.0),base.a);
}
