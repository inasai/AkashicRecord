package com.akashicrecords.menu;

import com.akashicrecords.data.DemonFurnace;
import com.akashicrecords.data.ModAttachments;
import com.akashicrecords.data.KnowledgeData;
import com.akashicrecords.data.StorageData;
import com.akashicrecords.knowledge.KnowledgeKeys;
import com.akashicrecords.knowledge.KnowledgeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.akashicrecords.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Меню «Демон Лапласа»: усе, що вміє книга (вивчення, отримання, сітка крафту), плюс вбудована піч,
 * а гравцевий інвентар завжди видно. Піч належить гравцеві, її вміст лишається після закриття вікна й плавлення триває.
 */
public class DemonMenu extends AkashicMenu {
    /** Розташування (відносно кута вікна). Сітка й результат на лівій сторінці вкладки «Створення». */
    public static final int GRID_X = 16;
    public static final int GRID_Y = 37;
    public static final int RESULT_X = 102;
    public static final int RESULT_Y = 55;
    public static final int INV_X = 51;
    public static final int INV_Y = 158;

    public static final int FURNACE_IN_X = 31;
    public static final int FURNACE_IN_Y = 36;
    public static final int FURNACE_FUEL_X = 31;
    public static final int FURNACE_FUEL_Y = 78;
    public static final int FURNACE_OUT_X = 95;
    public static final int FURNACE_OUT_Y = 60;

    private static final int FURNACE_FIRST = 46;

    /** Піч належить гравцеві й тікається окремо (ServerEvents), тому плавить і з закритим вікном. */
    private final Container furnace;
    private final ContainerData data; // горіння, тривалість горіння, прогрес, потрібно

    /** Лише клієнт: чи відкрита вкладка «Опрацювання». */
    public boolean furnacePage;

    public DemonMenu(int containerId, Inventory inventory) {
        super(ModMenus.DEMON.get(), containerId, inventory,
                new Layout(GRID_X, GRID_Y, RESULT_X, RESULT_Y, INV_X, INV_Y, true));
        if (inventory.player.level().isClientSide) {
            this.furnace = new SimpleContainer(3);
            this.data = new SimpleContainerData(4);
        } else {
            DemonFurnace state = inventory.player.getData(ModAttachments.FURNACE);
            this.furnace = state.container();
            this.data = state.data();
        }

        this.addSlot(new Slot(furnace, 0, FURNACE_IN_X, FURNACE_IN_Y) {
            @Override
            public boolean isActive() {
                return furnacePage;
            }
        });
        this.addSlot(new Slot(furnace, 1, FURNACE_FUEL_X, FURNACE_FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getBurnTime(RecipeType.SMELTING) > 0;
            }

            @Override
            public boolean isActive() {
                return furnacePage;
            }
        });
        this.addSlot(new Slot(furnace, 2, FURNACE_OUT_X, FURNACE_OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean isActive() {
                return furnacePage;
            }
        });
        this.addDataSlots(data);
    }

    // ------------------------------------------------------------ вкладання предметів

    /**
     * Покласти предмет у «Демона Лапласа»: вивчити (learn) або зберегти у сховище (без обмежень, навіть книгу).
     * slot = -1: предмет на курсорі; інакше комірка інвентаря. Усі перевірки тут, на сервері.
     */
    public void insertFromUi(ServerPlayer serverPlayer, boolean learn, int slot, boolean all) {
        Slot source = null;
        ItemStack stack;
        if (slot < 0) {
            stack = getCarried();
        } else if (slot >= INV_START && slot < INV_END) {
            source = this.slots.get(slot);
            stack = source.getItem();
        } else {
            return;
        }
        if (stack.isEmpty()) {
            return;
        }
        int count = all ? stack.getCount() : 1;

        if (learn) {
            ResourceLocation key = KnowledgeKeys.keyOf(stack);
            long value = key == null ? 0L : KnowledgeManager.getValue(key);
            if (key == null || value <= 0) {
                serverPlayer.displayClientMessage(Component.translatable("gui.akashic_records.demon_cannot_learn"), true);
                return;
            }
            KnowledgeData data = serverPlayer.getData(ModAttachments.KNOWLEDGE);
            data.addPoints(saturatedMul(value, count));
            data.learn(key);
            ModNetwork.syncKnowledge(serverPlayer);
        } else {
            StorageData storage = serverPlayer.getData(ModAttachments.STORAGE);
            storage.insert(stack.copyWithCount(1), count, Integer.MAX_VALUE);
            ModNetwork.syncStorage(serverPlayer);
        }

        stack.shrink(count);
        if (source != null) {
            if (stack.isEmpty()) {
                source.setByPlayer(ItemStack.EMPTY);
            } else {
                source.setChanged();
            }
        } else if (stack.isEmpty()) {
            setCarried(ItemStack.EMPTY);
        }
        playBookSound(serverPlayer, SoundEvents.BOOK_PUT, 1.0F);
    }

    /** Висота полум'я 0..13 */
    public int litScaled() {
        int duration = data.get(1) == 0 ? 200 : data.get(1);
        return data.get(0) * 13 / duration;
    }

    public boolean isLit() {
        return data.get(0) > 0;
    }

    /** Ширина стрілки 0..24 */
    public int cookScaled() {
        int total = data.get(3);
        return total != 0 && data.get(2) != 0 ? data.get(2) * 24 / total : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player quickPlayer, int index) {
        if (index >= INV_START && index < INV_END) {
            // Між основним інвентарем і швидким доступом (а не в сітку крафту, яку може бути не видно)
            Slot slot = this.slots.get(index);
            if (!slot.hasItem()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = slot.getItem();
            ItemStack copy = stack.copy();
            boolean moved = index < 37
                    ? this.moveItemStackTo(stack, 37, INV_END, false)
                    : this.moveItemStackTo(stack, INV_START, 37, false);
            if (!moved) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return copy;
        }
        return super.quickMoveStack(quickPlayer, index);
    }
}
