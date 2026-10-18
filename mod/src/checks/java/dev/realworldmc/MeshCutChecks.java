package dev.realworldmc;
import java.util.List;
public final class MeshCutChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        var wall=List.of(new MeshCut.Vertex(0,0,0,0,0),new MeshCut.Vertex(8,0,0,1,0),new MeshCut.Vertex(0,8,0,0,1));
        var hole=new MeshCut.Box(1,1,-.5,2,2,.5);
        var cut=MeshCut.subtract(wall,hole);
        double area=cut.stream().mapToDouble(MeshCut::area).sum();
        check(Math.abs(area-31)<1e-9,"A one-block hole must remove one square metre, not the whole 32-square-metre triangle");
        for(var polygon:cut)for(var v:polygon){
            check(Math.abs(v.u()-v.x()/8)<1e-9 && Math.abs(v.v()-v.y()/8)<1e-9,"Texture interpolation must remain seamless");
            check(!(v.x()>1 && v.x()<2 && v.y()>1 && v.y()<2),"No retained vertex inside the hole");
        }
        check(MeshCut.subtract(wall,new MeshCut.Box(20,20,20,21,21,21)).getFirst()==wall,"Nonintersecting triangles must be reused");
        check(MeshCut.subtract(wall,new MeshCut.Box(-1,-1,-1,9,9,1)).isEmpty(),"Fully destroyed surface must disappear");
        double again=cut.stream().flatMap(p->MeshCut.subtract(p,hole).stream()).mapToDouble(MeshCut::area).sum();
        check(Math.abs(again-area)<1e-9,"Repeated cuts must not expand damage");
        var shifted=wall.stream().map(v->new MeshCut.Vertex(v.x()+50000,v.y()+64,v.z()-50000,v.u(),v.v())).toList();
        check(Math.abs(MeshCut.subtract(shifted,new MeshCut.Box(50001,65,-50000.5,50002,66,-49999.5)).stream().mapToDouble(MeshCut::area).sum()-31)<1e-8,"Geographic offsets must preserve hole area");
        var boxes=new java.util.ArrayList<MeshCut.Box>();
        for(int x=0;x<20;x++)for(int y=0;y<10;y++)for(int z=0;z<5;z++)boxes.add(new MeshCut.Box(x*2,y*2,z*2,x*2+1,y*2+1,z*2+1));
        var index=new MeshCut.Index(boxes);
        for(int x=-1;x<42;x++) {
            var query=new MeshCut.Box(x,3,1,x+2,9,8);
            var expected=boxes.stream().filter(b->b.intersects(query)).collect(java.util.stream.Collectors.toSet());
            check(new java.util.HashSet<>(index.query(query)).equals(expected),"Damage index must retain every intersecting cell");
        }
        System.out.println("Mesh cuts: bounded holes, unchanged neighbours, UV continuity, repeated cuts and geographic offsets passed.");
    }
}
