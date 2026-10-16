package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.EvtBeingHitInfoOuterClass.EvtBeingHitInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** Independent vectors from the pinned native tag trees; these are not client captures. */
class AttackResultProtocol71Test {
    private Descriptors.Descriptor schema(String name) throws Exception {
        return (Descriptors.Descriptor)
                Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name)
                        .getMethod("getDescriptor")
                        .invoke(null);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
        AttackResult,element_type,1,UINT32
        AttackResult,damage,2,FLOAT
        AttackResult,hit_collision,4,MESSAGE
        AttackResult,anim_event_id,5,STRING
        AttackResult,ability_identifier,7,MESSAGE
        AttackResult,hit_eff_result,8,MESSAGE
        AttackResult,attacker_id,9,UINT32
        AttackResult,defense_id,10,UINT32
        AttackResult,LOPDFFOBMKD,11,UINT32
        AttackResult,AFPBFDHCDNK,12,UINT32
        AttackResult,ADHABOOLHMK,13,MESSAGE
        AttackResult,hit_retreat_angle_compat,14,INT32
        AttackResult,GPJCOMLMMCL,15,BOOL
        AttackResult,EGANPEDGKNP,17,UINT32
        AttackResult,CNHIFLOKNDJ,120,FLOAT
        AttackResult,KNAOCDCIJNO,184,UINT32
        AttackResult,FEKDLEBIFJG,259,FLOAT
        AttackResult,BGJEAEJIFPB,266,UINT32
        AttackResult,BEJNEFGFKDJ,345,BOOL
        AttackResult,PGPBACPJGNM,425,MESSAGE
        AttackResult,MDECMKGHDAK,496,FLOAT
        AttackResult,BAPFFBDFHBF,564,UINT32
        AttackResult,MAJCFLAEOEG,567,BOOL
        AttackResult,IPFLGNKFEAC,616,UINT32
        AttackResult,EKDNPKHMJIE,623,UINT32
        AttackResult,JILIGGDCLDH,748,UINT32
        AttackResult,JACFHPIOPPB,921,UINT32
        AttackResult,JPKAJEILEHI,989,BOOL
        AttackResult,LFDKHNJBIHP,1227,FLOAT
        AttackResult,OHPEIAGBOIK,1313,UINT32
        AttackResult,LMJFBGECCJF,1391,UINT32
        AttackResult,HOFKMPGIHFC,1436,MESSAGE
        AttackResult,EFBFOAOJADK,1567,MESSAGE
        AttackResult,PGCOOCEBKEK,1572,FLOAT
        AttackResult,_int_motion_info,1775,MESSAGE
        AttackResult,BDLPAOBHMJH,1825,UINT32
        AttackResult,GIHFBEIJHPB,1831,BOOL
        AttackResult,JBEADCOMPAH,1837,UINT32
        AttackResult,EPNBAAHLILJ,1850,UINT32
        AttackResult,BNBBLJCDMPJ,1975,UINT32
        HitCollision,NJKLJHDFGMG,1,FLOAT
        HitCollision,hit_collider_type,5,ENUM
        HitCollision,hit_point,9,MESSAGE
        HitCollision,FHFGJMFHCIJ,11,FLOAT
        HitCollision,hit_dir,12,MESSAGE
        HitCollision,EIHNKFAPOGM,13,FLOAT
        HitCollision,hit_box_index,15,INT32
        AttackHitEffectResult,dgedhlmbpca,1,UINT32
        AttackHitEffectResult,APDAHHOOLAA,2,FLOAT
        AttackHitEffectResult,LNJOFMNBLDI,6,FLOAT
        AttackHitEffectResult,NAKEMGBNBED,8,FLOAT
        AttackHitEffectResult,FDBKFJEIJPG,10,FLOAT
        AttackHitEffectResult,FEPJABMLCPF,13,UINT32
        """)
    void fieldsMatchTheNativeParser(String message, String name, int number, String type)
            throws Exception {
        var field = schema(message).findFieldByName(name);
        assertNotNull(field, message + "." + name);
        assertEquals(number, field.getNumber());
        assertEquals(Descriptors.FieldDescriptor.Type.valueOf(type), field.getType());
        assertEquals(number == 1313, field.isRepeated());
    }

    @FunctionalInterface
    interface Body {
        void write(CodedOutputStream out) throws Exception;
    }

    private byte[] wire(Body body) {
        return assertDoesNotThrow(
                () -> {
                    var bytes = new ByteArrayOutputStream();
                    var out = CodedOutputStream.newInstance(bytes);
                    body.write(out);
                    out.flush();
                    return bytes.toByteArray();
                });
    }

    private Object value(Message message, int number) {
        var field = message.getDescriptorForType().findFieldByNumber(number);
        assertNotNull(field, "Missing native field " + number);
        return message.getField(field);
    }

    @Test
    void theThreeMessagesHaveOnlyTheirNativeFieldCounts() throws Exception {
        for (var row :
                Map.of("AttackResult", 40, "HitCollision", 7, "AttackHitEffectResult", 6)
                        .entrySet()) {
            var descriptor = schema(row.getKey());
            assertEquals(row.getValue(), descriptor.getFields().size());
            assertTrue(descriptor.getFields().stream().noneMatch(f -> f.getNumber() >= 50000));
        }
    }

    @Test
    void hitEffectsAndIntegerMotionAreTypedInsideARealHitEnvelope() throws Exception {
        var effect =
                wire(
                        out -> {
                            out.writeUInt32(1, 4);
                            out.writeFloat(2, 1.5f);
                            out.writeFloat(6, 2.5f);
                            out.writeFloat(8, 3.5f);
                            out.writeFloat(10, 4.5f);
                            out.writeUInt32(13, -1);
                        });
        var motion =
                wire(
                        out -> {
                            out.writeUInt32(1, 123);
                            out.writeUInt32(5, 456);
                        });
        var attack =
                wire(
                        out -> {
                            out.writeUInt32(9, 101);
                            out.writeUInt32(10, 202);
                            out.writeUInt32(1, 5);
                            out.writeFloat(2, 123.5f);
                            out.writeString(5, "Attack01");
                            out.writeByteArray(8, effect);
                            out.writeByteArray(1775, motion);
                        });
        var envelope =
                EvtBeingHitInfo.parseFrom(
                        wire(
                                out -> {
                                    out.writeUInt32(3, 7);
                                    out.writeUInt32(4, 8);
                                    out.writeByteArray(7, attack);
                                }));
        var result = envelope.getAttackResult();
        assertEquals(101, result.getAttackerId());
        assertEquals(202, result.getDefenseId());
        assertEquals(5, result.getElementType());
        assertEquals(123.5f, result.getDamage());
        assertEquals("Attack01", result.getAnimEventId());
        assertEquals(4, result.getHitEffResult().getDgedhlmbpca());
        assertEquals(2.5f, value(result.getHitEffResult(), 6));
        assertEquals(3.5f, value(result.getHitEffResult(), 8));
        assertEquals(4.5f, value(result.getHitEffResult(), 10));
        assertEquals(-1, value(result.getHitEffResult(), 13));
        assertEquals(123, result.getIntMotionInfo().getEntityId());
        assertEquals(456, result.getIntMotionInfo().getSceneTime());
        assertTrue(result.getUnknownFields().asMap().isEmpty());
        assertEquals(envelope, EvtBeingHitInfo.parseFrom(envelope.toByteArray()));
    }

    @Test
    void allThreeCollisionFloatsUseTheirCurrentTags() throws Exception {
        var collision =
                wire(
                        out -> {
                            out.writeFloat(1, 1.25f);
                            out.writeFloat(11, 2.25f);
                            out.writeFloat(13, 3.25f);
                            out.writeInt32(15, -7);
                        });
        var result = AttackResult.parseFrom(wire(out -> out.writeByteArray(4, collision)));
        assertEquals(1.25f, value(result.getHitCollision(), 1));
        assertEquals(2.25f, value(result.getHitCollision(), 11));
        assertEquals(3.25f, value(result.getHitCollision(), 13));
        assertEquals(-7, result.getHitCollision().getHitBoxIndex());
        assertTrue(result.getHitCollision().getUnknownFields().asMap().isEmpty());
    }

    @Test
    void bothNativeVectorsAndAllFiveFlagsAreAccessible() throws Exception {
        var vector =
                wire(
                        out -> {
                            out.writeFloat(1, 1);
                            out.writeFloat(2, -2);
                            out.writeFloat(3, 3);
                        });
        var result =
                AttackResult.parseFrom(
                        wire(
                                out -> {
                                    out.writeByteArray(13, vector);
                                    out.writeByteArray(1436, vector);
                                    for (int tag : new int[] {15, 345, 567, 989, 1831})
                                        out.writeBool(tag, true);
                                }));
        for (int tag : new int[] {13, 1436}) {
            var nested = (Message) value(result, tag);
            assertEquals(1f, value(nested, 1));
            assertEquals(-2f, value(nested, 2));
            assertEquals(3f, value(nested, 3));
        }
        for (int tag : new int[] {15, 345, 567, 989, 1831}) assertEquals(true, value(result, tag));
        assertTrue(result.getUnknownFields().asMap().isEmpty());
    }

    @Test
    void fieldTwelveIsAnUnsignedWordWithCanonicalFiveByteEncoding() throws Exception {
        byte[] nativeWord = new byte[] {96, -1, -1, -1, -1, 15};
        var result = AttackResult.parseFrom(nativeWord);
        assertEquals(-1, result.getAFPBFDHCDNK());
        assertArrayEquals(nativeWord, result.toByteArray());
    }

    @Test
    void packedAndUnpackedRepeatedWordsAreBothAccepted() throws Exception {
        var packed =
                wire(
                        out -> {
                            out.writeUInt32NoTag(1);
                            out.writeUInt32NoTag(-1);
                        });
        var result =
                AttackResult.parseFrom(
                        wire(
                                out -> {
                                    out.writeByteArray(1313, packed);
                                    out.writeUInt32(1313, 7);
                                }));
        assertEquals(List.of(1, -1, 7), result.getOHPEIAGBOIKList());
        assertEquals(result, AttackResult.parseFrom(result.toByteArray()));
    }

    @Test
    void futureFieldsAndRetiredPlaceholdersRemainOpaque() throws Exception {
        var result =
                AttackResult.parseFrom(
                        wire(
                                out -> {
                                    out.writeUInt32(2047, 9);
                                    out.writeUInt32(50001, 7);
                                }));
        assertTrue(result.getAllFields().isEmpty());
        assertEquals(9L, result.getUnknownFields().getField(2047).getVarintList().get(0));
        assertEquals(7L, result.getUnknownFields().getField(50001).getVarintList().get(0));
        assertEquals(result, AttackResult.parseFrom(result.toByteArray()));
    }
}
