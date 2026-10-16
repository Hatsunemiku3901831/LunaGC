package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.net.packet.PacketOpcodes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** Vectors from the pinned 7.1 native readers/writers, independent of generated Java code. */
class MusicGameBookProtocol71Test {
    private Descriptors.Descriptor schema(String name) {
        return assertDoesNotThrow(
                () ->
                        (Descriptors.Descriptor)
                                Class.forName(
                                                "emu.grasscutter.net.proto."
                                                        + name
                                                        + "OuterClass$"
                                                        + name)
                                        .getMethod("getDescriptor")
                                        .invoke(null),
                name);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
        MusicGameBookAllDataNotify,LKAIFMEKBNG,1,UINT32,false
        MusicGameBookAllDataNotify,DHDNMAMDHIP,2,UINT32,true
        MusicGameBookAllDataNotify,PAEINCPKPHH,3,MESSAGE,true
        MusicGameBookAllDataNotify,POIDELONALN,5,BOOL,false
        MusicGameBookAllDataNotify,KKCGHOOLALK,7,UINT32,true
        MusicGameBookAllDataNotify,DGEAGAANHHO,9,UINT32,false
        MusicGameBookAllDataNotify,NKFEGNKDBBK,10,MESSAGE,false
        MusicGameBookAllDataNotify,JNJDOLEBBOO,12,UINT32,true
        MusicGameBookAllDataNotify,ONCKJAILFEB,13,UINT32,true
        MusicGameBookAllDataNotify,OMFHIFALODP,14,UINT32,true
        MusicGameBookAllDataNotify,IMNOPIKEOBA,15,UINT32,false
        MusicGameBookRecord,NFHCCJALOAJ,4,BOOL,false
        MusicGameBookRecord,JNJDOLEBBOO,5,UINT32,true
        MusicGameBookRecord,MCPDKDGGBDD,10,UINT32,false
        MusicGameBookRecord,IHPMDKPGJLB,11,UINT32,false
        MusicGameBookSettings,FPCNMIDOPLO,2,BOOL,false
        MusicGameBookSettings,DBGINECAKNF,3,UINT32,false
        MusicGameBookSettings,OFKMLIACIHC,4,UINT32,false
        MusicGameBookSettings,DKOKBJOJGAP,8,BOOL,false
        MusicGameBookSettings,JPMNHOHBKCA,12,BOOL,false
        MusicGameBookSettings,JDPOJFFPMHN,13,BOOL,false
        MusicGameBookSettings,NHIEOPAEFLF,14,UINT32,false
        MusicGameBookSettleReq,EGDIMEINBJA,1,UINT32,false
        MusicGameBookSettleReq,CAEKFNKKEPG,2,UINT32,false
        MusicGameBookSettleReq,IHPMDKPGJLB,4,UINT32,false
        MusicGameBookSettleReq,DFPIEMBCGCN,6,FLOAT,false
        MusicGameBookSettleReq,PGDGFMNNIII,7,UINT32,false
        MusicGameBookSettleReq,BCBDBEFONEF,9,UINT64,false
        MusicGameBookSettleReq,FMBMLBAGIIO,10,UINT32,true
        MusicGameBookSettleReq,FNCOKHKCDDG,13,BOOL,false
        MusicGameBookSettleReq,MPALNOCPELN,14,UINT32,false
        MusicGameBookSettleReq,MIENEOHDNBB,773,FLOAT,false
        MusicGameBookSettleReq,DGBEKHBEHFN,896,BOOL,false
        MusicGameBookSettleReq,LKIMDBMGKEM,1028,UINT32,false
        MusicGameBookSettleReq,KBBAMIPLHEJ,1071,UINT32,true
        MusicGameBookSettleReq,GHHABIPEHNN,1274,UINT32,false
        MusicGameBookSettleReq,NGMCGJBAJJC,1565,BOOL,false
        MusicGameBookSettleReq,KGAGGALLDPO,1909,UINT32,false
        MusicGameBookSettleReq,FJDDGBILJCF,2001,UINT32,false
        MusicGameBookStartReq,IHPMDKPGJLB,1,UINT32,false
        MusicGameBookStartReq,EGKLPEIGACI,3,BOOL,false
        MusicGameBookStartReq,BCBDBEFONEF,4,UINT64,false
        MusicGameBookSettleRsp,BLFNCCLLGLB,1,ENUM,false
        MusicGameBookSettleRsp,retcode,2,INT32,false
        MusicGameBookSettleRsp,IHPMDKPGJLB,3,UINT32,false
        MusicGameBookSettleRsp,PHKLGIKKAMA,4,BOOL,false
        MusicGameBookSettleRsp,OFKFKNJHDIO,5,UINT32,true
        MusicGameBookSettleRsp,IMNOPIKEOBA,6,UINT32,false
        MusicGameBookSettleRsp,OKICCBNHOOL,11,BOOL,false
        MusicGameBookSettleRsp,BCBDBEFONEF,12,UINT64,false
        MusicGameBookSettleRsp,JIOFAJEECJC,13,BOOL,false
        MusicGameBookStartRsp,IHPMDKPGJLB,2,UINT32,false
        MusicGameBookStartRsp,retcode,9,INT32,false
        MusicGameBookStartRsp,BCBDBEFONEF,13,UINT64,false
        GetRecommendedMusicGameBookReq,KKHMCPGDPJH,4,BOOL,false
        GetRecommendedMusicGameBookReq,JBDNPAECJKC,7,UINT32,false
        GetRecommendedMusicGameBookRsp,BMDMNFLCPLF,7,MESSAGE,true
        GetRecommendedMusicGameBookRsp,retcode,15,INT32,false
        MusicGameBookBriefInfo,GKFDMEALPDH,1,UINT32,false
        MusicGameBookBriefInfo,BBMIBHCCIKL,2,UINT32,false
        MusicGameBookBriefInfo,BCBDBEFONEF,3,UINT64,false
        MusicGameBookBriefInfo,BAEBFNBAMEH,4,MESSAGE,false
        MusicGameBookBriefInfo,NLLCAHFHGNA,5,UINT32,false
        MusicGameBookBriefInfo,MHPONLPAOIH,6,BOOL,false
        MusicGameBookBriefInfo,PCLFKIGAMFA,7,UINT32,false
        MusicGameBookBriefInfo,GKIAEIHNBGJ,8,BOOL,false
        MusicGameBookBriefInfo,JIOFAJEECJC,9,BOOL,false
        MusicGameBookBriefInfo,ANGGGIPAMIL,10,UINT32,false
        MusicGameBookBriefInfo,OGDFFPNPADF,11,UINT32,false
        MusicGameBookBriefInfo,MCPDKDGGBDD,12,UINT32,false
        MusicGameBookBriefInfo,FGBMHEJKKGJ,13,UINT32,false
        MusicGameBookBriefInfo,MHLFAFLKCGH,14,STRING,false
        MusicGameBookBriefInfo,CPKEBGBACCC,15,BOOL,false
        MusicGameBookBriefInfo,IMNOPIKEOBA,519,UINT32,false
        MusicGameBookBriefInfo,IIDNHMHOGJD,1038,STRING,false
        MusicGameBookBriefInfo,OHNBIBJGEAD,1216,UINT32,true
        MusicGameBookBriefInfo,IBKJHNJPBHD,1296,UINT32,true
        MusicGameBookBriefInfo,JLAFKJJIKGP,1755,STRING,false
        MusicGameBookBriefInfo,AONJOPALEAM,1886,UINT32,false
        MusicGameBookSocial,PBHMOCOLOME,7,UINT32,false
        MusicGameBookSocial,LHNNNBBIPGK,8,UINT32,false
        MusicGameBookSocial,APLJOBOPOPP,11,UINT32,false
        MusicGameBookSocial,LINEPEAIJGE,14,UINT32,false
        MusicGameBookSocial,FGNHFELMGGC,15,UINT32,false
        """)
    void nativeFieldLayout(String name, String field, int number, String type, boolean repeated) {
        var descriptor = schema(name).findFieldByName(field);
        assertNotNull(descriptor, name + "." + field);
        assertEquals(number, descriptor.getNumber());
        assertEquals(Descriptors.FieldDescriptor.Type.valueOf(type), descriptor.getType());
        assertEquals(repeated, descriptor.isRepeated());
        if (repeated && descriptor.getType() == Descriptors.FieldDescriptor.Type.UINT32)
            assertTrue(descriptor.isPacked());
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
        MusicGameBookAllDataNotify,11
        MusicGameBookRecord,4
        MusicGameBookSettings,7
        MusicGameBookSettleReq,17
        MusicGameBookStartReq,3
        MusicGameBookSettleRsp,9
        MusicGameBookStartRsp,3
        GetRecommendedMusicGameBookReq,2
        GetRecommendedMusicGameBookRsp,2
        MusicGameBookBriefInfo,21
        MusicGameBookSocial,5
        """)
    void onlyNativeFieldsAreDeclaredAndFutureFieldsSurvive(String name, int count)
            throws Exception {
        var descriptor = schema(name);
        assertEquals(count, descriptor.getFields().size());
        assertTrue(descriptor.getFields().stream().noneMatch(f -> f.getNumber() >= 50000));
        var generated =
                (Message)
                        Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name)
                                .getMethod("getDefaultInstance")
                                .invoke(null);
        byte[] bytes = wire(out -> out.writeUInt32(4095, -1));
        var parsed = generated.getParserForType().parseFrom(bytes);
        assertEquals(
                List.of(4294967295L), parsed.getUnknownFields().getField(4095).getVarintList());
        assertArrayEquals(bytes, parsed.toByteArray());
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
        MusicGameBookAllDataNotify,8786
        MusicGameBookStartReq,27235
        MusicGameBookStartRsp,29833
        MusicGameBookSettleReq,3829
        MusicGameBookSettleRsp,24067
        GetRecommendedMusicGameBookReq,8475
        GetRecommendedMusicGameBookRsp,23303
        """)
    void packetNumbersAreCurrentNativeLiterals(String name, int opcode) throws Exception {
        assertEquals(opcode, PacketOpcodes.class.getField(name).getInt(null));
    }

    @FunctionalInterface
    interface Body {
        void write(CodedOutputStream out) throws Exception;
    }

    private byte[] wire(Body body) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bytes);
        body.write(out);
        out.flush();
        return bytes.toByteArray();
    }

    private Message parse(String name, byte[] bytes) throws Exception {
        schema(name);
        var generated =
                (Message)
                        Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name)
                                .getMethod("getDefaultInstance")
                                .invoke(null);
        return generated.getParserForType().parseFrom(bytes);
    }

    private Object value(Message message, int field) {
        var descriptor = message.getDescriptorForType().findFieldByNumber(field);
        assertNotNull(descriptor, "Native field " + field);
        return message.getField(descriptor);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
        MusicGameBookAllDataNotify,2
        MusicGameBookAllDataNotify,7
        MusicGameBookAllDataNotify,12
        MusicGameBookAllDataNotify,13
        MusicGameBookAllDataNotify,14
        MusicGameBookRecord,5
        MusicGameBookSettleReq,10
        MusicGameBookSettleReq,1071
        MusicGameBookSettleRsp,5
        MusicGameBookBriefInfo,1216
        MusicGameBookBriefInfo,1296
        """)
    void repeatedWordsAcceptBothWireForms(String name, int field) throws Exception {
        var words =
                wire(
                        out -> {
                            out.writeUInt32NoTag(128);
                            out.writeUInt32NoTag(-1);
                        });
        var packed = parse(name, wire(out -> out.writeByteArray(field, words)));
        var unpacked =
                parse(
                        name,
                        wire(
                                out -> {
                                    out.writeUInt32(field, 128);
                                    out.writeUInt32(field, -1);
                                }));
        assertEquals(List.of(128, -1), value(packed, field));
        assertEquals(packed, unpacked);
        assertArrayEquals(packed.toByteArray(), unpacked.toByteArray());
        assertTrue(packed.getUnknownFields().asMap().isEmpty());
    }

    @Test
    void directoryContainsTypedRecordsAndSettings() throws Exception {
        var settings =
                wire(
                        out -> {
                            out.writeBool(2, true);
                            out.writeUInt32(3, 90);
                            out.writeUInt32(4, 120);
                            out.writeBool(8, true);
                            out.writeBool(12, true);
                            out.writeBool(13, true);
                            out.writeUInt32(14, 3);
                        });
        var record =
                wire(
                        out -> {
                            out.writeBool(4, true);
                            out.writeUInt32(5, 100);
                            out.writeUInt32(5, 101);
                            out.writeUInt32(10, 123456);
                            out.writeUInt32(11, 400);
                        });
        var directory =
                parse(
                        "MusicGameBookAllDataNotify",
                        wire(
                                out -> {
                                    out.writeUInt32(1, 11);
                                    out.writeUInt32(2, 22);
                                    out.writeByteArray(3, record);
                                    out.writeByteArray(3, record);
                                    out.writeBool(5, true);
                                    out.writeUInt32(7, 77);
                                    out.writeUInt32(9, 99);
                                    out.writeByteArray(10, settings);
                                    out.writeUInt32(12, 12);
                                    out.writeUInt32(13, 13);
                                    out.writeUInt32(14, 14);
                                    out.writeUInt32(15, 15);
                                }));
        assertEquals(2, ((List<?>) value(directory, 3)).size());
        var nested = (Message) ((List<?>) value(directory, 3)).get(0);
        assertEquals(schema("MusicGameBookRecord"), nested.getDescriptorForType());
        assertEquals(400, value(nested, 11));
        assertEquals(123456, value(nested, 10));
        assertEquals(List.of(100, 101), value(nested, 5));
        var options = (Message) value(directory, 10);
        assertEquals(schema("MusicGameBookSettings"), options.getDescriptorForType());
        assertEquals(true, value(options, 2));
        assertEquals(90, value(options, 3));
        assertEquals(3, value(options, 14));
        assertTrue(directory.getUnknownFields().asMap().isEmpty());
        assertTrue(nested.getUnknownFields().asMap().isEmpty());
        assertTrue(options.getUnknownFields().asMap().isEmpty());
    }

    @Test
    void startMessagesKeepFullWidthShareIdsAndIndependentFieldNumbers() throws Exception {
        var req =
                parse(
                        "MusicGameBookStartReq",
                        HexFormat.of().parseHex("08ac02180120ffffffffffffffffff01"));
        assertEquals(300, value(req, 1));
        assertEquals(true, value(req, 3));
        assertEquals(-1L, value(req, 4));
        assertEquals(3, req.getAllFields().size());
        var rsp =
                parse(
                        "MusicGameBookStartRsp",
                        wire(
                                out -> {
                                    out.writeUInt32(2, 300);
                                    out.writeInt32(9, -1);
                                    out.writeUInt64(13, -1L);
                                }));
        assertEquals(300, value(rsp, 2));
        assertEquals(-1, value(rsp, 9));
        assertEquals(-1L, value(rsp, 13));
        assertTrue(rsp.getUnknownFields().asMap().isEmpty());
        assertNotEquals(schema("MusicGameStartReq"), req.getDescriptorForType());
    }

    @Test
    void settleKeepsTwoDistinctFloatSlotsAndAllHighTags() throws Exception {
        var request =
                parse(
                        "MusicGameBookSettleReq",
                        wire(
                                out -> {
                                    out.writeUInt32(1, 11);
                                    out.writeUInt32(2, 22);
                                    out.writeUInt32(4, 44);
                                    out.writeFloat(6, 1.25f);
                                    out.writeUInt32(7, 77);
                                    out.writeUInt64(9, -1L);
                                    out.writeUInt32(10, 100);
                                    out.writeBool(13, true);
                                    out.writeUInt32(14, 140);
                                    out.writeFloat(773, -2.5f);
                                    out.writeBool(896, true);
                                    out.writeUInt32(1028, -1);
                                    out.writeUInt32(1071, 1071);
                                    out.writeUInt32(1274, 1274);
                                    out.writeBool(1565, true);
                                    out.writeUInt32(1909, 1909);
                                    out.writeUInt32(2001, 2001);
                                }));
        assertEquals(17, request.getAllFields().size());
        assertEquals(1.25f, value(request, 6));
        assertEquals(-2.5f, value(request, 773));
        assertEquals(-1L, value(request, 9));
        assertEquals(-1, value(request, 1028));
        assertEquals(List.of(1071), value(request, 1071));
        assertEquals(2001, value(request, 2001));
        assertTrue(request.getUnknownFields().asMap().isEmpty());
        var bytes =
                wire(
                        out -> {
                            out.writeEnum(1, 99);
                            out.writeInt32(2, -1);
                            out.writeUInt32(3, 44);
                            out.writeBool(4, true);
                            out.writeUInt32(5, 500);
                            out.writeUInt32(6, -1);
                            out.writeBool(11, true);
                            out.writeUInt64(12, -1L);
                            out.writeBool(13, true);
                        });
        var response = parse("MusicGameBookSettleRsp", bytes);
        assertEquals(99, ((Descriptors.EnumValueDescriptor) value(response, 1)).getNumber());
        assertEquals(-1, value(response, 2));
        assertEquals(-1L, value(response, 12));
        assertEquals(9, response.getAllFields().size());
        assertTrue(response.getUnknownFields().asMap().isEmpty());
        // Packed normalization is expected; unknown enum values remain ordinary field values.
        assertEquals(response, parse("MusicGameBookSettleRsp", response.toByteArray()));
    }

    @Test
    void recommendationsCarryTheirOwnBriefAndSocialTypes() throws Exception {
        var social =
                wire(
                        out -> {
                            for (int number : new int[] {7, 8, 11, 14, 15})
                                out.writeUInt32(number, number * 100);
                        });
        var brief =
                wire(
                        out -> {
                            out.writeUInt32(1, 1);
                            out.writeUInt64(3, -1L);
                            out.writeByteArray(4, social);
                            out.writeBool(6, true);
                            out.writeUInt32(12, 999);
                            out.writeString(14, "演奏者");
                            out.writeUInt32(519, 519);
                            out.writeString(1038, "曲谱");
                            out.writeUInt32(1216, 41);
                            out.writeUInt32(1296, 42);
                            out.writeString(1755, "签名");
                            out.writeUInt32(1886, -1);
                        });
        var response =
                parse(
                        "GetRecommendedMusicGameBookRsp",
                        wire(
                                out -> {
                                    out.writeByteArray(7, brief);
                                    out.writeByteArray(7, brief);
                                }));
        var list = (List<?>) value(response, 7);
        assertEquals(2, list.size());
        var nested = (Message) list.get(0);
        assertEquals(schema("MusicGameBookBriefInfo"), nested.getDescriptorForType());
        assertEquals(-1L, value(nested, 3));
        assertEquals("演奏者", value(nested, 14));
        assertEquals(List.of(41), value(nested, 1216));
        assertEquals(List.of(42), value(nested, 1296));
        assertEquals(-1, value(nested, 1886));
        var counters = (Message) value(nested, 4);
        assertEquals(schema("MusicGameBookSocial"), counters.getDescriptorForType());
        assertEquals(1500, value(counters, 15));
        assertTrue(nested.getUnknownFields().asMap().isEmpty());
        assertTrue(counters.getUnknownFields().asMap().isEmpty());
        assertNotEquals(schema("UgcMusicBriefInfo"), nested.getDescriptorForType());
    }
}
