package io.github.itzispyder.impropers3dminimap.util.minecraft;

import com.mojang.authlib.GameProfile;
import io.github.itzispyder.impropers3dminimap.Global;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class PlayerUtils implements Global {

    public static boolean invalid() {
        return mc == null || mc.player == null || mc.world == null || mc.interactionManager == null || mc.options == null;
    }

    public static boolean valid() {
        return !invalid();
    }

    public static boolean playerValid(PlayerEntity player) {
        if (invalid() || player == null) {
            return false;
        }

        ClientPlayerEntity p = mc.player;
        GameProfile profile = player.getGameProfile();
        PlayerListEntry entry = p.networkHandler.getPlayerListEntry(profile.id());

        return entry != null;
    }

    public static ClientPlayerEntity player() {
        return mc.player;
    }

    public static World getWorld() {
        return player().getEntityWorld();
    }

    public static ClientWorld getClientWorld() {
        // Entity.getEntityWorld() returns the generic World type; a ClientPlayerEntity's world is
        // always a ClientWorld at runtime, so this cast is safe (mirrors the old `clientWorld`
        // field access, which no longer exists).
        return (ClientWorld) player().getEntityWorld();
    }

    public static Vec3d getPos() {
        return player().getEntityPos();
    }

    public static Vec3d getEyes() {
        return player().getEyePos();
    }

    public static ClientPlayerInteractionManager getInteractions() {
        return mc.interactionManager;
    }

    public static void sendPacket(Packet<?> packet) {
        if (!invalid()) {
            player().networkHandler.sendPacket(packet);
        }
    }

    public static float getEntityNameLabelHeight(Entity entity, float tickDelta) {
        float yaw = entity.getYaw(tickDelta);
        Vec3d vec = entity.getAttachments().getPointNullable(EntityAttachmentType.NAME_TAG, 0, yaw);
        return (float)(vec == null ? 0.5 : vec.y + 0.5);
    }

    public static long getPing() {
        if (invalid()) {
            return -1;
        }

        GameProfile p = player().getGameProfile();
        // GameProfile is now a record in authlib 7.0.61 — getId() became id().
        PlayerListEntry entry = player().networkHandler.getPlayerListEntry(p.id());

        if (entry == null) {
            return -1;
        }

        return entry.getLatency();
    }

    public static int getFps() {
        return mc.getCurrentFps();
    }

    public static boolean isMoving() {
        if (invalid()) return false;
        ClientPlayerEntity p = player();

        return p.sidewaysSpeed != 0 || p.forwardSpeed != 0;
    }

    public static Entity getNearestEntity(Entity exclude, Vec3d at, double range, Predicate<Entity> filter) {
        return TargetUtils.getNearest(exclude, at, range, filter);
    }

    public static Entity getNearestEntity(double range, Predicate<Entity> filter) {
        return TargetUtils.getNearest(range, filter);
    }

    public static PlayerEntity getNearestPlayer(double range, Predicate<Entity> filter) {
        return (PlayerEntity)getNearestEntity(range, e -> filter.test(e) && TargetUtils.VALID_PLAYER.test(e));
    }

    public static void boxIterator(World world, Box box, BiConsumer<BlockPos, BlockState> function) {
        for (double x = box.minX; x <= box.maxX; x++) {
            for (double y = box.minY; y <= box.maxY; y++) {
                for (double z = box.minZ; z <= box.maxZ; z++) {
                    BlockPos pos = new BlockPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
                    BlockState state = world.getBlockState(pos);

                    if (state == null || state.isAir()) {
                        continue;
                    }
                    function.accept(pos, state);
                }
            }
        }
    }

    public static boolean runOnNearestBlock(double range, BiPredicate<BlockPos, BlockState> filter, BiConsumer<BlockPos, BlockState> function) {
        if (invalid()) {
            return false;
        }

        AtomicReference<Double> nearestDist = new AtomicReference<>(64.0);
        AtomicReference<BlockPos> nearestPos = new AtomicReference<>();
        AtomicReference<BlockState> nearestState = new AtomicReference<>();
        Box box = player().getBoundingBox().expand(range);
        Vec3d player = player().getEntityPos();
        World world = getWorld();

        PlayerUtils.boxIterator(world, box, (pos, state) -> {
            if (filter.test(pos, state) && pos.isWithinDistance(player, nearestDist.get())) {
                nearestDist.set(Math.sqrt(pos.getSquaredDistance(player)));
                nearestPos.set(pos);
                nearestState.set(state);
            }
        });

        if (nearestState.get() != null && nearestPos.get() != null) {
            function.accept(nearestPos.get(), nearestState.get());
            return true;
        }
        return false;
    }

    public static boolean runOnNearestBlock(double range, Predicate<BlockState> filter, BiConsumer<BlockPos, BlockState> function) {
        return runOnNearestBlock(range, (pos, state) -> filter.test(state), function);
    }

    public static boolean isHardcore() {
        return mc.getServer() != null && mc.getServer().isHardcore();
    }
}
