package emu.grasscutter.game.inventory;

import dev.morphia.Datastore;
import dev.morphia.annotations.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.EquipOuterClass.Equip;
import emu.grasscutter.net.proto.FurnitureOuterClass.Furniture;
import emu.grasscutter.net.proto.ItemHintOuterClass.ItemHint;
import emu.grasscutter.net.proto.ItemOuterClass.Item;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.MaterialOuterClass.Material;
import emu.grasscutter.net.proto.ReliquaryOuterClass.Reliquary;
import emu.grasscutter.net.proto.SceneReliquaryInfoOuterClass.SceneReliquaryInfo;
import emu.grasscutter.net.proto.SceneWeaponInfoOuterClass.SceneWeaponInfo;
import emu.grasscutter.net.proto.WeaponOuterClass.Weapon;
import emu.grasscutter.utils.objects.WeightedList;

import lombok.*;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;

@Entity(value = "items", useDiscriminator = false)
@Indexes(@Index(fields = {@Field("ownerId"), @Field("count")}))
public class GameItem {
    @Id private ObjectId id;
    @Indexed private int ownerId;
    @Getter @Setter private int itemId;
    @Getter @Setter private int count;
    @Getter private ItemSaveStamp saveStamp;
    @Getter private boolean deleted;
    @Getter private String foodRefundOperationId;
    @Getter private String recipeUseOperationId;
    @Transient private transient ItemSaveGuard saveGuard;

    public synchronized ItemSaveGuard getSaveGuard() {
        if (saveGuard == null) saveGuard = new ItemSaveGuard(this, null, null);
        return saveGuard;
    }

    void setSaveStamp(ItemSaveStamp stamp) {
        saveStamp = stamp;
    }

    void markDeleted() {
        deleted = true;
        count = 0;
    }

    void acceptSaveState(
            ItemSaveStamp stamp,
            boolean deleted,
            String foodRefund,
            String recipeUse,
            boolean applyCount) {
        saveStamp = stamp;
        this.deleted = deleted;
        foodRefundOperationId = foodRefund;
        recipeUseOperationId = recipeUse;
        if (deleted && applyCount) count = 0;
    }

    /** Templates are new unowned outputs, not copies loaded from the items collection. */
    public static GameItem fromUnownedTemplate(Datastore ds, Document template) {
        if (template.getInteger("ownerId", 0) != 0
                || Boolean.TRUE.equals(template.get("deleted"))
                || template.get("saveStamp") != null
                || template.get("foodRefundOperationId") != null
                || template.get("recipeUseOperationId") != null)
            throw new IllegalStateException("Reward template already belongs to a saved item");
        var item = ds.getMapper().fromDocument(GameItem.class, template);
        if (item.getOwnerId() != 0
                || item.getGuid() != 0
                || item.isDeleted()
                || item.getSaveStamp() != null)
            throw new IllegalStateException("Invalid unowned item template");
        item.saveGuard = null;
        return item;
    }

    @Transient @Getter private long guid; // Player unique id
    @Transient @Getter @Setter private transient volatile boolean reshapePending;
    @Transient @Getter @Setter private ItemData itemData;

    // Equips
    @Getter @Setter private int level;
    @Getter @Setter private int exp;
    @Getter @Setter private int totalExp;
    @Getter @Setter private int promoteLevel;
    @Getter @Setter private boolean favourite;
    @Getter @Setter private boolean locked;

    // Weapon
    @Getter private List<Integer> affixes;
    @Getter @Setter private int refinement = 0;
    @Setter private List<Integer> tpsAccessoryIds = new ArrayList<>();
    // Only material-derived grants are revocable. Older persisted IDs remain manual grants.
    @Setter private List<Integer> tpsMaterialAccessoryIds = new ArrayList<>();

    public List<Integer> getTpsAccessoryIds() {
        if (tpsAccessoryIds == null) tpsAccessoryIds = new ArrayList<>();
        return tpsAccessoryIds;
    }

