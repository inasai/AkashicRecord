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

/** Клієнт -> сервер: гравець просить отримати предмет за бали Знань. */
public record TakeItemPayload(ResourceLocation item, int amount) implements CustomPacketPayload {
    public static final Type<TakeItemPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "take_item"));

    public static final StreamCodec<ByteBuf, TakeItemPayload> CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, TakeItemPayload::item,
            ByteBufCodecs.VAR_INT, TakeItemPayload::amount,
            TakeItemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TakeItemPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AkashicMenu menu) {
            menu.take(player, payload.item(), payload.amount());
        }
    }
}
