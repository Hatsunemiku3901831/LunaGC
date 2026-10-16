package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

/** Independent vectors from type 79840's native parser/writer, not a client packet capture. */
class AbilityDurabilityZeroWire71Test {
    private Class<?> type() {
        return assertDoesNotThrow(
                () ->
                        Class.forName(
                                "emu.grasscutter.net.proto.AbilityMetaDurabilityIsZeroOuterClass$AbilityMetaDurabilityIsZero"));
    }

    private Message parse(byte... bytes) {
        return assertDoesNotThrow(
                () ->
                        (Message)
                                type().getMethod("parseFrom", byte[].class)
                                        .invoke(null, (Object) bytes));
    }

    @Test
    void argumentIsTheNativeTwentyTwo() {
        assertEquals(
                22,
                AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_DURABILITY_IS_ZERO
                        .getNumber());
    }

    @Test
    void theUnpairedNativeBooleanUsesFieldOne() {
        var message = parse((byte) 8, (byte) 1);
        var fields = message.getDescriptorForType().getFields();
        assertEquals(1, fields.size());
        var flag = fields.get(0);
        assertEquals(1, flag.getNumber());
        assertEquals("IFACBKIKAOL", flag.getName());
        assertEquals(FieldDescriptor.Type.BOOL, flag.getType());
        assertEquals(true, message.getField(flag));
    }

    @Test
    void theNormalSenderCanSendAnEmptyBodyForFalse() {
        var message = parse();
        assertEquals(false, message.getField(message.getDescriptorForType().findFieldByNumber(1)));
        assertArrayEquals(new byte[0], message.toByteArray());
    }

    @Test
    void trueRoundTripsAsTheNativeTagEight() {
        assertArrayEquals(new byte[] {8, 1}, parse((byte) 8, (byte) 1).toByteArray());
    }

    @Test
    void unknownFieldsArePreserved() {
        var message = parse((byte) 8, (byte) 1, (byte) 120, (byte) 7);
        assertEquals(7, message.getUnknownFields().getField(15).getVarintList().get(0));
        assertArrayEquals(new byte[] {8, 1, 120, 7}, message.toByteArray());
    }

    @Test
    void aTruncatedBooleanIsNotAnEmptyValidReport() throws Exception {
        var parser = type().getMethod("parseFrom", byte[].class);
        var thrown =
                assertThrows(
                        InvocationTargetException.class,
                        () -> parser.invoke(null, (Object) new byte[] {8}));
        assertInstanceOf(InvalidProtocolBufferException.class, thrown.getCause());
    }
}
