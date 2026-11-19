package com.pockyl.diegesis.impl.client.focus;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.scene.PlacingPhysics;
import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationSchema.SlotGroup;
import com.pockyl.diegesis.impl.action.StationAction;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.anim.Easing;
import com.pockyl.diegesis.impl.client.anim.Spring;
import com.pockyl.diegesis.impl.client.scene.NodeInstance;
import com.pockyl.diegesis.impl.client.scene.PlacedPhysics;
import com.pockyl.diegesis.impl.client.scene.PlacedItem;
import com.pockyl.diegesis.impl.client.scene.PlacedShape;
import com.pockyl.diegesis.impl.client.scene.Placing;
import com.pockyl.diegesis.impl.client.scene.SceneInstance;

/**
 * The hand at a scene with free placing: where the stack on the cursor would stand, how it is turned, and the turning of items
 * that stand already. The preview is worked out anew each frame from the cursor; nothing depends on where something was drawn.
 */
final class Placer {
    /** Times faster the stack in the hand comes down off an item than it rises onto one. */
    private static final float FALL = 3.0F;
    /** Pixels the stack in the hand may touch what it keeps beside without counting as over it. */
    private static final float BESIDE = 0.1F;
    /** How long a stack just put down is kept out of the hand while the server takes it. */
    private static final long PUT_DOWN_MILLIS = 500L;
    /** The swing of the stack in the hand: gravity (pixels a second squared), how much longer than to its middle the hold is
     * (fingers give a little), its damping ratio while the hand moves; below {@link #SWING_STILL} pixels a second the hand
     * counts as still and the swing calms down within {@code swing_calm}, reaching that damping in {@link #SWING_SETTLE}. */
    private static final float SWING_GRAVITY = 240.0F;
    private static final float SWING_GIVE = 2.0F;
    private static final float SWING_DAMPING = 0.18F;
    private static final float SWING_STILL = 6.0F;
    private static final float SWING_SETTLE = 0.15F;
    private static final float SWING_STEP = 1.0F / 240.0F;
    /** Of a rigid item's own tipping angle, how much tilt it keeps when let go of mid-swing: it rocks back onto its base. */
    private static final float RELEASE_TILT = 0.6F;
    /** How high (pixels) the stack in the hand arcs over as it hops to another surface. */
    private static final float HOP_ARC = 1.5F;
    /** How much of its swing the stack in the hand keeps as it bounces off a wall. */
    private static final float SWING_BOUNCE = 0.35F;

    private final Quaternionf turn = new Quaternionf();
    private float scale = 1.0F;
    private Item held;
    private ItemPose preview;
    /** With physics: where the stack in the hand is put down, upright under where it is held (the swing is only while carried). */
    private ItemPose letGo;
    private FocusInput.StationRay aimedRay;
    /** With physics: the hand's way across the surface (x, z; y unused), the surface it is over, its height and the rise onto items. */
    private final Spring glide = new Spring(0.07F);
    private PlaceSurface handSurface;
    private float base;
    private float climbed;
    private boolean gliding;
    /** The swing: how far the bottom is out (radians, x and z) and how fast it goes; the hand's speed a frame ago; how calm. */
    private final org.joml.Vector2f swingAngle = new org.joml.Vector2f();
    private final org.joml.Vector2f swingSpeed = new org.joml.Vector2f();
    private final Vector3f handSpeed = new Vector3f();
    private float calm;
    private Quaternionf hang = new Quaternionf();
    /** Where the stack in the hand was drawn last frame (station space), and the hop to another surface: from where, how far. */
    private final Vector3f drawn = new Vector3f();
    private final Vector3f hopFrom = new Vector3f();
    private final Vector3f hopAt = new Vector3f();
    private float hopLeft = -1.0F;
    /** How far the stack in the hand is held off what it presses into and cannot move (x, z): it stops beside it. */
    private final Vector3f nudge = new Vector3f();
    private ItemStack putDown;
    private long putDownUntil;

