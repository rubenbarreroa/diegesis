package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisBuffers;
import com.pockyl.diegesis.api.scene.ClearViewOptions;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.focus.FocusController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the camera's view of the station free ({@link ClearViewOptions}).
 */
public final class ClearView {
    private static final float NEAR_RADIUS = 0.2F;
    private static final float BEHIND = 0.5F;
    private static final Map<RenderType, Optional<RenderType>> GHOSTS = new HashMap<>();

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Vector3f AXIS = new Vector3f();
    private static final Vector3f POINT = new Vector3f();
    private static float strength;
    private static float length;
    private static float radius;
    private static float soft;
    private static boolean entities;
    private static boolean particles;
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static Entity current;
    private static FadingParticle faded;
    private static volatile Cone cone;

    private record Cone(Matrix4f view, float axisX, float axisY, float axisZ, float length, float radius, float soft, float strength,
                        double cameraX, double cameraY, double cameraZ) {
    }

    private ClearView() {
    }

    public static void begin(Matrix4f view, Camera camera) {
        strength = 0.0F;
        cone = null;
        FocusController focus = FocusController.get();
        ClientStation station = focus.station();
        if (!focus.isActive() || station == null || station.scene() == null) {
            return;
        }
        ClearViewOptions options = station.scene().definition().clearView();
        Vec3 eye = camera.position();
        Vector3fc looked = focus.baseTarget();
        Vec3 target = station.toWorld(looked.x(), looked.y(), looked.z());
        VIEW.set(view);
        VIEW.transformDirection(AXIS.set((float) (target.x - eye.x), (float) (target.y - eye.y), (float) (target.z - eye.z)));
        length = AXIS.length();
        if (length < 0.01F) {
            return;
        }
        AXIS.div(length);
        cameraX = eye.x;
        cameraY = eye.y;
        cameraZ = eye.z;
        radius = options.radius() / 16.0F;
        soft = options.soft() / 16.0F;
        entities = options.entities();
        particles = options.particles();
        strength = focus.blend();
        if (particles && strength > 0.0F) {
            cone = new Cone(new Matrix4f(VIEW), AXIS.x, AXIS.y, AXIS.z, length, radius, soft, strength, cameraX, cameraY, cameraZ);
        }
    }

    private static float fade(float x, float y, float z) {
        return fade(AXIS.x, AXIS.y, AXIS.z, length, radius, soft, strength, x, y, z);
    }

    private static float fade(float axisX, float axisY, float axisZ, float length, float radius, float soft, float strength,
                              float x, float y, float z) {
        float along = x * axisX + y * axisY + z * axisZ;
        if (along <= 0.0F || along >= length + BEHIND) {
            return 1.0F;
        }
        float dx = x - axisX * along;
        float dy = y - axisY * along;
        float dz = z - axisZ * along;
        float width = NEAR_RADIUS + (radius - NEAR_RADIUS) * Math.min(1.0F, along / length);
        float inside = 1.0F - smooth(((float) Math.sqrt(dx * dx + dy * dy + dz * dz) - width) / soft);
        if (along > length) {
            inside *= 1.0F - smooth((along - length) / BEHIND);
        }
        return 1.0F - strength * inside;
    }

    public static float particleFadeAt(double x, double y, double z) {
        Cone c = cone;
        if (c == null) {
            return 1.0F;
        }
        Vector3f point = c.view().transformDirection(
                new Vector3f((float) (x - c.cameraX()), (float) (y - c.cameraY()), (float) (z - c.cameraZ())));
        return fade(c.axisX(), c.axisY(), c.axisZ(), c.length(), c.radius(), c.soft(), c.strength(), point.x, point.y, point.z);
    }

    private static float smooth(float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    public static void entity(Entity entity) {
        current = entity;
    }

    public static DiegesisBuffers buffers(DiegesisBuffers buffers) {
        if (strength <= 0.0F || !entities || current == null || ShaderShadows.drawing() || !mayBeInside(current.getBoundingBox())) {
            return buffers;
        }
        return type -> {
            Optional<RenderType> ghost = GHOSTS.computeIfAbsent(type, ClearView::ghostOf);
            if (ghost.isEmpty()) {
                return buffers.getBuffer(type);
            }
            Fading fading = new Fading();
            fading.delegate = buffers.getBuffer(ghost.get());
            return fading;
        };
    }

    private static boolean mayBeInside(AABB box) {
        Vec3 center = box.getCenter();
        float reach = (float) Math.max(box.getSize(), 1.0) + soft;
        VIEW.transformDirection(POINT.set((float) (center.x - cameraX), (float) (center.y - cameraY), (float) (center.z - cameraZ)));
        float along = POINT.dot(AXIS);
        if (along < -reach || along > length + BEHIND + reach) {
            return false;
        }
        float distance = POINT.fma(-along, AXIS).length();
        return distance < radius + reach;
    }

    private static Optional<RenderType> ghostOf(RenderType type) {
        if (!type.format().contains("Normal") || !type.format().contains("UV2")) {
            return Optional.empty();
        }
        RenderSetup setup = type.state;
        if (setup == null || setup.textures == null) {
            return Optional.empty();
        }
        RenderSetup.TextureBinding binding = setup.textures.get("Sampler0");
        return binding != null ? Optional.of(DiegesisRenderTypes.ghostEntity(binding.location())) : Optional.empty();
    }

    public static float particleFade(double x, double y, double z) {
        restoreParticle();
        if (strength <= 0.0F || !particles) {
            return 1.0F;
        }
        VIEW.transformDirection(POINT.set((float) x, (float) y, (float) z));
        return fade(POINT.x, POINT.y, POINT.z);
    }

    public static void fadedParticle(FadingParticle particle) {
        faded = particle;
    }

    public static void restoreParticle() {
        FadingParticle particle = faded;
        if (particle != null) {
            faded = null;
            particle.diegesis$restore();
        }
    }

    public interface FadingParticle {
        void diegesis$restore();
    }

    private static final class Fading implements VertexConsumer {
        private VertexConsumer delegate;
        private float fade = 1.0F;

        private void look(float x, float y, float z) {
            VIEW.transformDirection(POINT.set(x, y, z));
            fade = ClearView.fade(POINT.x, POINT.y, POINT.z);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            look(x, y, z);
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, Math.round(alpha * fade));
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            int alpha = Math.round((color >>> 24) * fade);
            delegate.setColor((alpha & 0xFF) << 24 | (color & 0xFFFFFF));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            delegate.setUv3(u, v);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY,
                float normalZ) {
            look(x, y, z);
            int alpha = Math.round((color >>> 24) * fade);
            delegate.addVertex(x, y, z, alpha << 24 | color & 0xFFFFFF, u, v, overlay, light, normalX, normalY, normalZ);
        }
    }
}
