package com.akashicrecords.network;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.menu.DemonMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клієнт -> сервер: покласти предмет у «Демона Лапласа» (вивчити або зберегти).
 * slot = -1: предмет на курсорі; інакше номер комірки меню (інвентар гравця).
 * all: увесь стак (інакше один предмет).
 */
public record InsertPayload(boolean learn, int slot, boolean all) implements CustomPacketPayload {
    public static final Type<InsertPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "demon_insert"));

    public static final StreamCodec<ByteBuf, InsertPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, InsertPayload::learn,
            ByteBufCodecs.VAR_INT, InsertPayload::slot,
            ByteBufCodecs.BOOL, InsertPayload::all,
            InsertPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InsertPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof DemonMenu menu) {
            menu.insertFromUi(player, payload.learn(), payload.slot(), payload.all());
        }
    }
}
