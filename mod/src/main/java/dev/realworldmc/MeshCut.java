package dev.realworldmc;

import java.util.ArrayList;
import java.util.List;

/** Subtracts edited block volumes from textured triangles without deleting their neighbours. */
public final class MeshCut {
    private MeshCut() { }
    public record Vertex(double x, double y, double z, double u, double v) {
        double axis(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
        Vertex interpolate(Vertex b, double t) {
            return new Vertex(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,u+(b.u-u)*t,v+(b.v-v)*t);
        }
    }
    public record Box(double x, double y, double z, double maxX, double maxY, double maxZ) {
        double min(int axis) { return axis==0?x:axis==1?y:z; }
        double max(int axis) { return axis==0?maxX:axis==1?maxY:maxZ; }
        public boolean intersects(Box other) {
            return maxX>=other.x && x<=other.maxX && maxY>=other.y && y<=other.maxY && maxZ>=other.z && z<=other.maxZ;
        }
    }
    /** Sparse damage lookup avoids testing every explosion cell against every mesh triangle. */
    public static final class Index {
        private final Box bounds;
        private final Index left,right;
        private final List<Box> leaves;
        public Index(List<Box> boxes) {
            if(boxes.isEmpty()){bounds=null;left=right=null;leaves=List.of();return;}
            double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
            double[] max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(Box b:boxes)for(int i=0;i<3;i++){min[i]=Math.min(min[i],b.min(i));max[i]=Math.max(max[i],b.max(i));}
            bounds=new Box(min[0],min[1],min[2],max[0],max[1],max[2]);
            if(boxes.size()<=8){leaves=List.copyOf(boxes);left=right=null;return;}
            int axis=0;for(int i=1;i<3;i++)if(max[i]-min[i]>max[axis]-min[axis])axis=i;
            final int split=axis;
            var sorted=new ArrayList<>(boxes);sorted.sort(java.util.Comparator.comparingDouble(b->b.min(split)+b.max(split)));
            int middle=sorted.size()/2;left=new Index(sorted.subList(0,middle));right=new Index(sorted.subList(middle,sorted.size()));leaves=null;
        }
        public List<Box> query(Box area) {
            if(bounds==null || !bounds.intersects(area))return List.of();
            var result=new ArrayList<Box>();query(area,result);return result;
        }
        private void query(Box area,List<Box> result) {
            if(bounds==null || !bounds.intersects(area))return;
            if(leaves!=null){for(Box b:leaves)if(b.intersects(area))result.add(b);}
            else {left.query(area,result);right.query(area,result);}
        }
    }
    /** Convex output polygons preserve winding and linearly interpolated texture coordinates. */
    public static List<List<Vertex>> subtract(List<Vertex> polygon, Box box) {
        for(int axis=0;axis<3;axis++) {
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(Vertex v:polygon){min=Math.min(min,v.axis(axis));max=Math.max(max,v.axis(axis));}
            if(max<box.min(axis) || min>box.max(axis))return List.of(polygon);
        }
        List<List<Vertex>> outside=new ArrayList<>();
        List<Vertex> inside=polygon;
        for(int axis=0;axis<3 && inside.size()>=3;axis++) {
            for(int side=0;side<2 && inside.size()>=3;side++) {
                double plane=side==0?box.min(axis):box.max(axis);
                boolean keepGreater=side==0;
                List<Vertex> fragment=clip(inside,axis,plane,!keepGreater,false);
                if(area(fragment)>1e-12)outside.add(fragment);
                inside=clip(inside,axis,plane,keepGreater,true);
            }
        }
        return outside;
    }
    private static List<Vertex> clip(List<Vertex> polygon,int axis,double plane,boolean greater,boolean boundary) {
        List<Vertex> result=new ArrayList<>();
        if(polygon.isEmpty())return result;
        Vertex previous=polygon.getLast();
        double old=(previous.axis(axis)-plane)*(greater?1:-1);
        boolean was=boundary?old>=0:old>0;
        for(Vertex current:polygon) {
            double distance=(current.axis(axis)-plane)*(greater?1:-1);
            boolean now=boundary?distance>=0:distance>0;
            if(now!=was)result.add(previous.interpolate(current,old/(old-distance)));
            if(now)result.add(current);
            previous=current;old=distance;was=now;
        }
        return result;
    }
    public static double area(List<Vertex> polygon) {
        if(polygon.size()<3)return 0;
        Vertex a=polygon.getFirst();double area=0;
        for(int i=1;i<polygon.size()-1;i++) {
            Vertex b=polygon.get(i),c=polygon.get(i+1);
            double x=b.x-a.x,y=b.y-a.y,z=b.z-a.z,xx=c.x-a.x,yy=c.y-a.y,zz=c.z-a.z;
            area+=Math.sqrt(Math.pow(y*zz-z*yy,2)+Math.pow(z*xx-x*zz,2)+Math.pow(x*yy-y*xx,2))*.5;
        }
        return area;
    }
}
