package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.net.proto.Client72M0SceneAvatarRawOuterClass.FKDEDKIHNDA;
import emu.grasscutter.net.proto.Client72M0SceneTeamRawOuterClass.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class M0SceneRawGraphWireTest {
    @Test
    void thirteenIndependentScalarKeysRetainUint32AndUint64UpperBounds() throws Exception {
        byte[] input = hex("08ffffffff0f10ffffffff0f18ffffffffffffffffff0120ffffffff0f30ffffffff0f58ffffffff0f8801ffffffff0f9001ffffffff0f9801ffffffff0fb001ffffffff0fb801ffffffff0fc001ffffffff0fd001ffffffff0f");
        var avatar = FKDEDKIHNDA.parseFrom(input);
        assertEquals(13, avatar.getAllFields().size());
        for (var field : avatar.getAllFields().entrySet()) {
            if (field.getKey().getNumber() == 3) assertEquals(-1L, field.getValue());
            else assertEquals(-1, field.getValue());
        }
        assertArrayEquals(input, avatar.toByteArray());
    }

    @Test
    void packedAndUnpackedU32MapsAndApprovedNestedChildrenRemainIndependent() throws Exception {
        var avatar = FKDEDKIHNDA.parseFrom(hex("2a070001ffffffff0f28076a04080210037204080410057a020809aa010b08ffffffffffffffffff01ca01020807fa010b0808208180808080808010"));
        assertEquals(List.of(0, 1, -1, 7), avatar.getDIIEILGCADPList());
        assertEquals(Map.of(2, 3), avatar.getPJGIPBJMBOPMap());
        assertEquals(Map.of(4, 5), avatar.getJOJLFAOOLMHMap());
        assertEquals(9, avatar.getCDOAFDGJGDF(0).getJPCOIFLAAED());
        assertEquals(-1L, avatar.getBNHMCGLAMLE().getOHPFCLOBJEH());
        assertEquals(7, avatar.getCKDDHADKCBK().getEPCMGLPMKLC());
        assertEquals(8, avatar.getAMLLHJJIMNI(0).getLLHEBIHGDAE());
        assertEquals(9007199254740993L, avatar.getAMLLHJJIMNI(0).getFBLKPHOCBIK());
        assertEquals(avatar, FKDEDKIHNDA.parseFrom(avatar.toByteArray()));
    }

    @Test
    void nestedRowsKeepTwoGuidsEntityIdsFixed32AbilitiesAndHighTags() throws Exception {
        byte[] input = hex("20002a550a09188180808080808010220e0a0c080315123456781d87654321280232020801388180808080808010420208004801500358ffffffffffffffffff016204080110116a020809720208077800a82201aa280208082a0b2809588180808080808010480160ffffffffffffffffff01");
        var root = Raw83228.parseFrom(input);
        assertTrue(root.hasRawContext28());
        assertEquals(0, root.getRawContext28());
        assertEquals(-1, root.getRawEnum20());
        assertTrue(root.getRawFlag24());
        assertEquals(2, root.getRawRows18Count());
        var row = root.getRawRows18(0);
        assertEquals(9007199254740993L, row.getEIHPIGNKAPD().getFBLKPHOCBIK());
        assertEquals(9007199254740993L, row.getNPKKCHABEJH());
        assertEquals(-1L, row.getOKMEHFHNFCG());
        assertEquals(2, row.getIEDJNHILJBF());
        assertEquals(1, row.getLLHEBIHGDAE());
        assertEquals(3, row.getJMHKHAPJADA());
        assertEquals(17, row.getMPHLKPODDHK().getLLHEBIHGDAE());
        assertEquals(7, row.getMMCFMLLFAEE().getNLNJDCDLHHE());
        assertTrue(row.getCCODNLIKNGM().getFAGKAADHKDI());
        assertFalse(row.getLBJMODOPOCP().getFAGKAADHKDI());
        assertTrue(row.getLBJMODOPOCP().hasFAGKAADHKDI());
        var leaf = row.getEOFFDMLNFIM().getGFDEDOJEICE(0);
        assertEquals(3, leaf.getDOBGMPBDLKF());
        assertEquals(0x78563412, leaf.getEMBPDEHFOJK());
        assertEquals(0x21436587, leaf.getOPKFBNFODJM());
        assertTrue(row.getOJLCDFKMPLH());
        assertTrue(row.hasBCMCOBDAMKM());
        assertFalse(row.getBCMCOBDAMKM());
        assertEquals(8, row.getJOBBNEKLCOF(0).getLLHEBIHGDAE());
        assertEquals(9007199254740993L, root.getRawRows18(1).getOKMEHFHNFCG());
        assertEquals(9, root.getRawRows18(1).getIEDJNHILJBF());
        assertArrayEquals(input, root.toByteArray());
    }

    @ParameterizedTest
    @CsvSource({"6800,false", "6801,true", "6802,true", "688080808008,true", "68ffffffff0f,true"})
    void nativeBoolVectorsHaveExplicitPresenceAndNormalizedValue(String wire, boolean expected) throws Exception {
        var root = Raw86588.parseFrom(hex(wire));
        assertTrue(root.hasRawFlag20());
        assertEquals(expected, root.getRawFlag20());
        assertArrayEquals(hex(expected ? "6801" : "6800"), root.toByteArray());
    }

    @Test
    void truncationAndWrongLengthAreJavaParserErrorsWithoutInventedNativeClaims() {
        for (String bytes : List.of("2a0208", "2a01", "60ffffffffffffffffff", "2affffffff07"))
            assertThrows(InvalidProtocolBufferException.class, () -> Raw83228.parseFrom(hex(bytes)));
        for (String bytes : List.of("2a0180", "6a030802", "1880", "aa01ff"))
            assertThrows(InvalidProtocolBufferException.class, () -> FKDEDKIHNDA.parseFrom(hex(bytes)));
    }

    @Test
    void unknownInnerTypesAndCctorOnlyRootListAreNotDeclaredAsComplete() {
        var fields = FKDEDKIHNDA.getDescriptor().getFields().stream()
                .map(f -> f.getNumber()).collect(Collectors.toSet());
        assertEquals(28, fields.size());
        assertTrue(Collections.disjoint(fields, Set.of(9, 20, 28)));
        assertEquals(Set.of(13), Raw86588.getDescriptor().getFields().stream()
                .map(f -> f.getNumber()).collect(Collectors.toSet()));
        assertEquals(17, Raw76429.getDescriptor().getFields().size());
        assertEquals(Set.of(4, 5, 9, 12), Raw83228.getDescriptor().getFields().stream()
                .map(f -> f.getNumber()).collect(Collectors.toSet()));
    }

    private static byte[] hex(String input) { return HexFormat.of().parseHex(input); }
}
