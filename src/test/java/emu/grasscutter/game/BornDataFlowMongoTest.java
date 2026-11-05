package emu.grasscutter.game;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.GuardedPlayerTestStore;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.QueryCurrRegionHttpRspOuterClass.QueryCurrRegionHttpRsp;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.net.proto.SetPlayerBornDataRspOuterClass.SetPlayerBornDataRsp;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.game.GameSessionManager;
import emu.grasscutter.server.http.dispatch.RegionHandler;
import emu.grasscutter.server.packet.recv.HandlerPlayerLoginReq;
import emu.grasscutter.server.packet.recv.HandlerSetPlayerBornDataReq;
import emu.grasscutter.utils.JsonUtils;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Real creation/Player+Item Guards and Mongo; onLogin is an explicit continuation sink. */
class BornDataFlowMongoTest {
    private static final String ACCOUNT = "born-fixture", TOKEN = "born-fixture-token";
    private static final int UID = 72410;
    private final List<Runnable> restores = new ArrayList<>();
    private MongoClient mongo;
    private Datastore store;
    private GameServer server;
    private ProbeSession session;
    private BornPlayer player;

    @BeforeEach
    void setup() throws Exception {
        var uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        mongo = MongoClients.create(uri);
        store = Morphia.createDatastore(mongo, "lunagc_test_born_" + UUID.randomUUID().toString().replace("-", ""),
                MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
        store.getMapper().map(Account.class, BornPlayer.class, Avatar.class, GameItem.class);
        replace(DatabaseManager.class, null, "gameDatastore", store);
        var mode = Grasscutter.getRunMode();
        restores.add(() -> Grasscutter.setRunModeOverride(mode));
        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.DISPATCH_ONLY);
        server = new GameServer(); // No transport, scheduler or live game systems.
        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.HYBRID);
        replace(GameServer.class, server, "players", new ConcurrentHashMap<>());
        replace(Grasscutter.class, null, "gameServer", server);
        replace(Grasscutter.class, null, "pluginManager", new PluginManager());
        replace(ResourceLoader.class, null, "loadedAll", true);
        boolean questing = GAME_OPTIONS.questing.enabled;
        restores.add(() -> GAME_OPTIONS.questing.enabled = questing);
        GAME_OPTIONS.questing.enabled = true;
        for (int id : new int[] {501, 504, 701, 704}) {
            var depot = JsonUtils.decode("{id:" + id + ",skills:[],talents:[],inherentProudSkillOpens:[]}", AvatarSkillDepotData.class);
            depot.onLoad();
            put(GameData.getAvatarSkillDepotDataMap(), id, depot);
        }
        for (int id : new int[] {10000005, 10000007}) {
            var data = new AvatarData() {
                public int getId() { return id; }
                public List<Integer> getFetters() { return List.of(); }
                public int getInitialWeapon() { return 11001; }
                public float getGrowthCurveById(int level, emu.grasscutter.game.props.FightProperty prop) { return 1; }
            };
            put(GameData.getAvatarDataMap(), id, data);
        }
        var weapon = JsonUtils.decode("{id:11001,itemType:'ITEM_WEAPON',rankLevel:1,skillAffix:[],weaponProp:[]}", ItemData.class);
        weapon.onLoad();
        put(GameData.getItemDataMap(), 11001, weapon);
        @SuppressWarnings("unchecked")
        var regions = (Map<String, RegionHandler.RegionData>) field(RegionHandler.class, "regions").get(null);
        put(regions, "os_usa", new RegionHandler.RegionData(QueryCurrRegionHttpRsp.getDefaultInstance(), ""));
        store.getDatabase().getCollection("accounts").insertOne(new Document("_id", ACCOUNT)
                .append("username", ACCOUNT).append("token", TOKEN).append("permissions", List.of()));
        session = new ProbeSession(server);
        assertTrue(AccountLoginAdmission.begin(session, ACCOUNT, TOKEN, ignored -> {}));
        player = new BornPlayer();
        player.setUid(UID);
        field(Player.class, "accountId").set(player, ACCOUNT);
        field(Player.class, "teamManager").set(player, new TeamManager(player));
        player.setNickname("Before");
        session.setPlayer(player);
        session.setState(SessionState.WAITING_FOR_LOGIN);
        DatabaseHelper.saveGameSync(player);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (mongo == null) return;
        session.close();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        for (int n = restores.size() - 1; n >= 0; n--) restores.get(n).run();
        assertTrue(store.getDatabase().getName().startsWith("lunagc_test_born_"));
        store.getDatabase().drop();
        mongo.close();
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private void replace(Class<?> type, Object receiver, String name, Object value) throws Exception {
        var field = field(type, name);
        var old = field.get(receiver);
        restores.add(() -> {
            try { field.set(receiver, old); }
            catch (Exception error) { throw new AssertionError(error); }
        });
        field.set(receiver, value);
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restores.add(() -> { if (old == null) map.remove(key); else map.put(key, old); });
    }

