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

/** Клієнт -> сервер: покласти предмет з комірки інвентаря у сховище (1 шт. або всі такі самі). */
public record StorageDepositPayload(int slot, ResourceLocation item, boolean all) implements CustomPacketPayload {
    public static final Type<StorageDepositPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "storage_deposit"));

    public static final StreamCodec<ByteBuf, StorageDepositPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageDepositPayload::slot,
            ResourceLocation.STREAM_CODEC, StorageDepositPayload::item,
            ByteBufCodecs.BOOL, StorageDepositPayload::all,
            StorageDepositPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StorageDepositPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AkashicMenu menu) {
            menu.deposit(player, payload.slot(), payload.item(), payload.all());
        }
    }
}
