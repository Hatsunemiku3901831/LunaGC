package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.net.proto.Client72M0EntityRawOuterClass.*;
import emu.grasscutter.net.proto.Client72M0SceneTeamRawOuterClass.Raw76429;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class M0EntityRawWireTest {
    @ParameterizedTest
    @CsvSource({"0,00", "1,01", "4951,d726", "2147483648,8080808008", "4294967295,ffffffff0f"})
    void rootOwnedScalarVectorsRetainUnsignedBits(long value, String varint) throws Exception {
        var root = NAALIELJILH.parseFrom(hex("08" + varint + "48" + varint));
        assertTrue(root.hasOJPFPILNCIC());
        assertTrue(root.hasBBHDLEGOLPI());
        assertEquals(value, Integer.toUnsignedLong(root.getOJPFPILNCIC()));
        assertEquals(value, Integer.toUnsignedLong(root.getBBHDLEGOLPI()));
        assertArrayEquals(hex("08" + varint + "48" + varint), root.toByteArray());
    }

    @Test
    void independentChildLiteralBindsOnlyEstablishedScalarTags() throws Exception {
        byte[] bytes = hex("080110d7261a026d303801880101900102d00103");
        var child = IJCEPPNOCNM.parseFrom(bytes);
        assertEquals(1, child.getMCPCJJBMHMK());
        assertEquals(0x1357, child.getLLHEBIHGDAE());
        assertEquals("m0", child.getOKDLHHPJOLA());
        assertEquals(1, child.getDFENDOGMEAD());
        assertEquals(1, child.getCCJOFDMPEEK());
        assertEquals(2, child.getLKHOHNGMBDM());
        assertEquals(3, child.getPNLLENFDEFH());
        assertArrayEquals(bytes, child.toByteArray());
    }

    @ParameterizedTest
    @CsvSource({"0,00", "1,01", "2147483648,8080808008", "4294967295,ffffffff0f"})
    void allSixChildScalarsKeepZeroAndHighBits(long value, String varint) throws Exception {
        String bytes = "08" + varint + "10" + varint + "38" + varint
                + "8801" + varint + "9001" + varint + "d001" + varint;
        var child = IJCEPPNOCNM.parseFrom(hex(bytes));
        for (int actual : new int[] {child.getMCPCJJBMHMK(), child.getLLHEBIHGDAE(),
                child.getDFENDOGMEAD(), child.getCCJOFDMPEEK(), child.getLKHOHNGMBDM(), child.getPNLLENFDEFH()})
            assertEquals(value, Integer.toUnsignedLong(actual));
        assertArrayEquals(hex(bytes), child.toByteArray());
    }

    @Test
    void repeatedScalarLastValuePresenceAndMalformedInputHaveExplicitBoundaries() throws Exception {
        var repeated = NAALIELJILH.parseFrom(hex("08010800480c"));
        assertEquals(0, repeated.getOJPFPILNCIC());
        assertTrue(repeated.hasOJPFPILNCIC());
        assertEquals(12, repeated.getBBHDLEGOLPI());
        assertFalse(NAALIELJILH.getDefaultInstance().hasOJPFPILNCIC());
        assertFalse(IJCEPPNOCNM.getDefaultInstance().hasOKDLHHPJOLA());
        assertTrue(IJCEPPNOCNM.parseFrom(hex("1a00")).hasOKDLHHPJOLA());
        for (String invalid : List.of("08", "4880", "08808080808080808080808000", "00"))
            assertThrows(InvalidProtocolBufferException.class, () -> NAALIELJILH.parseFrom(hex(invalid)));
        for (String invalid : List.of("1a026d", "1a01ff", "8801", "d00180"))
            assertThrows(InvalidProtocolBufferException.class, () -> IJCEPPNOCNM.parseFrom(hex(invalid)));
    }

    @Test
    void partialSchemaCannotMasqueradeAsCompleteRootOrAvatarGraph() {
        assertEquals(Set.of(1, 2, 9), NAALIELJILH.getDescriptor().getFields().stream()
                .map(f -> f.getNumber()).collect(Collectors.toSet()));
        var list = NAALIELJILH.getDescriptor().findFieldByNumber(2);
        assertTrue(list.isRepeated());
        assertEquals(IJCEPPNOCNM.getDescriptor(), list.getMessageType());
        assertEquals(Set.of(1, 2, 3, 4, 7, 10, 17, 18, 26), IJCEPPNOCNM.getDescriptor().getFields().stream()
                .map(f -> f.getNumber()).collect(Collectors.toSet()));
        assertEquals(1, IJCEPPNOCNM.getDescriptor().getOneofs().get(0).getFieldCount());
    }

    @ParameterizedTest
    @CsvSource({"0,00", "1,01", "2147483648,8080808008", "4294967295,ffffffff0f"})
    void avatarOneofLocalUidAndTeamUidRetainTheIndependentTokenUidBits(long uid, String varint) throws Exception {
        byte[] avatar = hex("08" + varint);
        var child = IJCEPPNOCNM.parseFrom(hex("080152" + HexFormat.of().toHexDigits((byte) avatar.length) + "08" + varint));
        assertEquals(1, child.getMCPCJJBMHMK());
        assertEquals(IJCEPPNOCNM.RawAvatarBodyCase.RAW_AVATAR_10, child.getRawAvatarBodyCase());
        assertEquals(uid, Integer.toUnsignedLong(child.getRawAvatar10().getPJAJDHBBMGB()));
        assertEquals(uid, Integer.toUnsignedLong(Raw76429.parseFrom(hex("18" + varint)).getHCIEHNNJNCF()));
        assertFalse(child.getRawAvatar10().hasFBLKPHOCBIK(), "this UID-only fixture has no GUID field");
    }

    @Test
    void independentAvatarOneofKeepsUidAndHighGuidAsDifferentFields() throws Exception {
        byte[] bytes = hex("520b0807188180808080808010");
        var child = IJCEPPNOCNM.parseFrom(bytes);
        assertEquals(7, child.getRawAvatar10().getPJAJDHBBMGB());
        assertEquals(9007199254740993L, child.getRawAvatar10().getFBLKPHOCBIK());
        assertArrayEquals(bytes, child.toByteArray());
    }

    @Test
    void rootListLiteralRetainsTwoOrderedAvatarGraphsAndBothHighGuids() throws Exception {
        byte[] bytes = hex("1217080122060a0012001a00520b0807188180808080808010"
                + "1219080122060a0012001a00520d080818ffffffffffffffffff01480c");
        var root = NAALIELJILH.parseFrom(bytes);
        assertEquals(2, root.getNKBMECCPKPJCount());
        assertEquals(12, root.getBBHDLEGOLPI());
        assertFalse(root.hasOJPFPILNCIC());
        var first = root.getNKBMECCPKPJ(0);
        var second = root.getNKBMECCPKPJ(1);
        assertEquals(7, first.getRawAvatar10().getPJAJDHBBMGB());
        assertEquals(9007199254740993L, first.getRawAvatar10().getFBLKPHOCBIK());
        assertEquals(8, second.getRawAvatar10().getPJAJDHBBMGB());
        assertEquals(-1L, second.getRawAvatar10().getFBLKPHOCBIK());
        for (var child : root.getNKBMECCPKPJList()) {
            assertEquals(1, child.getMCPCJJBMHMK());
            assertFalse(child.hasLLHEBIHGDAE());
            assertTrue(child.getRawMotion4().hasHHAFHFEPLPD());
            assertTrue(child.getRawMotion4().hasHPLBLGJLIFF());
            assertTrue(child.getRawMotion4().hasDACMFIDFIJA());
        }
        assertArrayEquals(bytes, root.toByteArray());
    }

    @Test
    void listAppendEmptyMessageAndAbsentListRemainDifferent() throws Exception {
        assertEquals(0, NAALIELJILH.getDefaultInstance().getNKBMECCPKPJCount());
        var empty = NAALIELJILH.parseFrom(hex("120012020801"));
        assertEquals(2, empty.getNKBMECCPKPJCount());
        assertEquals(IJCEPPNOCNM.getDefaultInstance(), empty.getNKBMECCPKPJ(0));
        assertEquals(1, empty.getNKBMECCPKPJ(1).getMCPCJJBMHMK());
        assertArrayEquals(hex("120012020801"), empty.toByteArray());
    }

    @Test
    void wrongListWireAndDifferentFieldDoNotSelectTheTypedList() throws Exception {
        for (String literal : List.of("1001", "1a00")) {
            var root = NAALIELJILH.parseFrom(hex(literal));
            assertEquals(0, root.getNKBMECCPKPJCount());
            assertFalse(root.getUnknownFields().asMap().isEmpty());
            assertArrayEquals(hex(literal), root.toByteArray());
        }
    }

    @Test
    void truncatedListLengthsAndInvalidNestedChildrenAreRejected() {
        for (String literal : List.of("12", "1280", "120108", "12025201", "120100",
                "120322020a"))
            assertThrows(InvalidProtocolBufferException.class, () -> NAALIELJILH.parseFrom(hex(literal)));
    }

    private static byte[] hex(String input) { return HexFormat.of().parseHex(input); }
}
