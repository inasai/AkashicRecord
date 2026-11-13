package com.akashicrecords.network;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.client.ClientStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Сервер -> клієнт: вміст особистого сховища гравця. */
public record SyncStoragePayload(int capacity, List<ItemStack> stacks) implements CustomPacketPayload {
    public static final Type<SyncStoragePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "sync_storage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncStoragePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncStoragePayload::capacity,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncStoragePayload::stacks,
            SyncStoragePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncStoragePayload payload, IPayloadContext context) {
        ClientStorage.set(payload.capacity(), payload.stacks());
    }
}
