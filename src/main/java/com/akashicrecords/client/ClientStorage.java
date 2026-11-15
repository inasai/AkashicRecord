package com.akashicrecords.client;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Клієнтська копія особистого сховища гравця (приходить із сервера). */
public final class ClientStorage {
    private static int capacity;
    private static List<ItemStack> stacks = List.of();
    private static int version;

    private ClientStorage() {
    }

    public static void set(int newCapacity, List<ItemStack> newStacks) {
        capacity = newCapacity;
        stacks = List.copyOf(newStacks);
        version++;
    }

    public static int version() {
        return version;
    }

    public static int capacity() {
        return capacity;
    }

    public static List<ItemStack> stacks() {
        return stacks;
    }
}
