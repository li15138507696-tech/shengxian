package net.guzhenren.shengxian.core;

/**
 * Shared artistic geometry for the Minecraft renderer and offline GPU preview.
 * Source-local coordinates; no Minecraft classes, world writes, random spawning or per-vertex objects.
 */
public final class AscensionAuraMesh {
    public record Color(float r, float g, float b) {}
    public static final Color HEAVEN=new Color(.75f,.90f,1.0f);
    public static final Color EARTH=new Color(.19f,.78f,.54f);
    public static final Color HUMAN=new Color(1.0f,.62f,.25f);
    public static final Color JADE=new Color(.47f,.91f,.69f);
    private static final Color[] QI={HEAVEN,EARTH,HUMAN};
    private static final double TAU=Math.PI*2;
    @FunctionalInterface public interface Sink {
        void vertex(float x,float y,float z,float u,float v,float r,float g,float b,float a,float nx,float ny,float nz);
    }
    private AscensionAuraMesh() {}

    public static void draw(AscensionVisuals.Frame frame,float age,float burst,float fade,double ticks,
                            double cameraX,double cameraY,double cameraZ,int segments,boolean reduced,Sink sink) {
        draw(frame,age,burst,fade,ticks,cameraX,cameraY,cameraZ,segments,reduced,true,sink);
    }

