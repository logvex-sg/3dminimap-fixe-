package io.github.itzispyder.impropers3dminimap.render.animation;

import java.util.function.BooleanSupplier;

public class PollingAnimator extends Animator {

    private final BooleanSupplier poll;
    private boolean pollSuccess;

    public PollingAnimator(int length, BooleanSupplier poll, Animations.AnimationController animationController) {
        super(length, animationController);
        this.poll = poll;

        // Don't call poll.getAsBoolean() here: PollingAnimators are built as instance/static
        // fields (e.g. SimulationRadar.zoomAnimator), which run during the mod's class-init,
        // before MinecraftClient has finished constructing itself. Polling a KeyBinding that
        // early can NPE (other mods' keybind mixins assume mc.options already exists). Start
        // in the "not pressed" state instead; the real state is picked up by the first real
        // poll() call once rendering actually starts.
        this.pollSuccess = false;
        this.setReversed(true);
    }

    public PollingAnimator(int length, BooleanSupplier poll) {
        this(length, poll, Animations.LINEAR);
    }

    @Override
    public double getProgress() {
        poll();
        return super.getProgress();
    }

    public void poll() {
        if (poll.getAsBoolean() && !pollSuccess) {
            pollSuccess = true;
            this.setReversed(false);
            this.reset();
        }
        else if (!poll.getAsBoolean() && pollSuccess) {
            pollSuccess = false;
            this.setReversed(true);
            this.reset();
        }
    }
}
