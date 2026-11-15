package com.akashicrecords.compat;

import com.akashicrecords.menu.AkashicMenu;
import com.akashicrecords.menu.CraftSources;
import com.akashicrecords.menu.ModMenus;
import com.akashicrecords.network.FillRecipePayload;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Інтеграція з EMI: кнопка «+» підставляє рецепт у сітку «Демона Лапласа» та книги,
 * беручи предмети з інвентаря, сховища та за Знання. Завантажується лише коли EMI встановлено.
 */
@EmiEntrypoint
public class AkashicEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipeHandler(ModMenus.DEMON.get(), new Handler<>());
        registry.addRecipeHandler(ModMenus.AKASHIC.get(), new Handler<>());
    }

    private static class Handler<T extends AkashicMenu> implements StandardRecipeHandler<T> {
        @Override
        public List<Slot> getInputSources(T menu) {
            List<Slot> list = new ArrayList<>();
            for (int i = 10; i < 46 && i < menu.slots.size(); i++) {
                list.add(menu.slots.get(i));
            }
            return list;
        }

        @Override
        public List<Slot> getCraftingSlots(T menu) {
            List<Slot> list = new ArrayList<>();
            for (int i = 1; i <= 9 && i < menu.slots.size(); i++) {
                list.add(menu.slots.get(i));
            }
            return list;
        }

        @Override
        public Slot getOutputSlot(T menu) {
            return menu.slots.get(0);
        }

        @Override
        public boolean supportsRecipe(EmiRecipe recipe) {
            return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING && recipe.getId() != null;
        }

        @Override
        public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
            Minecraft mc = Minecraft.getInstance();
            ResourceLocation id = recipe.getId();
            if (mc.player == null || mc.level == null || id == null || !context.getScreenHandler().craftingPage) {
                return false;
            }
            return mc.level.getRecipeManager().byKey(id)
                    .map(holder -> CraftSources.canCraftClient(mc.player, holder.value()))
                    .orElse(false);
        }

        @Override
        public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
            ResourceLocation id = recipe.getId();
            if (id == null) {
                return false;
            }
            PacketDistributor.sendToServer(new FillRecipePayload(id, context.getAmount() > 1));
            Minecraft.getInstance().setScreen(context.getScreen());
            return true;
        }
    }
}
