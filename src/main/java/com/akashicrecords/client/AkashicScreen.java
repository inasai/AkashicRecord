package com.akashicrecords.client;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.knowledge.KnowledgeKeys;
import com.akashicrecords.knowledge.KnowledgeManager;
import com.akashicrecords.menu.AkashicMenu;
import com.akashicrecords.network.LearnItemPayload;
import com.akashicrecords.network.PickSourcePayload;
import com.akashicrecords.network.StorageDepositPayload;
import com.akashicrecords.network.StorageWithdrawPayload;
import com.akashicrecords.network.TakeItemPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Інтерфейс у вигляді розкритої книги: ліворуч категорії, праворуч рядки предметів.
 * Категорія «Вивчити предмет» показує праворуч предмети з інвентаря гравця.
 */
public class AkashicScreen extends AbstractContainerScreen<AkashicMenu> implements RecipeUpdateListener {
    // Розкладка (відносно лівого верхнього кута книги)
    private static final int PAGE_L_X = 8;
    private static final int PAGE_R_X = 156;
    private static final int PAGE_W = 140;
    private static final int PAGE_Y = 8;
    private static final int PAGE_H = 182;

    private static final int CAT_X = 16;
    private static final int CAT_Y = 42;
    private static final int CAT_ROW_H = 15;
    private static final int CAT_ROWS = 8;
    private static final int CAT_W = 122;

    private static final int GRID_X = 164;
    private static final int GRID_Y = 44;
    private static final int CRAFT_LIST_Y = 100;
    private static final int COLS = 7;
    private static final int ROWS = 7;
    private static final int CRAFT_ROWS = 4;
    private static final int CELL = 18;

    // Кольори
    private static final int COVER_OUTER = 0xFF2B170B;
    private static final int COVER_INNER = 0xFF54301A;
    private static final int SPINE = 0xFF3A2112;
    private static final int PAGE = 0xFFEADBB5;
    private static final int PAGE_SHADE_1 = 0xFFE0CFA2;
    private static final int PAGE_SHADE_2 = 0xFFD3BF8C;
    private static final int INK = 0xFF3B2A1A;
    private static final int INK_MUTED = 0xFF8A7757;
    private static final int INK_GOLD = 0xFF8A5A00;
    private static final int INK_NEW = 0xFF1F7A2A;
    private static final int INK_BAD = 0xFF9A2A2A;
    private static final int LINE = 0xFF9C8658;
    private static final int SELECT_BG = 0xFFD2BB80;
    private static final int HOVER_BG = 0x30000000;
    private static final int CELL_BORDER = 0xFFB9A474;
    private static final int CELL_BG = 0xFFDCCB9C;

    /** Сторінки книги: головна, «Алхімія», «Вивчене». Перемикаються стрілками внизу. */
    private enum Page { MAIN, ALCHEMY, LEARNED }

    private enum Kind { LEARN, CRAFT, STORAGE, ALL, NAV, POTIONS, BOOKS, TAB, OTHER }

    private enum RowType { HEADER, DEPOSIT, STORED, SRC_STORE, SRC_KNOW }

    /** Категорія ліворуч. keys: що вивчено в цій категорії (id предмета або ключ варіанта з KnowledgeKeys). */
    private record Entry(String key, Kind kind, Component label, ItemStack icon, List<ResourceLocation> keys) {
    }

    private record TabInfo(Component name, ItemStack icon, List<Item> items) {
    }

    private record LearnRow(ResourceLocation key, ItemStack stack, int count) {
    }

    /** Рядок сторінки «Сховище»: заголовок розділу, предмет з інвентаря або запис сховища. ref = комірка інвентаря або індекс запису. */
    private record StorageRow(RowType type, Component header, ItemStack stack, int ref, ResourceLocation key) {
    }

    private final List<TabInfo> tabs = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<ResourceLocation> shown = new ArrayList<>();
    private final List<LearnRow> learnRows = new ArrayList<>();
    private final List<StorageRow> storageRows = new ArrayList<>();
    private final Map<ResourceLocation, String> nameCache = new HashMap<>();
    private final Map<ResourceLocation, ItemStack> stackCache = new HashMap<>();

    private final RecipeBookComponent recipeBookComponent = new RecipeBookComponent();
    private boolean widthTooNarrow;
    private ImageButton recipeButton;
    private PageButton backButton;
    private PageButton forwardButton;
    private Page page = Page.MAIN;
    private List<ItemStack> lastStorage = List.of();

    private String selectedKey = "all";
    private int catScroll;
    private int rightScroll;
    private int lastVersion = -1;
    private EditBox search;

    public AkashicScreen(AkashicMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 304;
        this.imageHeight = 198;
    }

    // ------------------------------------------------------------------ дані

