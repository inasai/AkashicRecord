package com.akashicrecords.knowledge;

import com.akashicrecords.AkashicRecords;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Необов'язкова сумісність із ProjectE: читає EMC через рефлексію, тож мод
 * не залежить від ProjectE під час компіляції й працює без нього.
 */
final class ProjectECompat {
    private ProjectECompat() {
    }

    static Map<Item, Long> readAll() {
        Map<Item, Long> result = new HashMap<>();
        try {
            Class<?> apiClass = Class.forName("moze_intel.projecte.api.ProjectEAPI");
            Object proxy = apiClass.getMethod("getEMCProxy").invoke(null);
            Class<?> proxyClass = Class.forName("moze_intel.projecte.api.proxy.IEMCProxy");

            Method byLike = null;
            Method byStack = null;
            try {
                byLike = proxyClass.getMethod("getValue", ItemLike.class);
            } catch (NoSuchMethodException ignored) {
                byStack = proxyClass.getMethod("getValue", ItemStack.class);
            }

            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) {
                    continue;
                }
                Object raw = byLike != null ? byLike.invoke(proxy, item) : byStack.invoke(proxy, new ItemStack(item));
                long value = raw instanceof Number n ? n.longValue() : 0L;
                if (value > 0) {
                    result.put(item, value);
                }
            }
        } catch (Throwable t) {
            AkashicRecords.LOGGER.warn("[Akashic Records] Не вдалося прочитати EMC з ProjectE, використовую власні значення", t);
            result.clear();
        }
        return result;
    }
}