    /** The scene's placing, or null. */
    static PlacingOptions options(ClientStation station) {
        return station == null || station.scene() == null ? null : station.scene().definition().placing().orElse(null);
    }

    /** Where the stack in the hand would stand now, or null when it is not over a place. */
    ItemPose preview() {
        return preview;
    }

    void reset() {
        turn.identity();
        scale = 1.0F;
        held = null;
        preview = null;
        aimedRay = null;
        handSurface = null;
        gliding = false;
        putDown = null;
        hopLeft = -1.0F;
    }

    /** While the stack in the hand hops over to another surface: where it is, in station space; null otherwise. */
    Vector3fc hopPosition() {
        return gliding && hopLeft >= 0.0F ? hopAt : null;
    }

    /** The item taken from {@code pose} stays as it was turned while it is in the hand. */
    void adopt(ItemStack stack, ItemPose pose) {
        turn.set(pose.rotation());
        scale = pose.scale();
        held = stack.getItem();
    }

    /** With physics the stack in the hand glides and rises by itself: it is drawn right where this pose says, not eased again. */
    boolean glides() {
        return gliding;
    }

    /**
     * @param ray    the cursor ray
     * @param frozen the hand is turning the stack: it stays where it was
     * @param dt     seconds since the last frame
     */
    void update(ClientStation station, FocusInput.StationRay ray, ItemStack carried, boolean overWorld, boolean frozen, float dt) {
        gliding = false;
        preview = aim(station, ray, carried, overWorld, frozen, dt);
        PlacedPhysics physics = station.scene() == null ? null : station.scene().physics();
        if (!gliding) {
            handSurface = null;
            if (physics != null) {
                physics.carry(ItemStack.EMPTY, null, null, null);
            }
        }
    }

    private ItemPose aim(ClientStation station, FocusInput.StationRay ray, ItemStack carried, boolean overWorld, boolean frozen, float dt) {
        PlacingOptions options = options(station);
        if (options == null || carried.isEmpty() || !overWorld || !station.scene().isPlacingVisible()
                || !station.scene().isPlacingReachable()) {
            // Shut away (a door closed on them) or out of reach (behind its glass), the surfaces take nothing.
            return null;
        }
        if (carried == putDown && Util.getMillis() < putDownUntil) {
            // Just put down, the server has not taken it yet: nothing in the hand would stand where it is about to land.
            return null;
        }
        if (carried.getItem() != held) {
            turn.identity();
            scale = 1.0F;
            held = carried.getItem();
        }
        if (freeSlot(station, options, carried) == null) {
            return null;
        }
        if (!frozen || aimedRay == null) {
            aimedRay = ray;
        }
        PlacedShape shape = PlacedShape.of(carried, Minecraft.getInstance().level, options.blockModels(), options.framePose());
        PlacedPhysics physics = station.scene().physics();
        if (physics != null && options.physics().isPresent()) {
            return inHand(station, options, options.physics().get(), physics, shape, carried, dt);
        }
        ItemPose pose = options.stacking() ? onAnItem(station, options, shape, aimedRay) : null;
        if (pose == null) {
            Placing.Aim aim = Placing.aim(options, aimedRay.origin(), aimedRay.direction(), station.scene());
            if (aim != null) {
                pose = Placing.standing(options, shape, aim.surface(), aim.x(), aim.surface().height(), aim.z(), turn, scale, -1);
            }
        }
        return pose;
    }

