package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.ShareCDData;
import emu.grasscutter.data.excels.activity.ActivitySkillData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AllShareCDDataNotifyOuterClass.AllShareCDDataNotify;
import emu.grasscutter.net.proto.PlayerTimeNotifyOuterClass.PlayerTimeNotify;
import emu.grasscutter.net.proto.ShareCDOuterClass.ShareCD;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.PlayerTimeClock;
import emu.grasscutter.server.packet.send.PacketAllShareCDDataNotify;
import emu.grasscutter.server.packet.send.PacketPlayerTimeNotify;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

class AbilitySharedCooldownTest {
    private static List<AvatarSkillData> avatarSkills;
    private static List<ActivitySkillData> activitySkills;
    private static List<ShareCDData> groups;
    private AbilityTestFixture fixture;
    private EntityAvatar entity;
    private Ability ability;
    private AbilitySharedCooldown cooldown;
    private final AtomicLong now = new AtomicLong(1000);
    private final AtomicLong realTime = new AtomicLong(1791504000000L);
    private final AtomicLong pulseTime = new AtomicLong();
    private final List<Runnable> restore = new ArrayList<>();
    private final ActionSharedCooldown handler = new ActionSharedCooldown();

    @BeforeAll
    static void resources() throws Exception {
        var root = Path.of("resources/ExcelBinOutput");
        avatarSkills =
                JsonUtils.loadToList(
                        root.resolve("AvatarSkillExcelConfigData.json"), AvatarSkillData.class);
        activitySkills =
                JsonUtils.loadToList(
                        root.resolve("ActivitySkillExcelConfigData.json"), ActivitySkillData.class);
        groups =
                JsonUtils.loadToList(
                        root.resolve("ShareCDExcelConfigData.json"), ShareCDData.class);
        AbilityManager.registerHandlers();
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setUid(76301);
        fixture.player.setWorld(fixture.scene.getWorld());
        install(GameData.getAvatarSkillDataMap(), avatarSkills);
        install(GameData.getActivitySkillDataMap(), activitySkills);
        install(GameData.getShareCDDataMap(), groups);
        entity = avatar(fixture.player);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        ability =
                new Ability(
                        JsonUtils.decode("{\"abilityName\":\"SharedCooldown\"}", AbilityData.class),
                        entity,
                        fixture.player);
        cooldown = new AbilitySharedCooldown(fixture.player, now::get, realTime::get);
        fixture.player.packets.clear();
    }

    private <T extends GameResource> void install(Int2ObjectMap<T> map, List<T> values) {
        for (var value : values) {
            var old = map.put(value.getId(), value);
            restore.add(
                    () -> {
                        if (old == null) map.remove(value.getId());
                        else map.put(value.getId(), old);
                    });
        }
    }

    @AfterEach
    void close() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    private EntityAvatar avatar(Player player) {
        var depot =
                JsonUtils.decode(
                        "{\"id\":7301,\"skills\":[20011],\"talents\":[],\"inherentProudSkillOpens\":[]}",
                        AvatarSkillDepotData.class);
        depot.onLoad();
        var avatar = new SkillTalentLevelTest.RecordingAvatar(player, 10000073, depot);
        var result =
                new EntityAvatar(fixture.scene, avatar) {
                    final AbilityPeriodicHealing periodic =
                            new AbilityPeriodicHealing(this, pulseTime::get);

                    @Override
                    public void initAbilities() {}

                    @Override
                    public AbilityPeriodicHealing getAbilityPeriodicHealing() {
                        return periodic;
                    }
                };
        fixture.scene.getEntities().put(result.getId(), result);
        return result;
    }

    private AllShareCDDataNotify lastPacket() throws Exception {
        var packet = fixture.player.packets.get(fixture.player.packets.size() - 1);
        assertEquals(24819, packet.getOpcode());
        return AllShareCDDataNotify.parseFrom(packet.getData());
    }

    private static AbilityModifierAction action(String json) {
        return JsonUtils.decode(json, AbilityModifierAction.class);
    }

    @Test
    void wireBytesMatchTheVerifiedClientTagsIncludingTheSlotAndFullFlag() {
        var cd = ShareCD.newBuilder().setCdEndTime(1001).setCdSlot(1).build();
        var packet = new PacketAllShareCDDataNotify(Map.of(20064, List.of(cd)), true);
        assertEquals(24819, PacketOpcodes.AllShareCDDataNotify);
        assertArrayEquals(
                HexFormat.of().parseHex("321108e09c01120b320520e907280168e09c016001"),
                packet.getData());
    }

