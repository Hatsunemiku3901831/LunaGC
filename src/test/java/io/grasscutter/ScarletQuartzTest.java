package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Actual quartz pickup, timed team buff, locked ice and the normal combat entry point. */
class ScarletQuartzTest {
    private static final int BUFF = 500101;
    private static final String HAS = "HAS_DULINS_BLOOD_BUFF";
    private static final String MARK = "HAS_BLOOD_BUFF_MARK_NORMAL";
    private static final String USED = "DULINS_BLOOD_BUFF_USED";
    private static final Map<Integer, GadgetData> GADGETS = new HashMap<>();
    private static final Map<String, ConfigEntityGadget> CONFIGS = new HashMap<>();
    private static final Map<String, AbilityData> ABILITIES = new HashMap<>();
    private static final List<BuffData> BUFFS = new ArrayList<>();
    private static ItemData quartz;
    private PhlogistonMutationTest env;
    private PlayerBuffManager manager;
    private final AtomicLong now = new AtomicLong(100000);
    private final List<Runnable> restores = new ArrayList<>();

    @BeforeAll
    static void resources() throws Exception {
        Path root = Path.of("resources");
        for (var data :
                JsonUtils.loadToList(
                        root.resolve("ExcelBinOutput/GadgetExcelConfigData.json"),
                        GadgetData.class))
            if (Set.of(70950017, 70220046, 70220047, 70290022).contains(data.getId()))
                GADGETS.put(data.getId(), data);
        for (String file :
                List.of(
                        "ConfigGadget_Scene_Gear_SnowMountain.json",
                        "ConfigGadget_Scene_Resident.json")) {
            var path = root.resolve("BinOutput/Gadget").resolve(file);
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (var row : GADGETS.values())
                if (json.has(row.getJsonName()))
                    CONFIGS.put(
                            row.getJsonName(),
                            JsonUtils.decode(
                                    json.get(row.getJsonName()), ConfigEntityGadget.class));
        }
        for (String file :
                List.of(
                        "BinOutput/Ability/Temp/ItemAbilities.json",
                        "BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_Gear_SnowMountain.json"))
            for (var row : JsonUtils.loadToList(root.resolve(file), AbilityConfigData.class))
                ABILITIES.put(row.Default.abilityName, row.Default);
        for (var row :
                JsonUtils.loadToList(
                        root.resolve("ExcelBinOutput/BuffExcelConfigData.json"), BuffData.class))
            if (Set.of(BUFF, 500102).contains(row.getId())) {
                row.onLoad();
                BUFFS.add(row);
            }
        quartz =
                JsonUtils.loadToList(
                                root.resolve("ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(i -> i.getId() == 101005)
                        .findFirst()
                        .orElseThrow();
        quartz.onLoad();
        assertEquals(4, GADGETS.size());
        assertEquals(4, CONFIGS.size(), "All ice configs must come from actual resources");
        assertEquals(2, BUFFS.size());
        assertTrue(quartz.isUseOnGain());
        assertEquals(
                25,
                BUFFS.stream().filter(b -> b.getId() == BUFF).findFirst().orElseThrow().getTime());
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        V before = map.put(key, value);
        restores.add(
                () -> {
                    if (before == null) map.remove(key);
                    else map.put(key, before);
                });
    }

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMutationTest();
        env.setup();
        env.fixture.player.setUid(10001);
        env.fixture.player.setWorld(env.fixture.scene.getWorld());
        var server = env.fixture.scene.getWorld().getServer();
        env.fixture.player.setSession(new GameSession(server));
        var inventoryField = GameServer.class.getDeclaredField("inventorySystem");
        inventoryField.setAccessible(true);
        inventoryField.set(
                server,
                new InventorySystem(
                        server,
                        now::get,
                        ignored -> fail("Quartz pickup must not write ordinary inventory")));
        GADGETS.forEach((id, data) -> put(GameData.getGadgetDataMap(), id, data));
        CONFIGS.forEach((name, data) -> put(GameData.getGadgetConfigData(), name, data));
        ABILITIES.forEach((name, data) -> put(GameData.getAbilityDataMap(), name, data));
        BUFFS.forEach(data -> put(GameData.getBuffDataMap(), data.getId(), data));
        put(GameData.getItemDataMap(), quartz.getId(), quartz);
        manager = new PlayerBuffManager(env.fixture.player, now::get);
        var field = Player.class.getDeclaredField("buffManager");
        field.setAccessible(true);
        field.set(env.fixture.player, manager);
    }

    @AfterEach
    void close() {
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
        env.close();
    }

    private EntityGadget ice(int id) {
        var target = new EntityGadget(env.fixture.scene, id, new Position());
        env.fixture.scene.getEntities().put(target.getId(), target);
        return target;
    }

    private void attack(int attacker, EntityGadget ice, float damage) {
        env.fixture.scene.handleAttack(
                AttackResult.newBuilder()
                        .setAttackerId(attacker)
                        .setDefenseId(ice.getId())
                        .setDamage(damage)
                        .build());
    }

    private float value(EntityAvatar avatar, String key) {
        return avatar.getGlobalAbilityValues().getOrDefault(key, 0f);
    }

    private EntityAvatar avatar(Player player) {
        var data =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return player;
                    }

                    @Override
                    public int getAvatarId() {
                        return 10000006;
                    }

                    @Override
                    public void save() {}
                };
        var entity =
                new EntityAvatar(env.fixture.scene, data) {
                    @Override
                    public void initAbilities() {}

                    @Override
                    public emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo
                            toProto() {
                        return emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo
                                .getDefaultInstance();
                    }
                };
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        player.getTeamManager().getActiveTeam().add(entity);
        env.fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private EntityClientGadget summon(EntityAvatar owner) {
        var gadget =
                new EntityClientGadget(
                        env.fixture.scene,
                        owner.getPlayer(),
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(999001)
                                .setConfigId(42100001)
                                .setOwnerEntityId(owner.getId())
                                .build());
        env.fixture.scene.getEntities().put(gadget.getId(), gadget);
        return gadget;
    }