    /**
     * The stack in the hand: it trails the cursor with the physics' {@code carry_lag}, floats {@code hover} over what is under it,
     * and rises at {@code climb} onto items where it fits on top. Where it does not, it keeps beside them and moves nothing. With
     * {@code hand_pushes} it is a solid thing instead: it rises onto items it comes near and, too fast for that, it is still low
     * when it meets one and the physics has it shove the item aside.
     */
    private ItemPose inHand(ClientStation station, PlacingOptions options, PlacingPhysics feel, PlacedPhysics physics, PlacedShape shape,
            ItemStack carried, float dt) {
        // Where the cursor wants it: on the item it is over the top of, or where it meets a surface.
        PlaceSurface surface = null;
        float x = 0.0F;
        float z = 0.0F;
        ItemPose below = null;
        int belowSlot = -1;
        if (options.stacking()) {
            SceneInstance.Hit hit = station.scene().pick(aimedRay.origin(), aimedRay.direction(), node -> node.placed() != null);
            ItemPose under = hit == null ? null : hit.node().placed().pose();
            if (under != null && Placing.isOnTop(under, hit.point().y)) {
                surface = station.scene().surfaceOf(options, under);
                x = under.x();
                z = under.z();
                if (surface != null) {
                    below = under;
                    belowSlot = hit.node().placed().slot().index();
                }
            }
        }
        if (surface == null) {
            Placing.Aim aim = Placing.aim(options, aimedRay.origin(), aimedRay.direction(), station.scene());
            if (aim == null) {
                return null;
            }
            surface = aim.surface();
            x = aim.x();
            z = aim.z();
        }
        // Aimed at the top of an item, it goes on it, made as small as it must be to fit there (as the preview shows); too
        // small for the scene's sizes, it stays beside the item instead.
        ItemPose wanted = below == null ? null : Placing.standing(options, shape, surface, x, below.top(), z, turn, scale, -1);
        if (wanted != null && wanted.scale() < options.scaleRange()[0] - 1.0e-4F) {
            wanted = null;
            below = null;
        }
        boolean stacked = wanted != null;
        if (wanted == null) {
            wanted = Placing.standing(options, shape, surface, x, surface.height(), z, turn, scale, -1);
        }
        Vector3f target = new Vector3f(wanted.x(), 0.0F, wanted.z());
        Vector3f extents = shape.extents(turn, options.itemSize() * wanted.scale());
        // It rises onto an item only where it fits on top under the surface's headroom; elsewhere it stays low.
        float ceiling = surface.height() + surface.liftRoom() - extents.y * 2.0F;
        boolean pushes = feel.handPushes();
        if (!pushes && !stacked) {
            // It disturbs nothing: on top of what is under it where it fits, else beside it, so it lands where it is seen.
            PlacedPhysics.Ground under = physics.groundUnder(surface, target.x - extents.x, target.x + extents.x, target.z - extents.z,
                    target.z + extents.z);
            float low = under.top() + feel.hover() <= ceiling ? under.top() : surface.height();
            // A flat icon is as deep as it will be once put down, so it does not land a hair inside its neighbour.
            float least = feel.uprightFlat() && shape.isFlat() ? feel.flatDepth() * 0.5F : 0.0F;
            target.set(physics.clearSpot(surface, target, Math.max(extents.x, least), Math.max(extents.z, least), low,
                    low + extents.y * 2.0F));
        }
        int index = options.surfaces().indexOf(surface);
        boolean arrived = handSurface == null;
        boolean changed = handSurface != null && handSurface != surface;
        if (changed && feel.hopTime() > 0.0F) {
            // Over to another surface (a shelf, a door's bin): it hops there in a short arc, touching nothing on the way.
            hopFrom.set(drawn);
            hopLeft = feel.hopTime();
        }
        if (arrived || changed || feel.carryLag() <= 0.0F) {
            // The grip starts right where it is wanted on the surface; on another one the hop shows the way there.
            glide.snap(target);
            if (arrived || changed) {
                base = surface.height();
                handSpeed.zero();
            }
        }
        handSurface = surface;
        glide.setHalfLife(feel.carryLag());
        Vector3fc at = glide.update(target, dt);
        if (arrived) {
            swingAngle.zero();
            swingSpeed.zero();
            handSpeed.set(glide.velocity());
        }
        // Passing by, it rises only over what it is right over (a hair less: what it keeps beside touches it), never onto that.
        float reach = pushes ? feel.reach() : -Math.min(BESIDE, 0.5F * Math.min(extents.x, extents.z));
        PlacedPhysics.Ground ground = stacked ? new PlacedPhysics.Ground(below.top(), belowSlot)
                : physics.groundUnder(surface, at.x() - extents.x - reach, at.x() + extents.x + reach, at.z() - extents.z - reach,
                        at.z() + extents.z + reach);
        base = Easing.approach(base, surface.height(), 0.04F, dt);
        // Stacked, it goes on top however little room is left over it for the hover.
        float rise = stacked || ground.top() + feel.hover() <= ceiling ? ground.top() - surface.height() : 0.0F;
        // Come in over an item, it is over it already, not inside it shoving it away.
        climbed = arrived || changed ? rise
                : rise > climbed ? Math.min(rise, climbed + feel.climb() * dt) : Math.max(rise, climbed - feel.climb() * FALL * dt);
        float bottom = Math.max(base, Math.min(base + climbed + feel.hover(), ceiling));
        // On the item once it has risen over it; the shadow falls on what it stands over. Aimed at an item's top, it is on it
        // at once: a click then puts it there, never inside it.
        int on = stacked ? belowSlot : ground.index() >= 0 && rise > 0.0F && climbed >= rise - 0.05F ? ground.index() : -1;
        float y = on >= 0 ? ground.top() : surface.height();
        if (stacked) {
            bottom = Math.max(bottom, y);
        }
        // Held by its top: it hangs from there, and its middle is where the swing has it.
        hang = feel.swing() ? swung(feel, dt, extents.y) : new Quaternionf();
        Vector3f middle = new Quaternionf(hang).transform(new Vector3f(0.0F, -extents.y, 0.0F)).add(at.x(), bottom + extents.y * 2.0F, at.z());
        Quaternionf rotation = new Quaternionf(hang).mul(turn).normalize();
        if (feel.swing()) {
            keepSwingInside(surface, shape.extents(rotation, options.itemSize() * wanted.scale()), middle);
        }
        // Pressed against something that does not give way (an item against a wall), it stops beside it, not in it.
        Vector3f stuck = physics.handStuck();
        if (pushes && stuck.lengthSquared() > 1.0e-6F) {
            nudge.add(stuck.x, 0.0F, stuck.z);
        } else {
            nudge.mul((float) Math.pow(0.5, dt / 0.08));
        }
        if (arrived || changed || !pushes) {
            nudge.zero();
        }
        middle.add(nudge.x, 0.0F, nudge.z);
        ItemPose pose = new ItemPose(middle.x, y, middle.z, Math.max(0.0F, middle.y - y), rotation, wanted.scale(), on);
        letGo = released(options, feel, shape, at, bottom, y, wanted.scale(), on);
        // Where it is drawn, in station space: the start of the next hop, and the hop's way now.
        Vector3f here = new Vector3f(middle);
        org.joml.Matrix4f move = station.scene().surfaceMove(index);
        if (move != null) {
            move.transformPosition(here);
        }
        if (hopLeft >= 0.0F) {
            hopLeft -= dt;
            float u = Mth.clamp(1.0F - hopLeft / feel.hopTime(), 0.0F, 1.0F);
            float eased = u * u * (3.0F - 2.0F * u);
            hopAt.set(hopFrom).lerp(here, eased).add(0.0F, HOP_ARC * Mth.sin(u * Mth.PI), 0.0F);
            drawn.set(hopAt);
            if (u >= 1.0F) {
                hopLeft = -1.0F;
            }
            // In the air between the surfaces it touches nothing.
            physics.carry(ItemStack.EMPTY, null, null, null);
        } else {
            drawn.set(here);
            // Solid only with hand_pushes; otherwise it passes things by and touches none.
            physics.carry(pushes ? carried : ItemStack.EMPTY, shape, pose, surface);
        }
        gliding = true;
        return pose;
    }

