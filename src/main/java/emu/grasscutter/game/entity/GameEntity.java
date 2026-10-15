package emu.grasscutter.game.entity;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason;
import emu.grasscutter.net.proto.ChangeHpReasonOuterClass.ChangeHpReason;
import emu.grasscutter.net.proto.FightPropPairOuterClass.FightPropPair;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.MotionInfoOuterClass.MotionInfo;
import emu.grasscutter.net.proto.MotionStateOuterClass.MotionState;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.Vector3IntOuterClass.Vector3Int;
import emu.grasscutter.net.proto._EntityIntMotionInfoOuterClass._EntityIntMotionInfo;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.scripts.data.controller.EntityController;
import emu.grasscutter.net.proto.DetailAbilityInfoOuterClass.DetailAbilityInfo;
import emu.grasscutter.net.proto.PropChangeDetailInfoOuterClass.PropChangeDetailInfo;
import emu.grasscutter.server.event.entity.*;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import it.unimi.dsi.fastutil.ints.*;
import emu.grasscutter.*;
import emu.grasscutter.data.GameData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


import lombok.*;

import static emu.grasscutter.GameConstants.ENTITY_ID_BIT_SHIFT;

public abstract class GameEntity {
    @Getter private final Scene scene;
    private boolean restrictedFromHealing = false;
    private boolean convertToHpDebt = false;
    @Getter @Setter public int id;
    @Getter @Setter private SpawnDataEntry spawnEntry;
    @Setter private PropChangeDetailInfo propChangeDetailInfo;
    @Getter @Setter private DetailAbilityInfo detailAbilityInfo;

    @Getter @Setter private int campId;
    @Getter @Setter private int campType;

    @Getter @Setter private int blockId;
    @Getter @Setter private int configId;
    @Getter @Setter private int groupId;

    @Getter @Setter private MotionState motionState;
    @Getter @Setter private int lastMoveSceneTimeMs;

    @Getter @Setter private int lastMoveReliableSeq;

    @Setter private boolean lockHP;
    private boolean limbo;
    private float limboHpThreshold;

    public boolean isLockHP() {
        return lockHP || AbilityCombatState.read(this).lockHp();
    }

    @Getter
    private volatile boolean isDead = false;
    private final AtomicBoolean deathProcessed = new AtomicBoolean();
    // A summon must not survive a death followed by revival between its ticks.
    private final AtomicLong deathSequence = new AtomicLong();

    public long getDeathSequence() {
        return deathSequence.get();
    }

    @Getter @Setter private EntityController entityController;
    @Getter private ElementType lastAttackType = ElementType.None;

    @Getter private List<Ability> instancedAbilities = new EntityAbilityList(this);
    // Even versions are live; removal invalidates tasks captured before a later re-add.
    private final AtomicLong abilityLifecycleVersion = new AtomicLong();

    public long getAbilityLifecycleVersion() {
        return abilityLifecycleVersion.get();
    }

    public boolean isAbilityLifecycleCurrent(long version) {
        return (version & 1) == 0 && abilityLifecycleVersion.get() == version;
    }

    public void activateAbilityLifecycle() {
        abilityLifecycleVersion.updateAndGet(version -> (version & 1) == 0 ? version : version + 1);
        abilityGlobalValueWatchers.refresh();
    }

    public void invalidateAbilityLifecycle() {
        abilityLifecycleVersion.updateAndGet(version -> (version & 1) == 0 ? version + 1 : version);
        abilityGlobalPositions.clear();
        abilityStaminaCost.clear();
        getAbilityPeriodicHealing().clear();
        getAbilityModifierDuration().clear();
        abilityHealthEvents.clear();
        abilityGlobalValueWatchers.changed();
    }

    // Dynamic abilities have no client-list index. Cache by name, independently of active states.
    @Getter private final Map<String, Ability> dynamicAbilities = new ConcurrentHashMap<>();