    @Test
    void resourcesUseTheNormalLoaderAndSkillIdsResolveDifferentSharedGroups() {
        assertSame(GameData.getShareCDDataMap(), GameData.getMapByResourceDef(ShareCDData.class));
        assertSame(
                GameData.getActivitySkillDataMap(),
                GameData.getMapByResourceDef(ActivitySkillData.class));
        assertEquals(25, groups.size());
        assertEquals(216, activitySkills.size());
        assertEquals(20064, GameData.getAvatarSkillDataMap().get(20081).getShareCDID());
        assertTrue(GameData.getShareCDDataMap().get(20081).isUseRealTime());
        assertTrue(cooldown.startAvatarSkill(20081, 0));
        assertEquals(Set.of(20064), cooldown.snapshot().keySet());
        assertEquals(2000, cooldown.snapshot().get(20064).get(0).getCdEndTime());
        assertTrue(cooldown.startAvatarSkill(20086, 0));
        assertEquals(1, fixture.player.packets.size());
    }

    @Test
    void sameSlotUsesSeparateChargesInASerialQueue() throws Exception {
        assertTrue(cooldown.startAvatarSkill(20050, 0));
        assertTrue(cooldown.startAvatarSkill(20050, 0));
        assertEquals(List.of(5000L, 9000L), ends(20050));
        assertEquals(
                List.of(0, 0),
                cooldown.snapshot().get(20050).stream().map(ShareCD::getCdSlot).toList());
        assertEquals(2, lastPacket().getShareCdInfoMapOrThrow(20050).getShareCdListCount());
        assertFalse(lastPacket().getIsAll());
        assertTrue(cooldown.startAvatarSkill(20050, 1));
        assertEquals(2, fixture.player.packets.size());
        assertEquals(List.of(5000L, 9000L), ends(20050));
    }

    private List<Long> ends(int group) {
        return cooldown.snapshot().getOrDefault(group, List.of()).stream()
                .map(ShareCD::getCdEndTime)
                .toList();
    }

    @Test
    void eachQueuedChargeKeepsTheSlotThatDeterminesItsRecoveryDuration() {
        assertTrue(cooldown.startAvatarSkill(20050, 0));
        assertTrue(cooldown.startAvatarSkill(20050, 1));
        assertEquals(List.of(5000L, 7800L), ends(20050));
        assertEquals(
                List.of(0, 1),
                cooldown.snapshot().get(20050).stream().map(ShareCD::getCdSlot).toList());
        now.set(5000);
        assertEquals(List.of(7800L), ends(20050));
        assertTrue(cooldown.startAvatarSkill(20050, 0));
        assertEquals(List.of(7800L, 11800L), ends(20050));
        now.set(11800);
        assertTrue(cooldown.snapshot().isEmpty());
    }

    @Test
    void zeroDurationSlotFinishesWithoutCreatingALongCooldown() {
        assertTrue(cooldown.startAvatarSkill(20081, 1));
        assertTrue(cooldown.snapshot().isEmpty());
        assertTrue(cooldown.startAvatarSkill(20081, 0));
        assertEquals(List.of(2000L), ends(20064));
    }

    @Test
    void activitySkillsShareTheirConfiguredGroupAcrossDifferentSkillIds() {
        assertTrue(cooldown.startActivitySkill(20108, 0));
        assertEquals(List.of(2500L), ends(20080));
        assertTrue(cooldown.startActivitySkill(20070, 1));
        assertEquals(1, fixture.player.packets.size());
        now.set(2500);
        assertTrue(cooldown.startActivitySkill(20070, 1));
        assertEquals(List.of(3500L), ends(20080));
        assertTrue(cooldown.startActivitySkill(20163, 0));
        assertEquals(Set.of(20080, 20113), cooldown.snapshot().keySet());
    }

    @Test
    void clearSendsAnEmptyGroupAndKeepsOtherGroups() throws Exception {
        assertTrue(cooldown.startAvatarSkill(20050, 0));
        assertTrue(cooldown.startAvatarSkill(20059, 0));
        assertTrue(cooldown.clear(20050));
        var packet = lastPacket();
        assertEquals(Set.of(20050), packet.getShareCdInfoMapMap().keySet());
        assertEquals(20050, packet.getShareCdInfoMapOrThrow(20050).getShareCdId());
        assertEquals(0, packet.getShareCdInfoMapOrThrow(20050).getShareCdListCount());
        assertEquals(Set.of(20059), cooldown.snapshot().keySet());
        assertTrue(cooldown.clear(20081));
        assertEquals(0, lastPacket().getShareCdInfoMapOrThrow(20081).getShareCdListCount());
    }

