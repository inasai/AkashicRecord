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

/** Клієнт -> сервер: вивчити предмет, що лежить на курсорі (весь стак або 1 шт.). */
public record LearnCursorPayload(boolean all) implements CustomPacketPayload {
    public static final Type<LearnCursorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "learn_cursor"));
    public static final StreamCodec<ByteBuf, LearnCursorPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, LearnCursorPayload::all, LearnCursorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LearnCursorPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AkashicMenu menu) {
            menu.learnCarried(player, payload.all());
        }
    }
}
