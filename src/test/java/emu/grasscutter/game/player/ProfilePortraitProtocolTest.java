package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.GetAllUnlockProfileDataRspOuterClass.GetAllUnlockProfileDataRsp;
import emu.grasscutter.net.proto.ProfilePictureOuterClass.ProfilePicture;
import emu.grasscutter.net.proto.SetPlayerHeadFrameReqOuterClass.SetPlayerHeadFrameReq;
import emu.grasscutter.net.proto.SetPlayerHeadFrameRspOuterClass.SetPlayerHeadFrameRsp;
import emu.grasscutter.net.proto.SetPlayerHeadImageReqOuterClass.SetPlayerHeadImageReq;
import emu.grasscutter.net.proto.SetPlayerHeadImageRspOuterClass.SetPlayerHeadImageRsp;
import emu.grasscutter.net.proto.SocialDetailOuterClass.SocialDetail;
import emu.grasscutter.server.game.GameServerPacketHandler;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.junit.jupiter.api.*;

class ProfilePortraitProtocolTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    @Test
    void portraitFrameAndNamecardRequestsAreRegistered() throws Exception {
        var registry = new GameServerPacketHandler(PacketHandler.class);
        var field = GameServerPacketHandler.class.getDeclaredField("handlers");
        field.setAccessible(true);
        var handlers = (Int2ObjectMap<?>) field.get(registry);
        // Literal getters MLHHHHIAGOF / IEAEDEPOEHM / EGBGKBEJNKI / OCLEKCLPLAL.
        for (int opcode : new int[] {6969, 21517, 4179, 5683}) {
            assertNotNull(handlers.get(opcode), "Missing native profile request " + opcode);
        }
    }

    @Test
    void nativeSelectionReadsPictureIdFromFieldEight() throws Exception {
        // MLHHHHIAGOF writer/readers: picture id's tag is 0x40.
        assertEquals(
                100, SetPlayerHeadImageReq.parseFrom(new byte[] {0x40, 100}).getProfilePictureId());
    }

    @Test
    void profilePictureKeepsFrameAvatarPictureAndCostumeFieldsDistinct() throws Exception {
        // MFDLDKCDGCF writer 0x150974660: offsets 24/18/20/1c -> tags 08/10/18/20.
        var portrait = ProfilePicture.parseFrom(new byte[] {8, 11, 16, 21, 24, 100, 32, 22});
        assertEquals(11, portrait.getProfileFrameId());
        assertEquals(21, portrait.getAvatarId());
        assertEquals(100, portrait.getProfilePictureId());
        assertEquals(22, portrait.getCostumeId());
    }

    @Test
    void nativePictureResponseReadsFailureFromFieldSix() throws Exception {
        // KOFOEMBDLMO reader 0x14b48f3f0: retcode=6, profile picture=14.
        assertEquals(
                401, SetPlayerHeadImageRsp.parseFrom(new byte[] {0x30, (byte) 0x91, 3}).getRetcode());
    }

    @Test
    void birthdayNeverBecomesAPictureOrAFrame() throws Exception {
        // IIBOEFCLJEA is Birthday, including SetPlayerBirthdayReq (27497).
        var detail = SocialDetail.parseFrom(new byte[] {0x32, 4, 8, 10, 16, 8});
        assertEquals(10, detail.getBirthday().getMonth());
        assertEquals(8, detail.getBirthday().getDay());
        assertFalse(detail.hasProfilePicture());
    }

    @Test
    void nativeFrameSelectionAndResponseUseTheirOwnFields() throws Exception {
        // IEAEDEPOEHM writes tag 0x48; BGLAIJBKLAJ reads 0x1a / 0x78.
        var request = SetPlayerHeadFrameReq.parseFrom(new byte[] {0x48, (byte) 0xb5, (byte) 0x8d, 6});
        assertEquals(100021, request.getProfileFrameId());
        var response = SetPlayerHeadFrameRsp.parseFrom(new byte[] {0x1a, 2, 8, 11, 0x78, 5});
        assertEquals(11, response.getProfilePicture().getProfileFrameId());
        assertEquals(5, response.getRetcode());
    }

    @Test
    void nativeProfileListKeepsPicturesAndFramesInSeparatePackedLists() throws Exception {
        // MFLHNBCGNJP reader accepts tags 0x20/0x22 and 0x78/0x7a, with retcode=9.
        var list =
                GetAllUnlockProfileDataRsp.parseFrom(
                        new byte[] {0x22, 2, 1, 100, 0x7a, 3, (byte) 0xa0, (byte) 0x8d, 6});
        assertEquals(java.util.List.of(1, 100), list.getProfilePictureListList());
        assertEquals(java.util.List.of(100000), list.getProfileFrameListList());
        assertEquals(0, list.getRetcode());
    }
}
