package com.akashicrecords.knowledge;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.data.AkashicConfig;
import com.akashicrecords.item.ModItems;
import com.akashicrecords.network.ModNetwork;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.fml.ModList;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Сховище цінності предметів («Знання», аналог EMC).
 * На сервері значення обчислюються, на клієнті приходять пакетом.
 */
public final class KnowledgeManager {
    private static final String VALUES_DIR = "knowledge_values";
    private static final String POTIONS_DIR = "knowledge_potions";
    private static volatile Map<Item, Long> VALUES = Map.of();
    /** Цінність варіантів (зілля, книги зачарувань) за ключами з {@link KnowledgeKeys}. */
    private static volatile Map<ResourceLocation, Long> VARIANTS = Map.of();

    private KnowledgeManager() {
    }

    public static long getValue(Item item) {
        return VALUES.getOrDefault(item, 0L);
    }

    /** Цінність ключа: id звичайного предмета або ключ варіанта. */
    public static long getValue(ResourceLocation key) {
        Long variant = VARIANTS.get(key);
        if (variant != null) {
            return variant;
        }
        return BuiltInRegistries.ITEM.getOptional(key).map(KnowledgeManager::getValue).orElse(0L);
    }

    public static long getValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0L;
        }
        Item item = stack.getItem();
        if (item == Items.ENCHANTED_BOOK || item == Items.POTION
                || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
            ResourceLocation key = KnowledgeKeys.keyOf(stack);
            return key == null ? 0L : getValue(key);
        }
        return getValue(item);
    }

    public static Map<ResourceLocation, Long> snapshot() {
        Map<ResourceLocation, Long> out = new HashMap<>();
        VALUES.forEach((item, value) -> out.put(BuiltInRegistries.ITEM.getKey(item), value));
        out.putAll(VARIANTS);
        return out;
    }

    /** Викликається на клієнті, коли сервер надіслав таблицю значень. */
    public static void applyFromServer(Map<ResourceLocation, Long> map) {
        Map<Item, Long> values = new HashMap<>();
        Map<ResourceLocation, Long> variants = new HashMap<>();
        map.forEach((id, value) -> {
            if (KnowledgeKeys.parse(id) != null) {
                variants.put(id, value);
            } else {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> values.put(item, value));
            }
        });
        VALUES = values;
        VARIANTS = variants;
    }

    // ------------------------------------------------------------------ сервер

    public static void recompute(MinecraftServer server) {
        Map<Item, Long> values = new HashMap<>();
        Set<Item> fixed = new HashSet<>();
        Set<Item> banned = new HashSet<>();

        boolean usedProjectE = false;
        if (AkashicConfig.USE_PROJECTE.get() && ModList.get().isLoaded("projecte")) {
            Map<Item, Long> emc = ProjectECompat.readAll();
            if (!emc.isEmpty()) {
                values.putAll(emc);
                fixed.addAll(emc.keySet());
                usedProjectE = true;
                AkashicRecords.LOGGER.info("[Akashic Records] Взято EMC з ProjectE для {} предметів", emc.size());
            }
        }

        loadDatapackValues(server, usedProjectE, values, fixed, banned);

        // Сама книга не має цінності й не вивчається (навіть якщо її можна вивести з рецепта чи EMC)
        banned.add(ModItems.AKASHIC_BOOK.get());

        // Зілля й книги зачарувань оцінюються лише як варіанти (за ефектом і зачаруванням), а не як предмет загалом
        banned.add(Items.POTION);
        banned.add(Items.SPLASH_POTION);
        banned.add(Items.LINGERING_POTION);
        banned.add(Items.ENCHANTED_BOOK);

        if (!usedProjectE && AkashicConfig.DERIVE_FROM_RECIPES.get()) {
            deriveFromRecipes(server, values, fixed, banned);
        }

        banned.forEach(values::remove);
        VALUES = values;

        Map<ResourceLocation, Long> variants = new HashMap<>();
        loadPotions(server, variants);
        computeBooks(server.registryAccess(), variants);
        VARIANTS = variants;

        AkashicRecords.LOGGER.info("[Akashic Records] Цінність визначено для {} предметів і {} варіантів (зілля, книги)",
                values.size(), variants.size());

        ModNetwork.syncValuesToAll();
    }

    /**
     * Зілля беруться з data/&lt;простір&gt;/knowledge_potions/*.json:
     * {"splash_extra": 64, "lingering_extra": 64, "potions": {"minecraft:healing": 619, ...}}.
     * Значення звичайного зілля; вибухове й осідальне дорожчі на splash_extra / lingering_extra.
     * Значення 0 або менше забороняє зілля. Пізніші файли перекривають раніші.
     */
    private static void loadPotions(MinecraftServer server, Map<ResourceLocation, Long> out) {
        Map<ResourceLocation, Long> base = new LinkedHashMap<>();
        long splashExtra = 64;
        long lingeringExtra = 64;

        Map<ResourceLocation, Resource> files = new TreeMap<>(
                server.getResourceManager().listResources(POTIONS_DIR, rl -> rl.getPath().endsWith(".json")));
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            try (Reader reader = file.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) {
                    continue;
                }
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("splash_extra")) {
                    splashExtra = obj.get("splash_extra").getAsLong();
                }
                if (obj.has("lingering_extra")) {
                    lingeringExtra = obj.get("lingering_extra").getAsLong();
                }
                if (obj.has("potions") && obj.get("potions").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("potions").entrySet()) {
                        ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                        if (id == null || !BuiltInRegistries.POTION.containsKey(id)) {
                            AkashicRecords.LOGGER.debug("[Akashic Records] Невідоме зілля '{}' у {}", entry.getKey(), file.getKey());
                            continue;
                        }
                        long value = entry.getValue().getAsLong();
                        if (value <= 0) {
                            base.remove(id);
                        } else {
                            base.put(id, value);
                        }
                    }
                }
            } catch (Exception e) {
                AkashicRecords.LOGGER.warn("[Akashic Records] Не вдалося прочитати {}", file.getKey(), e);
            }
        }

        for (Map.Entry<ResourceLocation, Long> entry : base.entrySet()) {
            long value = entry.getValue();
            out.put(KnowledgeKeys.variantKey(KnowledgeKeys.Kind.POTION, entry.getKey(), 0), value);
            out.put(KnowledgeKeys.variantKey(KnowledgeKeys.Kind.SPLASH, entry.getKey(), 0), saturatedAdd(value, splashExtra));
            out.put(KnowledgeKeys.variantKey(KnowledgeKeys.Kind.LINGERING, entry.getKey(), 0), saturatedAdd(value, lingeringExtra));
        }
    }

    /** Книги зачарувань: для кожного зачарування й кожного рівня від 1 до максимального. */
    private static void computeBooks(RegistryAccess access, Map<ResourceLocation, Long> out) {
        long base = AkashicConfig.ENCHANTED_BOOK_BASE.get();
        Registry<Enchantment> registry = access.registryOrThrow(Registries.ENCHANTMENT);
        for (Holder.Reference<Enchantment> holder : registry.holders().toList()) {
            Enchantment enchantment = holder.value();
            int weight = Math.max(1, enchantment.definition().weight());
            int maxLevel = Math.max(1, enchantment.definition().maxLevel());
            ResourceLocation id = holder.key().location();
            for (int level = 1; level <= maxLevel; level++) {
                out.put(KnowledgeKeys.variantKey(KnowledgeKeys.Kind.BOOK, id, level), bookValue(base, weight, level));
            }
        }
    }

    /** Знання = ((base * 10) / weight) * 2^(level - 1) */
    static long bookValue(long base, int weight, int level) {
        long perWeight = (base * 10L) / weight;
        long multiplier = 1L << Math.min(Math.max(level - 1, 0), 40);
        try {
            return Math.multiplyExact(perWeight, multiplier);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static void loadDatapackValues(MinecraftServer server, boolean skipOwn,
                                           Map<Item, Long> values, Set<Item> fixed, Set<Item> banned) {
        ResourceManager rm = server.getResourceManager();
        Map<ResourceLocation, Resource> files = new TreeMap<>(rm.listResources(VALUES_DIR, rl -> rl.getPath().endsWith(".json")));
        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);

        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            if (skipOwn && file.getKey().getNamespace().equals(AkashicRecords.MODID)) {
                continue;
            }
            try (Reader reader = file.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) {
                    continue;
                }
                JsonObject obj = root.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    long value;
                    try {
                        value = entry.getValue().getAsLong();
                    } catch (Exception e) {
                        AkashicRecords.LOGGER.warn("[Akashic Records] {}: некоректне значення для '{}'", file.getKey(), entry.getKey());
                        continue;
                    }
                    String key = entry.getKey();
                    if (key.startsWith("#")) {
                        TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(key.substring(1)));
                        itemRegistry.getTag(tag).ifPresent(set -> set.forEach(holder -> put(holder.value(), value, values, fixed, banned)));
                    } else {
                        ResourceLocation id = ResourceLocation.parse(key);
                        Item item = itemRegistry.getOptional(id).orElse(null);
                        if (item == null || item == Items.AIR) {
                            AkashicRecords.LOGGER.debug("[Akashic Records] Невідомий предмет '{}' у {}", key, file.getKey());
                            continue;
                        }
                        put(item, value, values, fixed, banned);
                    }
                }
            } catch (Exception e) {
                AkashicRecords.LOGGER.warn("[Akashic Records] Не вдалося прочитати {}", file.getKey(), e);
            }
        }
    }

    private static void put(Item item, long value, Map<Item, Long> values, Set<Item> fixed, Set<Item> banned) {
        if (value <= 0) {
            banned.add(item);
            values.remove(item);
            fixed.add(item);
        } else {
            banned.remove(item);
            values.put(item, value);
            fixed.add(item);
        }
    }

    // ------------------------------------------------------- виведення з рецептів

    private static void deriveFromRecipes(MinecraftServer server, Map<Item, Long> values, Set<Item> fixed, Set<Item> banned) {
        RecipeManager recipes = server.getRecipeManager();
        RegistryAccess access = server.registryAccess();

        List<RecipeHolder<CraftingRecipe>> crafting = recipes.getAllRecipesFor(RecipeType.CRAFTING);
        List<RecipeHolder<SmeltingRecipe>> smelting = recipes.getAllRecipesFor(RecipeType.SMELTING);
        List<RecipeHolder<net.minecraft.world.item.crafting.StonecutterRecipe>> stonecutting =
                recipes.getAllRecipesFor(RecipeType.STONECUTTING);

        for (int pass = 0; pass < 40; pass++) {
            boolean changed = false;

            for (RecipeHolder<CraftingRecipe> holder : crafting) {
                CraftingRecipe recipe = holder.value();
                ItemStack result = recipe.getResultItem(access);
                if (result.isEmpty()) {
                    continue;
                }
                long total = 0;
                boolean ok = true;
                boolean anyIngredient = false;
                for (Ingredient ing : recipe.getIngredients()) {
                    if (ing.isEmpty()) {
                        continue;
                    }
                    anyIngredient = true;
                    long cost = cheapest(ing, values);
                    if (cost <= 0) {
                        ok = false;
                        break;
                    }
                    total = saturatedAdd(total, cost);
                }
                if (ok && anyIngredient) {
                    changed |= offer(result.getItem(), ceilDiv(total, result.getCount()), values, fixed, banned);
                }
            }

            for (RecipeHolder<SmeltingRecipe> holder : smelting) {
                ItemStack result = holder.value().getResultItem(access);
                if (result.isEmpty() || holder.value().getIngredients().isEmpty()) {
                    continue;
                }
                long cost = cheapest(holder.value().getIngredients().get(0), values);
                if (cost > 0) {
                    changed |= offer(result.getItem(), ceilDiv(cost, result.getCount()), values, fixed, banned);
                }
            }

            for (RecipeHolder<net.minecraft.world.item.crafting.StonecutterRecipe> holder : stonecutting) {
                SingleItemRecipe recipe = holder.value();
                ItemStack result = recipe.getResultItem(access);
                if (result.isEmpty() || recipe.getIngredients().isEmpty()) {
                    continue;
                }
                long cost = cheapest(recipe.getIngredients().get(0), values);
                if (cost > 0) {
                    changed |= offer(result.getItem(), ceilDiv(cost, result.getCount()), values, fixed, banned);
                }
            }

            if (!changed) {
                break;
            }
        }
    }

    private static boolean offer(Item item, long candidate, Map<Item, Long> values, Set<Item> fixed, Set<Item> banned) {
        if (item == Items.AIR || fixed.contains(item) || banned.contains(item)) {
            return false;
        }
        candidate = Math.max(1L, candidate);
        Long current = values.get(item);
        if (current == null || candidate < current) {
            values.put(item, candidate);
            return true;
        }
        return false;
    }

    /** Найдешевший варіант інгредієнта з урахуванням залишків (відра, пляшки). 0 = невідомо. */
    private static long cheapest(Ingredient ingredient, Map<Item, Long> values) {
        long best = 0;
        for (ItemStack stack : ingredient.getItems()) {
            Long value = values.get(stack.getItem());
            if (value == null) {
                continue;
            }
            long net = value;
            ItemStack remainder = stack.getCraftingRemainingItem();
            if (!remainder.isEmpty()) {
                Long rv = values.get(remainder.getItem());
                if (rv != null) {
                    net = Math.max(1L, net - rv * remainder.getCount());
                }
            }
            if (best == 0 || net < best) {
                best = net;
            }
        }
        return best;
    }

    private static long ceilDiv(long a, int b) {
        return Math.max(1L, (a + b - 1) / b);
    }

    private static long saturatedAdd(long a, long b) {
        long s = a + b;
        return s < 0 ? Long.MAX_VALUE : s;
    }
}
