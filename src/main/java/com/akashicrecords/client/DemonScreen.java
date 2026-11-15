package com.akashicrecords.client;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.knowledge.KnowledgeKeys;
import com.akashicrecords.knowledge.KnowledgeManager;
import com.akashicrecords.menu.DemonMenu;
import com.akashicrecords.network.LearnCursorPayload;
import com.akashicrecords.network.PickSourcePayload;
import com.akashicrecords.network.StorageWithdrawPayload;
import com.akashicrecords.network.TakeItemPayload;
import com.akashicrecords.network.InsertPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * «Демон Лапласа»: темне вікно з двома сторінками й закладками збоку.
 * Права сторінка завжди містить сітку всіх вивчених предметів (покладіть туди предмет, щоб вивчити його).
 * Ліва сторінка залежить від закладки: «Знання» (категорії), «Створення» (сітка крафту) або «Опрацювання» (піч).
 */
public class DemonScreen extends AbstractContainerScreen<DemonMenu> implements RecipeUpdateListener {
    private static final int PAGE_L_X = 8;
    private static final int PAGE_R_X = 134;
    private static final int PAGE_W = 122;
    private static final int PAGE_Y = 30;
    private static final int PAGE_H = 118;

    private static final int CAT_X = 12;
    private static final int CAT_Y = 36;
    private static final int CAT_ROW_H = 15;
    private static final int CAT_ROWS = 7;
    private static final int CAT_W = 112;

    private static final int GRID_X = 140;
    private static final int GRID_Y = 52;
    private static final int COLS = 6;
    private static final int ROWS = 5;
    private static final int CELL = 18;

    private static final int TAB_W = 26;
    private static final int TAB_H = 24;
    private static final int TAB_Y = 34;
    private static final int TAB_STEP = 32;

    // Кольори з моделей предметів: глибокий синьо-сірий, бірюза, аквамарин, індиго
    private static final int BG = 0xFF0A171E;
    private static final int PANEL_EDGE = 0xFF2B2A46;
    private static final int PAGE_BG = 0xFF10212B;
    private static final int PAGE_EDGE = 0xFF243747;
    private static final int CELL_BG = 0xFF0A141A;
    private static final int CELL_EDGE = 0xFF468886;
    private static final int CELL_EDGE_PLAIN = 0xFF1A2F38;
    private static final int TEXT = 0xFFDAFBFF;
    private static final int TEXT_MUTED = 0xFF8F9BC0;
    private static final int ACCENT = 0xFF92F0E0;
    private static final int GOLD = 0xFF7FDCE5;
    private static final int SELECT_BG = 0xFF1A2F38;
    private static final int HOVER_BG = 0x2892F0E0;
    private static final int STORED_EDGE = 0xFF8F9BC0;
    private static final int TAB_EDGE = 0xFF0A141A;
    private static final int BAD_SHADE = 0xB00A141A;

    private static final ResourceLocation ORB = ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png");
    private static final ResourceLocation LIT_SPRITE = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
    private static final ResourceLocation BURN_SPRITE = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

    private enum Tab {
        KNOW(0xFF468886, 0xFF62A096, Items.BOOK, "gui.akashic_records.demon_tab_know"),
        CRAFT(0xFF38566A, 0xFF5E88A0, Items.CRAFTING_TABLE, "gui.akashic_records.demon_tab_craft"),
        PROCESS(0xFF4A5270, 0xFF8F9BC0, Items.FURNACE, "gui.akashic_records.demon_tab_process");

        final int color;
        final int light;
        final Item icon;
        final String label;

        Tab(int color, int light, Item icon, String label) {
            this.color = color;
            this.light = light;
            this.icon = icon;
            this.label = label;
        }
    }

    /** Сортування сітки (вивчені предмети та предмети зі сховища). */
    private enum Sort {
        NAME_ASC("gui.akashic_records.sort_name_asc"),
        NAME_DESC("gui.akashic_records.sort_name_desc"),
        CREATIVE("gui.akashic_records.sort_creative"),
        VALUE_ASC("gui.akashic_records.sort_value_asc"),
        VALUE_DESC("gui.akashic_records.sort_value_desc");

        final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    /** Що показувати в сітці. */
    private enum Filter {
        ALL("gui.akashic_records.filter_all", "gui.akashic_records.filter_all_desc"),
        KNOWN("gui.akashic_records.filter_known", "gui.akashic_records.filter_known_desc"),
        UNKNOWN("gui.akashic_records.filter_unknown", "gui.akashic_records.filter_unknown_desc");

        final String label;
        final String description;

