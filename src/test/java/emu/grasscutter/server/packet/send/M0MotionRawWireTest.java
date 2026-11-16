package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.net.proto.Client72M0EntityRawOuterClass.IJCEPPNOCNM;
import emu.grasscutter.net.proto.Client72M0MotionRawOuterClass.Raw49847;
import emu.grasscutter.net.proto.Client72SceneHandshakeNestedOuterClass.BGDCNKAKGHI;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class M0MotionRawWireTest {
    @Test
    void fourDistinctVectorSlotsReuseTheApprovedFloatWire() throws Exception {
        // x=1.25, y=-2.5, z=3.75; each component has an independent fixed32 literal.
        byte[] bytes = hex("0a050d0000a03f120515000020c01a051d00007040320f0d0000a03f15000020c01d00007040");
        var motion = Raw49847.parseFrom(bytes);
        assertEquals(1.25f, motion.getHHAFHFEPLPD().getCJMJBINEBAG());
        assertEquals(-2.5f, motion.getHPLBLGJLIFF().getALOKJBKPLGA());
        assertEquals(3.75f, motion.getDACMFIDFIJA().getMGICHKNKDKO());
        assertEquals(1.25f, motion.getDFDHCAIKMAI().getCJMJBINEBAG());
        assertEquals(-2.5f, motion.getDFDHCAIKMAI().getALOKJBKPLGA());
        assertEquals(3.75f, motion.getDFDHCAIKMAI().getMGICHKNKDKO());
        assertArrayEquals(bytes, motion.toByteArray());
    }

    @ParameterizedTest
    @CsvSource({"0,00", "1,01", "4951,d726", "2147483648,8080808008", "4294967295,ffffffff0f"})
    void allFiveU32FieldsPreserveTheUnsignedRawBits(long value, String varint) throws Exception {
        byte[] bytes = hex("20" + varint + "38" + varint + "40" + varint + "50" + varint + "58" + varint);
        var motion = Raw49847.parseFrom(bytes);
        for (int actual : new int[] {motion.getFAJGLPCKIBH(), motion.getADMHCDNJLAI(),
                motion.getBBCBHMNKDIB(), motion.getEEILJPNIHDL(), motion.getKMMLNDMEIDD()})
            assertEquals(value, Integer.toUnsignedLong(actual));
        assertArrayEquals(bytes, motion.toByteArray());
    }

    @Test
    void u64ScalarKeepsHighAndMaximumValuesWithoutFloatingPointConversion() throws Exception {
        assertEquals(9007199254740993L, Raw49847.parseFrom(hex("488180808080808010")).getMIMLOPKLBEH());
        assertEquals(Long.MIN_VALUE + 1, Raw49847.parseFrom(hex("4881808080808080808001")).getMIMLOPKLBEH());
        byte[] maximum = hex("48ffffffffffffffffff01");
        assertEquals(-1L, Raw49847.parseFrom(maximum).getMIMLOPKLBEH());
        assertArrayEquals(maximum, Raw49847.parseFrom(maximum).toByteArray());
    }

    @Test
    void repeatedVectorsRetainOrderAndDistinctElements() throws Exception {
        byte[] bytes = hex("2a050d0000803f2a0515000000c0");
        var motion = Raw49847.parseFrom(bytes);
        assertEquals(2, motion.getBCOOEGCDHKKCount());
        assertEquals(1f, motion.getBCOOEGCDHKK(0).getCJMJBINEBAG());
        assertEquals(-2f, motion.getBCOOEGCDHKK(1).getALOKJBKPLGA());
        assertFalse(motion.getBCOOEGCDHKK(0).hasALOKJBKPLGA());
        assertArrayEquals(bytes, motion.toByteArray());
    }

    @Test
    void requiredPointerStructureIsSeparateFromUnknownVectorBusinessRoles() throws Exception {
        var child = IJCEPPNOCNM.parseFrom(hex("0801220712050d0000803f520408071809"));
        assertTrue(child.hasRawMotion4());
        assertTrue(child.getRawMotion4().hasHPLBLGJLIFF());
        assertEquals(1f, child.getRawMotion4().getHPLBLGJLIFF().getCJMJBINEBAG());
        assertEquals(7, child.getRawAvatar10().getPJAJDHBBMGB());
        assertEquals(9L, child.getRawAvatar10().getFBLKPHOCBIK());
        assertFalse(IJCEPPNOCNM.getDefaultInstance().hasRawMotion4());
        assertFalse(Raw49847.getDefaultInstance().hasHPLBLGJLIFF());
        assertTrue(Raw49847.parseFrom(hex("1200")).hasHPLBLGJLIFF());
    }

    @Test
    void vectorMergesRetainExplicitZeroAndIeee754Bits() throws Exception {
        var motion = Raw49847.parseFrom(hex("12050d0000803f12051500000000"));
        assertEquals(1f, motion.getHPLBLGJLIFF().getCJMJBINEBAG());
        assertTrue(motion.getHPLBLGJLIFF().hasALOKJBKPLGA());
        assertEquals(0f, motion.getHPLBLGJLIFF().getALOKJBKPLGA());
        var negativeZero = BGDCNKAKGHI.parseFrom(hex("0d00000080"));
        assertEquals(0x80000000, Float.floatToRawIntBits(negativeZero.getCJMJBINEBAG()));
        assertArrayEquals(hex("0d00000080"), negativeZero.toByteArray());
    }

    @Test
    void undeclaredCodecAndMalformedWireCannotAcquireBusinessMeaning() throws Exception {
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), Raw49847.getDescriptor().getFields().stream()
                .map(f -> f.getNumber()).collect(Collectors.toSet()));
        var unknown = Raw49847.parseFrom(hex("a00601"));
        assertTrue(unknown.getUnknownFields().hasField(100));
        for (String invalid : List.of("12", "12010d", "2a050d000000", "48", "20", "00"))
            assertThrows(InvalidProtocolBufferException.class, () -> Raw49847.parseFrom(hex(invalid)));
    }

    private static byte[] hex(String input) { return HexFormat.of().parseHex(input); }
}
