package dev.realworldmc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** Separate distant depth range: orbit must not reduce precision for Steve or nearby blocks. */
public final class SpaceView {
    private static VertexBuffer shade, stars;
    private SpaceView() {}
    public static boolean active() { return SpaceFlight.anchored(); }
    public static double far(double altitude) {
        return far(altitude,6_378_137);
    }
    public static double far(double altitude,double radius) {
        double h = Math.min(20_000_000, Math.max(0, altitude));
        return Math.min(50_000_000, Math.sqrt(h * (2 * radius + h)) * 1.15 + 30_000);
    }
    public static Matrix4f projection(Matrix4f original, double altitude) {
        return projection(original,altitude,6_378_137);
    }
    public static Matrix4f projection(Matrix4f original, double altitude,double radius) {
        double near = Math.max(1, altitude / 100), far = far(altitude,radius);
        return new Matrix4f(original).m22((float)(-(far + near) / (far - near)))
            .m32((float)(-2 * far * near / (far - near)));
    }
    public static float atmosphere(double altitude) {
        float t = (float)Math.clamp((altitude - 12000) / 68000, 0, 1);
        return t * t * (3 - 2 * t);
    }
    public static void sky(Matrix4f view, Matrix4f projection, double altitude) {
        float fade = atmosphere(altitude);
        if (fade <= 0) return;
        if (shade == null) {
            var quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            quad.addVertex(-1,-1,0).setColor(2,4,9,255); quad.addVertex(1,-1,0).setColor(2,4,9,255);
            quad.addVertex(1,1,0).setColor(2,4,9,255); quad.addVertex(-1,1,0).setColor(2,4,9,255);
            shade = upload(quad);
            var points = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            var random = new java.util.Random(563);
            for (int i=0; i<1200; i++) {
                var direction = new org.joml.Vector3f(random.nextFloat()*2-1,random.nextFloat()*2-1,random.nextFloat()*2-1);
                if (direction.lengthSquared()<.01f) continue;
                direction.normalize();
                var right = new org.joml.Vector3f(direction).cross(0,1,0);
                if (right.lengthSquared()<.001f) continue;
                right.normalize().mul(.15f + random.nextFloat()*.15f);
                var up = new org.joml.Vector3f(right).cross(direction).normalize().mul(right.length());
                direction.mul(100);
                int brightness=120+random.nextInt(136);
                for (int[] corner : new int[][]{{-1,-1},{1,-1},{1,1},{-1,1}}) {
                    var p = new org.joml.Vector3f(direction).fma(corner[0],right).fma(corner[1],up);
                    points.addVertex(p.x,p.y,p.z).setColor(brightness,brightness,brightness,255);
                }
            }
            stars = upload(points);
        }
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.disableCull(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1,1,1,fade);
        shade.bind(); shade.drawWithShader(new Matrix4f(),new Matrix4f(),RenderSystem.getShader());
        var starProjection = new Matrix4f(projection).m22(-201f/199f).m32(-400f/199f);
        // Stars are directions, never positions: camera translation must not move or clip them.
        RenderSystem.setShaderColor(1,1,1,fade*fade);
        stars.bind(); stars.drawWithShader(new Matrix4f(view).setTranslation(0,0,0),starProjection,RenderSystem.getShader());
        VertexBuffer.unbind(); RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.disableBlend(); RenderSystem.enableCull(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(true);
    }
    private static VertexBuffer upload(BufferBuilder builder) {
        var buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind(); buffer.upload(builder.buildOrThrow()); VertexBuffer.unbind(); return buffer;
    }
}
