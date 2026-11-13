package com.akashicrecords.item;

import com.akashicrecords.AkashicRecords;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AkashicRecords.MODID);

    public static final DeferredItem<AkashicBookItem> AKASHIC_BOOK = ITEMS.register("akashic_chronicles",
            () -> new AkashicBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private ModItems() {
    }
}
