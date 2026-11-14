package com.akashicrecords.knowledge;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.item.AkashicBookItem;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * «Ключ Знань»: що саме вивчено. Для звичайних предметів це їхній id (minecraft:diamond),
 * а для предметів, цінність яких залежить від даних (зілля, книги зачарувань), це
 * ключ варіанта в просторі імен мода:
 * <ul>
 *   <li>akashic_records:potion/minecraft/healing (так само splash_potion і lingering_potion);</li>
 *   <li>akashic_records:book/minecraft/sharpness/3 (книга з одним зачаруванням і рівнем);</li>
 *   <li>akashic_records:tipped_arrow/minecraft/healing (стріла із зіллям);</li>
 *   <li>akashic_records:suspicious_stew/minecraft/blindness (підозріле рагу з одним ефектом);</li>
 *   <li>akashic_records:ominous_bottle/minecraft/ominous_bottle/2 (зловісна пляшка з рівнем 0..4).</li>
 * </ul>
 */
public final class KnowledgeKeys {

    public enum Kind {
        POTION("potion"),
        SPLASH("splash_potion"),
        LINGERING("lingering_potion"),
        ARROW("tipped_arrow"),
        STEW("suspicious_stew"),
        OMINOUS("ominous_bottle"),
        BOOK("book");

        public final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        static Kind byPrefix(String prefix) {
            for (Kind kind : values()) {
                if (kind.prefix.equals(prefix)) {
                    return kind;
                }
            }
            return null;
        }

        public boolean isPotion() {
            return this == POTION || this == SPLASH || this == LINGERING;
        }

        /** Ключ містить ще й рівень (книги, зловісні пляшки). */
        public boolean hasLevel() {
            return this == BOOK || this == OMINOUS;
        }
    }

    /** Розібраний ключ варіанта. level використовується лише для книг. */
    public record Variant(Kind kind, ResourceLocation id, int level) {
    }

    private KnowledgeKeys() {
    }

    // ------------------------------------------------------------ побудова/розбір

