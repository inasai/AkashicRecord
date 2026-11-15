package com.akashicrecords.data;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AkashicConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue USE_PROJECTE;
    public static final ModConfigSpec.BooleanValue DERIVE_FROM_RECIPES;
    public static final ModConfigSpec.IntValue STORAGE_SLOTS;
    public static final ModConfigSpec.LongValue ENCHANTED_BOOK_BASE;
    public static final ModConfigSpec.BooleanValue UNLOCK_ALL_RECIPES;
    public static final ModConfigSpec.LongValue POTION_EFFECT_BASE;
    public static final ModConfigSpec.LongValue OMINOUS_BOTTLE_BASE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        USE_PROJECTE = b
                .comment("Якщо встановлено ProjectE, брати цінність предметів (EMC) з нього як Знання.",
                        "Тоді власні значення моду (файл base.json) і виведення з рецептів не використовуються;",
                        "файли knowledge_values з інших просторів імен (датапаки) усе одно мають пріоритет.")
                .translation("akashic_records.configuration.useProjectE")
                .define("useProjectE", true);

        DERIVE_FROM_RECIPES = b
                .comment("Виводити цінність предметів без явного значення з рецептів (верстак, піч, каменеріз).",
                        "Працює лише коли значення не взято з ProjectE.")
                .translation("akashic_records.configuration.deriveFromRecipes")
                .define("deriveFromRecipes", true);

        STORAGE_SLOTS = b
                .comment("Скільки записів (стаків) вміщує особисте сховище гравця в книзі.")
                .translation("akashic_records.configuration.storageSlots")
                .defineInRange("storageSlots", 108, 9, 1000);

        ENCHANTED_BOOK_BASE = b
                .comment("Базове значення для книг зачарувань у формулі:",
                        "Знання = ((base * 10) / вага_зачарування) * 2^(рівень - 1).",
                        "Вага береться з реєстрації зачарування в грі. Після зміни виконайте /akashic recalc.")
                .translation("akashic_records.configuration.enchantedBookBase")
                .defineInRange("enchantedBookBase", 8192L, 1L, 1_000_000_000L);

        UNLOCK_ALL_RECIPES = b
                .comment("Відкривати гравцю всі рецепти верстака, коли він відкриває книгу,",
                        "щоб книга рецептів на сторінці «Створення» показувала їх усі (як JEI).")
                .translation("akashic_records.configuration.unlockAllRecipes")
                .define("unlockAllRecipes", false);

        POTION_EFFECT_BASE = b
                .comment("Зілля без значення в таблиці (інших модів) рахуються за формулою:",
                        "за кожен ефект: база * хвилини (з округленням вгору) * 2^(рівень підсилювача);",
                        "миттєві ефекти вважаються за одну хвилину. До суми додається 16 (зілля), 64 (вибухове) або 256 (осідальне).",
                        "Це база за хвилину ефекту. Після зміни виконайте /akashic recalc.")
                .translation("akashic_records.configuration.potionEffectBase")
                .defineInRange("potionEffectBase", 1024L, 1L, 1_000_000_000L);

        OMINOUS_BOTTLE_BASE = b
                .comment("Вартість зловісної пляшки рівня 0; кожен наступний рівень (до 4) удвічі дорожчий.")
                .translation("akashic_records.configuration.ominousBottleBase")
                .defineInRange("ominousBottleBase", 512L, 1L, 1_000_000_000L);

        SPEC = b.build();
    }

    private AkashicConfig() {
    }
}
