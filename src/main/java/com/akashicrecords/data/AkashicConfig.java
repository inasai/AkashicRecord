package com.akashicrecords.data;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AkashicConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue USE_PROJECTE;
    public static final ModConfigSpec.BooleanValue DERIVE_FROM_RECIPES;
    public static final ModConfigSpec.IntValue STORAGE_SLOTS;
    public static final ModConfigSpec.LongValue ENCHANTED_BOOK_BASE;
    public static final ModConfigSpec.BooleanValue UNLOCK_ALL_RECIPES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        USE_PROJECTE = b
                .comment("Якщо встановлено ProjectE, брати цінність предметів (EMC) з нього як Знання.",
                        "Тоді власні значення моду (файл base.json) і виведення з рецептів не використовуються;",
                        "файли knowledge_values з інших просторів імен (датапаки) усе одно мають пріоритет.")
                .define("useProjectE", true);

        DERIVE_FROM_RECIPES = b
                .comment("Виводити цінність предметів без явного значення з рецептів (верстак, піч, каменеріз).",
                        "Працює лише коли значення не взято з ProjectE.")
                .define("deriveFromRecipes", true);

        STORAGE_SLOTS = b
                .comment("Скільки записів (стаків) вміщує особисте сховище гравця в книзі.")
                .defineInRange("storageSlots", 108, 9, 1000);

        ENCHANTED_BOOK_BASE = b
                .comment("Базове значення для книг зачарувань у формулі:",
                        "Знання = ((base * 10) / вага_зачарування) * 2^(рівень - 1).",
                        "Вага береться з реєстрації зачарування в грі. Після зміни виконайте /akashic recalc.")
                .defineInRange("enchantedBookBase", 8192L, 1L, 1_000_000_000L);

        UNLOCK_ALL_RECIPES = b
                .comment("Відкривати гравцю всі рецепти верстака, коли він відкриває книгу,",
                        "щоб книга рецептів на сторінці «Створення» показувала їх усі (як JEI).")
                .define("unlockAllRecipes", true);

        SPEC = b.build();
    }

    private AkashicConfig() {
    }
}