    @Test
    void sceneChangesKeepPlayerEndpointsAndAPlayerClockResetOnlyClearsNormalGroups()
            throws Exception {
        assertTrue(cooldown.startAvatarSkill(20050, 0));
        assertTrue(cooldown.startActivitySkill(20109, 0));
        var newScene =
                new AbilityTestFixture.TestScene(
                        (AbilityTestFixture.TestWorld) fixture.scene.getWorld(),
                        fixture.scene.getSceneData());
        fixture.player.setScene(newScene);
        cooldown.synchronize();
        assertTrue(lastPacket().getIsAll());
        assertEquals(Set.of(20050, 20081), lastPacket().getShareCdInfoMapMap().keySet());
        assertEquals(List.of(5000L), ends(20050));
        now.set(50);
        assertEquals(Set.of(20081), cooldown.snapshot().keySet());
        assertEquals(List.of(realTime.get() + 300000), ends(20081));
        assertTrue(cooldown.startAvatarSkill(20059, 0));
        assertEquals(List.of(2050L), ends(20059));
    }

    @Test
    void defaultClockUsesThePlayerNotifyTimeBeyondTheSignedIntegerBoundary() throws Exception {
        var nanos = new AtomicLong();
        var clock = new PlayerTimeClock(nanos::get);
        fixture.player.setSession(new GameSession(null, clock));
        nanos.set(4294967294L * 1_000_000L);
        var value = new AbilitySharedCooldown(fixture.player);
        assertTrue(value.startAvatarSkill(20050, 0));
        var packet =
                PlayerTimeNotify.parseFrom(new PacketPlayerTimeNotify(fixture.player).getData());
        assertEquals(4294967294L, packet.getPlayerTime());
        assertEquals(
                packet.getPlayerTime() + 4000, value.snapshot().get(20050).get(0).getCdEndTime());
    }

    @Test
    void realTimeGroupContinuesWhileThePlayerClockIsPaused() {
        var nanos = new AtomicLong();
        var clock = new PlayerTimeClock(nanos::get);
        var value =
                new AbilitySharedCooldown(
                        fixture.player, () -> clock.snapshot().playerTime(), realTime::get);
        assertTrue(value.startAvatarSkill(20050, 0));
        assertTrue(value.startActivitySkill(20109, 0));
        assertEquals(4000L, value.snapshot().get(20050).get(0).getCdEndTime());
        assertEquals(realTime.get() + 300000, value.snapshot().get(20081).get(0).getCdEndTime());
        nanos.set(500_000_000L);
        clock.setPaused(true);
        nanos.addAndGet(301_000_000_000L);
        realTime.addAndGet(301000);
        assertEquals(Set.of(20050), value.snapshot().keySet());
        clock.setPaused(false);
        nanos.addAndGet(3_500_000_000L);
        assertTrue(value.snapshot().isEmpty());
    }

    @Test
    void replacingTheSessionCannotImportThePreviousSessionsEndpoints() {
        assertTrue(cooldown.startAvatarSkill(20050, 0));
        assertTrue(cooldown.startActivitySkill(20109, 0));
        fixture.player.setSession(new GameSession(null));
        assertTrue(cooldown.snapshot().isEmpty());
    }

    @Test
    void realTimeEndpointsRemainUnixTimeWhenTheServerClockIsCorrected() {
        assertTrue(cooldown.startActivitySkill(20109, 0));
        long end = ends(20081).get(0);
        realTime.addAndGet(-10000);
        assertEquals(List.of(end), ends(20081));
        realTime.set(end);
        assertTrue(cooldown.snapshot().isEmpty());
    }

    @Test
    void invalidClocksAndEndpointOverflowDoNotCreatePackets() {
        now.set(Long.MAX_VALUE - 1);
        assertFalse(cooldown.startAvatarSkill(20050, 0));
        realTime.set(Long.MAX_VALUE - 1);
        now.set(-1);
        assertFalse(cooldown.startAvatarSkill(20050, 0));
        assertTrue(fixture.player.packets.isEmpty());
        assertTrue(cooldown.snapshot().isEmpty());
    }

