package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.mongodb.client.MongoClients;
import dev.morphia.Morphia;
import dev.morphia.annotations.*;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.AvatarCostumeData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.friends.PlayerProfile;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.props.ItemUseAction.ItemUseUnlockProfilePicture;
import emu.grasscutter.game.props.ItemUseAction.UseItemParams;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.game.props.ItemUseTarget;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetAllUnlockNameCardRspOuterClass.GetAllUnlockNameCardRsp;
import emu.grasscutter.net.proto.GetAllUnlockProfileDataRspOuterClass.GetAllUnlockProfileDataRsp;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SetPlayerHeadFrameReqOuterClass.SetPlayerHeadFrameReq;
import emu.grasscutter.net.proto.SetPlayerHeadFrameRspOuterClass.SetPlayerHeadFrameRsp;
import emu.grasscutter.net.proto.SetPlayerHeadImageReqOuterClass.SetPlayerHeadImageReq;
import emu.grasscutter.net.proto.SetPlayerHeadImageRspOuterClass.SetPlayerHeadImageRsp;
import emu.grasscutter.server.game.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ProfileCosmeticsTest {
    private final Gson gson = new Gson();
    private Map<Integer, ProfilePictureData> priorPictures;
    private Map<Integer, ProfileFrameData> priorFrames;
    private Map<Integer, AvatarCostumeData> priorCostumes;
    private final Map<Integer, ItemData> priorItems = new HashMap<>();
    private RecordingPlayer player;

    private static class RecordingPlayer extends Player {
        final List<BasePacket> packets = new ArrayList<>();
        int saves;

        @Override
        public void save() {
            saves++;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            if (packets != null) packets.add(packet);
        }

        @Override
        public Optional<GameHome> tryGetHome() {
            return Optional.empty();
        }
    }

    private static class RecordingSession extends GameSession {
        final List<BasePacket> packets = new ArrayList<>();

        RecordingSession(Player player) {
            super(null);
            setPlayer(player);
        }

        @Override
        public void send(BasePacket packet) {
            packets.add(packet);
        }
    }

    private static class OwnedAvatar extends Avatar {
        private final int id;

        OwnedAvatar(int id, int trial) {
            this.id = id;
            setTrialAvatarId(trial);
        }

        @Override
        public int getAvatarId() {
            return id;
        }
    }

    private <T> T[] rows(String name, Class<T[]> type) throws Exception {
        return gson.fromJson(Files.readString(Path.of("resources", "ExcelBinOutput", name)), type);
    }

    @BeforeEach
    void prepare() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        priorPictures = new HashMap<>(GameData.getProfilePictureDataMap());
        priorFrames = new HashMap<>(GameData.getProfileFrameDataMap());
        priorCostumes = new HashMap<>(GameData.getAvatarCostumeDataMap());
        GameData.getProfilePictureDataMap().clear();
        GameData.getProfileFrameDataMap().clear();
        GameData.getAvatarCostumeDataMap().clear();
        for (var row : rows("ProfilePictureExcelConfigData.json", ProfilePictureData[].class))
            GameData.getProfilePictureDataMap().put(row.getId(), row);
        for (var row : rows("ProfileFrameExcelConfigData.json", ProfileFrameData[].class))
            GameData.getProfileFrameDataMap().put(row.getId(), row);
        for (var row : rows("AvatarCostumeExcelConfigData.json", AvatarCostumeData[].class))
            GameData.getAvatarCostumeDataMap().put(row.getId(), row);
        for (var row : rows("MaterialExcelConfigData.json", ItemData[].class)) {
            if (row.getId() < 320001 || row.getId() > 320003) continue;
            row.onLoad();
            priorItems.put(row.getId(), GameData.getItemDataMap().put(row.getId(), row));
        }
        player = new RecordingPlayer();
        player.setNickname("Traveler");
        player.setSignature("");
    }

    @AfterEach
    void restore() {
        GameData.getProfilePictureDataMap().clear();
        GameData.getProfilePictureDataMap().putAll(priorPictures);
        GameData.getProfileFrameDataMap().clear();
        GameData.getProfileFrameDataMap().putAll(priorFrames);
        GameData.getAvatarCostumeDataMap().clear();
        GameData.getAvatarCostumeDataMap().putAll(priorCostumes);
        priorItems.forEach(
                (id, row) -> {
                    if (row == null) GameData.getItemDataMap().remove(id);
                    else GameData.getItemDataMap().put(id, row);
                });
    }

    @Test
    void actualCatalogLoadsEveryAliasAndTheNewerFrames() {
        assertTrue(GameData.getProfilePictureDataMap().size() >= 186);
        assertTrue(
                GameData.getProfilePictureDataMap().values().stream()
                        .allMatch(ProfilePictureData::isValid));
        assertEquals(
                5,
                GameData.getProfilePictureDataMap().values().stream()
                        .map(ProfilePictureData::getUnlockType)
                        .distinct()
                        .count());
        assertTrue(GameData.getProfileFrameDataMap().containsKey(100021));
        assertTrue(GameData.getProfileFrameDataMap().size() >= 12);
        assertEquals(ItemUseOp.ITEM_USE_UNLOCK_PROFILE_PICTURE, ItemUseOp.getTypeByValue(57));
    }

    @Test
    void legacyAvatarIdResolvesAPictureWithoutRewritingTheSave() {
        player.setHeadImage(10000021);
        int saves = player.saves;
        var portrait = player.getProfilePicture();
        assertEquals(100, portrait.getProfilePictureId());
        assertEquals(10000021, portrait.getAvatarId());
        assertEquals(100000, portrait.getProfileFrameId());
        assertEquals(0, player.getProfileCosmetics().getPictureId());
        assertEquals(saves, player.saves);
        assertEquals(10000021, player.getHeadImage());
    }

    @Test
    void onlyPermanentOwnedAvatarsUnlockPortraits() {
        assertFalse(player.selectProfilePicture(100));
        player.getAvatars().getAvatars().put(10000021, new OwnedAvatar(10000021, 1));
        assertFalse(player.selectProfilePicture(100));
        player.getAvatars().getAvatars().put(10000021, new OwnedAvatar(10000021, 0));
        assertTrue(player.selectProfilePicture(100));
        assertEquals(100, player.getProfilePicture().getProfilePictureId());
        assertFalse(player.getProfileCosmetics().isPictureUnlocked(player, 200));
    }

    @Test
    void costumePortraitKeepsTheCharacterAndCostumeIndependent() {
        assertFalse(player.selectProfilePicture(101));
        player.getCostumeList().add(202101);
        assertTrue(player.selectProfilePicture(101));
        var picture = player.getProfilePicture();
        assertEquals(101, picture.getProfilePictureId());
        assertEquals(10000021, picture.getAvatarId());
        assertEquals(202101, picture.getCostumeId());
        assertEquals(101, player.getOnlinePlayerInfo().getProfilePicture().getProfilePictureId());
    }

    @Test
    void questsAndDefaultsUseTheResourceUnlockConditions() {
        assertFalse(player.getProfileCosmetics().isPictureUnlocked(player, 100010));
        player.getForcedFinishedQuests().add(72105);
        assertTrue(player.getProfileCosmetics().isPictureUnlocked(player, 100010));
        assertTrue(player.getProfileCosmetics().isPictureUnlocked(player, 900003));
        assertFalse(player.getProfileCosmetics().isPictureUnlocked(player, -1));
        assertFalse(player.getProfileCosmetics().isPictureUnlocked(player, 123456789));
    }

    @Test
    void merelyReadingTheCatalogDoesNotGrantItemsOrQuests() {
        int saves = player.saves;
        var list = new emu.grasscutter.server.packet.send.PacketGetAllUnlockProfileDataRsp(player);
        assertEquals(22477, list.getOpcode());
        assertEquals(saves, player.saves);
        assertTrue(player.getInventory().getItems().isEmpty());
        assertTrue(player.getForcedFinishedQuests().isEmpty());
        assertTrue(player.getProfileCosmetics().getUnlockedPictures().isEmpty());
    }

    @Test
    void allThreeRealTicketsUnlockPermanentlyAndRefuseDuplicates() {
        var system = new InventorySystem(null);
        for (int itemId : new int[] {320001, 320002, 320003}) {
            var item =
                    new GameItem(GameData.getItemDataMap().get(itemId), 2) {
                        @Override
                        public void save() {}
                    };
            item.setOwner(player);
            player.getInventory().getItems().put(item.getGuid(), item);
            player.getInventory().getInventoryTab(item.getItemType()).onAddItem(item);
            assertSame(item, system.useItem(player, 0, item.getGuid(), 1, 0, false));
            assertEquals(1, item.getCount());
            assertNull(system.useItem(player, 0, item.getGuid(), 1, 0, false));
            assertEquals(1, item.getCount());
        }
        assertEquals(Set.of(99999, 200010, 200011), player.getProfileCosmetics().getUnlockedPictures());
        assertTrue(player.selectProfilePicture(200011));
        assertEquals(200011, player.getProfilePicture().getProfilePictureId());
    }

    @Test
    void invalidOrMismatchedTicketCannotConsumeInventory() {
        var params = new UseItemParams(player, ItemUseTarget.ITEM_USE_TARGET_NONE);
        params.usedItemId = 320001;
        assertFalse(new ItemUseUnlockProfilePicture(new String[] {"200010"}).useItem(params));
        assertFalse(new ItemUseUnlockProfilePicture(null).useItem(params));
        assertFalse(new ItemUseUnlockProfilePicture(new String[] {"bad"}).useItem(params));
        assertFalse(new ItemUseUnlockProfilePicture(new String[] {"100"}).useItem(params));
        params.count = 2;
        assertFalse(new ItemUseUnlockProfilePicture(new String[] {"99999"}).useItem(params));
        assertTrue(player.getProfileCosmetics().getUnlockedPictures().isEmpty());
        assertEquals(0, player.saves);
    }

    @Test
    void framesRequireAnExplicitRewardGrantAndNeverUnlockTheWholeCatalog() {
        assertEquals(List.of(100000), player.getProfileCosmetics().unlockedFrameIds());
        assertFalse(player.selectProfileFrame(100021));
        assertFalse(player.unlockProfileFrame(42));
        assertTrue(player.unlockProfileFrame(100021));
        assertFalse(player.unlockProfileFrame(100021));
        assertTrue(player.selectProfileFrame(100021));
        assertEquals(List.of(100000, 100021), player.getProfileCosmetics().unlockedFrameIds());
        assertEquals(100021, player.getProfilePicture().getProfileFrameId());
    }

    private BasePacket request(int opcode, byte[] body) throws Exception {
        var registry = new GameServerPacketHandler(PacketHandler.class);
        var field = GameServerPacketHandler.class.getDeclaredField("handlers");
        field.setAccessible(true);
        var handler = ((Int2ObjectMap<PacketHandler>) field.get(registry)).get(opcode);
        assertNotNull(handler);
        var session = new RecordingSession(player);
        var header = PacketHead.newBuilder().setClientSequenceId(42).build().toByteArray();
        handler.handle(session, header, body);
        assertEquals(1, session.packets.size());
        var packet = session.packets.get(0);
        assertEquals(42, PacketHead.parseFrom(packet.getHeader()).getClientSequenceId());
        return packet;
    }

    @Test
    void actualHandlersReplyOnceAndRejectLockedSelections() throws Exception {
        var body = SetPlayerHeadImageReq.newBuilder().setProfilePictureId(100).build().toByteArray();
        var denied = request(6969, body);
        assertEquals(1191, denied.getOpcode());
        assertEquals(
                Retcode.RET_PROFILE_PICTURE_NOT_UNLOCKED_VALUE,
                SetPlayerHeadImageRsp.parseFrom(denied.getData()).getRetcode());
        assertEquals(0, player.saves);
        player.getAvatars().getAvatars().put(10000021, new OwnedAvatar(10000021, 0));
        var allowed = request(6969, body);
        assertEquals(0, SetPlayerHeadImageRsp.parseFrom(allowed.getData()).getRetcode());
        assertEquals(
                100,
                SetPlayerHeadImageRsp.parseFrom(allowed.getData())
                        .getProfilePicture()
                        .getProfilePictureId());
        var frame =
                request(
                        21517,
                        SetPlayerHeadFrameReq.newBuilder().setProfileFrameId(100021).build().toByteArray());
        assertEquals(9314, frame.getOpcode());
        assertNotEquals(0, SetPlayerHeadFrameRsp.parseFrom(frame.getData()).getRetcode());
        assertEquals(100000, player.getProfileCosmetics().getFrameId());
        player.unlockProfileFrame(100021);
        frame =
                request(
                        21517,
                        SetPlayerHeadFrameReq.newBuilder().setProfileFrameId(100021).build().toByteArray());
        assertEquals(0, SetPlayerHeadFrameRsp.parseFrom(frame.getData()).getRetcode());
        assertEquals(
                100021,
                SetPlayerHeadFrameRsp.parseFrom(frame.getData()).getProfilePicture().getProfileFrameId());
    }

    @Test
    void nativeListHandlersReturnOwnedSortedEntriesOnly() throws Exception {
        player.getAvatars().getAvatars().put(10000021, new OwnedAvatar(10000021, 0));
        player.unlockProfilePicture(200010, 320002);
        var profile = request(4179, new byte[0]);
        var list = GetAllUnlockProfileDataRsp.parseFrom(profile.getData());
        assertEquals(22477, profile.getOpcode());
        assertTrue(list.getProfilePictureListList().containsAll(List.of(100, 200010, 900003)));
        assertFalse(list.getProfilePictureListList().contains(200));
        assertFalse(list.getProfilePictureListList().contains(100010));
        assertEquals(List.of(100000), list.getProfileFrameListList());
        assertEquals(
                list.getProfilePictureListList().stream().sorted().toList(),
                list.getProfilePictureListList());
        player.getNameCardList().addAll(List.of(210005, 210001, 210003));
        var namecards = request(5683, new byte[0]);
        assertEquals(4400, namecards.getOpcode());
        assertEquals(
                List.of(210001, 210003, 210005),
                GetAllUnlockNameCardRsp.parseFrom(namecards.getData()).getNameCardListList());
    }

    @Test
    void cachedFriendPortraitKeepsCostumeAndFrameAndMigratesOldAvatarSnapshots() {
        player.getCostumeList().add(202101);
        player.selectProfilePicture(101);
        player.unlockProfileFrame(100021);
        player.selectProfileFrame(100021);
        var snapshot = new PlayerProfile(player).toProfilePictureProto();
        assertEquals(player.getProfilePicture(), snapshot);
        var legacy = gson.fromJson("{\"avatarId\":10000021}", PlayerProfile.class);
        assertEquals(100, legacy.toProfilePictureProto().getProfilePictureId());
        assertEquals(100000, legacy.toProfilePictureProto().getProfileFrameId());
    }

    @Entity(value = "profile_verification", useDiscriminator = false)
    static class Snapshot {
        @Id private String id = "one";
        private ProfileCosmetics cosmetics;

        public Snapshot() {}

        Snapshot(ProfileCosmetics cosmetics) {
            this.cosmetics = cosmetics;
        }
    }

    @Test
    void mongoRoundTripPreservesConsumedTicketSelectedPortraitAndFrame() {
        var uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        player.unlockProfilePicture(200011, 320003);
        player.selectProfilePicture(200011);
        player.unlockProfileFrame(100021);
        player.selectProfileFrame(100021);
        var database = "lunagc_profile_verify_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = MongoClients.create(uri)) {
            try {
                var datastore =
                        Morphia.createDatastore(
                                client, database, MapperOptions.builder().storeEmpties(true).build());
                datastore.getMapper().map(Snapshot.class, ProfileCosmetics.class);
                datastore.save(new Snapshot(player.getProfileCosmetics()));
                var loaded = datastore.find(Snapshot.class).first().cosmetics;
                assertEquals(200011, loaded.getPictureId());
                assertEquals(100021, loaded.getFrameId());
                assertTrue(loaded.isPictureUnlocked(player, 200011));
                assertFalse(loaded.unlockPicture(200011, 320003));
                assertEquals(player.getProfilePicture(), loaded.toProto(0));
            } finally {
                client.getDatabase(database).drop();
            }
        }
    }
}