    public static void draw(AscensionVisuals.Frame frame,float age,float burst,float fade,double ticks,
                            double cameraX,double cameraY,double cameraZ,int segments,boolean reduced,boolean cinematic,Sink sink) {
        if(!Double.isFinite(cameraX)||!Double.isFinite(cameraY)||!Double.isFinite(cameraZ)||!Double.isFinite(ticks))
            throw new IllegalArgumentException("Nonfinite camera/time");
        if(!Float.isFinite(age)||!Float.isFinite(burst)||!Float.isFinite(fade)) throw new IllegalArgumentException("Nonfinite visual age");
        if(fade<=0) return;
        Writer w=new Writer(sink,cameraX,cameraY,cameraZ,Math.max(12,Math.min(48,segments))); w.cinematic=cinematic;
        double t=ticks*(reduced?.0075:.021);
        float energy=AscensionVisuals.unit(fade)*(.42f+frame.energy()*.52f);
        int detail=reduced||segments<24?0:1;
        if(burst>0 && frame.phase()==Phase.ABSORBING) scatter(w,t,1-burst,energy*burst,18);

        switch(frame.phase()) {
            case PREPARING -> {
                seal(w,1.9,t,energy*.46f,detail);
                orb(w,1.0,.64,t,energy*.12f,0);
                ring(w,.065,1.1,t,EARTH,energy*.30f,.025f,0);
                orbit(w,1,.6,t,HUMAN,energy*.26f,.022f,3);
                sparks(w,t,6,1.3,.4,HUMAN,energy*.36f);
            }
            case BREAKING -> {
                seal(w,2.25,t,energy*.50f,detail);
                for(int i=0;i<8+detail*4;i++) {
                    final double phase=i*TAU/(8+detail*4);
                    w.ribbon((u,p)->{
                        double a=phase+u*TAU*.23+t*.16;
                        double r=.62+frame.progress()*.16+Math.sin(u*28+phase)*frame.progress()*.045;
                        p.set(Math.cos(a)*r,.12+u*1.72,Math.sin(a)*r);
                    },.018f+frame.progress()*.016f,HEAVEN,energy*.74f);
                }
                orbit(w,1,.82,-t,EARTH,energy*.38f,.025f,4);
                sparks(w,t,10+detail*6,1.05,1.2,HEAVEN,energy*.7f);
            }
            case BROKEN, FAILED -> {
                scatter(w,t,Math.min(1,age/44),energy*(float)(1-smooth(age/80)),18+detail*14);
                seal(w,2.4,t,energy*(float)(1-smooth(age/36))*.4f,detail);
            }
            case ABSORBING -> {
                seal(w,3.8,t,energy*.44f,detail);
                // Weather descends from a broad high funnel; earth qi spirals upward from the ground.
                for(int qi=0;qi<3;qi++) {
                    final int kind=qi;
                    float weight=switch(qi){case 0->frame.heaven();case 1->frame.earth();default->frame.human();};
                    for(int strand=0;strand<3+detail*3;strand++) {
                        final double offset=strand*TAU/(3+detail*3)+qi*.8;
                        Curve path=(u,p)->{
                            double a=offset+t*(kind==1?-1:1)+u*TAU*(kind==2?1.35:.85);
                            double r=(kind==0?(cinematic?5.8:2.4):kind==1?(cinematic?3.75:1.5):(cinematic?1.50:1.05))*Math.pow(1-u,1.35)+.13;
                            r+=Math.sin(u*24+t*2+offset)*frame.instability()*.15;
                            double y=kind==0?(cinematic?12.8:4.4)*(1-u)+u:kind==1?.04+u:
                                    1+Math.sin(u*TAU+offset+t)*.75*(1-u);
                            p.set(Math.cos(a)*r,y,Math.sin(a)*r);
                        };
                        double reveal=smooth((age-(kind==2?0:kind==1?65:145))/(kind==2?80:100));
                        float alpha=weight<=.001f?0:energy*(.10f+weight*.72f)*(float)(.18+reveal*.82);
                        w.ribbon(path,qi==0?.115f:qi==1?.085f:.045f,QI[qi],alpha);
                        // A fine bright strand follows the wider, soft brush of each current.
                        if(detail>0) w.ribbon(path,.014f,QI[qi],alpha*.90f);
                        double cursor=fract(t*.15+strand*.173);
                        Point head=new Point();path.point(cursor,head);
                        w.spark(head.x,head.y,head.z,.035f,QI[qi],alpha*.95f);
                    }
                }
                for(int i=0;i<2+detail;i++) ring(w,.04+i*.035,1.1+i*.72,-t*(.5+i*.11),EARTH,energy*.3f,.042f,0);
                if(frame.heaven()>0 || frame.calamityMask()!=0) sky(w,t,energy*Math.max(.25f,frame.heaven())*(float)(.2+.8*smooth((age-145)/100)),detail,frame.calamityMask()!=0);
                if(frame.heaven()>0 && frame.earth()>0 && frame.human()>0) tripleWheel(w,t,energy*.50f,frame.instability());
                if((frame.calamityMask()&1)!=0) orbit(w,2.5,3.2,-t*1.3,EARTH,energy*.5f,.075f,4);
                if((frame.calamityMask()&2)!=0) orbit(w,4,2.9,t*.24,HEAVEN,energy*.45f,.034f,6);
                if((frame.calamityMask()&4)!=0) ring(w,.08,4.2,-t*.4,EARTH,energy*.5f,.12f,0);
                if((frame.calamityMask()&8)!=0) orbit(w,3,3,-t*.2,new Color(.58f,.19f,.21f),energy*.35f,.05f,3);
                if(frame.human()>0) for(int i=0;i<8+detail*4;i++) {
                    double a=i*2.399963+t*.4;
                    glyph(w,Math.cos(a)*.9,1+Math.sin(a*1.4)*.6,Math.sin(a)*.9,.08,a,HUMAN,energy*.5f,false,i);
                }
                orb(w,1.03,.34+frame.energy()*.12,t,energy*.70f,detail);
                sparks(w,t,8+detail*8,1.7,2.0,HEAVEN,energy*.52f);
                if(frame.insight()) {
                    orbit(w,1.15,1.2,-t*.45,JADE,energy*.57f,.018f,6);
                    rays(w,t,1.08,1.35,HEAVEN,energy*.55f,8+detail*4);
                }
            }
            case OPENING, FORMING -> {
                double growth=frame.phase()==Phase.FORMING?Math.min(1,age/58):0;
                double radius=.62+growth*(cinematic?2.45:.7), center=1.04+growth*1.1;
                seal(w,2.8+growth*2.3,t,energy*.45f,detail);
                orb(w,center,radius,t,energy*(.75f-(float)growth*.23f),detail);
                orbit(w,center,radius*1.12,-t*.4,HUMAN,energy*.5f,.035f,5+detail*3);
                for(int i=0;i<2+detail;i++)
                    ring(w,.045+i*.025,.9+growth*3.6+i*.45,-t,JADE,energy*.3f,.038f,0);
                sparks(w,t,12+detail*12,radius*1.05,center*1.2,JADE,energy*.66f);
                if(frame.phase()==Phase.FORMING) {
                    rays(w,t,center,radius*1.5,HEAVEN,energy*.34f,8);
                    if(age<26) {
                        double u=age/26.0;
                        w.spark(Math.cos(t)*1.2*(1-u),2.4-u*1.35,Math.sin(t)*1.2*(1-u),
                                .14f,HUMAN,energy);
                    }
                }
            }
            case IMMORTAL -> {
                seal(w,4.2,t,energy*(float)(1-smooth((age-110)/70))*.55f,detail);
                if(age<22) orb(w,3.7,.65*(1-.85*smooth(age/22)),t,energy,detail);
                double growth=smooth((age-16)/70);
                for(int i=0;i<3+detail;i++)
                    ring(w,.07+i*.08,.65+growth*(cinematic?4.4:1.2)+i*.36,-t,JADE,energy*(.36f-i*.045f),.05f,i%2);
                rays(w,t,1.6,2+growth*2.6,HEAVEN,energy*.5f,12+detail*6);
                sparks(w,t,16+detail*16,.55+growth*2.2,1.6+growth*2.1,JADE,energy*.9f);
                orbit(w,1.6,1.1+growth*.7,-t*.5,HUMAN,energy*.48f,.025f,6);
            }
        }
    }