    @Test
    void playerObjectsWithTheSameUidHaveIndependentQueues() {
        assertTrue(cooldown.startAvatarSkill(20050, 0));
        var other = new AbilityTestFixture.TestPlayer();
        other.setUid(fixture.player.getUid());
        other.setScene(fixture.scene);
        var value = new AbilitySharedCooldown(other, now::get, realTime::get);
        assertTrue(value.snapshot().isEmpty());
        assertTrue(value.startAvatarSkill(20059, 0));
        assertEquals(Set.of(20059), value.snapshot().keySet());
        assertEquals(Set.of(20050), cooldown.snapshot().keySet());
    }

    @Test
    void detachedOrConcurrentlyChangedSceneCannotStartAStaleCooldown() {
        assertTrue(cooldown.startAvatarSkill(20050, 0));
        fixture.player.setScene(null);
        assertFalse(cooldown.startAvatarSkill(20059, 0));
        assertEquals(Set.of(20050), cooldown.snapshot().keySet());
        fixture.player.setScene(fixture.scene);
        var value =
                new AbilitySharedCooldown(
                        fixture.player,
                        () -> {
                            fixture.player.setScene(null);
                            return 1000;
                        },
                        realTime::get);
        assertFalse(value.startAvatarSkill(20059, 0));
        assertTrue(value.snapshot().isEmpty());
    }

    @Test
    void unknownIdsAndSlotsDoNotSendGuessedEndpoints() {
        assertFalse(cooldown.startAvatarSkill(20081, -1));
        assertFalse(cooldown.startAvatarSkill(20081, 2));
        assertFalse(cooldown.startAvatarSkill(20109, 0));
        assertFalse(cooldown.startActivitySkill(20116, 0));
        assertFalse(cooldown.startAvatarSkill(999999, 0));
        assertFalse(cooldown.clear(999999));
        assertTrue(fixture.player.packets.isEmpty());
        assertTrue(cooldown.snapshot().isEmpty());
    }

    @Test
    void handlersRequireOwnedLiveRecipientsAndKeepPermanentChargesUntouched() {
        var action = action("{\"$type\":\"AvatarShareCDSkillStart\",\"skillID\":20050}");
        var avatar = (SkillTalentLevelTest.RecordingAvatar) entity.getAvatar();
        avatar.getSkillExtraChargeMap().put(20050, 3);
        avatar.saves = 0;
        assertTrue(handler.execute(ability, action, ByteString.EMPTY, entity));
        assertEquals(Map.of(20050, 3), avatar.getSkillExtraChargeMap());
        assertEquals(0, avatar.saves);
        var partner = avatar(new AbilityTestFixture.TestPlayer());
        assertFalse(handler.executeResolved(ability, action, ByteString.EMPTY, partner));
        assertFalse(handler.executeResolved(ability, action, ByteString.EMPTY, fixture.entity));
        action.target = "Unknown";
        assertFalse(handler.execute(ability, action, ByteString.EMPTY, entity));
        action.target = null;
        entity.invalidateAbilityLifecycle();
        assertFalse(handler.execute(ability, action, ByteString.EMPTY, entity));
        assertEquals(1, fixture.player.packets.size());
    }

    @Test
    void aFailedPredicateCannotConsumeACharge() {
        var action =
                action(
                        "{\"$type\":\"AvatarShareCDSkillStart\",\"skillID\":20050,\"predicates\":[{\"$type\":\"ByTargetGlobalValue\",\"key\":\"ready\",\"value\":1,\"compareType\":\"Equal\"}]}");
        assertTrue(handler.execute(ability, action, ByteString.EMPTY, entity));
        assertTrue(fixture.player.packets.isEmpty());
        assertTrue(fixture.player.getAbilityManager().getSharedCooldown().snapshot().isEmpty());
    }

    @Test
    void canonicalAndLegacySlotNamesRespectCanonicalValuesAndRejectMalformedNumbers() {
        for (var key : List.of("shareCDType", "ABLJLEAMAGL", "DMGPMFKKGMJ")) {
            var value =
                    action(
                            "{\"$type\":\"AvatarShareCDSkillStart\",\"GCOFBECOEFF\":20050,\""
                                    + key
                                    + "\":1}");
            assertEquals(20050, value.skillID);
            assertEquals(1, value.shareCDType);
        }
        var value =
                action(
                        "{\"$type\":\"AvatarShareCDSkillStart\",\"skillID\":20059,\"GCOFBECOEFF\":20050,\"shareCDType\":0,\"ABLJLEAMAGL\":1}");
        assertEquals(20059, value.skillID);
        assertEquals(0, value.shareCDType);
        value =
                action(
                        "{\"$type\":\"AvatarShareCDSkillStart\",\"skillID\":20050,\"ABLJLEAMAGL\":0,\"DMGPMFKKGMJ\":1}");
        assertNull(value.shareCDType);
        for (var literal : List.of("null", "-1", "0.5", "\"1\"", "2147483648", "true")) {
            value =
                    action(
                            "{\"$type\":\"AvatarShareCDSkillStart\",\"skillID\":20050,\"shareCDType\":"
                                    + literal
                                    + "}");
            assertFalse(ActionSharedCooldown.supported(value));
        }
    }

