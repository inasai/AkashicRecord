package com.akashicrecords.item;

import com.akashicrecords.AkashicRecords;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AkashicRecords.MODID);

    public static final DeferredItem<AkashicBookItem> AKASHIC_BOOK = ITEMS.register("akashic_chronicles",
            () -> new AkashicBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredItem<LaplaceAppleItem> LAPLACE_APPLE = ITEMS.register("laplace_apple",
            () -> new LaplaceAppleItem(new Item.Properties().rarity(Rarity.EPIC).food(
                    new FoodProperties.Builder().nutrition(4).saturationModifier(0.6F).alwaysEdible().build())));

    private ModItems() {
    }
}
