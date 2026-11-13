package com.akashicrecords.network;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.client.ClientKnowledge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Сервер -> клієнт: бали Знань і список вивчених предметів гравця. */
public record SyncKnowledgePayload(long points, List<ResourceLocation> learned) implements CustomPacketPayload {
    public static final Type<SyncKnowledgePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "sync_knowledge"));

    public static final StreamCodec<ByteBuf, SyncKnowledgePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SyncKnowledgePayload::points,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncKnowledgePayload::learned,
            SyncKnowledgePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncKnowledgePayload payload, IPayloadContext context) {
        ClientKnowledge.set(payload.points(), payload.learned());
    }
}
