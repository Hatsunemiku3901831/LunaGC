package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.entity.EntityItem;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.ItemUseAction.UseItemParams;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class UseOnGainPickupTest {
    private static ItemData quartz;
    private static BuffData buff;
    private PhlogistonMutationTest env;
    private final List<Runnable> restores = new ArrayList<>();
    private final AtomicInteger uses = new AtomicInteger();

    @BeforeAll
    static void resources() throws Exception {
        quartz =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(d -> d.getId() == 101005)
                        .findFirst()
                        .orElseThrow();
        buff =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/BuffExcelConfigData.json"),
                                BuffData.class)
                        .stream()
                        .filter(d -> d.getId() == 500101)
                        .findFirst()
                        .orElseThrow();
        quartz.onLoad();
        buff.onLoad();
        assertTrue(quartz.isUseOnGain());
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        V old = map.put(key, value);
        restores.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMutationTest();
        env.setup();
        var player = env.fixture.player;
        player.setUid(10001);
        player.setWorld(env.fixture.scene.getWorld());
        var server = env.fixture.scene.getWorld().getServer();
        player.setSession(new GameSession(server));
        var field = GameServer.class.getDeclaredField("inventorySystem");
        field.setAccessible(true);
        field.set(
                server,
                new InventorySystem(
                        server,
                        () -> 100000,
                        ignored -> fail("Instant use must not create a stored material")) {
                    @Override
                    public synchronized boolean useItemDirect(ItemData data, UseItemParams params) {
                        uses.incrementAndGet();
                        return super.useItemDirect(data, params);
                    }
                });
        put(GameData.getItemDataMap(), quartz.getId(), quartz);
        put(GameData.getBuffDataMap(), buff.getId(), buff);
    }

    @AfterEach
    void close() {
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
        env.close();
    }

    private EntityItem drop() {
        var drop =
                new EntityItem(
                        env.fixture.scene,
                        env.fixture.player,
                        GameData.getItemDataMap().get(101005),
                        new Position(),
                        1,
                        true);
        env.fixture.scene.getEntities().put(drop.getId(), drop);
        return drop;
    }

    private void pickup(EntityItem item) {
        item.onInteract(env.fixture.player, GadgetInteractReq.getDefaultInstance());
    }

    @Test
    void acceptedNativeEffectDespawnDropWithoutInventingStoredMaterial() {
        var drop = drop();
        pickup(drop);
        assertEquals(1, uses.get());
        assertTrue(env.fixture.player.getBuffManager().hasBuff(51));
        assertFalse(env.fixture.scene.getEntities().containsKey(drop.getId()));
        assertNull(
                env.fixture
                        .player
                        .getInventory()
                        .getInventoryTab(ItemType.ITEM_MATERIAL)
                        .getItemById(101005));
        assertTrue(
                env.fixture.player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.StoreItemChangeNotify));
    }

    @Test
    void rejectedEffectKeepsDropAvailable() {
        var data = GameData.getBuffDataMap().remove(500101);
        try {
            var drop = drop();
            pickup(drop);
            assertSame(drop, env.fixture.scene.getEntities().get(drop.getId()));
            assertFalse(env.fixture.player.getBuffManager().hasBuff(51));
        } finally {
            GameData.getBuffDataMap().put(500101, data);
        }
    }

    @Test
    void directAdmissionReportsTheActualUseResult() {
        assertTrue(
                env.fixture
                        .player
                        .getInventory()
                        .addItem(new GameItem(quartz), ActionReason.SubfieldDrop));
        assertEquals(1, uses.get());
        assertTrue(env.fixture.player.getBuffManager().hasBuff(51));
    }

    @Test
    void batchedEffectsReportAcquisitionWithoutStoredItemPacket() {
        env.fixture
                .player
                .getInventory()
                .addItems(List.of(new GameItem(quartz)), ActionReason.SubfieldDrop);
        assertEquals(1, uses.get());
        assertTrue(env.fixture.player.getBuffManager().hasBuff(51));
        assertTrue(
                env.fixture.player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.StoreItemChangeNotify));
        assertTrue(
                env.fixture.player.packets.stream()
                        .anyMatch(p -> p.getOpcode() == PacketOpcodes.ItemAddHintNotify));
    }

    @Test
    void consumedDropCannotApplyAgainThroughARetainedReference() {
        var drop = drop();
        pickup(drop);
        pickup(drop);
        assertEquals(1, uses.get());
    }

    @Test
    void parallelPickupAppliesEffectOnce() throws Exception {
        var drop = drop();
        var gate = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 2; i++)
                jobs.add(
                        executor.submit(
                                () -> {
                                    gate.await(5, TimeUnit.SECONDS);
                                    pickup(drop);
                                    return null;
                                }));
            for (var job : jobs) job.get(6, TimeUnit.SECONDS);
            assertEquals(1, uses.get());
            assertFalse(env.fixture.scene.getEntities().containsKey(drop.getId()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void partialNativeEffectCannotBeRepeatedFromTheDrop() throws Exception {
        var nodes =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/ExcelBinOutput/MaterialExcelConfigData.json")))
                        .getAsJsonArray();
        var node =
                java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                        .map(n -> n.getAsJsonObject())
                        .filter(n -> n.get("id").getAsInt() == 101005)
                        .findFirst()
                        .orElseThrow()
                        .deepCopy();
        node.add(
                "itemUse",
                JsonParser.parseString(
                        "[{useOp:'ITEM_USE_ADD_SERVER_BUFF',useParam:['500101','25']},{useOp:'ITEM_USE_ADD_SERVER_BUFF',useParam:['999999','25']}"
                            + " ]"));
        var partial = JsonUtils.decode(node, ItemData.class);
        partial.onLoad();
        put(GameData.getItemDataMap(), 101005, partial);
        var drop = drop();
        pickup(drop);
        assertTrue(env.fixture.player.getBuffManager().hasBuff(51));
        assertFalse(env.fixture.scene.getEntities().containsKey(drop.getId()));
        pickup(drop);
        assertEquals(1, uses.get());
    }
}
