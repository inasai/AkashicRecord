package com.akashicrecords.network;

import com.akashicrecords.data.AkashicConfig;
import com.akashicrecords.data.KnowledgeData;
import com.akashicrecords.data.ModAttachments;
import com.akashicrecords.data.StorageData;
import com.akashicrecords.knowledge.KnowledgeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SyncValuesPayload.TYPE, SyncValuesPayload.CODEC, SyncValuesPayload::handle);
        registrar.playToClient(SyncKnowledgePayload.TYPE, SyncKnowledgePayload.CODEC, SyncKnowledgePayload::handle);
        registrar.playToServer(TakeItemPayload.TYPE, TakeItemPayload.CODEC, TakeItemPayload::handle);
        registrar.playToServer(LearnItemPayload.TYPE, LearnItemPayload.CODEC, LearnItemPayload::handle);
        registrar.playToClient(SyncStoragePayload.TYPE, SyncStoragePayload.CODEC, SyncStoragePayload::handle);
        registrar.playToServer(StorageDepositPayload.TYPE, StorageDepositPayload.CODEC, StorageDepositPayload::handle);
        registrar.playToServer(StorageWithdrawPayload.TYPE, StorageWithdrawPayload.CODEC, StorageWithdrawPayload::handle);
        registrar.playToServer(PickSourcePayload.TYPE, PickSourcePayload.CODEC, PickSourcePayload::handle);
        registrar.playToServer(OpenDemonPayload.TYPE, OpenDemonPayload.CODEC, OpenDemonPayload::handle);
        registrar.playToServer(InsertPayload.TYPE, InsertPayload.CODEC, InsertPayload::handle);
        registrar.playToServer(LearnCursorPayload.TYPE, LearnCursorPayload.CODEC, LearnCursorPayload::handle);
    }

    public static void syncKnowledge(ServerPlayer player) {
        KnowledgeData data = player.getData(ModAttachments.KNOWLEDGE);
        PacketDistributor.sendToPlayer(player, new SyncKnowledgePayload(data.getPoints(), data.learnedList()));
    }

    public static void syncStorage(ServerPlayer player) {
        StorageData data = player.getData(ModAttachments.STORAGE);
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : data.stacks()) {
            copy.add(stack.copy());
        }
        PacketDistributor.sendToPlayer(player, new SyncStoragePayload(AkashicConfig.STORAGE_SLOTS.get(), copy));
    }

    public static void syncValues(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncValuesPayload(KnowledgeManager.snapshot()));
    }

    public static void syncValuesToAll() {
        PacketDistributor.sendToAllPlayers(new SyncValuesPayload(KnowledgeManager.snapshot()));
    }
}