        Filter(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /** Що робить предмет, покладений у сітку. */
    private enum Mode {
        LEARN("gui.akashic_records.mode_learn", "gui.akashic_records.mode_learn_desc"),
        STORE("gui.akashic_records.mode_store", "gui.akashic_records.mode_store_desc");

        final String label;
        final String description;

        Mode(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /** Запам'ятовуються між відкриттями вікна. */
    private static Mode mode = Mode.LEARN;
    private static Sort sort = Sort.NAME_ASC;
    private static Filter filter = Filter.ALL;
    private static final Collator COLLATOR = Collator.getInstance(Locale.of("uk", "UA"));

    /** Клітинка сітки: вивчений предмет (storageIndex = -1) або запис зі сховища. */
    private record Cell(ResourceLocation key, ItemStack stack, int storageIndex, long value, String name) {
        boolean stored() {
            return storageIndex >= 0;
        }
    }

    private record Entry(String key, Component label, ItemStack icon) {
    }

    private record TabInfo(Component name, ItemStack icon, List<Item> items) {
    }

    private final List<TabInfo> creativeTabs = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> visible = new ArrayList<>();
    private final Map<String, Set<ResourceLocation>> tabItemIds = new HashMap<>();
    private final Set<ResourceLocation> anyTabIds = new HashSet<>();
    private final Map<String, Integer> counts = new HashMap<>();
    private final List<Cell> universe = new ArrayList<>();
    private int lastStorageVersion = -1;
    private final Map<ResourceLocation, Integer> creativeOrder = new HashMap<>();
    private final List<Cell> shown = new ArrayList<>();
    private final Map<ResourceLocation, String> nameCache = new HashMap<>();
    private final Map<ResourceLocation, ItemStack> stackCache = new HashMap<>();

    private final RecipeBookComponent recipeBookComponent = new RecipeBookComponent();
    private boolean widthTooNarrow;
    private ImageButton recipeButton;
    private EditBox search;

    private Tab tab = Tab.KNOW;
    private String selectedKey = "all";
    private int catScroll;
    private int gridScroll;
    private int lastVersion = -1;

    public DemonScreen(DemonMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 264;
        this.imageHeight = 240;
    }

    // ------------------------------------------------------------------ ініціалізація

    @Override
    protected void init() {
        super.init();
        loadCreativeTabs();

        search = new EditBox(this.font, leftPos + GRID_X, topPos + 34, 106, 12, Component.translatable("gui.akashic_records.search"));
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setHint(Component.translatable("gui.akashic_records.search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        search.setResponder(text -> {
            gridScroll = 0;
            refreshShown();
        });
        addRenderableWidget(search);

        this.widthTooNarrow = this.width < 450;
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

    private void loadCreativeTabs() {
        creativeTabs.clear();
        creativeOrder.clear();
        Minecraft mc = this.minecraft;
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        try {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), false, mc.level.registryAccess());
        } catch (Exception e) {
            AkashicRecords.LOGGER.warn("[Akashic Records] Не вдалося оновити вкладки творчого інвентаря", e);
        }
        for (CreativeModeTab t : CreativeModeTabs.tabs()) {
            if (t.getType() != CreativeModeTab.Type.CATEGORY) {
                continue;
            }
            LinkedHashSet<Item> items = new LinkedHashSet<>();
            for (ItemStack stack : t.getDisplayItems()) {
                items.add(stack.getItem());
                creativeOrder.putIfAbsent(BuiltInRegistries.ITEM.getKey(stack.getItem()), creativeOrder.size());
            }
            if (!items.isEmpty()) {
                creativeTabs.add(new TabInfo(t.getDisplayName(), t.getIconItem(), new ArrayList<>(items)));
            }
        }
    }

    private boolean craftTab() {
        return tab == Tab.CRAFT;
    }

    /** Ліва сторінка залежить від закладки; книга рецептів зсуває вікно лише на «Створенні». */
    private void relayout() {
        boolean craft = craftTab();
        this.leftPos = craft
                ? recipeBookComponent.updateScreenPosition(this.width, this.imageWidth)
                : (this.width - this.imageWidth) / 2;
        search.setX(leftPos + GRID_X);
        search.setY(topPos + 34);
        recipeButton.visible = craft;
        recipeButton.setPosition(leftPos + 100, topPos + 82);
        this.menu.craftingPage = craft;
        this.menu.furnacePage = tab == Tab.PROCESS;
    }

    // ------------------------------------------------------------------ дані

    private ItemStack stackOf(ResourceLocation key) {
        return stackCache.computeIfAbsent(key, k -> {
            Minecraft mc = this.minecraft;
            if (mc == null || mc.level == null) {
                return ItemStack.EMPTY;
            }
            return KnowledgeKeys.stackFor(k, mc.level.registryAccess());
        });
    }

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

    private ResourceLocation itemId(Cell cell) {
        return BuiltInRegistries.ITEM.getKey(cell.stack().getItem());
    }

    private boolean accepts(Cell cell) {
        return switch (filter) {
            case ALL -> true;
            case KNOWN -> cell.value() > 0;
            case UNKNOWN -> cell.stored() && cell.value() <= 0;
        };
    }

    private void rebuild() {
        lastVersion = ClientKnowledge.version();
        lastStorageVersion = ClientStorage.version();

        universe.clear();
        for (ResourceLocation id : ClientKnowledge.learned()) {
            long value = KnowledgeManager.getValue(id);
            ItemStack stack = stackOf(id);
            if (value <= 0 || stack.isEmpty()) {
                continue;
            }
            Cell cell = new Cell(id, stack, -1, value, labelOf(stack).getString());
            if (accepts(cell)) {
                universe.add(cell);
            }
        }
        List<ItemStack> stored = ClientStorage.stacks();
        for (int i = 0; i < stored.size(); i++) {
            ItemStack stack = stored.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            Cell cell = new Cell(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack, i,
                    KnowledgeManager.getValue(stack), labelOf(stack).getString());
            if (accepts(cell)) {
                universe.add(cell);
            }
        }

        entries.clear();
        tabItemIds.clear();
        anyTabIds.clear();
        entries.add(new Entry("all", Component.translatable("gui.akashic_records.all_tab"), new ItemStack(Items.BOOKSHELF)));
        for (TabInfo info : creativeTabs) {
            String id = "tab:" + info.name().getString();
            Set<ResourceLocation> ids = new HashSet<>();
            for (Item item : info.items()) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                ids.add(key);
                anyTabIds.add(key);
            }
            tabItemIds.put(id, ids);
            entries.add(new Entry(id, info.name(), info.icon()));
        }
        entries.add(new Entry("other", Component.translatable("gui.akashic_records.other_tab"), new ItemStack(Items.CHEST)));
        entries.add(new Entry("potions", Component.translatable("gui.akashic_records.potions_tab"), new ItemStack(Items.POTION)));
        entries.add(new Entry("books", Component.translatable("gui.akashic_records.books_tab"), new ItemStack(Items.ENCHANTED_BOOK)));
        entries.add(new Entry("effects", Component.translatable("gui.akashic_records.effects_tab"), new ItemStack(Items.TIPPED_ARROW)));

        refreshVisible();
        refreshShown();
    }

    /** Чи належить клітинка категорії. */
    private boolean inEntry(Entry entry, Cell cell) {
        String key = entry.key();
        if (key.equals("all")) {
            return true;
        }
        KnowledgeKeys.Variant variant = cell.stored() ? null : KnowledgeKeys.parse(cell.key());
        switch (key) {
            case "other":
                return variant == null && !anyTabIds.contains(itemId(cell));
            case "potions":
                return variant != null && variant.kind().isPotion();
            case "books":
                return variant != null && variant.kind() == KnowledgeKeys.Kind.BOOK;
            case "effects":
                return variant != null && !variant.kind().isPotion() && variant.kind() != KnowledgeKeys.Kind.BOOK;
            default:
                Set<ResourceLocation> ids = tabItemIds.get(key);
                return variant == null && ids != null && ids.contains(itemId(cell));
        }
    }

    /** Категорії, у яких є що показати за поточного фільтра. */
    private void refreshVisible() {
        visible.clear();
        counts.clear();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int n = 0;
            for (Cell cell : universe) {
                if (inEntry(entry, cell)) {
                    n++;
                }
            }
            counts.put(entry.key(), n);
            if (i == 0 || n > 0) {
                visible.add(entry);
            }
        }
        if (selectedEntry() == null) {
            selectedKey = "all";
        }
        catScroll = Math.max(0, Math.min(catScroll, maxCatScroll()));
    }

