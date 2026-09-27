package io.github.itzispyder.impropers3dminimap.render.simulation;

import io.github.itzispyder.impropers3dminimap.Global;
import io.github.itzispyder.impropers3dminimap.client.Impropers3DMinimapClient;
import io.github.itzispyder.impropers3dminimap.config.Setting;
import io.github.itzispyder.impropers3dminimap.config.SettingSection;
import io.github.itzispyder.impropers3dminimap.render.animation.Animations;
import io.github.itzispyder.impropers3dminimap.render.animation.Animator;
import io.github.itzispyder.impropers3dminimap.render.animation.PollingAnimator;
import io.github.itzispyder.impropers3dminimap.render.ui.hud.Hud;
import io.github.itzispyder.impropers3dminimap.render.ui.hud.moveables.SimulationHud;
import io.github.itzispyder.impropers3dminimap.util.minecraft.PlayerUtils;
import io.github.itzispyder.impropers3dminimap.util.misc.Dictionary;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;


public class SimulationRadar implements Global {

    private final SettingSection scRadar = getConfig().createSettingSection("radar-settings");

    /**
     * PLEASE SEE setEnabled()
     */
    public final Setting<Boolean> enabled = scRadar.add(getConfig().createBoolSetting()
            .name("enabled")
            .def(true)
            .onSettingChange(setting -> {
                boolean enabled = setting.getVal();
                if (enabled)
                    onEnable();
                else
                    onDisable();
            })
            .build()
    );
    public final Setting<Dictionary<EntityType<?>>> targets = scRadar.add(getConfig().createEntitiesSetting()
            .name("targets")
            .build()
    );
    public final Setting<Dictionary<Block>> highlights = scRadar.add(getConfig().createBlocksSetting()
            .name("highlights")
            .build()
    );
    public final Setting<Integer> updateFrequency = scRadar.add(getConfig().createIntSetting()
            .name("update-frequency-seconds")
            .max(5)
            .min(1)
            .def(1)
            .build()
    );
    public final Setting<Integer> range = scRadar.add(getConfig().createIntSetting()
            .name("range")
            .max(20)
            .min(5)
            .def(20)
            .build()
    );
    private final SettingSection scHud = getConfig().createSettingSection("hud-settings");
    public final Setting<Double> ratioScale = scHud.add(getConfig().createDoubleSetting()
            .name("ratio-scale")
            .decimalPlaces(1)
            .max(3.0)
            .min(1.0)
            .def(2.0)
            .onSettingChange(setting -> updateHud())
            .build()
    );
    public final Setting<Ratios> ratio = scHud.add(getConfig().createEnumSetting(Ratios.class)
            .name("ratio")
            .def(Ratios.RATIO_2_1)
            .onSettingChange(setting -> updateHud())
            .build()
    );
    public final Setting<Boolean> renderBackground = scHud.add(getConfig().createBoolSetting()
            .name("render-background")
            .def(true)
            .build()
    );
    public final Setting<Integer> borderRadius = scHud.add(getConfig().createIntSetting()
            .name("border-radius")
            .max(8)
            .min(3)
            .def(3)
            .build()
    );
    private final SettingSection scRender = getConfig().createSettingSection("render-settings");
    public final Setting<SimulationMethod> drawMode = scRender.add(getConfig().createEnumSetting(SimulationMethod.class)
            .name("render-method")
            .def(SimulationMethod.QUADS)
            .onSettingChange(setting -> {
                Simulation sim = getSimulation();
                if (sim == null)
                    return;
                sim.setMethod(setting.getVal());
            })
            .build()
    );
    public final Setting<Boolean> useMapColors = scRender.add(getConfig().createBoolSetting()
            .name("use-map-colors")
            .def(true)
            .build()
    );
    public final Setting<Integer> focalLength = scRender.add(getConfig().createIntSetting()
            .name("focal-length")
            .max(1000)
            .min(1)
            .def(10)
            .onSettingChange(self -> setFocalLength(self.getVal()))
            .build()
    );
    public final Setting<Double> scale = scRender.add(getConfig().createDoubleSetting()
            .name("scale")
            .decimalPlaces(1)
            .max(15.0)
            .min(1.0)
            .def(4.0)
            .onSettingChange(self -> {
                Simulation sim = getSimulation();
                if (sim != null)
                    sim.setMapScale(self.getVal().floatValue());
            })
            .build()
    );

    public final Animator zoomAnimator = new PollingAnimator(300, Impropers3DMinimapClient.BIND_ZOOM::isPressed, Animations.FADE_IN_AND_OUT);

    private Simulation simulation;
    private BlockPos previousPos;
    private int ticks;

    public SimulationRadar() {

    }

