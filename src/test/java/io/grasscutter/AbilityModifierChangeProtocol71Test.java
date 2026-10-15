package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** Field numbers come from pinned native type 75443's reader and writer. */
class AbilityModifierChangeProtocol71Test {
    private Descriptors.FieldDescriptor field(String name) {
        var field = AbilityMetaModifierChange.getDescriptor().findFieldByName(name);
        assertNotNull(field, name);
        return field;
    }

    private Object read(AbilityMetaModifierChange message, String name) {
        return message.getField(field(name));
    }

    @Test
    void all_thirteen_fields_use_current_native_numbers() {
        var expected =
                Map.ofEntries(
                        Map.entry("attached_instanced_modifier", 1),
                        Map.entry("parent_ability_name", 2),
                        Map.entry("OCAMKBCONED", 3),
                        Map.entry("is_attached_parent_ability", 4),
                        Map.entry("apply_entity_id", 5),
                        Map.entry("server_buff_uid", 7),
                        Map.entry("action", 8),
                        Map.entry("parent_ability_override", 9),
                        Map.entry("modifier_local_id", 11),
                        Map.entry("FBIKNOFHCHI", 12),
                        Map.entry("is_mute_remote", 13),
                        Map.entry("MFBDIICANOF", 14),
                        Map.entry("properties", 15));
        assertEquals(expected.size(), AbilityMetaModifierChange.getDescriptor().getFields().size());
        expected.forEach(
                (name, number) -> assertEquals(number.intValue(), field(name).getNumber(), name));
        assertEquals(Descriptors.FieldDescriptor.Type.BOOL, field("FBIKNOFHCHI").getType());
        assertEquals(Descriptors.FieldDescriptor.Type.BOOL, field("is_mute_remote").getType());
    }

    @ParameterizedTest
    @CsvSource({
        "6001, true, false",
        "6801, false, true",
        "60016801, true, true",
        "'', false, false"
    })
    void native_flags_decode_independently(String hex, boolean first, boolean mute)
            throws Exception {
        var message = AbilityMetaModifierChange.parseFrom(HexFormat.of().parseHex(hex));
        assertEquals(first, read(message, "FBIKNOFHCHI"));
        assertEquals(mute, read(message, "is_mute_remote"));
        assertTrue(message.getUnknownFields().asMap().isEmpty());
        assertArrayEquals(HexFormat.of().parseHex(hex), message.toByteArray());
    }

    @Test
    void native_flag_writer_never_uses_legacy_placeholder_tags() throws Exception {
        var message =
                DynamicMessage.newBuilder(AbilityMetaModifierChange.getDescriptor())
                        .setField(field("FBIKNOFHCHI"), true)
                        .setField(field("is_mute_remote"), true)
                        .build();
        assertArrayEquals(HexFormat.of().parseHex("60016801"), message.toByteArray());
    }

    @Test
    void old_placeholders_remain_unknown_instead_of_becoming_native_flags() throws Exception {
        var stream = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(stream);
        out.writeBool(50000, true);
        out.writeBool(50001, true);
        out.flush();
        var message = AbilityMetaModifierChange.parseFrom(stream.toByteArray());
        assertEquals(false, read(message, "FBIKNOFHCHI"));
        assertEquals(false, read(message, "is_mute_remote"));
        assertEquals(Set.of(50000, 50001), message.getUnknownFields().asMap().keySet());
        assertArrayEquals(stream.toByteArray(), message.toByteArray());
    }

    @Test
    void flags_do_not_shift_parent_names_attachment_or_auxiliary_float() throws Exception {
        var name = AbilityString.newBuilder().setStr("Parent").build();
        var variant = AbilityString.newBuilder().setStr("Variant").build();
        var stream = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(stream);
        out.writeMessage(2, name);
        out.writeBool(4, true);
        out.writeUInt32(5, -1);
        out.writeUInt32(7, 47);
        out.writeMessage(9, variant);
        out.writeInt32(11, 3);
        out.writeBool(12, true);
        out.writeBool(13, true);
        out.writeFloat(14, 0.75f);
        out.flush();
        var message = AbilityMetaModifierChange.parseFrom(stream.toByteArray());
        assertEquals(name, message.getParentAbilityName());
        assertEquals(variant, message.getParentAbilityOverride());
        assertTrue(message.getIsAttachedParentAbility());
        assertEquals(4294967295L, Integer.toUnsignedLong(message.getApplyEntityId()));
        assertEquals(47, message.getServerBuffUid());
        assertEquals(3, message.getModifierLocalId());
        assertEquals(0.75f, message.getMFBDIICANOF());
        assertEquals(true, read(message, "FBIKNOFHCHI"));
        assertEquals(true, read(message, "is_mute_remote"));
        assertTrue(message.getUnknownFields().asMap().isEmpty());
        assertArrayEquals(stream.toByteArray(), message.toByteArray());
    }
}
