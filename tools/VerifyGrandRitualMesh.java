import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.guzhenren.shengxian.core.GrandRitualMesh;

/** Standalone CPU contract checks; run with the core sources on the classpath. */
public final class VerifyGrandRitualMesh {
    private static List<float[]> capture(double time, float strength, boolean low, int palette) {
        List<float[]> vertices = new ArrayList<>();
        GrandRitualMesh.draw(time, strength, low, palette,
            (x,y,z,u,v,r,g,b,a,nx,ny,nz) -> vertices.add(new float[]{x,y,z,u,v,r,g,b,a,nx,ny,nz}));
        return vertices;
    }
    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
    private static double[] cross(float[] a, float[] b, float[] c) {
        double x=b[0]-a[0], y=b[1]-a[1], z=b[2]-a[2];
        double X=c[0]-a[0], Y=c[1]-a[1], Z=c[2]-a[2];
        return new double[]{y*Z-z*Y,z*X-x*Z,x*Y-y*X};
    }
    private static void validate(List<float[]> mesh) {
        require(!mesh.isEmpty() && mesh.size()%4==0, "Nonempty grouped QUADS");
        boolean crown=false, ground=false, column=false;
        for (float[] v:mesh) {
            for(float value:v) require(Float.isFinite(value), "Finite vertex data");
            require(Math.abs(v[0])<=32 && Math.abs(v[2])<=32 && v[1]>=-2 && v[1]<=48, "Scene bounds");
            require(v[3]>=0 && v[3]<=1 && v[4]>=0 && v[4]<=1, "UV bounds");
            for(int i=5;i<=8;i++) require(v[i]>=0 && v[i]<=1,"Color/alpha bounds");
            double norm=v[9]*v[9]+v[10]*v[10]+v[11]*v[11];
            require(Math.abs(norm-1)<2e-5,"Unit normal");
            if(v[1]<1) require(Math.hypot(v[0],v[2])>=5,"Clear ground center");
            crown |= v[1]>41 && Math.hypot(v[0],v[2])>20;
            ground |= v[1]>5 && v[1]<13 && Math.hypot(v[0],v[2])>12;
            column |= v[1]>18 && v[1]<22;
        }
        require(crown && ground && column,"Crown, ridge and rising tubes present");
        for(int i=0;i<mesh.size();i+=4) {
            float[] a=mesh.get(i),b=mesh.get(i+1),c=mesh.get(i+2),d=mesh.get(i+3);
            double[] n=cross(a,b,c), m=cross(a,c,d);
            double nn=0,mm=0;
            for(int k=0;k<3;k++){nn+=n[k]*n[k];mm+=m[k]*m[k];}
            require(nn>1e-12 && mm>1e-12,"Both quad triangles have positive area");
            require(n[0]*m[0]+n[1]*m[1]+n[2]*m[2]>0,"Consistent quad winding");
            require(n[0]*a[9]+n[1]*a[10]+n[2]*a[11]>0,"Normal follows face winding");
            for(int j=1;j<4;j++) for(int k=9;k<12;k++)
                require(mesh.get(i+j)[k]==a[k],"Flat mathematical face normals");
        }
    }
    private static void same(List<float[]> a,List<float[]> b) {
        require(a.size()==b.size(),"Deterministic count");
        for(int i=0;i<a.size();i++) require(Arrays.equals(a.get(i),b.get(i)),"Deterministic vertex data");
    }
    private static void organicShape() {
        List<float[]> mesh=capture(0,1,false,0);
        double lo=100,hi=-100,inner=100,outer=0;
        boolean heaven=false,earth=false,human=false;
        double[] peaks=new double[96];
        for(float[] v:mesh) {
            double radius=Math.hypot(v[0],v[2]);
            if(v[1]>32 && radius>17) {
                lo=Math.min(lo,v[1]);hi=Math.max(hi,v[1]);
                inner=Math.min(inner,radius);outer=Math.max(outer,radius);
            }
            if(v[1]>15 && v[1]<25) {
                heaven |= v[7]>v[5]*1.2 && v[5]>v[6]*.7;
                earth |= v[6]>v[5]*2 && v[6]>v[7];
                human |= v[5]>v[7]*2 && v[5]>v[6];
            }
            if(v[1]>4 && v[1]<14 && radius>14 && radius<18) {
                int slot=(int)Math.round((Math.atan2(v[2],v[0])+Math.PI*2)%(Math.PI*2)*96/(Math.PI*2))%96;
                peaks[slot]=Math.max(peaks[slot],v[1]);
            }
        }
        require(hi-lo>8 && outer-inner>8,"Broad organic crown profile variation");
        require(heaven && earth && human,"Distinct heaven, earth and human tube colors");
        for(int i=0;i<96;i++) {
            require(peaks[i]>4,"Continuous ridge crest");
            require(Math.abs(peaks[i]-peaks[(i+1)%96])<2,"Broad ridge peaks without tiny sawteeth");
        }
    }
    public static void main(String[] args) {
        organicShape();
        int high=0,low=0;
        for(int palette=0;palette<3;palette++) {
            List<float[]> h=capture(123.25,1,false,palette),l=capture(123.25,1,true,palette);
            validate(h);validate(l);same(h,capture(123.25,1,false,palette));
            require(l.size()<h.size(),"Low detail reduces geometry");high=h.size();low=l.size();
        }
        for(float zero:new float[]{0,-1,Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY})
            require(capture(0,zero,false,0).isEmpty(),"Invalid/zero strength emits nothing");
        same(capture(0,2,false,0),capture(0,1,false,0));
        validate(capture(Double.NaN,.5f,false,1));
        validate(capture(Double.POSITIVE_INFINITY,.5f,true,2));
        validate(capture(Double.MAX_VALUE,Float.MIN_VALUE,true,0));
        validate(capture(-100000,.25f,false,2));
        for(int sample=0;sample<24;sample++) {
            validate(capture(sample*1000,1,false,sample%3));
            validate(capture(sample*1000,1,true,sample%3));
        }
        require(!Arrays.equals(capture(0,1,true,0).get(0),capture(0,1,true,1).get(0)),"Distinct palettes");
        System.out.println("GrandRitualMesh PASS: high="+high+" low="+low+" vertices; organic crown/ridge, distinct tube colors, 24 animation poses, finite, unit normals, bounds, UV, nondegenerate quads, center clearance, palettes, clamps and determinism");
    }
}
