package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneNotify;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Protocol72EnterSceneNotifyProducerTest {
    @BeforeAll static void initializeConfiguration() { Grasscutter.getConfig(); }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void entryAndTeleportConstructorsOnlySendTheFourNativeBoundFields(int variant) throws Exception {
        var player = new FixedTokenPlayer(47259);
        var properties = properties();
        var packet = switch (variant) {
            case 0 -> new PacketPlayerEnterSceneNotify(player);
            case 1 -> new PacketPlayerEnterSceneNotify(player, properties);
            case 2 -> new PacketPlayerEnterSceneNotify(player, player, properties);
            case 3 -> new PacketPlayerEnterSceneNotify(player, player, EnterType.EnterType_ENTER_JUMP,
                EnterReason.TransPoint, 3, properties.getTeleportTo());
            case 4 -> new PacketPlayerEnterSceneNotify(player, 123456, properties, false);
            default -> new PacketPlayerEnterSceneNotify(player, 123456, properties, true);
        };
        assertEquals(27748, packet.getOpcode());
        assertEquals("PlayerEnterSceneNotify", PacketOpcodesUtils.getOpcodeName(27748));
        assertEquals(SceneLoadState.LOADING, player.getSceneLoadState());
        assertEquals(47259, player.getEnterSceneToken());
        int type = variant == 0 ? 1 : variant < 4 ? 3 : variant == 4 ? 9 : 10;
        // Native sceneId fixture raw4294906403 -> entering3; native token
        // fixture raw1 -> logical47259; target Vector x/y/z = 1.25/-2.5/65536.
        String wire = "38a3a4fcff0f40" + HexFormat.of().toHexDigits((byte) type)
            + "5001720f0d0000a03f15000020c01d00008047";
        assertArrayEquals(HexFormat.of().parseHex(wire), packet.getData());
        var fields = UnknownFieldSet.parseFrom(packet.getData()).asMap();
        assertEquals(Set.of(7, 8, 10, 14), fields.keySet());
        assertEquals(4294906403L, fields.get(7).getVarintList().get(0));
        assertEquals(1L, fields.get(10).getVarintList().get(0));
    }

    @Test
    void existingServerEnumAdapterMatchesAllFifteenNativeEnterValues() {
        // Target enum86551 independently decoded symbols in numeric order.
        String[] names = {"EnterType_ENTER_NONE", "EnterType_ENTER_SELF", "EnterType_ENTER_GOTO",
            "EnterType_ENTER_JUMP", "EnterType_ENTER_OTHER", "EnterType_ENTER_BACK", "EnterType_ENTER_DUNGEON",
            "EnterType_ENTER_DUNGEON_REPLAY", "EnterType_ENTER_GOTO_BY_PORTAL", "EnterType_ENTER_SELF_HOME",
            "EnterType_ENTER_OTHER_HOME", "EnterType_ENTER_GOTO_RECREATE", "EnterType_ENTER_GOTO_BY_TPL",
            "ENTER_BEYOND", "ENTER_HALL"};
        for (int i = 0; i < names.length; i++) assertEquals(i, EnterType.valueOf(names[i]).getNumber());
    }

    @Test
    void nativeRawZeroTokenStillHasAPresentField() throws Exception {
        // Native Notify raw0 -> Ready chain yields logical47256. Suppressing
        // this explicit value would leave the target constructor default.
        var packet = new PacketPlayerEnterSceneNotify(new FixedTokenPlayer(47256));
        assertArrayEquals(HexFormat.of().parseHex(
            "38a3a4fcff0f40015000720f0d0000a03f15000020c01d00008047"), packet.getData());
        assertEquals(0L, UnknownFieldSet.parseFrom(packet.getData()).getField(10).getVarintList().get(0));
    }

    private static TeleportProperties properties() {
        return TeleportProperties.builder().sceneId(3).dungeonId(17)
            .teleportTo(new Position(1.25f, -2.5f, 65536f))
            .enterType(EnterType.EnterType_ENTER_JUMP).enterReason(EnterReason.TransPoint).build();
    }

    private static final class FixedTokenPlayer extends Player {
        private final int token;
        FixedTokenPlayer(int token) {
            this.token = token;
            setSceneId(3);
            getPosition().set(new Position(1.25f, -2.5f, 65536f));
        }
        @Override public void setEnterSceneToken(int generated) {
            assertTrue(generated >= 1000 && generated < 99999);
            super.setEnterSceneToken(token);
        }
    }
}
