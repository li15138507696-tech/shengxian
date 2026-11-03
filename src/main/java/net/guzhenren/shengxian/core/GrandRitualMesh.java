package net.guzhenren.shengxian.core;

/** Solid world-space ritual geometry. Every four sink vertices form one textured face. */
public final class GrandRitualMesh {
    private static final double TAU = Math.PI * 2;
    // Stone, crown, warm mineral, heaven, earth, and human body colors.
    private static final float[][][] COLORS = {
        {{.16f,.31f,.25f},{.33f,.64f,.58f},{.72f,.55f,.28f},{.69f,.83f,.96f},{.23f,.63f,.40f},{.90f,.55f,.20f}},
        {{.16f,.025f,.035f},{.46f,.07f,.09f},{.66f,.17f,.12f},{.65f,.25f,.27f},{.32f,.045f,.07f},{.70f,.16f,.095f}},
        {{.19f,.12f,.29f},{.43f,.29f,.64f},{.66f,.49f,.80f},{.70f,.65f,.91f},{.34f,.21f,.53f},{.62f,.32f,.70f}}
    };
    private GrandRitualMesh() {}

    /** Nonfinite strength is treated as zero; nonfinite time uses the static pose. */
    public static void draw(double ticks, float strength, boolean low, int palette, AscensionAuraMesh.Sink sink) {
        float energy = Float.isFinite(strength) ? Math.max(0, Math.min(1, strength)) : 0;
        if (energy == 0) return;
        if (sink == null) throw new NullPointerException("sink");
        // Reduce before multiplication so even finite extreme timestamps remain finite.
        double phase = Double.isFinite(ticks) ? (ticks % 24000) * .003 : 0;
        Faces faces = new Faces(sink, energy, COLORS[Math.floorMod(palette, COLORS.length)]);
        crown(faces, low ? 48 : 96, low ? 8 : 16, phase);
        for (int arm = 0; arm < 3; arm++) tube(faces, low ? 24 : 64, low ? 8 : 12, arm, phase);
        ridge(faces, low ? 48 : 96);
    }

    private static void crown(Faces f, int around, int section, double phase) {
        for (int i=0;i<around;i++) {
            double a=TAU*i/around, b=TAU*(i+1)/around;
            for (int j=0;j<section;j++) {
                double u=TAU*j/section, v=TAU*(j+1)/section;
                // Profile direction first gives an outward face winding.
                f.quad(torus(a,u,phase),torus(a,v,phase),torus(b,v,phase),torus(b,u,phase),
                    1, .84+.10*Math.sin(a*3+.7)+.06*Math.cos(u*2-a));
            }
        }
    }
    private static double[] torus(double a, double b, double phase) {
        // Broad asymmetrical swells turn a toroidal mass into a weathered storm crown.
        double drift=phase*.12;
        double center=22+1.5*Math.sin(a*3+.4)+.75*Math.cos(a*5-1.1);
        double height=40+1.9*Math.sin(a*2+.6)+.8*Math.cos(a*3+drift);
        double thickness=2.5+.52*Math.sin(a*3-.8)+.25*Math.cos(a*5+.5);
        double r=center+thickness*Math.cos(b);
        return new double[]{r*Math.cos(a),height+thickness*1.12*Math.sin(b),r*Math.sin(a)};
    }

