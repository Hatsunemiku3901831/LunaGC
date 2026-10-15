package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.codex.CodexAnimalData;
import emu.grasscutter.data.excels.codex.CodexAnimalData.CountType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.QueryCodexMonsterBeKilledNumReqOuterClass.QueryCodexMonsterBeKilledNumReq;
import emu.grasscutter.net.proto.QueryCodexMonsterBeKilledNumRspOuterClass.QueryCodexMonsterBeKilledNumRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerQueryCodexMonsterBeKilledNumReq;
import emu.grasscutter.server.packet.send.PacketQueryCodexMonsterBeKilledNumRsp;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;

/** Current client type 20894 consumes equally sized uint32 lists at fields 14, 10 and 15. */
class AnimalCodexCountsProtocol71Test {
    private static List<CodexAnimalData> rows;
    private static int killId;
    private static int captureId;
    private AbilityTestFixture fixture;
    private Map<Integer, CodexAnimalData> previous;

    @BeforeAll
    static void resources() throws Exception {
        rows =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AnimalCodexExcelConfigData.json"),
                        CodexAnimalData.class);
        killId =
                rows.stream()
                        .filter(row -> row.getCountType() == CountType.CODEX_COUNT_TYPE_KILL)
                        .findFirst()
                        .orElseThrow()
                        .getId();
        captureId =
                rows.stream()
                        .filter(row -> row.getCountType() == CountType.CODEX_COUNT_TYPE_CAPTURE)
                        .findFirst()
                        .orElseThrow()
                        .getId();
        assertNotEquals(killId, captureId);
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        previous = new HashMap<>(GameData.getCodexAnimalDataMap());
        GameData.getCodexAnimalDataMap().clear();
        for (var row : rows) GameData.getCodexAnimalDataMap().put(row.getId(), row);
        counts().put(killId, 7);
        counts().put(captureId, 11);
    }

    @AfterEach
    void restore() {
        GameData.getCodexAnimalDataMap().clear();
        GameData.getCodexAnimalDataMap().putAll(previous);
        fixture.close();
    }

    private Map<Integer, Integer> counts() {
        return fixture.player.getCodex().getUnlockedAnimal();
    }

    private BasePacket packet(Integer... ids) {
        return new PacketQueryCodexMonsterBeKilledNumRsp(fixture.player, Arrays.asList(ids));
    }

    private Map<Integer, List<Integer>> wire(BasePacket packet) throws Exception {
        assertEquals(20558, packet.getOpcode());
        var fields = new HashMap<Integer, List<Integer>>();
        var input = CodedInputStream.newInstance(packet.getData());
        while (!input.isAtEnd()) {
            int tag = input.readTag(), field = tag >>> 3;
            if (field != 10 && field != 14 && field != 15) {
                input.skipField(tag);
                continue;
            }
            var values = fields.computeIfAbsent(field, key -> new ArrayList<>());
            if ((tag & 7) == 2) {
                int limit = input.pushLimit(input.readRawVarint32());
                while (input.getBytesUntilLimit() > 0) values.add(input.readUInt32());
                input.popLimit(limit);
            } else {
                assertEquals(0, tag & 7);
                values.add(input.readUInt32());
            }
        }
        return fields;
    }

    private List<Integer> values(Map<Integer, List<Integer>> fields, int field) {
        return fields.getOrDefault(field, List.of());
    }

    private void aligned(BasePacket packet, List<Integer> ids, List<Long> totals) throws Exception {
        var fields = wire(packet);
        assertEquals(ids, values(fields, 14));
        var first = values(fields, 10);
        var second = values(fields, 15);
        assertEquals(ids.size(), first.size(), "first statistic must match the ID list");
        assertEquals(ids.size(), second.size(), "second statistic must match the ID list");
        // This is the indexing contract at native 0x14f66e160, not a generated-proto round trip.
        var actual = new ArrayList<Long>();
        for (int index = 0; index < ids.size(); index++) {
            assertTrue(first.get(index) >= 0);
            assertTrue(second.get(index) >= 0);
            actual.add((long) first.get(index) + second.get(index));
        }
        assertEquals(totals, actual);
    }

    @Test
    void a_single_unlocked_monster_has_both_statistics() throws Exception {
        var response = packet(killId);
        aligned(response, List.of(killId), List.of(7L));
        var fields = wire(response);
        assertEquals(List.of(7), values(fields, 10));
        assertEquals(List.of(0), values(fields, 15));
    }

    @Test
    void a_capture_record_also_has_both_statistics() throws Exception {
        var response = packet(captureId);
        aligned(response, List.of(captureId), List.of(11L));
        var fields = wire(response);
        assertEquals(List.of(0), values(fields, 10));
        assertEquals(List.of(11), values(fields, 15));
    }

    @Test
    void mixed_records_keep_the_requested_order() throws Exception {
        aligned(packet(captureId, killId), List.of(captureId, killId), List.of(11L, 7L));
    }

    @Test
    void repeated_ids_keep_matching_statistics_at_every_position() throws Exception {
        aligned(
                packet(killId, captureId, killId),
                List.of(killId, captureId, killId),
                List.of(7L, 11L, 7L));
    }

    @Test
    void omitting_locked_ids_cannot_misalign_the_remaining_entries() throws Exception {
        aligned(
                packet(0, captureId, Integer.MAX_VALUE, killId),
                List.of(captureId, killId),
                List.of(11L, 7L));
    }

    @Test
    void zero_counts_still_occupy_both_array_positions() throws Exception {
        counts().put(killId, 0);
        aligned(packet(killId), List.of(killId), List.of(0L));
    }

    @Test
    void a_legacy_null_counter_has_a_safe_zero_snapshot() throws Exception {
        counts().put(killId, null);
        aligned(packet(killId), List.of(killId), List.of(0L));
    }

    @Test
    void a_legacy_negative_counter_is_not_encoded_as_a_huge_unsigned_count() throws Exception {
        counts().put(killId, -7);
        aligned(packet(killId), List.of(killId), List.of(0L));
    }

    @Test
    void large_valid_counts_keep_their_full_value() throws Exception {
        counts().put(killId, Integer.MAX_VALUE);
        aligned(packet(killId), List.of(killId), List.of((long) Integer.MAX_VALUE));
    }

    @Test
    void an_empty_query_has_three_empty_lists() throws Exception {
        aligned(packet(), List.of(), List.of());
    }

    @Test
    void locked_only_queries_do_not_invent_unlocks() throws Exception {
        aligned(packet(0, Integer.MAX_VALUE), List.of(), List.of());
    }

    @Test
    void building_a_response_does_not_change_saved_progress() {
        counts().put(killId, null);
        var before = new HashMap<>(counts());
        int sent = fixture.player.packets.size();
        packet(killId, captureId);
        assertEquals(before, counts());
        assertEquals(sent, fixture.player.packets.size());
    }

    @Test
    void the_descriptor_has_the_verified_fields_instead_of_placeholder_numbers() {
        var fields = QueryCodexMonsterBeKilledNumRsp.getDescriptor().getFields();
        assertEquals(
                Set.of(2, 10, 14, 15),
                new HashSet<>(fields.stream().map(field -> field.getNumber()).toList()));
        for (var field : fields)
            if (field.getNumber() != 2) {
                assertTrue(field.isRepeated());
                assertEquals(
                        com.google.protobuf.Descriptors.FieldDescriptor.Type.UINT32,
                        field.getType());
            }
    }

    @Test
    void the_request_handler_emits_a_response_safe_for_native_indexing() throws Exception {
        var sent = new ArrayList<BasePacket>();
        var session =
                new GameSession(fixture.scene.getWorld().getServer()) {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public void send(BasePacket packet) {
                        sent.add(packet);
                    }
                };
        var request =
                QueryCodexMonsterBeKilledNumReq.newBuilder()
                        .addCodexIdList(captureId)
                        .addCodexIdList(0)
                        .addCodexIdList(killId)
                        .build();
        new HandlerQueryCodexMonsterBeKilledNumReq()
                .handle(session, new byte[0], request.toByteArray());
        assertEquals(1, sent.size());
        assertEquals(PacketOpcodes.QueryCodexMonsterBeKilledNumRsp, sent.get(0).getOpcode());
        aligned(sent.get(0), List.of(captureId, killId), List.of(11L, 7L));
    }
}