    private Document row() {
        return store.getDatabase().getCollection("players").find(new Document("_id", UID)).first();
    }

    private void login() throws Exception {
        new HandlerPlayerLoginReq().handle(session, new byte[0], new byte[0]);
    }

    private void born(int avatar, String name) throws Exception {
        new HandlerSetPlayerBornDataReq().handle(session, new byte[0],
                SetPlayerBornDataReq.newBuilder().setAvatarId(avatar).setNickName(name).build().toByteArray());
    }

    private int lastError() throws Exception {
        var packet = session.sent.get(session.sent.size() - 1);
        assertEquals(414, packet.getOpcode());
        return SetPlayerBornDataRsp.parseFrom(packet.getData()).getRetcode();
    }

    @Test
    void aNewAccountWaitsForExplicitSelectionWithoutCreatingAnything() throws Exception {
        var before = row();
        login();
        assertEquals(SessionState.PICKING_CHARACTER, session.getState());
        assertEquals(0, player.logins);
        assertEquals(0, player.getAvatars().getAvatarCount());
        assertEquals(before, row());
        assertEquals(List.of(5008, PacketOpcodes.PlayerLoginRsp), session.sent.stream().map(BasePacket::getOpcode).toList());
        assertArrayEquals(new byte[0], session.sent.get(0).getData());
        login();
        assertEquals(0, player.logins);
        assertEquals(0, store.getDatabase().getCollection("avatars").countDocuments());
        assertEquals(before, row());
    }

    @ParameterizedTest
    @CsvSource({"10000005,true,501", "10000007,true,701", "10000005,false,504", "10000007,false,704"})
    void successConfirmsTheSelectedCharacterTeamAndWeaponBeforeContinuing(int avatarId, boolean questing, int depot) throws Exception {
        GAME_OPTIONS.questing.enabled = questing;
        login();
        born(avatarId, "旅人🚀");
        assertEquals(0, lastError());
        assertEquals(1, player.logins);
        assertEquals(1, player.mails);
        assertEquals(SessionState.ACTIVE, session.getState());
        assertEquals(avatarId, row().getInteger("mainCharacterId"));
        var cold = store.find(BornPlayer.class).first();
        assertNotNull(cold);
        assertEquals("旅人🚀", cold.getNickname());
        assertEquals(avatarId, cold.getHeadImage());
        assertEquals(List.of(avatarId), cold.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars());
        assertEquals(depot, store.find(Avatar.class).first().getSkillDepotId());
        assertEquals(1, store.getDatabase().getCollection("avatars").countDocuments());
        assertEquals(1, store.getDatabase().getCollection("items").countDocuments());
        assertEquals(avatarId, store.find(GameItem.class).first().getEquipCharacter());
        assertNotNull(row().get("saveStamp"));
    }

