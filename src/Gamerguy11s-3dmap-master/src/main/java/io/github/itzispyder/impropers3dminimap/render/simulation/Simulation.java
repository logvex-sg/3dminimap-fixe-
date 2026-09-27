package io.github.itzispyder.impropers3dminimap.render.simulation;

import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.itzispyder.impropers3dminimap.Impropers3DMinimap;
import io.github.itzispyder.impropers3dminimap.util.math.Color;
import io.github.itzispyder.impropers3dminimap.util.math.MathUtils;
import io.github.itzispyder.impropers3dminimap.util.minecraft.PlayerUtils;
import io.github.itzispyder.impropers3dminimap.util.minecraft.RenderConstants;
import io.github.itzispyder.impropers3dminimap.util.minecraft.RenderUtils;
import io.github.itzispyder.impropers3dminimap.util.misc.Dictionary;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Simulation {

    private final ClientPlayerEntity player;
    private final SimulationRenderer renderer;
    private final BlockView world;
    private Vec3d focalPoint;
    private int x, y, width, height;
    private float mapScale;
    private SimulationMethod method;
    public double zoomDelta;

    public Simulation(ClientPlayerEntity player, int x, int y, int w, int h, float mapScale, SimulationMethod method, long focalLength) {
        this.focalPoint = new Vec3d(x + w / 2.0, y + h / 2.0, focalLength);
        this.player = player;
        this.world = player.getEntityWorld();
        this.renderer = new SimulationRenderer(this);
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
        this.mapScale = mapScale;
        this.method = method;
    }


    public void render(DrawContext context, Vec3d camera, Quaternionf rotation, boolean renderBackground, int borderRadius, int accentColor) {
        int r = borderRadius;
        zoomDelta = Impropers3DMinimap.radar.zoomAnimator.getAnimation();

        int x = this.x;
        int y = this.y;
        int width = this.width;
        int height = this.height;
        int winW = RenderUtils.width();
        int winH = RenderUtils.height();

        if (zoomDelta > 0) {
            int destW = 420;
            int destH = 240;
            int destX = (winW - destW) / 2;
            int destY = (winH - destH) / 2;

            x = (int) MathUtils.lerpClamped(x, destX, zoomDelta);
            y = (int) MathUtils.lerpClamped(y, destY, zoomDelta);
            width = (int) MathUtils.lerpClamped(width, destW, zoomDelta);
            height = (int) MathUtils.lerpClamped(height, destH, zoomDelta);
        }

        if (renderBackground) {
            RenderUtils.fillRoundRect(context, x, y, width, height, r, accentColor);
            RenderUtils.fillRect(context, x + r, y + r, width - r * 2, height - r * 2, Color.BLACK.getHex());

            Color color1 = Color.AQUA;
            Color color2 = Color.MAGENTA;
            int c11 = color1.getHex();
            int c12 = color2.getHex();
            int c01 = color1.getHexCustomAlpha(0.0);
            int c02 = color2.getHexCustomAlpha(0.0);
            RenderUtils.fillRoundShadowGradient(context, x, y, width, height, r, 3, c11, c02, c12, c01, c11, c02, c12, c01);
        }

        if (renderer.worldSize() > 0) {
            Vec3d focal = this.focalPoint;
            if (zoomDelta > 0)
                focal = new Vec3d(MathUtils.lerpClamped(focal.x, winW / 2F, zoomDelta), MathUtils.lerpClamped(focal.y, winH / 2F, zoomDelta), focal.z);

            // context.enableScissor() only records a clip rect for DrawContext's own deferred
            // GUI draw queue (fill/drawText/etc). Every draw call in this mod - including this
            // one - goes straight to a RenderLayer via layer.draw(buf.end()) (see
            // RenderUtils.drawBuffer), which is an immediate GPU draw that never consults
            // DrawContext's scissor stack at all. So the block/entity geometry was never being
            // clipped to the HUD box - it was drawing at full, unclipped size across the whole
            // screen. The real mechanism for clipping raw RenderLayer draws is
            // RenderSystem.enableScissorForRenderTypeDraws, which needs framebuffer-pixel
            // coordinates (GUI-scaled coords * scale factor, Y flipped from the bottom) rather
            // than the logical GUI coordinates everything else in this class uses - confirmed
            // against GuiRenderer.enableScissor(ScreenRect, RenderPass) in the mapped source.
            var window = net.minecraft.client.MinecraftClient.getInstance().getWindow();
            int scaleFactor = window.getScaleFactor();
            int fbHeight = window.getFramebufferHeight();
            int scissorX = (x + r) * scaleFactor;
            int scissorW = Math.max(0, (width - r * 2) * scaleFactor);
            int scissorH = Math.max(0, (height - r * 2) * scaleFactor);
            int scissorY = fbHeight - (y + r + (height - r * 2)) * scaleFactor;

            // Scissor is global GL state, not scoped to this draw call - if renderer.render()
            // throws anything (it walks live block/collision data, so it can), the disable call
            // below never runs and the scissor rect stays clamped to this tiny box for every
            // draw call on every following frame - including whole other screens like the HUD
            // editor - which is exactly what looked like a "black screen". try/finally guarantees
            // the scissor always gets released even if rendering this frame's geometry fails.
            com.mojang.blaze3d.systems.RenderSystem.enableScissorForRenderTypeDraws(scissorX, scissorY, scissorW, scissorH);
            try {
                renderer.render(context, camera, focal, rotation, mapScale, zoomDelta);
            } finally {
                com.mojang.blaze3d.systems.RenderSystem.disableScissorForRenderTypeDraws();
            }
        }

        renderMapViewer(context, x + width / 2, y + height / 2 + 2, 12);
    }

    public void renderMapViewer(DrawContext context, int x, int y, int size) {
        BufferBuilder buf = RenderUtils.getBuffer(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        Matrix4f mat = RenderUtils.toMatrix4f(context.getMatrices());

        buf.vertex(mat, x, y + 3, 0).color(0xFFFF0000); // center
        buf.vertex(mat, x - size / 2F, y + size / 2F, 0).color(0xFFFF0000); // bottom left
        buf.vertex(mat, x, y - size / 2F, 0).color(0xFFFF0000); // top center
        buf.vertex(mat, x, y + 3, 0).color(0xFF800000); // center
        buf.vertex(mat, x + size / 2F, y + size / 2F, 0).color(0xFF800000); // bottom right

        RenderUtils.drawBuffer(buf, RenderConstants.TRI_STRIP);
    }

    public void update(int radius, boolean useMapColors, Dictionary<Block> targets) {
        renderer.clear();
        Box box = player.getBoundingBox().expand(radius);

        for (double x = box.minX; x <= box.maxX; x++) {
            for (double y = box.minY; y <= box.maxY; y++) {
                for (double z = box.minZ; z <= box.maxZ; z++) {
                    BlockPos pos = new BlockPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
                    update(pos, world.getBlockState(pos), useMapColors, targets);
                }
            }
        }
    }

    public void updateEntities(int radius, Dictionary<EntityType<?>> targets) {
        // no-arg getEntities() only exists on ClientWorld, not the generic World that
        // getEntityWorld() returns; a ClientPlayerEntity's world is always a ClientWorld.
        for (Entity ent : ((net.minecraft.client.world.ClientWorld) player.getEntityWorld()).getEntities())
            if (ent != null && ent.isAlive() && !ent.isSpectator() && ent.distanceTo(player) <= radius)
                if (ent != PlayerUtils.player() && targets.lookup(ent.getType()))
                    update(ent);
    }

    public void update(BlockPos pos, BlockState state, boolean useMapColors, Dictionary<Block> targets) {
        renderer.update(world, pos, state, useMapColors, targets.lookup(state.getBlock()));
    }

    public void update(Entity ent) {
        renderer.update(ent);
    }

    public Vec2f projectVector(Vec3d vec) {
        return projectVector(vec.x, vec.y, vec.z);
    }

    public Vec2f projectVector(Vec3d vec, Quaternionf rotation, Vec3d origin) {
        Vector3f transform = vec.subtract(origin).toVector3f();
        transform = rotation.transform(transform).add(origin.toVector3f());
        return projectVector(transform.x, transform.y, transform.z);
    }

    public Vec2f projectVector(Vec3d vec, Quaternionf rotation) {
        Vector3f transform = rotation.transform(vec.toVector3f());
        return projectVector(transform.x, transform.y, transform.z);
    }

    public Vec2f projectVector(double x, double y, double z) {
        double focal = -MathUtils.lerpClamped(focalPoint.z, 1, zoomDelta);
        double depth = MathUtils.lerpClamped(focal + z, (focal + z) * 0.055, zoomDelta);

        // This is a radar covering all directions around the player (not just what the game
        // camera's view frustum can see), so it constantly has points sitting at or behind the
        // focal plane (depth >= 0) - that's a normal, frequent case here, not a rare edge case.
        // Previously depth was force-set to -1e-35 whenever this happened, which doesn't avoid
        // the division blow-up, it *guarantees* it: focal * x / depth then evaluates to an
        // enormous (or effectively infinite) pixel coordinate for every single one of those
        // points. That produced exactly the giant, screen-covering quads seen in-game - not
        // occasionally, but for every point on the wrong side of the focal plane, which given a
        // 360-degree radius is most of them. There is no sane finite screen position for a point
        // at/behind the focal plane in this projection, so signal "unprojectable" with NaN and
        // let the caller skip drawing it instead of drawing a degenerate near-infinite quad.
        if (depth >= 0)
            return new Vec2f(Float.NaN, Float.NaN);

        float px = (float)(focal * x / depth);
        float py = (float)(focal * y / depth);
        //System.out.printf("PROJECT [%s, %s, %s] -> [%s, %s]%n".formatted((int)x, (int)y, (int)z, (int)px, (int)py));
        return new Vec2f(px, py);
    }

    public Vec3d getFocalPoint() {
        return focalPoint;
    }

    public void setFocalLength(double length) {
        focalPoint = new Vec3d(focalPoint.x, focalPoint.y, length);
    }

    public SimulationRenderer getRenderer() {
        return renderer;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
        this.focalPoint = new Vec3d(x + width / 2.0, y + height / 2.0, focalPoint.z);
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
        this.focalPoint = new Vec3d(x + width / 2.0, y + height / 2.0, focalPoint.z);
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
        this.focalPoint = new Vec3d(x + width / 2.0, y + height / 2.0, focalPoint.z);
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
        this.focalPoint = new Vec3d(x + width / 2.0, y + height / 2.0, focalPoint.z);
    }

    public void setDimensions(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
        this.focalPoint = new Vec3d(x + width / 2.0, y + height / 2.0, focalPoint.z);
    }

    public SimulationMethod getMethod() {
        return method;
    }

    public void setMethod(SimulationMethod method) {
        this.method = method;
    }

    public float getMapScale() {
        return mapScale;
    }

    public void setMapScale(float mapScale) {
        this.mapScale = mapScale;
    }

    public boolean outOfBounds(Vec2f v1, Vec2f v2) {
        return outOfBounds(v1.x, v1.y, v2.x, v2.y);
    }

    public boolean outOfBounds(Vec2f v1, Vec2f v2, Vec2f v3, Vec2f v4) {
        return outOfBounds(v1) && outOfBounds(v2) && outOfBounds(v3) && outOfBounds(v4);
    }

    public boolean outOfBounds(float x1, float y1, float x2, float y2) {
        return outOfBounds(x1, y1) && outOfBounds(x2, y2);
    }

    public boolean outOfBounds(Vec2f v) {
        return outOfBounds(v.x, v.y);
    }

    public boolean outOfBounds(float x, float y) {
        // A NaN here means projectVector() couldn't place this point on screen at all (see the
        // comment there) - treat that the same as being off-screen so it gets culled instead of
        // drawn at whatever garbage coordinate NaN comparisons would otherwise produce.
        if (Float.isNaN(x) || Float.isNaN(y))
            return true;

        // Culling here happens using the raw, pre-scale projected coordinates, but the
        // matrix that actually draws them applies mapScale afterward (see
        // SimulationRenderer.render: matrices.scale(scale, -scale)). Without dividing by
        // mapScale here, this compared unscaled pixel offsets against the full HUD box size,
        // which silently discarded almost every block once real camera coordinates made the
        // projected offsets a normal, non-inflated size.
        float halfW = (width / 2F) / mapScale;
        float halfH = (height / 2F) / mapScale;
        return x < -halfW || y < -halfH || x > halfW || y > halfH;
    }

    public float withScale(float value, float scale) {
        return value / scale - value;
    }
}