    public List<Integer> getTpsMaterialAccessoryIds() {
        if (tpsMaterialAccessoryIds == null) tpsMaterialAccessoryIds = new ArrayList<>();
        return tpsMaterialAccessoryIds;
    }

    // Relic
    @Getter @Setter private int mainPropId;
    @Getter private List<Integer> appendPropIdList;
    private List<Integer> purchasedAppendPropIdList;
    private List<Integer> definiteAppendPropIdList;
    @Getter private int definedInitialAppendCount;
    @Getter private int definedUpgradeGuarantee;

    public List<Integer> getPurchasedAppendPropIdList() {
        return purchasedAppendPropIdList == null
                ? List.of()
                : Collections.unmodifiableList(purchasedAppendPropIdList);
    }

    public List<Integer> getDefiniteAppendPropIdList() {
        return definiteAppendPropIdList == null
                ? List.of()
                : Collections.unmodifiableList(definiteAppendPropIdList);
    }

    /**
     * Preserve the actual initial rolls and the shared minimum number of selected-stat upgrades.
     */
    public void markAsDefinedReliquary(List<Integer> selected, int minimumHits) {
        if (itemData == null
                || getItemType() != ItemType.ITEM_RELIQUARY
                || level != 1
                || appendPropIdList == null
                || appendPropIdList.size() < 3
                || appendPropIdList.size() > 4
                || selected == null
                || selected.isEmpty()
                || new HashSet<>(selected).size() != selected.size()
                || !appendPropIdList.containsAll(selected)
                || minimumHits <= 0
                || minimumHits > (itemData.getMaxLevel() - 1) / 4 - (4 - appendPropIdList.size()))
            throw new IllegalArgumentException("Invalid defined artifact rolls");
        var properties = new HashSet<FightProperty>();
        for (int id : selected) {
            var row = GameData.getReliquaryAffixDataMap().get(id);
            if (row == null
                    || row.getFightProp() == null
                    || row.getFightProp() == FightProperty.FIGHT_PROP_NONE
                    || !properties.add(row.getFightProp()))
                throw new IllegalArgumentException("Invalid defined artifact selection");
        }
        purchasedAppendPropIdList = new ArrayList<>(selected);
        definiteAppendPropIdList = new ArrayList<>(selected);
        definedInitialAppendCount = appendPropIdList.size();
        definedUpgradeGuarantee = minimumHits;
    }

    @Getter @Setter private int equipCharacter;
    @Transient @Getter @Setter private EntityWeapon weaponEntity;
    @Transient @Getter private boolean newItem = false;

    public GameItem() {
        // Morphia only
    }

    public GameItem(int itemId) {
        this(GameData.getItemDataMap().get(itemId));
    }

    public GameItem(int itemId, int count) {
        this(GameData.getItemDataMap().get(itemId), count);
    }

    public GameItem(ItemParamData itemParamData) {
        this(itemParamData.getId(), itemParamData.getCount());
    }

    public GameItem(ItemData data) {
        this(data, 1);
    }

