package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.protobuf.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.net.proto.DungeonEntryInfoOuterClass.DungeonEntryInfo;
import emu.grasscutter.net.proto.DungeonEntryInfoRspOuterClass.DungeonEntryInfoRsp;
import emu.grasscutter.net.proto.WeeklyBossResinDiscountInfoOuterClass.WeeklyBossResinDiscountInfo;
import emu.grasscutter.net.proto._InvestigationMonsterDetailOuterClass._InvestigationMonsterDetail;
import emu.grasscutter.server.packet.send.PacketDungeonEntryInfoRsp;

import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Raw client tags, independent of the generated field constants, and actual entry packet builders.
 */
class DungeonEntryProtocol71Test {
    private AbilityTestFixture environment;
    private InvestigationRewardTest.ClaimPlayer player;
    private final Gson gson = new Gson();
    private Map<Integer, DungeonData> oldDungeons;

    @BeforeEach
    void prepare() throws Exception {
        environment = new AbilityTestFixture();
        player = new InvestigationRewardTest.ClaimPlayer();
        oldDungeons = new HashMap<>(GameData.getDungeonDataMap());
        GameData.getDungeonDataMap().clear();
        GameData.getDungeonDataMap()
                .put(
                        2021,
                        gson.fromJson("{id:2021,subType:'DUNGEON_SUB_BOSS'}", DungeonData.class));
        GameData.getDungeonDataMap()
                .put(4300, gson.fromJson("{id:4300,type:'DUNGEON_PLOT'}", DungeonData.class));
    }

    @AfterEach
    void close() {
        GameData.getDungeonDataMap().clear();
        GameData.getDungeonDataMap().putAll(oldDungeons);
        environment.close();
    }

    private static Map<Integer, Integer> uints(byte[] bytes) throws Exception {
        var input = CodedInputStream.newInstance(bytes);
        var values = new HashMap<Integer, Integer>();
        int tag;
        while ((tag = input.readTag()) != 0) {
            if ((tag & 7) == 0) values.put(tag >>> 3, input.readUInt32());
            else input.skipField(tag);
        }
        return values;
    }

