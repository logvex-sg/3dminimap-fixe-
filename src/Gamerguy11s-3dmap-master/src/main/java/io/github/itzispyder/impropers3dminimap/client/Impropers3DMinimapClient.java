package io.github.itzispyder.impropers3dminimap.client;

import io.github.itzispyder.impropers3dminimap.Impropers3DMinimap;
import io.github.itzispyder.impropers3dminimap.render.ui.hud.Hud;
import io.github.itzispyder.impropers3dminimap.render.ui.hud.moveables.SimulationHud;
import io.github.itzispyder.impropers3dminimap.render.ui.screens.ConfigScreen;
import io.github.itzispyder.impropers3dminimap.util.math.Color;
import io.github.itzispyder.impropers3dminimap.util.minecraft.PlayerUtils;
import io.github.itzispyder.impropers3dminimap.util.minecraft.RenderUtils;
import io.github.itzispyder.impropers3dminimap.util.misc.Scheduler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class Impropers3DMinimapClient implements ClientModInitializer {

    // KeyBinding's constructor now takes a KeyBinding.Category instead of a raw String category
    // id. Checked KeyBinding.java directly: Category.create(String) is a *private* convenience
    // overload only used internally for vanilla's own categories (it calls Identifier.ofVanilla).
    // The public entry point for mods is Category.create(Identifier), so we build our own
    // namespaced Identifier instead.
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("impropers3dminimap", "binds"));

    public static final KeyBinding BIND_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "binds.impropers3dminimap.menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
    ));
    public static final KeyBinding BIND_ZOOM = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "binds.impropers3dminimap.zoom",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY
    ));

    private static final Impropers3DMinimapClient system = new Impropers3DMinimapClient();
    public static Impropers3DMinimapClient getInstance() {
        return system;
    }

    public TextRenderer textRenderer;
    public final Scheduler scheduler;
    public final Color accent;
    public final Color background;

    public Impropers3DMinimapClient() {
        this.scheduler = new Scheduler();
        this.accent = new Color(0xFF0080B3);
        this.background = new Color(0xB2000000);
    }

    @Override
    public void onInitializeClient() {
        Hud.addHud(new SimulationHud());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (BIND_MENU.wasPressed())
                client.setScreen(new ConfigScreen());
            Impropers3DMinimap.radar.onTick();
        });
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.of("impropers3dminimap", "simulation_hud"),
                (drawContext, tickCounter) -> {
                    if (PlayerUtils.invalid())
                        return;
                    if (!diagLogged) {
                        diagLogged = true;
                        SimulationHud sh = Hud.get(SimulationHud.class);
                        System.out.println("[DIAG] hud callback firing. simulationHud=" + sh
                                + " canRender=" + (sh != null && sh.canRender())
                                + " simulation=" + (Impropers3DMinimap.radar.getSimulation())
                                + " worldSize=" + (Impropers3DMinimap.radar.getSimulation() != null
                                        ? Impropers3DMinimap.radar.getSimulation().getRenderer().worldSize() : -1)
                                + " enabled=" + Impropers3DMinimap.radar.isEnabled());
                    }
                    RenderUtils.beginGuiDraws();
                    try {
                        for (Hud hud : Hud.huds().values()) {
                            try {
                                hud.render(drawContext);
                            } catch (Throwable t) {
                                if (!diagErrorLogged) {
                                    diagErrorLogged = true;
                                    t.printStackTrace();
                                }
                            }
                        }
                    } finally {
                        RenderUtils.endGuiDraws();
                    }
                }
        );
    }

    private boolean diagLogged = false;
    private boolean diagErrorLogged = false;
}
