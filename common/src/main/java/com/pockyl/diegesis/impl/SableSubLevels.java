package com.pockyl.diegesis.impl;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4d;

/**
 * Optional soft-compatibility bridge for Sable moving structures via reflection,
 * so Diegesis builds cleanly on Minecraft 26.3 without a compile-time Sable artifact.
 */
public final class SableSubLevels implements SubLevels.Bridge {
    private final Object companionInstance;
    private final Method getContaining;
    private final Class<?> clientSubLevelAccessClass;
    private final Method renderPose;
    private final Method logicalPose;
    private final Method bakeIntoMatrix;

    private SableSubLevels() throws ReflectiveOperationException {
        Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
        this.companionInstance = companionClass.getField("INSTANCE").get(null);
        this.getContaining = companionClass.getMethod("getContaining", Level.class, BlockPos.class);
        Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess");
        this.clientSubLevelAccessClass = Class.forName("dev.ryanhcode.sable.companion.ClientSubLevelAccess");
        this.renderPose = this.clientSubLevelAccessClass.getMethod("renderPose");
        this.logicalPose = subLevelClass.getMethod("logicalPose");
        Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
        this.bakeIntoMatrix = poseClass.getMethod("bakeIntoMatrix", Matrix4d.class);
    }

    public static void install() {
        try {
            SubLevels.install(new SableSubLevels());
        } catch (Throwable ignored) {
            // Sable not present or API changed on this version; safely skip.
        }
    }

    @Override
    public boolean pose(Level level, BlockPos pos, boolean render, Matrix4d dest) {
        try {
            Object sub = getContaining.invoke(companionInstance, level, pos);
            if (sub == null) {
                return false;
            }
            Object pose = (render && clientSubLevelAccessClass.isInstance(sub))
                    ? renderPose.invoke(sub)
                    : logicalPose.invoke(sub);
            bakeIntoMatrix.invoke(pose, dest);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
