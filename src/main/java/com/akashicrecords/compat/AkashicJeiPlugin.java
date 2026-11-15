package com.akashicrecords.compat;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.menu.AkashicMenu;
import com.akashicrecords.menu.CraftSources;
import com.akashicrecords.menu.DemonMenu;
import com.akashicrecords.menu.ModMenus;
import com.akashicrecords.network.FillRecipePayload;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Інтеграція з JEI: кнопка «+» підставляє рецепт у сітку «Демона Лапласа» та книги,
 * беручи предмети з інвентаря, сховища та за Знання. Завантажується лише коли JEI встановлено.
 */
@JeiPlugin
public class AkashicJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(
                new Handler<AkashicMenu>(AkashicMenu.class, ModMenus.AKASHIC.get(), helper), RecipeTypes.CRAFTING);
        registration.addRecipeTransferHandler(
                new Handler<DemonMenu>(DemonMenu.class, ModMenus.DEMON.get(), helper), RecipeTypes.CRAFTING);
    }

    private static class Handler<C extends AkashicMenu> implements IRecipeTransferHandler<C, RecipeHolder<CraftingRecipe>> {
        private final Class<C> menuClass;
        private final MenuType<C> menuType;
        private final IRecipeTransferHandlerHelper helper;

        Handler(Class<C> menuClass, MenuType<C> menuType, IRecipeTransferHandlerHelper helper) {
            this.menuClass = menuClass;
            this.menuType = menuType;
            this.helper = helper;
        }

        @Override
        public Class<? extends C> getContainerClass() {
            return menuClass;
        }

        @Override
        public Optional<MenuType<C>> getMenuType() {
            return Optional.of(menuType);
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Override
        public @Nullable IRecipeTransferError transferRecipe(C menu, RecipeHolder<CraftingRecipe> recipe,
                                                              IRecipeSlotsView recipeSlots, Player player,
                                                              boolean maxTransfer, boolean doTransfer) {
            if (!menu.craftingPage) {
                return helper.createUserErrorWithTooltip(Component.translatable("gui.akashic_records.craft_open_tab"));
            }
            if (!CraftSources.canCraftClient(player, recipe.value())) {
                return helper.createUserErrorWithTooltip(Component.translatable("gui.akashic_records.craft_missing"));
            }
            if (doTransfer) {
                PacketDistributor.sendToServer(new FillRecipePayload(recipe.id(), maxTransfer));
            }
            return null;
        }
    }
}