    /**
     * The stack let go of now: right under the grip, on what the grip is over, with the swing's tilt (a rigid item's cut below
     * its own tipping angle, so the swing alone never knocks it over; a flat item rocks back anyway).
     */
    private ItemPose released(PlacingOptions options, PlacingPhysics feel, PlacedShape shape, Vector3fc grip, float bottom, float y,
            float size, int on) {
        Quaternionf tilt = new Quaternionf(hang);
        float angle = 2.0F * (float) Math.acos(Math.min(1.0F, Math.abs(tilt.w())));
        boolean flat = feel.uprightFlat() && shape.isFlat();
        if (!flat && angle > 1.0e-4F) {
            Vector3f upright = shape.extents(turn, options.itemSize() * size);
            float tipping = (float) Math.atan2(Math.min(upright.x, upright.z), Math.max(1.0e-3F, upright.y));
            float most = RELEASE_TILT * tipping;
            if (angle > most) {
                tilt = new Quaternionf().slerp(tilt, most / angle);
            }
        }
        Quaternionf rotation = tilt.mul(turn).normalize();
        float half = shape.extents(rotation, options.itemSize() * size).y;
        return new ItemPose(grip.x(), y, grip.z(), Math.max(0.0F, bottom - y + half), rotation, size, on);
    }

