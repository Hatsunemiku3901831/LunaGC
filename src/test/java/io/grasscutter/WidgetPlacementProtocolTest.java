package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.UnknownFieldSet;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.send.*;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

/** Field numbers come from the hash-checked 7.1 native readers, not generated descriptors. */
class WidgetPlacementProtocolTest {
    @Test
    void bagResponseUsesTheCurrentCommand() {
        assertEquals(21894, new PacketWidgetDoBagRsp(220026).getOpcode());
    }

    @Test
    void bagResponseIdentifiesTheMaterialInField13() throws Exception {
        var fields = UnknownFieldSet.parseFrom(new PacketWidgetDoBagRsp(220026).getData());
        assertEquals(220026L, fields.getField(13).getVarintList().stream().findFirst().orElse(-1L));
        assertFalse(fields.hasField(12));
    }

    @Test
    void createResponseUsesTheCurrentCommand() {
        assertEquals(7173, new PacketUseWidgetCreateGadgetRsp(220026).getOpcode());
    }

    @Test
    void createResponseUsesNativeReturnAndMaterialFields() throws Exception {
        var packet = new PacketUseWidgetCreateGadgetRsp(601, 220026);
        var fields = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(601L, fields.getField(5).getVarintList().stream().findFirst().orElse(-1L));
        assertEquals(220026L, fields.getField(11).getVarintList().stream().findFirst().orElse(-1L));
        assertEquals(2, fields.asMap().size());
    }

    @Test
    void createRequestReadsTheNativeMaterialPositionAndRotation() throws Exception {
        var pos = VectorOuterClass.Vector.newBuilder().setX(2).setY(3).setZ(4).build();
        var rot = VectorOuterClass.Vector.newBuilder().setY(90).build();
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        wire.writeUInt32(4, 220026);
        wire.writeMessage(14, pos);
        wire.writeMessage(15, rot);
        wire.flush();
        var request =
                UseWidgetCreateGadgetReqOuterClass.UseWidgetCreateGadgetReq.parseFrom(
                        bytes.toByteArray());
        assertEquals(220026, request.getMaterialId());
        assertEquals(pos, request.getPos());
        assertEquals(rot, request.getRot());
        assertEquals(20020, PacketOpcodes.UseWidgetCreateGadgetReq);
    }

    @Test
    void bagCreatorRequestRetainsTheVerifiedNativeNesting() throws Exception {
        var creator = new ByteArrayOutputStream();
        var inner = CodedOutputStream.newInstance(creator);
        inner.writeEnum(1, 2);
        inner.writeUInt32(13, 42);
        inner.flush();
        var bytes = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(bytes);
        wire.writeUInt32(2, 220026);
        wire.writeByteArray(1378, creator.toByteArray());
        wire.flush();
        var request = WidgetDoBagReqOuterClass.WidgetDoBagReq.parseFrom(bytes.toByteArray());
        assertEquals(220026, request.getMaterialId());
        assertTrue(request.hasWidgetCreatorInfo());
        assertEquals(2, request.getWidgetCreatorInfo().getOpTypeValue());
        assertEquals(42, request.getWidgetCreatorInfo().getEntityId());
    }
}
