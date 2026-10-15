package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Message;

import emu.grasscutter.net.packet.PacketOpcodes;

import org.junit.jupiter.api.Test;

import java.util.*;

/** Golden bytes from the current client's request writers and response/notify readers. */
class ArtifactOfferProtocolTest {
    Message parse(String name, String hex) {
        try {
            var type = Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name);
            var bytes = HexFormat.of().parseHex(hex);
            var result =
                    (Message)
                            type.getMethod("parseFrom", byte[].class).invoke(null, (Object) bytes);
            assertArrayEquals(bytes, result.toByteArray());
            return result;
        } catch (ReflectiveOperationException error) {
            return fail("Current artifact message is unavailable: " + name, error);
        }
    }

    Object field(Message message, String name) {
        var field = message.getDescriptorForType().findFieldByName(name);
        assertNotNull(field, name);
        return message.getField(field);
    }

    @Test
    void extractRequestHasSeparateGuidAndMaterialLists() {
        var message = parse("ReliquaryOfferExtractReq", "08bc052a04ac02ad02420608abb4061002");
        assertEquals(700, field(message, "schedule_id"));
        assertEquals(List.of(300L, 301L), field(message, "reliquary_guid_list"));
        var items = (List<?>) field(message, "item_list");
        assertEquals(1, items.size());
        assertEquals(105003, field((Message) items.get(0), "item_id"));
        assertEquals(2, field((Message) items.get(0), "count"));
    }

    @Test
    void extractResponseEchoesWhatWasConsumedAndTheError() {
        var message = parse("ReliquaryOfferExtractRsp", "1a0608abb406100228083204ac02ad0260bc05");
        assertEquals(700, field(message, "schedule_id"));
        assertEquals(8, field(message, "retcode"));
        assertEquals(List.of(300L, 301L), field(message, "reliquary_guid_list"));
        assertEquals(1, ((List<?>) field(message, "item_list")).size());
    }

    @Test
    void defineRequestKeepsMainSubstatsSlotAndPurchaseIdDistinct() {
        var message = parse("ReliquaryOfferDefineReq", "08141a02161c4003480250bc05");
        assertEquals(20, field(message, "main_prop_type"));
        assertEquals(List.of(22, 28), field(message, "append_prop_type_list"));
        assertEquals(3, field(message, "equip_type"));
        assertEquals(2, field(message, "purchase_id"));
        assertEquals(700, field(message, "schedule_id"));
    }

    @Test
    void defineResponseContainsTheCurrentErrorField() {
        var message = parse("ReliquaryOfferDefineRsp", "080218bc055808");
        assertEquals(2, field(message, "purchase_id"));
        assertEquals(700, field(message, "schedule_id"));
        assertEquals(8, field(message, "retcode"));
    }

    @Test
    void stateNotifyPreservesFractionalProgressAndDefinitionCounts() {
        var message =
                parse("ReliquaryOfferDataNotify", "28bc05380140d00f550000003f58016a056099756801");
        assertEquals(700, field(message, "schedule_id"));
        assertEquals(1, field(message, "progress"));
        assertEquals(0.5f, field(message, "fractional_progress"));
        assertEquals(2000, field(message, "cycle_end_time"));
        assertEquals(1, field(message, "extracted_count"));
        var counts = (List<?>) field(message, "define_count_list");
        assertEquals(1, counts.size());
        assertEquals(15001, field((Message) counts.get(0), "define_id"));
        assertEquals(1, field((Message) counts.get(0), "count"));
    }

    @Test
    void descriptiveOpcodeAliasesRetainTheVerifiedNativeNumbers() throws Exception {
        var ids =
                Map.of(
                        "ReliquaryOfferExtractReq",
                        1781,
                        "ReliquaryOfferExtractRsp",
                        24,
                        "ReliquaryOfferDefineReq",
                        22095,
                        "ReliquaryOfferDefineRsp",
                        3259,
                        "ReliquaryOfferDataNotify",
                        24434,
                        "ReliquaryOfferCompanionNotify",
                        228);
        for (var entry : ids.entrySet()) {
            assertEquals(
                    entry.getValue(), PacketOpcodes.class.getField(entry.getKey()).getInt(null));
        }
    }
}
