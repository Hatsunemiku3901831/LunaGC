package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.WidgetPlacementManager;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.WidgetCreateLocationInfoOuterClass.WidgetCreateLocationInfo;
import emu.grasscutter.net.proto.WidgetCreatorInfoOuterClass.WidgetCreatorInfo;
import emu.grasscutter.net.proto.WidgetDoBagReqOuterClass.WidgetDoBagReq;
import emu.grasscutter.net.proto.WidgetDoBagRspOuterClass.WidgetDoBagRsp;
import emu.grasscutter.server.packet.recv.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Actual bag and create handlers, real current material/gadget rows, and real scene removal. */
class WidgetPlacementTest {
    static final int POT = 220026, FIREWORKS = 220047;
    static List<ItemData> materials;
    static List<GadgetData> gadgets;
    static Map<String, emu.grasscutter.data.binout.config.ConfigEntityGadget> configurations;
    NatsaurusPossessionTest env;

    @BeforeAll
    static void resources() throws Exception {
        if (emu.grasscutter.scripts.ScriptLoader.getEngine() == null)
            emu.grasscutter.scripts.ScriptLoader.init();
        materials =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(d -> Set.of(POT, FIREWORKS).contains(d.getId()))
                        .toList();
        materials.forEach(ItemData::onLoad);
        gadgets =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/GadgetExcelConfigData.json"),
                                GadgetData.class)
                        .stream()
                        .filter(d -> Set.of(70500025, 70800058).contains(d.getId()))
                        .toList();
        assertEquals(2, materials.size());
        assertEquals(2, gadgets.size());
        configurations = new HashMap<>();
        for (var file :
                List.of(
                        "ConfigGadget_Scene_Toys.json",
                        "ConfigGadget_Scene_Gear_Activity_V2_4.json")) {
            var json =
                    com.google.gson.JsonParser.parseString(
                                    java.nio.file.Files.readString(
                                            Path.of("resources/BinOutput/Gadget", file)))
                            .getAsJsonObject();
            for (var gadget : gadgets) {
                if (json.has(gadget.getJsonName()))
                    configurations.put(
                            gadget.getJsonName(),
                            JsonUtils.decode(
                                    json.get(gadget.getJsonName()).toString(),
                                    emu.grasscutter.data.binout.config.ConfigEntityGadget.class));
            }
        }
        assertEquals(2, configurations.size());
    }

    @BeforeEach
    void setup() throws Exception {
        env = new NatsaurusPossessionTest();
        env.setup();
        env.fixture.player.getPosition().set(0, 0, 0);
        materials.forEach(d -> env.put(GameData.getItemDataMap(), d.getId(), d));
        gadgets.forEach(d -> env.put(GameData.getGadgetDataMap(), d.getId(), d));
        configurations.forEach((key, value) -> env.put(GameData.getGadgetConfigData(), key, value));
    }

    @AfterEach
    void close() {
        env.close();
    }

    GameItem own(int id) {
        var item =
                new GameItem(GameData.getItemDataMap().get(id), 1) {
                    @Override
                    public void save() {}
                };
        item.setOwner(env.fixture.player);
        env.fixture.player.getInventory().getItems().put(item.getGuid(), item);
        env.fixture.player.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(item);
        return item;
    }

    WidgetCreateLocationInfo location(float x) {
        return WidgetCreateLocationInfo.newBuilder()
                .setPos(new Position(x, 3, 4).toProto())
                .setRot(new Position(0, 90, 0).toProto())
                .build();
    }

    WidgetDoBagReq request(int id, int op, int entityId, WidgetCreateLocationInfo location) {
        var creator = WidgetCreatorInfo.newBuilder().setOpTypeValue(op).setEntityId(entityId);
        if (location != null) creator.setLocationInfo(location);
        return WidgetDoBagReq.newBuilder().setMaterialId(id).setWidgetCreatorInfo(creator).build();
    }

    WidgetDoBagRsp bag(WidgetDoBagReq request) throws Exception {
        new HandlerWidgetDoBagReq()
                .handle(env.fixture.player.getSession(), new byte[0], request.toByteArray());
        return WidgetDoBagRsp.parseFrom(
                env.fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.WidgetDoBagRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow()
                        .getData());
    }

    List<EntityBaseGadget> placed(int gadgetId) {
        return env.fixture.scene.getEntities().values().stream()
                .filter(e -> e instanceof EntityBaseGadget)
                .map(e -> (EntityBaseGadget) e)
                .filter(g -> g.getGadgetId() == gadgetId)
                .toList();
    }

    void rejected(WidgetDoBagReq request) throws Exception {
        assertNotEquals(0, bag(request).getRetcode());
        assertTrue(placed(70500025).isEmpty());
        assertTrue(placed(70800058).isEmpty());
    }

    @Test
    void unownedPotCannotBeCreated() throws Exception {
        rejected(request(POT, 2, 0, location(2)));
    }

    @Test
    void zeroCountCannotBeUsed() throws Exception {
        own(POT).setCount(0);
        rejected(request(POT, 2, 0, location(2)));
    }

    @Test
    void unsupportedMaterialDoesNotReturnSuccess() throws Exception {
        rejected(request(220005, 2, 0, location(2)));
    }

    @Test
    void unknownCreatorOperationCannotCreate() throws Exception {
        own(POT);
        rejected(request(POT, 99, 0, location(2)));
    }

    @Test
    void noneOperationCannotCreate() throws Exception {
        own(POT);
        rejected(request(POT, 0, 0, location(2)));
    }

    @Test
    void missingLocationCannotPlaceAtWorldOrigin() throws Exception {
        own(POT);
        rejected(request(POT, 2, 0, null));
    }

    @Test
    void locationWithoutPositionCannotCreate() throws Exception {
        own(POT);
        rejected(request(POT, 2, 0, WidgetCreateLocationInfo.getDefaultInstance()));
    }

    @Test
    void nanPositionCannotCreate() throws Exception {
        own(POT);
        rejected(request(POT, 2, 0, location(Float.NaN)));
    }

    @Test
    void excessiveDistanceCannotCreate() throws Exception {
        own(POT);
        rejected(request(POT, 2, 0, location(10000)));
    }

    @Test
    void potIsAnOwnedGadgetAndPreservesTheItem() throws Exception {
        var item = own(POT);
        assertEquals(0, bag(request(POT, 2, 0, location(2))).getRetcode());
        var gadget = assertInstanceOf(EntityGadget.class, placed(70500025).get(0));
        assertSame(env.avatar, gadget.getOwner());
        assertTrue(gadget.getPosition().equal3d(new Position(2, 3, 4)));
        assertTrue(gadget.getRotation().equal3d(new Position(0, 90, 0)));
        assertEquals(1, item.getCount());
        assertTrue(env.fixture.player.getTeamManager().getGadgets().contains(gadget));
    }

    @Test
    void fireworksUsesItsWorktopResource() throws Exception {
        own(FIREWORKS);
        assertEquals(0, bag(request(FIREWORKS, 2, 0, location(2))).getRetcode());
        var gadget = assertInstanceOf(EntityGadget.class, placed(70800058).get(0));
        assertInstanceOf(GadgetWorktop.class, gadget.getContent());
    }

    @Test
    void successfulPlacementEmitsExactlyOneResourceCooldown() throws Exception {
        own(POT);
        long before = System.currentTimeMillis();
        assertEquals(0, bag(request(POT, 2, 0, location(2))).getRetcode());
        var packets =
                env.fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.WidgetCoolDownNotify)
                        .toList();
        assertEquals(1, packets.size());
        var cd =
                WidgetCoolDownNotifyOuterClass.WidgetCoolDownNotify.parseFrom(
                                packets.get(0).getData())
                        .getGroupCoolDownDataList(0);
        assertEquals(15, cd.getId());
        assertTrue(cd.getIsSuccess());
        assertTrue(cd.getCoolDownTime() >= before + 5000);
        assertTrue(cd.getCoolDownTime() <= System.currentTimeMillis() + 5000);
    }

    @Test
    void duplicateDuringCooldownCannotCreateAnotherEntity() throws Exception {
        own(POT);
        assertEquals(0, bag(request(POT, 2, 0, location(2))).getRetcode());
        assertEquals(
                RetcodeOuterClass.Retcode.RET_ITEM_IN_COOLDOWN.getNumber(),
                bag(request(POT, 2, 0, location(3))).getRetcode());
        assertEquals(1, placed(70500025).size());
    }

    @Test
    void retractRemovesOwnPlacementInsteadOfCreatingAnother() throws Exception {
        own(POT);
        bag(request(POT, 2, 0, location(2)));
        int id = placed(70500025).get(0).getId();
        assertEquals(0, bag(request(POT, 1, id, null)).getRetcode());
        assertTrue(placed(70500025).isEmpty());
        assertTrue(env.fixture.player.getTeamManager().getGadgets().isEmpty());
        var packet =
                env.fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.WidgetGadgetDataNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        assertTrue(
                WidgetGadgetDataNotifyOuterClass.WidgetGadgetDataNotify.parseFrom(packet.getData())
                        .getWidgetGadgetData()
                        .getGadgetEntityIdListList()
                        .isEmpty());
    }

    @Test
    void retractCannotTargetAnUnrelatedEntity() throws Exception {
        own(POT);
        bag(request(POT, 2, 0, location(2)));
        var original = placed(70500025).get(0);
        var other = new EntityGadget(env.fixture.scene, 70500025, new Position());
        env.fixture.scene.addEntity(other);
        assertNotEquals(0, bag(request(POT, 1, other.getId(), null)).getRetcode());
        assertSame(original, env.fixture.scene.getEntityById(original.getId()));
        assertSame(other, env.fixture.scene.getEntityById(other.getId()));
        assertEquals(2, placed(70500025).size());
    }

    @Test
    void sceneRemovalClearsOnlyTheRemovedWidget() throws Exception {
        own(POT);
        own(FIREWORKS);
        bag(request(POT, 2, 0, location(2)));
        bag(request(FIREWORKS, 2, 0, location(3)));
        var pot = placed(70500025).get(0);
        var fireworks = placed(70800058).get(0);
        env.fixture.scene.removeEntity(pot);
        assertFalse(env.fixture.player.getTeamManager().getGadgets().contains(pot));
        assertTrue(env.fixture.player.getTeamManager().getGadgets().contains(fireworks));
        assertSame(fireworks, env.fixture.scene.getEntityById(fireworks.getId()));
    }

    @Test
    void malformedPacketReceivesAnError() {
        assertDoesNotThrow(
                () ->
                        new HandlerWidgetDoBagReq()
                                .handle(
                                        env.fixture.player.getSession(),
                                        new byte[0],
                                        new byte[] {(byte) 0xff}));
        var packet =
                env.fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.WidgetDoBagRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(
                0,
                assertDoesNotThrow(() -> WidgetDoBagRsp.parseFrom(packet.getData())).getRetcode());
    }

    @Test
    void missingSceneReceivesAnError() throws Exception {
        own(POT);
        env.fixture.player.setScene(null);
        rejected(request(POT, 2, 0, location(2)));
    }

    UseWidgetCreateGadgetReqOuterClass.UseWidgetCreateGadgetReq flatRequest(int id) {
        return UseWidgetCreateGadgetReqOuterClass.UseWidgetCreateGadgetReq.newBuilder()
                .setMaterialId(id)
                .setPos(new Position(2, 3, 4).toProto())
                .build();
    }

    @Test
    void currentCreateHandlerUsesWidgetConfigEvenWhenMaterialGadgetIdIsZero() throws Exception {
        own(POT);
        assertEquals(0, GameData.getItemDataMap().get(POT).getGadgetId());
        var unrelated = new EntityGadget(env.fixture.scene, 70800058, new Position());
        env.fixture.scene.addEntity(unrelated);
        env.fixture.player.getTeamManager().getGadgets().add(unrelated);
        new HandlerUseWidgetCreateGadgetReq()
                .handle(
                        env.fixture.player.getSession(),
                        new byte[0],
                        flatRequest(POT).toByteArray());
        var packet =
                env.fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.UseWidgetCreateGadgetRsp)
                        .findFirst()
                        .orElseThrow();
        var response =
                UseWidgetCreateGadgetRspOuterClass.UseWidgetCreateGadgetRsp.parseFrom(
                        packet.getData());
        assertEquals(0, response.getRetcode());
        assertEquals(POT, response.getMaterialId());
        assertEquals(1, placed(70500025).size());
        assertSame(unrelated, env.fixture.scene.getEntityById(unrelated.getId()));
        assertTrue(env.fixture.player.getTeamManager().getGadgets().contains(unrelated));
    }

    @Test
    void createHandlerRejectsAnUnownedItemAndStillAcknowledgesIt() throws Exception {
        new HandlerUseWidgetCreateGadgetReq()
                .handle(
                        env.fixture.player.getSession(),
                        new byte[0],
                        flatRequest(POT).toByteArray());
        var packet =
                env.fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.UseWidgetCreateGadgetRsp)
                        .findFirst()
                        .orElseThrow();
        var response =
                UseWidgetCreateGadgetRspOuterClass.UseWidgetCreateGadgetRsp.parseFrom(
                        packet.getData());
        assertEquals(
                RetcodeOuterClass.Retcode.RET_ITEM_NOT_EXIST.getNumber(), response.getRetcode());
        assertEquals(POT, response.getMaterialId());
        assertTrue(placed(70500025).isEmpty());
    }

    @Test
    void createAndBagRequestsShareTheirCooldown() throws Exception {
        own(POT);
        new HandlerUseWidgetCreateGadgetReq()
                .handle(
                        env.fixture.player.getSession(),
                        new byte[0],
                        flatRequest(POT).toByteArray());
        assertEquals(
                RetcodeOuterClass.Retcode.RET_ITEM_IN_COOLDOWN.getNumber(),
                bag(request(POT, 2, 0, location(3))).getRetcode());
        assertEquals(1, placed(70500025).size());
    }

    @Test
    void malformedCreateRequestGetsAnErrorWithoutSpawning() throws Exception {
        new HandlerUseWidgetCreateGadgetReq()
                .handle(env.fixture.player.getSession(), new byte[0], new byte[] {(byte) 0xff});
        var packet =
                env.fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.UseWidgetCreateGadgetRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotEquals(
                0,
                UseWidgetCreateGadgetRspOuterClass.UseWidgetCreateGadgetRsp.parseFrom(
                                packet.getData())
                        .getRetcode());
        assertTrue(placed(70500025).isEmpty());
    }

    @Test
    void expiryReplacesOnlyThePreviousOwnedPlacement() {
        own(POT);
        var now = new AtomicLong(1_800_000_000_000L);
        var manager = new WidgetPlacementManager(env.fixture.player, now::get);
        var first = request(POT, 2, 0, location(2));
        assertEquals(0, manager.useBag(first));
        var old = placed(70500025).get(0);
        assertEquals(now.get() + 5000, manager.cooldowns().get(0).getCoolDownTime());
        now.addAndGet(4999);
        assertEquals(
                RetcodeOuterClass.Retcode.RET_ITEM_IN_COOLDOWN.getNumber(), manager.useBag(first));
        assertSame(old, env.fixture.scene.getEntityById(old.getId()));
        now.incrementAndGet();
        assertTrue(manager.cooldowns().isEmpty());
        assertEquals(0, manager.useBag(request(POT, 2, old.getId(), location(4))));
        assertEquals(1, placed(70500025).size());
        assertNotEquals(old.getId(), placed(70500025).get(0).getId());
        assertNull(env.fixture.scene.getEntityById(old.getId()));
        assertFalse(env.fixture.player.getTeamManager().getGadgets().contains(old));
        assertEquals(1, env.fixture.player.getTeamManager().getGadgets().size());
    }

    @Test
    void failedReplacementPreservesTheOldEntityAndDoesNotStartCooldown() {
        own(POT);
        var now = new AtomicLong(1_800_000_000_000L);
        var manager = new WidgetPlacementManager(env.fixture.player, now::get);
        assertEquals(0, manager.useBag(request(POT, 2, 0, location(2))));
        var previous = placed(70500025).get(0);
        now.addAndGet(5000);
        env.put(GameData.getGadgetDataMap(), 70500025, null);
        assertEquals(
                RetcodeOuterClass.Retcode.RET_GADGET_CREATE_FAIL.getNumber(),
                manager.useBag(request(POT, 2, previous.getId(), location(4))));
        assertSame(previous, env.fixture.scene.getEntityById(previous.getId()));
        assertTrue(manager.cooldowns().isEmpty());
    }

    @Test
    void failedAppearanceRollsBackOnlyTheNewEntityAndCanRetry() throws Exception {
        own(POT);
        var now = new AtomicLong(1_800_000_000_000L);
        var manager = new WidgetPlacementManager(env.fixture.player, now::get);
        assertEquals(0, manager.useBag(request(POT, 2, 0, location(2))));
        var previous = placed(70500025).get(0);
        now.addAndGet(5000);
        var packets = AbilityTestFixture.TestScene.class.getDeclaredField("packets");
        packets.setAccessible(true);
        var original = packets.get(env.fixture.scene);
        var fail = new AtomicBoolean(true);
        packets.set(
                env.fixture.scene,
                new CopyOnWriteArrayList<emu.grasscutter.net.packet.BasePacket>() {
                    @Override
                    public boolean add(emu.grasscutter.net.packet.BasePacket packet) {
                        if (packet.getOpcode() == PacketOpcodes.SceneEntityAppearNotify
                                && fail.get())
                            throw new IllegalStateException("injected widget appearance failure");
                        return super.add(packet);
                    }
                });
        try {
            var next = request(POT, 2, previous.getId(), location(4));
            assertEquals(
                    RetcodeOuterClass.Retcode.RET_GADGET_CREATE_FAIL.getNumber(),
                    manager.useBag(next));
            assertEquals(1, placed(70500025).size());
            assertSame(previous, placed(70500025).get(0));
            assertTrue(manager.cooldowns().isEmpty());
            fail.set(false);
            assertEquals(0, manager.useBag(next));
            assertEquals(1, placed(70500025).size());
            assertEquals(1, env.fixture.player.getTeamManager().getGadgets().size());
        } finally {
            packets.set(env.fixture.scene, original);
        }
    }

    @Test
    void fullWidgetSnapshotIncludesActiveResourceCooldowns() throws Exception {
        own(POT);
        own(FIREWORKS);
        assertEquals(0, bag(request(POT, 2, 0, location(2))).getRetcode());
        assertEquals(0, bag(request(FIREWORKS, 2, 0, location(3))).getRetcode());
        var packet =
                new emu.grasscutter.server.packet.send.PacketAllWidgetDataNotify(
                        env.fixture.player);
        var snapshot =
                AllWidgetDataNotifyOuterClass.AllWidgetDataNotify.parseFrom(packet.getData());
        assertEquals(
                List.of(15, 24),
                snapshot.getCoolDownGroupDataListList().stream()
                        .map(WidgetCoolDownDataOuterClass.WidgetCoolDownData::getId)
                        .toList());
    }

    @Test
    void leavingTheSceneRemovesBothWidgetsWithoutMutatingTheIteration() throws Exception {
        own(POT);
        own(FIREWORKS);
        bag(request(POT, 2, 0, location(2)));
        bag(request(FIREWORKS, 2, 0, location(3)));
        assertEquals(2, env.fixture.player.getTeamManager().getGadgets().size());
        env.fixture.scene.setDontDestroyWhenEmpty(true);
        assertDoesNotThrow(() -> env.fixture.scene.removePlayer(env.fixture.player));
        assertTrue(placed(70500025).isEmpty());
        assertTrue(placed(70800058).isEmpty());
        assertTrue(env.fixture.player.getTeamManager().getGadgets().isEmpty());
    }

    @Test
    void simultaneousBagAndCreateCannotBypassTheCooldown() throws Exception {
        own(POT);
        var manager = env.fixture.player.getWidgets().placements();
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var bag =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                start.await();
                                return manager.useBag(request(POT, 2, 0, location(2)));
                            });
            var create =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                start.await();
                                return manager.create(flatRequest(POT));
                            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var codes =
                    new ArrayList<>(
                            List.of(bag.get(5, TimeUnit.SECONDS), create.get(5, TimeUnit.SECONDS)));
            Collections.sort(codes);
            assertEquals(
                    List.of(0, RetcodeOuterClass.Retcode.RET_ITEM_IN_COOLDOWN.getNumber()), codes);
            assertEquals(1, placed(70500025).size());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void widgetAbilitiesBelongToThePlacingPlayerInAnotherHostsWorld() throws Exception {
        own(POT);
        var data =
                JsonUtils.decode(
                        "{abilityName:'WidgetOwnership',modifiers:{}}",
                        emu.grasscutter.data.binout.AbilityData.class);
        env.put(GameData.getAbilityDataMap(), data.abilityName, data);
        var name = GameData.getGadgetDataMap().get(70500025).getJsonName();
        env.put(
                GameData.getGadgetConfigData(),
                name,
                JsonUtils.decode(
                        "{abilities:[{abilityName:'WidgetOwnership'}]}",
                        emu.grasscutter.data.binout.config.ConfigEntityGadget.class));
        var world = env.fixture.scene.getWorld();
        var host = emu.grasscutter.game.world.World.class.getDeclaredField("host");
        host.setAccessible(true);
        var old = host.get(world);
        var other = new AbilityTestFixture.TestPlayer();
        other.setUid(10002);
        other.setWorld(world);
        host.set(world, other);
        try {
            assertEquals(0, bag(request(POT, 2, 0, location(2))).getRetcode());
            var ability =
                    placed(70500025).get(0).getInstancedAbilities().stream()
                            .filter(Objects::nonNull)
                            .filter(a -> a.getData().abilityName.equals("WidgetOwnership"))
                            .findFirst()
                            .orElseThrow();
            assertSame(env.fixture.player, ability.getPlayerOwner());
        } finally {
            host.set(world, old);
        }
    }

    @Test
    void staleUnregisteredSceneCannotCreateWidgets() throws Exception {
        own(POT);
        env.fixture.scene.getWorld().deregisterScene(env.fixture.scene);
        rejected(request(POT, 2, 0, location(2)));
    }
}
