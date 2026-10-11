package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.net.proto.AsterMidCampInfoNotifyOuterClass.AsterMidCampInfoNotify;
import emu.grasscutter.net.proto.AsterMidInfoNotifyOuterClass.AsterMidInfoNotify;
import emu.grasscutter.net.proto.GadgetGeneralRewardInfoOuterClass.GadgetGeneralRewardInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.data.SceneMeta;

import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class AsterMidRecoveryTest {
    private AsterMidTestFixture fixture;

    @BeforeEach
    void resources() throws Exception {
        fixture = new AsterMidTestFixture();
    }

    @AfterEach
    void restore() {
        fixture.close();
    }

    @Test
    void allSixtyOriginalControlAndBattleGroupsLoadWithTheRealCommonScript() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        assertTrue(
                emu.grasscutter.config.Configuration.FAST_REQUIRE,
                "Exercise the default fast-require path");
        var meta = SceneMeta.of(3);
        assertNotNull(meta);
        assertTrue(AsterMidCamps.resourcesAvailable());
        var seen = new HashSet<Integer>();
        int commonScripts = 0;
        for (var camp : AsterMidCamps.camps().values()) {
            var core =
                    AsterMidSceneController.definition(
                                    camp.groupId(), camp.groupPosition(), meta.blocks.values())
                            .load(3);
            assertEquals(camp.sealGadgetId(), core.gadgets.get(camp.sealConfigId()).gadget_id);
            assertEquals(70950021, core.gadgets.get(camp.rewardConfigId()).gadget_id);
            if (core.getBindings().get("action_group_load") != null) {
                assertTrue(
                        core.triggers.values().stream()
                                .anyMatch(t -> t.getName().equals("GROUP_LOAD")));
                commonScripts++;
            }
            assertTrue(core.variables.stream().anyMatch(v -> v.name.equals("isDone")));
            assertTrue(seen.add(core.id));
            for (var entry : camp.battleGroups().entrySet()) {
                var battle =
                        AsterMidSceneController.definition(
                                        entry.getKey(), entry.getValue(), meta.blocks.values())
                                .load(3);
                assertTrue(battle.getSuiteByIndex(camp.emptySuite()).sceneMonsters.isEmpty());
                assertFalse(battle.monsters.isEmpty());
                assertTrue(battle.monsters.values().stream().allMatch(m -> m.kill_score >= 0));
                assertTrue(seen.add(battle.id));
            }
        }
        assertEquals(120, seen.size());
        assertEquals(
                20,
                commonScripts,
                "Only the original scripts requiring AsterMiddle gain its group-load hook");
    }

    @Test
    void actualDropsIncludeEnergyAndBothExpTypesWithoutUsingPreviewIdsAsRewardIds() {
        assertTrue(AsterMidCamps.resourcesAvailable());
        assertEquals(new AsterMidRewards.Reward(20, 14, 100, 10, 15), AsterMidRewards.reward(1));
        assertEquals(new AsterMidRewards.Reward(20, 54, 100, 15, 35), AsterMidRewards.reward(5));
        assertEquals(new AsterMidRewards.Reward(20, 94, 100, 20, 45), AsterMidRewards.reward(9));
        assertFalse(GameData.getAsterMidDifficultyDataMap().get(9).getWorldLevelVec().contains(8));
        assertFalse(GameData.getRewardDataMap().containsKey(9101));
        var original = GameData.getDropTableDataMap().remove(210010000);
        assertNull(AsterMidRewards.reward(1));
        assertFalse(AsterMidCamps.resourcesAvailable());
        GameData.getDropTableDataMap().put(original.getId(), original);
    }

    @Test
    void thirtyRecoveriesPersistProgressAndCompleteAllSixMissionsOnce() {
        var debits = new AtomicInteger();
        var grants = new AtomicInteger();
        var saves = new AtomicInteger();
        for (int i = 0; i < 30; i++) {
            var progress = AsterSchedule.progress(fixture.data);
            if (progress.midBatchFinished()) {
                progress.startMidBatch(1);
                fixture.data.setDetail(progress);
            }
            int camp = fixture.offer(i % 4, 3);
            assertEquals(
                    0,
                    take(
                            camp,
                            3,
                            () -> {
                                debits.incrementAndGet();
                                return true;
                            },
                            r -> {
                                assertEquals(25, r.energy());
                                grants.incrementAndGet();
                            },
                            saves::incrementAndGet));
            assertNotEquals(
                    0,
                    take(
                            camp,
                            3,
                            () -> fail("duplicate debit"),
                            r -> fail("duplicate grant"),
                            () -> fail()));
            // Decode a fresh document after each acknowledgement, as a reconnect does.
            assertEquals(i + 1, AsterSchedule.progress(fixture.data).midCollectCount());
            assertEquals(25 * (i + 1), AsterSchedule.progress(fixture.data).credit());
        }
        assertEquals(30, debits.get());
        assertEquals(30, grants.get());
        assertEquals(60, saves.get());
        for (int id = 1200110; id <= 1200115; id++)
            assertTrue(fixture.data.getWatcherInfoMap().get(id).isFinished());
        var totals = new HashMap<Integer, Integer>();
        for (int id = 1200110; id <= 1200115; id++) {
            assertEquals(
                    0,
                    AsterWatcherRewards.take(
                            fixture.data,
                            fixture.config,
                            20,
                            fixture.now,
                            id,
                            reward -> 0,
                            reward ->
                                    GameData.getRewardDataMap()
                                            .get(reward)
                                            .getRewardItemList()
                                            .forEach(
                                                    p ->
                                                            totals.merge(
                                                                    p.getId(),
                                                                    p.getCount(),
                                                                    Integer::sum)),
                            () -> {}));
            assertEquals(
                    Retcode.RET_ACTIVITY_WATCHER_REWARD_TAKEN_VALUE,
                    AsterWatcherRewards.take(
                            fixture.data,
                            fixture.config,
                            20,
                            fixture.now,
                            id,
                            r -> fail(),
                            r -> fail(),
                            () -> fail()));
        }
        assertTrue(totals.get(201) > 0);
        assertTrue(totals.get(202) > 0);
    }

    @Test
    void insufficientResinKeepsTheOfferForRetry() {
        int camp = fixture.offer(0, 1);
        assertEquals(
                Retcode.RET_RESIN_NOT_ENOUGH_VALUE,
                take(camp, 1, () -> false, r -> fail(), () -> {}));
        var progress = AsterSchedule.progress(fixture.data);
        assertEquals(0, progress.pendingMidClaim());
        assertEquals(0, progress.midCollectCount());
        assertEquals(1, progress.midOffers().get(camp));
        assertEquals(0, take(camp, 1, () -> true, r -> {}, () -> {}));
        assertTrue(AsterSchedule.progress(fixture.data).midFinishedCamps().contains(camp));
    }

    @Test
    void reentrantGrantAndReloadedReservationCannotGrantTwice() {
        int camp = fixture.offer(0, 2);
        assertThrows(
                IllegalStateException.class,
                () ->
                        take(
                                camp,
                                2,
                                () -> true,
                                reward -> {
                                    assertEquals(
                                            Retcode.RET_SVR_ERROR_VALUE,
                                            take(camp, 2, () -> fail(), r -> fail(), () -> fail()));
                                    throw new IllegalStateException("Partial EXP write");
                                },
                                () -> {}));
        assertEquals(camp, AsterSchedule.progress(fixture.data).pendingMidClaim());
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                take(camp, 2, () -> fail(), r -> fail(), () -> fail()));
        assertFalse(fixture.data.getWatcherInfoMap().get(1200110).isFinished());
    }

    @Test
    void unacknowledgedReservationFailsBeforeDebitAndAcknowledgementFailureRemainsReserved() {
        int camp = fixture.offer(0, 1);
        String before = fixture.data.getDetail();
        assertThrows(
                IllegalStateException.class,
                () ->
                        take(
                                camp,
                                1,
                                () -> fail(),
                                r -> fail(),
                                () -> {
                                    throw new IllegalStateException("save failed");
                                }));
        assertEquals(before, fixture.data.getDetail());
        var saves = new AtomicInteger();
        var grants = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        take(
                                camp,
                                1,
                                () -> true,
                                r -> grants.incrementAndGet(),
                                () -> {
                                    if (saves.incrementAndGet() == 2)
                                        throw new IllegalStateException("ack failed");
                                }));
        assertEquals(1, grants.get());
        assertEquals(camp, AsterSchedule.progress(fixture.data).pendingMidClaim());
        assertEquals(0, fixture.data.getWatcherInfoMap().get(1200110).getCurProgress());
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                take(camp, 1, () -> fail(), r -> fail(), () -> fail()));
    }

    @Test
    void wrongCampDifficultyScheduleAndClosedChapterCannotSpendResin() {
        int camp = fixture.offer(0, 1);
        assertNotEquals(0, take(camp, 2, () -> fail(), r -> fail(), () -> fail()));
        assertNotEquals(0, take(123456, 1, () -> fail(), r -> fail(), () -> fail()));
        assertEquals(
                Retcode.RET_ACTIVITY_CLOSE_VALUE,
                AsterMidRewards.take(
                        fixture.data,
                        fixture.config,
                        19,
                        fixture.now,
                        camp,
                        1,
                        () -> fail(),
                        r -> fail(),
                        () -> fail()));
        fixture.data.setScheduleId(fixture.config.getScheduleId() + 1);
        assertEquals(
                Retcode.RET_ACTIVITY_CLOSE_VALUE,
                take(camp, 1, () -> fail(), r -> fail(), () -> fail()));
        fixture.data.setScheduleId(fixture.config.getScheduleId());
        assertEquals(
                Retcode.RET_ACTIVITY_CLOSE_VALUE,
                AsterMidRewards.take(
                        fixture.data,
                        fixture.config,
                        20,
                        AsterSchedule.contentCloseTime(fixture.config),
                        camp,
                        1,
                        () -> fail(),
                        r -> fail(),
                        () -> fail()));
    }

    @Test
    void batchRefreshRequiresAllFourClaimsAndKeepsExplorationCurrency() {
        var progress = AsterSchedule.progress(fixture.data);
        var fragment = AsterFragments.areas().values().iterator().next();
        assertTrue(progress.collect(fragment.groupId(), fragment.configIds().iterator().next()));
        fixture.data.setDetail(progress);
        for (int i = 0; i < 4; i++) {
            int camp = fixture.offer(i, 1);
            assertEquals(0, take(camp, 1, () -> true, r -> {}, () -> {}));
            progress = AsterSchedule.progress(fixture.data);
            if (i != 3)
                assertThrows(
                        IllegalStateException.class,
                        () -> AsterSchedule.progress(fixture.data).startMidBatch(2));
        }
        assertTrue(progress.midBatchFinished());
        assertEquals(61, progress.credit());
        progress.startMidBatch(2);
        fixture.data.setDetail(progress);
        assertEquals(4, AsterSchedule.progress(fixture.data).midCollectCount());
        assertTrue(AsterSchedule.progress(fixture.data).midFinishedCamps().isEmpty());
        var fresh = new AsterProgress();
        assertEquals(0, fresh.midCollectCount());
        assertEquals(0, fresh.credit());
    }

    @Test
    void currentNativeTagsDecodeCampAndRewardContents() throws Exception {
        var detail = AsterMidSceneController.info(fixture.data, fixture.config, 20, fixture.now);
        assertTrue(detail.getIsOpen());
        assertEquals(4, detail.getCampListCount());
        var out = new ByteArrayOutputStream();
        var wire = CodedOutputStream.newInstance(out);
        for (var camp : detail.getCampListList()) wire.writeMessage(13, camp);
        wire.flush();
        assertEquals(
                detail.getCampListList(),
                AsterMidCampInfoNotify.parseFrom(out.toByteArray()).getCampListList());
        out.reset();
        wire = CodedOutputStream.newInstance(out);
        wire.writeMessage(6, detail);
        wire.flush();
        assertEquals(detail, AsterMidInfoNotify.parseFrom(out.toByteArray()).getInfo());
        assertEquals(
                13,
                detail.getCampList(0).getDescriptorForType().findFieldByName("pos").getNumber());
        assertEquals(
                15,
                detail.getCampList(0)
                        .getDescriptorForType()
                        .findFieldByName("camp_id")
                        .getNumber());
        var reward =
                GadgetGeneralRewardInfo.newBuilder()
                        .setResin(20)
                        .addRemainUidList(10001)
                        .addQualifyUidList(10001)
                        .build();
        out.reset();
        wire = CodedOutputStream.newInstance(out);
        wire.writeMessage(43, reward);
        wire.flush();
        assertEquals(reward, SceneGadgetInfo.parseFrom(out.toByteArray()).getGeneralReward());
        assertEquals(
                Set.of(1, 3, 4), UnknownFieldSet.parseFrom(reward.toByteArray()).asMap().keySet());
    }

    private int take(
            int camp,
            int difficulty,
            java.util.function.BooleanSupplier debit,
            java.util.function.Consumer<AsterMidRewards.Reward> grant,
            Runnable save) {
        return AsterMidRewards.take(
                fixture.data,
                fixture.config,
                20,
                fixture.now,
                camp,
                difficulty,
                debit,
                grant,
                save);
    }
}
