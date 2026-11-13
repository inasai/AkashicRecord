package com.akashicrecords.item;

import com.akashicrecords.client.ClientKnowledge;
import com.akashicrecords.client.Format;
import com.akashicrecords.menu.AkashicMenu;
import com.akashicrecords.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class AkashicBookItem extends Item {
    public AkashicBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new AkashicMenu(id, inventory),
                    Component.translatable("container.akashic_records.chronicles")));
            ModNetwork.syncKnowledge(serverPlayer);
            ModNetwork.syncStorage(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.akashic_records.knowledge", Format.num(ClientKnowledge.points()))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.akashic_records.hint").withStyle(ChatFormatting.GRAY));
    }
}
