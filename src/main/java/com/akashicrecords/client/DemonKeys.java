package com.akashicrecords.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Клавіша відкриття «Демона Лапласа» (за замовчуванням X). */
public final class DemonKeys {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.akashic_records.demon", InputConstants.Type.KEYSYM, InputConstants.KEY_X, "key.categories.akashic_records");

    private DemonKeys() {
    }
}
