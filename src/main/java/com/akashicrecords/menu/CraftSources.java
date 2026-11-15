package com.akashicrecords.menu;

import com.akashicrecords.client.ClientKnowledge;
import com.akashicrecords.client.ClientStorage;
import com.akashicrecords.data.KnowledgeData;
import com.akashicrecords.data.ModAttachments;
import com.akashicrecords.data.StorageData;
import com.akashicrecords.knowledge.KnowledgeKeys;
import com.akashicrecords.knowledge.KnowledgeManager;
import com.akashicrecords.network.ModNetwork;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Джерела для сітки крафту: інвентар, особисте сховище та вивчені предмети (за бали Знань).
 * Використовується і для підсвічування рецептів у книзі рецептів (клієнт), і для автопідстановки (сервер).
 */
public final class CraftSources {
    private static final int KIND_INVENTORY = 0;
    private static final int KIND_STORAGE = 1;
    private static final int KIND_KNOWLEDGE = 2;

    private CraftSources() {
    }

    // ------------------------------------------------------------ клієнт і сервер

    private static List<ItemStack> storage(Player player) {
        return player.level().isClientSide ? ClientStorage.stacks() : player.getData(ModAttachments.STORAGE).stacks();
    }

    private static long points(Player player) {
        return player.level().isClientSide ? ClientKnowledge.points() : player.getData(ModAttachments.KNOWLEDGE).getPoints();
    }

    private static List<ResourceLocation> learned(Player player) {
        return player.level().isClientSide ? ClientKnowledge.learned() : player.getData(ModAttachments.KNOWLEDGE).learnedList();
    }

    /** Додає до книги рецептів те, що гравець може взяти зі сховища та за Знання (понад інвентар). */
    public static void fillStackedContents(Player player, StackedContents contents) {
        for (ItemStack stack : storage(player)) {
            contents.accountStack(stack, stack.getCount());
        }
        long points = points(player);
        for (ResourceLocation key : learned(player)) {
            if (KnowledgeKeys.parse(key) != null) {
                continue;
            }
            long value = KnowledgeManager.getValue(key);
            if (value <= 0 || value > points) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            int count = (int) Math.min(points / value, 576L);
            contents.accountStack(new ItemStack(item, count), count);
        }
    }

    /** Клієнт: чи вистачає інгредієнтів з інвентаря, сховища та Знань (для EMI та JEI). */
    public static boolean canCraftClient(Player player, Recipe<?> recipe) {
        StackedContents contents = new StackedContents();
        player.getInventory().fillStackedContents(contents);
        fillStackedContents(player, contents);
        return contents.canCraft(recipe, null);
    }

    // ------------------------------------------------------------------ сервер

    private record Cand(int kind, int ref, ItemStack proto, long cost) {
    }

    /** Що саме списано для одного місця сітки. */
    private record Take(int kind, int ref, int amount, long cost) {
    }

    private record Placed(int gridSlot, ItemStack stack, List<Take> takes) {
    }

