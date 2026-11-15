package com.akashicrecords.client;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.item.AkashicBookItem;
import com.akashicrecords.knowledge.KnowledgeKeys;
import com.akashicrecords.knowledge.KnowledgeManager;
import com.akashicrecords.network.OpenDemonPayload;
import com.akashicrecords.menu.ModMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class ClientSetup {
    private ClientSetup() {
    }

    @EventBusSubscriber(modid = AkashicRecords.MODID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenus.AKASHIC.get(), AkashicScreen::new);
            event.register(ModMenus.DEMON.get(), DemonScreen::new);
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(DemonKeys.OPEN);
        }
    }

    @EventBusSubscriber(modid = AkashicRecords.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        /** Чи є в гравця книга «Хроніки Акаши» (інвентар, броня, друга рука). */
        private static boolean hasBook(Player player) {
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof AkashicBookItem) {
                    return true;
                }
            }
            for (ItemStack stack : player.getInventory().offhand) {
                if (stack.getItem() instanceof AkashicBookItem) {
                    return true;
                }
            }
            return false;
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (DemonKeys.OPEN.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    PacketDistributor.sendToServer(OpenDemonPayload.INSTANCE);
                }
            }
        }

        /**
         * Показує цінність (Знання) у підказці предмета, що її має, і позначку «Невідомо» для невивченого.
         * Лише коли в гравця є книга «Хроніки Акаши» (або відкрито вікно «Демон Лапласа»).
         */
        @SubscribeEvent
        public static void onTooltip(ItemTooltipEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !(hasBook(mc.player) || mc.screen instanceof DemonScreen)) {
                return;
            }
            ItemStack stack = event.getItemStack();
            long value = KnowledgeManager.getValue(stack);
            if (value > 0) {
                event.getToolTip().add(Component.translatable("gui.akashic_records.cost", Format.num(value))
                        .withStyle(ChatFormatting.GOLD));
                int count = stack.getCount(); // вартість поточної кількості
                if (count > 1) {
                    long stackValue = value > Long.MAX_VALUE / count ? Long.MAX_VALUE : value * count;
                    event.getToolTip().add(Component.translatable("gui.akashic_records.stack_cost", Format.num(stackValue))
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            var key = KnowledgeKeys.keyOf(stack);
            if (key != null && !ClientKnowledge.hasLearned(key)) {
                event.getToolTip().add(Component.translatable("tooltip.akashic_records.unknown")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }
}