    @Test
    void controlledModifierStartAndRemovalUseSharedCooldownHandlers() {
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"SharedState\",\"modifiers\":{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Shared\"}]},\"Shared\":{\"onAdded\":[{\"$type\":\"AvatarShareCDSkillStart\",\"skillID\":20050}],\"onRemoved\":[{\"$type\":\"ClearShareCD\",\"shareCDID\":20050}]}}}",
                        AbilityData.class);
        var selected = new Ability(data, entity, fixture.player);
        selected.registerModifier(
                entity, new AbilityModifierController(selected, data, data.modifiers.get("Pulse")));
        pulseTime.set(1_000_000_000L);
        entity.onTick(0);
        assertNotNull(selected.getModifiers(entity).get("Shared"));
        assertTrue(selected.getModifiers(entity).get("Shared").hasServerCallbacks());
        assertEquals(
                1,
                fixture.player
                        .getAbilityManager()
                        .getSharedCooldown()
                        .snapshot()
                        .get(20050)
                        .size());
        assertEquals(1, fixture.player.packets.size());
        selected.removeModifier(entity, "Shared");
        assertTrue(fixture.player.getAbilityManager().getSharedCooldown().snapshot().isEmpty());
        assertEquals(2, fixture.player.packets.size());
    }

    static Stream<Arguments> actualActions() throws Exception {
        var base = Path.of("resources/BinOutput/Ability/Temp");
        var result = new ArrayList<Arguments>();
        try (var paths = Files.walk(base)) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                var text = Files.readString(path);
                if (text.contains("ShareCD"))
                    collect(JsonParser.parseString(text), base.relativize(path).toString(), result);
            }
        }
        assertEquals(73, result.size());
        return result.stream();
    }

    private static void collect(JsonElement value, String path, List<Arguments> result) {
        if (value.isJsonArray()) {
            for (var child : value.getAsJsonArray()) collect(child, path, result);
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("$type")
                    && Set.of(
                                    "AvatarShareCDSkillStart",
                                    "ActivitySkillShareCDStart",
                                    "ClearShareCD")
                            .contains(object.get("$type").getAsString()))
                result.add(Arguments.of(path, object));
            object.entrySet().forEach(entry -> collect(entry.getValue(), path, result));
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("actualActions")
    void actualResourceNodesSelectTheirGroupAndSlotOrRemainExplicitlyUnsupported(
            String path, JsonObject node) throws Exception {
        var action = JsonUtils.decode(node, AbilityModifierAction.class);
        boolean expected =
                action.type != AbilityModifierAction.Type.ActivitySkillShareCDStart
                        || action.skillID != 20116;
        assertEquals(expected, ActionSharedCooldown.supported(action));
        assertEquals(expected, handler.execute(ability, action, ByteString.EMPTY, entity));
        if (!expected) {
            assertTrue(fixture.player.packets.isEmpty());
            return;
        }
        int group =
                action.type == AbilityModifierAction.Type.ClearShareCD
                        ? action.shareCDID
                        : action.type == AbilityModifierAction.Type.AvatarShareCDSkillStart
                                ? GameData.getAvatarSkillDataMap()
                                        .get(action.skillID)
                                        .getShareCDID()
                                : GameData.getActivitySkillDataMap()
                                        .get(action.skillID)
                                        .getShareCDID();
        var packet = lastPacket();
        assertFalse(packet.getIsAll());
        assertEquals(Set.of(group), packet.getShareCdInfoMapMap().keySet());
        if (action.type == AbilityModifierAction.Type.ClearShareCD)
            assertEquals(0, packet.getShareCdInfoMapOrThrow(group).getShareCdListCount());
        else
            assertEquals(
                    action.shareCDType,
                    packet.getShareCdInfoMapOrThrow(group).getShareCdList(0).getCdSlot());
    }
}
