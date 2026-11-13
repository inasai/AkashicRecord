package com.akashicrecords.menu;

import com.akashicrecords.data.AkashicConfig;
import com.akashicrecords.data.KnowledgeData;
import com.akashicrecords.data.ModAttachments;
import com.akashicrecords.data.StorageData;
import com.akashicrecords.item.AkashicBookItem;
import com.akashicrecords.knowledge.KnowledgeKeys;
import com.akashicrecords.knowledge.KnowledgeManager;
import com.akashicrecords.network.ModNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Меню книги. Самі комірки гравця тут невидимі: вони потрібні лише для того,
 * щоб сервер синхронізував інвентар із клієнтом, поки відкрита книга.
 */
public class AkashicMenu extends AbstractContainerMenu {

    public AkashicMenu(int containerId, Inventory inventory) {
        super(ModMenus.AKASHIC.get(), containerId);
        for (int i = 0; i < 36; i++) {
            this.addSlot(new Slot(inventory, i, 0, 0) {
                @Override
                public boolean isActive() {
                    return false;
                }
            });
        }
    }

    /**
     * Предмет можна вивчити, якщо для нього є ключ Знань: звичайний предмет із цінністю й без додаткових даних,
     * зілля з відомим ефектом або книга з одним зачаруванням. Саму книгу «Хроніки Акаши» вивчити не можна.
     */
    public static boolean canLearn(ItemStack stack) {
        return KnowledgeKeys.keyOf(stack) != null;
    }

    /** У сховище можна покласти те, що не можна вивчити, крім самої книги «Хроніки Акаши». */
    public static boolean canStore(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof AkashicBookItem) && !canLearn(stack);
    }

    /**
     * Вивчення з інвентаря: вилучає 1 шт. (або всі такі самі, якщо all), дає бали Знань
     * і відкриває ключ для отримання. Усі перевірки виконуються тут, на сервері.
     */
    public void learn(ServerPlayer serverPlayer, ResourceLocation key, boolean all) {
        long value = KnowledgeManager.getValue(key);
        if (value <= 0) {
            return;
        }

        Inventory inventory = serverPlayer.getInventory();
        int wanted = all ? Integer.MAX_VALUE : 1;
        int removed = 0;
        for (int slot = 0; slot < inventory.items.size() && removed < wanted; slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (key.equals(KnowledgeKeys.keyOf(stack))) {
                int amount = Math.min(stack.getCount(), wanted - removed);
                inventory.removeItem(slot, amount);
                removed += amount;
            }
        }
        if (removed <= 0) {
            return;
        }

        KnowledgeData data = serverPlayer.getData(ModAttachments.KNOWLEDGE);
        data.addPoints(saturatedMul(value, removed));
        data.learn(key);

        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.2F);
        ModNetwork.syncKnowledge(serverPlayer);
    }

    /** Отримання за бали Знань. Усі перевірки виконуються тут, на сервері. */
    public void take(ServerPlayer serverPlayer, ResourceLocation key, int requested) {
        long value = KnowledgeManager.getValue(key);
        if (value <= 0) {
            return;
        }
        KnowledgeData data = serverPlayer.getData(ModAttachments.KNOWLEDGE);
        if (!data.hasLearned(key)) {
            return;
        }

        ItemStack prototype = KnowledgeKeys.stackFor(key, serverPlayer.level().registryAccess());
        if (prototype.isEmpty()) {
            return;
        }

        int maxStack = prototype.getMaxStackSize();
        int amount = Mth.clamp(requested, 1, maxStack);
        long affordable = data.getPoints() / value;
        amount = (int) Math.min(amount, affordable);
        if (amount <= 0) {
            return;
        }
        if (!data.spend(value * amount)) {
            return;
        }

        serverPlayer.getInventory().placeItemBackInInventory(prototype.copyWithCount(amount));
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.0F);
        ModNetwork.syncKnowledge(serverPlayer);
    }

    /** Кладе предмет з комірки інвентаря у сховище (1 шт. або всі такі самі). */
    public void deposit(ServerPlayer serverPlayer, int slot, ResourceLocation id, boolean all) {
        Inventory inventory = serverPlayer.getInventory();
        if (slot < 0 || slot >= inventory.items.size()) {
            return;
        }
        ItemStack clicked = inventory.items.get(slot);
        if (!canStore(clicked) || !BuiltInRegistries.ITEM.getKey(clicked.getItem()).equals(id)) {
            return;
        }

        ItemStack template = clicked.copyWithCount(1);
        StorageData storage = serverPlayer.getData(ModAttachments.STORAGE);
        int capacity = AkashicConfig.STORAGE_SLOTS.get();
        int wanted = all ? Integer.MAX_VALUE : 1;

        List<Integer> order = new ArrayList<>();
        order.add(slot);
        if (all) {
            for (int i = 0; i < inventory.items.size(); i++) {
                if (i != slot) {
                    order.add(i);
                }
            }
        }

        int moved = 0;
        boolean full = false;
        for (int index : order) {
            if (moved >= wanted) {
                break;
            }
            ItemStack stack = inventory.items.get(index);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) {
                continue;
            }
            int amount = Math.min(stack.getCount(), wanted - moved);
            int inserted = storage.insert(template, amount, capacity);
            if (inserted > 0) {
                inventory.removeItem(index, inserted);
                moved += inserted;
            }
            if (inserted < amount) {
                full = true;
                break;
            }
        }

        if (full) {
            serverPlayer.displayClientMessage(Component.translatable("gui.akashic_records.storage_full"), true);
        }
        if (moved > 0) {
            serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 0.8F);
        }
        ModNetwork.syncStorage(serverPlayer);
    }

    /** Забирає предмети із запису сховища в інвентар гравця. */
    public void withdraw(ServerPlayer serverPlayer, int index, ResourceLocation id, int amount) {
        StorageData storage = serverPlayer.getData(ModAttachments.STORAGE);
        if (index < 0 || index >= storage.stacks().size()) {
            return;
        }
        ItemStack stored = storage.stacks().get(index);
        if (!BuiltInRegistries.ITEM.getKey(stored.getItem()).equals(id)) {
            return; // запис змінився, поки клієнт не отримав оновлення
        }

        ItemStack result = storage.extract(index, Mth.clamp(amount, 1, stored.getCount()));
        serverPlayer.getInventory().placeItemBackInInventory(result);
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.0F);
        ModNetwork.syncStorage(serverPlayer);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static long saturatedMul(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
