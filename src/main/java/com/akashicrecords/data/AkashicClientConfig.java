package com.akashicrecords.data;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Клієнтські налаштування (файл akashic_records-client.toml): розміщення HUD. */
public final class AkashicClientConfig {
    public enum HudAnchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.BooleanValue TARGET_HUD_ENABLED;
    public static final ModConfigSpec.EnumValue<HudAnchor> HUD_ANCHOR;
    public static final ModConfigSpec.IntValue HUD_OFFSET_X;
    public static final ModConfigSpec.IntValue HUD_OFFSET_Y;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        HUD_ENABLED = b.comment("Показувати HUD з іменем і Знаннями, коли в руці книга «Хроніки Акаши».")
                .translation("akashic_records.configuration.hudEnabled")
                .define("hudEnabled", true);
        TARGET_HUD_ENABLED = b.comment("Показувати другий HUD (назва й вартість) для предмета чи блока, на який наведено приціл.")
                .translation("akashic_records.configuration.targetHudEnabled")
                .define("targetHudEnabled", true);
        HUD_ANCHOR = b.comment("Де розміщується HUD: TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT.")
                .translation("akashic_records.configuration.hudAnchor")
                .defineEnum("hudAnchor", HudAnchor.TOP_LEFT);
        HUD_OFFSET_X = b.comment("Відступ від краю екрана по горизонталі (у пікселях).")
                .translation("akashic_records.configuration.hudOffsetX")
                .defineInRange("hudOffsetX", 6, 0, 2000);
        HUD_OFFSET_Y = b.comment("Відступ від краю екрана по вертикалі (у пікселях).")
                .translation("akashic_records.configuration.hudOffsetY")
                .defineInRange("hudOffsetY", 6, 0, 2000);
        HUD_SCALE = b.comment("Масштаб HUD гравця (HUD предмета на чверть менший).")
                .translation("akashic_records.configuration.hudScale")
                .defineInRange("hudScale", 1.0, 0.5, 3.0);

        SPEC = b.build();
    }

    private AkashicClientConfig() {
    }
}
