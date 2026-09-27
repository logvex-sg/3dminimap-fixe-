package io.github.itzispyder.impropers3dminimap.render.simulation;

import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.itzispyder.impropers3dminimap.render.animation.Animator;
import io.github.itzispyder.impropers3dminimap.util.math.Color;
import io.github.itzispyder.impropers3dminimap.util.math.MathUtils;
import io.github.itzispyder.impropers3dminimap.util.minecraft.RenderConstants;
import io.github.itzispyder.impropers3dminimap.util.minecraft.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix3x2fStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimulationRenderer {

    private final Simulation simulation;
    private final Map<ChunkPos, Map<BlockPos, SimulatedBlock>> blocks, highlightedBlocks;
    private final Map<Integer, Entity> entities;
    private final Animator radarPingAnimator;

    public SimulationRenderer(Simulation simulation) {
        this.simulation = simulation;
        this.blocks = new ConcurrentHashMap<>();
        this.highlightedBlocks = new ConcurrentHashMap<>();
        this.entities = new HashMap<>();
        this.radarPingAnimator = new Animator(1000);
    }

    public void render(DrawContext context, Vec3d camera, Vec3d focalPoint, Quaternionf rotation, float scale, double zoomDelta) {
        int originX = (int) focalPoint.x;
        int originY = (int) focalPoint.y;
        scale = (float) MathUtils.lerpClamped(scale, 4.0, zoomDelta);

        Matrix3x2fStack matrices = context.getMatrices();

        matrices.pushMatrix();
        matrices.translate(originX, originY);
        matrices.scale(scale, -scale);

        renderWorld(context.getMatrices(), camera, rotation);
        renderWorldHighlighted(context.getMatrices(), camera, rotation);
        renderEntities(context, camera, rotation);

        matrices.popMatrix();
    }

    public void renderWorld(Matrix3x2fStack matrices, Vec3d camera, Quaternionf rotation) {
        BufferBuilder buf = RenderUtils.getBuffer(simulation.getMethod().drawMode, VertexFormats.POSITION_COLOR);
        Matrix4f mat = RenderUtils.toMatrix4f(matrices);

        List<SimulatedBlock> sorted = sortedByDistance(blocks, camera);

        for (SimulatedBlock block : sorted)
            block.render(mat, buf, simulation, camera, rotation);

        BuiltBuffer draw = buf.endNullable();
        if (draw == null)
            return;

        simulation.getMethod().getLayer().draw(draw);
    }

    public void renderWorldHighlighted(Matrix3x2fStack matrices, Vec3d camera, Quaternionf rotation) {
        BufferBuilder buf = RenderUtils.getBuffer(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f mat = RenderUtils.toMatrix4f(matrices);

        for (SimulatedBlock block : sortedByDistance(highlightedBlocks, camera))
            block.render(mat, buf, simulation, camera, rotation);

        BuiltBuffer draw = buf.endNullable();
        boolean empty = draw == null;

        if (empty)
            return;

        RenderConstants.QUADS.draw(draw);
    }

    /**
     * The render pipelines used here (see RenderConstants) have depth testing and depth
     * writing both disabled, and every projected vertex is flattened to screen z=0 anyway -
     * so there is nothing on the GPU side deciding which face is "in front". Without this,
     * blocks draw in whatever order a HashMap happens to iterate them, which is why nearer
     * and farther blocks were overlapping in random order instead of looking like solid,
     * correctly-occluded 3D geometry. This sorts farthest-first so nearer blocks are drawn
     * last and correctly paint over anything farther away (a manual painter's algorithm).
     */
    private static List<SimulatedBlock> sortedByDistance(Map<ChunkPos, Map<BlockPos, SimulatedBlock>> source, Vec3d camera) {
        List<SimulatedBlock> list = new ArrayList<>();
        for (var chunk : source.values())
            list.addAll(chunk.values());

        list.sort(Comparator.comparingDouble((SimulatedBlock b) -> distanceSq(b.getPos(), camera)).reversed());
        return list;
    }

    private static double distanceSq(BlockPos pos, Vec3d camera) {
        double dx = (pos.getX() + 0.5) - camera.x;
        double dy = (pos.getY() + 0.5) - camera.y;
        double dz = (pos.getZ() + 0.5) - camera.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public void renderEntities(DrawContext context, Vec3d camera, Quaternionf rotation) {
        if (radarPingAnimator.isFinished()) {
            radarPingAnimator.reverse();
            radarPingAnimator.reset();
        }

        float animation = (float)radarPingAnimator.getAnimation();
        float radius = 1.0F;

        for (Entity ent : entities.values()) {
            Vec2f pos = simulation.projectVector(ent.getEntityPos().subtract(camera), rotation);
            Color color = simulation.outOfBounds(pos.x, pos.y) ? Color.ORANGE : Color.RED;
            fillCircle(context, pos.x, pos.y, 0.5F + radius * animation, color.getHexCustomAlpha(0.5));
            fillCircle(context, pos.x, pos.y, 0.333F, color.getHex());
        }
    }

    public static void fillCircle(DrawContext context, float cX, float cY, float radius, int color) {
        BufferBuilder buf = RenderUtils.getBuffer(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        Matrix4f mat = RenderUtils.toMatrix4f(context.getMatrices());

        buf.vertex(mat, cX, cY, 0).color(color);

        for (int i = 0; i <= 360; i ++) {
            double angle = Math.toRadians(i);
            float x = (float)(Math.cos(angle) * radius) + cX;
            float y = (float)(Math.sin(angle) * radius) + cY;
            buf.vertex(mat, x, y, 0).color(color);
        }

        RenderUtils.drawBuffer(buf, RenderConstants.TRI_FAN);
    }

    public synchronized void update(BlockView world, BlockPos pos, boolean useMapColors, boolean highlight) {
        if (pos != null && world != null) {
            SimulatedBlock block = new SimulatedBlock(world, pos, useMapColors, simulation.getMethod(), highlight);
            if (!block.isValid())
                return;

            ChunkPos chunkPos = ChunkSectionPos.from(pos).toChunkPos();
            var chunk = highlight ? highlightedBlocks : blocks;
            var blocks = chunk.computeIfAbsent(chunkPos, cp -> new ConcurrentHashMap<>());
            blocks.put(pos, block);
        }
    }

    public synchronized void update(BlockView world, BlockPos pos, BlockState state, boolean useMapColors, boolean highlight) {
        if (pos != null && world != null && state != null) {
            SimulatedBlock block = new SimulatedBlock(world, pos, state, useMapColors, simulation.getMethod(), highlight);
            if (!block.isValid())
                return;

            ChunkPos chunkPos = ChunkSectionPos.from(pos).toChunkPos();
            var chunk = highlight ? highlightedBlocks : blocks;
            var blocks = chunk.computeIfAbsent(chunkPos, cp -> new ConcurrentHashMap<>());
            blocks.put(pos, block);
        }
    }

    public synchronized void update(Entity entity) {
        if (entity != null && entity.isAlive() && !entity.isSpectator())
            entities.put(entity.getId(), entity);
    }

    public void clearWorld() {
        blocks.clear();
        highlightedBlocks.clear();
    }

    public void clearEntities() {
        entities.clear();
    }

    public void clear() {
        clearEntities();
        clearWorld();
    }

    public int worldSize() {
        return blocks.values().stream().mapToInt(Map::size).sum()
                + highlightedBlocks.values().stream().mapToInt(Map::size).sum();
    }

    public int entityCount() {
        return entities.size();
    }
}