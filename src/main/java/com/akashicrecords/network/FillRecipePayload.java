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

/**
 * Клієнт -> сервер: підставити рецепт у сітку крафту (від EMI або JEI).
 * Використовує ту саму логіку, що й книга рецептів: інвентар, сховище та Знання.
 */
public record FillRecipePayload(ResourceLocation recipe, boolean all) implements CustomPacketPayload {
    public static final Type<FillRecipePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "fill_recipe"));

    public static final StreamCodec<ByteBuf, FillRecipePayload> CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, FillRecipePayload::recipe,
            ByteBufCodecs.BOOL, FillRecipePayload::all,
            FillRecipePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FillRecipePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AkashicMenu menu) {
            player.server.getRecipeManager().byKey(payload.recipe())
                    .ifPresent(holder -> menu.handlePlacement(payload.all(), holder, player));
        }
    }
}