    @Test
    void nativePickupArmsTeamAndDoesNotStoreConsumable() {
        var second = avatar(env.fixture.player);
        var drop =
                new EntityItem(
                        env.fixture.scene, env.fixture.player, quartz, new Position(), 1, true);
        env.fixture.scene.getEntities().put(drop.getId(), drop);
        drop.onInteract(env.fixture.player, GadgetInteractReq.getDefaultInstance());
        assertFalse(env.fixture.scene.getEntities().containsKey(drop.getId()));
        assertTrue(manager.hasBuff(51));
        assertNull(
                env.fixture
                        .player
                        .getInventory()
                        .getInventoryTab(emu.grasscutter.game.inventory.ItemType.ITEM_MATERIAL)
                        .getItemById(101005));
        for (var member : List.of(env.caster, second)) {
            assertEquals(1, value(member, HAS));
            assertEquals(1, value(member, MARK));
            assertEquals(0, value(member, USED));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {70950017, 70220046, 70220047})
    void nativeSingleSmashIceBreaksAndConsumesOnce(int id) {
        var target = ice(id);
        assertTrue(target.isLockHP());
        assertTrue(manager.addBuff(BUFF));
        attack(env.caster.getId(), target, 0);
        assertFalse(env.fixture.scene.getEntities().containsKey(target.getId()));
        assertFalse(manager.hasBuff(51));
        assertEquals(0, value(env.caster, HAS));
        assertEquals(0, value(env.caster, MARK));
        assertEquals(1, value(env.caster, USED));
        var another = ice(id);
        attack(env.caster.getId(), another, 0);
        assertSame(another, env.fixture.scene.getEntities().get(another.getId()));
    }

    @Test
    void hiddenPartyMemberAndNewActiveMemberShareTheCurrentPickup() {
        assertTrue(manager.addBuff(BUFF));
        env.fixture.scene.getEntities().remove(env.caster.getId());
        var second = avatar(env.fixture.player);
        manager.onTick();
        assertEquals(1, value(second, HAS));
        var target = ice(70950017);
        attack(second.getId(), target, 1);
        assertFalse(env.fixture.scene.getEntities().containsKey(target.getId()));
        assertEquals(0, value(env.caster, HAS));
        assertEquals(1, value(second, USED));
    }

    @Test
    void validProjectileUsesItsOwnPlayersPickup() {
        var projectile = summon(env.caster);
        var target = ice(70950017);
        assertTrue(manager.addBuff(BUFF));
        attack(projectile.getId(), target, 0);
        assertFalse(env.fixture.scene.getEntities().containsKey(target.getId()));
        assertFalse(manager.hasBuff(51));
    }

    @Test
    void expiredBuffCannotSmashBeforePeriodicCleanup() {
        assertTrue(manager.addBuff(BUFF));
        now.addAndGet(25000);
        var target = ice(70950017);
        attack(env.caster.getId(), target, 0);
        assertSame(target, env.fixture.scene.getEntities().get(target.getId()));
        manager.onTick();
        assertFalse(manager.hasBuff(51));
        assertEquals(0, value(env.caster, HAS));
        assertEquals(0, value(env.caster, MARK));
    }

    @Test
    void expiredReplacementAndClearDoNotLeaveArmedValues() {
        assertTrue(manager.addBuff(BUFF));
        assertEquals(1, value(env.caster, HAS));
        assertTrue(manager.addBuff(500102));
        assertEquals(0, value(env.caster, HAS));
        assertEquals(0, value(env.caster, MARK));
        assertTrue(manager.addBuff(BUFF));
        manager.clearBuffs();
        assertEquals(0, value(env.caster, HAS));
        assertEquals(0, value(env.caster, MARK));
    }

    @Test
    void onePickupCannotBreakTwoIceBlocksConcurrently() throws Exception {
        var first = ice(70950017);
        var second = ice(70950017);
        assertTrue(manager.addBuff(BUFF));
        var gate = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (var target : List.of(first, second))
                jobs.add(
                        executor.submit(
                                () -> {
                                    gate.await(5, TimeUnit.SECONDS);
                                    attack(env.caster.getId(), target, 0);
                                    return null;
                                }));
            for (var job : jobs) job.get(6, TimeUnit.SECONDS);
            assertEquals(
                    1,
                    List.of(first, second).stream()
                            .filter(t -> env.fixture.scene.getEntities().containsKey(t.getId()))
                            .count());
            assertFalse(manager.hasBuff(51));
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(floats = {-1f, Float.NaN, Float.POSITIVE_INFINITY})
    void invalidDamageCannotConsumePickup(float damage) {
        assertTrue(manager.addBuff(BUFF));
        var target = ice(70950017);
        attack(env.caster.getId(), target, damage);
        assertSame(target, env.fixture.scene.getEntities().get(target.getId()));
        assertTrue(manager.hasBuff(51));
    }

    @Test
    void unknownAttackerCannotBorrowHostPickup() {
        assertTrue(manager.addBuff(BUFF));
        var target = ice(70950017);
        attack(123456789, target, 0);
        assertSame(target, env.fixture.scene.getEntities().get(target.getId()));
        assertTrue(manager.hasBuff(51));
    }

    @Test
    void anotherPlayersAttackCannotBorrowHostsPickup() {
        var guest = new AbilityTestFixture.TestPlayer();
        guest.setUid(10002);
        guest.setScene(env.fixture.scene);
        guest.setWorld(env.fixture.scene.getWorld());
        var attacker = avatar(guest);
        assertTrue(manager.addBuff(BUFF));
        var target = ice(70950017);
        attack(attacker.getId(), target, 0);
        assertSame(target, env.fixture.scene.getEntities().get(target.getId()));
        assertTrue(manager.hasBuff(51));
    }

    @Test
    void questIceWithoutASingleSmashResourceActionIsNotKilled() {
        assertTrue(manager.addBuff(BUFF));
        var treeIce = ice(70290022);
        attack(env.caster.getId(), treeIce, 1);
        assertSame(treeIce, env.fixture.scene.getEntities().get(treeIce.getId()));
        assertTrue(manager.hasBuff(51));
    }

    @Test
    void staleProjectileCannotUseTheReplacementOwnersPickup() {
        var projectile = summon(env.caster);
        env.fixture.scene.removeEntity(env.caster);
        env.fixture.scene.getEntities().put(env.caster.getId(), env.caster);
        assertTrue(manager.addBuff(BUFF));
        var target = ice(70950017);
        attack(projectile.getId(), target, 0);
        assertSame(target, env.fixture.scene.getEntities().get(target.getId()));
        assertTrue(manager.hasBuff(51));
    }

    @Test
    void actualVisibilitySwitchKeepsTheOriginalProjectilesOwner() {
        var projectile = summon(env.caster);
        var replacement = avatar(env.fixture.player);
        env.fixture.scene.replaceEntity(env.caster, replacement);
        assertFalse(env.fixture.scene.getEntities().containsKey(env.caster.getId()));
        assertTrue(manager.addBuff(BUFF));
        var target = ice(70950017);
        attack(projectile.getId(), target, 0);
        assertFalse(env.fixture.scene.getEntities().containsKey(target.getId()));
        assertFalse(manager.hasBuff(51));
        assertEquals(1, value(env.caster, USED));
        assertEquals(1, value(replacement, USED));
    }

    @Test
    void aNewPickupResetsUsedStateAndCanBreakTheNextIce() {
        assertTrue(manager.addBuff(BUFF));
        attack(env.caster.getId(), ice(70950017), 0);
        assertEquals(1, value(env.caster, USED));
        assertTrue(manager.addBuff(BUFF));
        assertEquals(0, value(env.caster, USED));
        var target = ice(70950017);
        attack(env.caster.getId(), target, 0);
        assertFalse(env.fixture.scene.getEntities().containsKey(target.getId()));
        assertEquals(1, value(env.caster, USED));
    }

    @Test
    void additionalResourceConditionsAreNotBypassed() {
        var target = ice(70950017);
        var action =
                target.getInstancedAbilities()
                        .get(0)
                        .getData()
                        .modifiers
                        .get("Ice_Shield")
                        .onBeingHit[0];
        var before = action.predicates;
        try {
            action.predicates = new ArrayList<>(before);
            action.predicates.add(
                    Map.of(
                            "$type",
                            "ByTargetGlobalValue",
                            "key",
                            "ExtraQuestRequirement",
                            "value",
                            1));
            assertTrue(manager.addBuff(BUFF));
            attack(env.caster.getId(), target, 0);
            assertSame(target, env.fixture.scene.getEntities().get(target.getId()));
            assertTrue(manager.hasBuff(51));
        } finally {
            action.predicates = before;
        }
    }

    @Test
    void clearingUnrelatedBuffsDoesNotCreateQuartzState() {
        assertTrue(manager.addBuff(500102));
        manager.clearBuffs();
        assertFalse(env.caster.getGlobalAbilityValues().containsKey(HAS));
        assertFalse(env.caster.getGlobalAbilityValues().containsKey(MARK));
    }

    @Test
    void aChangedOwnerIdDoesNotRetargetAnOldProjectile() {
        var projectile = summon(env.caster);
        env.fixture.scene.getEntities().remove(env.caster.getId());
        env.caster.setId(env.caster.getId() + 1);
        env.fixture.scene.getEntities().put(env.caster.getId(), env.caster);
        assertTrue(manager.addBuff(BUFF));
        var target = ice(70950017);
        attack(projectile.getId(), target, 0);
        assertSame(target, env.fixture.scene.getEntities().get(target.getId()));
        assertTrue(manager.hasBuff(51));
    }

    @Test
    void anotherWorldCannotConsumeTheOldScenesPickup() {
        assertTrue(manager.addBuff(BUFF));
        env.fixture.player.setWorld(null);
        var target = ice(70950017);
        attack(env.caster.getId(), target, 0);
        assertSame(target, env.fixture.scene.getEntities().get(target.getId()));
        assertTrue(manager.hasBuff(51));
    }

    @Test
    void aProjectileCreatedWhileItsOwnerIsDeadCannotBorrowALaterPickup() throws Exception {
        var dead = GameEntity.class.getDeclaredMethod("setDead", boolean.class);
        dead.setAccessible(true);
        dead.invoke(env.caster, true);
        var projectile = summon(env.caster);
        dead.invoke(env.caster, false);
        assertTrue(manager.addBuff(BUFF));
        var target = ice(70950017);
        attack(projectile.getId(), target, 0);
        assertSame(target, env.fixture.scene.getEntities().get(target.getId()));
        assertTrue(manager.hasBuff(51));
    }
}