    /**
     * Swings the stack in the hand on as a pendulum hung from its top, pushed by how the hand speeds up and slows down: moved
     * right, its bottom stays behind; stopped, it swings on to the right, then rocks to and fro less and less.
     */
    private Quaternionf swung(PlacingPhysics feel, float dt, float halfHeight) {
        Vector3fc speed = glide.velocity();
        float ax = dt > 0.0F ? (speed.x() - handSpeed.x) / dt : 0.0F;
        float az = dt > 0.0F ? (speed.z() - handSpeed.z) / dt : 0.0F;
        handSpeed.set(speed);
        float length = halfHeight + SWING_GIVE;
        float stiffness = SWING_GRAVITY / length;
        float omega = (float) Math.sqrt(stiffness);
        // A still hand calms it: it hangs still within swing_calm, so the player who stops to aim has a still item to put down.
        boolean still = Math.sqrt(speed.x() * speed.x() + speed.z() * speed.z()) < SWING_STILL;
        calm = Mth.clamp(calm + (still ? dt : -dt) / SWING_SETTLE, 0.0F, 1.0F);
        float calmRatio = Math.min(1.0F, 3.0F / (omega * feel.swingCalm()));
        float damping = 2.0F * Mth.lerp(calm, SWING_DAMPING, Math.max(SWING_DAMPING, calmRatio)) * omega;
        int steps = Math.max(1, (int) Math.ceil(dt / SWING_STEP));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            swingSpeed.x += (-stiffness * swingAngle.x - damping * swingSpeed.x - ax / length) * h;
            swingSpeed.y += (-stiffness * swingAngle.y - damping * swingSpeed.y - az / length) * h;
            swingAngle.x += swingSpeed.x * h;
            swingAngle.y += swingSpeed.y * h;
        }
        float angle = swingAngle.length();
        float widest = (float) Math.toRadians(feel.swingAngle());
        if (angle > widest) {
            swingAngle.mul(widest / angle);
            angle = widest;
        }
        if (angle < 1.0e-5F) {
            return new Quaternionf();
        }
        // The bottom goes out along (x, z): a turn about the level axis square to that.
        return new Quaternionf().fromAxisAngleRad(-swingAngle.y / angle, 0.0F, swingAngle.x / angle, angle);
    }

    /**
     * A swing that takes the stack in the hand into a wall of its surface bounces back off it; whatever is still out is held
     * inside, so it never shows through the wall.
     */
    private void keepSwingInside(PlaceSurface surface, Vector3f extents, Vector3f middle) {
        float lowX = surface.from().x() + extents.x;
        float highX = surface.to().x() - extents.x;
        float lowZ = surface.from().y() + extents.z;
        float highZ = surface.to().y() - extents.z;
        if (middle.x > highX && swingSpeed.x > 0.0F || middle.x < lowX && swingSpeed.x < 0.0F) {
            swingSpeed.x *= -SWING_BOUNCE;
        }
        if (middle.z > highZ && swingSpeed.y > 0.0F || middle.z < lowZ && swingSpeed.y < 0.0F) {
            swingSpeed.y *= -SWING_BOUNCE;
        }
        middle.x = lowX > highX ? (surface.from().x() + surface.to().x()) / 2.0F : Mth.clamp(middle.x, lowX, highX);
        middle.z = lowZ > highZ ? (surface.from().y() + surface.to().y()) / 2.0F : Mth.clamp(middle.z, lowZ, highZ);
    }

    private ItemPose onAnItem(ClientStation station, PlacingOptions options, PlacedShape shape, FocusInput.StationRay ray) {
        SceneInstance.Hit hit = station.scene().pick(ray.origin(), ray.direction(), node -> node.placed() != null);
        ItemPose below = hit == null ? null : hit.node().placed().pose();
        if (below == null || !Placing.isOnTop(below, hit.point().y)) {
            return null;
        }
        PlaceSurface surface = station.scene().surfaceOf(options, below);
        if (surface == null) {
            return null;
        }
        float room = surface.height() + surface.clearance() - below.top();
        if (Placing.fittingScale(options, shape, turn, scale, room) < options.scaleRange()[0]) {
            return null;
        }
        return Placing.standing(options, shape, surface, below.x(), below.top(), below.z(), turn, scale, hit.node().placed().slot().index());
    }

    /** The first slot of the group that is free and takes {@code stack}, or null. */
    private static SlotRef freeSlot(ClientStation station, PlacingOptions options, ItemStack stack) {
        SlotGroup group = station.type().schema().slotGroups().get(options.group());
        if (group == null || !group.rules().accepts().test(stack)) {
            return null;
        }
        for (int i = 0; i < group.size(); i++) {
            if (station.state().getItem(group.ref(i)).isEmpty()) {
                return group.ref(i);
            }
        }
        return null;
    }

    /** Puts the stack down where the preview shows it; false when there is no place. */
    boolean place(ClientStation station, ItemStack carried) {
        SlotRef slot = preview == null ? null : freeSlot(station, options(station), carried);
        ItemPose pose = gliding && letGo != null ? letGo : preview;
        PlacedPhysics physics = station.scene().physics();
        if (physics != null && slot != null && gliding) {
            // A moving hand lets it slide on a little where it lands.
            Vector3fc speed = glide.velocity();
            physics.toss(slot.index(), speed.x(), speed.z());
        }
        if (slot == null || !station.perform(new StationAction.Place(slot, pose))) {
            return false;
        }
        // Let go of: from here it falls by itself, and the hand is out of its way.
        putDown = carried;
        putDownUntil = Util.getMillis() + PUT_DOWN_MILLIS;
        preview = null;
        gliding = false;
        handSurface = null;
        if (station.scene().physics() != null) {
            station.scene().physics().carry(ItemStack.EMPTY, null, null, null);
        }
        return true;
    }

    /** One notch of the wheel: turns about the vertical, with {@code tilt} about the view's sideways axis; with {@code size}, grows. */
    private Quaternionf notched(PlacingOptions options, Quaternionfc rotation, double amount, boolean tilt, Vector3fc right) {
        float angle = (float) Math.toRadians(options.turnStep()) * (float) Math.signum(amount);
        Quaternionf step = tilt ? new Quaternionf().rotateAxis(angle, right.x(), right.y(), right.z()) : new Quaternionf().rotateY(angle);
        return step.mul(rotation).normalize();
    }

    private static float grown(PlacingOptions options, float value, double amount) {
        float next = value + options.scaleStep() * (float) Math.signum(amount);
        return Mth.clamp(Math.round(next / options.scaleStep()) * options.scaleStep(), options.scaleRange()[0], options.scaleRange()[1]);
    }

    /** The wheel on the stack in the hand. */
    void scrollCarried(PlacingOptions options, double amount, boolean tilt, boolean size, Vector3fc right) {
        if (size) {
            scale = grown(options, scale, amount);
        } else {
            turn.set(notched(options, turn, amount, tilt, right));
        }
    }

    /** The wheel on an item that stands. */
    void scrollPlaced(ClientStation station, NodeInstance node, double amount, boolean tilt, boolean size, Vector3fc right) {
        PlacingOptions options = options(station);
        ItemPose pose = node.placed().pose();
        if (pose == null) {
            return;
        }
        Quaternionfc rotation = size ? pose.rotation() : notched(options, pose.rotation(), amount, tilt, right);
        apply(station, node.placed(), rotation, size ? grown(options, pose.scale(), amount) : pose.scale(), true);
    }

    /** A drag turning the stack in the hand: sideways about the vertical, up and down about the view's sideways axis. */
    void dragCarried(PlacingOptions options, float dx, float dy, Vector3fc right) {
        turn.set(dragged(options, turn, dx, dy, right));
    }

    /** A drag turning an item that stands; {@code send} tells the server, otherwise it only moves here for now. */
    void dragPlaced(ClientStation station, NodeInstance node, float dx, float dy, Vector3fc right, boolean send) {
        ItemPose pose = node.placed().pose();
        if (pose != null) {
            apply(station, node.placed(), dragged(options(station), pose.rotation(), dx, dy, right), pose.scale(), send);
        }
    }

    /** Sends where the item ended up. */
    void commit(ClientStation station, NodeInstance node) {
        ItemPose pose = node.placed().pose();
        if (pose != null) {
            apply(station, node.placed(), pose.rotation(), pose.scale(), true);
        }
    }

    private static Quaternionf dragged(PlacingOptions options, Quaternionfc rotation, float dx, float dy, Vector3fc right) {
        float power = options.physics().map(PlacingPhysics::turnPower).orElse(1.0F);
        float speed = (float) Math.toRadians(options.turnSpeed() * power);
        return new Quaternionf().rotateY(dx * speed).rotateAxis(dy * speed, right.x(), right.y(), right.z()).mul(rotation).normalize();
    }

    /** Gives a standing item a new turn and size: it settles on its lowest point, and what stands on it goes up or down with it. */
    private static void apply(ClientStation station, PlacedItem item, Quaternionfc rotation, float scale, boolean send) {
        PlacingOptions options = item.options();
        ItemPose before = item.pose();
        PlaceSurface surface = station.scene() != null ? station.scene().surfaceOf(options, before) : Placing.surfaceOf(options, before);
        ItemPose after = surface != null
                ? Placing.standing(options, item.shape(), surface, before.x(), before.y(), before.z(), rotation, scale, before.on())
                : new ItemPose(before.x(), before.y(), before.z(), item.shape().extents(rotation, options.itemSize() * scale).y, rotation, scale, before.on());
        set(station, item.slot(), after, send);
        carryAbove(station, options, item.slot(), after.top() - before.top(), send);
    }

    private static void carryAbove(ClientStation station, PlacingOptions options, SlotRef below, float pixels, boolean send) {
        if (Math.abs(pixels) < 1.0e-4F) {
            return;
        }
        SlotGroup group = station.type().schema().slotGroups().get(below.group());
        for (int i = 0; i < group.size(); i++) {
            ItemPose pose = station.state().getPose(group.ref(i));
            if (pose != null && pose.on() == below.index() && !station.state().getItem(group.ref(i)).isEmpty()) {
                set(station, group.ref(i), pose.raised(pixels), send);
                carryAbove(station, options, group.ref(i), pixels, send);
            }
        }
    }

    private static void set(ClientStation station, SlotRef slot, ItemPose pose, boolean send) {
        if (send) {
            station.perform(new StationAction.Pose(slot, pose));
        } else {
            station.state().setPose(slot, pose);
        }
        if (station.scene() != null && station.scene().physics() != null) {
            station.scene().physics().steer(slot.index(), pose);
        }
    }
}