    private static void orb(Writer w,double center,double radius,double t,float alpha,int detail) {
        for(int i=0;i<6+detail*6;i++) {
            final double phase=i*TAU/(6+detail*6)+t*.24;
            w.ribbon((u,p)->{
                double a=u*TAU+t*.3;
                p.set(Math.cos(a)*radius*Math.cos(phase),center+Math.sin(a)*radius*.82,
                        Math.cos(a)*radius*Math.sin(phase));
            },.025f+(float)radius*.008f,QI[i%3],alpha*.55f);
        }
        for(int i=0;i<3;i++) {
            final double lat=(i-1)*.46;
            ring(w,center+radius*lat,radius*Math.sqrt(1-lat*lat),t+i,QI[i],alpha*.75f,.035f,i%2);
        }
        sparks(w,t,6,radius*.9,center,JADE,alpha*.55f);
    }
    private static void orbit(Writer w,double center,double radius,double t,Color c,float alpha,float width,int bands) {
        for(int i=0;i<bands;i++) {
            final double phase=i*TAU/bands;
            w.ribbon((u,p)->{
                double a=u*TAU+t+phase;
                p.set(Math.cos(a)*radius,center+Math.sin(a*2+phase)*radius*.54,Math.sin(a)*radius);
            },width,c,alpha);
        }
    }
    private static void ring(Writer w,double y,double radius,double t,Color c,float alpha,float width,int tilt) {
        w.ribbon((u,p)->{
            double a=u*TAU+t;
            double r=radius*(1+Math.sin(a*3+t)*.018);
            p.set(Math.cos(a)*r,y+(tilt==0?0:Math.sin(a)*radius*.24),Math.sin(a)*r);
        },width,c,alpha);
    }
    private static void rays(Writer w,double t,double center,double radius,Color c,float alpha,int count) {
        if(!w.cinematic) return;
        for(int i=0;i<count;i++) {
            final double phase=i*TAU/count+t*.08;
            w.ribbon((u,p)->{
                double r=(.28+u*.72)*radius;
                p.set(Math.cos(phase)*r,center+Math.sin(phase*2+t)*u*.45+u*u*.7,Math.sin(phase)*r);
            },.008f,c,alpha*.65f);
        }
    }
    private static void sparks(Writer w,double t,int count,double radius,double height,Color c,float alpha) {
        for(int i=0;i<count;i++) {
            double a=i*2.399963+t*(i%2==0?.5:-.3);
            double lift=fract(i*.618+t*.055);
            double r=radius*(.55+.45*Math.sin(i*2.7+lift*2));
            w.spark(Math.cos(a)*r,.22+lift*height,Math.sin(a)*r,
                    (float)(.022+.035*(.5+.5*Math.sin(i+t))),c,alpha*(float)Math.sin(Math.PI*lift));
        }
    }
    private static void scatter(Writer w,double t,double spread,float alpha,int count) {
        for(int i=0;i<count;i++) {
            final double a=i*2.399963+t*.12;
            double r=.45+spread*(1.4+(i%4)*.4);
            double y=1+Math.sin(a*2)*(.25+spread*.8)+spread*.6;
            w.spark(Math.cos(a)*r,y,Math.sin(a)*r,.045f,QI[i%3],alpha*(float)(1-spread*.65));
            if(i%2==0) {
                w.ribbon((u,p)->p.set(Math.cos(a)*r*(.7+u*.3),y+u*.25,Math.sin(a)*r*(.7+u*.3)),
                        .013f,QI[i%3],alpha*.32f);
            }
        }
    }

