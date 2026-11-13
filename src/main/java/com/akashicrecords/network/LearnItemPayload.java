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

/** Клієнт -> сервер: гравець вивчає предмет зі свого інвентаря (1 шт. або всі). */
public record LearnItemPayload(ResourceLocation item, boolean all) implements CustomPacketPayload {
    public static final Type<LearnItemPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "learn_item"));

    public static final StreamCodec<ByteBuf, LearnItemPayload> CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, LearnItemPayload::item,
            ByteBufCodecs.BOOL, LearnItemPayload::all,
            LearnItemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LearnItemPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AkashicMenu menu) {
            menu.learn(player, payload.item(), payload.all());
        }
    }
}