    private static void tube(Faces f, int length, int section, int arm, double phase) {
        double[][] previous=tubeRing(0,section,arm,phase);
        // Convex even-sided end polygons partition into genuine four-corner caps.
        for(int j=1;j<section-2;j+=2)
            f.quad(previous[0],previous[j],previous[j+1],previous[j+2],3+arm,.7);
        for(int i=0;i<length;i++) {
            double[][] next=tubeRing((double)(i+1)/length,section,arm,phase);
            for(int j=0;j<section;j++) {
                int k=(j+1)%section;
                f.quad(previous[j],next[j],next[k],previous[k],
                    3+arm, .86+.09*Math.sin((double)i/length*7+arm));
            }
            previous=next;
        }
        for(int j=1;j<section-2;j+=2)
            f.quad(previous[0],previous[j+2],previous[j+1],previous[j],3+arm,.9);
    }
    private static double[][] tubeRing(double t, int section, int arm, double phase) {
        double swell=Math.sin(Math.PI*t);
        double a=arm*TAU/3+phase+TAU*(.50+.13*arm)*t
            +.22*swell*Math.sin(t*6+arm*1.8);
        double radius=4+10*t+.65*swell*Math.sin(t*5+arm*2);
        double thickness=.8+1.2*t+.18*swell*Math.sin(t*9+arm);
        double x=radius*Math.cos(a), z=radius*Math.sin(a);
        double[][] ring=new double[section][];
        // Horizontal sections keep the base and top exactly at y=1 and y=40.
        // Their swept, expanding helix is a solid oblique tube, not a ribbon.
        for(int j=0;j<section;j++) {
            double b=TAU*j/section;
            ring[j]=new double[]{x+thickness*Math.cos(b),1+39*t,z+thickness*Math.sin(b)};
        }
        return ring;
    }

    private static void ridge(Faces f, int segments) {
        double[] radii={11,13,16,19,21};
        for(int band=0;band<radii.length-1;band++) {
            for(int i=0;i<segments;i++) {
                int next=(i+1)%segments;
                f.quad(rock(radii,band,i,segments),rock(radii,band,next,segments),
                    rock(radii,band+1,next,segments),rock(radii,band+1,i,segments),
                    0, .78+.16*Math.sin(TAU*i/segments*3+band*.6));
            }
        }
        for(int i=0;i<segments;i++) {
            int next=(i+1)%segments;
            f.quad(rock(radii,0,i,segments),rock(radii,4,i,segments),
                rock(radii,4,next,segments),rock(radii,0,next,segments),0,.55);
        }
    }
    private static double[] rock(double[] radii, int band, int i, int segments) {
        double a=TAU*i/segments;
        // Shared angular waves continue across detail levels and neighboring layers.
        double n=.5+.22*Math.sin(3*a+.6)+.17*Math.cos(5*a-.8)+.11*Math.sin(7*a+1.1);
        double shoulder=Math.sin(2*a+.4)+.35*Math.cos(4*a-1.2);
        double r=radii[band]+(band==0 || band==4 ? .55*shoulder : .8*shoulder);
        double y;
        switch(band) {
            case 0: case 4: y=-1.4; break;
            case 1: y=1.2+n*2.4; break;
            case 2: y=5.5+n*6; break;
            default: y=1.8+n*2.6;
        }
        return new double[]{r*Math.cos(a),y,r*Math.sin(a)};
    }
    private static final class Faces {
        private final AscensionAuraMesh.Sink sink;
        private final float alpha;
        private final float[][] colors;
        Faces(AscensionAuraMesh.Sink sink,float alpha,float[][] colors) {
            this.sink=sink;this.alpha=alpha;this.colors=colors;
        }
        void quad(double[] a,double[] b,double[] c,double[] d,int material,double detail) {
            // Area normal of both triangles: exact geometric flat-surface shading.
            double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
            double vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            double wx=d[0]-a[0],wy=d[1]-a[1],wz=d[2]-a[2];
            double nx=uy*vz-uz*vy+vy*wz-vz*wy;
            double ny=uz*vx-ux*vz+vz*wx-vx*wz;
            double nz=ux*vy-uy*vx+vx*wy-vy*wx;
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            nx/=length;ny/=length;nz/=length;
            double light=(.46+.54*Math.max(0,nx*.36+ny*.82+nz*.44))*detail;
            float[] color=colors[material];
            float r=(float)(color[0]*light),g=(float)(color[1]*light),blue=(float)(color[2]*light);
            vertex(a,0,0,r,g,blue,nx,ny,nz);vertex(b,0,1,r,g,blue,nx,ny,nz);
            vertex(c,1,1,r,g,blue,nx,ny,nz);vertex(d,1,0,r,g,blue,nx,ny,nz);
        }
        private void vertex(double[] p,float u,float v,float r,float g,float b,double nx,double ny,double nz) {
            sink.vertex((float)p[0],(float)p[1],(float)p[2],u,v,r,g,b,alpha,(float)nx,(float)ny,(float)nz);
        }
    }
}
