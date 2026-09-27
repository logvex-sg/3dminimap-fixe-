package io.github.itzispyder.impropers3dminimap.render.simulation;

import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.itzispyder.impropers3dminimap.util.minecraft.RenderConstants;
import net.minecraft.client.render.RenderLayer;

public enum SimulationMethod {

    // Filled voxels are the only render mode. The old LINES (wireframe) mode is what made the
    // minimap look like "lines in the block colour" instead of solid terrain, and it was
    // reachable through the persisted `render-method` config key - so a stale or hand-edited
    // value silently downgraded the map. Removing the constant also makes any leftover "LINES"
    // string in the config fail to resolve and fall back to the default (see SettingData.revert).
    QUADS(VertexFormat.DrawMode.QUADS, 0xFF, RenderConstants.QUADS);

    public final VertexFormat.DrawMode drawMode;
    public final int transparency;
    private final RenderLayer layer;

    SimulationMethod(VertexFormat.DrawMode drawMode, int transparency, RenderLayer layer) {
        this.drawMode = drawMode;
        this.transparency = transparency;
        this.layer = layer;
    }

    public RenderLayer getLayer() {
        return layer;
    }
}