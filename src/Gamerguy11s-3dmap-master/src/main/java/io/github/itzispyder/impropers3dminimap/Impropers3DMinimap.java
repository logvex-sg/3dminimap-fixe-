package io.github.itzispyder.impropers3dminimap;

import io.github.itzispyder.impropers3dminimap.config.Config;
import io.github.itzispyder.impropers3dminimap.render.simulation.SimulationRadar;
import io.github.itzispyder.impropers3dminimap.util.misc.JsonSerializable;
import net.fabricmc.api.ModInitializer;

// NOTE: this is Fabric's *common* entrypoint ("main" in fabric.mod.json) - it is expected to run
// on any environment, including a dedicated server, per "environment": "*". It must therefore
// only reference environment-agnostic code. Keybindings, HudRenderCallback and ClientTickEvents
// are all client-only Fabric API and used to be registered here directly; that only happened to
// work because a real dedicated server never loads this mod's jar in practice, but it's not a
// safe assumption to build on and it's not how Fabric's entrypoint contract is meant to be used.
// That registration now lives in Impropers3DMinimapClient#onInitializeClient, the "client"
// entrypoint, where it belongs. This class now only holds shared state (config, radar).
public class Impropers3DMinimap implements ModInitializer, Global {

    public static final Config config = JsonSerializable.load(Config.PATH, Config.class, new Config());
    public static final SimulationRadar radar = new SimulationRadar();

    @Override
    public void onInitialize() {
        config.load();
        config.save();
    }
}
