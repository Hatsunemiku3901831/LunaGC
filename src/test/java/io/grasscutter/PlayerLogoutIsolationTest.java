package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.Datastore;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;

import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.game.CoopRequest;
import emu.grasscutter.game.activity.crucible.CrucibleMatchSystem;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.chat.ChatSystem;
import emu.grasscutter.game.dungeons.DungeonSystem;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.friends.*;
import emu.grasscutter.game.home.EnterHomeRequest;
import emu.grasscutter.game.managers.SotSManager;
import emu.grasscutter.game.managers.stamina.StaminaManager;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameServer;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logout keeps independent cleanup and persistence attempts running after a synchronous failure.
 */
class PlayerLogoutIsolationTest extends QuestTestFixture {
    @Entity(value = "players", useDiscriminator = false)
    static class Member extends Player {
        @Transient final TeamManager team = new TeamManager(this);
        @Transient final List<String> calls = new ArrayList<>();
        @Transient final Set<String> failures = new HashSet<>();
        @Transient Datastore store;
        @Transient GameServer server;
        @Transient boolean armed, error;

        void hit(String stage) {
            if (!armed) return;
            calls.add(stage);
            if (failures.contains(stage)) {
                if (error) throw new AssertionError("injected " + stage);
                throw new IllegalStateException("injected " + stage);
            }
        }

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public void sendPacket(BasePacket packet) {}

        @Override
        public PlayerProfile getProfile() {
            hit("profile");
            return super.getProfile();
        }

        @Override
        public void save() {
            hit("player");
            store.save(this);
        }

        @Override
        public void removeFromServer() {
            hit("unregister");
            super.removeFromServer();
        }
    }

    private Member member;
    private World world;

    @BeforeEach
    void prepare() throws Exception {
        store.getMapper().map(Member.class);
        member = new Member();
        member.store = store;
        member.setUid(20119);
        member.setNickname("logout-latest");
        var server = new GameServer();
        member.server = server;
        replaceField(
                GameServer.class, server, "crucibleMatchSystem", new CrucibleMatchSystem(server));
        replaceField(GameServer.class, server, "dungeonSystem", new DungeonSystem(server));
        replaceField(GameServer.class, server, "players", new ConcurrentHashMap<Integer, Player>());
        replaceField(GameServer.class, server, "worlds", new HashSet<World>());
        server.getPlayers().put(member.getUid(), member);
        server.setChatSystem(
                new ChatSystem(server) {
                    @Override
                    public void clearHistoryOnLogout(Player player) {
                        member.hit("chat");
                        super.clearHistoryOnLogout(player);
                    }
                });
        replaceField(
                Player.class,
                member,
                "sotsManager",
                new SotSManager(member) {
                    @Override
                    public void stopRecovery() {
                        member.hit("statue");
                        super.stopRecovery();
                    }
                });
        replaceField(
                Player.class,
                member,
                "staminaManager",
                new StaminaManager(member) {
                    @Override
                    public void stopSustainedStaminaHandler() {
                        member.hit("stamina");
                        super.stopSustainedStaminaHandler();
                    }
                });
        replaceField(
                Player.class,
                member,
                "friendsList",
                new FriendsList(member) {
                    @Override
                    public void save() {
                        member.hit("friends");
                    }
                });
        world =
                new World(server, member) {
                    @Override
                    public void removePlayer(Player player) {
                        member.hit("world");
                        super.removePlayer(player);
                    }
                };
        var scene =
                new Scene(world, player.getScene().getSceneData()) {
                    // This suite isolates logout ordering. Scene callback internals have separate
                    // tests.
                    @Override
                    public void removePlayer(Player departing) {
                        getPlayers().remove(departing);
                        departing.setScene(null);
                    }

                    @Override
                    public void saveGroups() {}
                };
        scene.setDontDestroyWhenEmpty(true);
        world.registerScene(scene);
        world.getPlayers().add(member);
        scene.getPlayers().add(member);
        member.setWorld(world);
        member.setScene(scene);
        avatar(10000073, "avatar1", true);
        avatar(10000089, "avatar2", true);
        member.getCoopRequests().put(1, new CoopRequest(player));
        member.getEnterHomeRequests().put(1, new EnterHomeRequest(requester()));
        member.getEnterHomeRequests().put(2, new EnterHomeRequest(requester()));
        var requests = new ArrayList<>(member.getEnterHomeRequests().values());
        ((Requester) requests.get(0).getRequester()).stage = "home1";
        ((Requester) requests.get(1).getRequester()).stage = "home2";
        member.armed = true;
    }