    @Test
    void aDuplicateCannotRenameRecreateReloginOrSendTheWelcomeTwice() throws Exception {
        login();
        born(10000005, "Aether");
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        var before = row();
        var router = new GameServerPacketHandler(PacketHandler.class);
        router.handle(session, 8541, new byte[0], SetPlayerBornDataReq.newBuilder()
                .setAvatarId(10000007).setNickName("Changed").build().toByteArray());
        assertEquals(116, lastError());
        assertArrayEquals(HexFormat.of().parseHex("7874"), session.sent.get(session.sent.size() - 1).getData());
        assertEquals(before, row());
        assertEquals("Aether", player.getNickname());
        assertEquals(List.of(10000005), player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars());
        assertEquals(1, player.logins);
        assertEquals(1, player.mails);
        assertEquals(1, store.getDatabase().getCollection("avatars").countDocuments());
        assertEquals(1, store.getDatabase().getCollection("items").countDocuments());
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "blank", "control", "avatar", "high-avatar", "legacy-tag", "malformed", "missing-resource"})
    void rejectedCreationReturnsNonzeroAndNeverMutatesOrLogsIn(String kind) throws Exception {
        login();
        var before = row();
        int avatar = kind.equals("avatar") ? 10000001 : kind.equals("high-avatar") ? -1 : 10000005;
        String name = switch (kind) { case "empty" -> ""; case "blank" -> "  "; case "control" -> "A\0B"; default -> "Aether"; };
        if (kind.equals("missing-resource")) GameData.getAvatarDataMap().remove(10000005);
        if (kind.equals("legacy-tag") || kind.equals("malformed")) {
            var wire = HexFormat.of().parseHex(kind.equals("legacy-tag") ? "1085ade204220178" : "220378");
            new HandlerSetPlayerBornDataReq().handle(session, new byte[0], wire);
        } else born(avatar, name);
        assertEquals(-1, lastError());
        assertArrayEquals(HexFormat.of().parseHex("78ffffffffffffffffff01"), session.sent.get(session.sent.size() - 1).getData());
        assertEquals(0, player.logins);
        assertEquals(0, player.mails);
        assertEquals(0, player.getAvatars().getAvatarCount());
        assertEquals(SessionState.PICKING_CHARACTER, session.getState());
        assertEquals(before, row());
        assertEquals(0, store.getDatabase().getCollection("items").countDocuments());
    }

    @Test
    void rotatedCredentialsCannotReachCreation() throws Exception {
        login();
        var account = DatabaseHelper.getAccountById(ACCOUNT);
        account.setToken("rotated");
        DatabaseHelper.saveAccountSync(account);
        int packets = session.sent.size();
        born(10000005, "Aether");
        assertEquals(packets, session.sent.size());
        assertTrue(session.isLoginRevoked());
        assertEquals(0, player.logins);
        assertEquals(0, player.getAvatars().getAvatarCount());
    }

    @Test
    void aConfirmedOldAccountDoesNotReenterSelectionAfterColdReload() throws Exception {
        login();
        born(10000007, "Lumine");
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        var before = row();
        session.close();
        session = new ProbeSession(server);
        assertTrue(AccountLoginAdmission.begin(session, ACCOUNT, TOKEN, ignored -> {}));
        player = store.find(BornPlayer.class).first();
        session.setPlayer(player);
        player.getAvatars().loadFromDatabase();
        session.setState(SessionState.WAITING_FOR_LOGIN);
        login();
        assertEquals(1, player.logins);
        assertEquals(0, player.mails);
        assertEquals(SessionState.ACTIVE, session.getState());
        assertFalse(session.sent.stream().anyMatch(p -> p.getOpcode() == 5008));
        assertEquals(before, row());
    }

    @ParameterizedTest
    @ValueSource(strings = {"before-write", "after-write"})
    void failedSaveConfirmationCannotSendSuccessOrEnterLogin(String point) throws Exception {
        login();
        GuardedPlayerTestStore.loadSeededProfile(player, store,
                () -> { if (point.equals("before-write")) throw new IllegalStateException("injected before write"); },
                ignored -> { if (point.equals("after-write")) throw new IllegalStateException("injected confirmation loss"); });
        assertThrows(IllegalStateException.class, () -> born(10000005, "Aether"));
        assertEquals(-1, lastError());
        assertEquals(0, player.logins);
        assertEquals(0, player.mails);
        assertTrue(session.isLoginRevoked());
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        int packets = session.sent.size();
        born(10000007, "Retry");
        assertEquals(packets, session.sent.size());
        assertEquals(1, store.getDatabase().getCollection("avatars").countDocuments());
        assertEquals(1, store.getDatabase().getCollection("items").countDocuments());
    }

    @Test
    void anAvatarOnlyPartialSaveCannotBeMistakenForACompletedOldAccount() throws Exception {
        login();
        GuardedPlayerTestStore.loadSeededProfile(player, store,
                () -> { throw new IllegalStateException("injected player save rejection"); }, ignored -> {});
        assertThrows(IllegalStateException.class, () -> born(10000005, "Aether"));
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
        assertEquals(0, row().getInteger("mainCharacterId"));
        session = new ProbeSession(server);
        assertTrue(AccountLoginAdmission.begin(session, ACCOUNT, TOKEN, ignored -> {}));
        player = store.find(BornPlayer.class).first();
        session.setPlayer(player);
        player.getAvatars().loadFromDatabase();
        assertEquals(1, player.getAvatars().getAvatarCount());
        session.setState(SessionState.WAITING_FOR_LOGIN);
        login();
        assertTrue(session.isLoginRevoked());
        assertEquals(0, player.logins);
        assertTrue(session.sent.isEmpty());
    }

    @Entity(value = "players", useDiscriminator = false)
    public static class BornPlayer extends Player {
        @Transient transient int logins;
        @Transient transient int mails;

        @Override
        public void onLogin() {
            var store = DatabaseManager.getGameDatastore();
            var row = store.getDatabase().getCollection("players").find(new Document("_id", getUid())).first();
            assertNotNull(row);
            assertEquals(getMainCharacterId(), row.getInteger("mainCharacterId"));
            assertEquals(getNickname(), row.getString("nickname"));
            assertEquals(1, store.getDatabase().getCollection("avatars").countDocuments());
            assertEquals(1, store.getDatabase().getCollection("items").countDocuments());
            logins++;
            getSession().setState(SessionState.ACTIVE);
        }

        @Override
        public void sendMail(Mail mail) { mails++; }
    }

    static class ProbeSession extends GameSession {
        final List<BasePacket> sent = new ArrayList<>();

        ProbeSession(GameServer server) {
            super(server);
            onConnected(new GameSessionManager.KcpTunnel() {
                public InetSocketAddress getAddress() { return new InetSocketAddress("127.0.0.1", 12345); }
                public void writeData(byte[] bytes) {}
                public void close() {}
                public int getSrtt() { return 0; }
            });
        }

        @Override
        public void send(BasePacket packet) { if (!isLoginRevoked()) sent.add(packet); }
    }
}
