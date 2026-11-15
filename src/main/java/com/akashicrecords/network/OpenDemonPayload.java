package com.akashicrecords.network;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.menu.DemonOpener;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клієнт -> сервер: гравець натиснув клавішу «Демон Лапласа». */
public record OpenDemonPayload() implements CustomPacketPayload {
    public static final OpenDemonPayload INSTANCE = new OpenDemonPayload();
    public static final Type<OpenDemonPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "open_demon"));
    public static final StreamCodec<ByteBuf, OpenDemonPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDemonPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DemonOpener.open(player);
        }
    }
}
