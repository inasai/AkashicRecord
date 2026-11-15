package com.akashicrecords.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Піч гравця з вікна «Демон Лапласа». Належить гравцеві (а не вікну), тому плавить і з закритим вікном.
 * Тікається щотіку з ServerEvents, поки гравець у грі.
 */
public class DemonFurnace {
    public static final Codec<DemonFurnace> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(DemonFurnace::itemList),
            Codec.INT.optionalFieldOf("lit", 0).forGetter(f -> f.lit),
            Codec.INT.optionalFieldOf("lit_duration", 0).forGetter(f -> f.litDuration),
            Codec.INT.optionalFieldOf("progress", 0).forGetter(f -> f.progress),
            Codec.INT.optionalFieldOf("total", 200).forGetter(f -> f.total)
    ).apply(inst, DemonFurnace::new));

    private final SimpleContainer container = new SimpleContainer(3);
    private int lit;
    private int litDuration;
    private int progress;
    private int total = 200;

    /** Дані для вікна: горіння, тривалість горіння, прогрес, потрібно. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> lit;
                case 1 -> litDuration;
                case 2 -> progress;
                default -> total;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> lit = value;
                case 1 -> litDuration = value;
                case 2 -> progress = value;
                default -> total = value;
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public DemonFurnace() {
    }

    public DemonFurnace(List<ItemStack> items, int lit, int litDuration, int progress, int total) {
        for (int i = 0; i < Math.min(3, items.size()); i++) {
            container.setItem(i, items.get(i).copy());
        }
        this.lit = lit;
        this.litDuration = litDuration;
        this.progress = progress;
        this.total = total;
    }

    public SimpleContainer container() {
        return container;
    }

    public ContainerData data() {
        return data;
    }

    private List<ItemStack> itemList() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            list.add(container.getItem(i));
        }
        return list;
    }

    /** Один тік печі. */
    public void tick(Level level) {
        ItemStack input = container.getItem(0);
        if (input.isEmpty() && lit <= 0 && progress <= 0) {
            return;
        }
        ItemStack fuel = container.getItem(1);
        if (lit > 0) {
            lit--;
        }

        Optional<RecipeHolder<SmeltingRecipe>> recipe = input.isEmpty()
                ? Optional.empty()
                : level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
        ItemStack result = recipe.map(r -> r.value().assemble(new SingleRecipeInput(input), level.registryAccess()))
                .orElse(ItemStack.EMPTY);
        ItemStack output = container.getItem(2);
        boolean canSmelt = !result.isEmpty()
                && (output.isEmpty() || (ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize()));

        if (lit <= 0 && canSmelt && !fuel.isEmpty()) {
            int burn = fuel.getBurnTime(RecipeType.SMELTING);
            if (burn > 0) {
                lit = burn;
                litDuration = burn;
                ItemStack remainder = fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if (fuel.isEmpty()) {
                    container.setItem(1, remainder);
                }
            }
        }

        total = recipe.map(r -> ((AbstractCookingRecipe) r.value()).getCookingTime()).orElse(200);
        if (lit > 0 && canSmelt) {
            progress++;
            if (progress >= total) {
                progress = 0;
                if (output.isEmpty()) {
                    container.setItem(2, result.copy());
                } else {
                    output.grow(result.getCount());
                }
                input.shrink(1);
                if (input.isEmpty()) {
                    container.setItem(0, ItemStack.EMPTY);
                }
            }
        } else if (progress > 0) {
            progress = Math.max(0, progress - 2);
        }
        container.setChanged();
    }
}
