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

/** Клієнт -> сервер: узяти предмет зі сховища або за Знання на курсор (для сітки крафту). */
public record PickSourcePayload(boolean fromStorage, ResourceLocation key, int index, int amount) implements CustomPacketPayload {
    public static final Type<PickSourcePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "pick_source"));

    public static final StreamCodec<ByteBuf, PickSourcePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PickSourcePayload::fromStorage,
            ResourceLocation.STREAM_CODEC, PickSourcePayload::key,
            ByteBufCodecs.VAR_INT, PickSourcePayload::index,
            ByteBufCodecs.VAR_INT, PickSourcePayload::amount,
            PickSourcePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PickSourcePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AkashicMenu menu) {
            menu.pick(player, payload.fromStorage(), payload.key(), payload.index(), payload.amount());
        }
    }
}