    @Getter
    private Int2ObjectMap<AbilityModifierController> instancedModifiers =
            Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    // Named states are attached to an entity, even when their ability belongs to another caster.
    @Getter private final Map<Ability, Map<String, AbilityModifierController>> appliedAbilityModifiers =
            new ConcurrentHashMap<>();
    @Getter private final AbilityHpModifier abilityHpModifier = new AbilityHpModifier(this);
    @Getter private final emu.grasscutter.game.ability.AbilityPeriodicHealing abilityPeriodicHealing =
            new emu.grasscutter.game.ability.AbilityPeriodicHealing(this);
    @Getter private final emu.grasscutter.game.ability.AbilityModifierDuration abilityModifierDuration =
            new emu.grasscutter.game.ability.AbilityModifierDuration(this);
    @Getter private final AbilityGlobalValueWatchers abilityGlobalValueWatchers = new AbilityGlobalValueWatchers(this);
    @Getter private final emu.grasscutter.game.ability.AbilityHealthEvents abilityHealthEvents =
            new emu.grasscutter.game.ability.AbilityHealthEvents(this);
    @Getter private final emu.grasscutter.game.ability.AbilityStaminaCost abilityStaminaCost =
            new emu.grasscutter.game.ability.AbilityStaminaCost(this);

    public List<AbilityModifierController> getInstancedModifiersSnapshot() {
        synchronized (instancedModifiers) {
            return new ArrayList<>(instancedModifiers.values());
        }
    }

    // Abilities run on a thread pool, so a plain HashMap here threw ConcurrentModificationException
    // out of whichever action happened to be reading the values while another wrote them
    @Getter private Map<String, Float> globalAbilityValues = new ConcurrentHashMap<>();
    @Getter private final AbilityGlobalPositions abilityGlobalPositions = new AbilityGlobalPositions(this);

    public GameEntity(Scene scene) {
        this.scene = scene;
        this.motionState = MotionState.MotionState_MOTION_NONE;
    }

    public abstract void initAbilities();

    public EntityType getEntityType() {
        return EntityIdType.toEntityType(this.getId() >> ENTITY_ID_BIT_SHIFT);
    }
    public boolean isConvertToHpDebt() {
        return convertToHpDebt;
    }

    public float getNyxValue() {
        if (this.getGlobalAbilityValues().containsKey("NyxValue")) {
            return this.getGlobalAbilityValues().get("NyxValue");
        } else {
            Grasscutter.getLogger().debug("NyxValue not found");
            return 0f;
        }
    }

    public void setConvertToHpDebt(boolean convertToHpDebt) {
        this.convertToHpDebt = convertToHpDebt;
    }

    public abstract int getEntityTypeId();

    public World getWorld() {
        return this.getScene().getWorld();
    }
        public boolean isRestrictedFromHealing() {
            return restrictedFromHealing;
        }

        public void setRestrictedFromHealing(boolean restricted) {
            this.restrictedFromHealing = restricted;
        }

    public boolean isAlive() {
        return !this.isDead;
    }

    // HP reaches zero before Scene.killEntity runs, so the HP flag cannot guard settlement.
    public boolean tryBeginDeath() {
        return deathProcessed.compareAndSet(false, true);
    }

    protected void setDead(boolean dead) {
        boolean wasDead = this.isDead;
        this.isDead = dead;
        if (dead && !wasDead) deathSequence.incrementAndGet();
        if (!dead) deathProcessed.set(false);
    }
    public LifeState getLifeState() {
        return this.isAlive() ? LifeState.LIFE_ALIVE : LifeState.LIFE_DEAD;
    }

    public abstract Int2FloatMap getFightProperties();

    public abstract Position getPosition();

    public abstract Position getRotation();

    // Not every entity carries fight properties, and the ones that do can be asked for them before
    // they are built. Reading one used to throw straight out of whatever ability action asked.
    public void setFightProperty(FightProperty prop, float value) {
        this.setFightProperty(prop.getId(), value);
    }

    public void setFightProperty(int id, float value) {
        var properties = this.getFightProperties();
        if (properties == null) return;

        float previous = properties.getOrDefault(id, 0f);
        properties.put(id, value);
        if (id == FightProperty.FIGHT_PROP_CUR_HP.getId()) abilityHealthEvents.changed(previous, value);
    }

    public void addFightProperty(FightProperty prop, float value) {
        this.setFightProperty(prop.getId(), this.getFightProperty(prop) + value);
    }

