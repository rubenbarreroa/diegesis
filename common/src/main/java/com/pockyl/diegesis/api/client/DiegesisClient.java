package com.pockyl.diegesis.api.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.impl.client.ClientRegistry;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.ClientStations;
import com.pockyl.diegesis.impl.client.debug.DebugView;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.fx.LiquidRenderer;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;

import java.util.Optional;

/**
 * The library's entry point for client code. Call the {@code register} methods from your client setup; never from
 * common code (a dedicated server has no client classes).
 */
public final class DiegesisClient {
    private DiegesisClient() {
    }

    /** Client code (animations, sounds, effects) for the stations of a type. */
    public static void registerBehavior(Identifier station, StationBehavior.Factory factory) {
        ClientRegistry.registerBehavior(station, factory);
    }

    /**
     * A scene built in code instead of a JSON file. A JSON file with the same id
     * ({@code assets/<namespace>/diegesis/scenes/<path>.json}) still wins, so resource packs can restyle it.
     */
    public static void registerScene(Identifier id, SceneDefinition scene) {
        ClientRegistry.registerScene(id, scene);
    }

    /**
     * Loads a block model that scenes use but that is not under {@code models/diegesis/} (every model there is loaded
     * by itself). {@code model} is a model id: {@code mymod:block/flask} for {@code assets/mymod/models/block/flask.json}.
     */
    public static void registerModel(Identifier model) {
        ClientRegistry.registerModel(model);
    }

    /**
     * Gives an item a look made from its data: in inventories, hands, frames and on shelves it is the shape {@code shape} makes
     * for the stack, with {@code texture} (a sprite of the block atlas) on it. The shape stands on the middle of the block's floor,
     * in pixels. The item still needs a model file, for its display transforms; shapes are kept per item data.
     */
    public static void registerItemShape(net.minecraft.world.item.Item item, java.util.function.Function<net.minecraft.world.item.ItemStack, MeshShape> shape,
            Identifier texture) {
        com.pockyl.diegesis.impl.client.mesh.ItemShapes.register(item, shape, texture);
    }

    /** A scene particle style, for emitters, effects and {@link StationView#spawnParticles}. */
    public static void registerParticle(Identifier id, ParticleStyle style) {
        ClientRegistry.registerParticle(id, style);
    }

    /** How a liquid looks in tanks (its id is a fluid's or one the mod made up for a reagent). */
    public static void registerLiquid(Identifier id, LiquidLook look) {
        ClientRegistry.registerLiquid(id, look);
    }

    /** An effect the stations' logic can play with {@code StationContext.effect(id, ...)}. */
    /** The colour a mixture is drawn in (the liquids' looks blended by amount), ARGB: for particles of the same colour. */
    public static int liquidColor(TankContents contents) {
        return LiquidRenderer.look(contents).color();
    }

    public static void registerEffect(Identifier id, EffectHandler handler) {
        ClientRegistry.registerEffect(id, handler);
    }

    /**
     * Shows or hides the debug view: the hit box of every node (coloured by role, with its id), the laid-out inventory
     * slots, the work plane and the camera target. Also on while vanilla hit boxes are (F3+B) and with the client
     * command {@code /diegesis debug}.
     */
    public static void setDebugView(boolean on) {
        DebugView.set(on);
    }

    public static boolean isDebugView() {
        return DebugView.isOn();
    }

    /** Whether the local player is working at a station (the camera is at it). */
    public static boolean isFocused() {
        return FocusController.get().isActive();
    }

    /**
     * How far the camera has flown over from one station to a neighbour ({@code camera.neighbors}), eased, 0 to 1; 1 when it is not
     * flying over. Below 1 while the station left hears {@code onClose}: it can hand what floats in the air over to the next one,
     * which is {@link #focusedStation()} from then on, also before the server has let the player in.
     */
    public static float goingOver() {
        return FocusController.get().goingOver();
    }

    /**
     * How far the camera has come from the player's view to the station worked at, eased, 0 to 1: rising while it flies in, falling
     * while it flies back after {@code onClose} ({@link #focusedStation()} is still the station left then), 1 at the station and
     * while going over to a neighbour, 0 when nobody works at one. What comes with a station, such as a book that hangs by it, can
     * appear and go with this, in step with the camera.
     */
    public static float arrival() {
        return FocusController.get().arrival();
    }