    private Entry selectedEntry() {
        for (Entry entry : visible) {
            if (entry.key().equals(selectedKey)) {
                return entry;
            }
        }
        return null;
    }

    /** Сітка показує вибрану категорію на «Знаннях» і все на інших закладках. */
    private void refreshShown() {
        shown.clear();
        Entry entry = tab == Tab.KNOW ? selectedEntry() : (visible.isEmpty() ? null : visible.get(0));
        if (entry == null) {
            return;
        }
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (Cell cell : universe) {
            if (inEntry(entry, cell)
                    && (query.isEmpty() || cell.name().toLowerCase(Locale.ROOT).contains(query) || cell.key().getPath().contains(query))) {
                shown.add(cell);
            }
        }
        shown.sort(comparatorFor(sort));
        gridScroll = Math.min(gridScroll, maxGridScroll());
    }

    private Comparator<Cell> comparatorFor(Sort mode) {
        Comparator<Cell> byName = Comparator.comparing(Cell::name, COLLATOR).thenComparingInt(Cell::storageIndex);
        Comparator<Cell> byNameDesc = Comparator.comparing(Cell::name, COLLATOR.reversed()).thenComparingInt(Cell::storageIndex);
        return switch (mode) {
            case NAME_ASC -> byName;
            case NAME_DESC -> byNameDesc;
            case CREATIVE -> Comparator.<Cell>comparingInt(this::creativeRank).thenComparing(byName);
            case VALUE_ASC -> Comparator.<Cell>comparingLong(Cell::value).thenComparing(byName);
            case VALUE_DESC -> Comparator.<Cell>comparingLong(Cell::value).reversed().thenComparing(byName);
        };
    }

    /** Місце у творчому режимі; варіанти (зілля, книги, стріли) після звичайних предметів. */
    private int creativeRank(Cell cell) {
        if (!cell.stored()) {
            KnowledgeKeys.Variant variant = KnowledgeKeys.parse(cell.key());
            if (variant != null) {
                return 1_000_001 + variant.kind().ordinal();
            }
        }
        return creativeOrder.getOrDefault(itemId(cell), 1_000_000);
    }