    /**
     * Автопідстановка рецепта: спершу очищає сітку (предмети повертаються в інвентар),
     * потім добирає інгредієнти з інвентаря, сховища та Знань (у такому порядку).
     * Якщо placeAll, ставить стільки наборів, скільки дозволяють джерела (до стака).
     */
    public static void place(ServerPlayer player, AkashicMenu menu, CraftingContainer grid, Recipe<?> recipe, boolean placeAll) {
        if (recipe.getType() != RecipeType.CRAFTING) {
            return;
        }

        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack old = grid.removeItemNoUpdate(i);
            if (!old.isEmpty()) {
                player.getInventory().placeItemBackInInventory(old);
            }
        }

        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int[] slotOf = new int[ingredients.size()];
        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            if (width > 3 || shaped.getHeight() > 3) {
                return;
            }
            for (int i = 0; i < slotOf.length; i++) {
                slotOf[i] = (i / width) * 3 + (i % width);
            }
        } else {
            if (slotOf.length > 9) {
                return;
            }
            for (int i = 0; i < slotOf.length; i++) {
                slotOf[i] = i;
            }
        }

        Inventory inventory = player.getInventory();
        KnowledgeData knowledge = player.getData(ModAttachments.KNOWLEDGE);
        StorageData storage = player.getData(ModAttachments.STORAGE);

        List<List<Cand>> candidates = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            candidates.add(ingredient.isEmpty() ? List.of() : candidatesFor(ingredient, inventory, storage, knowledge));
        }

        List<Placed> plan = null;
        for (int times = placeAll ? 64 : 1; times >= 1 && plan == null; times--) {
            plan = tryPlan(ingredients, slotOf, candidates, inventory, storage, knowledge, times);
        }
        if (plan == null) {
            player.displayClientMessage(Component.translatable("gui.akashic_records.craft_missing"), true);
            menu.slotsChanged(grid);
            return;
        }

        // Списання
        TreeMap<Integer, Integer> fromStorage = new TreeMap<>();
        long spent = 0;
        for (Placed placed : plan) {
            for (Take take : placed.takes()) {
                switch (take.kind()) {
                    case KIND_INVENTORY -> inventory.removeItem(take.ref(), take.amount());
                    case KIND_STORAGE -> fromStorage.merge(take.ref(), take.amount(), Integer::sum);
                    default -> spent += take.cost() * take.amount();
                }
            }
        }
        for (var entry : fromStorage.descendingMap().entrySet()) { // з кінця, щоб індекси не збивалися
            storage.extract(entry.getKey(), entry.getValue());
        }
        if (spent > 0) {
            knowledge.spend(spent);
        }
        for (Placed placed : plan) {
            grid.setItem(placed.gridSlot(), placed.stack());
        }

        if (!fromStorage.isEmpty()) {
            ModNetwork.syncStorage(player);
        }
        if (spent > 0) {
            ModNetwork.syncKnowledge(player);
        }
        menu.slotsChanged(grid);
    }

    private static List<Cand> candidatesFor(Ingredient ingredient, Inventory inventory, StorageData storage, KnowledgeData knowledge) {
        List<Cand> result = new ArrayList<>();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (!stack.isEmpty() && ingredient.test(stack)) {
                result.add(new Cand(KIND_INVENTORY, slot, stack, 0));
            }
        }
        List<ItemStack> stored = storage.stacks();
        for (int i = 0; i < stored.size(); i++) {
            if (ingredient.test(stored.get(i))) {
                result.add(new Cand(KIND_STORAGE, i, stored.get(i), 0));
            }
        }
        List<Cand> known = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        for (ItemStack option : ingredient.getItems()) {
            Item item = option.getItem();
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            long value = KnowledgeManager.getValue(key);
            if (value > 0 && knowledge.hasLearned(key) && seen.add(item)) {
                ItemStack proto = new ItemStack(item);
                if (ingredient.test(proto)) {
                    known.add(new Cand(KIND_KNOWLEDGE, 0, proto, value));
                }
            }
        }
        known.sort((a, b) -> Long.compare(a.cost(), b.cost())); // найдешевші першими
        result.addAll(known);
        return result;
    }

    private static List<Placed> tryPlan(NonNullList<Ingredient> ingredients, int[] slotOf, List<List<Cand>> candidates,
                                        Inventory inventory, StorageData storage, KnowledgeData knowledge, int times) {
        int[] invLeft = new int[inventory.items.size()];
        for (int i = 0; i < invLeft.length; i++) {
            invLeft[i] = inventory.items.get(i).getCount();
        }
        int[] storageLeft = new int[storage.stacks().size()];
        for (int i = 0; i < storageLeft.length; i++) {
            storageLeft[i] = storage.stacks().get(i).getCount();
        }
        long[] points = {knowledge.getPoints()};

        List<Placed> result = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            List<Cand> list = candidates.get(i);
            if (ingredients.get(i).isEmpty()) {
                continue;
            }

            Placed placed = null;
            List<Cand> tried = new ArrayList<>();
            for (Cand lead : list) {
                boolean already = false;
                for (Cand t : tried) {
                    if (ItemStack.isSameItemSameComponents(t.proto(), lead.proto())) {
                        already = true;
                        break;
                    }
                }
                if (already || lead.proto().getMaxStackSize() < times) {
                    continue;
                }
                tried.add(lead);

                List<Cand> group = new ArrayList<>();
                long total = 0;
                for (Cand c : list) {
                    if (ItemStack.isSameItemSameComponents(c.proto(), lead.proto())) {
                        group.add(c);
                        total += switch (c.kind()) {
                            case KIND_INVENTORY -> invLeft[c.ref()];
                            case KIND_STORAGE -> storageLeft[c.ref()];
                            default -> c.cost() > 0 ? points[0] / c.cost() : 0;
                        };
                    }
                }
                if (total < times) {
                    continue;
                }

                List<Take> takes = new ArrayList<>();
                int need = times;
                for (Cand c : group) {
                    if (need <= 0) {
                        break;
                    }
                    int available = switch (c.kind()) {
                        case KIND_INVENTORY -> invLeft[c.ref()];
                        case KIND_STORAGE -> storageLeft[c.ref()];
                        default -> (int) Math.min(Integer.MAX_VALUE, points[0] / c.cost());
                    };
                    int amount = Math.min(need, available);
                    if (amount <= 0) {
                        continue;
                    }
                    switch (c.kind()) {
                        case KIND_INVENTORY -> invLeft[c.ref()] -= amount;
                        case KIND_STORAGE -> storageLeft[c.ref()] -= amount;
                        default -> points[0] -= c.cost() * amount;
                    }
                    takes.add(new Take(c.kind(), c.ref(), amount, c.cost()));
                    need -= amount;
                }
                placed = new Placed(slotOf[i], lead.proto().copyWithCount(times), takes);
                break;
            }
            if (placed == null) {
                return null;
            }
            result.add(placed);
        }
        return result;
    }
}
