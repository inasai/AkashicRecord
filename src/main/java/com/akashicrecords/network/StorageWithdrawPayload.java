package com.akashicrecords.network;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.menu.AkashicMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клієнт -> сервер: забрати предмети із запису сховища. */
public record StorageWithdrawPayload(int index, ResourceLocation item, int amount) implements CustomPacketPayload {
    public static final Type<StorageWithdrawPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "storage_withdraw"));

    public static final StreamCodec<ByteBuf, StorageWithdrawPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageWithdrawPayload::index,
            ResourceLocation.STREAM_CODEC, StorageWithdrawPayload::item,
            ByteBufCodecs.VAR_INT, StorageWithdrawPayload::amount,
            StorageWithdrawPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StorageWithdrawPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AkashicMenu menu) {
            menu.withdraw(player, payload.index(), payload.item(), payload.amount());
        }
    }
}