    private static byte[] scalars(Map<Integer, Integer> values) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        for (var entry : new TreeMap<>(values).entrySet())
            output.writeUInt32(entry.getKey(), entry.getValue());
        output.flush();
        return bytes.toByteArray();
    }

    @Test
    void entryDescriptorMatchesTheClientsWriterOffsetsAndTags() {
        var descriptor = DungeonEntryInfo.getDescriptor();
        for (var entry :
                Map.of(
                                "max_boss_chest_num",
                                4,
                                "dungeon_id",
                                15,
                                "boss_chest_num",
                                10,
                                "next_refresh_time",
                                2,
                                "weekly_boss_resin_discount_info",
                                3,
                                "is_passed",
                                12)
                        .entrySet())
            assertEquals(
                    entry.getValue(),
                    descriptor.findFieldByName(entry.getKey()).getNumber(),
                    entry.getKey());
        assertNull(descriptor.findFieldByNumber(50000));
        assertNull(descriptor.findFieldByNumber(50001));
    }

    @Test
    void aNativeEntryFixtureDoesNotTurnTheDungeonIdIntoTheClaimCount() throws Exception {
        var entry =
                DungeonEntryInfo.parseFrom(new byte[] {0x20, 3, 0x50, 2, 0x78, (byte) 0xe5, 0x0f});
        assertEquals(2021, entry.getDungeonId());
        assertEquals(2, entry.getBossChestNum());
        assertEquals(3, entry.getMaxBossChestNum());
    }

    @Test
    void serializedEntryNamesUseNativeTagsInsteadOfCompatibilityPlaceholders() throws Exception {
        var entry =
                DungeonEntryInfo.newBuilder()
                        .setDungeonId(2021)
                        .setBossChestNum(2)
                        .setMaxBossChestNum(3)
                        .build();
        assertEquals(Map.of(4, 3, 10, 2, 15, 2021), uints(entry.toByteArray()));
    }

    @Test
    void weeklyBossPacketSendsNativeIdCountersAndTheNextResinCost() throws Exception {
        player.setWeeklyBossChestNum(2);
        var point = gson.fromJson("{id:11,dungeonIds:[2021]}", PointData.class);
        var packet = new PacketDungeonEntryInfoRsp(point, player);
        var reply = DungeonEntryInfoRsp.parseFrom(packet.getData());
        var entry = reply.getDungeonEntryList(0);
        assertEquals(Map.of(4, 3, 10, 2, 15, 2021), uints(entry.toByteArray()));
        var discount = entry.getWeeklyBossResinDiscountInfo();
        assertEquals(1, discount.getDiscountNum());
        assertEquals(30, discount.getResinCost());
        assertEquals(60, discount.getOriginalResinCost());
    }

    @Test
    void anOrdinaryDungeonPacketStillUsesTheNativeDungeonIdTag() throws Exception {
        var point = gson.fromJson("{id:11,dungeonIds:[4300]}", PointData.class);
        var reply =
                DungeonEntryInfoRsp.parseFrom(
                        new PacketDungeonEntryInfoRsp(point, player).getData());
        assertEquals(Map.of(15, 4300), uints(reply.getDungeonEntryList(0).toByteArray()));
    }

    @Test
    void questAdditionalEntriesUseTheSameNativeFields() throws Exception {
        var point = gson.fromJson("{id:11,dungeonIds:[4300]}", PointData.class);
        var reply =
                DungeonEntryInfoRsp.parseFrom(
                        new PacketDungeonEntryInfoRsp(point, List.of(2021), player).getData());
        assertEquals(2, reply.getDungeonEntryListCount());
        assertEquals(Map.of(15, 4300), uints(reply.getDungeonEntryList(0).toByteArray()));
        assertEquals(Map.of(4, 3, 15, 2021), uints(reply.getDungeonEntryList(1).toByteArray()));
    }

    @Test
    void monsterDetailDescriptorHasAllFourteenNativeFields() {
        var expected =
                Map.ofEntries(
                        Map.entry("is_area_locked", 1),
                        Map.entry("resin", 2),
                        Map.entry("is_alive", 3),
                        Map.entry("refresh_interval", 4),
                        Map.entry("next_boss_chest_refresh_time", 5),
                        Map.entry("boss_chest_num", 6),
                        Map.entry("pos", 7),
                        Map.entry("next_refresh_time", 8),
                        Map.entry("max_boss_chest_num", 9),
                        Map.entry("_map_layer_id", 10),
                        Map.entry("level", 11),
                        Map.entry("_monster_config", 12),
                        Map.entry("weekly_boss_resin_discount_info", 13),
                        Map.entry("_is_respawning", 15));
        var descriptor = _InvestigationMonsterDetail.getDescriptor();
        assertEquals(14, descriptor.getFields().size());
        for (var entry : expected.entrySet())
            assertEquals(
                    entry.getValue(),
                    descriptor.findFieldByName(entry.getKey()).getNumber(),
                    entry.getKey());
        assertTrue(descriptor.getFields().stream().allMatch(field -> field.getNumber() < 50000));
    }

    @Test
    void nativeMonsterClaimCountersShareTheEntryMeaningWithDifferentTags() throws Exception {
        var detail = _InvestigationMonsterDetail.parseFrom(scalars(Map.of(6, 2, 9, 3)));
        assertEquals(2, detail.getBossChestNum());
        assertEquals(3, detail.getMaxBossChestNum());
        assertEquals(
                Map.of(6, 2, 9, 3),
                uints(
                        _InvestigationMonsterDetail.newBuilder()
                                .setBossChestNum(2)
                                .setMaxBossChestNum(3)
                                .build()
                                .toByteArray()));
    }

    @Test
    void nativeMonsterFlagsDoNotDisappearIntoUnknownFields() throws Exception {
        var detail = _InvestigationMonsterDetail.parseFrom(scalars(Map.of(1, 1, 3, 1, 15, 1)));
        assertTrue(detail.getIsAreaLocked());
        assertTrue(detail.getIsAlive());
        assertTrue(detail.getIsRespawning());
        assertEquals(
                Map.of(1, 1, 3, 1, 15, 1),
                uints(
                        _InvestigationMonsterDetail.newBuilder()
                                .setIsAreaLocked(true)
                                .setIsAlive(true)
                                .setIsRespawning(true)
                                .build()
                                .toByteArray()));
    }

    @Test
    void nativeMonsterRefreshTimesDoNotDisappearIntoUnknownFields() throws Exception {
        var expected = Map.of(4, 600, 5, 1700001000, 8, 1700000000);
        var detail = _InvestigationMonsterDetail.parseFrom(scalars(expected));
        assertEquals(600, detail.getRefreshInterval());
        assertEquals(1700001000, detail.getNextBossChestRefreshTime());
        assertEquals(1700000000, detail.getNextRefreshTime());
        assertEquals(
                expected,
                uints(
                        _InvestigationMonsterDetail.newBuilder()
                                .setRefreshInterval(600)
                                .setNextBossChestRefreshTime(1700001000)
                                .setNextRefreshTime(1700000000)
                                .build()
                                .toByteArray()));
    }

    @Test
    void weeklyDiscountAndKnownMonsterFieldsKeepTheirExistingNativeTags() throws Exception {
        var discount =
                WeeklyBossResinDiscountInfo.newBuilder()
                        .setDiscountNum(1)
                        .setDiscountNumLimit(3)
                        .setResinCost(30)
                        .setOriginalResinCost(60)
                        .build();
        assertEquals(Map.of(1, 1, 2, 3, 3, 30, 4, 60), uints(discount.toByteArray()));
        var detail =
                _InvestigationMonsterDetail.newBuilder()
                        .setResin(40)
                        .setLevel(91)
                        .setMapLayerId(3)
                        .setIsAlive(true)
                        .setWeeklyBossResinDiscountInfo(discount)
                        .build();
        assertEquals(Map.of(2, 40, 3, 1, 10, 3, 11, 91), uints(detail.toByteArray()));
        var decoded = _InvestigationMonsterDetail.parseFrom(detail.toByteArray());
        assertEquals(discount, decoded.getWeeklyBossResinDiscountInfo());
    }
}