    public GameItem(ItemData data, int count) {
        this.itemId = data.getId();
        this.itemData = data;

        switch (data.getItemType()) {
            case ITEM_VIRTUAL:
                this.count = count;
                break;
            case ITEM_WEAPON:
                this.count = 1;
                this.level = Math.max(this.count, 1); // ??????????????????
                this.affixes = new ArrayList<>(2);
                if (data.getSkillAffix() != null) {
                    for (int skillAffix : data.getSkillAffix()) {
                        if (skillAffix > 0) {
                            this.affixes.add(skillAffix);
                        }
                    }
                }
                break;
            case ITEM_RELIQUARY:
                this.count = 1;
                this.level = 1;
                this.appendPropIdList = new ArrayList<>();
                // Create main property
                ReliquaryMainPropData mainPropData =
                        GameDepot.getRandomRelicMainProp(data.getMainPropDepotId());
                if (mainPropData != null) {
                    this.mainPropId = mainPropData.getId();
                }
                // Create extra stats
                this.addAppendProps(data.getAppendPropNum());
                break;
            case ITEM_TPS_WEAPON:
                this.count = 1;
                this.level = 1;
                break;
            default:
                this.count = Math.min(count, data.getStackLimit());
        }
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwner(Player player) {
        this.ownerId = player.getUid();
        this.guid = player.getNextGameGuid();
        this.reshapePending =
                this.id != null
                        && player.getArtifactTransmuterData()
                                .getReshapeCandidates()
                                .containsKey(this.id.toHexString());
    }

    public void checkIsNew(Inventory inventory) {
        // display notification when player obtain new item
        if (inventory.getItemById(this.itemId) == null) {
            this.newItem = true;
        }
    }

    public ObjectId getObjectId() {
        return id;
    }

    /** Reserve a durable identity before an economic intent references a newly created item. */
    public synchronized ObjectId ensurePersistenceId() {
        if (id == null) id = new ObjectId();
        return id;
    }

    /** Restore an unowned reserved output without assigning it a second economic identity. */
    public synchronized void restorePersistenceId(ObjectId reservedId) {
        if (reservedId == null || id != null || ownerId != 0 || guid != 0)
            throw new IllegalStateException("Only a new unowned item can restore its reserved ID");
        id = reservedId;
    }

    public ItemType getItemType() {
        return this.itemData.getItemType();
    }

    public static int getMinPromoteLevel(int level) {
        if (level > 80) {
            return 6;
        } else if (level > 70) {
            return 5;
        } else if (level > 60) {
            return 4;
        } else if (level > 50) {
            return 3;
        } else if (level > 40) {
            return 2;
        } else if (level > 20) {
            return 1;
        }
        return 0;
    }

    public int getEquipSlot() {
        return this.getItemData().getEquipType().getValue();
    }

    public boolean isEquipped() {
        return this.getEquipCharacter() > 0;
    }

    public boolean isDestroyable() {
        return !this.isLocked() && !this.isEquipped() && !this.reshapePending;
    }

    public void addAppendProp() {
        this.addAppendProp(ArtifactRollBias.NONE);
    }

    public void addAppendProp(ArtifactRollBias bias) {
        if (this.appendPropIdList == null) {
            this.appendPropIdList = new ArrayList<>();
        }

        if (this.appendPropIdList.size() < 4) {
            this.addNewAppendProp(bias);
        } else {
            this.upgradeRandomAppendProp(bias);
        }
    }

    public void addAppendProps(int quantity) {
        this.addAppendProps(quantity, ArtifactRollBias.NONE);
    }

    public void addAppendProps(int quantity, ArtifactRollBias bias) {
        int num = Math.max(quantity, 0);
        for (int i = 0; i < num; i++) {
            this.addAppendProp(bias);
        }
    }

    private Set<FightProperty> getAppendFightProperties() {
        Set<FightProperty> props = new HashSet<>();
        // Previously this would check no more than the first four affixes, however custom artifacts
        // may
        // not respect this order.
        for (int appendPropId : this.appendPropIdList) {
            ReliquaryAffixData affixData = GameData.getReliquaryAffixDataMap().get(appendPropId);
            if (affixData != null) {
                props.add(affixData.getFightProp());
            }
        }
        return props;
    }

    private void addNewAppendProp(ArtifactRollBias bias) {
        List<ReliquaryAffixData> affixList =
                GameDepot.getRelicAffixList(this.itemData.getAppendPropDepotId());

        if (affixList == null) {
            return;
        }

        // Build blacklist - Dont add same stat as main/sub stat
        Set<FightProperty> blacklist = this.getAppendFightProperties();
        ReliquaryMainPropData mainPropData =
                GameData.getReliquaryMainPropDataMap().get(this.mainPropId);
        if (mainPropData != null) {
            blacklist.add(mainPropData.getFightProp());
        }

        // Build random list
        WeightedList<ReliquaryAffixData> randomList = new WeightedList<>();
        for (ReliquaryAffixData affix : affixList) {
            if (!blacklist.contains(affix.getFightProp())) {
                randomList.add(affix.getWeight() * bias.weigh(affix), affix);
            }
        }

        if (randomList.size() == 0) {
            return;
        }

        // Add random stat
        ReliquaryAffixData affixData = randomList.next();
        this.appendPropIdList.add(affixData.getId());
    }

    private void upgradeRandomAppendProp(ArtifactRollBias bias) {
        List<ReliquaryAffixData> affixList =
                GameDepot.getRelicAffixList(this.itemData.getAppendPropDepotId());

        if (affixList == null) {
            return;
        }

        // Build whitelist
        Set<FightProperty> whitelist = this.getAppendFightProperties();
        var forced = this.requiredDefinedUpgradeProps();
        if (!forced.isEmpty()) whitelist.retainAll(forced);

        // Build random list
        WeightedList<ReliquaryAffixData> randomList = new WeightedList<>();
        for (ReliquaryAffixData affix : affixList) {
            if (whitelist.contains(affix.getFightProp())) {
                randomList.add(
                        affix.getUpgradeWeight() * (forced.isEmpty() ? bias.weigh(affix) : 1),
                        affix);
            }
        }

        if (randomList.size() == 0) {
            return;
        }

        // Add random stat
        ReliquaryAffixData affixData = randomList.next();
        this.appendPropIdList.add(affixData.getId());
    }

    /**
     * Keep ordinary rolls until the remaining upgrade opportunities are needed for the guarantee.
     */
    private Set<FightProperty> requiredDefinedUpgradeProps() {
        if (definedUpgradeGuarantee <= 0
                || definedInitialAppendCount < 3
                || definedInitialAppendCount > 4
                || getDefiniteAppendPropIdList().isEmpty()) return Set.of();
        var selected = new HashSet<FightProperty>();
        for (int id : getDefiniteAppendPropIdList()) {
            var row = GameData.getReliquaryAffixDataMap().get(id);
            if (row == null || row.getFightProp() == null) return Set.of();
            selected.add(row.getFightProp());
        }
        int hits = 0;
        for (int id : appendPropIdList) {
            var row = GameData.getReliquaryAffixDataMap().get(id);
            if (row != null && selected.contains(row.getFightProp())) hits++;
        }
        int needed = definedUpgradeGuarantee - Math.max(0, hits - selected.size());
        int maximumGrowth = (itemData.getMaxLevel() - 1) / 4 - (4 - definedInitialAppendCount);
        int remaining = maximumGrowth - (appendPropIdList.size() - 4);
        return needed > 0 && remaining > 0 && remaining <= needed ? selected : Set.of();
    }

    public void onLoad() {
        if (this.itemData == null) {
            this.itemData = GameData.getItemDataMap().get(getItemId());
        }
    }

    @PostLoad
    private void onLoadSaveState(Document row, Datastore source) {
        onLoad();
        if (row.get("foodRefundOperationId") != null) {
            Object refund = row.get("foodRefundOperationId");
            if (!(refund instanceof String op) || !UUID.fromString(op).toString().equals(op))
                throw new IllegalStateException("Invalid stored food refund identity");
        }
        if (row.get("recipeUseOperationId") != null) {
            Object value = row.get("recipeUseOperationId");
            if (!(value instanceof String op) || !UUID.fromString(op).toString().equals(op))
                throw new IllegalStateException("Invalid stored recipe use identity");
        }
        if (row.get("_id") != null) saveGuard = new ItemSaveGuard(this, row, source);
    }

    public void save() {
        if (this.count > 0 && this.ownerId > 0) {
            DatabaseHelper.saveItem(this);
        } else if (this.getObjectId() != null) {
            DatabaseHelper.deleteItem(this);
        }
    }

    public SceneWeaponInfo createSceneWeaponInfo() {
        var weaponInfo =
                SceneWeaponInfo.newBuilder()
                        .setEntityId(
                                this.getWeaponEntity() != null ? this.getWeaponEntity().getId() : 0)
                        .setItemId(this.getItemId())
                        .setGuid(this.getGuid())
                        .setLevel(this.getLevel())
                        .setGadgetId(this.getItemData().getGadgetId())
                        .setAbilityInfo(
                                getWeaponEntity() != null
                                        ? getWeaponEntity().getAbilityRoots().toSyncState()
                                        : AbilitySyncStateInfo.getDefaultInstance());

        if (this.getAffixes() != null && this.getAffixes().size() > 0) {
            for (int affix : this.getAffixes()) {
                weaponInfo.putAffixMap(affix, this.getRefinement());
            }
        }

        return weaponInfo.build();
    }

    public SceneReliquaryInfo createSceneReliquaryInfo() {
        SceneReliquaryInfo relicInfo =
                SceneReliquaryInfo.newBuilder()
                        .setItemId(this.getItemId())
                        .setGuid(this.getGuid())
                        .setLevel(this.getLevel())
                        .build();

        return relicInfo;
    }

    public Weapon toWeaponProto() {
        Weapon.Builder weapon =
                Weapon.newBuilder()
                        .setLevel(this.getLevel())
                        .setExp(this.getExp())
                        .setPromoteLevel(this.getPromoteLevel());

        if (this.getAffixes() != null && this.getAffixes().size() > 0) {
            for (int affix : this.getAffixes()) {
                weapon.putAffixMap(affix, this.getRefinement());
            }
        }

        return weapon.build();
    }

    public Reliquary toReliquaryProto() {
        Reliquary.Builder relic =
                Reliquary.newBuilder()
                        .setLevel(this.getLevel())
                        .setExp(this.getExp())
                        .setPromoteLevel(this.getPromoteLevel())
                        .setMainPropId(this.getMainPropId())
                        .addAllAppendPropIdList(this.getAppendPropIdList())
                        .addAllPurchasedAppendPropIdList(this.getPurchasedAppendPropIdList())
                        .addAllDefiniteAppendPropIdList(this.getDefiniteAppendPropIdList());

        return relic.build();
    }

    public Item toProto() {
        Item.Builder proto = Item.newBuilder().setGuid(this.getGuid()).setItemId(this.getItemId());

        switch (getItemType()) {
            case ITEM_WEAPON:
                Weapon weapon = this.toWeaponProto();
                proto.setEquip(
                        Equip.newBuilder().setWeapon(weapon).setIsLocked(this.isLocked()).build());
                break;
            case ITEM_RELIQUARY:
                Reliquary relic = this.toReliquaryProto();
                proto.setEquip(
                        Equip.newBuilder()
                                .setReliquary(relic)
                                .setIsLocked(this.isLocked())
                                .build());
                break;
            case ITEM_FURNITURE:
                Furniture furniture = Furniture.newBuilder().setCount(getCount()).build();
                proto.setFurniture(furniture);
                break;
            case ITEM_TPS_WEAPON:
                proto.setTpsWeapon(TpsWeaponSystem.toTpsWeaponProto(this));
                break;
            default:
                Material material = Material.newBuilder().setCount(getCount()).build();
                proto.setMaterial(material);
                break;
        }

        return proto.build();
    }

    public ItemHint toItemHintProto() {
        var proto =
                ItemHint.newBuilder()
                        .setItemId(getItemId())
                        .setGuid(this.getGuid())
                        .setCount(getCount())
                        .setIsNew(this.isNewItem())
                        .build();
        return proto;
    }

    public ItemParam toItemParam() {
        return ItemParam.newBuilder().setItemId(this.getItemId()).setCount(this.getCount()).build();
    }
}