    /**
     * Screens that may open over a station without ending it, such as a recipe viewer's page opened by a key over an item: the
     * station, its camera and a menu station's menu wait under it, and the player is back at the station when it returns to the
     * station's screen. Any other screen ends the work at the station, as before.
     */
    public static void registerChildScreen(java.util.function.Predicate<net.minecraft.client.gui.screens.Screen> test) {
        com.pockyl.diegesis.impl.client.ClientRegistry.registerChildScreen(test);
    }

    /**
     * Where a world point is on the screen, in GUI pixels, as the last frame was drawn; empty when it is behind the camera. For laying
     * other mods' overlays round what a station shows.
     */
    public static Optional<org.joml.Vector2f> toScreen(net.minecraft.world.phys.Vec3 world) {
        return Optional.ofNullable(com.pockyl.diegesis.impl.client.focus.Projector.toScreen(world));
    }

    /** The class of a station's screen, for mods that register handlers by screen class (a recipe viewer's overlay). */
    public static Class<? extends net.minecraft.client.gui.screens.Screen> stationScreenClass() {
        return com.pockyl.diegesis.impl.client.focus.FocusScreen.class;
    }

    /** Whether {@code screen} is the screen of a station being worked at: a parent a child screen returns to. */
    public static boolean isStationScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof com.pockyl.diegesis.impl.client.focus.FocusScreen;
    }

    /**
     * The stack under the cursor at the station the local player works at: in an inventory slot, a station slot or an output; empty
     * elsewhere. For recipe viewers' keys over an item.
     */
    public static net.minecraft.world.item.ItemStack hoveredStack() {
        return com.pockyl.diegesis.impl.client.focus.FocusScreen.hoveredStack();
    }

    /** The station the local player works at. */
    public static Optional<StationView> focusedStation() {
        return Optional.ofNullable(FocusController.get().station());
    }

    /**
     * Buffers for a {@link NodePainter} that multiply an ARGB colour into what is drawn through them; an alpha below 255 fades it. Items
     * and models cannot be tinted otherwise. Block items (a carrot, a mushroom) ask for the block sheet's solid or cutout type, which
     * ignores alpha: through these buffers they get the see-through item type, so they fade like any other icon. Other models must
     * be drawn with a see-through render type to fade.
     */
    public static com.pockyl.diegesis.api.client.DiegesisBuffers tinted(com.pockyl.diegesis.api.client.DiegesisBuffers buffers, int argb) {
        boolean fading = argb >>> 24 < 255;
        return type -> {
            net.minecraft.client.renderer.rendertype.RenderType drawn = fading ? seeThrough(type) : type;
            if (drawn != type) {
                com.pockyl.diegesis.impl.client.render.KeptTypes.keep(buffers, type);
            }
            return new com.pockyl.diegesis.impl.client.render.TintedConsumer(buffers.getBuffer(drawn), argb);
        };
    }

    private static net.minecraft.client.renderer.rendertype.RenderType seeThrough(net.minecraft.client.renderer.rendertype.RenderType type) {
        if (type == net.minecraft.client.renderer.Sheets.cutoutItemSheet()) {
            return net.minecraft.client.renderer.Sheets.translucentItemSheet();
        }
        if (type == net.minecraft.client.renderer.Sheets.cutoutBlockItemSheet()
                || type == net.minecraft.client.renderer.rendertype.RenderTypes.cutoutMovingBlock()
                || type == net.minecraft.client.renderer.rendertype.RenderTypes.solidMovingBlock()) {
            return net.minecraft.client.renderer.Sheets.translucentBlockItemSheet();
        }
        return type;
    }

    /** A station shown on this client at {@code pos}. */
    public static Optional<StationView> station(BlockPos pos) {
        return Optional.ofNullable(ClientStations.get().at(pos));
    }

    /**
     * Whether the scene of the station at {@code pos} is drawn in this frame: the station is shown and the camera is near enough.
     * A station is kept a little farther than its scene is drawn, so a block entity renderer that a scene stands in for should
     * stop exactly when this is true, or the block is missing in between. Asked while the level is drawn, before the scenes.
     */
    public static boolean isSceneDrawn(BlockPos pos) {
        ClientStation station = ClientStations.get().at(pos);
        return station != null && SceneRenderer.isDrawn(station, Minecraft.getInstance().gameRenderer.mainCamera().position());
    }
}
