package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import org.junit.jupiter.api.Test;

import java.util.*;

/** Wire fixtures follow the pinned 7.1 native parsers, independently of generated accessors. */
class AbilityDurabilityWire71Test {
    private Descriptors.Descriptor schema(String name) throws Exception {
        try {
            return (Descriptors.Descriptor)
                    Class.forName("emu.grasscutter.net.proto." + name + "OuterClass$" + name)
                            .getMethod("getDescriptor")
                            .invoke(null);
        } catch (ClassNotFoundException missing) {
            return fail("Missing verified 7.1 schema: " + name);
        }
    }

    private DynamicMessage decode(String name, String hex) throws Exception {
        return DynamicMessage.parseFrom(schema(name), HexFormat.of().parseHex(hex));
    }

    private Object field(DynamicMessage message, String name) {
        var descriptor = message.getDescriptorForType().findFieldByName(name);
        assertNotNull(descriptor, name);
        return message.getField(descriptor);
    }

    private void layout(
            String name,
            Map<String, Integer> numbers,
            Map<String, Descriptors.FieldDescriptor.Type> types)
            throws Exception {
        var message = schema(name);
        assertEquals(numbers.size(), message.getFields().size());
        numbers.forEach(
                (key, number) -> {
                    var field = message.findFieldByName(key);
                    assertNotNull(field, key);
                    assertEquals(number.intValue(), field.getNumber(), key);
                    assertEquals(types.get(key), field.getType(), key);
                    assertFalse(field.isRepeated());
                });
    }

    @Test
    void shield_layout_uses_the_current_native_fields() throws Exception {
        layout(
                "AbilityMixinShieldBar",
                Map.of(
                        "player_num",
                        2,
                        "element_type",
                        9,
                        "max_durability",
                        10,
                        "remaining_durability",
                        15),
                Map.of(
                        "player_num",
                        Descriptors.FieldDescriptor.Type.UINT32,
                        "element_type",
                        Descriptors.FieldDescriptor.Type.UINT32,
                        "max_durability",
                        Descriptors.FieldDescriptor.Type.FLOAT,
                        "remaining_durability",
                        Descriptors.FieldDescriptor.Type.FLOAT));
    }

    @Test
    void native_shield_fixture_keeps_current_and_maximum_separate() throws Exception {
        var message = decode("AbilityMixinShieldBar", "10034806550080de427d00008c41");
        assertEquals(3, field(message, "player_num"));
        assertEquals(6, field(message, "element_type"));
        assertEquals(111.25f, field(message, "max_durability"));
        assertEquals(17.5f, field(message, "remaining_durability"));
        assertTrue(message.getUnknownFields().asMap().isEmpty());
        assertArrayEquals(
                HexFormat.of().parseHex("10034806550080de427d00008c41"), message.toByteArray());
    }

    @Test
    void old_shield_numbers_are_not_accepted_as_current_durability() throws Exception {
        var message = decode("AbilityMixinShieldBar", "080338062d0080de424500008c41");
        assertEquals(0f, field(message, "max_durability"));
        assertEquals(0f, field(message, "remaining_durability"));
        assertEquals(Set.of(1, 5, 7, 8), message.getUnknownFields().asMap().keySet());
    }

    @Test
    void a_zero_shield_still_has_the_protobuf_zero_values() throws Exception {
        var message = decode("AbilityMixinShieldBar", "");
        assertEquals(0f, field(message, "remaining_durability"));
        assertEquals(0f, field(message, "max_durability"));
    }

    @Test
    void unsigned_shield_metadata_is_not_truncated() throws Exception {
        var message = decode("AbilityMixinShieldBar", "10ffffffff0f48feffffff0f");
        assertEquals(4294967295L, Integer.toUnsignedLong((Integer) field(message, "player_num")));
        assertEquals(4294967294L, Integer.toUnsignedLong((Integer) field(message, "element_type")));
    }

    @Test
    void durability_update_layout_preserves_the_auxiliary_float_without_guessing_its_role()
            throws Exception {
        layout(
                "AbilityMetaModifierDurabilityChange",
                Map.of("remaining_durability", 5, "JODMMPLFMEG", 8, "is_serverbuff_modifier", 15),
                Map.of(
                        "remaining_durability",
                        Descriptors.FieldDescriptor.Type.FLOAT,
                        "JODMMPLFMEG",
                        Descriptors.FieldDescriptor.Type.FLOAT,
                        "is_serverbuff_modifier",
                        Descriptors.FieldDescriptor.Type.BOOL));
    }

    @Test
    void native_durability_update_fixture_preserves_all_three_values() throws Exception {
        var message = decode("AbilityMetaModifierDurabilityChange", "2d00008c4145000020407801");
        assertEquals(17.5f, field(message, "remaining_durability"));
        assertEquals(2.5f, field(message, "JODMMPLFMEG"));
        assertEquals(true, field(message, "is_serverbuff_modifier"));
        assertTrue(message.getUnknownFields().asMap().isEmpty());
        assertArrayEquals(
                HexFormat.of().parseHex("2d00008c4145000020407801"), message.toByteArray());
    }

    @Test
    void omitted_update_values_keep_their_defaults() throws Exception {
        var message = decode("AbilityMetaModifierDurabilityChange", "");
        assertEquals(0f, field(message, "remaining_durability"));
        assertEquals(0f, field(message, "JODMMPLFMEG"));
        assertEquals(false, field(message, "is_serverbuff_modifier"));
    }

    @Test
    void modifier_snapshot_and_live_update_have_distinct_wire_contracts() throws Exception {
        var snapshot = decode("ModifierDurability", "0d000020401500008c41");
        assertEquals(2.5f, field(snapshot, "reduce_ratio"));
        assertEquals(17.5f, field(snapshot, "remaining_durability"));
        var update = decode("AbilityMetaModifierDurabilityChange", "0d000020401500008c41");
        assertEquals(0f, field(update, "remaining_durability"));
        assertEquals(0f, field(update, "JODMMPLFMEG"));
        assertEquals(Set.of(1, 2), update.getUnknownFields().asMap().keySet());
    }

    @Test
    void future_fields_are_retained_for_diagnostics_and_reencoding() throws Exception {
        var message = decode("AbilityMixinShieldBar", "550080de427d00008c41a00607");
        assertEquals(111.25f, field(message, "max_durability"));
        assertEquals(17.5f, field(message, "remaining_durability"));
        assertEquals(List.of(7L), message.getUnknownFields().getField(100).getVarintList());
        assertArrayEquals(
                HexFormat.of().parseHex("550080de427d00008c41a00607"), message.toByteArray());
    }
}