    /** Оновлює списки, якщо сервер прислав нові дані. */
    private void syncData() {
        if (lastVersion != ClientKnowledge.version() || lastStorageVersion != ClientStorage.version()) {
            rebuild();
        }
    }

    private int gridSize() {
        return shown.size();
    }

    private int maxGridScroll() {
        int rows = (gridSize() + COLS - 1) / COLS;
        return Math.max(0, rows - ROWS);
    }

    private int maxCatScroll() {
        return Math.max(0, visible.size() - CAT_ROWS);
    }

    @Override
    protected void containerTick() {
        syncData();
        if (craftTab()) {
            recipeBookComponent.tick();
        }
    }

    // ------------------------------------------------------------------ геометрія

    private int tabX(Tab t) {
        return leftPos - 22 - (t == tab ? 4 : 0);
    }

    private int tabY(Tab t) {
        return topPos + TAB_Y + t.ordinal() * TAB_STEP;
    }

    private Tab tabAt(double mouseX, double mouseY) {
        for (Tab t : Tab.values()) {
            int x = tabX(t);
            int y = tabY(t);
            if (mouseX >= x && mouseX < x + TAB_W + 4 && mouseY >= y && mouseY < y + TAB_H) {
                return t;
            }
        }
        return null;
    }

    private int catIndexAt(double mouseX, double mouseY) {
        if (tab != Tab.KNOW) {
            return -1;
        }
        double x = mouseX - (leftPos + CAT_X);
        double y = mouseY - (topPos + CAT_Y);
        if (x < 0 || x >= CAT_W || y < 0 || y >= CAT_ROWS * CAT_ROW_H) {
            return -1;
        }
        int index = catScroll + (int) (y / CAT_ROW_H);
        return index < visible.size() ? index : -1;
    }

    private int gridIndexAt(double mouseX, double mouseY) {
        double x = mouseX - (leftPos + GRID_X);
        double y = mouseY - (topPos + GRID_Y);
        if (x < 0 || y < 0 || x >= COLS * CELL || y >= ROWS * CELL) {
            return -1;
        }
        int index = (gridScroll + (int) (y / CELL)) * COLS + (int) (x / CELL);
        return index < gridSize() ? index : -1;
    }

    private boolean overRightPage(double mouseX, double mouseY) {
        return mouseX >= leftPos + PAGE_R_X && mouseX < leftPos + PAGE_R_X + PAGE_W
                && mouseY >= topPos + PAGE_Y && mouseY < topPos + PAGE_Y + PAGE_H;
    }

    // ------------------------------------------------------------------ малювання

