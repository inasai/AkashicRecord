package com.akashicrecords.network;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.knowledge.KnowledgeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/** Сервер -> клієнт: таблиця цінності всіх предметів. */
public record SyncValuesPayload(Map<ResourceLocation, Long> values) implements CustomPacketPayload {
    public static final Type<SyncValuesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "sync_values"));

    public static final StreamCodec<ByteBuf, SyncValuesPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, ResourceLocation, Long, Map<ResourceLocation, Long>>map(
                    HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_LONG),
            SyncValuesPayload::values,
            SyncValuesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncValuesPayload payload, IPayloadContext context) {
        KnowledgeManager.applyFromServer(payload.values());
    }
}
