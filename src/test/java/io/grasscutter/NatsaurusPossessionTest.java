package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;

/** Exercises real scene membership, ability callbacks and vehicle packets, without a client. */
class NatsaurusPossessionTest {
    AbilityTestFixture fixture;
    EntityAvatar avatar;
    Ability ability;
    final List<Runnable> restores = new ArrayList<>();
    static final int DRILL = 45003011, HOOK = 45003021, BOAT = 45001001;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setUid(10001);
        fixture.player.setWorld(fixture.scene.getWorld());
        fixture.scene.getWorld().registerScene(fixture.scene);
        fixture.scene.getWorld().getPlayers().add(fixture.player);
        fixture.scene.getPlayers().add(fixture.player);
        fixture.player.getTeamManager().setEntity(new EntityTeam(fixture.player));
        var pluginField = emu.grasscutter.Grasscutter.class.getDeclaredField("pluginManager");
        pluginField.setAccessible(true);
        var oldPlugins = pluginField.get(null);
        pluginField.set(null, new emu.grasscutter.plugin.PluginManager());
        restores.add(() -> assertDoesNotThrow(() -> pluginField.set(null, oldPlugins)));
        var session =
                new GameSession(null) {
                    @Override
                    public void send(BasePacket packet) {
                        fixture.player.packets.add(packet);
                    }
                };
        session.setPlayer(fixture.player);
        fixture.player.setSession(session);
        avatar =
                new EntityAvatar(
                        fixture.scene,
                        new Avatar() {
                            @Override
                            public emu.grasscutter.game.player.Player getPlayer() {
                                return fixture.player;
                            }

                            @Override
                            public void save() {}
                        }) {
                    @Override
                    public void initAbilities() {}

                    @Override
                    public SceneEntityInfoOuterClass.SceneEntityInfo toProto() {
                        return SceneEntityInfoOuterClass.SceneEntityInfo.getDefaultInstance();
                    }
                };
        fixture.player.getTeamManager().getActiveTeam().add(avatar);
        fixture.scene.getEntities().put(avatar.getId(), avatar);
        for (var row :
                List.of(
                        "{id:45003011,jsonName:'Natsaurus_Drillhead_Vehicle_01',type:'Vehicle'}",
                        "{id:45003021,jsonName:'Natsaurus_Hookwalker_Vehicle_01',type:'Vehicle'}",
                        "{id:45001001,jsonName:'Skiff_Normal_01',type:'Vehicle'}")) {
            var data = JsonUtils.decode(row, GadgetData.class);
            put(GameData.getGadgetDataMap(), data.getId(), data);
            put(
                    GameData.getGadgetConfigData(),
                    data.getJsonName(),
                    JsonUtils.decode(
                            "{abilities:[],combat:{property:{HP:10000,isInvincible:true},die:{hasAnimatorDie:true,dieEndTime:1.5}}}",
                            ConfigEntityGadget.class));
        }
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'PossessionTest',modifiers:{}}", AbilityData.class),
                        avatar,
                        fixture.player);
        fixture.player.packets.clear();
        fixture.scene.packets.clear();
    }

    <K, V> void put(Map<K, V> map, K key, V value) {
        V previous = map.put(key, value);
        restores.add(
                () -> {
                    if (previous == null) map.remove(key);
                    else map.put(key, previous);
                });
    }

    @AfterEach
    void close() {
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
        fixture.close();
    }

    Class<?> helper() {
        return assertDoesNotThrow(
                () -> Class.forName("emu.grasscutter.game.ability.NatsaurusVehicleHelper"));
    }

    Object call(String name, Class<?>[] parameters, Object... args) {
        return assertDoesNotThrow(() -> helper().getMethod(name, parameters).invoke(null, args));
    }

    EntityVehicle board(GameEntity source) {
        return (EntityVehicle)
                call(
                        "board",
                        new Class<?>[] {emu.grasscutter.game.player.Player.class, GameEntity.class},
                        fixture.player,
                        source);
    }

    void end(String method) {
        call(method, new Class<?>[] {emu.grasscutter.game.player.Player.class}, fixture.player);
    }

    EntityVehicle candle(int gadget) {
        var entity =
                new EntityVehicle(
                        fixture.scene,
                        fixture.player,
                        gadget,
                        0,
                        new Position(1, 2, 3),
                        new Position(0, 90, 0));
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    AbilityMixinData mixin(String type) {
        var parsed =
                JsonUtils.decode(
                        "{'$type':'" + type + "',vehicleType:'Natsaurus'}", AbilityMixinData.class);
        assertNotNull(parsed.type, type + " must be decoded");
        return parsed;
    }

    void execute(String type, ByteString payload) {
        fixture.player.getAbilityManager().executeMixin(ability, mixin(type), payload, avatar);
    }

    List<EntityVehicle> mounts() {
        return fixture.scene.getEntities().values().stream()
                .filter(e -> e instanceof EntityVehicle)
                .map(e -> (EntityVehicle) e)
                .filter(e -> !e.getVehicleMembers().isEmpty())
                .toList();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "CheckSubTagScanEntityMixin",
                "TryEnterVehicleMixin",
                "DoActionOnVehicleInteractPostMixin",
                "TriggerVehicleOff"
            })
    void vehicleMixinTypesAreParsed(String type) {
        mixin(type);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Monster_Natsaurus_Drillhead_Normal_01",
                "Monster_Natsaurus_Drillhead_Small_01",
                "Natsaurus_Drillhead_Normal_TheAbyss_01"
            })
    void tribeResolvesToResourceVehicle(String name) {
        assertEquals(DRILL, call("vehicleGadgetId", new Class<?>[] {String.class}, name));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Monster_Hilichurl",
                "Natsaurus_",
                "Fake_Natsaurus_Drillhead",
                "Monster_Natsaurus_Unknown_Normal_01"
            })
    void unrelatedMonsterCannotBePossessed(String name) {
        assertEquals(0, call("vehicleGadgetId", new Class<?>[] {String.class}, name));
    }

    @Test
    void parkedBoatCannotBePossessed() {
        var boat = candle(BOAT);
        assertNull(board(boat));
        assertSame(boat, fixture.scene.getEntityById(boat.getId()));
    }

    @Test
    void deadCandleCannotBePossessed() {
        var source = candle(DRILL);
        source.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        assertNull(board(source));
    }

    @Test
    void unregisteredSourceCannotBePossessed() {
        var source = candle(DRILL);
        fixture.scene.getEntities().remove(source.getId());
        assertNull(board(source));
    }

    @Test
    void crossSceneSourceCannotBePossessed() {
        var other =
                new AbilityTestFixture.TestScene(
                        (AbilityTestFixture.TestWorld) fixture.scene.getWorld(),
                        fixture.scene.getSceneData());
        var source =
                new EntityVehicle(other, fixture.player, DRILL, 0, new Position(), new Position());
        fixture.scene.getEntities().put(source.getId(), source);
        assertNull(board(source));
    }

    @Test
    void occupiedMountCannotBeStolen() {
        var source = candle(DRILL);
        source.getVehicleMembers()
                .add(VehicleMemberOuterClass.VehicleMember.newBuilder().setUid(20001).build());
        assertNull(board(source));
    }

    @Test
    void boardingUsesSelectedCandlePositionAndConsumesItOnce() {
        var source = candle(DRILL);
        var mount = board(source);
        assertNotNull(mount);
        assertNull(fixture.scene.getEntityById(source.getId()));
        assertEquals(1, mounts().size());
        assertEquals(source.getPosition().toProto(), mount.getPosition().toProto());
        assertEquals(source.getRotation().toProto(), mount.getRotation().toProto());
        assertNotSame(source.getPosition(), mount.getPosition());
        assertEquals(10001, mount.getVehicleMembers().get(0).getUid());
        assertEquals(10000, mount.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertNull(board(source));
        assertEquals(1, mounts().size());
    }

    @Test
    void ordinaryExitKeepsReusableCandleAndClearsRider() {
        var mount = board(candle(DRILL));
        assertNotNull(mount);
        end("eject");
        assertTrue(mount.getVehicleMembers().isEmpty());
        assertSame(mount, fixture.scene.getEntityById(mount.getId()));
        end("eject");
        assertNotNull(board(mount));
        assertEquals(1, mounts().size());
    }

    @Test
    void changingDragonLeavesPreviousCandleWithoutKillingIt() {
        var previous = board(candle(DRILL));
        assertNotNull(previous);
        var next = board(candle(HOOK));
        assertNotNull(next);
        assertTrue(previous.getVehicleMembers().isEmpty());
        assertTrue(previous.isAlive());
        assertSame(previous, fixture.scene.getEntityById(previous.getId()));
        assertEquals(1, mounts().size());
    }

    @Test
    void transferCleanupRemovesMountAndKeepsBoat() {
        var boat = candle(BOAT);
        var mount = board(candle(DRILL));
        assertNotNull(mount);
        end("takeAway");
        assertTrue(mount.getVehicleMembers().isEmpty());
        assertNull(fixture.scene.getEntityById(mount.getId()));
        assertSame(boat, fixture.scene.getEntityById(boat.getId()));
        end("takeAway");
    }

    @Test
    void validCommandTeleportCleansMountAtOldPosition() {
        var mount = board(candle(DRILL));
        assertNotNull(mount);
        put(GameData.getSceneDataMap(), 3, fixture.scene.getSceneData());
        assertTrue(
                fixture.scene
                        .getWorld()
                        .transferPlayerToScene(
                                fixture.player, 3, TeleportType.COMMAND, new Position(20, 0, 0)));
        assertNull(fixture.scene.getEntityById(mount.getId()));
        assertTrue(mount.getVehicleMembers().isEmpty());
    }

    @Test
    void invalidTeleportLeavesPossessionIntact() {
        var mount = board(candle(DRILL));
        assertNotNull(mount);
        assertFalse(
                fixture.scene
                        .getWorld()
                        .transferPlayerToScene(
                                fixture.player, 999991, TeleportType.COMMAND, new Position()));
        assertSame(mount, fixture.scene.getEntityById(mount.getId()));
        assertEquals(1, mount.getVehicleMembers().size());
    }

    @Test
    void canceledTeleportLeavesPossessionIntact() {
        var mount = board(candle(DRILL));
        assertNotNull(mount);
        put(GameData.getSceneDataMap(), 3, fixture.scene.getSceneData());
        new emu.grasscutter.server.event.EventHandler<>(
                        emu.grasscutter.server.event.player.PlayerTeleportEvent.class)
                .listener(emu.grasscutter.server.event.player.PlayerTeleportEvent::cancel)
                .register(null);
        assertFalse(
                fixture.scene
                        .getWorld()
                        .transferPlayerToScene(
                                fixture.player, 3, TeleportType.COMMAND, new Position()));
        assertSame(mount, fixture.scene.getEntityById(mount.getId()));
        assertEquals(1, mount.getVehicleMembers().size());
    }

    @Test
    void modifierAttachmentBoardsWithoutClientMixinInvokeAndDuplicateDoesNotRepeat()
            throws Exception {
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'Possession_Attach',modifiers:{Perform:{modifierMixins:[{$type:'TryEnterVehicleMixin',vehicleType:'Natsaurus'},{$type:'DoActionOnVehicleInteractPostMixin',onVehicleIn:[{$type:'AddGlobalValue',key:'post',value:1}]}]}}}",
                                AbilityData.class),
                        avatar,
                        fixture.player);
        avatar.getInstancedAbilities().add(ability);
        execute("CheckSubTagScanEntityMixin", packed(candle(DRILL).getId()));
        var entry =
                AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                        .setEntityId(avatar.getId())
                        .setArgumentType(
                                AbilityInvokeArgumentOuterClass.AbilityInvokeArgument
                                        .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                        .setHead(
                                AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead.newBuilder()
                                        .setInstancedAbilityId(1)
                                        .setInstancedModifierId(321))
                        .setAbilityData(
                                AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange
                                        .newBuilder()
                                        .setAction(
                                                ModifierActionOuterClass.ModifierAction
                                                        .MODIFIER_ACTION_ADDED)
                                        .setModifierLocalId(0)
                                        .build()
                                        .toByteString())
                        .build();
        fixture.player.getAbilityManager().onAbilityInvoke(entry);
        assertEquals(1, mounts().size());
        assertEquals(1f, avatar.getGlobalAbilityValues().get("post"));
        fixture.player.getAbilityManager().onAbilityInvoke(entry);
        assertEquals(1, mounts().size());
        assertEquals(1f, avatar.getGlobalAbilityValues().get("post"));
    }

    @Test
    void explicitResourceExitActionEndsPossession() {
        var mount = board(candle(DRILL));
        assertNotNull(mount);
        var action =
                JsonUtils.decode(
                        "{$type:'TriggerVehicleOff',target:'Self'}",
                        AbilityModifier.AbilityModifierAction.class);
        assertNotNull(action.type);
        fixture.player
                .getAbilityManager()
                .executeActionNow(ability, action, ByteString.EMPTY, avatar);
        assertTrue(mount.getVehicleMembers().isEmpty());
        assertSame(mount, fixture.scene.getEntityById(mount.getId()));
    }

    @Test
    void liveSceneSnapshotIncludesRiderAndExitKeepsOtherPlayersMount() {
        var mount = board(candle(DRILL));
        assertNotNull(mount);
        var info = mount.toProto().getGadget().getVehicleInfo();
        assertEquals(1, info.getMemberListCount());
        assertEquals(10001, info.getMemberList(0).getUid());
        var other = new AbilityTestFixture.TestPlayer();
        other.setUid(20001);
        var foreign =
                new EntityVehicle(fixture.scene, other, HOOK, 0, new Position(), new Position());
        fixture.scene.getEntities().put(foreign.getId(), foreign);
        foreign.getVehicleMembers()
                .add(VehicleMemberOuterClass.VehicleMember.newBuilder().setUid(20001).build());
        end("takeAway");
        assertSame(foreign, fixture.scene.getEntityById(foreign.getId()));
        assertEquals(1, foreign.getVehicleMembers().size());
    }

    @Test
    void lateExitFromOldMountDoesNotClearNewVehicleStamina() throws Exception {
        var mount = board(candle(DRILL));
        assertNotNull(mount);
        var field =
                emu.grasscutter.game.managers.stamina.StaminaManager.class.getDeclaredField(
                        "vehicleId");
        field.setAccessible(true);
        field.setInt(fixture.player.getStaminaManager(), 12345);
        var member = mount.getVehicleMembers().get(0);
        call(
                "dismount",
                new Class<?>[] {
                    emu.grasscutter.game.player.Player.class,
                    EntityVehicle.class,
                    VehicleMemberOuterClass.VehicleMember.class
                },
                fixture.player,
                mount,
                member);
        assertEquals(12345, field.getInt(fixture.player.getStaminaManager()));
    }

    @Test
    void staleScanFromPreviousSceneCannotBoard() {
        var selected = candle(DRILL);
        execute("CheckSubTagScanEntityMixin", packed(selected.getId()));
        fixture.player.setScene(
                new AbilityTestFixture.TestScene(
                        (AbilityTestFixture.TestWorld) fixture.scene.getWorld(),
                        fixture.scene.getSceneData()));
        execute("TryEnterVehicleMixin", ByteString.EMPTY);
        assertTrue(mounts().isEmpty());
    }

    @Test
    void nativePackedCaptureAndUnpackedIdsResolveEqually() {
        var parser =
                assertDoesNotThrow(
                        () ->
                                Class.forName(
                                        "emu.grasscutter.game.ability.mixins.CheckSubTagScanEntityMixin"));
        var method =
                assertDoesNotThrow(
                        () -> parser.getMethod("firstScannedEntityId", ByteString.class));
        assertEquals(
                4194499,
                assertDoesNotThrow(
                        () ->
                                method.invoke(
                                        null,
                                        ByteString.copyFrom(
                                                HexFormat.of().parseHex("1205c381800200")))));
        assertEquals(4194499, assertDoesNotThrow(() -> method.invoke(null, packed(4194499))));
        assertEquals(
                0,
                assertDoesNotThrow(
                        () ->
                                method.invoke(
                                        null,
                                        ByteString.copyFrom(
                                                HexFormat.of().parseHex("1205c381800200120180")))));
    }

    @Test
    void scanThenAttachIsSynchronousAndDoesNotChooseNearestDragon() {
        var unwanted = candle(DRILL);
        var selected = candle(HOOK);
        execute("CheckSubTagScanEntityMixin", packed(selected.getId()));
        execute("TryEnterVehicleMixin", ByteString.EMPTY);
        assertEquals(1, mounts().size());
        assertEquals(HOOK, mounts().get(0).getGadgetId());
        assertSame(unwanted, fixture.scene.getEntityById(unwanted.getId()));
        execute("TryEnterVehicleMixin", ByteString.EMPTY);
        assertEquals(1, mounts().size());
    }

    @Test
    void releasingWithoutTargetClearsScan() {
        var selected = candle(DRILL);
        execute("CheckSubTagScanEntityMixin", packed(selected.getId()));
        execute("CheckSubTagScanEntityMixin", ByteString.EMPTY);
        execute("TryEnterVehicleMixin", ByteString.EMPTY);
        assertTrue(mounts().isEmpty());
    }

    @Test
    void rejectedScanDoesNotRetainPreviousTarget() {
        var selected = candle(DRILL);
        execute("CheckSubTagScanEntityMixin", packed(selected.getId()));
        execute("CheckSubTagScanEntityMixin", packed(candle(BOAT).getId()));
        execute("TryEnterVehicleMixin", ByteString.EMPTY);
        assertTrue(mounts().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"120180", "12020180120101", "1001120180", "12050102", "100112010180"})
    void malformedScanCannotBoard(String hex) {
        candle(DRILL);
        execute("CheckSubTagScanEntityMixin", ByteString.copyFrom(HexFormat.of().parseHex(hex)));
        execute("TryEnterVehicleMixin", ByteString.EMPTY);
        assertTrue(mounts().isEmpty());
    }

    @Test
    void vehicleDeathReleasesRiderAfterDeathNotification() {
        var mount = board(candle(DRILL));
        assertNotNull(mount);
        fixture.scene.packets.clear();
        fixture.player.packets.clear();
        mount.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        fixture.scene.killEntity(mount, 0);
        assertTrue(mount.getVehicleMembers().isEmpty());
        assertNull(fixture.scene.getEntityById(mount.getId()));
        assertTrue(
                fixture.scene.packets.stream()
                        .anyMatch(
                                p ->
                                        p.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .SceneEntityDisappearNotify));
        assertTrue(
                fixture.player.packets.stream()
                        .anyMatch(
                                p ->
                                        p.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .VehicleInteractRsp));
    }

    @Test
    void boardingPacketPreservesMountHealthAndPosition() throws Exception {
        var mount = board(candle(DRILL));
        assertNotNull(mount);
        var packet =
                fixture.player.packets.stream()
                        .filter(
                                p ->
                                        p.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .VehicleInteractRsp)
                        .findFirst()
                        .orElseThrow();
        var rsp = VehicleInteractRspOuterClass.VehicleInteractRsp.parseFrom(packet.getData());
        assertEquals(mount.getPosition().toProto(), rsp.getVehiclePos());
        assertEquals(mount.getRotation().toProto(), rsp.getVehicleRot());
        assertEquals(4, rsp.getVehicleType());
        assertEquals(10000, mount.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
    }

    @Test
    void actualResourceHasDecodedScanAndTransferMixins() throws Exception {
        var path =
                Path.of(
                        "resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Natsaurus_V5_0_Dragon.json");
        assertTrue(Files.isRegularFile(path), "Resource evidence is required");
        AbilityData actual = null;
        for (var row : JsonParser.parseString(Files.readString(path)).getAsJsonArray()) {
            var data = JsonUtils.decode(row.getAsJsonObject().get("Default"), AbilityData.class);
            if ("TeamAbility_Natsaurus_Transfer_Vehicle_Skill".equals(data.abilityName))
                actual = data;
        }
        assertNotNull(actual);
        var transferType = actual.modifiers.get("Avatar_Perform").modifierMixins[0].type;
        assertNotNull(transferType);
        assertEquals("TryEnterVehicleMixin", transferType.name());
        var scan =
                actual.modifiers.get("UNIQUE_CurLocalAvatar_Natsaurus_Transfer_Tick_Search")
                        .modifierMixins[0];
        assertNotNull(scan.type);
        assertEquals("CheckSubTagScanEntityMixin", scan.type.name());
        var start = assertDoesNotThrow(() -> AbilityMixinData.class.getField("onSelectStart"));
        assertTrue(((Object[]) start.get(scan)).length > 0);
    }

    static ByteString packed(int id) {
        return assertDoesNotThrow(
                () -> {
                    var bytes = new java.io.ByteArrayOutputStream();
                    var out = com.google.protobuf.CodedOutputStream.newInstance(bytes);
                    out.writeUInt32(2, id);
                    out.flush();
                    return ByteString.copyFrom(bytes.toByteArray());
                });
    }
}
