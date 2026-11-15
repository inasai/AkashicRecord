package com.akashicrecords.menu;

import com.akashicrecords.data.AkashicConfig;
import com.akashicrecords.data.ModAttachments;
import com.akashicrecords.data.StorageData;
import com.akashicrecords.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

/** Відкриває «Демона Лапласа», якщо гравець уже з'їв Яблуко Лапласа. */
public final class DemonOpener {
    private DemonOpener() {
    }

    /** Вміст старого окремого вмістилища переходить у загальне сховище. */
    private static void migrateOldVault(ServerPlayer player) {
        StorageData vault = player.getData(ModAttachments.VAULT);
        if (vault.stacks().isEmpty()) {
            return;
        }
        StorageData storage = player.getData(ModAttachments.STORAGE);
        for (ItemStack stack : vault.stacks()) {
            storage.insert(stack, stack.getCount(), Integer.MAX_VALUE);
        }
        vault.stacks().clear();
    }

    public static void open(ServerPlayer player) {
        if (!player.getData(ModAttachments.KNOWLEDGE).hasDemon()) {
            player.displayClientMessage(Component.translatable("message.akashic_records.demon_locked"), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new DemonMenu(id, inventory),
                Component.translatable("container.akashic_records.demon")));
        ModNetwork.syncKnowledge(player);
        migrateOldVault(player);
        ModNetwork.syncStorage(player);
        if (AkashicConfig.UNLOCK_ALL_RECIPES.get()) {
            player.awardRecipes(player.server.getRecipeManager().getRecipes());
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
