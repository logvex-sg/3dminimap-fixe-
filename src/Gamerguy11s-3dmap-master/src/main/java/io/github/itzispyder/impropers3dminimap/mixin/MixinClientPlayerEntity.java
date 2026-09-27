package io.github.itzispyder.impropers3dminimap.mixin;

import io.github.itzispyder.impropers3dminimap.Impropers3DMinimap;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity {

    @Inject(method = "init", at = @At("RETURN"))
    public void init(CallbackInfo ci) {
        // Don't route through PlayerUtils/mc.player here: this fires at the RETURN of the new
        // ClientPlayerEntity's own init(), which happens BEFORE MinecraftClient.player is
        // (re)assigned to this new instance. PlayerUtils.invalid() was therefore always true at
        // this point, onJoin() silently bailed out every time, and `simulation` never got
        // (re)created on join/respawn/dimension change - onTick() then hit the `simulation ==
        // null` early-return forever after, which is why the minimap rendered nothing. Pass the
        // instance being initialized directly instead of relying on the not-yet-updated field.
        Impropers3DMinimap.radar.onJoin((ClientPlayerEntity)(Object)this);
    }
}
