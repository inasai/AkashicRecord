package com.akashicrecords;

import com.akashicrecords.data.KnowledgeData;
import com.akashicrecords.data.ModAttachments;
import com.akashicrecords.knowledge.KnowledgeManager;
import com.akashicrecords.network.ModNetwork;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = AkashicRecords.MODID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        KnowledgeManager.recompute(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.syncValues(player);
            ModNetwork.syncKnowledge(player);
            ModNetwork.syncStorage(player);
        }
    }

    /** Піч «Демона Лапласа» працює, поки гравець у грі, навіть із закритим вікном. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasData(ModAttachments.FURNACE)) {
            player.getData(ModAttachments.FURNACE).tick(player.level());
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("akashic")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("recalc").executes(ctx -> {
                    KnowledgeManager.recompute(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.akashic_records.recalc"), true);
                    return 1;
                }))
                .then(Commands.literal("add")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                    KnowledgeData data = player.getData(ModAttachments.KNOWLEDGE);
                                    data.addPoints(amount);
                                    ModNetwork.syncKnowledge(player);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.akashic_records.add", amount), false);
                                    return 1;
                                }))));
    }
}
