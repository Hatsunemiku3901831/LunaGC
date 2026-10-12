package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MotionInfoOuterClass.MotionInfo;
import emu.grasscutter.net.proto.SceneEntityAppearNotifyOuterClass.SceneEntityAppearNotify;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import javax.script.SimpleBindings;

/**
 * Actual Lua bridge, scene membership and appearance packets with controlled avatar serialization.
 */
class LuaSceneRelocationTest {
    private AbilityTestFixture environment;
    private Room scene;
    private Player player;
    private EntityAvatar avatar;
    private ScriptLib library;
    private SimpleBindings bindings;
    private SceneGroup group;

    static class Room extends Scene {
        int joins, leaves;
        final List<BasePacket> packets = new ArrayList<>();

        Room(World world) {
            super(
                    world,
                    new Gson().fromJson("{id:9,type:'SCENE_DUNGEON'}", SceneData.class),
                    false);
        }

        @Override
        public void addPlayer(Player player) {
            joins++;
            super.addPlayer(player);
        }

        @Override
        public void removePlayer(Player player) {
            leaves++;
            super.removePlayer(player);
        }

        @Override
        public void broadcastPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    @BeforeAll
    static void lua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = environment.player;
        player.setUid(10001);
        scene = new Room(environment.scene.getWorld());
        scene.getWorld().getScenes().put(scene.getId(), scene);
        player.setWorld(scene.getWorld());
        player.setScene(scene);
        player.setSceneId(scene.getId());
        player.getTeamManager().setEntity(new EntityTeam(player));
        scene.getPlayers().add(player);
        player.getPosition().set(1, 2, 3);
        player.getRotation().set(4, 5, 6);
        avatar = makeAvatar(player);
        player.getTeamManager().getActiveTeam().add(avatar);
        scene.getEntities().put(avatar.getId(), avatar);
        group = SceneGroup.of(220025001);
        group.setLoaded(true);
        group.monsters = Map.of();
        group.gadgets = Map.of();
        group.regions = Map.of();
        group.triggers = Map.of();
        var variable = new SceneVar();
        variable.name = "checkpoint";
        variable.value = 7;
        group.variables = List.of(variable);
        scene.getScriptManager().registerLocalGroups(List.of(group));
        scene.getScriptManager().loadGroupFromScript(group);
        library = ScriptLoader.getScriptLib();
        library.setSceneScriptManager(scene.getScriptManager());
        library.setCurrentGroup(group);
        bindings = new SimpleBindings();
        bindings.put("ScriptLib", ScriptLoader.getScriptLibLua());
        bindings.put("context", library.createEventContext(new ScriptArgs(group.id, 1), group));
    }

    private EntityAvatar makeAvatar(Player owner) {
        var data =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return owner;
                    }

