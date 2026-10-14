package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.VehiclePhlogistonPointsNotifyOuterClass.VehiclePhlogistonPointsNotify;
import emu.grasscutter.server.packet.send.PacketVehiclePhlogistonPointsNotify;

import org.junit.jupiter.api.*;

import java.util.*;

class VehiclePhlogistonProtocolTest {
    PhlogistonMutationTest env;

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMutationTest();
        env.setup();
    }

    @AfterEach
    void close() {
        env.close();
    }

    @Test
    void currentClientReceivesFuelAt13AndEntityAt15() throws Exception {
        var vehicle = env.environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.setCurPhlogiston(17.5f);
        var packet = new PacketVehiclePhlogistonPointsNotify(vehicle);
        assertEquals(7926, packet.getOpcode());
        assertEquals(7926, PacketOpcodes.VehiclePhlogistonPointsNotify);
        var wire = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(Set.of(13, 15), wire.asMap().keySet());
        assertEquals(List.of(Float.floatToRawIntBits(17.5f)), wire.getField(13).getFixed32List());
        assertEquals(List.of((long) vehicle.getId()), wire.getField(15).getVarintList());
    }

    @Test
    void nativeDecoderTagShapeMapsToPublicFieldNames() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        output.writeFloat(13, 12.5f);
        output.writeUInt32(15, 0x04000042);
        output.flush();
        var parsed = VehiclePhlogistonPointsNotify.parseFrom(bytes.toByteArray());
        assertEquals(12.5f, parsed.getCurPhlogiston());
        assertEquals(0x04000042, parsed.getEntityId());
        assertEquals(UnknownFieldSet.getDefaultInstance(), parsed.getUnknownFields());
    }

    @Test
    void emptyPoolRetainsVehicleIdWithoutInventingFuel() throws Exception {
        var vehicle = env.environment.candle(NatsaurusPossessionTest.DRILL);
        vehicle.setCurPhlogiston(0);
        var packet = new PacketVehiclePhlogistonPointsNotify(vehicle);
        assertEquals(7926, packet.getOpcode());
        var wire = UnknownFieldSet.parseFrom(packet.getData());
        assertEquals(Set.of(15), wire.asMap().keySet());
        assertEquals(List.of((long) vehicle.getId()), wire.getField(15).getVarintList());
    }
}