    public static ResourceLocation variantKey(Kind kind, ResourceLocation id, int level) {
        String path = kind.prefix + "/" + id.getNamespace() + "/" + id.getPath();
        if (kind.hasLevel()) {
            path += "/" + level;
        }
        return ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, path);
    }

    /** @return розібраний ключ або null, якщо це не ключ варіанта (наприклад, звичайний id предмета) */
    public static Variant parse(ResourceLocation key) {
        if (!key.getNamespace().equals(AkashicRecords.MODID)) {
            return null;
        }
        String path = key.getPath();
        int first = path.indexOf('/');
        if (first < 0) {
            return null;
        }
        Kind kind = Kind.byPrefix(path.substring(0, first));
        if (kind == null) {
            return null;
        }

        String rest = path.substring(first + 1);
        int level = 0;
        if (kind.hasLevel()) {
            int last = rest.lastIndexOf('/');
            if (last < 0) {
                return null;
            }
            try {
                level = Integer.parseInt(rest.substring(last + 1));
            } catch (NumberFormatException e) {
                return null;
            }
            rest = rest.substring(0, last);
        }

        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryBuild(rest.substring(0, slash), rest.substring(slash + 1));
        return id == null ? null : new Variant(kind, id, level);
    }

    // ----------------------------------------------------------- ключ предмета

    /**
     * Ключ, за яким цей стак можна вивчити, або null, якщо вивчити його не можна
     * (немає цінності, є сторонні дані, це сама книга Хроніки Акаши).
     */
    public static ResourceLocation keyOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof AkashicBookItem) {
            return null;
        }
        if (item == Items.ENCHANTED_BOOK) {
            return bookKey(stack);
        }
        if (item == Items.TIPPED_ARROW) {
            return potionKey(stack, Kind.ARROW);
        }
        if (item == Items.SUSPICIOUS_STEW) {
            return stewKey(stack);
        }
        if (item == Items.OMINOUS_BOTTLE) {
            return ominousKey(stack);
        }
        Kind potionKind = potionKind(item);
        if (potionKind != null) {
            return potionKey(stack, potionKind);
        }
        if (!stack.getComponentsPatch().isEmpty()) {
            return null;
        }
        return KnowledgeManager.getValue(item) > 0 ? BuiltInRegistries.ITEM.getKey(item) : null;
    }

    /** Предмети, цінність яких залежить від даних: їх оцінюють лише як варіанти. */
    public static boolean isVariantItem(Item item) {
        return item == Items.ENCHANTED_BOOK || item == Items.POTION || item == Items.SPLASH_POTION
                || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW
                || item == Items.SUSPICIOUS_STEW || item == Items.OMINOUS_BOTTLE;
    }

    private static ResourceLocation stewKey(ItemStack stack) {
        SuspiciousStewEffects effects = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (effects == null || effects.effects().size() != 1 || !onlyHasExtra(stack, DataComponents.SUSPICIOUS_STEW_EFFECTS)) {
            return null;
        }
        ResourceLocation effectId = effects.effects().get(0).effect().unwrapKey().map(ResourceKey::location).orElse(null);
        if (effectId == null) {
            return null;
        }
        ResourceLocation key = variantKey(Kind.STEW, effectId, 0);
        return KnowledgeManager.getValue(key) > 0 ? key : null;
    }

    private static ResourceLocation ominousKey(ItemStack stack) {
        if (!onlyHasExtra(stack, DataComponents.OMINOUS_BOTTLE_AMPLIFIER)) {
            return null;
        }
        Integer amplifier = stack.get(DataComponents.OMINOUS_BOTTLE_AMPLIFIER);
        ResourceLocation key = variantKey(Kind.OMINOUS, BuiltInRegistries.ITEM.getKey(Items.OMINOUS_BOTTLE), amplifier == null ? 0 : amplifier);
        return KnowledgeManager.getValue(key) > 0 ? key : null;
    }

    private static Kind potionKind(Item item) {
        if (item == Items.POTION) {
            return Kind.POTION;
        }
        if (item == Items.SPLASH_POTION) {
            return Kind.SPLASH;
        }
        if (item == Items.LINGERING_POTION) {
            return Kind.LINGERING;
        }
        return null;
    }

    private static Item potionItem(Kind kind) {
        return switch (kind) {
            case SPLASH -> Items.SPLASH_POTION;
            case LINGERING -> Items.LINGERING_POTION;
            default -> Items.POTION;
        };
    }

    /** Стак не повинен мати ніяких компонентів, крім стандартних і одного дозволеного (назва, опис, чари тощо відхиляються). */
    private static boolean onlyHasExtra(ItemStack stack, DataComponentType<?> extra) {
        Set<DataComponentType<?>> expected = new HashSet<>(new ItemStack(stack.getItem()).getComponents().keySet());
        expected.add(extra);
        return stack.getComponents().keySet().equals(expected);
    }

    private static ResourceLocation potionKey(ItemStack stack, Kind kind) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()
                || !contents.customEffects().isEmpty() || contents.customColor().isPresent()) {
            return null;
        }
        if (!onlyHasExtra(stack, DataComponents.POTION_CONTENTS)) {
            return null;
        }
        ResourceLocation potionId = contents.potion().get().unwrapKey().map(ResourceKey::location).orElse(null);
        if (potionId == null) {
            return null;
        }
        ResourceLocation key = variantKey(kind, potionId, 0);
        return KnowledgeManager.getValue(key) > 0 ? key : null;
    }

    private static ResourceLocation bookKey(ItemStack stack) {
        ItemEnchantments enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (enchantments == null || enchantments.size() != 1) {
            return null; // книги з кількома зачаруваннями вивчити не можна, їх можна покласти у сховище
        }
        if (!onlyHasExtra(stack, DataComponents.STORED_ENCHANTMENTS)) {
            return null;
        }
        var entry = enchantments.entrySet().iterator().next();
        ResourceLocation enchantmentId = entry.getKey().unwrapKey().map(ResourceKey::location).orElse(null);
        if (enchantmentId == null) {
            return null;
        }
        ResourceLocation key = variantKey(Kind.BOOK, enchantmentId, entry.getIntValue());
        return KnowledgeManager.getValue(key) > 0 ? key : null;
    }

    // ---------------------------------------------------------- стак за ключем

    /** Створює стак (1 шт.) за ключем або порожній стак, якщо ключа вже не існує. */
    public static ItemStack stackFor(ResourceLocation key, HolderLookup.Provider lookup) {
        Variant variant = parse(key);
        if (variant == null) {
            Item item = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }

        if (variant.kind() == Kind.STEW) {
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(variant.id()).orElse(null);
            if (effect == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(Items.SUSPICIOUS_STEW);
            stack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS,
                    new SuspiciousStewEffects(List.of(new SuspiciousStewEffects.Entry(effect, 160))));
            return stack;
        }

        if (variant.kind() == Kind.OMINOUS) {
            ItemStack stack = new ItemStack(Items.OMINOUS_BOTTLE);
            stack.set(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, variant.level());
            return stack;
        }

        if (variant.kind() != Kind.BOOK) {
            Holder<Potion> potion = BuiltInRegistries.POTION.getHolder(variant.id()).orElse(null);
            if (potion == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(variant.kind() == Kind.ARROW ? Items.TIPPED_ARROW : potionItem(variant.kind()));
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.of(potion), Optional.empty(), List.of()));
            return stack;
        }

        Holder<Enchantment> enchantment = lookup.lookupOrThrow(Registries.ENCHANTMENT)
                .get(ResourceKey.create(Registries.ENCHANTMENT, variant.id())).orElse(null);
        if (enchantment == null) {
            return ItemStack.EMPTY;
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(enchantment, variant.level());
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }
}
