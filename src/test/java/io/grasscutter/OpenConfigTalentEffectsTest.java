package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AvatarSkillMaxChargeCountNotifyOuterClass.AvatarSkillMaxChargeCountNotify;
import emu.grasscutter.net.proto.ProudSkillExtraLevelNotifyOuterClass.ProudSkillExtraLevelNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;

class OpenConfigTalentEffectsTest {
    private static List<AvatarSkillData> skills;
    private static List<AvatarSkillDepotData> depots;
    private static Map<String, String> raw = new LinkedHashMap<>();
    private final List<Runnable> restore = new ArrayList<>();
    private AbilityTestFixture fixture;
    private SkillTalentLevelTest.RecordingPlayer player;
    private SkillTalentLevelTest.RecordingAvatar avatar;

    @BeforeAll
    static void resources() throws Exception {
        skills =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarSkillExcelConfigData.json"),
                        AvatarSkillData.class);
        depots =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarSkillDepotExcelConfigData.json"),
                        AvatarSkillDepotData.class);
        try (var files =
                Files.newDirectoryStream(
                        Path.of("resources/BinOutput/Talent/AvatarTalents"), "*.json")) {
            for (var file : files)
                JsonParser.parseString(Files.readString(file))
                        .getAsJsonObject()
                        .entrySet()
                        .forEach(e -> raw.put(e.getKey(), e.getValue().toString()));
        }
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        player = new SkillTalentLevelTest.RecordingPlayer();
        player.setUid(73556);
        player.setScene(fixture.scene);
        skills.forEach(s -> put(GameData.getAvatarSkillDataMap(), s.getId(), s));
        depots.forEach(
                d -> {
                    d.onLoad();
                    put(GameData.getAvatarSkillDepotDataMap(), d.getId(), d);
                });
        for (int group : new int[] {7331, 7332, 7339})
            put(GameData.getProudSkillGroupMaxLevels(), group, 15);
        avatar = new SkillTalentLevelTest.RecordingAvatar(player, 10000073, depot(7301));
        player.packets.clear();
    }

    @AfterEach
    void cleanup() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    private static AvatarSkillDepotData depot(int id) {
        return depots.stream().filter(d -> d.getId() == id).findFirst().orElseThrow();
    }

    private static OpenConfigEntry config(String json) {
        return new OpenConfigEntry(
                "Test", JsonUtils.decode(JsonParser.parseString(json), OpenConfigData[].class));
    }

    private void apply(String json) {
        avatar.calcConstellation(config(json), true);
    }

    private int extra(int group) {
        return avatar.getProudSkillBonusMap().getOrDefault(group, 0);
    }

    private List<ProudSkillExtraLevelNotify> extraNotifies() {
        return player.packets.stream()
                .filter(p -> p.getOpcode() == PacketOpcodes.ProudSkillExtraLevelNotify)
                .map(
                        p -> {
                            try {
                                return ProudSkillExtraLevelNotify.parseFrom(p.getData());
                            } catch (Exception e) {
                                throw new AssertionError(e);
                            }
                        })
                .toList();
    }

    private List<AvatarSkillMaxChargeCountNotify> chargeNotifies() {
        return player.packets.stream()
                .filter(p -> p.getOpcode() == PacketOpcodes.AvatarSkillMaxChargeCountNotify)
                .map(
                        p -> {
                            try {
                                return AvatarSkillMaxChargeCountNotify.parseFrom(p.getData());
                            } catch (Exception e) {
                                throw new AssertionError(e);
                            }
                        })
                .toList();
    }

    private void talent(int id, String name, String json) {
        put(
                GameData.getAvatarTalentDataMap(),
                id,
                JsonUtils.decode(
                        "{\"talentId\":" + id + ",\"openConfig\":\"" + name + "\"}",
                        AvatarTalentData.class));
        put(GameData.getOpenConfigEntries(), name, config(json));
    }

    @Test
    void realEnergyDiskKeepsBothOneLevelModifiers() {
        apply(raw.get("Talent_RogueV2"));
        assertEquals(1, extra(7332));
        assertEquals(1, extra(7339));
        assertEquals(0, extra(7331));
        assertEquals(
                Set.of(2, 9),
                extraNotifies().stream()
                        .map(ProudSkillExtraLevelNotify::getTalentIndex)
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(extraNotifies().stream().allMatch(p -> p.getExtraLevel() == 1));
    }

    @Test
    void realTartagliaNormalModifierIsOneRatherThanThree() {
        apply(raw.get("Tartaglia_PermanentSkill_3"));
        assertEquals(1, extra(7331));
        assertEquals(1, extraNotifies().get(0).getExtraLevel());
        assertEquals(Map.of(10731, 1, 10732, 1, 10735, 1), avatar.getSkillLevelMap());
    }

    @Test
    void threeLevelConstellationRowsStillUseTheCorrectGroup() {
        apply(raw.get("Nahida_Constellation_3"));
        apply(raw.get("Nahida_Constellation_5"));
        assertEquals(3, extra(7332));
        assertEquals(3, extra(7339));
    }

    @Test
    void multipleModifiersForOneSlotAccumulateAndNotifyTheFinalTotalOnce() {
        apply(
                "[{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":2,\"extraLevel\":1},{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":2,\"extraLevel\":2}]");
        assertEquals(3, extra(7332));
        assertEquals(1, extraNotifies().size());
    }

    @Test
    void unknownTypesWithATalentIndexDoNotGrantLevels() {
        apply("[{\"$type\":\"Unknown\",\"talentIndex\":2,\"extraLevel\":3}]");
        assertEquals(0, extra(7332));
        assertTrue(extraNotifies().isEmpty());
    }

    @Test
    void typeNameSubstringsDoNotActivateLevelOrChargeEffects() {
        apply(
                "[{\"$type\":\"UnknownAddTalentExtraLevel\",\"talentIndex\":2,\"extraLevel\":3},{\"$type\":\"UnknownModifySkillPoint\",\"skillID\":10732,\"pointDelta\":1}]");
        assertEquals(0, extra(7332));
        assertTrue(avatar.getSkillExtraChargeMap().isEmpty());
    }

    @Test
    void otherTalentTypesDoNotChangeAvatarSkillLevels() {
        apply(
                "[{\"$type\":\"AddTalentExtraLevel\",\"talentType\":\"Team\",\"talentIndex\":2,\"extraLevel\":3}]");
        assertEquals(0, extra(7332));
    }

    @Test
    void missingZeroNegativeOrInvalidIndexModifiersDoNotInventThreeLevels() {
        apply(
                "[{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":2},{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":2,\"extraLevel\":0},{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":9,\"extraLevel\":-1},{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":5,\"extraLevel\":3}]");
        assertEquals(0, extra(7332));
        assertEquals(0, extra(7339));
        assertTrue(extraNotifies().isEmpty());
    }

    @Test
    void legacyCompiledCacheKeepsItsThreeLevelConstellationFallback() {
        var cached =
                JsonUtils.decode(
                        "{\"name\":\"Legacy\",\"extraTalentIndex\":2}", OpenConfigEntry.class);
        avatar.calcConstellation(cached, true);
        assertEquals(3, extra(7332));
    }

    @Test
    void supportedLegacyDiscriminatorUsesItsExplicitAmount() {
        apply("[{\"$type\":\"DPACHKADLMK\",\"OJOFFKLNAHN\":9,\"extraLevel\":1}]");
        assertEquals(1, extra(7339));
    }

    @Test
    void oneSlotAtTheResourceCapDoesNotSuppressAnotherSlot() {
        avatar.setSkillLevel(10732, 15);
        player.packets.clear();
        apply(raw.get("Talent_RogueV2"));
        assertEquals(0, extra(7332));
        assertEquals(1, extra(7339));
        assertEquals(1, extraNotifies().size());
        assertEquals(9, extraNotifies().get(0).getTalentIndex());
    }

    @Test
    void twoChargeModifiersAccumulateInsteadOfReplacingTheFirst() {
        apply(
                "[{\"$type\":\"ModifySkillPoint\",\"skillID\":10732,\"pointDelta\":1},{\"$type\":\"ModifySkillPoint\",\"skillID\":10732,\"pointDelta\":2}]");
        assertEquals(4, avatar.getSkillExtraChargeMap().get(10732));
        assertEquals(1, chargeNotifies().size());
        assertEquals(4, chargeNotifies().get(0).getMaxChargeCount());
    }

    @Test
    void separateChargeConfigsAccumulateFromTheCurrentCount() {
        String row = "[{\"$type\":\"ModifySkillPoint\",\"skillID\":10732,\"pointDelta\":1}]";
        apply(row);
        apply(row);
        assertEquals(3, avatar.getSkillExtraChargeMap().get(10732));
    }

    @Test
    void aForeignSkillDoesNotGainChargesOnThisAvatar() {
        apply("[{\"$type\":\"ModifySkillPoint\",\"skillID\":10607,\"pointDelta\":1}]");
        assertFalse(avatar.getSkillExtraChargeMap().containsKey(10607));
        assertTrue(chargeNotifies().isEmpty());
    }

    @Test
    void negativeOrOverflowingChargeCountsAreIgnored() {
        apply(
                "[{\"$type\":\"ModifySkillPoint\",\"skillID\":10732,\"pointDelta\":-2},{\"$type\":\"ModifySkillPoint\",\"skillID\":10732,\"pointDelta\":2147483647}]");
        assertTrue(avatar.getSkillExtraChargeMap().isEmpty());
        assertTrue(chargeNotifies().isEmpty());
    }

    @Test
    void repeatedUnlockAndRecalculationDoNotAccumulateCharges() {
        String row = "[{\"$type\":\"ModifySkillPoint\",\"skillID\":10732,\"pointDelta\":1}]";
        talent(731, "First", row);
        talent(732, "Second", row);
        assertTrue(avatar.unlockConstellation(731, true));
        assertTrue(avatar.unlockConstellation(732, true));
        assertEquals(3, avatar.getSkillExtraChargeMap().get(10732));
        avatar.unlockConstellation(732, true);
        avatar.recalcConstellations();
        assertEquals(3, avatar.getSkillExtraChargeMap().get(10732));
    }

    @Test
    void forceResetImmediatelyRestoresChargeAndLevelStateAndNotifiesTheClient() {
        talent(
                731,
                "Combined",
                "[{\"$type\":\"ModifySkillPoint\",\"skillID\":10732,\"pointDelta\":1},{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":2,\"extraLevel\":1}]");
        avatar.unlockConstellation(731, true);
        player.packets.clear();
        avatar.forceConstellationLevel(0);
        assertEquals(0, extra(7332));
        assertTrue(avatar.getSkillExtraChargeMap().isEmpty());
        assertEquals(0, extraNotifies().get(0).getExtraLevel());
        assertEquals(1, chargeNotifies().get(0).getMaxChargeCount());
    }

    @Test
    void allElevenRealChargeRowsIncludeXiaosSubSkill() {
        int count = 0;
        for (var e : raw.entrySet()) {
            var rows = JsonParser.parseString(e.getValue()).getAsJsonArray();
            var ids = new HashSet<Integer>();
            for (var r : rows)
                if (r.getAsJsonObject().get("$type").getAsString().equals("ModifySkillPoint"))
                    ids.add(r.getAsJsonObject().get("skillID").getAsInt());
            if (ids.isEmpty()) continue;
            var d =
                    depots.stream()
                            .filter(
                                    x -> {
                                        var all = new HashSet<Integer>();
                                        x.getSkillsAndEnergySkill().forEach(all::add);
                                        if (x.getSubSkills() != null) all.addAll(x.getSubSkills());
                                        return all.containsAll(ids);
                                    })
                            .findFirst()
                            .orElseThrow();
            var value =
                    new SkillTalentLevelTest.RecordingAvatar(player, 10000000 + d.getId() / 100, d);
            value.calcConstellation(config(e.getValue()), false);
            for (int id : ids) {
                assertEquals(
                        GameData.getAvatarSkillDataMap().get(id).getMaxChargeNum() + 1,
                        value.getSkillExtraChargeMap().get(id),
                        e.getKey());
                count++;
            }
        }
        assertEquals(11, count);
    }

    @Test
    void travelerElementChangesRestoreOnlyTheActiveDepotsSavedConstellations() throws Exception {
        var talents =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarTalentExcelConfigData.json"),
                        AvatarTalentData.class);
        var value = new SkillTalentLevelTest.RecordingAvatar(player, 10000005, depot(504));
        for (int id : new int[] {73, 93}) {
            var talent = talents.stream().filter(t -> t.getId() == id).findFirst().orElseThrow();
            put(GameData.getAvatarTalentDataMap(), id, talent);
            put(
                    GameData.getOpenConfigEntries(),
                    talent.getOpenConfig(),
                    config(raw.get(talent.getOpenConfig())));
        }
        value.setSkillDepotData(depot(504));
        assertTrue(value.unlockConstellation(73, true));
        var wind = new HashMap<>(value.getProudSkillBonusMap());
        assertTrue(wind.values().contains(3));
        value.setSkillDepotData(depot(506));
        assertTrue(value.getProudSkillBonusMap().values().stream().allMatch(n -> n == 0));
        assertTrue(value.unlockConstellation(93, true));
        var rock = new HashMap<>(value.getProudSkillBonusMap());
        assertTrue(rock.values().contains(3));
        value.setSkillDepotData(depot(504));
        assertEquals(wind, value.getProudSkillBonusMap());
        value.forceConstellationLevel(0);
        value.setSkillDepotData(depot(506));
        assertEquals(rock, value.getProudSkillBonusMap());
    }

    @Test
    void loginBeforeAvatarDataDoesNotSendIncrementalTalentUpdates() {
        player.loggedIn = false;
        apply(raw.get("Talent_RogueV2"));
        apply("[{\"$type\":\"ModifySkillPoint\",\"skillID\":10732,\"pointDelta\":1}]");
        assertEquals(1, extra(7332));
        assertEquals(2, avatar.getSkillExtraChargeMap().get(10732));
        assertTrue(extraNotifies().isEmpty());
        assertTrue(chargeNotifies().isEmpty());
    }
}
