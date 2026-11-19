package com.pockyl.diegesis.api.scene;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

import java.util.Optional;

/**
 * Keeps the player's inventory in a drawer at a side of the screen: an icon sits at that side, and pointing at the side
 * slides the inventory out over the scene. While it is out it comes first: what is under it in the scene is neither
 * hovered nor clicked. It slides back when the cursor leaves, unless the player pinned it with a click on the icon or
 * Tab. A stack on the cursor opens it the same way, an object in the hand or a control being turned does not.
 *
 * @param side        the side of the screen
 * @param align       where along that side it sits: 0 at the start (left or top), 0.5 in the middle, 1 at the end
 * @param icon        the item drawn on the icon
 * @param iconTexture a 16x16 texture drawn on the icon instead of the item
 * @param hoverZone   how wide (GUI pixels) the strip along the side is that opens the drawer; 0: only the icon does
 * @param openDelay   seconds the cursor must stay there, so that passing by opens nothing
 * @param closeDelay  seconds the drawer waits after the cursor left, so that overshooting closes nothing
 * @param slideTime   seconds it takes to slide
 * @param margin      gap (GUI pixels) between the open drawer and the side of the screen
 * @param style       the vanilla panel, or plain dark see-through squares
 * @param background  ARGB background of the {@code minimal} style
 * @param slotColor   ARGB colour of a slot in the {@code minimal} style
 * @param alwaysOpen  the drawer slides out when the station opens and stays out, without an icon: for stations that are
 *                    worked from the inventory all the time. Escape leaves the station at once
 * @param compact     only the stacks the station can use, packed in rows of up to nine and the drawer no bigger than them
 *                    ({@code StationView.setInventoryFilter}); with a stack on the cursor one empty slot to put it in. It
 *                    changes shape only while the cursor is away from it, so nothing moves under the hand
 * @param carrySize   the size (pixels) of a stack taken from the drawer while it is carried over the scene: as big as the
 *                    station's own items look. Without it the stack keeps the size of the last one carried
 * @param layout      {@code rows}: the drawer as described above. {@code hotbar}: the hotbar stays where the HUD has it and takes
 *                    clicks, the off hand beside it at a menu station; over it rows of the other stacks the station can use
 *                    ({@code StationView.setInventoryFilter}), in the order they had when the station opened, with one empty
 *                    slot always among them. Side, align, icon, style and compact do not apply to it
 */
