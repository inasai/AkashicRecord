package com.akashicrecords.menu;

import com.akashicrecords.data.AkashicConfig;
import com.akashicrecords.data.KnowledgeData;
import com.akashicrecords.data.ModAttachments;
import com.akashicrecords.data.StorageData;
import com.akashicrecords.item.AkashicBookItem;
import com.akashicrecords.knowledge.KnowledgeKeys;
import com.akashicrecords.knowledge.KnowledgeManager;
import com.akashicrecords.network.ModNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Меню книги. Влаштоване як меню верстака (ті самі номери комірок), щоб працювала ванільна книга рецептів:
 * комірка 0 це результат, 1..9 сітка 3x3, 10..45 інвентар гравця. Інвентар у книзі не малюється,
 * але потрібен для синхронізації. Сітка й результат видимі лише на сторінці «Створення».
 */
public class AkashicMenu extends RecipeBookMenu<CraftingInput, CraftingRecipe> {
    /** Розташування сітки й результату (відносно лівого верхнього кута книги). */
    public static final int GRID_SLOT_X = 165;
    public static final int GRID_SLOT_Y = 43;
    public static final int RESULT_SLOT_X = 250;
    public static final int RESULT_SLOT_Y = 61;

    protected static final int RESULT_SLOT = 0;
    protected static final int INV_START = 10;
    protected static final int INV_END = 46;

    protected final TransientCraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    protected final Player player;

    /** Лише клієнт: чи відкрита сторінка «Створення» (від цього залежить, чи видно комірки). */
    public boolean craftingPage;

    /** Розкладка комірок: сітка, результат і (за потреби) видимий інвентар. */
    public record Layout(int gridX, int gridY, int resultX, int resultY, int invX, int invY, boolean showInventory) {
        public static final Layout BOOK = new Layout(GRID_SLOT_X, GRID_SLOT_Y, RESULT_SLOT_X, RESULT_SLOT_Y, 0, 0, false);
    }

    public AkashicMenu(int containerId, Inventory inventory) {
        this(ModMenus.AKASHIC.get(), containerId, inventory, Layout.BOOK);
    }

    protected AkashicMenu(net.minecraft.world.inventory.MenuType<?> type, int containerId, Inventory inventory, Layout layout) {
        super(type, containerId);
        this.player = inventory.player;

        this.addSlot(new ResultSlot(inventory.player, this.craftSlots, this.resultSlots, 0, layout.resultX(), layout.resultY()) {
            @Override
            public boolean isActive() {
                return craftingPage;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(this.craftSlots, col + row * 3, layout.gridX() + col * 18, layout.gridY() + row * 18) {
                    @Override
                    public boolean isActive() {
                        return craftingPage;
                    }
                });
            }
        }
        // Інвентар: спершу основні 27 комірок, потім панель швидкого доступу (як у верстака)
        for (int i = 9; i < 36; i++) {
            this.addSlot(inventorySlot(inventory, i, layout, layout.invX() + (i % 9) * 18, layout.invY() + (i / 9 - 1) * 18));
        }
        for (int i = 0; i < 9; i++) {
            this.addSlot(inventorySlot(inventory, i, layout, layout.invX() + i * 18, layout.invY() + 58));
        }
    }