    @Override
    protected void init() {
        super.init();
        loadTabs();

        search = new EditBox(this.font, leftPos + GRID_X, topPos + 22, 124, 12, Component.translatable("gui.akashic_records.search"));
        search.setMaxLength(40);
        search.setHint(Component.translatable("gui.akashic_records.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(text -> {
            rightScroll = 0;
            refreshShown();
        });
        addRenderableWidget(search);

        this.widthTooNarrow = this.width < 450; // книга широка, тому книга рецептів потребує місця ліворуч
        recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
        this.addWidget(recipeBookComponent);

        recipeButton = addRenderableWidget(new ImageButton(0, 0, 20, 18, RecipeBookComponent.RECIPE_BUTTON_SPRITES, button -> {
            recipeBookComponent.toggleVisibility();
            relayout();
        }) {
            @Override
            public void playDownSound(SoundManager soundManager) {
                soundManager.play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
            }
        });
        // Стрілки як у «Книги й пера»; звук перегортання вмикає сама кнопка
        backButton = addRenderableWidget(new PageButton(0, 0, false, button -> turnPage(-1), true));
        forwardButton = addRenderableWidget(new PageButton(0, 0, true, button -> turnPage(1), true));

        rebuild();
        relayout();
    }

    @Override
    public void recipesUpdated() {
        recipeBookComponent.recipesUpdated();
    }

    @Override
    public RecipeBookComponent getRecipeBookComponent() {
        return recipeBookComponent;
    }

    private boolean craftMode() {
        Entry entry = selected();
        return entry != null && entry.kind() == Kind.CRAFT;
    }

    private int listTop() {
        return craftMode() ? CRAFT_LIST_Y : GRID_Y;
    }

    private int listRows() {
        return craftMode() ? CRAFT_ROWS : ROWS;
    }

    /** Розставляє елементи, що залежать від сторінки: книга рецептів зсуває книгу вправо лише на «Створенні». */
    private void relayout() {
        boolean craft = craftMode();
        this.leftPos = craft
                ? recipeBookComponent.updateScreenPosition(this.width, this.imageWidth)
                : (this.width - this.imageWidth) / 2;

        search.setX(leftPos + GRID_X);
        search.setY(topPos + 22);
        search.setWidth(craft ? 100 : 124);

        recipeButton.visible = craft;
        recipeButton.setPosition(leftPos + PAGE_R_X + PAGE_W - 28, topPos + 19);

        backButton.visible = page.ordinal() > 0;
        backButton.setPosition(leftPos + PAGE_L_X + 10, topPos + PAGE_Y + PAGE_H - 17);
        forwardButton.visible = page.ordinal() < Page.values().length - 1;
        forwardButton.setPosition(leftPos + PAGE_R_X + PAGE_W - 10 - 23, topPos + PAGE_Y + PAGE_H - 17);

        this.menu.craftingPage = craft;
    }

    private void turnPage(int direction) {
        int target = Math.max(0, Math.min(Page.values().length - 1, page.ordinal() + direction));
        setPage(Page.values()[target]);
    }

    private void setPage(Page target) {
        page = target;
        selectedKey = "";
        catScroll = 0;
        rightScroll = 0;
        rebuild();
        relayout();
    }

    private String defaultKey() {
        if (page == Page.MAIN) {
            return "all";
        }
        return entries.isEmpty() ? "" : entries.get(0).key();
    }

