package com.athensmc.fsshopkeepers.net;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.config.ShopConfig;
import com.athensmc.fsshopkeepers.money.Cash;
import com.athensmc.fsshopkeepers.shop.ShopObjectKind;
import com.athensmc.fsshopkeepers.shop.ShopRegistry;
import com.athensmc.fsshopkeepers.shop.ShopSpawner;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;
import com.athensmc.fsshopkeepers.shop.TradeOffer;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: make this shop look like this.
 *
 * <p>The one message that changes a shop, and therefore the one place every rule about what a shop may be has to be
 * enforced. The client's copy of the shop is treated as a request, not as truth: the name is length-limited, the mob
 * is checked against the allow-list, the price of every row is bounded, the number of rows is capped, and the fields
 * only staff may set are ignored when the sender is a shop's owner rather than staff.</p>
 *
 * <p>Rejecting the whole packet on a bad field, rather than fixing it up quietly, is deliberate for the fields an
 * admin chose - a mob that is not allowed - and the opposite for fields that are merely out of range, which are
 * clamped. The difference is whether silently changing the value would hide a decision the admin made.</p>
 */
public record SaveShopPacket(UUID shopId, String name, int nameColor, boolean nameBold,
        ShopObjectKind objectKind, ResourceLocation entityType, CompoundTag entityData, int linkedAccount,
        boolean forHire, long hireCost, String tradePermission, String cashLabel, String cashColor,
        List<TradeOffer> offers) {

    /** Longest shop name accepted, matching what fits above a mob without covering the ones beside it. */
    public static final int MAX_NAME = 48;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(shopId);
        buf.writeUtf(name, MAX_NAME);
        buf.writeInt(nameColor & 0xFFFFFF);
        buf.writeBoolean(nameBold);
        buf.writeEnum(objectKind);
        buf.writeResourceLocation(entityType);
        buf.writeNbt(entityData);
        buf.writeVarInt(linkedAccount);
        buf.writeBoolean(forHire);
        buf.writeVarLong(hireCost);
        buf.writeUtf(tradePermission, 128);
        buf.writeUtf(cashLabel, 32);
        buf.writeUtf(cashColor, 8);
        buf.writeVarInt(offers.size());
        for (TradeOffer offer : offers) {
            offer.write(buf);
        }
    }

    public static SaveShopPacket decode(FriendlyByteBuf buf) {
        UUID shopId = buf.readUUID();
        String name = buf.readUtf(MAX_NAME);
        int nameColor = buf.readInt() & 0xFFFFFF;
        boolean nameBold = buf.readBoolean();
        ShopObjectKind kind = buf.readEnum(ShopObjectKind.class);
        ResourceLocation entityType = buf.readResourceLocation();
        CompoundTag entityData = buf.readNbt();
        int account = buf.readVarInt();
        boolean forHire = buf.readBoolean();
        long hireCost = buf.readVarLong();
        String permission = buf.readUtf(128);
        String cashLabel = buf.readUtf(32);
        String cashColor = buf.readUtf(8);
        int count = buf.readVarInt();
        // Bounded before allocating: a hostile client claiming a million rows should not be able to make the
        // server reserve the memory for them before the cap is checked.
        int safeCount = Math.min(count, ShopConfig.get().maxTradesPerShop);
        List<TradeOffer> offers = new ArrayList<>(Math.max(0, safeCount));
        for (int i = 0; i < count; i++) {
            TradeOffer offer = TradeOffer.read(buf);
            if (i < safeCount) {
                offers.add(offer);
            }
        }
        return new SaveShopPacket(shopId, name, nameColor, nameBold, kind, entityType,
                entityData == null ? new CompoundTag() : entityData, account, forHire, hireCost, permission,
                cashLabel, cashColor, offers);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> apply(ctx.getSender()));
        ctx.setPacketHandled(true);
    }

    private void apply(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ShopRegistry registry = ShopRegistry.get(player.server);
        Shopkeeper shop = registry.byId(shopId);
        if (shop == null) {
            player.sendSystemMessage(Component.literal("Esa tienda ya no existe.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!EditorAccess.mayEdit(player, shop)) {
            player.sendSystemMessage(Component.literal("No tienes permiso para editar esta tienda.")
                    .withStyle(ChatFormatting.RED));
            FantasticShopkeepers.LOGGER.warn("{} intento editar la tienda {} sin permiso.",
                    player.getGameProfile().getName(), shopId);
            return;
        }

        ShopConfig config = ShopConfig.get();
        boolean staff = EditorAccess.mayEditStaffFields(player);

        if (objectKind == ShopObjectKind.SIGN && !config.enableSignShops) {
            player.sendSystemMessage(Component.literal("Las tiendas de cartel estan desactivadas.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (objectKind == ShopObjectKind.VIRTUAL && !config.enableVirtualShops) {
            player.sendSystemMessage(Component.literal("Las tiendas virtuales estan desactivadas.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (objectKind == ShopObjectKind.LIVING && !config.allowsEntity(entityType.toString())) {
            player.sendSystemMessage(Component.literal("Ese mob no esta permitido como tendero.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (linkedAccount > 0 && !Cash.ownsAccount(player.server, shop.owner() == null
                ? player.getUUID() : shop.owner(), linkedAccount)) {
            player.sendSystemMessage(Component.literal("Esa cuenta bancaria no es tuya.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ShopObjectKind previousKind = shop.objectKind();
        ResourceLocation previousEntity = shop.entityType();
        CompoundTag previousEntityData = shop.entityData();

        shop.setName(trimName(name));
        shop.setNameStyle(nameColor, nameBold);
        shop.setObjectKind(objectKind);
        shop.setEntityType(entityType);
        shop.setEntityData(entityData);
        shop.setLinkedAccount(linkedAccount);
        shop.setForHire(forHire, hireCost);
        // Only staff may decide who is allowed to trade; an owner's copy of this field is ignored rather than
        // rejected, so an owner saving an unchanged form does not get an error about a field they never saw.
        if (staff) {
            shop.setTradePermission(tradePermission);
            applyMoneyAppearance(player);
        }
        shop.setOffers(sanitiseOffers());

        registry.refresh(shop);

        ServerLevel level = player.server.getLevel(shop.level());
        if (level != null) {
            boolean bodyChanged = previousKind != objectKind || !previousEntity.equals(entityType)
                    || !previousEntityData.equals(shop.entityData());
            if (!objectKind.hasEntity()) {
                ShopSpawner.despawn(level, shop, registry);
            } else {
                // An explicit save is the recovery action for a spawn that Mohist previously rejected.
                ShopSpawner.allowSpawnRetry(shop, registry);
                if (bodyChanged) {
                    ShopSpawner.despawn(level, shop, registry);
                    ShopSpawner.ensureSpawned(level, shop, registry);
                } else {
                    ShopSpawner.refreshAppearance(level, shop, registry);
                }
            }
        }

        int usable = shop.tradableOffers().size();
        int total = shop.offers().size();
        String note = usable == total ? "" : " (" + (total - usable) + " sin terminar)";
        player.sendSystemMessage(Component.literal("Tienda guardada: " + usable + " tratos activos" + note + ".")
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Saves the money's name and colour, which are server-wide rather than the shop's.
     *
     * <p>Edited from the shop editor because that is where staff already are, but written to the config so every shop shows
     * the same money. Only written when something actually changed, so an admin saving a shop's prices does not rewrite the
     * config file for nothing.</p>
     */
    private void applyMoneyAppearance(ServerPlayer player) {
        ShopConfig config = ShopConfig.get();
        String label = cashLabel == null ? "" : cashLabel.strip();
        String colour = cashColor == null ? "" : cashColor.strip().replace("#", "");
        boolean changed = false;

        if (!label.isBlank() && !label.equals(config.cashLabel)) {
            config.cashLabel = label;
            changed = true;
        }
        if (colour.length() == 6 && !colour.equalsIgnoreCase(config.cashColor)) {
            config.cashColor = colour;
            changed = true;
        } else if (colour.length() != 6 && !colour.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "El color del dinero se deja como estaba: hacen falta seis digitos hexadecimales, "
                            + "por ejemplo 55FF55.").withStyle(ChatFormatting.YELLOW));
        }
        if (changed) {
            ShopConfig.save();
            player.sendSystemMessage(Component.literal("Dinero: \"" + config.cashLabel + "\" en #"
                    + config.cashColor + ".").withStyle(ChatFormatting.GREEN));
        }
    }

    private String trimName(String raw) {
        String cleaned = raw == null ? "" : raw.replace("\u00a7", "").strip();
        return cleaned.length() <= MAX_NAME ? cleaned : cleaned.substring(0, MAX_NAME);
    }

    /**
     * Drops rows that are entirely blank and bounds the rest.
     *
     * <p>Blank rows are the editor's way of showing an empty slot, so they arrive in the packet and are simply not
     * stored. Rows that are partly filled are kept: an admin who set an item but no price yet should find their work
     * still there next time they open the editor, rather than having it thrown away as invalid.</p>
     */
    private List<TradeOffer> sanitiseOffers() {
        List<TradeOffer> cleaned = new ArrayList<>(offers.size());
        for (TradeOffer offer : offers) {
            boolean blank = offer.result().isEmpty() && offer.cost1().isEmpty() && offer.cost2().isEmpty()
                    && offer.priceCents() <= 0L;
            if (!blank) {
                cleaned.add(offer);
            }
        }
        return cleaned;
    }
}
