package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Message;
import emu.grasscutter.net.proto.GetPlayerTokenRspOuterClass.GetPlayerTokenRsp;
import emu.grasscutter.net.proto.PlayerLoginRspOuterClass.PlayerLoginRsp;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Native-reader merge regressions; expectations are not generated with the Java schema. */
class Protocol72ResponseMergeTest {
    @Test
    void duplicateTokenNestedExplicitZeroClearsTheEarlierValue() throws Exception {
        // Existing response-graph fixture token_nested_explicit_zero:
        // native NNK first has child1=9, child2=5; second child1=0 overwrites 9.
        var response = GetPlayerTokenRsp.parseFrom(hex("b21c0408091005b21c02080070f9ffffffffffffffff01"));
        var nested = (Message) field(response, 454);
        assertEquals(0, field(nested, 1));
        assertEquals(5, field(nested, 2));
        assertEquals(-7, field(response, 14));
        assertTrue(nested.hasField(nested.getDescriptorForType().findFieldByNumber(1)));
    }

    @Test
    void omittedNestedFieldsKeepEarlierValuesDuringNativeMerge() throws Exception {
        // Existing response-graph fixture token_nested_merge, including uint32 300.
        var response = GetPlayerTokenRsp.parseFrom(hex("b21c0808ac0210011a0141b21c051002220142"));
        var nested = (Message) field(response, 454);
        assertEquals(300, field(nested, 1));
        assertEquals(2, field(nested, 2));
        assertEquals("A", field(nested, 3));
        assertEquals("B", field(nested, 4));
    }

    @Test
    void sameOneofMessageMergePreservesUint64AndClearsExplicitFalse() throws Exception {
        // Existing response-graph fixture oneof_same_message_merge. Native child
        //2=false overwrites true while uint64 9007199254740993 remains exact.
        var response = PlayerLoginRsp.parseFrom(hex("b21d0f1a0d620b0881808080808080101001b21d081a06620410001809"));
        var hml = (Message) field(response, 470);
        var choice = (Message) field(hml, 3);
        var child = (Message) field(choice, 12);
        assertEquals(9007199254740993L, field(child, 1));
        assertEquals(false, field(child, 2));
        assertEquals(9, field(child, 3));
        assertTrue(child.hasField(child.getDescriptorForType().findFieldByNumber(2)));
    }

    @Test
    void resourceDuplicateExplicitZerosAndEmptyStringsClearAllEarlierValues() throws Exception {
        // New fixed-target GraphReader72 run, recorded in local analysis as
        // f02b-response-merge-native.json: native MDKG reader 149EC4B80 enters
        // twice and all seven fields become zero/false/empty on the same object.
        var response = PlayerLoginRsp.parseFrom(hex("ea0313082a10011a014d2201532a01563201423a014e"
                + "ea030e080010001a0022002a0032003a00"));
        var nested = (Message) field(response, 61);
        assertEquals(0, field(nested, 1));
        assertEquals(false, field(nested, 2));
        for (int tag = 3; tag <= 7; tag++) assertEquals("", field(nested, tag));
        for (int tag = 1; tag <= 7; tag++) {
            assertTrue(nested.hasField(nested.getDescriptorForType().findFieldByNumber(tag)));
        }
    }

    private static Object field(Message message, int tag) {
        return message.getField(message.getDescriptorForType().findFieldByNumber(tag));
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