    /** Категорії беруться з вкладок творчого інвентаря, тож збігаються з ванільними та модовими. */
    private void loadTabs() {
        tabs.clear();
        Minecraft mc = this.minecraft;
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        try {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), false, mc.level.registryAccess());
        } catch (Exception e) {
            AkashicRecords.LOGGER.warn("[Akashic Records] Не вдалося оновити вкладки творчого інвентаря", e);
        }
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                continue;
            }
            LinkedHashSet<Item> items = new LinkedHashSet<>();
            for (ItemStack stack : tab.getDisplayItems()) {
                items.add(stack.getItem());
            }
            if (!items.isEmpty()) {
                tabs.add(new TabInfo(tab.getDisplayName(), tab.getIconItem(), new ArrayList<>(items)));
            }
        }
    }

    /** Стак за ключем Знань (для зілля й книг з відповідними даними). */
    private ItemStack stackOf(ResourceLocation key) {
        return stackCache.computeIfAbsent(key, k -> {
            Minecraft mc = this.minecraft;
            if (mc == null || mc.level == null) {
                return ItemStack.EMPTY;
            }
            return KnowledgeKeys.stackFor(k, mc.level.registryAccess());
        });
    }

    /** Назва для списків: у книги зачарувань це саме зачарування з рівнем, а не однакове «Зачарована книга». */
    private Component labelOf(ItemStack stack) {
        if (stack.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (enchantments != null && enchantments.size() == 1) {
                var entry = enchantments.entrySet().iterator().next();
                return Component.translatable("gui.akashic_records.book_label",
                        Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
            }
        }
        return stack.getHoverName();
    }

    private String nameOf(ResourceLocation key) {
        return nameCache.computeIfAbsent(key, k -> labelOf(stackOf(k)).getString());
    }

    private void rebuild() {
        lastVersion = ClientKnowledge.version();

        Set<ResourceLocation> itemKeys = new HashSet<>();
        List<ResourceLocation> potionKeys = new ArrayList<>();
        List<ResourceLocation> bookKeys = new ArrayList<>();
        List<ResourceLocation> effectKeys = new ArrayList<>();
        for (ResourceLocation id : ClientKnowledge.learned()) {
            if (KnowledgeManager.getValue(id) <= 0) { // без цінності (наприклад, сама книга) не показуємо
                continue;
            }
            KnowledgeKeys.Variant variant = KnowledgeKeys.parse(id);
            if (variant == null) {
                if (BuiltInRegistries.ITEM.containsKey(id)) {
                    itemKeys.add(id);
                }
            } else if (variant.kind() == KnowledgeKeys.Kind.BOOK) {
                bookKeys.add(id);
            } else if (!variant.kind().isPotion()) { // стріли, юшка, зловісні пляшки
                effectKeys.add(id);
            } else {
                potionKeys.add(id);
            }
        }

        Comparator<ResourceLocation> byName = Comparator.comparing(this::nameOf);
        potionKeys.sort(byName);
        bookKeys.sort(byName);
        effectKeys.sort(byName);

        entries.clear();
        List<ResourceLocation> all = new ArrayList<>(itemKeys);
        all.addAll(potionKeys);
        all.addAll(bookKeys);
        all.addAll(effectKeys);
        all.sort(byName);

        if (page == Page.MAIN) {
            entries.add(new Entry("learn", Kind.LEARN, Component.translatable("gui.akashic_records.learn_tab"),
                    new ItemStack(Items.WRITABLE_BOOK), List.of()));
            entries.add(new Entry("craft", Kind.CRAFT, Component.translatable("gui.akashic_records.craft_tab"),
                    new ItemStack(Items.CRAFTING_TABLE), List.of()));
            entries.add(new Entry("storage", Kind.STORAGE, Component.translatable("gui.akashic_records.storage_tab"),
                    new ItemStack(Items.ENDER_CHEST), List.of()));
            entries.add(new Entry("all", Kind.ALL, Component.translatable("gui.akashic_records.all_tab"),
                    new ItemStack(Items.BOOKSHELF), all));
            entries.add(new Entry("nav:alchemy", Kind.NAV, Component.translatable("gui.akashic_records.alchemy_tab"),
                    new ItemStack(Items.BREWING_STAND), List.of()));
            entries.add(new Entry("nav:learned", Kind.NAV, Component.translatable("gui.akashic_records.learned_tab"),
                    new ItemStack(Items.KNOWLEDGE_BOOK), List.of()));
        } else if (page == Page.ALCHEMY) {
            entries.add(new Entry("potions", Kind.POTIONS, Component.translatable("gui.akashic_records.potions_tab"),
                    new ItemStack(Items.POTION), potionKeys));
            entries.add(new Entry("books", Kind.BOOKS, Component.translatable("gui.akashic_records.books_tab"),
                    new ItemStack(Items.ENCHANTED_BOOK), bookKeys));
            entries.add(new Entry("effects", Kind.OTHER, Component.translatable("gui.akashic_records.effects_tab"),
                    new ItemStack(Items.TIPPED_ARROW), effectKeys));
        } else {
            Set<ResourceLocation> inAnyTab = new HashSet<>();
            for (TabInfo tab : tabs) {
                List<ResourceLocation> list = new ArrayList<>();
                for (Item item : tab.items()) {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                    inAnyTab.add(key);
                    if (itemKeys.contains(key)) {
                        list.add(key);
                    }
                }
                if (!list.isEmpty()) {
                    entries.add(new Entry("tab:" + tab.name().getString(), Kind.TAB, tab.name(), tab.icon(), list));
                }
            }

            List<ResourceLocation> other = new ArrayList<>();
            for (ResourceLocation key : itemKeys) {
                if (!inAnyTab.contains(key)) {
                    other.add(key);
                }
            }
            if (!other.isEmpty()) {
                other.sort(byName);
                entries.add(new Entry("other", Kind.OTHER, Component.translatable("gui.akashic_records.other_tab"),
                        new ItemStack(Items.CHEST), other));
            }
        }

        if (selected() == null) {
            selectedKey = defaultKey();
        }
        catScroll = Math.min(catScroll, maxCatScroll());
        refreshShown();
    }

    private Entry selected() {
        for (Entry entry : entries) {
            if (entry.key().equals(selectedKey)) {
                return entry;
            }
        }
        return null;
    }

    private boolean learnMode() {
        Entry entry = selected();
        return entry != null && entry.kind() == Kind.LEARN;
    }

    private boolean storageMode() {
        Entry entry = selected();
        return entry != null && (entry.kind() == Kind.STORAGE || entry.kind() == Kind.CRAFT);
    }

    private int rightSize() {
        if (storageMode()) {
            return storageRows.size();
        }
        return learnMode() ? learnRows.size() : shown.size();
    }

    private boolean matchesStack(String query, ItemStack stack) {
        return query.isEmpty() || labelOf(stack).getString().toLowerCase(Locale.ROOT).contains(query);
    }

    /** Два розділи в одному списку: «З інвентаря» (покласти) і «У сховищі» (забрати). */
    private void buildStorageRows(String query) {
        List<ItemStack> groups = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        Minecraft mc = this.minecraft;
        if (mc != null && mc.player != null) {
            List<ItemStack> inventory = mc.player.getInventory().items;
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.get(slot);
                if (!AkashicMenu.canStore(stack)) {
                    continue;
                }
                boolean merged = false;
                for (ItemStack group : groups) {
                    if (ItemStack.isSameItemSameComponents(group, stack)) {
                        group.grow(stack.getCount());
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    groups.add(stack.copy());
                    slots.add(slot);
                }
            }
        }

        List<StorageRow> deposit = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            if (matchesStack(query, groups.get(i))) {
                deposit.add(new StorageRow(RowType.DEPOSIT, null, groups.get(i), slots.get(i), null));
            }
        }

        List<ItemStack> stored = ClientStorage.stacks();
        List<StorageRow> withdraw = new ArrayList<>();
        for (int i = 0; i < stored.size(); i++) {
            if (matchesStack(query, stored.get(i))) {
                withdraw.add(new StorageRow(RowType.STORED, null, stored.get(i), i, null));
            }
        }

        if (!deposit.isEmpty()) {
            storageRows.add(new StorageRow(RowType.HEADER, Component.translatable("gui.akashic_records.storage_inventory"), ItemStack.EMPTY, -1, null));
            storageRows.addAll(deposit);
        }
        if (!withdraw.isEmpty()) {
            storageRows.add(new StorageRow(RowType.HEADER,
                    Component.translatable("gui.akashic_records.storage_stored", stored.size(), ClientStorage.capacity()),
                    ItemStack.EMPTY, -1, null));
            storageRows.addAll(withdraw);
        }
    }

    /** Джерела для сітки крафту: предмети зі сховища та вивчені предмети (за бали Знань). */
    private void buildCraftRows(String query) {
        List<ItemStack> stored = ClientStorage.stacks();
        List<StorageRow> fromStorage = new ArrayList<>();
        for (int i = 0; i < stored.size(); i++) {
            ItemStack stack = stored.get(i);
            if (matchesStack(query, stack)) {
                fromStorage.add(new StorageRow(RowType.SRC_STORE, null, stack, i, BuiltInRegistries.ITEM.getKey(stack.getItem())));
            }
        }

        List<StorageRow> fromKnowledge = new ArrayList<>();
        for (ResourceLocation key : ClientKnowledge.learned()) {
            if (KnowledgeManager.getValue(key) <= 0 || !matches(query, key)) {
                continue;
            }
            ItemStack stack = stackOf(key);
            if (!stack.isEmpty()) {
                fromKnowledge.add(new StorageRow(RowType.SRC_KNOW, null, stack, -1, key));
            }
        }
        fromKnowledge.sort(Comparator.comparing(row -> nameOf(row.key())));

        if (!fromStorage.isEmpty()) {
            storageRows.add(new StorageRow(RowType.HEADER, Component.translatable("gui.akashic_records.craft_from_storage"),
                    ItemStack.EMPTY, -1, null));
            storageRows.addAll(fromStorage);
        }
        if (!fromKnowledge.isEmpty()) {
            storageRows.add(new StorageRow(RowType.HEADER, Component.translatable("gui.akashic_records.craft_from_knowledge"),
                    ItemStack.EMPTY, -1, null));
            storageRows.addAll(fromKnowledge);
        }
    }

    private boolean matches(String query, ResourceLocation key) {
        if (query.isEmpty()) {
            return true;
        }
        return nameOf(key).toLowerCase(Locale.ROOT).contains(query) || key.getPath().contains(query);
    }

    private void refreshShown() {
        Entry entry = selected();
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        shown.clear();
        learnRows.clear();
        storageRows.clear();
        if (entry == null) {
            return;
        }

        if (entry.kind() == Kind.STORAGE) {
            buildStorageRows(query);
        } else if (entry.kind() == Kind.CRAFT) {
            buildCraftRows(query);
        } else if (entry.kind() == Kind.LEARN) {
            Minecraft mc = this.minecraft;
            if (mc != null && mc.player != null) {
                Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
                for (ItemStack stack : mc.player.getInventory().items) {
                    ResourceLocation key = KnowledgeKeys.keyOf(stack);
                    if (key != null) {
                        counts.merge(key, stack.getCount(), Integer::sum);
                    }
                }
                for (Map.Entry<ResourceLocation, Integer> e : counts.entrySet()) {
                    if (matches(query, e.getKey())) {
                        learnRows.add(new LearnRow(e.getKey(), stackOf(e.getKey()), e.getValue()));
                    }
                }
                // Нові предмети першими, далі за назвою
                learnRows.sort(Comparator
                        .comparing((LearnRow row) -> ClientKnowledge.hasLearned(row.key()))
                        .thenComparing(row -> nameOf(row.key())));
            }
        } else {
            for (ResourceLocation key : entry.keys()) {
                if (matches(query, key)) {
                    shown.add(key);
                }
            }
        }
        rightScroll = Math.min(rightScroll, maxRightScroll());
    }

    private int maxCatScroll() {
        return Math.max(0, entries.size() - CAT_ROWS);
    }

    private int maxRightScroll() {
        int total = rightSize();
        return Math.max(0, total - listRows());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (lastVersion != ClientKnowledge.version()) {
            rebuild();
        } else if (craftMode()) {
            recipeBookComponent.tick();
            if (lastStorage != ClientStorage.stacks()) {
                lastStorage = ClientStorage.stacks();
                refreshShown();
            }
        } else if (learnMode() || storageMode()) {
            refreshShown(); // інвентар або сховище могли змінитися
        }
    }

    // --------------------------------------------------------- позиції курсора

    private int catIndexAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - CAT_X;
        double y = mouseY - topPos - CAT_Y;
        if (x < 0 || y < 0 || x >= CAT_W || y >= CAT_ROWS * CAT_ROW_H) {
            return -1;
        }
        int index = catScroll + (int) (y / CAT_ROW_H);
        return index < entries.size() ? index : -1;
    }

    /** Індекс у списку правої сторінки (shown або learnRows) або -1. */
    private int rightIndexAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - GRID_X;
        double y = mouseY - topPos - listTop();
        if (x < 0 || y < 0 || y >= listRows() * CELL) {
            return -1;
        }
        if (x >= COLS * CELL) {
            return -1;
        }
        int index = rightScroll + (int) (y / CELL);
        int size = rightSize();
        return index < size ? index : -1;
    }

    // --------------------------------------------------------------- малювання

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;

        // Обкладинка, сторінки, корінець
        g.fill(x0, y0, x0 + imageWidth, y0 + imageHeight, COVER_OUTER);
        g.fill(x0 + 2, y0 + 2, x0 + imageWidth - 2, y0 + imageHeight - 2, COVER_INNER);
        g.fill(x0 + PAGE_L_X + PAGE_W, y0 + PAGE_Y, x0 + PAGE_R_X, y0 + PAGE_Y + PAGE_H, SPINE);

        g.fill(x0 + PAGE_L_X, y0 + PAGE_Y, x0 + PAGE_L_X + PAGE_W, y0 + PAGE_Y + PAGE_H, PAGE);
        g.fill(x0 + PAGE_L_X + PAGE_W - 7, y0 + PAGE_Y, x0 + PAGE_L_X + PAGE_W, y0 + PAGE_Y + PAGE_H, PAGE_SHADE_1);
        g.fill(x0 + PAGE_L_X + PAGE_W - 3, y0 + PAGE_Y, x0 + PAGE_L_X + PAGE_W, y0 + PAGE_Y + PAGE_H, PAGE_SHADE_2);

        g.fill(x0 + PAGE_R_X, y0 + PAGE_Y, x0 + PAGE_R_X + PAGE_W, y0 + PAGE_Y + PAGE_H, PAGE);
        g.fill(x0 + PAGE_R_X, y0 + PAGE_Y, x0 + PAGE_R_X + 7, y0 + PAGE_Y + PAGE_H, PAGE_SHADE_1);
        g.fill(x0 + PAGE_R_X, y0 + PAGE_Y, x0 + PAGE_R_X + 3, y0 + PAGE_Y + PAGE_H, PAGE_SHADE_2);

        renderLeftPage(g, mouseX, mouseY);
        renderRightPage(g, mouseX, mouseY);
    }

    private void renderLeftPage(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;

        g.drawString(this.font, Component.translatable("container.akashic_records.chronicles"), x0 + CAT_X, y0 + 14, INK, false);
        g.drawString(this.font, Component.translatable("gui.akashic_records.knowledge", Format.num(ClientKnowledge.points())),
                x0 + CAT_X, y0 + 26, INK_GOLD, false);
        g.fill(x0 + CAT_X, y0 + 38, x0 + CAT_X + CAT_W, y0 + 39, LINE);

        int hovered = catIndexAt(mouseX, mouseY);
        for (int row = 0; row < CAT_ROWS; row++) {
            int index = catScroll + row;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            int y = y0 + CAT_Y + row * CAT_ROW_H;

            if (entry.key().equals(selectedKey)) {
                g.fill(x0 + CAT_X, y, x0 + CAT_X + CAT_W, y + CAT_ROW_H - 1, SELECT_BG);
            } else if (index == hovered) {
                g.fill(x0 + CAT_X, y, x0 + CAT_X + CAT_W, y + CAT_ROW_H - 1, HOVER_BG);
            }

            renderSmallItem(g, entry.icon(), x0 + CAT_X + 2, y + 1);

            String count;
            if (entry.kind() == Kind.LEARN || entry.kind() == Kind.CRAFT) {
                count = "";
            } else if (entry.kind() == Kind.NAV) {
                count = ">";
            } else if (entry.kind() == Kind.STORAGE) {
                count = ClientStorage.stacks().size() + "/" + ClientStorage.capacity();
            } else {
                count = Integer.toString(entry.keys().size());
            }
            int countWidth = this.font.width(count);
            String label = this.font.plainSubstrByWidth(entry.label().getString(), CAT_W - 20 - countWidth - 6);
            g.drawString(this.font, label, x0 + CAT_X + 17, y + 4, INK, false);
            if (!count.isEmpty()) {
                g.drawString(this.font, count, x0 + CAT_X + CAT_W - 3 - countWidth, y + 4, INK_MUTED, false);
            }
        }

        renderScrollbar(g, x0 + CAT_X + CAT_W + 2, y0 + CAT_Y, CAT_ROWS * CAT_ROW_H, entries.size(), CAT_ROWS, catScroll);
        if (entries.isEmpty()) {
            int y = y0 + CAT_Y + 20;
            for (FormattedCharSequence line : this.font.split(Component.translatable("gui.akashic_records.learned_empty"), CAT_W)) {
                g.drawString(this.font, line, x0 + CAT_X, y, INK_MUTED, false);
                y += 10;
            }
        }
    }

    private void renderRightPage(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;
        Entry entry = selected();

        if (entry != null) {
            String title = this.font.plainSubstrByWidth(entry.label().getString(), 124);
            g.drawString(this.font, title, x0 + GRID_X, y0 + 12, INK, false);
        }
        g.fill(x0 + GRID_X, y0 + 38, x0 + GRID_X + 124, y0 + 39, LINE);

        if (craftMode()) {
            renderCraftArea(g);
        }

        int hovered = rightIndexAt(mouseX, mouseY);
        long points = ClientKnowledge.points();

        if (storageMode()) {
            renderStorageRows(g, hovered);
        } else if (learnMode()) {
            for (int row = 0; row < listRows(); row++) {
                int index = rightScroll + row;
                if (index >= learnRows.size()) {
                    break;
                }
                LearnRow learnRow = learnRows.get(index);
                int y = y0 + listTop() + row * CELL;
                int x = x0 + GRID_X;

                g.fill(x, y, x + COLS * CELL, y + CELL - 1, CELL_BG);
                if (index == hovered) {
                    g.fill(x, y, x + COLS * CELL, y + CELL - 1, HOVER_BG);
                }
                g.renderItem(learnRow.stack(), x + 1, y + 1);
                g.renderItemDecorations(this.font, learnRow.stack().copyWithCount(learnRow.count()), x + 1, y + 1);

                boolean isNew = !ClientKnowledge.hasLearned(learnRow.key());
                String value = "+" + Format.num(KnowledgeManager.getValue(learnRow.key()));
                int valueWidth = this.font.width(value);
                String name = this.font.plainSubstrByWidth(nameOf(learnRow.key()), COLS * CELL - 22 - valueWidth - 6);
                g.drawString(this.font, name, x + 22, y + 5, isNew ? INK_NEW : INK, false);
                g.drawString(this.font, value, x + COLS * CELL - 3 - valueWidth, y + 5, INK_GOLD, false);
            }
            renderScrollbar(g, x0 + GRID_X + COLS * CELL + 3, y0 + listTop(), listRows() * CELL, learnRows.size(), listRows(), rightScroll);
            if (learnRows.isEmpty()) {
                String key = search != null && !search.getValue().isEmpty()
                        ? "gui.akashic_records.nothing_found" : "gui.akashic_records.learn_empty";
                renderCenteredText(g, Component.translatable(key));
            }
        } else {
            for (int row = 0; row < listRows(); row++) {
                int index = rightScroll + row;
                if (index >= shown.size()) {
                    break;
                }
                ResourceLocation key = shown.get(index);
                int y = y0 + listTop() + row * CELL;
                int x = x0 + GRID_X;

                g.fill(x, y, x + COLS * CELL, y + CELL - 1, CELL_BG);
                if (index == hovered) {
                    g.fill(x, y, x + COLS * CELL, y + CELL - 1, HOVER_BG);
                }
                g.renderItem(stackOf(key), x + 1, y + 1);

                long value = KnowledgeManager.getValue(key);
                boolean affordable = value > 0 && value <= points;
                String cost = Format.num(value);
                int costWidth = this.font.width(cost);
                String name = this.font.plainSubstrByWidth(nameOf(key), COLS * CELL - 22 - costWidth - 6);
                g.drawString(this.font, name, x + 22, y + 5, affordable ? INK : INK_BAD, false);
                g.drawString(this.font, cost, x + COLS * CELL - 3 - costWidth, y + 5, affordable ? INK_GOLD : INK_BAD, false);
            }
            renderScrollbar(g, x0 + GRID_X + COLS * CELL + 3, y0 + listTop(), listRows() * CELL, shown.size(), listRows(), rightScroll);
            if (shown.isEmpty()) {
                String key = search != null && !search.getValue().isEmpty()
                        ? "gui.akashic_records.nothing_found" : "gui.akashic_records.empty";
                renderCenteredText(g, Component.translatable(key));
            }
        }
    }

    /** Рамки під сітку 3x3 і результат; самі предмети малюють комірки меню. */
    private void renderCraftArea(GuiGraphics g) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = leftPos + AkashicMenu.GRID_SLOT_X - 1 + col * 18;
                int sy = topPos + AkashicMenu.GRID_SLOT_Y - 1 + row * 18;
                g.fill(sx, sy, sx + 18, sy + 18, CELL_BORDER);
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, CELL_BG);
            }
        }
        int rx = leftPos + AkashicMenu.RESULT_SLOT_X - 5;
        int ry = topPos + AkashicMenu.RESULT_SLOT_Y - 5;
        g.fill(rx, ry, rx + 26, ry + 26, CELL_BORDER);
        g.fill(rx + 1, ry + 1, rx + 25, ry + 25, CELL_BG);

        int ax = leftPos + 224;
        int ay = topPos + 66;
        g.fill(ax, ay + 2, ax + 12, ay + 4, INK_MUTED);
        g.fill(ax + 10, ay, ax + 12, ay + 6, INK_MUTED);
        g.fill(ax + 12, ay + 1, ax + 14, ay + 5, INK_MUTED);
        g.fill(ax + 14, ay + 2, ax + 16, ay + 4, INK_MUTED);
    }

    private void renderStorageRows(GuiGraphics g, int hovered) {
        int x = leftPos + GRID_X;
        int width = COLS * CELL;

        for (int row = 0; row < listRows(); row++) {
            int index = rightScroll + row;
            if (index >= storageRows.size()) {
                break;
            }
            StorageRow storageRow = storageRows.get(index);
            int y = topPos + listTop() + row * CELL;

            if (storageRow.type() == RowType.HEADER) {
                g.drawString(this.font, this.font.plainSubstrByWidth(storageRow.header().getString(), width - 4),
                        x + 2, y + 5, INK_MUTED, false);
                g.fill(x, y + CELL - 2, x + width, y + CELL - 1, LINE);
                continue;
            }

            g.fill(x, y, x + width, y + CELL - 1, CELL_BG);
            if (index == hovered) {
                g.fill(x, y, x + width, y + CELL - 1, HOVER_BG);
            }
            ItemStack stack = storageRow.stack();
            g.renderItem(stack, x + 1, y + 1);

            String count;
            int nameColor = INK;
            int countColor = INK_MUTED;
            Component label = labelOf(stack);
            if (storageRow.type() == RowType.SRC_KNOW) {
                long value = KnowledgeManager.getValue(storageRow.key());
                boolean affordable = value > 0 && value <= ClientKnowledge.points();
                count = Format.num(value);
                nameColor = affordable ? INK : INK_BAD;
                countColor = affordable ? INK_GOLD : INK_BAD;
            } else {
                count = "×" + Format.num(stack.getCount());
            }
            int countWidth = this.font.width(count);
            String name = this.font.plainSubstrByWidth(label.getString(), width - 22 - countWidth - 6);
            g.drawString(this.font, name, x + 22, y + 5, nameColor, false);
            g.drawString(this.font, count, x + width - 3 - countWidth, y + 5, countColor, false);
        }

        renderScrollbar(g, x + width + 3, topPos + listTop(), listRows() * CELL, storageRows.size(), listRows(), rightScroll);
        if (storageRows.isEmpty()) {
            String key = search != null && !search.getValue().isEmpty()
                    ? "gui.akashic_records.nothing_found"
                    : (craftMode() ? "gui.akashic_records.craft_empty" : "gui.akashic_records.storage_empty");
            renderCenteredText(g, Component.translatable(key));
        }
    }

    private void renderStorageTooltip(GuiGraphics g, int index, int mouseX, int mouseY) {
        StorageRow row = storageRows.get(index);
        if (row.type() == RowType.HEADER) {
            return;
        }
        List<Component> lines = new ArrayList<>(getTooltipFromItem(this.minecraft, row.stack()));
        if (row.type() == RowType.DEPOSIT) {
            lines.add(Component.translatable("gui.akashic_records.storage_deposit_hint", row.stack().getCount())
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (row.type() == RowType.SRC_STORE || row.type() == RowType.SRC_KNOW) {
            lines.add(Component.translatable("gui.akashic_records.craft_pick_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("gui.akashic_records.storage_withdraw_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private void handleStorageClick(int index, int button) {
        StorageRow row = storageRows.get(index);
        if (row.type() == RowType.HEADER) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(row.stack().getItem());
        if (row.type() == RowType.SRC_STORE || row.type() == RowType.SRC_KNOW) {
            int max = Math.max(1, row.stack().getMaxStackSize());
            int amount = button == 0 ? (hasShiftDown() ? max : 1) : Math.max(1, max / 2);
            if (row.type() == RowType.SRC_STORE) {
                PacketDistributor.sendToServer(new PickSourcePayload(true, id, row.ref(), amount));
            } else {
                PacketDistributor.sendToServer(new PickSourcePayload(false, row.key(), 0, amount));
            }
            return;
        }
        if (row.type() == RowType.DEPOSIT) {
            if (button == 0) {
                PacketDistributor.sendToServer(new StorageDepositPayload(row.ref(), id, hasShiftDown()));
            }
        } else {
            int count = row.stack().getCount();
            int amount;
            if (button == 0) {
                amount = hasShiftDown() ? count : 1;
            } else {
                amount = Math.max(1, (count + 1) / 2);
            }
            PacketDistributor.sendToServer(new StorageWithdrawPayload(row.ref(), id, amount));
        }
    }

    private void renderCenteredText(GuiGraphics g, Component text) {
        List<FormattedCharSequence> lines = this.font.split(text, 116);
        int centerX = leftPos + GRID_X + (COLS * CELL) / 2;
        int y = topPos + listTop() + 30;
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, centerX - this.font.width(line) / 2, y, INK_MUTED, false);
            y += 10;
        }
    }

    private void renderSmallItem(GuiGraphics g, ItemStack stack, int x, int y) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.75F, 0.75F, 1.0F);
        g.renderItem(stack, 0, 0);
        pose.popPose();
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int height, int total, int visible, int scroll) {
        if (total <= visible) {
            return;
        }
        g.fill(x, y, x + 3, y + height, 0x30000000);
        int thumb = Math.max(8, height * visible / total);
        int maxScroll = total - visible;
        int thumbY = y + (height - thumb) * scroll / maxScroll;
        g.fill(x, thumbY, x + 3, thumbY + thumb, INK_MUTED);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Усе малюється в renderBg
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int right = rightIndexAt(mouseX, mouseY);
        if (right >= 0) {
            if (storageMode()) {
                renderStorageTooltip(g, right, mouseX, mouseY);
            } else if (learnMode()) {
                LearnRow row = learnRows.get(right);
                ItemStack stack = row.stack();
                boolean isNew = !ClientKnowledge.hasLearned(row.key());
                List<Component> lines = new ArrayList<>(getTooltipFromItem(this.minecraft, stack));
                lines.add(Component.translatable("gui.akashic_records.learn_value",
                        Format.num(KnowledgeManager.getValue(row.key()))).withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable(isNew ? "gui.akashic_records.learn_new" : "gui.akashic_records.learn_known")
                        .withStyle(isNew ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.akashic_records.learn_hint", row.count())
                        .withStyle(ChatFormatting.DARK_GRAY));
                g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            } else {
                ResourceLocation key = shown.get(right);
                ItemStack stack = stackOf(key);
                List<Component> lines = new ArrayList<>(getTooltipFromItem(this.minecraft, stack));
                lines.add(Component.translatable("gui.akashic_records.click_hint").withStyle(ChatFormatting.DARK_GRAY));
                g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            }
        } else {
            super.renderTooltip(g, mouseX, mouseY); // підказки предметів у комірках сітки
        }
    }

    // ------------------------------------------------------------------ ввід

    private void select(int index) {
        Entry entry = entries.get(index);
        if (entry.kind() == Kind.NAV) { // сторінка «перегортається», ліворуч з'являються нові вкладки
            playTurnSound();
            Page target = entry.key().equals("nav:alchemy") ? Page.ALCHEMY : Page.LEARNED;
            setPage(target);
            return;
        }
        if (!entry.key().equals(selectedKey)) {
            selectedKey = entry.key();
            rightScroll = 0;
            refreshShown();
            relayout();
            playTurnSound();
        }
    }

    private void playTurnSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!craftMode()) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        if (recipeBookComponent.isVisible() && widthTooNarrow) {
            this.renderBackground(g, mouseX, mouseY, partialTick);
            recipeBookComponent.render(g, mouseX, mouseY, partialTick);
        } else {
            super.render(g, mouseX, mouseY, partialTick);
            recipeBookComponent.render(g, mouseX, mouseY, partialTick);
            recipeBookComponent.renderGhostRecipe(g, leftPos, topPos, true, partialTick);
        }
        recipeBookComponent.renderTooltip(g, leftPos, topPos, mouseX, mouseY);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        ClickType effective = type;
        if (slot != null && craftMode() && slotId == 0 && type == ClickType.PICKUP) {
            effective = ClickType.QUICK_MOVE; // результат одразу йде в інвентар
        }
        super.slotClicked(slot, slotId, mouseButton, effective);
        if (craftMode()) {
            recipeBookComponent.slotClicked(slot);
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        boolean outside = super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
        if (craftMode()) {
            return recipeBookComponent.hasClickedOutside(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight, mouseButton) && outside;
        }
        return outside;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (craftMode() && !(search != null && search.isFocused()) && recipeBookComponent.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (craftMode()) {
            if (recipeBookComponent.mouseClicked(mouseX, mouseY, button)) {
                setFocused(recipeBookComponent);
                return true;
            }
            if (widthTooNarrow && recipeBookComponent.isVisible()) {
                return true;
            }
        }
        if (button == 0) {
            int category = catIndexAt(mouseX, mouseY);
            if (category >= 0) {
                select(category);
                return true;
            }
        }

        int index = rightIndexAt(mouseX, mouseY);
        if (index >= 0 && (button == 0 || button == 1)) {
            if (storageMode()) {
                handleStorageClick(index, button);
            } else if (learnMode()) {
                if (button == 0) {
                    LearnRow row = learnRows.get(index);
                    PacketDistributor.sendToServer(new LearnItemPayload(row.key(), hasShiftDown()));
                }
            } else {
                ResourceLocation key = shown.get(index);
                int max = Math.max(1, stackOf(key).getMaxStackSize());
                int amount;
                if (button == 0) {
                    amount = hasShiftDown() ? max : 1;
                } else {
                    amount = Math.max(1, max / 2);
                }
                PacketDistributor.sendToServer(new TakeItemPayload(key, amount));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int direction = (int) Math.signum(scrollY);
        if (mouseX < leftPos + PAGE_R_X) {
            catScroll = Math.max(0, Math.min(maxCatScroll(), catScroll - direction));
        } else {
            rightScroll = Math.max(0, Math.min(maxRightScroll(), rightScroll - direction));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (search != null && search.isFocused()) {
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput();
        }
        if (craftMode() && recipeBookComponent.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
