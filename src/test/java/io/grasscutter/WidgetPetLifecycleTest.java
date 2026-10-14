package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.player.WidgetCatalog;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

class WidgetPetLifecycleTest {
    WidgetEquipmentRequestTest requests;
    static List<ResourceLoader.AbilityConfigData> resources;
    static List<ItemData> items;

    @BeforeAll
    static void readResources() throws Exception {
        resources =
                JsonUtils.loadToList(
                        Path.of(
                                "resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_Toys.json"),
                        ResourceLoader.AbilityConfigData.class);
        items =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                        ItemData.class);
    }

    @BeforeEach
    void setup() throws Exception {
        requests = new WidgetEquipmentRequestTest();
        requests.setup();
        for (var item : items) {
            var config = WidgetCatalog.current().get(item.getId());
            if (config != null && config.hasGroup()) {
                item.onLoad();
                requests.env.put(GameData.getItemDataMap(), item.getId(), item);
            }
        }
        for (var row : resources)
            if (row.Default != null
                    && row.Default.abilityName != null
                    && row.Default.abilityName.startsWith("SceneObj_Toys_"))
                requests.env.put(
                        GameData.getAbilityDataMap(), row.Default.abilityName, row.Default);
    }

    @AfterEach
    void close() throws Exception {
        drain();
        requests.close();
    }

    EntityTeam team() {
        return requests.env.fixture.player.getTeamManager().getEntity();
    }

    Ability ability(String name) {
        return team().getInstancedAbilities().stream()
                .filter(Objects::nonNull)
                .filter(a -> name.equals(a.getData().abilityName))
                .findFirst()
                .orElse(null);
    }

    int id(String name) {
        return requests
                .env
                .fixture
                .player
                .getTeamManager()
                .getAbilityControlBlock()
                .getAbilityEmbryoListList()
                .stream()
                .filter(a -> a.getAbilityNameHash() == Utils.abilityHash(name))
                .findFirst()
                .orElseThrow()
                .getAbilityId();
    }

    void drain() throws Exception {
        var gate = new CyclicBarrier(4);
        var jobs = new ArrayList<Future<?>>();
        for (int i = 0; i < 4; i++)
            jobs.add(
                    AbilityManager.eventExecutor.submit(
                            () -> {
                                gate.await(5, TimeUnit.SECONDS);
                                return null;
                            }));
        for (var job : jobs) job.get(6, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                220014, 220015, 220016, 220023, 220038, 220041, 220045, 220061, 220062, 220072,
                220074, 220076, 220079, 220084, 220095, 220096, 220105, 220115, 220120, 220137
            })
    void allTwentyReferenceFollowersInstallRealResourceAbilities(int material) throws Exception {
        requests.own(material, 1);
        assertEquals(0, requests.equip(material, 0, 1).getRetcode());
        var config = WidgetCatalog.current().get(material);
        assertTrue(config.hasGroup());
        for (var named : config.abilities()) {
            var root = ability(named.name());
            assertNotNull(root, named.name());
            assertSame(requests.env.fixture.player, root.getPlayerOwner());
            assertSame(root, team().getInstancedAbilities().get(id(named.name()) - 1));
        }
        drain();
        assertEquals(1, requests.env.fixture.player.getInventory().getItemCountById(material));
        assertTrue(
                requests.env.fixture.player.packets.stream().anyMatch(p -> p.getOpcode() == 5968));
    }

    @Test
    void notificationUsesNativeBooleanTwoAndMaterialThree() {
        requests.own(220023, 1);
        requests.equip(220023, 0, 1);
        var packet =
                requests.env.fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == 5968)
                        .findFirst()
                        .orElseThrow();
        var values = new HashMap<Integer, Integer>();
        assertDoesNotThrow(
                () -> {
                    var input = CodedInputStream.newInstance(packet.getData());
                    int tag;
                    while ((tag = input.readTag()) != 0) {
                        assertEquals(0, tag & 7);
                        values.put(tag >> 3, input.readUInt32());
                    }
                });
        assertEquals(Map.of(2, 1, 3, 220023), values);
    }

    @Test
    void duplicateEquipKeepsSameInstanceAndDoesNotRepeatAttach() {
        requests.own(220023, 1);
        requests.equip(220023, 0, 1);
        var root = ability("SceneObj_Toys_Endora");
        int size = team().getInstancedAbilities().size();
        requests.env.fixture.player.packets.clear();
        requests.equip(220023, 0, 1);
        assertSame(root, ability("SceneObj_Toys_Endora"));
        assertEquals(size, team().getInstancedAbilities().size());
        assertTrue(
                requests.env.fixture.player.packets.stream().noneMatch(p -> p.getOpcode() == 5968));
    }

    @Test
    void switchingAndReequippingKeepsOtherAbilityIdsStable() {
        requests.own(220023, 1);
        requests.own(220015, 1);
        requests.equip(220023, 0, 1);
        int oldId = id("SceneObj_Toys_Endora");
        var unrelated =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'Widget_Unrelated',modifiers:{}}", AbilityData.class),
                        team(),
                        requests.env.fixture.player);
        team().getInstancedAbilities().add(unrelated);
        int otherId = team().getInstancedAbilities().size();
        requests.equip(220015, 0, 1);
        assertNull(team().getInstancedAbilities().get(oldId - 1));
        assertSame(unrelated, team().getInstancedAbilities().get(otherId - 1));
        requests.equip(220023, 0, 1);
        assertEquals(oldId, id("SceneObj_Toys_Endora"));
        assertSame(unrelated, team().getInstancedAbilities().get(otherId - 1));
        var ids =
                requests
                        .env
                        .fixture
                        .player
                        .getTeamManager()
                        .getAbilityControlBlock()
                        .getAbilityEmbryoListList()
                        .stream()
                        .map(a -> a.getAbilityId())
                        .toList();
        assertEquals(ids.size(), new HashSet<>(ids).size());
    }

    @Test
    void delayedClientAddCannotResurrectAnInactivePetSlot() throws Exception {
        requests.own(220023, 1);
        requests.own(220015, 1);
        requests.equip(220023, 0, 1);
        int oldId = id("SceneObj_Toys_Endora");
        requests.equip(220015, 0, 1);
        var message =
                AbilityMetaAddAbilityOuterClass.AbilityMetaAddAbility.newBuilder()
                        .setAbility(
                                AbilityAppliedAbilityOuterClass.AbilityAppliedAbility.newBuilder()
                                        .setAbilityName(
                                                AbilityStringOuterClass.AbilityString.newBuilder()
                                                        .setStr("SceneObj_Toys_Endora")))
                        .build();
        requests.env
                .fixture
                .player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                                .setEntityId(team().getId())
                                .setArgumentTypeValue(13)
                                .setHead(
                                        AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead
                                                .newBuilder()
                                                .setInstancedAbilityId(oldId))
                                .setAbilityData(message.toByteString())
                                .build());
        assertNull(team().getInstancedAbilities().get(oldId - 1));
        assertNotNull(ability("SceneObj_Toys_SeelieCreater"));
    }

    @Test
    void anExistingAbilityInABaseSlotCannotCreateDuplicateEmbryoIds() {
        var foreign =
                new Ability(
                        GameData.getAbilityData("SceneObj_Toys_Endora"),
                        team(),
                        requests.env.fixture.player);
        team().getInstancedAbilities().add(foreign);
        requests.own(220023, 1);
        requests.equip(220023, 0, 1);
        var ids =
                requests
                        .env
                        .fixture
                        .player
                        .getTeamManager()
                        .getAbilityControlBlock()
                        .getAbilityEmbryoListList()
                        .stream()
                        .map(a -> a.getAbilityId())
                        .toList();
        assertEquals(ids.size(), new HashSet<>(ids).size());
        requests.equip(220023, 1, 1);
        assertSame(foreign, team().getInstancedAbilities().get(0));
    }

    @Test
    void detachPreservesAnExistingAbilityOwnedByAnotherFeature() {
        int base =
                requests.env
                        .fixture
                        .player
                        .getTeamManager()
                        .getBaseAbilityControlBlock()
                        .getAbilityEmbryoListCount();
        while (team().getInstancedAbilities().size() < base)
            team().getInstancedAbilities().add(null);
        var foreign =
                new Ability(
                        GameData.getAbilityData("SceneObj_Toys_Endora"),
                        team(),
                        requests.env.fixture.player);
        team().getInstancedAbilities().add(foreign);
        requests.own(220023, 1);
        requests.equip(220023, 0, 1);
        requests.equip(220023, 1, 1);
        assertSame(foreign, team().getInstancedAbilities().get(base));
    }

    @Test
    void aReplacedOwnedInstanceStillReleasesItsOwnModifiers() {
        requests.own(220023, 1);
        requests.equip(220023, 0, 1);
        var root = ability("SceneObj_Toys_Endora");
        int index = id("SceneObj_Toys_Endora") - 1;
        var data = JsonUtils.decode("{modifierName:'Widget_Test_Owned'}", AbilityModifier.class);
        var modifier = new AbilityModifierController(root, root.getData(), data);
        team().getInstancedModifiers().put(501, modifier);
        root.registerModifier(team(), modifier);
        var foreign =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'Widget_Foreign_Replacement',modifiers:{}}",
                                AbilityData.class),
                        team(),
                        requests.env.fixture.player);
        team().getInstancedAbilities().set(index, foreign);
        requests.equip(220023, 1, 1);
        assertSame(foreign, team().getInstancedAbilities().get(index));
        assertFalse(team().getInstancedModifiers().containsKey(501));
    }

    @Test
    void removingLastOwnedItemDetachesItsSlotAndAbility() {
        var item = requests.own(220023, 1);
        requests.equip(220023, 0, 1);
        assertTrue(requests.env.fixture.player.getInventory().removeItem(item, 1));
        assertEquals(0, requests.env.fixture.player.getWidgetPetId());
        assertNull(ability("SceneObj_Toys_Endora"));
    }

    @Test
    void reconnectPreloadAppearsInActualEnterScenePacket() throws Exception {
        requests.own(220003, 1);
        requests.own(220023, 1);
        requests.env.fixture.player.setWidgetId(220003);
        requests.env.fixture.player.setWidgetPetId(220023);
        for (var name : List.of("proudSkillList", "talentIdList")) {
            var f = emu.grasscutter.game.avatar.Avatar.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(requests.env.avatar.getAvatar(), new HashSet<Integer>());
        }
        requests.env.fixture.player.getWidgets().prepareForScene();
        var wire =
                PlayerEnterSceneInfoNotifyOuterClass.PlayerEnterSceneInfoNotify.parseFrom(
                        new PacketPlayerEnterSceneInfoNotify(requests.env.fixture.player)
                                .getData());
        assertTrue(
                wire.getTeamEnterInfo().getAbilityControlBlock().getAbilityEmbryoListList().stream()
                        .anyMatch(
                                a ->
                                        a.getAbilityNameHash()
                                                == Utils.abilityHash("SceneObj_Toys_Endora")));
        requests.env.fixture.player.getWidgets().syncEquipped();
        assertTrue(
                requests.env.fixture.player.packets.stream().anyMatch(p -> p.getOpcode() == 5968));
    }

    @Test
    void legacySinglePetSlotMigratesAndIsReportedAsFollower() {
        requests.own(220023, 1);
        requests.env.fixture.player.setWidgetId(220023);
        var slots = requests.slots();
        assertEquals(0, requests.env.fixture.player.getWidgetId());
        assertEquals(220023, requests.env.fixture.player.getWidgetPetId());
        assertEquals(1, slots.size());
        assertEquals(1, slots.get(0).getTagValue());
        assertNotNull(ability("SceneObj_Toys_Endora"));
    }

    @Test
    void recreatedTeamRestoresPetWithoutKeepingOldInstance() {
        requests.own(220023, 1);
        requests.equip(220023, 0, 1);
        var old = team();
        var root = ability("SceneObj_Toys_Endora");
        requests.env
                .fixture
                .player
                .getTeamManager()
                .setEntity(new EntityTeam(requests.env.fixture.player));
        requests.env.fixture.player.getWidgets().prepareForScene();
        assertNotSame(root, ability("SceneObj_Toys_Endora"));
        assertTrue(old.getInstancedAbilities().stream().noneMatch(a -> a == root));
    }

    @Test
    void widgetSaveRunsAfterSceneAndManagerLocksAreReleased() {
        var scene = requests.env.fixture.scene;
        var actual =
                new AbilityTestFixture.TestPlayer() {
                    @Override
                    public void save() {
                        assertFalse(Thread.holdsLock(scene));
                        assertFalse(Thread.holdsLock(getWidgets()));
                    }
                };
        actual.setUid(10002);
        actual.setWorld(scene.getWorld());
        actual.setScene(scene);
        actual.getTeamManager().setEntity(new EntityTeam(actual));
        var item = new emu.grasscutter.game.inventory.GameItem(220003, 1);
        item.setOwner(actual);
        actual.getInventory().getItems().put(item.getGuid(), item);
        actual.getInventory()
                .getInventoryTab(emu.grasscutter.game.inventory.ItemType.ITEM_MATERIAL)
                .onAddItem(item);
        assertEquals(0, actual.getWidgets().setSlot(220003, 0, List.of(0)).retcode());
    }
}