    private static double smooth(double x) {
        x=Math.max(0,Math.min(1,x)); return x*x*(3-2*x);
    }
    private static void seal(Writer w,double radius,double t,float alpha,int detail) {
        if(!w.cinematic) return;
        int layers=detail>0?4:2;
        for(int i=0;i<layers;i++)
            ring(w,.035+i*.012,radius*(.50+i*.15),t*(i%2==0?1:-.6),
                    i%2==0?HUMAN:EARTH,alpha*(.9f-i*.1f),.012f,0);
        int marks=detail>0?24:12;
        for(int i=0;i<marks;i++) {
            double a=i*TAU/marks+t*.10;
            double r=radius*.87;
            glyph(w,Math.cos(a)*r,.07,Math.sin(a)*r,.10,a,HUMAN,alpha*.75f,true,i);
            if(i%2==0) {
                w.stroke(Math.cos(a)*radius*.52,.055,Math.sin(a)*radius*.52,
                        Math.cos(a)*radius*.78,.055,Math.sin(a)*radius*.78,.009f,HUMAN,alpha*.52f);
            }
        }
    }
    private static void glyph(Writer w,double x,double y,double z,double scale,double phase,Color c,float alpha,boolean ground,int seed) {
        // Abstract seal strokes, never text or gameplay symbols.
        for(int line=0;line<4;line++) {
            double ax=(line<2?-.7:.0)*scale, bx=(line<2?.7:.0)*scale;
            double ay=(line==0?-.5:line==1?.5:-.8)*scale;
            double by=(line<2?ay:.8*scale);
            if(line==3) {ax=-.55*scale;bx=.45*scale;ay=.7*scale;by=-.4*scale;}
            double xa=Math.cos(phase)*ax-Math.sin(phase)*ay;
            double za=Math.sin(phase)*ax+Math.cos(phase)*ay;
            double xb=Math.cos(phase)*bx-Math.sin(phase)*by;
            double zb=Math.sin(phase)*bx+Math.cos(phase)*by;
            w.stroke(x+xa,ground?y:y+za,ground?z+za:z,
                    x+xb,ground?y:y+zb,ground?z+zb:z,.007f,c,alpha);
        }
    }
    private static void sky(Writer w,double t,float amount,int detail,boolean storm) {
        if(!w.cinematic) return;
        int layers=detail>0?5:2;
        Color color=storm?new Color(.31f,.43f,.55f):HEAVEN;
        for(int i=0;i<layers;i++) {
            double y=12.2+i*.34, radius=5.1+i*.75;
            ring(w,y,radius,-t*(.12+i*.035),color,amount*(.15f+i*.012f),.24f+i*.035f,0);
            final double level=y, r=radius;
            final int k=i;
            w.ribbon((u,p)->{
                double a=u*TAU-t*.19+k*.6;
                p.set(Math.cos(a)*r,level+Math.sin(a*3+t*.15)*.20,Math.sin(a)*r);
            },.38f,HEAVEN,amount*.075f);
        }
        for(int i=0;i<3+detail*3;i++) {
            final double a=i*TAU/(3+detail*3)+t*.07;
            w.ribbon((u,p)->{
                double r=.65+u*.35;
                p.set(Math.cos(a)*r,1.3+u*11.0,Math.sin(a)*r);
            },.045f,HEAVEN,amount*.18f);
        }
    }
    private static void tripleWheel(Writer w,double t,float alpha,float instability) {
        if(!w.cinematic) return;
        for(int i=0;i<3;i++) {
            final double tilt=new double[]{.26,1.03,-.92}[i];
            final double speed=new double[]{.43,-.32,.27}[i];
            final double shift=i*TAU/3;
            w.ribbon((u,p)->{
                double a=u*TAU+t*speed+shift;
                double r=2.15+Math.sin(a*3+t)*instability*.33;
                p.set(Math.cos(a)*r,2.05+Math.sin(a)*r*Math.sin(tilt),Math.sin(a)*r*Math.cos(tilt));
            },.032f,QI[i],alpha);
            final double offset=i*TAU/3;
            w.ribbon((u,p)->{
                double a=offset+t*.28+u*TAU*.82;
                double r=.12+u*1.25;
                p.set(Math.cos(a)*r,3.75+Math.sin(a*2)*.11,Math.sin(a)*r);
            },.045f,QI[i],alpha*.7f);
        }
    }
    public static void impact(float age,float fade,double ticks,double cx,double cy,double cz,int segments,Sink sink) {
        if(age<0||age>24||fade<=0) return;
        Writer w=new Writer(sink,cx,cy,cz,Math.max(12,Math.min(48,segments)));
        float strength=AscensionVisuals.unit(fade*(1-age/24));
        ring(w,.12,.5+age*.22,ticks*.005,HEAVEN,strength*.75f,.075f,0);
        ring(w,1.0,.3+age*.13,-ticks*.005,EARTH,strength*.42f,.035f,1);
    }

