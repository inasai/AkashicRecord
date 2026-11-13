package com.akashicrecords.data;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Особисте сховище гравця для предметів, яких не можна вивчити.
 * Кожен запис не перевищує максимального розміру стака свого предмета.
 */
public class StorageData {
    public static final Codec<StorageData> CODEC = ItemStack.CODEC.listOf().xmap(StorageData::new, StorageData::stacks);

    private final List<ItemStack> stacks = new ArrayList<>();

    public StorageData() {
    }

    public StorageData(List<ItemStack> loaded) {
        for (ItemStack stack : loaded) {
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
    }

    public List<ItemStack> stacks() {
        return stacks;
    }

    /**
     * Кладе до {@code amount} предметів за зразком у сховище.
     *
     * @return скільки предметів реально покладено (менше, якщо сховище заповнене)
     */
    public int insert(ItemStack prototype, int amount, int capacity) {
        int max = prototype.getMaxStackSize();
        int remaining = amount;

        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                break;
            }
            if (stack.getCount() < max && ItemStack.isSameItemSameComponents(stack, prototype)) {
                int add = Math.min(max - stack.getCount(), remaining);
                stack.grow(add);
                remaining -= add;
            }
        }
        while (remaining > 0 && stacks.size() < capacity) {
            int add = Math.min(max, remaining);
            stacks.add(prototype.copyWithCount(add));
            remaining -= add;
        }
        return amount - remaining;
    }

    /** Забирає до {@code amount} предметів із запису за індексом. */
    public ItemStack extract(int index, int amount) {
        ItemStack stored = stacks.get(index);
        int taken = Math.min(amount, stored.getCount());
        ItemStack result = stored.copyWithCount(taken);
        stored.shrink(taken);
        if (stored.isEmpty()) {
            stacks.remove(index);
        }
        return result;
    }
}
