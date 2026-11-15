package com.akashicrecords.client;

import com.akashicrecords.AkashicRecords;
import com.akashicrecords.data.AkashicClientConfig;
import com.akashicrecords.data.AkashicClientConfig.HudAnchor;
import com.akashicrecords.item.AkashicBookItem;
import com.akashicrecords.knowledge.KnowledgeManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * HUD, поки гравець тримає книгу: рамка з досягнень, голова гравця, ім'я та Знання;
 * нижче менший такий самий HUD для предмета, на який наведено приціл.
 */
@EventBusSubscriber(modid = AkashicRecords.MODID, value = Dist.CLIENT)
public final class KnowledgeHud {
    private static final ResourceLocation FRAME = ResourceLocation.withDefaultNamespace("advancements/task_frame_obtained");
    private static final ResourceLocation ORB = ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png");
    private static final int FRAME_SIZE = 26;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF80FF20;
    private static final int GRAY = 0xFFAAAAAA;

    private KnowledgeHud() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(AkashicRecords.MODID, "knowledge_hud"), KnowledgeHud::render);
    }

    private static boolean holdingBook(Minecraft mc) {
        return mc.player != null
                && (mc.player.getMainHandItem().getItem() instanceof AkashicBookItem
                || mc.player.getOffhandItem().getItem() instanceof AkashicBookItem);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !holdingBook(mc)
                || !AkashicClientConfig.HUD_ENABLED.get()) {
            return;
        }

        Font font = mc.font;
        float scale = AkashicClientConfig.HUD_SCALE.get().floatValue();
        HudAnchor anchor = AkashicClientConfig.HUD_ANCHOR.get();

        String points = Format.num(ClientKnowledge.points());
        Component name = mc.player.getDisplayName();
        int playerWidth = FRAME_SIZE + 4 + Math.max(font.width(name), 10 + font.width(points));

        ItemStack target = AkashicClientConfig.TARGET_HUD_ENABLED.get() ? targetStack(mc) : ItemStack.EMPTY;
        float targetScale = scale * 0.75F;
        int targetWidth = 0;
        Component targetName = null;
        String targetCost = null;
        if (!target.isEmpty()) {
            targetName = labelOf(target);
            long value = KnowledgeManager.getValue(target);
            targetCost = value > 0 ? Format.num(value) : null;
            int second = targetCost != null ? 10 + font.width(targetCost)
                    : font.width(Component.translatable("gui.akashic_records.hud_no_value"));
            targetWidth = FRAME_SIZE + 4 + Math.max(font.width(targetName), second);
        }

        float totalWidth = Math.max(playerWidth * scale, targetWidth * targetScale);
        float totalHeight = FRAME_SIZE * scale + (target.isEmpty() ? 0 : 4 + FRAME_SIZE * targetScale);

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int ox = AkashicClientConfig.HUD_OFFSET_X.get();
        int oy = AkashicClientConfig.HUD_OFFSET_Y.get();
        float x = switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> ox;
            case TOP_CENTER, BOTTOM_CENTER -> (sw - totalWidth) / 2F;
            default -> sw - ox - totalWidth;
        };
        float y = switch (anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> oy;
            default -> sh - oy - totalHeight;
        };

        // HUD гравця
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.blitSprite(FRAME, 0, 0, FRAME_SIZE, FRAME_SIZE);
        PlayerFaceRenderer.draw(g, mc.player.getSkin(), 5, 5, 16);
        g.drawString(font, name, FRAME_SIZE + 4, 4, WHITE, true);
        drawKnowledge(g, font, FRAME_SIZE + 4, 14, points, GREEN);
        g.pose().popPose();

        // HUD предмета
        if (!target.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x, y + FRAME_SIZE * scale + 4, 0);
            g.pose().scale(targetScale, targetScale, 1.0F);
            g.blitSprite(FRAME, 0, 0, FRAME_SIZE, FRAME_SIZE);
            g.renderItem(target, 5, 5);
            g.drawString(font, targetName, FRAME_SIZE + 4, 4, WHITE, true);
            if (targetCost != null) {
                drawKnowledge(g, font, FRAME_SIZE + 4, 14, targetCost, GREEN);
            } else {
                g.drawString(font, Component.translatable("gui.akashic_records.hud_no_value"), FRAME_SIZE + 4, 14, GRAY, true);
            }
            g.pose().popPose();
        }
    }

    /** Сфера досвіду та число Знань. */
    private static void drawKnowledge(GuiGraphics g, Font font, int x, int y, String text, int color) {
        g.blit(ORB, x, y, 8, 8, 0.0F, 0.0F, 16, 16, 64, 64);
        g.drawString(font, text, x + 10, y, color, true);
    }

    private static ItemStack targetStack(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit == null || mc.level == null || mc.player == null) {
            return ItemStack.EMPTY;
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
            if (state.isAir()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = state.getCloneItemStack(blockHit, mc.level, blockHit.getBlockPos(), mc.player);
            return stack == null ? ItemStack.EMPTY : stack;
        }
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (entity instanceof ItemEntity itemEntity) {
                return itemEntity.getItem();
            }
            ItemStack stack = entity.getPickResult();
            return stack == null ? ItemStack.EMPTY : stack;
        }
        return ItemStack.EMPTY;
    }

    private static Component labelOf(ItemStack stack) {
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
}