    private void frame(GuiGraphics g, int x, int y, int w, int h, int edge, int fill) {
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private void cell(GuiGraphics g, int x, int y, int edge) {
        frame(g, x, y, CELL, CELL, edge, CELL_BG);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;

        // Закладки (під вікном, щоб їхній край виглядав приєднаним)
        for (Tab t : Tab.values()) {
            renderTab(g, t, mouseX, mouseY);
        }

        frame(g, x0, y0, imageWidth, imageHeight, PANEL_EDGE, BG);

        // Заголовок і Знання
        g.fill(x0 + 8, y0 + 8, x0 + 10, y0 + 22, ACCENT);
        g.drawString(this.font, this.title, x0 + 13, y0 + 11, TEXT, false);
        frame(g, x0 + BAR_X, y0 + 7, BAR_W, 16, PAGE_EDGE, PAGE_BG);
        g.blit(ORB, x0 + BAR_X + 6, y0 + 11, 8, 8, 0.0F, 0.0F, 16, 16, 64, 64);
        String points = Component.translatable("gui.akashic_records.knowledge", Format.num(ClientKnowledge.points())).getString();
        g.drawString(this.font, this.font.plainSubstrByWidth(points, MODE_X - BAR_X - 21), x0 + BAR_X + 18, y0 + 11, GOLD, false);
        renderButtons(g, mouseX, mouseY);

        // Сторінки
        boolean dropping = !this.menu.getCarried().isEmpty();
        frame(g, x0 + PAGE_L_X, y0 + PAGE_Y, PAGE_W, PAGE_H, PAGE_EDGE, PAGE_BG);
        frame(g, x0 + PAGE_R_X, y0 + PAGE_Y, PAGE_W, PAGE_H, dropping ? ACCENT : PAGE_EDGE, PAGE_BG);

        renderLeftPage(g, mouseX, mouseY);
        renderRightPage(g, mouseX, mouseY);

        // Інвентар гравця
        frame(g, x0 + INV_PANEL_X, y0 + INV_PANEL_Y, INV_PANEL_W, INV_PANEL_H, PAGE_EDGE, PAGE_BG);
        for (int i = 10; i < 46 && i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            cell(g, x0 + slot.x - 1, y0 + slot.y - 1, CELL_EDGE_PLAIN);
        }
    }

    private static final int BAR_X = 98;
    private static final int BAR_W = 158;
    private static final int SORT_X = BAR_X + BAR_W - 14;
    private static final int SORT_Y = 9;
    private static final int SORT_S = 12;
    private static final int FILTER_X = SORT_X - 13;
    private static final int MODE_X = FILTER_X - 13;

    private static final int INV_PANEL_X = 8;
    private static final int INV_PANEL_Y = 152;
    private static final int INV_PANEL_W = 248;
    private static final int INV_PANEL_H = 84;

    private void renderTab(GuiGraphics g, Tab t, int mouseX, int mouseY) {
        int x = tabX(t);
        int y = tabY(t);
        boolean selected = t == tab;
        boolean hover = tabAt(mouseX, mouseY) == t;
        int color = selected || hover ? t.light : t.color;
        int width = TAB_W + 4;
        for (int r = 0; r < TAB_H; r++) {
            int notch = Math.max(0, 6 - Math.abs(r - TAB_H / 2) / 2); // «ластівчин хвіст» на лівому краю
            g.fill(x + notch, y + r, x + width, y + r + 1, color);
        }
        g.fill(x + 3, y, x + width, y + 1, TAB_EDGE);
        g.fill(x + 3, y + TAB_H - 1, x + width, y + TAB_H, TAB_EDGE);
        g.renderItem(new ItemStack(t.icon), x + 9, y + 4);
    }

    private void renderLeftPage(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;
        switch (tab) {
            case KNOW -> {
                int hovered = catIndexAt(mouseX, mouseY);
                for (int row = 0; row < CAT_ROWS; row++) {
                    int index = catScroll + row;
                    if (index >= visible.size()) {
                        break;
                    }
                    Entry entry = visible.get(index);
                    int y = y0 + CAT_Y + row * CAT_ROW_H;
                    boolean selected = entry.key().equals(selectedKey);
                    if (selected) {
                        g.fill(x0 + CAT_X, y, x0 + CAT_X + CAT_W, y + CAT_ROW_H - 1, SELECT_BG);
                        g.fill(x0 + CAT_X, y, x0 + CAT_X + 2, y + CAT_ROW_H - 1, ACCENT);
                    } else if (index == hovered) {
                        g.fill(x0 + CAT_X, y, x0 + CAT_X + CAT_W, y + CAT_ROW_H - 1, HOVER_BG);
                    }
                    renderSmallItem(g, entry.icon(), x0 + CAT_X + 4, y + 1);
                    String count = Format.num(counts.getOrDefault(entry.key(), 0));
                    int countWidth = this.font.width(count);
                    String label = this.font.plainSubstrByWidth(entry.label().getString(), CAT_W - 20 - countWidth - 6);
                    g.drawString(this.font, label, x0 + CAT_X + 20, y + 4, selected ? TEXT : TEXT_MUTED, false);
                    g.drawString(this.font, count, x0 + CAT_X + CAT_W - 4 - countWidth, y + 4, TEXT_MUTED, false);
                }
                renderScrollbar(g, x0 + CAT_X + CAT_W + 1, y0 + CAT_Y, CAT_ROWS * CAT_ROW_H, visible.size(), CAT_ROWS, catScroll);
            }
            case CRAFT -> {
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        cell(g, x0 + DemonMenu.GRID_X - 1 + col * 18, y0 + DemonMenu.GRID_Y - 1 + row * 18, CELL_EDGE);
                    }
                }
                frame(g, x0 + DemonMenu.RESULT_X - 5, y0 + DemonMenu.RESULT_Y - 5, 26, 26, CELL_EDGE, CELL_BG);
                int ax = x0 + 75;
                int ay = y0 + 58;
                g.fill(ax, ay + 2, ax + 12, ay + 4, TEXT_MUTED);
                g.fill(ax + 10, ay, ax + 12, ay + 6, TEXT_MUTED);
                g.fill(ax + 12, ay + 1, ax + 14, ay + 5, TEXT_MUTED);
                g.fill(ax + 14, ay + 2, ax + 16, ay + 4, TEXT_MUTED);
                int y = y0 + 108;
                for (var line : this.font.split(Component.translatable("gui.akashic_records.demon_craft_hint"), PAGE_W - 12)) {
                    g.drawString(this.font, line, x0 + PAGE_L_X + 6, y, TEXT_MUTED, false);
                    y += 10;
                }
            }
            case PROCESS -> {
                cell(g, x0 + DemonMenu.FURNACE_IN_X - 1, y0 + DemonMenu.FURNACE_IN_Y - 1, CELL_EDGE);
                cell(g, x0 + DemonMenu.FURNACE_FUEL_X - 1, y0 + DemonMenu.FURNACE_FUEL_Y - 1, CELL_EDGE);
                frame(g, x0 + DemonMenu.FURNACE_OUT_X - 5, y0 + DemonMenu.FURNACE_OUT_Y - 5, 26, 26, CELL_EDGE, CELL_BG);

                int flameX = x0 + DemonMenu.FURNACE_IN_X + 1;
                int flameY = y0 + 56;
                g.fill(flameX, flameY, flameX + 14, flameY + 14, 0xFF0A141A);
                if (this.menu.isLit()) {
                    int lit = this.menu.litScaled();
                    g.blitSprite(LIT_SPRITE, 14, 14, 0, 14 - lit, flameX, flameY + 14 - lit, 14, lit);
                }
                int arrowX = x0 + 59;
                int arrowY = y0 + 60;
                g.fill(arrowX, arrowY, arrowX + 24, arrowY + 16, 0xFF0A141A);
                g.blitSprite(BURN_SPRITE, 24, 16, 0, 0, arrowX, arrowY, this.menu.cookScaled() + 1, 16);
            }
        }
    }

