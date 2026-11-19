package com.pockyl.diegesis.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.ClientPlatform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricClientPlatform implements ClientPlatform {
    static final Map<Identifier, ExtraModelKey<BlockStateModel>> EXTRA_MODELS = new ConcurrentHashMap<>();

    static ExtraModelKey<BlockStateModel> keyFor(Identifier id) {
        return EXTRA_MODELS.computeIfAbsent(id, k -> ExtraModelKey.create(k::toString));
    }

    @Override
    public boolean serverHasChannel() {
        return Minecraft.getInstance().getConnection() != null && FabricNetwork.serverHasChannel();
    }

    @Override
    public void sendToServer(Packets.ClientPacket packet) {
        if (serverHasChannel()) {
            FabricNetwork.sendToServer(packet);
        }
    }

    @Override
    public BlockStateModel getModel(Identifier id) {
        ModelManager manager = Minecraft.getInstance().getModelManager();
        ExtraModelKey<BlockStateModel> key = EXTRA_MODELS.get(id);
        if (key != null) {
            BlockStateModel model = manager.getModel(key);
            if (model != null) {
                return model;
            }
        }
        return manager.getBlockStateModelSet().missingModel();
    }
}
