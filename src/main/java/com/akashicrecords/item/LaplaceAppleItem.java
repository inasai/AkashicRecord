package com.akashicrecords.item;

import com.akashicrecords.data.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Яблуко Лапласа: після вживання відкриває гравцеві меню «Демон Лапласа» (клавіша X за замовчуванням). */
public class LaplaceAppleItem extends Item {
    public LaplaceAppleItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            var data = player.getData(ModAttachments.KNOWLEDGE);
            if (!data.hasDemon()) {
                data.setDemon(true);
                player.displayClientMessage(Component.translatable("message.akashic_records.demon_unlocked")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.akashic_records.laplace_apple").withStyle(ChatFormatting.GRAY));
    }
}
