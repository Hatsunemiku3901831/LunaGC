package emu.grasscutter.game.gacha;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.net.proto.DoGachaRspOuterClass.DoGachaRsp;
import emu.grasscutter.net.proto.GachaItemOuterClass.GachaItem;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.server.packet.send.PacketDoGachaRsp;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GachaRadianceTest {
    private static final int FEATURED = 1058;
    private static final int FALLBACK = 1042;
    private static GachaSystem system;

    @BeforeAll
    static void bootstrap() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        system =
                new GachaSystem(null) {
                    @Override
                    public synchronized boolean load() {
                        return true;
                    }

                    @Override
                    public int randomRange(int min, int max) {
                        return min;
                    }
                };
    }

    private GachaBanner banner(String type, int chance, String radiance) {
        var banner =
                new Gson()
                        .fromJson(
                                "{\"bannerType\":\""
                                        + type
                                        + "\",\"prefabPath\":\"test\",\"eventChance5\":"
                                        + chance
                                        + (radiance == null ? "" : ",\"capturingRadianceChances\":" + radiance)
                                        + "}",
                                GachaBanner.class);
        banner.onLoad();
        return banner;
    }

    private GachaSystem.PullResult pull(GachaBanner banner, PlayerGachaBannerInfo state) {
        return system.doRarePull(
                new int[] {FEATURED}, new int[] {FALLBACK}, new int[0], 5, banner, state, null);
    }

    private DoGachaRsp packet(GachaBanner banner, PlayerGachaBannerInfo state, boolean captured)
            throws Exception {
        // The preceding normal gold pull must not hide a capture later in a ten-pull response.
        var items =
                List.of(
                        GachaItem.newBuilder().setGachaItem(ItemParam.newBuilder().setItemId(11501)).build(),
                        GachaItem.newBuilder()
                                .setGachaItem(ItemParam.newBuilder().setItemId(FEATURED))
                                .setIsFlashCard(captured)
                                .build());
        return DoGachaRsp.parseFrom(new PacketDoGachaRsp(banner, items, state).getData());
    }

    @Test
    void winningTheCoinFlipIsAnOrdinaryGoldPull() throws Exception {
        var banner = banner("CHARACTER", 100, "[100]");
        var state = new PlayerGachaBannerInfo();
        state.setConsecutiveFeaturedLosses(2);
        var result = pull(banner, state);
        assertEquals(FEATURED, result.itemId());
        assertFalse(result.capturedRadiance());
        assertEquals(0, state.getConsecutiveFeaturedLosses());
        assertFalse(packet(banner, state, result.capturedRadiance()).getIsCapturingRadiance());
    }

    @Test
    void guaranteedFeaturedPullDoesNotTriggerCaptureOrClearTheCoinFlipStreak() throws Exception {
        var banner = banner("CHARACTER", 0, "[100]");
        var state = new PlayerGachaBannerInfo();
        state.setFailedFeaturedItemPulls(5, 1);
        state.setConsecutiveFeaturedLosses(2);
        var result = pull(banner, state);
        assertEquals(FEATURED, result.itemId());
        assertFalse(result.capturedRadiance());
        assertEquals(0, state.getFailedFeaturedItemPulls(5));
        assertEquals(2, state.getConsecutiveFeaturedLosses());
        assertFalse(packet(banner, state, result.capturedRadiance()).getIsCapturingRadiance());
    }

    @Test
    void rescuedCoinFlipSignalsCaptureInTheResponse() throws Exception {
        var banner = banner("CHARACTER", 0, "[100]");
        var state = new PlayerGachaBannerInfo();
        state.setConsecutiveFeaturedLosses(2);
        var result = pull(banner, state);
        assertEquals(FEATURED, result.itemId());
        assertTrue(result.capturedRadiance());
        assertEquals(0, state.getFailedFeaturedItemPulls(5));
        assertEquals(0, state.getConsecutiveFeaturedLosses());
        assertTrue(packet(banner, state, result.capturedRadiance()).getIsCapturingRadiance());
    }

    @Test
    void losingWithoutCaptureAddsOneLossAndTheFollowingGuaranteePreservesIt() {
        var banner = banner("CHARACTER", 0, "[0]");
        var state = new PlayerGachaBannerInfo();
        state.setConsecutiveFeaturedLosses(1);
        var lost = pull(banner, state);
        assertEquals(FALLBACK, lost.itemId());
        assertFalse(lost.capturedRadiance());
        assertEquals(1, state.getFailedFeaturedItemPulls(5));
        assertEquals(2, state.getConsecutiveFeaturedLosses());
        var guaranteed = pull(banner, state);
        assertEquals(FEATURED, guaranteed.itemId());
        assertFalse(guaranteed.capturedRadiance());
        assertEquals(2, state.getConsecutiveFeaturedLosses());
    }

    @Test
    void nonCharacterBannersHaveNoDefaultCaptureEvenAfterManyLosses() throws Exception {
        for (String type : List.of("WEAPON", "STANDARD", "BEGINNER", "CHRONICLE")) {
            var banner = banner(type, 0, null);
            var state = new PlayerGachaBannerInfo();
            state.setConsecutiveFeaturedLosses(100);
            assertEquals(0, banner.getCapturingRadianceChance(100));
            var result = pull(banner, state);
            assertEquals(FALLBACK, result.itemId());
            assertFalse(result.capturedRadiance());
            assertFalse(packet(banner, state, result.capturedRadiance()).getIsCapturingRadiance());
        }
    }
}