    private void renderRightPage(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;
        frame(g, x0 + PAGE_R_X + 6, y0 + 33, PAGE_W - 12, 14, PAGE_EDGE, CELL_BG);

        int hovered = gridIndexAt(mouseX, mouseY);
        long points = ClientKnowledge.points();
        int size = gridSize();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int x = x0 + GRID_X + col * CELL;
                int y = y0 + GRID_Y + row * CELL;
                int index = (gridScroll + row) * COLS + col;
                boolean filled = index < size;
                boolean storedCell = filled && shown.get(index).stored();
                cell(g, x, y, index == hovered ? ACCENT : storedCell ? STORED_EDGE : filled ? CELL_EDGE : CELL_EDGE_PLAIN);
                if (!filled) {
                    continue;
                }
                Cell data = shown.get(index);
                g.renderItem(data.stack(), x + 1, y + 1);
                if (data.stored()) {
                    g.renderItemDecorations(this.font, data.stack(), x + 1, y + 1);
                } else if (data.value() > points) {
                    g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, BAD_SHADE);
                }
            }
        }
        renderScrollbar(g, x0 + GRID_X + COLS * CELL + 2, y0 + GRID_Y, ROWS * CELL, (size + COLS - 1) / COLS, ROWS, gridScroll);

        if (size == 0) {
            String message = universe.isEmpty() && filter == Filter.ALL && search.getValue().isEmpty()
                    ? "gui.akashic_records.demon_empty" : "gui.akashic_records.nothing_found";
            int y = y0 + GRID_Y + 32;
            for (var line : this.font.split(Component.translatable(message), COLS * CELL - 10)) {
                g.drawString(this.font, line, x0 + GRID_X + (COLS * CELL - this.font.width(line)) / 2, y, TEXT_MUTED, false);
                y += 10;
            }
        }
    }

    // ------------------------------------------------------------------ кнопка сортування

    private boolean overSort(double mouseX, double mouseY) {
        return mouseX >= leftPos + SORT_X && mouseX < leftPos + SORT_X + SORT_S
                && mouseY >= topPos + SORT_Y && mouseY < topPos + SORT_Y + SORT_S;
    }

    private boolean overMode(double mouseX, double mouseY) {
        return mouseX >= leftPos + MODE_X && mouseX < leftPos + MODE_X + SORT_S
                && mouseY >= topPos + SORT_Y && mouseY < topPos + SORT_Y + SORT_S;
    }

    private void cycleMode(int step) {
        Mode[] all = Mode.values();
        mode = all[(mode.ordinal() + step + all.length) % all.length];
        playTurnSound();
    }

    private boolean overFilter(double mouseX, double mouseY) {
        return mouseX >= leftPos + FILTER_X && mouseX < leftPos + FILTER_X + SORT_S
                && mouseY >= topPos + SORT_Y && mouseY < topPos + SORT_Y + SORT_S;
    }

    private void renderButtons(GuiGraphics g, int mouseX, int mouseY) {
        // Режим вкладання
        int x = leftPos + MODE_X;
        int y = topPos + SORT_Y;
        boolean hover = overMode(mouseX, mouseY);
        frame(g, x, y, SORT_S, SORT_S, hover ? ACCENT : PAGE_EDGE, hover ? SELECT_BG : CELL_BG);
        int color = hover ? ACCENT : GOLD;
        if (mode == Mode.LEARN) { // кулька досвіду: вивчити
            g.blit(ORB, x + 2, y + 2, 8, 8, 0.0F, 0.0F, 16, 16, 64, 64);
        } else { // скриня: запам'ятати
            g.fill(x + 2, y + 4, x + 10, y + 10, color);
            g.fill(x + 2, y + 6, x + 10, y + 7, CELL_BG);
            g.fill(x + 5, y + 6, x + 7, y + 8, CELL_BG);
        }

        // Фільтр
        x = leftPos + FILTER_X;
        hover = overFilter(mouseX, mouseY);
        frame(g, x, y, SORT_S, SORT_S, hover ? ACCENT : PAGE_EDGE, hover ? SELECT_BG : CELL_BG);
        color = hover ? ACCENT : GOLD;
        switch (filter) {
            case ALL -> { // чотири клітинки
                g.fill(x + 2, y + 2, x + 5, y + 5, color);
                g.fill(x + 7, y + 2, x + 10, y + 5, color);
                g.fill(x + 2, y + 7, x + 5, y + 10, color);
                g.fill(x + 7, y + 7, x + 10, y + 10, color);
            }
            case KNOWN -> { // галочка
                g.fill(x + 2, y + 6, x + 4, y + 7, color);
                g.fill(x + 3, y + 7, x + 5, y + 8, color);
                g.fill(x + 4, y + 8, x + 6, y + 9, color);
                g.fill(x + 5, y + 7, x + 7, y + 8, color);
                g.fill(x + 6, y + 6, x + 8, y + 7, color);
                g.fill(x + 7, y + 4, x + 9, y + 6, color);
                g.fill(x + 8, y + 2, x + 10, y + 4, color);
            }
            case UNKNOWN -> g.drawString(this.font, "?", x + 4, y + 2, color, false);
        }

        // Сортування
        x = leftPos + SORT_X;
        hover = overSort(mouseX, mouseY);
        frame(g, x, y, SORT_S, SORT_S, hover ? ACCENT : PAGE_EDGE, hover ? SELECT_BG : CELL_BG);
        color = hover ? ACCENT : GOLD;
        switch (sort) {
            case NAME_ASC -> { // смужки від довгої до короткої
                g.fill(x + 2, y + 2, x + 10, y + 3, color);
                g.fill(x + 2, y + 4, x + 8, y + 5, color);
                g.fill(x + 2, y + 6, x + 6, y + 7, color);
                g.fill(x + 2, y + 8, x + 4, y + 9, color);
            }
            case NAME_DESC -> { // смужки від короткої до довгої
                g.fill(x + 2, y + 2, x + 4, y + 3, color);
                g.fill(x + 2, y + 4, x + 6, y + 5, color);
                g.fill(x + 2, y + 6, x + 8, y + 7, color);
                g.fill(x + 2, y + 8, x + 10, y + 9, color);
            }
            case CREATIVE -> { // плитки
                g.fill(x + 2, y + 2, x + 5, y + 5, color);
                g.fill(x + 7, y + 2, x + 10, y + 5, color);
                g.fill(x + 4, y + 7, x + 8, y + 10, color);
            }
            case VALUE_ASC -> { // стовпчики вгору
                g.fill(x + 2, y + 8, x + 4, y + 10, color);
                g.fill(x + 5, y + 6, x + 7, y + 10, color);
                g.fill(x + 8, y + 2, x + 10, y + 10, color);
            }
            case VALUE_DESC -> { // стовпчики вниз
                g.fill(x + 2, y + 2, x + 4, y + 10, color);
                g.fill(x + 5, y + 6, x + 7, y + 10, color);
                g.fill(x + 8, y + 8, x + 10, y + 10, color);
            }
        }
    }

    private void cycleSort(int step) {
        Sort[] all = Sort.values();
        sort = all[(sort.ordinal() + step + all.length) % all.length];
        gridScroll = 0;
        refreshShown();
        playTurnSound();
    }

    private void cycleFilter(int step) {
        Filter[] all = Filter.values();
        filter = all[(filter.ordinal() + step + all.length) % all.length];
        gridScroll = 0;
        rebuild();
        playTurnSound();
    }

    private static Component option(String label, boolean current) {
        Component text = Component.translatable(label);
        return current
                ? Component.literal("> ").append(text).withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD)
                : Component.literal("  ").append(text).withStyle(net.minecraft.ChatFormatting.GRAY);
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
        g.fill(x, y, x + 3, y + height, 0x40000000);
        int thumb = Math.max(8, height * visible / total);
        int thumbY = y + (height - thumb) * scroll / (total - visible);
        g.fill(x, thumbY, x + 3, thumbY + thumb, TEXT_MUTED);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Усе малюється в renderBg
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        syncData();
        if (!craftTab()) {
            super.render(g, mouseX, mouseY, partialTick);
            renderCustomTooltip(g, mouseX, mouseY);
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
        renderCustomTooltip(g, mouseX, mouseY);
    }

    /** Підказки кнопок, закладок і сітки малюються явно в render(), після всього іншого. */
    private void renderCustomTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (overSort(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.akashic_records.sort_title").withStyle(net.minecraft.ChatFormatting.WHITE));
            for (Sort mode : Sort.values()) {
                lines.add(option(mode.label, mode == sort));
            }
            lines.add(Component.translatable("gui.akashic_records.sort_scope").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("gui.akashic_records.sort_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
        if (overMode(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.akashic_records.mode_title").withStyle(net.minecraft.ChatFormatting.WHITE));
            for (Mode m : Mode.values()) {
                lines.add(option(m.label, m == mode));
            }
            lines.add(Component.translatable(mode.description).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("gui.akashic_records.mode_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("gui.akashic_records.sort_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
        if (overFilter(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.akashic_records.filter_title").withStyle(net.minecraft.ChatFormatting.WHITE));
            for (Filter mode : Filter.values()) {
                lines.add(option(mode.label, mode == filter));
            }
            lines.add(Component.translatable(filter.description).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("gui.akashic_records.sort_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0) {
            Cell cell = shown.get(index);
            List<Component> lines = new ArrayList<>(getTooltipFromItem(this.minecraft, cell.stack()));
            if (cell.stored()) {
                lines.add(Component.translatable("gui.akashic_records.demon_stored_hint").withStyle(net.minecraft.ChatFormatting.AQUA));
                if (cell.value() <= 0) {
                    lines.add(Component.translatable("gui.akashic_records.demon_unknown_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            }
            lines.add(Component.translatable("gui.akashic_records.demon_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
        Tab t = tabAt(mouseX, mouseY);
        if (t != null) {
            g.renderTooltip(this.font, Component.translatable(t.label), mouseX, mouseY);
            return;
        }
    }

    // ------------------------------------------------------------------ ввід

    private void playTurnSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    private void setTab(Tab target) {
        if (target == tab) {
            return;
        }
        tab = target;
        gridScroll = 0;
        refreshShown();
        relayout();
        playTurnSound();
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot != null && type == ClickType.QUICK_MOVE && mouseButton == 1 && slotId >= 10 && slotId < 46) {
            // Shift+ПКМ по предмету в інвентарі: вивчити або зберегти (залежно від режиму)
            PacketDistributor.sendToServer(new InsertPayload(mode == Mode.LEARN, slotId, true));
            return;
        }
        ClickType effective = type;
        if (slot != null && craftTab() && slotId == 0 && type == ClickType.PICKUP) {
            effective = ClickType.QUICK_MOVE; // результат одразу йде в інвентар
        }
        super.slotClicked(slot, slotId, mouseButton, effective);
        if (craftTab()) {
            recipeBookComponent.slotClicked(slot);
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        if (tabAt(mouseX, mouseY) != null) {
            return false;
        }
        boolean outside = super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
        if (craftTab()) {
            return recipeBookComponent.hasClickedOutside(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight, mouseButton) && outside;
        }
        return outside;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (craftTab() && !(search != null && search.isFocused()) && recipeBookComponent.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncData();
        if (search != null && search.isFocused() && !search.isMouseOver(mouseX, mouseY)) {
            search.setFocused(false); // клік поза полем пошуку знімає з нього фокус
        }
        if (craftTab()) {
            if (recipeBookComponent.mouseClicked(mouseX, mouseY, button)) {
                setFocused(recipeBookComponent);
                return true;
            }
            if (widthTooNarrow && recipeBookComponent.isVisible()) {
                return true;
            }
        }

        if (overSort(mouseX, mouseY) && (button == 0 || button == 1)) {
            cycleSort(button == 0 ? 1 : -1);
            return true;
        }
        if (overMode(mouseX, mouseY) && (button == 0 || button == 1)) {
            cycleMode(button == 0 ? 1 : -1);
            return true;
        }
        if (overFilter(mouseX, mouseY) && (button == 0 || button == 1)) {
            cycleFilter(button == 0 ? 1 : -1);
            return true;
        }

        Tab clickedTab = tabAt(mouseX, mouseY);
        if (clickedTab != null && button == 0) {
            setTab(clickedTab);
            return true;
        }

        int cat = catIndexAt(mouseX, mouseY);
        if (cat >= 0 && button == 0) {
            Entry entry = visible.get(cat);
            if (!entry.key().equals(selectedKey)) {
                selectedKey = entry.key();
                gridScroll = 0;
                refreshShown();
                playTurnSound();
            }
            return true;
        }

        if (overRightPage(mouseX, mouseY)) {
            ItemStack carried = this.menu.getCarried();
            if (!carried.isEmpty()) {
                // Предмет на курсорі: ЛКМ весь стак, ПКМ один предмет (вивчити або зберегти за режимом)
                PacketDistributor.sendToServer(new InsertPayload(mode == Mode.LEARN, -1, button == 0));
                return true;
            }
            int index = gridIndexAt(mouseX, mouseY);
            if (index >= 0) {
                takeFromGrid(shown.get(index), button);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** ЛКМ: 1 шт. на курсор, ПКМ: половина стака на курсор, Shift+ЛКМ: повний стак в інвентар. */
    private void takeFromGrid(Cell cell, int button) {
        int max = Math.max(1, cell.stack().getMaxStackSize());
        if (cell.stored()) {
            ResourceLocation id = cell.key();
            if (button == 0 && hasShiftDown()) {
                PacketDistributor.sendToServer(new StorageWithdrawPayload(cell.storageIndex(), id, max));
            } else if (button == 0) {
                PacketDistributor.sendToServer(new PickSourcePayload(true, id, cell.storageIndex(), 1));
            } else if (button == 1) {
                PacketDistributor.sendToServer(new PickSourcePayload(true, id, cell.storageIndex(), Math.max(1, max / 2)));
            }
            return;
        }
        ResourceLocation key = cell.key();
        if (cell.value() <= 0) {
            return;
        }
        if (button == 0 && hasShiftDown()) {
            PacketDistributor.sendToServer(new TakeItemPayload(key, max));
        } else if (button == 0) {
            PacketDistributor.sendToServer(new PickSourcePayload(false, key, 0, 1));
        } else if (button == 1) {
            PacketDistributor.sendToServer(new PickSourcePayload(false, key, 0, Math.max(1, max / 2)));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        syncData();
        int direction = scrollY > 0 ? -1 : 1;
        if (mouseX < leftPos + PAGE_R_X) {
            if (tab == Tab.KNOW) {
                catScroll = Math.max(0, Math.min(maxCatScroll(), catScroll + direction));
            }
        } else {
            gridScroll = Math.max(0, Math.min(maxGridScroll(), gridScroll + direction));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search != null && search.isFocused()) {
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput();
        }
        if (craftTab() && recipeBookComponent.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (DemonKeys.OPEN.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