public record InventoryDrawer(Side side, float align, Item icon, Optional<Identifier> iconTexture, float hoverZone, float openDelay,
        float closeDelay, float slideTime, float margin, Style style, int background, int slotColor, boolean alwaysOpen, boolean compact,
        Optional<Float> carrySize, Layout layout) {
    public static final InventoryDrawer DEFAULT = new InventoryDrawer(Side.RIGHT, 0.5F, Items.BUNDLE, Optional.empty(), 10.0F, 0.08F, 0.35F,
            0.18F, 4.0F, Style.PANEL, 0xC0101216, 0x66000000, false, false, Optional.empty(), Layout.ROWS);

    public static final Codec<InventoryDrawer> CODEC = StrictCodecs.record(instance -> instance.group(
            StrictCodecs.optional(Side.CODEC, "side", DEFAULT.side).forGetter(InventoryDrawer::side),
            StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "align", DEFAULT.align).forGetter(InventoryDrawer::align),
            StrictCodecs.optional(BuiltInRegistries.ITEM.byNameCodec(), "icon", DEFAULT.icon).forGetter(InventoryDrawer::icon),
            StrictCodecs.optional(Identifier.CODEC, "icon_texture").forGetter(InventoryDrawer::iconTexture),
            StrictCodecs.optional(Codec.floatRange(0.0F, 64.0F), "hover_zone", DEFAULT.hoverZone).forGetter(InventoryDrawer::hoverZone),
            StrictCodecs.optional(Codec.floatRange(0.0F, 2.0F), "open_delay", DEFAULT.openDelay).forGetter(InventoryDrawer::openDelay),
            StrictCodecs.optional(Codec.floatRange(0.0F, 5.0F), "close_delay", DEFAULT.closeDelay).forGetter(InventoryDrawer::closeDelay),
            StrictCodecs.optional(Codec.floatRange(0.0F, 2.0F), "slide_time", DEFAULT.slideTime).forGetter(InventoryDrawer::slideTime),
            StrictCodecs.optional(Codec.floatRange(0.0F, 64.0F), "margin", DEFAULT.margin).forGetter(InventoryDrawer::margin),
            StrictCodecs.optional(Style.CODEC, "style", DEFAULT.style).forGetter(InventoryDrawer::style),
            StrictCodecs.optional(Visual.COLOR, "background", DEFAULT.background).forGetter(InventoryDrawer::background),
            StrictCodecs.optional(Visual.COLOR, "slot_color", DEFAULT.slotColor).forGetter(InventoryDrawer::slotColor),
            StrictCodecs.optional(Codec.BOOL, "always_open", DEFAULT.alwaysOpen).forGetter(InventoryDrawer::alwaysOpen),
            StrictCodecs.optional(Codec.BOOL, "compact", DEFAULT.compact).forGetter(InventoryDrawer::compact),
            StrictCodecs.optional(Codec.floatRange(1.0F, 64.0F), "carry_size").forGetter(InventoryDrawer::carrySize),
            StrictCodecs.optional(Layout.CODEC, "layout", DEFAULT.layout).forGetter(InventoryDrawer::layout)
    ).apply(instance, InventoryDrawer::new));

    /** A drawer laid out in rows, as before there were layouts. */
    public InventoryDrawer(Side side, float align, Item icon, Optional<Identifier> iconTexture, float hoverZone, float openDelay,
            float closeDelay, float slideTime, float margin, Style style, int background, int slotColor, boolean alwaysOpen, boolean compact,
            Optional<Float> carrySize) {
        this(side, align, icon, iconTexture, hoverZone, openDelay, closeDelay, slideTime, margin, style, background, slotColor, alwaysOpen,
                compact, carrySize, Layout.ROWS);
    }

    /** A drawer whose stacks keep the size of the last one carried. */
    public InventoryDrawer(Side side, float align, Item icon, Optional<Identifier> iconTexture, float hoverZone, float openDelay,
            float closeDelay, float slideTime, float margin, Style style, int background, int slotColor, boolean alwaysOpen, boolean compact) {
        this(side, align, icon, iconTexture, hoverZone, openDelay, closeDelay, slideTime, margin, style, background, slotColor, alwaysOpen,
                compact, Optional.empty(), Layout.ROWS);
    }

    /** The default drawer at {@code side}, for scenes built in code. */
    public static InventoryDrawer at(Side side) {
        return DEFAULT.withSide(side);
    }

    public InventoryDrawer withSide(Side value) {
        return new InventoryDrawer(value, align, icon, iconTexture, hoverZone, openDelay, closeDelay, slideTime, margin, style, background,
                slotColor, alwaysOpen, compact, carrySize, layout);
    }

    /** The same drawer with another item on its icon. */
    public InventoryDrawer withIcon(Item value) {
        return new InventoryDrawer(side, align, value, Optional.empty(), hoverZone, openDelay, closeDelay, slideTime, margin, style, background,
                slotColor, alwaysOpen, compact, carrySize, layout);
    }

    /** The same drawer with a 16x16 texture on its icon. */
    public InventoryDrawer withIconTexture(Identifier value) {
        return new InventoryDrawer(side, align, icon, Optional.of(value), hoverZone, openDelay, closeDelay, slideTime, margin, style, background,
                slotColor, alwaysOpen, compact, carrySize, layout);
    }

    public InventoryDrawer withStyle(Style value) {
        return new InventoryDrawer(side, align, icon, iconTexture, hoverZone, openDelay, closeDelay, slideTime, margin, value, background,
                slotColor, alwaysOpen, compact, carrySize, layout);
    }

    /** The same drawer showing only the stacks the station can use. */
    public InventoryDrawer withCompact(boolean value) {
        return new InventoryDrawer(side, align, icon, iconTexture, hoverZone, openDelay, closeDelay, slideTime, margin, style, background,
                slotColor, alwaysOpen, value, carrySize, layout);
    }

    /** The same drawer carrying its stacks over the scene at this size (pixels). */
    public InventoryDrawer withCarrySize(float value) {
        return new InventoryDrawer(side, align, icon, iconTexture, hoverZone, openDelay, closeDelay, slideTime, margin, style, background,
                slotColor, alwaysOpen, compact, Optional.of(value), layout);
    }

    /** The same drawer laid out another way. */
    public InventoryDrawer withLayout(Layout value) {
        return new InventoryDrawer(side, align, icon, iconTexture, hoverZone, openDelay, closeDelay, slideTime, margin, style, background,
                slotColor, alwaysOpen, compact, carrySize, value);
    }

    /** Whether the hotbar stays in place and the drawer builds on it. */
    public boolean keepsHotbar() {
        return layout == Layout.HOTBAR;
    }

    /** A side of the screen. */
    public enum Side implements StringRepresentable {
        LEFT("left"),
        RIGHT("right"),
        TOP("top"),
        BOTTOM("bottom");

        public static final Codec<Side> CODEC = StringRepresentable.fromEnum(Side::values);

        private final String name;

        Side(String name) {
            this.name = name;
        }

        /** Whether the drawer slides sideways. */
        public boolean isHorizontal() {
            return this == LEFT || this == RIGHT;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** How the drawer lays the inventory out. */
    public enum Layout implements StringRepresentable {
        /** Rows at a side of the screen. */
        ROWS("rows"),
        /** The hotbar where the HUD has it, the stacks the station can use over it. */
        HOTBAR("hotbar");

        public static final Codec<Layout> CODEC = StringRepresentable.fromEnum(Layout::values);

        private final String name;

        Layout(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** How the drawer looks. */
    public enum Style implements StringRepresentable {
        /** The vanilla inventory panel. */
        PANEL("panel"),
        /** Dark see-through squares without a frame. */
        MINIMAL("minimal");

        public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);

        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
