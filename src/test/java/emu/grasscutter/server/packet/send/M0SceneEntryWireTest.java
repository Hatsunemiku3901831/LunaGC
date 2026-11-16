package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.net.proto.Client72M0SceneEntryOuterClass.*;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class M0SceneEntryWireTest {
    @Test
    void independentGoldenWireRetainsKnownTagsAndNoUnknownChildren() throws Exception {
        byte[] golden = HexFormat.of().parseHex("1208200138025003680438056006");
        var root = NOCHHDCLKDJ.parseFrom(golden);
        assertEquals(5, root.getMCCMGLDOECH());
        assertEquals(6, root.getOLCJCLBMMCF());
        var row = root.getDIGAMAKGCPA(0);
        assertEquals(1, row.getFDDDFIAINCE());
        assertEquals(2, row.getIEDJNHILJBF());
        assertEquals(3, row.getNPKKCHABEJH());
        assertEquals(4, row.getOKMEHFHNFCG());
        assertArrayEquals(golden, root.toByteArray());
        assertEquals(Set.of(2, 7, 12, 14, 15), root.getDescriptorForType().getFields().stream()
                .map(f -> f.getNumber()).collect(Collectors.toSet()));
        assertEquals(Set.of(4, 7, 10, 13), row.getDescriptorForType().getFields().stream()
                .map(f -> f.getNumber()).collect(Collectors.toSet()));
    }

    @Test
    void independentWorldAndTeamIdentityChildrenAreNonemptyAndDistinct() throws Exception {
        byte[] wire = HexFormat.of().parseHex("720228047a023805");
        var root = NOCHHDCLKDJ.parseFrom(wire);
        assertEquals(4, root.getLFLOLJADPBI().getLLHEBIHGDAE());
        assertEquals(5, root.getAEKMONHBHAA().getJNBCPNHJCNI());
        assertNotEquals(root.getLFLOLJADPBI().getLLHEBIHGDAE(), root.getAEKMONHBHAA().getJNBCPNHJCNI());
        assertArrayEquals(wire, root.toByteArray());
        assertEquals(Set.of(1, 5), JLILNBLGMIP.getDescriptor().getFields().stream()
                .map(f -> f.getNumber()).collect(Collectors.toSet()));
        assertEquals(Set.of(1, 3, 7), BINIKNHCLIA.getDescriptor().getFields().stream()
                .map(f -> f.getNumber()).collect(Collectors.toSet()));
    }

    @Test
    void explicitControlWithZeroEntriesDiffersFromAbsentControlAndHasNoFakeStateRecord() throws Exception {
        byte[] wire = HexFormat.of().parseHex("7a040a003805");
        var root = NOCHHDCLKDJ.parseFrom(wire);
        var team = root.getAEKMONHBHAA();
        assertTrue(team.hasEOFFDMLNFIM());
        assertEquals(0, team.getEOFFDMLNFIM().getGFDEDOJEICECount());
        assertEquals(0, team.getKNFDPJCMNOECount());
        assertEquals(5, team.getJNBCPNHJCNI());
        assertFalse(NOCHHDCLKDJ.parseFrom(HexFormat.of().parseHex("7a023805"))
                .getAEKMONHBHAA().hasEOFFDMLNFIM());
        assertNull(JLILNBLGMIP.getDescriptor().findFieldByNumber(2));
        assertNull(BINIKNHCLIA.getDescriptor().findFieldByNumber(4));
        assertArrayEquals(wire, root.toByteArray());
    }

    @Test
    void listHighGuidTokenBitsExplicitZeroAndTruncationRemainDistinct() throws Exception {
        var row = CKMLFOFDEHN.newBuilder().setFDDDFIAINCE(0x80000001)
                .setIEDJNHILJBF(0).setNPKKCHABEJH(-1L).setOKMEHFHNFCG(9007199254740993L);
        var root = NOCHHDCLKDJ.newBuilder().setMCCMGLDOECH(-1).setOLCJCLBMMCF(0)
                .addDIGAMAKGCPA(row).addDIGAMAKGCPA(row).build();
        var parsed = NOCHHDCLKDJ.parseFrom(root.toByteArray());
        assertEquals(2, parsed.getDIGAMAKGCPACount());
        assertEquals(-1, parsed.getMCCMGLDOECH());
        assertTrue(parsed.hasOLCJCLBMMCF());
        assertTrue(parsed.getDIGAMAKGCPA(0).hasIEDJNHILJBF());
        assertEquals(-1L, parsed.getDIGAMAKGCPA(0).getNPKKCHABEJH());
        assertEquals(9007199254740993L, parsed.getDIGAMAKGCPA(1).getOKMEHFHNFCG());
        assertFalse(NOCHHDCLKDJ.getDefaultInstance().hasMCCMGLDOECH());
        assertThrows(InvalidProtocolBufferException.class,
                () -> NOCHHDCLKDJ.parseFrom(HexFormat.of().parseHex("120820013802")));
    }
}