    public float getFightProperty(FightProperty prop) {
        var properties = this.getFightProperties();
        return properties == null ? 0f : properties.getOrDefault(prop.getId(), 0f);
    }

    public boolean hasFightProperty(FightProperty prop) {
        var properties = this.getFightProperties();
        return properties != null && properties.containsKey(prop.getId());
    }

    public void addAllFightPropsToEntityInfo(SceneEntityInfo.Builder entityInfo) {
        var properties = this.getFightProperties();
        if (properties == null) return;

        properties.forEach(
                        (key, value) -> {
                            if (key == 0) return;
                            entityInfo.addFightPropList(
                                    FightPropPair.newBuilder().setPropType(key).setPropValue(value).build());
                        });
    }

    protected void setLimbo(float hpThreshold) {
        limbo = true;
        limboHpThreshold = hpThreshold;
    }
    public GameEntity getTrueOwner() {
    if (this instanceof EntityClientGadget gadget) {
        GameEntity owner = gadget.getScene().getEntityById(gadget.getOwnerEntityId());

        return (owner instanceof EntityClientGadget) ? owner.getTrueOwner() : owner;
    }
    return this;
}

    public void onAddAbilityModifier(AbilityModifier data) {
        if (data.properties == null) {
            return;
        }
        // No ability instance here to resolve a named special against, so an unresolvable
        // one reads as zero and simply leaves the Limbo threshold unset.
        float hpThresholdRatio = data.properties.Actor_HpThresholdRatio.get(0f);

        if (data.state == AbilityModifier.State.Limbo && hpThresholdRatio > 0.0f) {
            Grasscutter.getLogger().debug("Limbo set to {}", hpThresholdRatio);
            this.setLimbo(hpThresholdRatio);
        }
    }

    protected MotionInfo getMotionInfo() {
        return MotionInfo.newBuilder()
                .setPos(this.getPosition().toProto())
                .setRot(this.getRotation().toProto())
                .setSpeed(Vector.newBuilder())
                .setState(this.getMotionState())
                .build();
    }

    protected void injectIntMotionInfo(SceneEntityInfo.Builder entityInfo) {
        Position pos = this.getPosition();
        Position rot = this.getRotation();
        if (pos == null || rot == null) return;

        entityInfo.setIntMotionInfo(
                _EntityIntMotionInfo.newBuilder()
                        .setEntityId(this.getId())
                        .setPos(toMilli(pos))
                        .setRot(toMilli(rot))
                        .setState(this.getMotionState()));
    }

    private static Vector3Int toMilli(Position p) {
        return Vector3Int.newBuilder()
                .setX(Math.round(p.getX() * 1000f))
                .setY(Math.round(p.getY() * 1000f))
                .setZ(Math.round(p.getZ() * 1000f))
                .build();
    }

    public float heal(float amount) {
        return heal(amount, false);
    }