    public void onEnable() {
        // Toggling the setting happens well after join, so mc.player is already valid here -
        // this path is fine going through PlayerUtils.
        if (PlayerUtils.valid())
            onJoin(PlayerUtils.player());
    }

    public void onDisable() {
        if (simulation != null) {
            simulation.getRenderer().clear();
            simulation = null;
        }
        previousPos = null;
    }

    public void onTick() {
        if (PlayerUtils.invalid() || simulation == null)
            return;

        Hud hud = Hud.get(SimulationHud.class);
        simulation.setDimensions(hud.getX(), hud.getY(), hud.getWidth(), hud.getHeight());

        BlockPos playerPos = PlayerUtils.player().getBlockPos();
        int max = updateFrequency.getVal() * 20;
        boolean canUpdate = previousPos == null || !previousPos.equals(playerPos);

        if (ticks++ >= max && canUpdate) {
            // This used to run on asyncExecutor (a background thread pool) touching
            // world.getBlockState()/getCollisionShape() tens of thousands of times per scan.
            // BlockView/chunk data is not safe to read concurrently with the main client thread,
            // which is mutating it constantly (chunk load/unload, block updates) - that's a
            // classic source of nondeterministic corruption, exceptions, and lock contention
            // with the render thread, which is exactly "sometimes it just won't render" and FPS
            // hitching. It was added to keep this off the render thread for FPS, but doing it
            // unsafely is worse than not doing it at all. Running it synchronously here is safe;
            // it's already throttled to once every updateFrequency seconds (not every frame), so
            // the cost is a small periodic stutter instead of random breakage.
            try {
                simulation.update(range.getVal(), useMapColors.getVal(), highlights.getVal());
            } catch (Throwable t) {
                t.printStackTrace();
            }
            previousPos = playerPos;
            ticks = 0;
        }
        simulation.updateEntities(range.getVal() * 2, targets.getVal());
    }

    public void renderHud(DrawContext context) {
        if (PlayerUtils.invalid() || !mc.isWindowFocused() || simulation == null)
            return;

        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        ClientPlayerEntity p = PlayerUtils.player();

        Quaternionf rotationPitch = new Quaternionf().rotationX((float)Math.toRadians(p.getPitch()));
        Quaternionf rotationYaw = new Quaternionf().rotationY((float)Math.toRadians(p.getYaw() + 180));
        simulation.render(context, cam, rotationPitch.mul(rotationYaw), renderBackground.getVal(), borderRadius.getVal(), system.accent.getHex());
    }

    public void onJoin(net.minecraft.client.network.ClientPlayerEntity player) {
        // Deliberately takes the player instance instead of reading PlayerUtils.player()/mc.player:
        // this is called from the ClientPlayerEntity mixin at the moment that entity finishes its
        // own init(), which is before MinecraftClient.player gets pointed at it. Going through
        // mc.player here would see the old (or null) player and silently skip creating the
        // simulation - see the comment in MixinClientPlayerEntity.
        if (player == null || player.getEntityWorld() == null)
            return;
        Hud hud = Hud.get(SimulationHud.class);

        simulation = new Simulation(
                player,
                hud.getX(), hud.getY(), hud.getWidth(), hud.getHeight(),
                scale.getVal().floatValue(), drawMode.getVal(), focalLength.getVal()
        );
        System.out.println("[DIAG] onJoin: simulation created, hud=" + hud
                + " x=" + hud.getX() + " y=" + hud.getY() + " w=" + hud.getWidth() + " h=" + hud.getHeight()
                + " enabled=" + enabled.getVal());
    }

    public void setFocalLength(double focalLength) {
        if (simulation != null)
            simulation.setFocalLength(focalLength);
    }

    public Simulation getSimulation() {
        return simulation;
    }

    public boolean isEnabled() {
        return enabled.getVal();
    }

    public void setEnabled(boolean enabled) {
        if (enabled != isEnabled())
            this.enabled.setVal(enabled);
    }

    public void updateHud() {
        Hud hud = Hud.get(SimulationHud.class);
        if (hud == null)
            return;

        Ratios ratio = this.ratio.getVal();
        float scale = ratioScale.getVal().floatValue() * 100;
        float a = ratio.a;
        float b = ratio.b;
        float c = (float) Math.sqrt(a * a + b * b);

        int width = (int) (a / c * scale);
        int height = (int) (b / c * scale);

        hud.setWidth(width);
        hud.setHeight(height);
    }

    public enum Ratios {
        RATIO_2_1(2, 1),
        RATIO_4_3(4, 3),
        RATIO_1_1(1, 1),
        RATIO_3_4(3, 4),
        RATIO_1_2(1, 2);

        public final int a, b;

        Ratios(int a, int b) {
            this.a = a;
            this.b = b;
        }
    }
}
