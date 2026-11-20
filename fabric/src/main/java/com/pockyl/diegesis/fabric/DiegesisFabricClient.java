package com.pockyl.diegesis.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.diegesis.impl.StationRegistry;
import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;
import com.pockyl.diegesis.impl.client.debug.DebugView;
import com.pockyl.diegesis.impl.client.scene.SceneLibrary;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

public final class DiegesisFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DiegesisClientRuntime.init();
        FabricNetwork.registerClient();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("controlify")) {
            com.pockyl.diegesis.fabric.compat.ControlifyCompat.init();
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> DiegesisClientRuntime.tick());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommands.literal("diegesis").then(ClientCommands.literal("debug").executes(command -> {
                    DebugView.toggle();
                    return 1;
                }))));
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(SceneLibrary.ID, SceneLibrary.INSTANCE);
        PreparableModelLoadingPlugin.register(
                (state, executor) -> CompletableFuture.supplyAsync(() -> SceneLibrary.modelsToLoad(state.resourceManager()), executor),
                (Set<Identifier> models, ModelLoadingPlugin.Context context) -> {
                    for (Identifier modelId : models) {
                        context.addModel(FabricClientPlatform.keyFor(modelId), SimpleUnbakedExtraModel.blockStateModel(modelId));
                    }
                    Set<Block> hideable = new HashSet<>();
                    for (StationType type : StationRegistry.all()) {
                        if (type.hidesBlock()) {
                            Stream.concat(type.blocks().stream(), type.lateBlocks().stream()).forEach(hideable::add);
                        }
                    }
                    context.modifyBlockModelAfterBake().register((model, modify) -> model != null && modify.state() != null
                            && hideable.contains(modify.state().getBlock()) ? new HideableFabricModel(model) : model);
                });
    }
}