                    @Override
                    public void save() {}
                };
        return new EntityAvatar(scene, data) {
            @Override
            public void initAbilities() {}

            @Override
            public SceneEntityInfo toProto() {
                return SceneEntityInfo.newBuilder()
                        .setEntityId(getId())
                        .setMotionInfo(
                                MotionInfo.newBuilder()
                                        .setPos(getPosition().toProto())
                                        .setRot(getRotation().toProto()))
                        .build();
            }
        };
    }

    @AfterEach
    void close() {
        library.removeCurrentGroup();
        library.removeSceneScriptManager();
        scene.getScriptManager().onDestroy();
        environment.scene.getScriptManager().onDestroy();
        environment.close();
    }

    private int move(String options) throws Exception {
        return ((Number)
                        ScriptLoader.getEngine()
                                .eval(
                                        "return ScriptLib.TransPlayerToPos(context,"
                                                + options
                                                + ")",
                                        bindings))
                .intValue();
    }

    private void kept() {
        assertSame(scene, player.getScene());
        assertSame(scene, scene.getWorld().getScenes().get(scene.getId()));
        assertFalse(scene.getScriptManager().isDestroyed());
        assertEquals(0, scene.joins);
        assertEquals(0, scene.leaves);
        assertSame(avatar, scene.getEntityById(avatar.getId()));
        assertSame(group, scene.getScriptManager().getLoadedGroupById(group.id));
        assertEquals(7, scene.getScriptManager().getVariables(group.id).get("checkpoint"));
        assertTrue(
                environment.player.packets.stream()
                        .noneMatch(
                                p ->
                                        p.getOpcode() == PacketOpcodes.PlayerEnterSceneNotify
                                                || p.getOpcode()
                                                        == PacketOpcodes.EnterScenePeerNotify));
    }

    private void unchanged() {
        assertEquals(1f, player.getPosition().getX());
        assertEquals(2f, player.getPosition().getY());
        assertEquals(3f, player.getPosition().getZ());
        assertEquals(5f, player.getRotation().getY());
        assertTrue(scene.packets.isEmpty());
    }

    @Test
    void luaMovementKeepsTheSoloScenePartyAndScriptState() throws Exception {
        long version = avatar.getAbilityLifecycleVersion();
        assertEquals(
                0, move("{uid_list={10001},pos={x=13.25,y=1.5,z=81.75},rot={x=0,y=190.5,z=0}}"));
        kept();
        assertTrue(avatar.isAbilityLifecycleCurrent(version));
        assertEquals(13.25f, player.getPosition().getX());
        assertEquals(1.5f, player.getPosition().getY());
        assertEquals(81.75f, player.getPosition().getZ());
        assertEquals(190.5f, player.getRotation().getY());
        var appeared =
                scene.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.SceneEntityAppearNotify)
                        .findFirst()
                        .orElseThrow();
        var entity = SceneEntityAppearNotify.parseFrom(appeared.getData()).getEntityList(0);
        assertEquals(13.25f, entity.getMotionInfo().getPos().getX());
        assertEquals(190.5f, entity.getMotionInfo().getRot().getY());
    }

    @Test
    void omittedRotationKeepsThePlayersCurrentRotation() throws Exception {
        assertEquals(0, move("{uid_list={10001},pos={x=13,y=1,z=81}}"));
        kept();
        assertEquals(4f, player.getRotation().getX());
        assertEquals(5f, player.getRotation().getY());
        assertEquals(6f, player.getRotation().getZ());
    }

    @Test
    void aYawOnlyRotationUsesZeroForOmittedAxes() throws Exception {
        assertEquals(0, move("{uid_list={10001},pos={x=13,y=1,z=81},rot={y=190}}"));
        kept();
        assertEquals(0f, player.getRotation().getX());
        assertEquals(190f, player.getRotation().getY());
        assertEquals(0f, player.getRotation().getZ());
    }

    @Test
    void duplicateUidsMoveTheActorAndRefreshItsAppearanceOnce() throws Exception {
        assertEquals(0, move("{uid_list={10001,10001},pos={x=13,y=1,z=81}}"));
        kept();
        assertEquals(
                1,
                scene.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.SceneEntityAppearNotify)
                        .count());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{uid_list={},pos={x=1,y=2,z=3}}",
                "{uid_list={10001},pos=1}",
                "{uid_list={10001},pos={x=1,y=2}}",
                "{uid_list={10001},pos={x=math.huge,y=2,z=3}}",
                "{uid_list={10001},pos={x=1,y=0/0,z=3}}",
                "{uid_list={10001},pos={x=1,y=2,z=3},rot={y=math.huge}}",
                "{uid_list={10001},pos={x=1,y=2,z=3},rot=1}",
                "{uid_list={10001},pos={x=1,y=2,z=3},rot={y='bad'}}",
                "{uid_list={99999},pos={x=1,y=2,z=3}}",
                "{uid_list={10001,99999},pos={x=9,y=8,z=7}}",
                "{uid_list={10001,-1},pos={x=9,y=8,z=7}}"
            })
    void invalidRequestsCannotMoveOrReloadAnyActor(String request) throws Exception {
        assertNotEquals(0, move(request));
        kept();
        unchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"directory", "player", "member", "avatar"})
    void staleMembershipAndPartyEntriesCannotBeMoved(String change) throws Exception {
        switch (change) {
            case "directory" -> scene.getWorld().getScenes().remove(scene.getId());
            case "player" -> player.setScene(null);
            case "member" -> scene.getPlayers().clear();
            case "avatar" -> scene.getEntities().remove(avatar.getId());
            default -> fail(change);
        }
        assertNotEquals(0, move("{uid_list={10001},pos={x=9,y=8,z=7}}"));
        unchanged();
        assertEquals(0, scene.joins);
        assertEquals(0, scene.leaves);
    }

    @Test
    void theActualDvalinTimeAxisMovementKeepsTheCurrentScene() throws Exception {
        var source =
                java.nio.file.Files.readString(
                        java.nio.file.Path.of(
                                "resources/Scripts/Scene/20025/scene20025_group220025001.lua"));
        int function = source.indexOf("function action_EVENT_TIME_AXIS_PASS_1014");
        int begin = source.indexOf("local player = ScriptLib.GetSceneUidList", function);
        int end = source.indexOf("return 0", begin);
        assertTrue(function >= 0 && begin > function && end > begin);
        var result =
                ScriptLoader.getEngine().eval(source.substring(begin, end) + "return 0", bindings);
        assertEquals(0, ((Number) result).intValue());
        kept();
        assertEquals(13f, player.getPosition().getX());
        assertEquals(1f, player.getPosition().getY());
        assertEquals(81f, player.getPosition().getZ());
        assertEquals(190f, player.getRotation().getY());
    }

    private Player guest() {
        var guest = new AbilityTestFixture.TestPlayer();
        guest.setUid(10002);
        guest.setScene(scene);
        guest.setSceneId(scene.getId());
        guest.setWorld(scene.getWorld());
        guest.getTeamManager().setEntity(new EntityTeam(guest));
        guest.getPosition().set(0, 0, 0);
        scene.getPlayers().add(guest);
        var entity = makeAvatar(guest);
        guest.getTeamManager().getActiveTeam().add(entity);
        scene.getEntities().put(entity.getId(), entity);
        return guest;
    }

    @Test
    void selectedSceneMembersMoveTogetherAndKeepTheirPartyInstances() throws Exception {
        var guest = guest();
        var previous = guest.getTeamManager().getCurrentAvatarEntity();
        assertEquals(0, move("{uid_list={10001,10002},pos={x=13.25,y=1.5,z=81.75},rot={y=190.5}}"));
        kept();
        assertSame(previous, guest.getTeamManager().getCurrentAvatarEntity());
        assertEquals(13.25f, guest.getPosition().getX());
        assertEquals(190.5f, guest.getRotation().getY());
    }

    @Test
    void anInvalidSecondMemberDoesNotPartiallyMoveTheFirst() throws Exception {
        var guest = guest();
        scene.getEntities().remove(guest.getTeamManager().getCurrentAvatarEntity().getId());
        assertNotEquals(0, move("{uid_list={10001,10002},pos={x=9,y=8,z=7}}"));
        kept();
        unchanged();
        assertEquals(0f, guest.getPosition().getX());
    }

    @Test
    void inactivePartyMembersAreNotPublishedAsAdditionalVisibleAvatars() throws Exception {
        var inactive = makeAvatar(player);
        player.getTeamManager().getActiveTeam().add(inactive);
        assertEquals(0, move("{uid_list={10001},pos={x=13,y=1,z=81}}"));
        kept();
        assertFalse(scene.getEntities().containsKey(inactive.getId()));
        var appeared =
                scene.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.SceneEntityAppearNotify)
                        .findFirst()
                        .orElseThrow();
        assertEquals(1, SceneEntityAppearNotify.parseFrom(appeared.getData()).getEntityListCount());
        assertEquals(13f, inactive.getPosition().getX());
    }
}
