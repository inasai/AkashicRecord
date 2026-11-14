package com.akashicrecords;

import com.akashicrecords.data.AkashicClientConfig;
import com.akashicrecords.data.AkashicConfig;
import com.akashicrecords.data.ModAttachments;
import com.akashicrecords.item.ModItems;
import com.akashicrecords.menu.ModMenus;
import com.akashicrecords.network.ModNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(AkashicRecords.MODID)
public class AkashicRecords {
    public static final String MODID = "akashic_records";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AkashicRecords(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);

        modBus.addListener(ModNetwork::register);
        modBus.addListener(this::addCreative);

        container.registerConfig(ModConfig.Type.COMMON, AkashicConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, AkashicClientConfig.SPEC, "akashic_records-client.toml");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.AKASHIC_BOOK.get());
        }
    }
}
