package com.pockyl.diegesis.impl.client.mesh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.MeshShape;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Items whose look is a {@link MeshShape} made from their data. Applied onto {@link ItemStackRenderState}
 * via {@code ItemRendererMixin} so inventories, frames, hands and shelves all show the procedural mesh.
 */
public final class ItemShapes {
    private static final int KEPT_SHAPES = 256;
    private static final Map<Item, Entry> ITEMS = new ConcurrentHashMap<>();

    private ItemShapes() {
    }

    public static void register(Item item, Function<ItemStack, MeshShape> shape, Identifier texture) {
        ITEMS.put(item, new Entry(shape, texture));
    }

    /** Replaces the first layer's quads of {@code state} when {@code stack} has a procedural mesh shape. */
    public static void apply(ItemStack stack, ItemStackRenderState state) {
        if (ITEMS.isEmpty() || stack.isEmpty()) {
            return;
        }
        Entry entry = ITEMS.get(stack.getItem());
        if (entry == null) {
            return;
        }
        ItemQuads quads = entry.quads(stack);
        if (state.activeLayerCount > 0) {
            ItemStackRenderState.LayerRenderState layer = state.layers[0];
            layer.setQuads(quads);
            layer.setUsesBlockLight(true);
            state.activeLayerCount = 1;
        } else {
            ItemStackRenderState.LayerRenderState layer = state.newLayer();
            layer.setQuads(quads);
            layer.setUsesBlockLight(true);
        }
    }

    /** Models are new after a resource reload, and the sprites with them. */
    public static void clearCache() {
        ITEMS.values().forEach(entry -> entry.baked.clear());
    }

    private static final class Entry {
        private final Function<ItemStack, MeshShape> shape;
        private final Identifier texture;
        private final Map<DataComponentPatch, ItemQuads> baked = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<DataComponentPatch, ItemQuads> eldest) {
                return size() > KEPT_SHAPES;
            }
        };

        Entry(Function<ItemStack, MeshShape> shape, Identifier texture) {
            this.shape = shape;
            this.texture = texture;
        }

        ItemQuads quads(ItemStack stack) {
            DataComponentPatch key = stack.getComponentsPatch();
            ItemQuads found = baked.get(key);
            if (found == null) {
                TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(texture);
                found = ItemQuads.split(shape.apply(stack).mesh().quads(sprite));
                baked.put(key, found);
            }
            return found;
        }
    }
}
