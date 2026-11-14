package com.akashicrecords.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Підключає екран налаштувань (Моди -> Хроніки Акаши -> Налаштування); тексти беруться з файлів мови. */
public final class ClientConfigScreen {
    private ClientConfigScreen() {
    }

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