    public synchronized float heal(float amount, boolean mute) {
        if (this.getFightProperties() == null || !Float.isFinite(amount) || amount <= 0f) {
            return 0f;
        }

        float curHp = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        float maxHp = this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float curHpDebt = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (!Float.isFinite(curHp) || !Float.isFinite(maxHp) || maxHp <= 0f
                || !Float.isFinite(curHpDebt)) {
            return 0f;
        }

        // Healing pays the outstanding debt first; only the remainder can restore health.
        float toRepay = Math.min(amount, Math.max(0f, curHpDebt));
        float toHeal = Math.min(Math.max(0f, maxHp - curHp), amount - toRepay);
        float remainingDebt = curHpDebt - toRepay;
        if (toHeal > 0f) {
            this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, curHp + toHeal);
        }
        if (toRepay > 0f) {
            this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, remainingDebt);
        }

        // Publish only after both values have been settled.
        if (getScene() != null) {
            if (toHeal > 0f) {
                getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_HP));
            }
            if (toRepay > 0f) {
                getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
                getScene().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(
                        this, FightProperty.FIGHT_PROP_CUR_HP_DEBTS, -toRepay,
                        mute ? PropChangeReason.PropChangeReason_PROP_CHANGE_NONE
                                : PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                        remainingDebt > 0f
                                ? ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY
                                : ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY_FINISH));
            }
        }

        return toHeal;
    }

    public void damage(float amount) {
        GameEntity ownerEntity = resolveOwnerEntity(this);
        this.damage(amount, 0, ElementType.None);
    }
    private GameEntity resolveOwnerEntity(GameEntity owner) {
        if (owner instanceof EntityClientGadget ownerGadget) {

            GameEntity nextOwner = ownerGadget.getScene().getEntityById(ownerGadget.getOwnerEntityId());
            return resolveOwnerEntity(nextOwner);
        }
        return owner;
    }
    public void addSpecialEnergy(float energy) {
        var properties = getFightProperties();
        if (properties == null || !Float.isFinite(energy)) return;
        synchronized (properties) {
            float current = getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
            float max = Math.max(0f, getFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY));
            setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY,
                    Math.max(0f, Math.min(max, current + energy)));
            if (getScene() != null) getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(
                    this, FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
        }
    }

    public void clearSpecialEnergy(){
        var properties = getFightProperties();
        if (properties == null) return;
        synchronized (properties) {
            setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, 0f);
            if (getScene() != null) getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(
                    this, FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
        }
    }

    public void damage(float amount, ElementType attackType) {
        this.damage(amount, 0, attackType);
    }

    public void damage(float amount, int killerId, ElementType attackType) {
        this.damage(amount, killerId, attackType, PropChangeReason.PropChangeReason_PROP_CHANGE_NONE, ChangeHpReason.ChangeHpReason_CHANGE_HP_NONE);
    }

    public void damage(float amount, PropChangeReason propChangeReason, ChangeHpReason changeHpReason) {
        this.damage(amount, 0, ElementType.None, propChangeReason, changeHpReason);
    }

    public void damage(float amount, int killerId, ElementType attackType, PropChangeReason propChangeReason, ChangeHpReason changeHpReason) {
        damage(amount, killerId, attackType, propChangeReason, changeHpReason, true);
    }

    public void loseHpByAbility(float amount, boolean respectLockHp) {
        damage(amount, 0, ElementType.None, PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                ChangeHpReason.ChangeHpReason_CHANGE_HP_SUB_ABILITY, respectLockHp);
    }

    private void damage(float amount, int killerId, ElementType attackType,
            PropChangeReason propChangeReason, ChangeHpReason changeHpReason, boolean respectLockHp) {

        if (!Float.isFinite(amount) || amount < 0f) return;
        if (this.isDead || deathProcessed.get()) return;
        if (this.getFightProperties() == null || !hasFightProperty(FightProperty.FIGHT_PROP_CUR_HP)) {
            return;
        }

        var combatState = AbilityCombatState.read(this);
        if (combatState.invincible()) {
            if (amount > 0f) syncInvincibleHp();
            return;
        }

        EntityDamageEvent event =
                new EntityDamageEvent(this, amount, attackType, this.getScene().getEntityById(killerId));
        event.call();
        float requestedDamage = event.getDamage();
        if (event.isCanceled() || !Float.isFinite(requestedDamage) || requestedDamage < 0f) {
            return;
        }

        if (requestedDamage > 0) {
            abilityHealthEvents.prepareDamage();
            combatState = AbilityCombatState.read(this);
        }

        float effectiveDamage = 0;
        float curHp = getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        if (combatState.lockHp() && respectLockHp) {
            // Modifier shields also block lethal damage, independently of a gadget's base HP lock.
        } else if (limbo || combatState.limbo()) {
            float maxHp = getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            float curRatio = curHp / maxHp;
            float threshold = Math.max(limbo ? limboHpThreshold : 0f, combatState.hpThreshold());
            if (curRatio > threshold) {

                effectiveDamage = requestedDamage;
            }
            if (effectiveDamage >= curHp) {

                effectiveDamage = Math.max(0f, curHp - 1f);
            }
        } else if (curHp != Float.POSITIVE_INFINITY && (!lockHP || !respectLockHp)
                || lockHP && curHp <= requestedDamage) {
            effectiveDamage = requestedDamage;
        }

        this.addFightProperty(FightProperty.FIGHT_PROP_CUR_HP, -effectiveDamage);

        if (Float.isFinite(curHp) && curHp > 0f && effectiveDamage > 0f)
            this.getWorld().getDamageLog().record(
                    this, killerId, Math.min(curHp, effectiveDamage), attackType);

        this.lastAttackType = attackType;
        this.checkIfDead();
        this.runLuaCallbacks(event);

        this.getScene()
                .broadcastPacket(
                        new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_HP));

        ChangeHpReason dmgHpReason = changeHpReason;
        if (effectiveDamage > 0) {
            GameEntity attacker = this.getScene().getEntityById(killerId);
            if (changeHpReason != ChangeHpReason.ChangeHpReason_CHANGE_HP_NONE) {
                dmgHpReason = changeHpReason;
            } else if (attacker instanceof EntityAvatar) {
                dmgHpReason = ChangeHpReason.ChangeHpReason_CHANGE_HP_SUB_AVATAR;
            } else if (attacker instanceof EntityMonster) {
                dmgHpReason = ChangeHpReason.ChangeHpReason_CHANGE_HP_SUB_MONSTER;
            } else {
                dmgHpReason = ChangeHpReason.ChangeHpReason_CHANGE_HP_SUB_ABILITY;
            }
            this.getScene().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(
                this, FightProperty.FIGHT_PROP_CUR_HP, -effectiveDamage,
                propChangeReason, dmgHpReason));
        }

        if (this instanceof EntityAvatar entityAvatar) {
            entityAvatar.getPlayer().sendPacket(
                new PacketAvatarFightPropNotify(entityAvatar.getAvatar()));
        }

        if (this.isDead) {
            this.getScene().killEntity(this, killerId, dmgHpReason);
        }
    }

    /** Echo authoritative HP for a blocked hit without running damage settlement or callbacks. */
    private void syncInvincibleHp() {
        var currentScene = getScene();
        if (currentScene == null) return;
        synchronized (currentScene) {
            float hp = getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            if (!Float.isFinite(hp) || hp < 0f || !isAlive()
                    || !isAbilityLifecycleCurrent(getAbilityLifecycleVersion())) return;
            var avatar = this instanceof EntityAvatar value ? value : null;
            if (avatar != null && (avatar.getPlayer() == null
                    || avatar.getPlayer().getScene() != currentScene
                    || avatar.getPlayer().getWorld() != currentScene.getWorld())) return;
            var registered = currentScene.getEntities().get(getId());
            if (registered != this && !(registered == null && avatar != null
                    && avatar.isInSceneParty())) return;
            currentScene.broadcastPacket(new PacketEntityFightPropUpdateNotify(
                    this, FightProperty.FIGHT_PROP_CUR_HP));
            if (avatar != null)
                avatar.getPlayer().sendPacket(new PacketAvatarFightPropNotify(avatar.getAvatar()));
        }
    }

    public void checkIfDead() {
        if (this.getFightProperties() == null || !hasFightProperty(FightProperty.FIGHT_PROP_CUR_HP)) {
            return;
        }

        if (this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) <= 0f) {
            this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0f);
            float debt = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            if (debt >= 0) {
                this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 0f);
                this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
                this.getScene().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(this, FightProperty.FIGHT_PROP_CUR_HP_DEBTS, -debt, PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY, ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_CLEAR));
            }
            this.setDead(true);
        }
    }

    public void runLuaCallbacks(EntityDamageEvent event) {
        if (entityController != null) {
            entityController.onBeHurt(this, event.getAttackElementType(), true);
        }
    }

    public void move(Position position, Position rotation) {

        this.getPosition().set(position);
        this.getRotation().set(rotation);
    }

    public void onInteract(Player player, GadgetInteractReq interactReq) {}

    public void onCreate() {}

    public void onRemoved() {}

    private int[] parseCountRange(String range) {
        var split = range.split(";");
        if (split.length == 1)
            return new int[] {Integer.parseInt(split[0]), Integer.parseInt(split[0])};
        return new int[] {Integer.parseInt(split[0]), Integer.parseInt(split[1])};
    }

    public boolean dropSubfieldItem(int dropId) {
        return dropSubfieldItem(dropId, new Random());
    }

    boolean dropSubfieldItem(int dropId, Random random) {
        var drop = GameData.getDropSubfieldMappingMap().get(dropId);
        if (drop == null) return false;
        var dropTableEntry = GameData.getDropTableExcelConfigDataMap().get(drop.getItemId());
        if (dropTableEntry == null) return false;

        Int2ObjectMap<Integer> itemsToDrop = new Int2ObjectOpenHashMap<>();
        switch (dropTableEntry.getRandomType()) {
            case 0:
                {
                    int weightCount = 0;
                    for (var entry : dropTableEntry.getDropVec()) {
                        weightCount += Math.max(0, entry.getWeight());
                    }

                    if (weightCount == 0) return true;

                    int randomValue = random.nextInt(weightCount);

                    weightCount = 0;
                    for (var entry : dropTableEntry.getDropVec()) {
                        weightCount += Math.max(0, entry.getWeight());
                        if (randomValue < weightCount) {
                            if (entry.getItemId() == 0) break;
                            var countRange = parseCountRange(entry.getCountRange());
                            itemsToDrop.put(
                                    entry.getItemId(),
                                    Integer.valueOf((random.nextBoolean() ? countRange[0] : countRange[1])));
                            break;
                        }
                    }
                }
                break;
            case 1:
                {
                    for (var entry : dropTableEntry.getDropVec()) {
                        if (entry.getItemId() != 0 && random.nextInt(10000) < entry.getWeight()) {
                            var countRange = parseCountRange(entry.getCountRange());
                            itemsToDrop.put(
                                    entry.getItemId(),
                                    Integer.valueOf((random.nextBoolean() ? countRange[0] : countRange[1])));
                        }
                    }
                }
                break;
        }

        for (var entry : itemsToDrop.int2ObjectEntrySet()) {
            if (entry.getValue() > 0) spawnSubfieldItem(entry.getIntKey(), entry.getValue());
        }

        return true;
    }

    protected void spawnSubfieldItem(int itemId, int count) {
        var item =
                new EntityItem(
                        scene,
                        null,
                        GameData.getItemDataMap().get(itemId),
                        getPosition().nearby2d(1f).addY(0.5f),
                        count,
                        true);

        scene.addEntity(item);
    }

    public boolean dropSubfield(String subfieldName) {
        var subfieldMapping = GameData.getSubfieldMappingMap().get(getEntityTypeId());
        if (subfieldMapping == null || subfieldMapping.getSubfields() == null) return false;

        for (var entry : subfieldMapping.getSubfields()) {
            if (entry.getSubfieldName().compareTo(subfieldName) == 0) {
                return dropSubfieldItem(entry.getDrop_id());
            }
        }

        return false;
    }

    public void onTick(int sceneTime) {
        onAbilityTick();
        if (entityController != null) {
            entityController.onTimer(this, sceneTime);
        }
    }

    /** Party members offstage continue ability clocks, without running scene entity controllers. */
    public void onAbilityTick() {
        abilityGlobalValueWatchers.flushPending();
        getAbilityPeriodicHealing().tick();
        getAbilityModifierDuration().tick();
    }

    public int onClientExecuteRequest(int param1, int param2, int param3) {
        if (entityController != null) {
            return entityController.onClientExecuteRequest(this, param1, param2, param3);
        }
        return 0;
    }

    public void onDeath(int killerId) {

        this.setDead(true);
        EntityDeathEvent event = new EntityDeathEvent(this, killerId);
        event.call();

        if (entityController != null) {
            entityController.onDie(this, getLastAttackType());
        }
    }

    public void onAbilityValueUpdate() {
        abilityGlobalValueWatchers.changed();
        abilityHpModifier.refresh(AbilityGlobalValueWatchers.shouldNotify());
        getAbilityPeriodicHealing().refresh();
        getAbilityModifierDuration().refresh();
    }

    public abstract SceneEntityInfo toProto();

    @Override
    public String toString() {
        return "Entity ID: %s; Group ID: %s; Config ID: %s"
                .formatted(this.getId(), this.getGroupId(), this.getConfigId());
    }
}