    private static Slot inventorySlot(Inventory inventory, int index, Layout layout, int x, int y) {
        boolean visible = layout.showInventory();
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean isActive() {
                return visible;
            }
        };
    }

    public CraftingContainer craftSlots() {
        return craftSlots;
    }

    // ------------------------------------------------------------- крафт

    @Override
    public void slotsChanged(Container container) {
        if (container == craftSlots) {
            updateResult();
        }
        super.slotsChanged(container);
    }

    private void updateResult() {
        Level level = player.level();
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> recipe =
                level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isPresent()) {
            ItemStack assembled = recipe.get().value().assemble(input, level.registryAccess());
            if (assembled.isItemEnabled(level.enabledFeatures())) {
                result = assembled;
            }
        }
        resultSlots.setItem(0, result);
        setRemoteSlot(0, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, result));
    }

    @Override
    public void handlePlacement(boolean placeAll, RecipeHolder<?> recipe, ServerPlayer serverPlayer) {
        CraftSources.place(serverPlayer, this, craftSlots, recipe.value(), placeAll);
    }

    @Override
    public void fillCraftSlotsStackedContents(StackedContents contents) {
        craftSlots.fillStackedContents(contents);
        CraftSources.fillStackedContents(player, contents);
    }

    @Override
    public void clearCraftingContent() {
        craftSlots.clearContent();
        resultSlots.clearContent();
    }

    @Override
    public boolean recipeMatches(RecipeHolder<CraftingRecipe> recipe) {
        return recipe.value().matches(craftSlots.asCraftInput(), player.level());
    }

    @Override
    public int getResultSlotIndex() {
        return RESULT_SLOT;
    }

    @Override
    public int getGridWidth() {
        return craftSlots.getWidth();
    }

    @Override
    public int getGridHeight() {
        return craftSlots.getHeight();
    }

    @Override
    public int getSize() {
        return 10;
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    @Override
    public boolean shouldMoveToInventory(int slotIndex) {
        return slotIndex != RESULT_SLOT;
    }

    @Override
    public void removed(Player removedBy) {
        super.removed(removedBy);
        this.clearContainer(removedBy, craftSlots);
    }

    @Override
    public ItemStack quickMoveStack(Player quickPlayer, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            if (index == RESULT_SLOT) {
                if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, copy);
            } else if (index >= INV_START && index < INV_END) {
                if (!this.moveItemStackTo(stack, 1, 10, false)) {
                    if (index < 37) {
                        if (!this.moveItemStackTo(stack, 37, INV_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(stack, INV_START, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (!this.moveItemStackTo(stack, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == copy.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(quickPlayer, stack);
            if (index == RESULT_SLOT) {
                quickPlayer.drop(stack, false);
            }
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player stillValidPlayer) {
        return true;
    }

    /**
     * Предмет можна вивчити, якщо для нього є ключ Знань: звичайний предмет із цінністю й без додаткових даних,
     * зілля з відомим ефектом або книга з одним зачаруванням. Саму книгу «Хроніки Акаши» вивчити не можна.
     */
    public static boolean canLearn(ItemStack stack) {
        return KnowledgeKeys.keyOf(stack) != null;
    }

    /** У сховище можна покласти будь-що (і з цінністю, і з чарами), крім самої книги «Хроніки Акаши». */
    public static boolean canStore(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof AkashicBookItem);
    }

    /**
     * Вивчення з інвентаря: вилучає 1 шт. (або всі такі самі, якщо all), дає бали Знань
     * і відкриває ключ для отримання. Усі перевірки виконуються тут, на сервері.
     */
    public void learn(ServerPlayer serverPlayer, ResourceLocation key, boolean all) {
        long value = KnowledgeManager.getValue(key);
        if (value <= 0) {
            return;
        }

        Inventory inventory = serverPlayer.getInventory();
        int wanted = all ? Integer.MAX_VALUE : 1;
        int removed = 0;
        for (int slot = 0; slot < inventory.items.size() && removed < wanted; slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (key.equals(KnowledgeKeys.keyOf(stack))) {
                int amount = Math.min(stack.getCount(), wanted - removed);
                inventory.removeItem(slot, amount);
                removed += amount;
            }
        }
        if (removed <= 0) {
            return;
        }

        KnowledgeData data = serverPlayer.getData(ModAttachments.KNOWLEDGE);
        data.addPoints(saturatedMul(value, removed));
        data.learn(key);

        playBookSound(serverPlayer, SoundEvents.BOOK_PUT, 1.0F);
        ModNetwork.syncKnowledge(serverPlayer);
    }

    /** Отримання за бали Знань. Усі перевірки виконуються тут, на сервері. */
    public void take(ServerPlayer serverPlayer, ResourceLocation key, int requested) {
        long value = KnowledgeManager.getValue(key);
        if (value <= 0) {
            return;
        }
        KnowledgeData data = serverPlayer.getData(ModAttachments.KNOWLEDGE);
        if (!data.hasLearned(key)) {
            return;
        }

        ItemStack prototype = KnowledgeKeys.stackFor(key, serverPlayer.level().registryAccess());
        if (prototype.isEmpty()) {
            return;
        }

        int maxStack = prototype.getMaxStackSize();
        int amount = Mth.clamp(requested, 1, maxStack);
        long affordable = data.getPoints() / value;
        amount = (int) Math.min(amount, affordable);
        if (amount <= 0) {
            return;
        }
        if (!data.spend(value * amount)) {
            return;
        }

        serverPlayer.getInventory().placeItemBackInInventory(prototype.copyWithCount(amount));
        playBookSound(serverPlayer, SoundEvents.BOOK_PAGE_TURN, 1.0F);
        ModNetwork.syncKnowledge(serverPlayer);
    }

    /** Вивчає предмет, що лежить на курсорі (весь стак або 1 шт.). */
    public void learnCarried(ServerPlayer serverPlayer, boolean all) {
        ItemStack carried = getCarried();
        ResourceLocation key = KnowledgeKeys.keyOf(carried);
        if (key == null) {
            return;
        }
        long value = KnowledgeManager.getValue(key);
        if (value <= 0) {
            return;
        }
        int count = all ? carried.getCount() : 1;
        KnowledgeData data = serverPlayer.getData(ModAttachments.KNOWLEDGE);
        data.addPoints(saturatedMul(value, count));
        data.learn(key);
        carried.shrink(count);
        setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        playBookSound(serverPlayer, SoundEvents.BOOK_PUT, 1.0F);
        ModNetwork.syncKnowledge(serverPlayer);
    }

    /** Кладе предмет з комірки інвентаря у сховище (1 шт. або всі такі самі). */
    public void deposit(ServerPlayer serverPlayer, int slot, ResourceLocation id, boolean all) {
        Inventory inventory = serverPlayer.getInventory();
        if (slot < 0 || slot >= inventory.items.size()) {
            return;
        }
        ItemStack clicked = inventory.items.get(slot);
        if (!canStore(clicked) || !BuiltInRegistries.ITEM.getKey(clicked.getItem()).equals(id)) {
            return;
        }

        ItemStack template = clicked.copyWithCount(1);
        StorageData storage = serverPlayer.getData(ModAttachments.STORAGE);
        int capacity = AkashicConfig.STORAGE_SLOTS.get();
        int wanted = all ? Integer.MAX_VALUE : 1;

        List<Integer> order = new ArrayList<>();
        order.add(slot);
        if (all) {
            for (int i = 0; i < inventory.items.size(); i++) {
                if (i != slot) {
                    order.add(i);
                }
            }
        }

        int moved = 0;
        boolean full = false;
        for (int index : order) {
            if (moved >= wanted) {
                break;
            }
            ItemStack stack = inventory.items.get(index);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) {
                continue;
            }
            int amount = Math.min(stack.getCount(), wanted - moved);
            int inserted = storage.insert(template, amount, capacity);
            if (inserted > 0) {
                inventory.removeItem(index, inserted);
                moved += inserted;
            }
            if (inserted < amount) {
                full = true;
                break;
            }
        }

        if (full) {
            serverPlayer.displayClientMessage(Component.translatable("gui.akashic_records.storage_full"), true);
        }
        if (moved > 0) {
            playBookSound(serverPlayer, SoundEvents.BOOK_PUT, 1.0F);
        }
        ModNetwork.syncStorage(serverPlayer);
    }

    /** Забирає предмети із запису сховища в інвентар гравця. */
    public void withdraw(ServerPlayer serverPlayer, int index, ResourceLocation id, int amount) {
        StorageData storage = serverPlayer.getData(ModAttachments.STORAGE);
        if (index < 0 || index >= storage.stacks().size()) {
            return;
        }
        ItemStack stored = storage.stacks().get(index);
        if (!BuiltInRegistries.ITEM.getKey(stored.getItem()).equals(id)) {
            return; // запис змінився, поки клієнт не отримав оновлення
        }

        ItemStack result = storage.extract(index, Mth.clamp(amount, 1, stored.getCount()));
        serverPlayer.getInventory().placeItemBackInInventory(result);
        playBookSound(serverPlayer, SoundEvents.BOOK_PAGE_TURN, 1.0F);
        ModNetwork.syncStorage(serverPlayer);
    }

    /** Бере предмет зі сховища або за Знання й кладе його на курсор (звідти в сітку крафту). */
    public void pick(ServerPlayer serverPlayer, boolean fromStorage, ResourceLocation key, int index, int requested) {
        ItemStack carried = getCarried();
        if (fromStorage) {
            StorageData storage = serverPlayer.getData(ModAttachments.STORAGE);
            if (index < 0 || index >= storage.stacks().size()) {
                return;
            }
            ItemStack stored = storage.stacks().get(index);
            if (!BuiltInRegistries.ITEM.getKey(stored.getItem()).equals(key)) {
                return;
            }
            int room = roomFor(carried, stored);
            int amount = Math.min(Math.min(requested, stored.getCount()), room);
            if (amount <= 0) {
                return;
            }
            ItemStack taken = storage.extract(index, amount);
            setCarried(merge(carried, taken));
            ModNetwork.syncStorage(serverPlayer);
        } else {
            long value = KnowledgeManager.getValue(key);
            KnowledgeData data = serverPlayer.getData(ModAttachments.KNOWLEDGE);
            if (value <= 0 || !data.hasLearned(key)) {
                return;
            }
            ItemStack prototype = KnowledgeKeys.stackFor(key, serverPlayer.level().registryAccess());
            if (prototype.isEmpty()) {
                return;
            }
            int amount = Math.min(requested, roomFor(carried, prototype));
            amount = (int) Math.min(amount, data.getPoints() / value);
            if (amount <= 0 || !data.spend(value * amount)) {
                return;
            }
            setCarried(merge(carried, prototype.copyWithCount(amount)));
            ModNetwork.syncKnowledge(serverPlayer);
        }
        playBookSound(serverPlayer, SoundEvents.BOOK_PAGE_TURN, 1.0F);
    }

    /** Скільки ще предметів такого виду влізе на курсор. */
    private static int roomFor(ItemStack carried, ItemStack incoming) {
        if (carried.isEmpty()) {
            return incoming.getMaxStackSize();
        }
        if (!ItemStack.isSameItemSameComponents(carried, incoming)) {
            return 0;
        }
        return carried.getMaxStackSize() - carried.getCount();
    }

    private static ItemStack merge(ItemStack carried, ItemStack added) {
        if (carried.isEmpty()) {
            return added;
        }
        return carried.copyWithCount(carried.getCount() + added.getCount());
    }

    protected static void playBookSound(ServerPlayer serverPlayer, net.minecraft.sounds.SoundEvent sound, float pitch) {
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(), sound, SoundSource.PLAYERS, 0.8F, pitch);
    }

    protected static long saturatedMul(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
