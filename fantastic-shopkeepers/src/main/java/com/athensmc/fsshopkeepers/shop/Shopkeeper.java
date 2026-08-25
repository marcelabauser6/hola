package com.athensmc.fsshopkeepers.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One shop: who owns it, where it stands, what it looks like and what it sells.
 *
 * <p>Mutable, and edited in place by the server when an admin saves the editor. The list of offers is the one
 * exception: it is replaced wholesale rather than modified row by row, so a buyer whose trade is being validated
 * on the same tick either sees the old set or the new one and never a half-written mixture.</p>
 *
 * <p>The mob's appearance is a registry id plus a bag of NBT rather than a subclass per species. Shopkeepers'
 * original approach was one class for every variant a mob could have - a class for a sheep's colour, a class for a
 * horse's markings, a class for whether a panda is lazy - which is 83 classes for 30 mobs and needs a new one
 * every time Mojang adds a creature. Storing the variant as {@link #entityData} and applying it to the spawned
 * entity gets the same result for any mob the server knows about, including mobs from other mods that nobody
 * wrote a class for.</p>
 */
public final class Shopkeeper {

    private static final String ID = "Id";
    private static final String TYPE = "Type";
    private static final String OWNER = "Owner";
    private static final String OWNER_NAME = "OwnerName";
    private static final String NAME = "Name";
    private static final String NAME_COLOR = "NameColor";
    private static final String NAME_BOLD = "NameBold";
    private static final String OBJECT_KIND = "ObjectKind";
    private static final String ENTITY_TYPE = "EntityType";
    private static final String ENTITY_DATA = "EntityData";
    private static final String LEVEL = "Level";
    private static final String POS = "Pos";
    private static final String ENTITY_ID = "EntityId";
    private static final String CONTAINER = "Container";
    private static final String ACCOUNT = "Account";
    private static final String OFFERS = "Offers";
    private static final String FOR_HIRE = "ForHire";
    private static final String HIRE_COST = "HireCost";
    private static final String TRADE_PERMISSION = "TradePermission";

    private static final int TAG_COMPOUND = 10;

    /** The mob a shop is when nothing else was chosen. */
    public static final ResourceLocation DEFAULT_ENTITY =
            new ResourceLocation("minecraft", "villager");

    /** Default colour for an unformatted shop name. */
    public static final int DEFAULT_NAME_COLOR = 0xFFFFFF;

    private final UUID id;
    private ShopType type;
    private UUID owner;
    private String ownerName = "";
    private String name = "";
    private int nameColor = DEFAULT_NAME_COLOR;
    private boolean nameBold;
    private ShopObjectKind objectKind = ShopObjectKind.LIVING;
    private ResourceLocation entityType = DEFAULT_ENTITY;
    private CompoundTag entityData = new CompoundTag();
    private ResourceKey<Level> level = Level.OVERWORLD;
    private BlockPos pos = BlockPos.ZERO;
    private UUID entityId;
    private BlockPos containerPos;
    private int linkedAccount;
    private List<TradeOffer> offers = List.of();
    private boolean forHire;
    private long hireCost;
    private String tradePermission = "";

    public Shopkeeper(UUID id, ShopType type) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.type = type == null ? ShopType.ADMIN : type;
    }

    /** A new shop with a fresh identity. */
    public static Shopkeeper create(ShopType type, ResourceKey<Level> level, BlockPos pos) {
        Shopkeeper shop = new Shopkeeper(UUID.randomUUID(), type);
        shop.level = level == null ? Level.OVERWORLD : level;
        shop.pos = pos == null ? BlockPos.ZERO : pos.immutable();
        return shop;
    }

    public UUID id() {
        return id;
    }

    public ShopType type() {
        return type;
    }

    public void setType(ShopType type) {
        if (type != null) {
            this.type = type;
        }
    }

    /** The owning player, or null for an admin shop. */
    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    /**
     * The owner's last known name.
     *
     * <p>Stored alongside the uuid rather than looked up, because listing a hundred shops should not need a
     * hundred profile lookups, and because a name is still worth printing for a player who has since left.</p>
     */
    public String ownerName() {
        return ownerName;
    }

    public boolean isOwner(UUID player) {
        return owner != null && owner.equals(player);
    }

    /** The shop's display name, shown above the mob and in listings. */
    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public int nameColor() {
        return nameColor;
    }

    public boolean nameBold() {
        return nameBold;
    }

    /** Applies the safe, structured style chosen in the editor; no legacy formatting codes enter the saved name. */
    public void setNameStyle(int color, boolean bold) {
        this.nameColor = color & 0xFFFFFF;
        this.nameBold = bold;
    }

    /** The styled form used above the mob and as the trading-window title. */
    public Component displayNameComponent() {
        return Component.literal(displayName()).withStyle(style -> style
                .withColor(TextColor.fromRgb(nameColor))
                .withBold(nameBold));
    }

    public ShopObjectKind objectKind() {
        return objectKind;
    }

    public void setObjectKind(ShopObjectKind kind) {
        if (kind != null) {
            this.objectKind = kind;
        }
    }

    public ResourceLocation entityType() {
        return entityType;
    }

    public void setEntityType(ResourceLocation entityType) {
        this.entityType = entityType == null ? DEFAULT_ENTITY : entityType;
    }

    /**
     * The variant NBT applied to the spawned mob.
     *
     * <p>Returned as a copy. Handing out the live tag would let a caller change a shop's appearance without the
     * registry being told to save, and an appearance that reverts on restart is a confusing kind of bug.</p>
     */
    public CompoundTag entityData() {
        return entityData.copy();
    }

    public void setEntityData(CompoundTag data) {
        this.entityData = data == null ? new CompoundTag() : data.copy();
    }

    public ResourceKey<Level> level() {
        return level;
    }

    public void setLevel(ResourceKey<Level> level) {
        if (level != null) {
            this.level = level;
        }
    }

    public BlockPos pos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        if (pos != null) {
            this.pos = pos.immutable();
        }
    }

    /** The uuid of the mob currently representing this shop, or null when it is not spawned. */
    public UUID entityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    /** The container this shop draws stock from, or null when it has none. */
    public BlockPos containerPos() {
        return containerPos;
    }

    /**
     * Whether this shop is limited by a chest.
     *
     * <p>A shop without one has unlimited stock. That is the normal case: requiring a chest to exist before a shop can be
     * created made the commonest thing anyone wants - a shop that just sells - the awkward one, and silently linking
     * whatever chest happened to be nearby produced shops backed by a container their owner had never seen.</p>
     */
    public boolean usesContainer() {
        return containerPos != null;
    }

    public void setContainerPos(BlockPos containerPos) {
        this.containerPos = containerPos == null ? null : containerPos.immutable();
    }

    /** The bank account sales are paid into, or zero to use the owner's default. */
    public int linkedAccount() {
        return linkedAccount;
    }

    public void setLinkedAccount(int linkedAccount) {
        this.linkedAccount = Math.max(0, linkedAccount);
    }

    /** Every row, including the incomplete ones the editor is still holding. */
    public List<TradeOffer> offers() {
        return offers;
    }

    public void setOffers(List<TradeOffer> offers) {
        this.offers = offers == null ? List.of() : List.copyOf(offers);
    }

    /** Only the rows a buyer can actually trade. */
    public List<TradeOffer> tradableOffers() {
        List<TradeOffer> usable = new ArrayList<>(offers.size());
        for (TradeOffer offer : offers) {
            if (offer.isComplete()) {
                usable.add(offer);
            }
        }
        return Collections.unmodifiableList(usable);
    }

    public boolean forHire() {
        return forHire;
    }

    public long hireCost() {
        return hireCost;
    }

    /** Puts the shop up for sale, or takes it off the market when {@code forHire} is false. */
    public void setForHire(boolean forHire, long hireCost) {
        this.forHire = forHire;
        this.hireCost = Math.max(0L, hireCost);
    }

    /**
     * An extra permission a buyer needs to trade here, or blank for none.
     *
     * <p>Lets staff build shops only some players may use - a members' shop, a quest reward - without a separate
     * kind of shop existing for it.</p>
     */
    public String tradePermission() {
        return tradePermission;
    }

    public void setTradePermission(String tradePermission) {
        this.tradePermission = tradePermission == null ? "" : tradePermission.trim();
    }

    /** The name to show when the shop has not been given one. */
    public String displayName() {
        if (!name.isBlank()) {
            return name;
        }
        if (type.isPlayerShop() && !ownerName.isBlank()) {
            return "Tienda de " + ownerName;
        }
        return type.title();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID, id);
        tag.putString(TYPE, type.id());
        if (owner != null) {
            tag.putUUID(OWNER, owner);
        }
        tag.putString(OWNER_NAME, ownerName);
        tag.putString(NAME, name);
        tag.putInt(NAME_COLOR, nameColor);
        if (nameBold) {
            tag.putBoolean(NAME_BOLD, true);
        }
        tag.putString(OBJECT_KIND, objectKind.id());
        tag.putString(ENTITY_TYPE, entityType.toString());
        if (!entityData.isEmpty()) {
            tag.put(ENTITY_DATA, entityData);
        }
        tag.putString(LEVEL, level.location().toString());
        tag.putLong(POS, pos.asLong());
        if (entityId != null) {
            tag.putUUID(ENTITY_ID, entityId);
        }
        if (containerPos != null) {
            tag.putLong(CONTAINER, containerPos.asLong());
        }
        if (linkedAccount > 0) {
            tag.putInt(ACCOUNT, linkedAccount);
        }
        ListTag offerList = new ListTag();
        for (TradeOffer offer : offers) {
            offerList.add(offer.save());
        }
        tag.put(OFFERS, offerList);
        if (forHire) {
            tag.putBoolean(FOR_HIRE, true);
            tag.putLong(HIRE_COST, hireCost);
        }
        if (!tradePermission.isBlank()) {
            tag.putString(TRADE_PERMISSION, tradePermission);
        }
        return tag;
    }

    public static Shopkeeper load(CompoundTag tag) {
        Shopkeeper shop = new Shopkeeper(tag.hasUUID(ID) ? tag.getUUID(ID) : UUID.randomUUID(),
                ShopType.byId(tag.getString(TYPE)));
        if (tag.hasUUID(OWNER)) {
            shop.owner = tag.getUUID(OWNER);
        }
        shop.ownerName = tag.getString(OWNER_NAME);
        shop.name = tag.getString(NAME);
        shop.nameColor = tag.contains(NAME_COLOR) ? tag.getInt(NAME_COLOR) & 0xFFFFFF : DEFAULT_NAME_COLOR;
        shop.nameBold = tag.getBoolean(NAME_BOLD);
        shop.objectKind = ShopObjectKind.byId(tag.getString(OBJECT_KIND));
        ResourceLocation entity = ResourceLocation.tryParse(tag.getString(ENTITY_TYPE));
        shop.entityType = entity == null ? DEFAULT_ENTITY : entity;
        shop.entityData = tag.getCompound(ENTITY_DATA).copy();
        ResourceLocation levelId = ResourceLocation.tryParse(tag.getString(LEVEL));
        shop.level = levelId == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, levelId);
        shop.pos = BlockPos.of(tag.getLong(POS));
        if (tag.hasUUID(ENTITY_ID)) {
            shop.entityId = tag.getUUID(ENTITY_ID);
        }
        if (tag.contains(CONTAINER)) {
            shop.containerPos = BlockPos.of(tag.getLong(CONTAINER));
        }
        shop.linkedAccount = tag.getInt(ACCOUNT);
        List<TradeOffer> offers = new ArrayList<>();
        for (Tag element : tag.getList(OFFERS, TAG_COMPOUND)) {
            if (element instanceof CompoundTag offerTag) {
                offers.add(TradeOffer.load(offerTag));
            }
        }
        shop.offers = List.copyOf(offers);
        shop.forHire = tag.getBoolean(FOR_HIRE);
        shop.hireCost = tag.getLong(HIRE_COST);
        shop.tradePermission = tag.getString(TRADE_PERMISSION);
        return shop;
    }

    /**
     * Sends the shop to a client that is about to edit it.
     *
     * <p>Only what the editor draws. The owner's uuid, the spawned entity's uuid and the container position stay
     * on the server: the client has no use for them and a client that cannot see them cannot be modified to lie
     * about them.</p>
     */
    public void writeForEditor(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeEnum(type);
        buf.writeUtf(name, 64);
        buf.writeInt(nameColor);
        buf.writeBoolean(nameBold);
        buf.writeUtf(ownerName, 64);
        buf.writeEnum(objectKind);
        buf.writeResourceLocation(entityType);
        buf.writeNbt(entityData);
        buf.writeVarInt(linkedAccount);
        buf.writeBoolean(forHire);
        buf.writeVarLong(hireCost);
        buf.writeUtf(tradePermission, 128);
        // The money's name and colour are server-wide settings, not the shop's, but the editor is where staff change
        // them so they travel with the shop being edited.
        buf.writeUtf(com.athensmc.fsshopkeepers.config.ShopConfig.get().cashLabel, 32);
        buf.writeUtf(com.athensmc.fsshopkeepers.config.ShopConfig.get().cashColor, 8);
        buf.writeVarInt(offers.size());
        for (TradeOffer offer : offers) {
            offer.write(buf);
        }
    }

    /** The editable half of a shop as the client holds it. */
    public record EditorView(UUID id, ShopType type, String name, int nameColor, boolean nameBold,
            String ownerName, ShopObjectKind objectKind, ResourceLocation entityType, CompoundTag entityData,
            int linkedAccount, boolean forHire, long hireCost, String tradePermission,
            String cashLabel, String cashColor, List<TradeOffer> offers) {

        public static EditorView read(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            ShopType type = buf.readEnum(ShopType.class);
            String name = buf.readUtf(64);
            int nameColor = buf.readInt() & 0xFFFFFF;
            boolean nameBold = buf.readBoolean();
            String ownerName = buf.readUtf(64);
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
            List<TradeOffer> offers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                offers.add(TradeOffer.read(buf));
            }
            return new EditorView(id, type, name, nameColor, nameBold, ownerName, kind, entityType,
                    entityData == null ? new CompoundTag() : entityData, account, forHire, hireCost,
                    permission, cashLabel, cashColor, offers);
        }

        /** The mob type as a readable label for the appearance tab. */
        public String entityLabel() {
            EntityType<?> resolved = EntityType.byString(entityType.toString()).orElse(null);
            return resolved == null ? entityType.getPath() : resolved.getDescription().getString();
        }
    }
}