    private static double fract(double x) { return x-Math.floor(x); }
    private interface Curve { void point(double u,Point p); }
    private static final class Point {
        float x,y,z;
        void set(double x,double y,double z) { this.x=(float)x;this.y=(float)y;this.z=(float)z; }
    }
    private static final class Writer {
        final Sink sink; final float cx,cy,cz; final int segments; boolean cinematic=true;
        final Point a=new Point(),b=new Point();
        Writer(Sink sink,double x,double y,double z,int segments) {
            this.sink=sink;cx=(float)x;cy=(float)y;cz=(float)z;this.segments=segments;
        }
        void ribbon(Curve curve,float width,Color color,float alpha) {
            ribbon(curve,width,color,alpha,segments);
        }
        void stroke(double x,double y,double z,double xx,double yy,double zz,float width,Color color,float alpha) {
            ribbon((u,p)->p.set(x+(xx-x)*u,y+(yy-y)*u,z+(zz-z)*u),width,color,alpha*3,1);
        }
        void ribbon(Curve curve,float width,Color color,float alpha,int count) {
            if(alpha<=.001f) return;
            for(int i=0;i<count;i++) {
                float u=i/(float)count,v=(i+1)/(float)count;
                curve.point(u,a);curve.point(v,b);
                float tx=b.x-a.x,ty=b.y-a.y,tz=b.z-a.z;
                float vx=cx-(a.x+b.x)*.5f,vy=cy-(a.y+b.y)*.5f,vz=cz-(a.z+b.z)*.5f;
                float sx=ty*vz-tz*vy,sy=tz*vx-tx*vz,sz=tx*vy-ty*vx;
                float length=(float)Math.sqrt(sx*sx+sy*sy+sz*sz);
                if(length<1e-7f) {sx=1;sy=0;sz=0;length=1;}
                sx=sx/length*width;sy=sy/length*width;sz=sz/length*width;
                float nx=sy*tz-sz*ty,ny=sz*tx-sx*tz,nz=sx*ty-sy*tx;
                float n=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                if(n<1e-7f) {nx=0;ny=0;nz=1;n=1;}
                nx/=n;ny/=n;nz/=n;
                float opacity=AscensionVisuals.unit(alpha*(.13+.87*Math.sin(Math.PI*(.025+u*.95))));
                put(a.x-sx,a.y-sy,a.z-sz,u,0,color,opacity,nx,ny,nz);
                put(a.x+sx,a.y+sy,a.z+sz,u,1,color,opacity,nx,ny,nz);
                put(b.x+sx,b.y+sy,b.z+sz,v,1,color,opacity,nx,ny,nz);
                put(b.x-sx,b.y-sy,b.z-sz,v,0,color,opacity,nx,ny,nz);
            }
        }
        void spark(double x,double y,double z,float size,Color c,float alpha) {
            if(alpha<=.001f) return;
            float nx=cx-(float)x,ny=cy-(float)y,nz=cz-(float)z;
            float n=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(n<1e-7f){nx=0;ny=0;nz=1;n=1;} nx/=n;ny/=n;nz/=n;
            float rx=nz,rz=-nx,rl=(float)Math.sqrt(rx*rx+rz*rz);
            if(rl<1e-7f){rx=1;rz=0;rl=1;} rx/=rl;rz/=rl;
            float ux=ny*rz,uy=nz*rx-nx*rz,uz=-ny*rx;
            for(int layer=0;layer<2;layer++) {
                float s=size*(layer==0?2.6f:1),opacity=AscensionVisuals.unit(alpha*(layer==0?.16f:.92f));
                put((float)x-ux*s*1.7f,(float)y-uy*s*1.7f,(float)z-uz*s*1.7f,0,.5f,c,opacity,nx,ny,nz);
                put((float)x+rx*s,(float)y,(float)z+rz*s,.5f,1,c,opacity,nx,ny,nz);
                put((float)x+ux*s*1.7f,(float)y+uy*s*1.7f,(float)z+uz*s*1.7f,1,.5f,c,opacity,nx,ny,nz);
                put((float)x-rx*s,(float)y,(float)z-rz*s,.5f,0,c,opacity,nx,ny,nz);
            }
        }
        void put(float x,float y,float z,float u,float v,Color c,float a,float nx,float ny,float nz) {
            sink.vertex(x,y,z,u,v,c.r,c.g,c.b,a,nx,ny,nz);
        }
    }
}
