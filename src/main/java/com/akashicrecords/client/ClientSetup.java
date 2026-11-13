package com.akashicrecords.client;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.knowledge.KnowledgeManager;
import com.akashicrecords.menu.ModMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class ClientSetup {
    private ClientSetup() {
    }

    @EventBusSubscriber(modid = AkashicRecords.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenus.AKASHIC.get(), AkashicScreen::new);
        }
    }

    @EventBusSubscriber(modid = AkashicRecords.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        /** Показує цінність (Знання) у підказці кожного предмета, що її має. */
        @SubscribeEvent
        public static void onTooltip(ItemTooltipEvent event) {
            long value = KnowledgeManager.getValue(event.getItemStack());
            if (value > 0) {
                event.getToolTip().add(Component.translatable("gui.akashic_records.cost", Format.num(value))
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
