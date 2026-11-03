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
const vec3 BMIN=vec3(-104,0,-104),BMAX=vec3(104,130,104);
float hash3(vec3 p){p=fract(p*.1031);p+=dot(p,p.yxz+33.33);return fract((p.x+p.y)*p.z);}
float noise3(vec3 p){
 vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
 return mix(mix(mix(hash3(i),hash3(i+vec3(1,0,0)),f.x),mix(hash3(i+vec3(0,1,0)),hash3(i+vec3(1,1,0)),f.x),f.y),
 mix(mix(hash3(i+vec3(0,0,1)),hash3(i+vec3(1,0,1)),f.x),mix(hash3(i+vec3(0,1,1)),hash3(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float fbm(vec3 p){return noise3(p)*.54+noise3(p*2.03+7.1)*.27+noise3(p*4.11+19.3)*.13+noise3(p*8.21)*.06;}
vec2 interval(vec3 o,vec3 d){
 vec3 inv=mix(vec3(-1),vec3(1),greaterThanEqual(d,vec3(0)))/max(abs(d),vec3(.00001));
 vec3 a=(BMIN-o)*inv,b=(BMAX-o)*inv;
 return vec2(max(max(min(a,b).x,min(a,b).y),min(a,b).z),min(min(max(a,b).x,max(a,b).y),max(a,b).z));
}
// A weather front with irregular shelves, an off-center opening, and broad gathering cloud lanes.
float envelope(vec3 p,out vec3 tint,out float glow){
 float r=length(p.xz),ang=atan(p.z,p.x);
 float warp=sin(ang*3.+r*.10)*2.5+sin(p.z*.12+1.2)*1.8;
 vec3 anvil=(p-vec3(3.,85.+warp,0))/vec3(43,20,36);
 float crown=(1.-dot(anvil,anvil))*.95;
 float eye=length(p.xz-vec2(-3,1));
 crown=max(crown,(1.-length((p-vec3(-15,96.,-3))/vec3(27,21,25)))*.72);
 float shelf=1.-length((p-vec3(-7,70.+warp,2))/vec3(33,9,28));
 shelf*=.78;
 float body=max(crown,shelf*.72);
 tint=mix(vec3(.55,.65,.68),vec3(.28,.38,.48),Storm);
 glow=0.;
 for(int j=0;j<3;j++){
  float phase=float(j)*2.0944;
  float h=clamp(p.y/80.,0.,1.);
  float a=phase+h*1.65+Time*.025;
  float rad=mix(24.,2.,h);
  vec2 center=vec2(cos(a),sin(a))*rad;
  vec2 delta=p.xz-center;
  vec2 radial=vec2(cos(a),sin(a));
  float across=dot(delta,vec2(-radial.y,radial.x));
  float along=dot(delta,radial);
  float lane=1.-length(vec2(across/(8.5+sin(h*8.+phase)*2.0),along/12.5));
  lane=min(lane,smoothstep(2.,14.,p.y)*(1.-smoothstep(66.,84.,p.y)));
  lane*=.78;
  if(lane>body){
   body=lane;
   tint=j==0?vec3(.61,.68,.72):(j==1?vec3(.45,.54,.51):vec3(.64,.58,.44));
   glow=.13;
  }
 }
 float ground=exp(-pow((p.y-1.8)/1.9,2.))*smoothstep(6.,13.,r)*(1.-smoothstep(24.,38.,r))*.28;
 if(ground>body){body=ground;tint=vec3(.45,.38,.28);}
 return max(0.,body);
}
float density(vec3 p,out vec3 tint,out float glow){
 p.xz*=.5;
 float base=envelope(p,tint,glow);
 if(base<.012)return 0.;
 base=smoothstep(.030,.24,base);
 float ang=atan(p.z,p.x);
 // Warped multi-scale turbulence: broad billows plus fine fibrous erosion.
 vec3 q=p+vec3(sin(p.y*.08+ang*3.),0.,cos(ang*2.+p.y*.05))*2.0;
 float n=fbm(q*.19+vec3(0,0,-Time*.055));
 float fine=noise3(q*1.15+vec3(Time*.06,0,0));
 float ridge=1.-abs(2.*noise3(q*.42+vec3(7.3,0,Time*.02))-1.);
 ridge=pow(ridge,5.);
 float erosion=(1.-smoothstep(.20,.76,n))*.40+(1.-smoothstep(.25,.85,fine))*.12;
 float body=max(0.,base-erosion);
 body+=base*ridge*.12;
 float bands=.90+.10*sin(p.y*.55+ang*2.+n*3.);
 body*=bands;
 // Thin luminous filaments embedded in the cloud.
 float veins=smoothstep(.68,.96,ridge)*base;
 glow=max(glow,veins*.35);
 tint=mix(tint,vec3(.86,.68,.34),veins*.50);
 tint=mix(tint,vec3(.34,.68,.72),smoothstep(.35,.85,fine)*.18);
 return body*2.15;
}
void main(){
 float strength=min(clamp(Strength,0.,1.),clamp(vColor.a,0.,1.));if(strength<.002)discard;
 vec3 rd=normalize(vLocal-CameraLocal);
 vec2 hit=interval(CameraLocal,rd);float start=max(hit.x,0.),finish=hit.y;
 vec2 uv=clamp(gl_FragCoord.xy/FrameSize,vec2(0),vec2(1));
 float scene=texture(SceneDepthSampler,uv).r;
 if(scene<.999999){vec4 v=InverseProjection*vec4(uv*2.-1.,scene*2.-1.,1);finish=min(finish,length(v.xyz/max(abs(v.w),.000001))/max(VolumeScale,1.));}
 if(finish<=start)discard;
 int count=clamp(Steps,32,80);float stepSize=(finish-start)/float(count);
 float t=start+stepSize*(.3+.4*hash3(floor(vLocal*4.)));
 vec3 accum=vec3(0);float trans=1.;
 vec3 sun=normalize(vec3(-.45,.8,.32));
 for(int i=0;i<80;i++){
  if(i>=count||trans<.012)break;
  vec3 p=CameraLocal+rd*t,tint;float glow;
  float den=density(p,tint,glow);
  if(den>.001){
   float shadow=1.;
   if(LowQuality<.5){vec3 c;float g;float a=density(p+sun*4.,c,g);float b=density(p+sun*11.,c,g);shadow=exp(-(a*4.5+b*7.)*.58);}
   float height=smoothstep(58.,120.,p.y);
   vec3 ambient=vec3(.105,.125,.145)+height*vec3(.045,.050,.055);
   float silver=pow(max(0.,dot(rd,sun)),7.);
   vec3 lighting=ambient+shadow*vec3(1.15,1.02,.85)*(0.50+height*.50)+silver*shadow*.26;
   float eyeGlow=exp(-dot(p.xz+vec2(3,-1),p.xz+vec2(3,-1))*.010)*smoothstep(42.,100.,p.y);
   lighting+=eyeGlow*vec3(.74,.54,.21);
   vec3 color=tint*lighting+vec3(.95,.72,.35)*glow*.12;
   if(Storm>.5)color=mix(color,color*vec3(1.7,.55,.44),Storm*.65);
   float opacity=1.-exp(-den*stepSize*.50);
   accum+=trans*opacity*color;trans*=1.-opacity;
  }
  t+=stepSize;
 }
 float opacity=1.-trans;if(opacity<.003)discard;
 vec3 color=accum/max(opacity,.0001);
 fragColor=vec4(clamp(color,vec3(0),vec3(1.7)),opacity*strength);
}
