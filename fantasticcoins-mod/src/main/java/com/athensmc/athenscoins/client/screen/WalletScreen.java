package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.AthensCoinsMod;
import com.athensmc.athenscoins.item.ModItems;
import com.athensmc.athenscoins.menu.WalletMenu;
import com.athensmc.athenscoins.wallet.CoinFormat;
import com.athensmc.athenscoins.wallet.CoinType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The wallet GUI.
 *
 * <p>Painted entirely by this screen from the WalletGUI artwork (by SrJulioXD) shipped in the
 * mod's own namespace — no vanilla models, fonts or textures are overridden, so nothing else in
 * the game is affected. The menu has no slots, so the wallet is read-only by construction.</p>
 */
public class WalletScreen extends AbstractContainerScreen<WalletMenu> {

    /** The artwork sits inside a 256x256 sheet at this offset and size. */
    private static final int ART_U = 40;
    private static final int ART_V = 86;
    private static final int ART_W = 176;
    private static final int ART_H = 76;

    /** The "USER" panel that the player's face fills. */
    private static final int FACE_X = 18;
    private static final int FACE_Y = 26;
    private static final int FACE_SIZE = 32;

    /** The six 16x16 cells of the "INFO" grid. */
    private static final int[][] CELLS = {
            { 80, 36 }, { 98, 36 }, { 116, 36 },
            { 80, 53 }, { 98, 53 }, { 116, 53 },
    };

    private static final ResourceLocation ICON_BALANCE =
            new ResourceLocation(AthensCoinsMod.MOD_ID, "textures/gui/icons/balance.png");
    private static final ResourceLocation ICON_DOCUMENTS =
            new ResourceLocation(AthensCoinsMod.MOD_ID, "textures/gui/icons/documents.png");

    /** Wallet colour scheme (1-3), remembered for the session. Click the wallet to cycle it. */
    private static int theme = 1;

    public WalletScreen(WalletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = ART_W;
        this.imageHeight = ART_H;
    }

    private static ResourceLocation walletTexture() {
        return new ResourceLocation(AthensCoinsMod.MOD_ID, "textures/gui/wallet_" + theme + ".png");
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(walletTexture(), leftPos, topPos, ART_U, ART_V, ART_W, ART_H);
    }

    /** The artwork already spells out "USER" and "INFO"; no extra labels needed. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // AbstractContainerScreen#render does not dim the world itself; vanilla screens do it here.
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderFace(graphics);
        renderCells(graphics);
        renderCellTooltip(graphics, mouseX, mouseY);
    }

    private void renderFace(GuiGraphics graphics) {
        ResourceLocation skin = resolveSkin();
        int x = leftPos + FACE_X;
        int y = topPos + FACE_Y;
        RenderSystem.enableBlend();
        // Base head (u=8,v=8) then the hat overlay (u=40,v=8) from the 64x64 skin.
        graphics.blit(skin, x, y, FACE_SIZE, FACE_SIZE, 8.0F, 8.0F, 8, 8, 64, 64);
        graphics.blit(skin, x, y, FACE_SIZE, FACE_SIZE, 40.0F, 8.0F, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    private ResourceLocation resolveSkin() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Player owner = minecraft.level.getPlayerByUUID(menu.ownerId());
            if (owner instanceof AbstractClientPlayer clientPlayer) {
                return clientPlayer.getSkinTextureLocation();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(menu.ownerId());
    }

    private void renderCells(GuiGraphics graphics) {
        // Row 1: the three denominations, showing the digital amount as the cell count.
        for (CoinType type : CoinType.ORDERED) {
            int[] cell = CELLS[type.ordinal()];
            int x = leftPos + cell[0];
            int y = topPos + cell[1];
            graphics.renderItem(new ItemStack(type.item()), x, y);
            drawCount(graphics, CoinFormat.compact(menu.digital(type)), x, y);
        }

        // Row 2: total, account info, bank.
        graphics.blit(ICON_BALANCE, leftPos + CELLS[3][0], topPos + CELLS[3][1],
                0.0F, 0.0F, 16, 16, 16, 16);
        graphics.blit(ICON_DOCUMENTS, leftPos + CELLS[4][0], topPos + CELLS[4][1],
                0.0F, 0.0F, 16, 16, 16, 16);
        graphics.renderItem(new ItemStack(ModItems.ATM_ITEM.get()),
                leftPos + CELLS[5][0], topPos + CELLS[5][1]);
    }

    private void drawCount(GuiGraphics graphics, String text, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.drawString(font, text, x + 17 - font.width(text), y + 9, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    // ------------------------------------------------------------------ tooltips

    private void renderCellTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int hovered = cellAt(mouseX, mouseY);
        if (hovered < 0) {
            return;
        }
        graphics.renderComponentTooltip(font, tooltipFor(hovered), mouseX, mouseY);
    }

    private int cellAt(int mouseX, int mouseY) {
        for (int i = 0; i < CELLS.length; i++) {
            int x = leftPos + CELLS[i][0];
            int y = topPos + CELLS[i][1];
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return i;
            }
        }
        return -1;
    }

    private List<Component> tooltipFor(int index) {
        List<Component> lines = new ArrayList<>();
        if (index < 3) {
            CoinType type = CoinType.ORDERED[index];
            lines.add(type.displayName().copy().withStyle(style -> style.withColor(type.color())));
            lines.add(Component.translatable("tooltip.athens_coins.cash",
                            Component.literal(CoinFormat.plain(menu.cash(type)))
                                    .withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.athens_coins.digital",
                            Component.literal(CoinFormat.plain(menu.digital(type)))
                                    .withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("tooltip.athens_coins.worth", type.bronzeValue())
                    .withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }

        switch (index) {
            case 3 -> {
                lines.add(Component.translatable("tooltip.athens_coins.total")
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("tooltip.athens_coins.total_cash",
                                Component.literal(CoinFormat.plain(menu.totalCashInBronze()))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.athens_coins.total_digital",
                                Component.literal(CoinFormat.plain(menu.totalDigitalInBronze()))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.AQUA));
                lines.add(Component.literal(CoinFormat.breakdown(menu.totalDigitalInBronze()))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            case 4 -> {
                lines.add(Component.translatable("tooltip.athens_coins.account")
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.translatable("tooltip.athens_coins.account_owner",
                                Component.literal(menu.ownerName()).withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.athens_coins.account_rate")
                        .withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("tooltip.athens_coins.account_theme")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            default -> {
                lines.add(Component.translatable("tooltip.athens_coins.bank")
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("tooltip.athens_coins.bank_how")
                        .withStyle(ChatFormatting.GRAY));
                lines.add((menu.atmNearby()
                        ? Component.translatable("tooltip.athens_coins.bank_near")
                        .withStyle(ChatFormatting.GREEN)
                        : Component.translatable("tooltip.athens_coins.bank_far")
                                .withStyle(ChatFormatting.RED)));
            }
        }
        return lines;
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clicking the wallet body (but not a cell) flips through the three colour schemes.
        boolean insideWallet = mouseX >= leftPos && mouseX < leftPos + imageWidth
                && mouseY >= topPos && mouseY < topPos + imageHeight;
        if (insideWallet && cellAt((int) mouseX, (int) mouseY) < 0) {
            theme = theme % 3 + 1;
            if (minecraft != null) {
                minecraft.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                SoundEvents.BOOK_PAGE_TURN, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