    class Requester extends Player {
        String stage;

        @Override
        public void sendPacket(BasePacket packet) {
            member.hit(stage);
        }
    }

    private Requester requester() {
        return new Requester();
    }

    private Avatar avatar(int id, String stage, boolean owned) {
        var avatar =
                new Avatar() {
                    @Override
                    public int getAvatarId() {
                        return id;
                    }

                    @Override
                    public Player getPlayer() {
                        return member;
                    }

                    @Override
                    public AvatarData getAvatarData() {
                        return new AvatarData();
                    }

                    @Override
                    public void save() {
                        member.hit(stage);
                    }
                };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 35);
        if (owned) member.getAvatars().getAvatars().put(id, avatar);
        member.getTeamManager().getActiveTeam().add(new EntityAvatar(member.getScene(), avatar));
        return avatar;
    }

    private Document saved() {
        return store.getDatabase()
                .getCollection("players")
                .find(new Document("_id", member.getUid()))
                .first();
    }

    private void failAt(String stage) throws Exception {
        member.failures.add(stage);
        if (stage.equals("matching"))
            replaceField(GameServer.class, member.server, "crucibleMatchSystem", null);
        if (stage.equals("dungeon"))
            replaceField(GameServer.class, member.server, "dungeonSystem", null);
    }

    private void assertFinished() {
        assertTrue(member.calls.contains("player"), member.calls.toString());
        assertTrue(member.calls.contains("avatar1"), member.calls.toString());
        assertTrue(member.calls.contains("avatar2"), member.calls.toString());
        assertTrue(member.calls.contains("friends"), member.calls.toString());
        assertTrue(member.calls.contains("world"), member.calls.toString());
        assertTrue(member.getCoopRequests().isEmpty());
        assertTrue(member.getEnterHomeRequests().isEmpty());
        assertTrue(member.calls.contains("home2"), member.calls.toString());
        assertEquals("unregister", member.calls.get(member.calls.size() - 1));
        assertTrue(
                member.server.getPlayers().values().stream()
                        .noneMatch(current -> current == member));
        if (!member.failures.contains("world")) {
            assertFalse(world.getPlayers().contains(member));
            assertNull(member.getWorld());
        }
        if (member.failures.contains("player")) assertNull(saved());
        else {
            assertNotNull(saved());
            assertEquals("logout-latest", saved().getString("nickname"));
        }
    }

    @Test
    void healthy_logout_saves_before_unregistering() {
        assertDoesNotThrow(member::onLogout);
        assertFinished();
        assertTrue(member.calls.indexOf("player") < member.calls.indexOf("friends"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "statue",
                "matching",
                "chat",
                "stamina",
                "dungeon",
                "world",
                "profile",
                "home1",
                "player",
                "avatar1",
                "friends"
            })
    void one_failure_does_not_skip_other_cleanup_or_saves(String stage) throws Exception {
        failAt(stage);
        assertDoesNotThrow(member::onLogout);
        assertFinished();
    }

    @Test
    void multiple_failures_still_attempt_each_independent_save() throws Exception {
        for (var stage : Set.of("statue", "dungeon", "home1", "avatar1")) failAt(stage);
        assertDoesNotThrow(member::onLogout);
        assertFinished();
    }

    @Test
    void cleanup_errors_have_the_same_isolation_as_runtime_exceptions() {
        member.failures.add("chat");
        member.error = true;
        assertDoesNotThrow(member::onLogout);
        assertFinished();
    }

    @Test
    void inactive_owned_avatars_are_saved_and_temporary_scene_avatars_are_excluded() {
        member.getTeamManager()
                .getActiveTeam()
                .removeIf(e -> e.getAvatar().getAvatarId() == 10000089);
        avatar(10000005, "borrowed", false);
        assertDoesNotThrow(member::onLogout);
        assertFinished();
        assertFalse(member.calls.contains("borrowed"));
    }

    @Test
    void unregistering_an_old_session_preserves_a_replacement_with_the_same_uid() throws Exception {
        var replacement = new Player();
        replacement.setUid(member.getUid());
        member.server.getPlayers().put(replacement.getUid(), replacement);
        failAt("dungeon");
        assertDoesNotThrow(member::onLogout);
        assertFinished();
        assertSame(replacement, member.server.getPlayers().get(replacement.getUid()));
    }
}
