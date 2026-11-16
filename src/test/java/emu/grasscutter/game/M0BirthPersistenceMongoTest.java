package emu.grasscutter.game;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;
import dev.morphia.Morphia;
import dev.morphia.annotations.*;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.M0Resources;
import emu.grasscutter.database.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.net.proto.SetPlayerBornDataRspOuterClass.SetPlayerBornDataRsp;
import emu.grasscutter.net.proto.GetPlayerTokenReqOuterClass.GetPlayerTokenReq;
import emu.grasscutter.net.proto.GetPlayerTokenRspOuterClass.GetPlayerTokenRsp;
import emu.grasscutter.net.proto.Client72AvatarWireOuterClass.ILEPMBEJKIA;
import emu.grasscutter.net.proto.Client72M0SceneEntryOuterClass.NOCHHDCLKDJ;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.recv.HandlerSetPlayerBornDataReq;
import emu.grasscutter.server.packet.recv.HandlerGetPlayerTokenReq;
import emu.grasscutter.server.packet.recv.HandlerEnterSceneReadyReq;
import emu.grasscutter.server.packet.recv.HandlerSceneInitFinishReq;
import emu.grasscutter.net.proto.EnterSceneReadyReqOuterClass.EnterSceneReadyReq;
import emu.grasscutter.net.proto.SceneInitFinishReqOuterClass.SceneInitFinishReq;
import emu.grasscutter.server.packet.send.PacketAvatarDataNotify;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneInfoNotify;
import emu.grasscutter.server.packet.send.M0PeerIdentityProjection;
import emu.grasscutter.server.packet.send.M0AvatarIdentityProjection;
import emu.grasscutter.server.packet.send.PacketGetPlayerTokenRsp;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/** Real finite resources, World identities and persistence; no client acceptance is claimed. */
class M0BirthPersistenceMongoTest {
    @Test
    void femaleBirthIsConfirmedBeforeSuccessAndEquipmentRestoresIntoFreshPlayer() throws Exception {
        var config = Grasscutter.getConfig();
        String root = System.getenv("LUNAGC_M0_INSTANCE_ROOT");
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(root);
        assertEquals("mongodb://127.0.0.1:27027", uri);
        var previousResources = config.folderStructure.resources;
        var previousData = config.folderStructure.data;
        boolean previousMode = Configuration.GAME_INFO.m0.enabled;
        boolean previousQuesting = Configuration.GAME_OPTIONS.questing.enabled;
        boolean previousScripts = Configuration.GAME_INFO.enableScriptInBigWorld;
        var previousRunMode = Grasscutter.getRunMode();
        var previousStore = DatabaseManager.getGameDatastore();
        try (var client = MongoClients.create(uri)) {
            String database = "lunagc_m0_birth_" + UUID.randomUUID().toString().replace("-", "");
            var store = Morphia.createDatastore(client, database,
                    MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
            store.getMapper().map(Account.class, Player.class, BirthPlayer.class, Avatar.class, GameItem.class);
            field(DatabaseManager.class, "gameDatastore").set(null, store);
            config.folderStructure.resources = Path.of(root).resolve("resources").toAbsolutePath().toString();
            config.folderStructure.data = Path.of(root).resolve("test-data").toAbsolutePath().toString();
            Configuration.GAME_INFO.m0.enabled = true;
            Configuration.GAME_OPTIONS.questing.enabled = true;
            M0Resources.loadBirthResources();
            Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.HYBRID);
            var server = FiniteGameServerTestSupport.unbound();
            store.getDatabase().getCollection("accounts").insertOne(new Document("_id", "m0-birth")
                    .append("username", "m0-birth").append("token", "m0-test-token").append("permissions", List.of()));
            var session = new CapturingSession(server);
            assertTrue(AccountLoginAdmission.begin(session, "m0-birth", "m0-test-token", account -> {}));
            var player = new BirthPlayer();
            player.setUid(74472);
            player.setM0Profile(true);
            field(Player.class, "accountId").set(player, "m0-birth");
            field(Player.class, "teamManager").set(player, new TeamManager(player));
            session.setPlayer(player);
            session.setState(GameSession.SessionState.PICKING_CHARACTER);
            DatabaseHelper.saveGameSync(player);
            var handler = new HandlerSetPlayerBornDataReq();
            handler.handle(session, new byte[0], SetPlayerBornDataReq.newBuilder()
                    .setAvatarId(10000005).setNickName("Unsupported").build().toByteArray());
            assertEquals(-1, SetPlayerBornDataRsp.parseFrom(session.sent.get(0).getData()).getRetcode());
            assertEquals(0, store.getDatabase().getCollection("avatars").countDocuments());
            assertEquals(0, store.getDatabase().getCollection("items").countDocuments());
            session.sent.clear();
            handler.handle(session, new byte[0], SetPlayerBornDataReq.newBuilder()
                    .setAvatarId(10000007).setNickName("M0新生").build().toByteArray());
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
            assertTrue(player.confirmed);
            assertEquals(0, SetPlayerBornDataRsp.parseFrom(session.sent.get(session.sent.size() - 1).getData()).getRetcode());
            var avatar = player.getAvatars().getAvatarById(10000007);
            var weapon = avatar.getWeaponNotNull();
            assertEquals(11101, weapon.getItemId());
            assertEquals(701, avatar.getSkillDepotId());
            assertTrue(avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK) > 17.808f);
            var wire = ILEPMBEJKIA.parseFrom(new PacketAvatarDataNotify(player).getData());
            assertEquals(avatar.getGuid(), wire.getJGECGMPNGBD(0).getFBLKPHOCBIK());
            assertEquals(weapon.getGuid(), wire.getJGECGMPNGBD(0).getAODNDDBPMFB(0));
            var weaponObjectId = weapon.getObjectId();
            var avatarObjectId = avatar.getObjectId();
            AccountLoginAdmission.closed(session);
            var fresh = store.find(BirthPlayer.class).first();
            var freshSession = new CapturingSession(server);
            assertTrue(AccountLoginAdmission.begin(freshSession, "m0-birth", "m0-test-token", account -> {}));
            freshSession.setPlayer(fresh);
            fresh.getAvatars().loadFromDatabase();
            fresh.getInventory().loadFromDatabase();
            var restored = fresh.getAvatars().getAvatarById(10000007);
            assertNotSame(avatar, restored);
            assertEquals(avatarObjectId, restored.getObjectId());
            assertEquals(weaponObjectId, restored.getWeaponNotNull().getObjectId());
            assertSame(restored.getWeaponNotNull(), fresh.getInventory().getItemByGuid(restored.getWeaponNotNull().getGuid()));
            assertEquals("M0新生", fresh.getNickname());
            assertTrue(fresh.isM0Profile());
            assertEquals(701, restored.getSkillDepotId());
            assertEquals(avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP), restored.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            assertEquals(1, store.getDatabase().getCollection("avatars").countDocuments());
            assertEquals(1, store.getDatabase().getCollection("items").countDocuments());
            M0Resources.loadSceneIdentityResources();
            Configuration.GAME_INFO.enableScriptInBigWorld = false;
            fresh.useNormalLogin = true;
            fresh.onLogin();
            var currentEntity = fresh.getTeamManager().getCurrentAvatarEntity();
            assertTrue(currentEntity.getId() != 0);
            assertTrue(restored.getWeaponNotNull().getWeaponEntity().getId() != 0);
            var scenePacket = new PacketPlayerEnterSceneInfoNotify(fresh);
            assertEquals(21761, scenePacket.getOpcode());
            var entry = NOCHHDCLKDJ.parseFrom(scenePacket.getData());
            assertEquals(currentEntity.getId(), entry.getOLCJCLBMMCF());
            assertEquals(fresh.getWorld().getLevelEntityId(), entry.getLFLOLJADPBI().getLLHEBIHGDAE());
            assertEquals(fresh.getTeamManager().getEntity().getId(), entry.getAEKMONHBHAA().getJNBCPNHJCNI());
            assertNotEquals(0, entry.getLFLOLJADPBI().getLLHEBIHGDAE());
            assertNotEquals(0, entry.getAEKMONHBHAA().getJNBCPNHJCNI());
            assertNotEquals(entry.getLFLOLJADPBI().getLLHEBIHGDAE(), entry.getAEKMONHBHAA().getJNBCPNHJCNI());
            assertTrue(entry.getAEKMONHBHAA().hasEOFFDMLNFIM());
            assertEquals(0, entry.getAEKMONHBHAA().getEOFFDMLNFIM().getGFDEDOJEICECount());
            assertEquals(0, entry.getAEKMONHBHAA().getKNFDPJCMNOECount());
            var peerIdentity = M0PeerIdentityProjection.project(fresh);
            assertEquals(fresh.getEnterSceneToken(), peerIdentity.getMCCMGLDOECH());
            assertEquals(fresh.getPeerId(), peerIdentity.getAANCEPCPHKJ());
            assertTrue(fresh.getPeerId() > 0);
            assertSame(fresh, fresh.getWorld().getHost(), "M0 uses its real host world");
            assertEquals(peerIdentity.getAANCEPCPHKJ(), entry.getLFLOLJADPBI().getHKLILOKPKBJ());
            assertTrue(peerIdentity.hasOHNANNOOMCJ());
            assertEquals(fresh.getWorld().getHostPeerId(), peerIdentity.getOHNANNOOMCJ());
            assertEquals(peerIdentity.getAANCEPCPHKJ(), peerIdentity.getOHNANNOOMCJ(), "the real M0 host is this local player");
            assertFalse(peerIdentity.hasOHAKDDILKKB());
            var identities = M0AvatarIdentityProjection.projectCurrentPlayer(fresh);
            int tokenUid = GetPlayerTokenRsp.parseFrom(new PacketGetPlayerTokenRsp(freshSession).getData()).getUid();
            assertEquals(tokenUid, identities.getHCIEHNNJNCF());
            assertEquals(tokenUid, identities.getMPHLKPODDHK().getRawAvatar10().getPJAJDHBBMGB());
            assertEquals(fresh.getUid(), tokenUid);
            assertNotEquals(fresh.getUid(), peerIdentity.getAANCEPCPHKJ());
            assertEquals(restored.getGuid(), identities.getOKMEHFHNFCG());
            assertEquals(restored.getGuid(), identities.getMPHLKPODDHK().getRawAvatar10().getFBLKPHOCBIK());
            assertEquals(currentEntity.getId(), identities.getLLHEBIHGDAE());
            assertEquals(restored.getWeaponNotNull().getGuid(), identities.getNPKKCHABEJH());
            assertEquals(currentEntity.getWeaponEntityId(), identities.getIEDJNHILJBF());
            assertEquals(fresh.getSceneId(), identities.getJMHKHAPJADA());
            assertFalse(identities.getMPHLKPODDHK().hasLLHEBIHGDAE());
            assertTrue(fresh.getWorld().getEntity().getInstancedAbilities().isEmpty());
            assertTrue(fresh.getTeamManager().getEntity().getInstancedAbilities().isEmpty());
            var baseAbilities = emu.grasscutter.data.GameData.getAbilityEmbryoInfo()
                    .get(restored.getAvatarData().getName()).getAbilities();
            assertEquals(5, baseAbilities.length);
            for (var ability : baseAbilities)
                assertTrue(restored.getAvatarData().getAbilities().contains(emu.grasscutter.utils.Utils.abilityHash(ability)));
            var levelAbilities = emu.grasscutter.data.GameData.getConfigLevelEntityDataMap().get("Level_BigWorld").getAvatarAbilities();
            assertEquals(baseAbilities.length + levelAbilities.size(), restored.getAvatarData().getAbilities().size());
            assertEquals(fresh.getEnterSceneToken(), entry.getMCCMGLDOECH());
            assertEquals(1, entry.getDIGAMAKGCPACount());
            var entered = entry.getDIGAMAKGCPA(0);
            assertEquals(restored.getGuid(), entered.getOKMEHFHNFCG());
            assertEquals(currentEntity.getId(), entered.getFDDDFIAINCE());
            assertEquals(restored.getWeaponNotNull().getGuid(), entered.getNPKKCHABEJH());
            assertEquals(restored.getWeaponNotNull().getWeaponEntity().getId(), entered.getIEDJNHILJBF());
            assertEquals(GameSession.SessionState.ACTIVE, freshSession.getState());
            assertNull(fresh.getActivityManager());
            assertNull(fresh.getDailyTaskManager());
            int packets = freshSession.sent.size();
            server.onTick();
            assertEquals(1, server.tickStatus().completed());
            assertEquals(packets, freshSession.sent.size(), "M0 ticks must not enter unreviewed gameplay senders");
            new HandlerEnterSceneReadyReq().handle(freshSession, new byte[0], EnterSceneReadyReq.newBuilder()
                    .setMCCMGLDOECH((fresh.getEnterSceneToken() ^ 0xE8C3) + 0x4C01).build().toByteArray());
            assertEquals(packets + 1, freshSession.sent.size());
            assertEquals(PacketOpcodes.EnterSceneReadyRsp, freshSession.sent.get(packets).getOpcode());
            new HandlerSceneInitFinishReq().handle(freshSession, new byte[0], SceneInitFinishReq.newBuilder()
                    .setMCCMGLDOECH((fresh.getEnterSceneToken() ^ 0x5673) + 0xF8CD).build().toByteArray());
            assertTrue(freshSession.isLoginRevoked());
            assertEquals(packets + 1, freshSession.sent.size(), "unknown initialization cannot emit success or legacy notifications");
            assertEquals(Player.SceneLoadState.LOADING, fresh.getSceneLoadState());
            var joined = fresh.getWorld();
            float savedX = fresh.getPosition().getX() + 0.125f;
            fresh.getPosition().setX(savedX);
            restored.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 123.5f);
            freshSession.handleClose();
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
            assertEquals(GameSession.SessionState.INACTIVE, freshSession.getState());
            assertEquals(0, joined.getPlayerCount());
            assertFalse(server.getPlayers().containsKey(fresh.getUid()));
            var afterLogout = store.find(BirthPlayer.class).first();
            var afterLogoutSession = new CapturingSession(server);
            assertTrue(AccountLoginAdmission.begin(afterLogoutSession, "m0-birth", "m0-test-token", account -> {}));
            afterLogoutSession.setPlayer(afterLogout);
            afterLogout.loadFromDatabase();
            var loggedOutAvatar = afterLogout.getAvatars().getAvatarById(10000007);
            assertNotSame(restored, loggedOutAvatar);
            assertEquals(avatarObjectId, loggedOutAvatar.getObjectId());
            assertEquals(weaponObjectId, loggedOutAvatar.getWeaponNotNull().getObjectId());
            assertEquals(123.5f, loggedOutAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            assertEquals(savedX, afterLogout.getPosition().getX());
            assertEquals("M0新生", afterLogout.getNickname());
            assertTrue(afterLogout.isM0Profile());
            assertTrue(afterLogout.getAvatars().isLoaded());
            assertTrue(afterLogout.getInventory().isLoaded());
            assertEquals(1, store.getDatabase().getCollection("avatars").countDocuments());
            assertEquals(1, store.getDatabase().getCollection("items").countDocuments());
            AccountLoginAdmission.closed(afterLogoutSession);
            store.getDatabase().getCollection("accounts").insertOne(new Document("_id", "old-profile")
                    .append("username", "old-profile").append("token", "old-test-token").append("permissions", List.of()));
            var old = new BirthPlayer();
            old.setUid(74473);
            field(Player.class, "accountId").set(old, "old-profile");
            field(Player.class, "teamManager").set(old, new TeamManager(old));
            DatabaseHelper.saveGameSync(old);
            var oldBefore = store.getDatabase().getCollection("players").find(new Document("_id", 74473)).first();
            var oldSession = new CapturingSession(server);
            new HandlerGetPlayerTokenReq().handle(oldSession, new byte[0], GetPlayerTokenReq.newBuilder()
                    .setAccountUid("old-profile").setAccountToken("old-test-token").setKeyId(1).build().toByteArray());
            assertTrue(oldSession.isLoginRevoked());
            assertEquals(-1, GetPlayerTokenRsp.parseFrom(oldSession.sent.get(0).getData()).getRetcode());
            assertFalse(oldSession.getPlayer().getAvatars().isLoaded());
            assertFalse(oldSession.getPlayer().getInventory().isLoaded());
            assertEquals(oldBefore, store.getDatabase().getCollection("players").find(new Document("_id", 74473)).first());
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(10)));
            System.out.println("M0_BIRTH_PERSISTENCE database=" + database + " avatar=10000007 depot=701 weapon=11101 coldRestore=true");
        } finally {
            field(DatabaseManager.class, "gameDatastore").set(null, previousStore);
            config.folderStructure.resources = previousResources;
            config.folderStructure.data = previousData;
            Configuration.GAME_INFO.m0.enabled = previousMode;
            Configuration.GAME_OPTIONS.questing.enabled = previousQuesting;
            Configuration.GAME_INFO.enableScriptInBigWorld = previousScripts;
            Grasscutter.setRunModeOverride(previousRunMode);
        }
    }

    @Entity(value = "players", useDiscriminator = false)
    public static class BirthPlayer extends Player {
        @Transient boolean confirmed;
        @Transient boolean useNormalLogin;
        @Override public void onLogin() {
            if (useNormalLogin) { super.onLogin(); return; }
            var db = DatabaseManager.getGameDatastore().getDatabase();
            assertEquals(10000007, db.getCollection("players").find(new Document("_id", getUid())).first().getInteger("mainCharacterId"));
            assertEquals(1, db.getCollection("avatars").countDocuments());
            assertEquals(1, db.getCollection("items").countDocuments());
            confirmed = true;
        }
    }

    private static final class CapturingSession extends GameSession {
        final List<BasePacket> sent = new ArrayList<>();
        CapturingSession(GameServer server) { super(server); }
        @Override public void send(BasePacket packet) { if (!isLoginRevoked()) sent.add(packet); }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
