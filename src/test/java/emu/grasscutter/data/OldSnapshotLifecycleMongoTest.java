package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;
import static emu.grasscutter.data.D02OldSnapshotAdmissionTest.*;
import static emu.grasscutter.data.D02OldSnapshotRestoreMongoTest.*;

import com.google.gson.JsonObject;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import com.mongodb.event.*;
import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.database.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetPlayerTokenReqOuterClass.GetPlayerTokenReq;
import emu.grasscutter.net.proto.GetPlayerTokenRspOuterClass.GetPlayerTokenRsp;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.recv.HandlerGetPlayerTokenReq;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OldSnapshotLifecycleMongoTest {
    @TempDir Path temporary;

    @Test
    void fixedCopyAdmissionWorldTickLogoutAndColdLoadRemainFiniteAndPreserveTheSave() throws Exception {
        var config = Grasscutter.getConfig();
        var previousResources = config.folderStructure.resources;
        var previousData = config.folderStructure.data;
        var old = Configuration.GAME_INFO.oldSnapshot;
        boolean previousMode = old.enabled;
        String previousPin = old.manifestSha256, previousInputRoot = old.inputRoot;
        boolean previousScripts = Configuration.GAME_INFO.enableScriptInBigWorld;
        var previousStore = DatabaseManager.getGameDatastore();
        var previousRunMode = Grasscutter.getRunMode();
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertEquals("mongodb://127.0.0.1:27027", uri);
        Path resourceRoot = Path.of(System.getenv("LUNAGC_OLD_RESOURCE_BUNDLE"));
        config.folderStructure.resources = resourceRoot.toString();
        config.folderStructure.data = temporary.resolve("data").toString();
        old.enabled = true;
        old.manifestSha256 = System.getenv("LUNAGC_OLD_RESOURCE_MANIFEST_SHA256");
        old.inputRoot = Path.of("").toAbsolutePath().toString();
        Configuration.GAME_INFO.enableScriptInBigWorld = false;
        String database = "lunagc_old_lifecycle_" + UUID.randomUUID().toString().replace("-", "");
        var writes = new AtomicInteger();
        var settings = MongoClientSettings.builder().applyConnectionString(new ConnectionString(uri))
                .addCommandListener(new CommandListener() {
                    @Override public void commandStarted(CommandStartedEvent event) {
                        if (database.equals(event.getDatabaseName()) && Set.of("insert", "update", "delete", "findAndModify").contains(event.getCommandName())) writes.incrementAndGet();
                    }
                }).build();
        try (var client = MongoClients.create(settings)) {
            OldSnapshotResources.load();
            assertTrue(OldSnapshotResources.isReady());
            assertTrue(ResourceLoader.isLoginReady());
            assertFalse(ResourceLoader.isLoadedAll());
            assertFalse(M0Resources.isBirthReady());
            var manifest = JsonUtils.loadToClass(resourceRoot.resolve("D02-OLD-RESOURCE-MANIFEST.json"), JsonObject.class);
            var bundle = new D02OldSnapshotResourceLoadTest.Bundle(resourceRoot, manifest, new JsonObject());
            var sourcePlayers = bson(fixedInput(bundle, "selected-0/player.bson"));
            var sourceAvatars = bson(fixedInput(bundle, "selected-0/avatars-0.bson"));
            var sourceItems = bson(fixedInput(bundle, "selected-0/items-0.bson"));
            var sourceAccounts = bson(fixedInput(bundle, "selected-0/account.bson"));
            var store = Morphia.createDatastore(client, database,
                    MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
            store.getMapper().map(Account.class, Player.class, Avatar.class, GameItem.class);
            field(DatabaseManager.class, "gameDatastore").set(null, store);
            var db = store.getDatabase();
            db.getCollection("players").insertMany(sourcePlayers);
            db.getCollection("avatars").insertMany(sourceAvatars);
            db.getCollection("items").insertMany(sourceItems);
            db.getCollection("accounts").insertMany(sourceAccounts);
            Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.DISPATCH_ONLY);
            var server = new GameServer();
            field(GameServer.class, "players").set(server, new java.util.concurrent.ConcurrentHashMap<Integer, Player>());
            field(GameServer.class, "worlds").set(server, Collections.synchronizedSet(new HashSet<emu.grasscutter.game.world.World>()));
            Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.HYBRID);
            var account = store.find(Account.class).first();
            var rejected = new CapturingSession(server);
            int beforeNetwork = writes.get();
            new HandlerGetPlayerTokenReq().handle(rejected, new byte[0], GetPlayerTokenReq.newBuilder()
                    .setAccountUid(account.getId()).setAccountToken(account.getToken()).setKeyId(1).build().toByteArray());
            assertTrue(rejected.isLoginRevoked());
            assertNull(rejected.getPlayer(), "unclosed snapshot wire stops before Player or storage");
            assertEquals(1, rejected.sent.size());
            assertEquals(-1, GetPlayerTokenRsp.parseFrom(rejected.sent.get(0).getData()).getRetcode());
            assertEquals(beforeNetwork, writes.get());
            var selected = store.find(Player.class).first();
            var session = new CapturingSession(server);
            session.setAccount(account);
            session.setPlayer(selected);
            int beforeHydration = writes.get();
            int copiedUid = selected.getUid();
            selected.setUid(copiedUid + 1);
            try { assertThrows(IllegalStateException.class, selected::loadFromDatabase); }
            finally { selected.setUid(copiedUid); }
            var missing = GameData.getItemDataMap().remove(11101);
            try { assertThrows(IllegalStateException.class, selected::loadFromDatabase); }
            finally { GameData.getItemDataMap().put(11101, missing); }
            var reward = GameData.getRewardDataMap().values().iterator().next();
            GameData.getRewardDataMap().remove(reward.getId());
            try { assertThrows(IllegalStateException.class, selected::loadFromDatabase); }
            finally { GameData.getRewardDataMap().put(reward.getId(), reward); }
            assertFalse(selected.getAvatars().isLoaded());
            assertFalse(selected.getInventory().isLoaded());
            assertEquals(beforeHydration, writes.get());
            selected.loadFromDatabase();
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(30)));
            assertSnapshot(selected, sourceAvatars, sourceItems);
            assertFalse(selected.isM0Profile());
            assertNull(selected.getActivityManager());
            assertNull(selected.getDailyTaskManager());
            selected.prepareOldSnapshotWorld();
            var joined = selected.getWorld();
            assertNotNull(joined);
            assertEquals(3, selected.getSceneId());
            assertEquals(1, joined.getPlayerCount());
            assertTrue(joined.getLevelEntityId() != 0);
            assertFalse(selected.getTeamManager().getActiveTeam().isEmpty());
            for (var entity : selected.getTeamManager().getActiveTeam()) {
                assertSame(selected.getAvatars().getAvatarById(entity.getAvatar().getAvatarId()), entity.getAvatar());
                assertTrue(entity.getId() != 0);
                assertNotNull(entity.getAvatar().getWeapon().getWeaponEntity());
            }
            server.registerPlayer(selected);
            int packets = session.sent.size();
            session.updateLastPingTime(1);
            selected.onTick();
            assertFalse(joined.onTick());
            assertEquals(packets, session.sent.size(), "finite ticks cannot start gameplay notification callbacks");
            assertTrue(session.sent.stream().noneMatch(p -> p.getOpcode() == PacketOpcodes.AvatarDataNotify
                    || p.getOpcode() == PacketOpcodes.PlayerStoreNotify));
            int beforeLogout = emu.grasscutter.utils.Utils.getCurrentSeconds();
            selected.onLogout();
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(30)));
            assertEquals(0, joined.getPlayerCount());
            assertFalse(server.getPlayers().containsKey(selected.getUid()));
            var fresh = store.find(Player.class).first();
            var freshSession = new CapturingSession(server);
            freshSession.setAccount(account);
            freshSession.setPlayer(fresh);
            fresh.loadFromDatabase();
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(30)));
            assertSnapshot(fresh, sourceAvatars, sourceItems);
            assertStoredFields(db.getCollection("items"), sourceItems, Set.of("saveStamp"));
            var allStoredItems = db.getCollection("items").find().into(new ArrayList<>()).stream()
                    .collect(java.util.stream.Collectors.toMap(d -> d.getObjectId("_id"), d -> d));
            int inactive = 0;
            for (var original : sourceItems) {
                if (Boolean.TRUE.equals(original.getBoolean("deleted")) || original.getInteger("count") <= 0) {
                    inactive++;
                    assertEquals(original, allStoredItems.get(original.getObjectId("_id")));
                }
            }
            assertEquals(1527, inactive);
            assertStoredFields(db.getCollection("avatars"), sourceAvatars, Set.of("currentHp", "currentEnergy", "currentSpecialEnergy"));
            var expectedPlayer = new Document(sourcePlayers.get(0));
            var expectedProfile = new Document(expectedPlayer.get("playerProfile", Document.class));
            int logoutTime = fresh.getProfile().getLastActiveTime();
            assertTrue(logoutTime >= beforeLogout && logoutTime <= emu.grasscutter.utils.Utils.getCurrentSeconds());
            expectedProfile.put("lastActiveTime", logoutTime);
            expectedPlayer.put("playerProfile", expectedProfile);
            assertStoredFields(db.getCollection("players"), List.of(expectedPlayer), Set.of("saveStamp"));
            assertEquals(12254, db.getCollection("items").countDocuments());
            assertFalse(ResourceLoader.isLoadedAll());
            System.out.println("OLD_LIFECYCLE_COMPLETE admitted=true avatars=121 activeItems=10727 equipment=471 world=true tick=true logout=true coldRestore=true networkEntry=false database=" + database);
        } finally {
            DatabaseHelper.awaitIdle(Duration.ofSeconds(30));
            field(DatabaseManager.class, "gameDatastore").set(null, previousStore);
            config.folderStructure.resources = previousResources;
            config.folderStructure.data = previousData;
            old.enabled = previousMode; old.manifestSha256 = previousPin; old.inputRoot = previousInputRoot;
            Configuration.GAME_INFO.enableScriptInBigWorld = previousScripts;
            Grasscutter.setRunModeOverride(previousRunMode);
        }
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) throws Exception {
        var result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static final class CapturingSession extends GameSession {
        final List<BasePacket> sent = new ArrayList<>();
        CapturingSession(GameServer server) { super(server); }
        @Override public void send(BasePacket packet) { sent.add(packet); }
    }
}
