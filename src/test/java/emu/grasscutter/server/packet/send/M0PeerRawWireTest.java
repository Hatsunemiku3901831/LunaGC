package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.net.proto.Client72M0PeerRawOuterClass.IAKGECCPGDA;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class M0PeerRawWireTest {
    @ParameterizedTest
    @CsvSource({"0,00", "1,01", "4951,d726", "2147483648,8080808008", "4294967295,ffffffff0f"})
    void allFourIndependentOwnedUint32VectorsPreserveBits(long value, String varint) throws Exception {
        byte[] bytes = hex("20" + varint + "50" + varint + "58" + varint + "60" + varint);
        var root = IAKGECCPGDA.parseFrom(bytes);
        for (int actual : new int[] {root.getOHNANNOOMCJ(), root.getAANCEPCPHKJ(), root.getMCCMGLDOECH(), root.getOHAKDDILKKB()})
            assertEquals(value, Integer.toUnsignedLong(actual));
        assertEquals(4, root.getAllFields().size());
        assertArrayEquals(bytes, root.toByteArray());
    }

    @Test
    void distinctKeysLastValueAndAbsentUnknownBusinessFieldsHaveSeparatePresence() throws Exception {
        var root = IAKGECCPGDA.parseFrom(hex("2001500258036004"));
        assertEquals(1, root.getOHNANNOOMCJ());
        assertEquals(2, root.getAANCEPCPHKJ());
        assertEquals(3, root.getMCCMGLDOECH());
        assertEquals(4, root.getOHAKDDILKKB());
        var known = IAKGECCPGDA.parseFrom(hex("500158025000"));
        assertEquals(0, known.getAANCEPCPHKJ());
        assertTrue(known.hasAANCEPCPHKJ());
        assertFalse(known.hasOHNANNOOMCJ());
        assertFalse(known.hasOHAKDDILKKB());
        assertEquals(Set.of(4, 10, 11, 12), IAKGECCPGDA.getDescriptor().getFields().stream()
                .map(f -> f.getNumber()).collect(Collectors.toSet()));
    }

    @Test
    void truncatedAndMalformedJavaWireCannotProduceAValidIdentityObject() {
        for (String bytes : List.of("50", "5880", "60808080808080808080808000", "00"))
            assertThrows(InvalidProtocolBufferException.class, () -> IAKGECCPGDA.parseFrom(hex(bytes)));
    }

    private static byte[] hex(String input) { return HexFormat.of().parseHex(input); }
}
