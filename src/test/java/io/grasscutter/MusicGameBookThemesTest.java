package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.UnknownFieldSet;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;

import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MusicGameBookAllDataNotifyOuterClass.MusicGameBookAllDataNotify;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.UseItemReqOuterClass.UseItemReq;
import emu.grasscutter.net.proto.UseItemRspOuterClass.UseItemRsp;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.JsonUtils;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real resource coupons, request dispatch, inventory admission and isolated guarded Player writes.
 */
class MusicGameBookThemesTest extends QuestTestFixture {
    static final int BOOK = 220110;
    static final int THEME_NOTIFY = 22198;
    static GameServerPacketHandler dispatcher;
    static List<ItemData> materials;
    final List<Runnable> restores = new ArrayList<>();
    final List<BasePacket> packets = new CopyOnWriteArrayList<>();
    final AtomicBoolean failWrite = new AtomicBoolean();
    final AtomicBoolean failAfterWrite = new AtomicBoolean();
    Thread themeCaller;
    Player actual;
    GuardedPlayerTestStore playerStore;

    @BeforeAll
    static void resources() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
        materials =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(
                                x ->
                                        x.getId() == BOOK
                                                || x.getId() >= 319001 && x.getId() <= 319025)
                        .toList();
        assertEquals(26, materials.size());
        materials.forEach(ItemData::onLoad);
    }

    @BeforeEach
    void musicPlayer() throws Exception {
        themeCaller = Thread.currentThread();
        materials.forEach(this::put);
        replaceField(
                GameServer.class,
                player.server,
                "inventorySystem",
                new InventorySystem(player.server));
        actual = new Player();
        actual.setUid(10002);
        attach(actual);
        playerStore =
                new GuardedPlayerTestStore(
                        actual,
                        () -> {
                            if (failWrite.get())
                                throw new IllegalStateException("theme write fault before commit");
                        },
                        document -> {
                            // Obtain-history can queue an earlier asynchronous Player save.
                            // Inject the lost acknowledgement into the actual grant caller.
                            if (Thread.currentThread() != themeCaller) return;
                            var themes = document.getList("musicGameBookThemes", Integer.class);
                            if (themes != null
                                    && themes.contains(1)
                                    && failAfterWrite.getAndSet(false))
                                throw new IllegalStateException(
                                        "theme acknowledgement lost after commit");
                        });
    }

    void attach(Player target) throws Exception {
        var session =
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        packets.add(packet);
                    }
                };
        session.setPlayer(target);
        session.setState(GameSession.SessionState.ACTIVE);
        target.setSession(session);
        target.setWorld(player.getWorld());
        target.setScene(player.getScene());
        replaceField(Player.class, target, "teamManager", new TeamManager(target));
        target.getTeamManager().setEntity(new EntityTeam(target));
    }

    @AfterEach
    void restoreMusic() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (playerStore != null) playerStore.close();
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
    }

    void put(ItemData data) {
        data.onLoad();
        var old = GameData.getItemDataMap().put(data.getId(), data);
        restores.add(
                () -> {
                    if (old == null) GameData.getItemDataMap().remove(data.getId());
                    else GameData.getItemDataMap().put(data.getId(), old);
                });
    }

    GameItem stock(int id) {
        var item = new GameItem(GameData.getItemDataMap().get(id), 1);
        item.setOwner(actual);
        actual.getInventory().getItems().put(item.getGuid(), item);
        actual.getInventory().getInventoryTab(item.getItemType()).onAddItem(item);
        store.save(item);
        return item;
    }

    UseItemRsp use(GameItem item, int count, int option, List<Integer> options) throws Exception {
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.UseItemReq,
                PacketHead.newBuilder().setClientSequenceId(37).build().toByteArray(),
                UseItemReq.newBuilder()
                        .setGuid(item.getGuid())
                        .setCount(count)
                        .setOptionIdx(option)
                        .addAllOptionIdxList(options)
                        .build()
                        .toByteArray());
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var replies =
                packets.stream().filter(x -> x.getOpcode() == PacketOpcodes.UseItemRsp).toList();
        assertEquals(1, replies.size());
        return UseItemRsp.parseFrom(replies.get(0).getData());
    }

    List<Integer> dataThemes() throws Exception {
        var message =
                MusicGameBookAllDataNotify.parseFrom(
                        packets.stream()
                                .filter(
                                        x ->
                                                x.getOpcode()
                                                        == PacketOpcodes.MusicGameBookAllDataNotify)
                                .reduce((a, b) -> b)
                                .orElseThrow()
                                .getData());
        var field = message.getDescriptorForType().findFieldByNumber(2);
        assertNotNull(field);
        return (List<Integer>) message.getField(field);
    }

    List<Integer> notifiedThemes() throws Exception {
        var result = new ArrayList<Integer>();
        for (var packet : packets)
            if (packet.getOpcode() == THEME_NOTIFY) {
                var fields = UnknownFieldSet.parseFrom(packet.getData()).asMap();
                assertEquals(Set.of(6), fields.keySet());
                var values = fields.get(6).getVarintList();
                assertEquals(1, values.size());
                result.add(Math.toIntExact(values.get(0)));
            }
        return result;
    }

    @Test
    void resourceEnumsUseCurrentNativeConstants() {
        assertEquals(
                57,
                assertDoesNotThrow(() -> MaterialType.valueOf("MATERIAL_MUSIC_GAME_BOOK_THEME"))
                        .getValue());
        assertEquals(
                61,
                assertDoesNotThrow(() -> ItemUseOp.valueOf("ITEM_USE_MUSIC_GAME_BOOK_UNLOCK_THEME"))
                        .getValue());
    }

    @Test
    void bookOpensWithoutConsumptionOrGrantingAnExtraCopy() throws Exception {
        var book = stock(BOOK);
        var response = use(book, 1, 0, List.of());
        assertEquals(0, response.getRetcode());
        assertEquals(BOOK, response.getItemId());
        assertEquals(book.getGuid(), response.getGuid());
        assertSame(book, actual.getInventory().getItemById(BOOK));
        assertEquals(1, book.getCount());
        assertEquals(List.of(8), dataThemes());
        assertNull(playerStore.read(actual.getUid()), "Opening must not write a player profile");
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
        319001,1
        319002,2
        319003,3
        319004,4
        319005,5
        319006,6
        319007,7
        319008,9
        319009,10
        319010,11
        319011,12
        319012,13
        319013,14
        319014,15
        319015,16
        319016,17
        319017,18
        319018,19
        319019,20
        319020,21
        319021,22
        319022,23
        319023,24
        319024,25
        319025,26
        """)
    void everyResourceCouponUnlocksOnGainAndPersists(int item, int theme) throws Exception {
        assertTrue(actual.getInventory().addItem(item));
        assertEquals(0, actual.getInventory().getItemCountById(item));
        assertEquals(0, actual.getInventory().getItemCountById(BOOK));
        assertEquals(List.of(theme), notifiedThemes());
        assertNotNull(playerStore.read(actual.getUid()));
        var book = stock(BOOK);
        assertEquals(0, use(book, 1, 0, List.of()).getRetcode());
        assertEquals(new TreeSet<>(List.of(8, theme)), new TreeSet<>(dataThemes()));
    }

    @Test
    void duplicateGrantIsAcceptedWithoutRepeatingAnUnlockNotification() throws Exception {
        assertTrue(actual.getInventory().addItem(319001));
        var before = savedThemes();
        packets.clear();
        assertTrue(actual.getInventory().addItem(319001));
        assertEquals(List.of(), notifiedThemes());
        // Accepted item deliveries may save obtain-history progress. The unlock set stays fixed.
        assertEquals(before, savedThemes());
        assertEquals(0, actual.getInventory().getItemCountById(319001));
    }

    Set<Integer> savedThemes() {
        var document =
                playerStore
                        .datastore()
                        .getDatabase()
                        .getCollection("players")
                        .withReadConcern(ReadConcern.MAJORITY)
                        .withReadPreference(ReadPreference.primary())
                        .find(new Document("_id", actual.getUid()))
                        .first();
        assertNotNull(document);
        return Set.copyOf(document.getList("musicGameBookThemes", Integer.class));
    }

    @Test
    void anAcknowledgementLostAfterCommitIsConfirmedBeforeOpeningTheBook() throws Exception {
        failAfterWrite.set(true);
        assertThrows(IllegalStateException.class, () -> actual.getInventory().addItem(319001));
        assertEquals(Set.of(1), savedThemes());
        assertEquals(List.of(), notifiedThemes());
        var book = stock(BOOK);
        assertEquals(0, use(book, 1, 0, List.of()).getRetcode());
        assertEquals(List.of(1), notifiedThemes());
        assertEquals(List.of(1, 8), dataThemes());
        packets.clear();
        assertTrue(actual.getInventory().addItem(319001));
        assertEquals(List.of(), notifiedThemes());
        assertEquals(Set.of(1), savedThemes());
    }

    @Test
    void aNotificationFailureDoesNotRejectAnAcknowledgedGrant() throws Exception {
        var session =
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        if (packet.getOpcode() == THEME_NOTIFY)
                            throw new IllegalStateException("disconnected theme recipient");
                        packets.add(packet);
                    }
                };
        session.setPlayer(actual);
        actual.setSession(session);
        assertTrue(actual.getInventory().addItem(319001));
        assertEquals(Set.of(1), savedThemes());
        attach(actual);
        var book = stock(BOOK);
        assertEquals(0, use(book, 1, 0, List.of()).getRetcode());
        assertEquals(List.of(1, 8), dataThemes());
    }

    @Test
    void aCouponStoredByARewardFlowCanAlsoBeUsedFromTheBag() throws Exception {
        var coupon = stock(319001);
        assertEquals(0, use(coupon, 1, 0, List.of()).getRetcode());
        assertEquals(0, actual.getInventory().getItemCountById(319001));
        assertEquals(List.of(1), notifiedThemes());
        assertNotNull(playerStore.read(actual.getUid()));
    }

    @ParameterizedTest
    @CsvSource({"0,0", "-1,0", "2,0", "1,1"})
    void bookRejectsInvalidCountsAndOptionsWithoutPublishingData(int count, int option)
            throws Exception {
        var book = stock(BOOK);
        assertNotEquals(0, use(book, count, option, List.of()).getRetcode());
        assertEquals(1, book.getCount());
        assertTrue(
                packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.MusicGameBookAllDataNotify));
    }

    @Test
    void bookRejectsGroupedChoicesAndForeignOwnership() throws Exception {
        var book = stock(BOOK);
        assertNotEquals(0, use(book, 1, 0, List.of(1)).getRetcode());
        replaceField(GameItem.class, book, "ownerId", 10003);
        assertNotEquals(0, use(book, 1, 0, List.of()).getRetcode());
        assertEquals(1, book.getCount());
        assertTrue(
                packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.MusicGameBookAllDataNotify));
    }

    @Test
    void unsupportedThemeAndMixedRecipeDoNotAcceptAnIncomingItem() throws Exception {
        put(
                JsonUtils.decode(
                        "{id:319090,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_MUSIC_GAME_BOOK_THEME',useTarget:'ITEM_USE_TARGET_NONE',useOnGain:true,itemUse:[{useOp:'ITEM_USE_MUSIC_GAME_BOOK_UNLOCK_THEME',useParam:['99999']}]}",
                        ItemData.class));
        put(
                JsonUtils.decode(
                        "{id:319091,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_MUSIC_GAME_BOOK_THEME',useTarget:'ITEM_USE_TARGET_NONE',useOnGain:true,itemUse:[{useOp:'ITEM_USE_MUSIC_GAME_BOOK_UNLOCK_THEME',useParam:['1']},{useOp:'ITEM_USE_MUSIC_GAME_BOOK_UNLOCK_THEME',useParam:['2']}]}",
                        ItemData.class));
        assertFalse(actual.getInventory().addItem(319090));
        assertFalse(actual.getInventory().addItem(319091));
        assertNull(playerStore.read(actual.getUid()));
        assertEquals(List.of(), notifiedThemes());
    }

    @Test
    void aColdPlayerReloadKeepsAnUnlockedTheme() throws Exception {
        assertTrue(actual.getInventory().addItem(319001));
        var cold =
                playerStore
                        .datastore()
                        .find(Player.class)
                        .filter(Filters.eq("_id", actual.getUid()))
                        .first();
        assertNotNull(cold);
        actual = cold;
        attach(cold);
        var book = stock(BOOK);
        assertEquals(0, use(book, 1, 0, List.of()).getRetcode());
        assertEquals(List.of(1, 8), dataThemes());
    }

    @Test
    void loginPublishesTheSavedThemeSetWithoutGrantingTheBook() throws Exception {
        assertTrue(actual.getInventory().addItem(319001));
        packets.clear();
        actual.getMusicGameBook().onLogin();
        assertEquals(List.of(1, 8), dataThemes());
        assertEquals(0, actual.getInventory().getItemCountById(BOOK));
        assertEquals(List.of(), notifiedThemes());
    }

    @Test
    void distinctThemeGrantsMergeInsteadOfReplacingThePreviousTheme() throws Exception {
        assertTrue(actual.getInventory().addItem(319025));
        assertTrue(actual.getInventory().addItem(319001));
        assertEquals(Set.of(1, 26), savedThemes());
        var book = stock(BOOK);
        assertEquals(0, use(book, 1, 0, List.of()).getRetcode());
        assertEquals(List.of(1, 8, 26), dataThemes());
    }

    @Test
    void openingDuringAPendingWriteCannotPublishAnUnconfirmedTheme() throws Exception {
        failWrite.set(true);
        assertThrows(IllegalStateException.class, () -> actual.getInventory().addItem(319001));
        var book = stock(BOOK);
        assertNotEquals(0, use(book, 1, 0, List.of()).getRetcode());
        assertTrue(
                packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.MusicGameBookAllDataNotify));
        assertEquals(List.of(), notifiedThemes());
        assertEquals(1, book.getCount());
        failWrite.set(false);
        assertEquals(0, use(book, 1, 0, List.of()).getRetcode());
        assertEquals(List.of(1, 8), dataThemes());
        assertEquals(List.of(1), notifiedThemes());
    }

    @Test
    void aDifferentCouponCanConfirmAPendingThemeAndMergeItsOwnTheme() throws Exception {
        failWrite.set(true);
        assertThrows(IllegalStateException.class, () -> actual.getInventory().addItem(319001));
        failWrite.set(false);
        assertTrue(actual.getInventory().addItem(319002));
        assertEquals(Set.of(1, 2), savedThemes());
        assertEquals(List.of(1, 2), notifiedThemes());
        var book = stock(BOOK);
        assertEquals(0, use(book, 1, 0, List.of()).getRetcode());
        assertEquals(List.of(1, 2, 8), dataThemes());
    }

    @Test
    void failedProfileWritePublishesNoUnlockAndTheSameCouponCanRetry() throws Exception {
        failWrite.set(true);
        assertThrows(IllegalStateException.class, () -> actual.getInventory().addItem(319001));
        assertNull(playerStore.read(actual.getUid()));
        assertEquals(List.of(), notifiedThemes());
        failWrite.set(false);
        assertTrue(actual.getInventory().addItem(319001));
        assertEquals(List.of(1), notifiedThemes());
        assertNotNull(playerStore.read(actual.getUid()));
        var book = stock(BOOK);
        assertEquals(0, use(book, 1, 0, List.of()).getRetcode());
        assertEquals(List.of(1, 8), dataThemes());
    }
}
