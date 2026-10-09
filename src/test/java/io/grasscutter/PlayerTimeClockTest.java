package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.managers.SatiationManager;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AvatarSatiationDataNotifyOuterClass.AvatarSatiationDataNotify;
import emu.grasscutter.net.proto.PlayerTimeNotifyOuterClass.PlayerTimeNotify;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.PlayerTimeClock;
import emu.grasscutter.server.packet.send.PacketPlayerTimeNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

class PlayerTimeClockTest {
    @Test
    void timeAdvancesBetweenPingsFromItsOwnMonotonicOrigin() {
        var nanos = new AtomicLong(123_000_000_000L);
        var clock = new PlayerTimeClock(nanos::get);
        assertEquals(0, clock.snapshot().playerTime());
        nanos.addAndGet(1_500_000_000L);
        assertEquals(1500, clock.snapshot().playerTime());
        assertFalse(clock.snapshot().paused());
    }

    @Test
    void pauseFreezesTimeAndResumeExcludesThePausedInterval() {
        var nanos = new AtomicLong();
        var clock = new PlayerTimeClock(nanos::get);
        nanos.set(1_500_000_000L);
        clock.setPaused(true);
        nanos.addAndGet(300_000_000_000L);
        assertEquals(new PlayerTimeClock.Snapshot(1500, true), clock.snapshot());
        clock.setPaused(false);
        nanos.addAndGet(500_000_000L);
        assertEquals(new PlayerTimeClock.Snapshot(2000, false), clock.snapshot());
    }

    @Test
    void repeatedPauseAndResumeDoNotResetTheRunningOrFrozenTime() {
        var nanos = new AtomicLong();
        var clock = new PlayerTimeClock(nanos::get);
        nanos.set(2_000_000_000L);
        clock.setPaused(false);
        nanos.set(3_000_000_000L);
        clock.setPaused(true);
        nanos.set(4_000_000_000L);
        clock.setPaused(true);
        assertEquals(3000, clock.snapshot().playerTime());
        clock.setPaused(false);
        nanos.set(5_000_000_000L);
        clock.setPaused(false);
        assertEquals(4000, clock.snapshot().playerTime());
    }

    @Test
    void fractionalMillisecondsArePreservedAcrossPauseTransitions() {
        var nanos = new AtomicLong();
        var clock = new PlayerTimeClock(nanos::get);
        nanos.set(600_000L);
        clock.setPaused(true);
        nanos.set(9_000_000L);
        clock.setPaused(false);
        nanos.addAndGet(600_000L);
        assertEquals(1, clock.snapshot().playerTime());
    }

    @Test
    void monotonicCounterWrappingItsSignedBoundaryStillMeasuresTheElapsedInterval() {
        var nanos = new AtomicLong(Long.MAX_VALUE - 500_000);
        var clock = new PlayerTimeClock(nanos::get);
        nanos.addAndGet(1_000_000L);
        assertEquals(1, clock.snapshot().playerTime());
    }

    @Test
    void pingSamplesStillUpdateNetworkStateWithoutChangingTheAnnouncedPlayerClock() {
        var nanos = new AtomicLong();
        var session = new GameSession(null, new PlayerTimeClock(nanos::get));
        nanos.set(900_000_000L);
        session.updateLastPingTime(-1);
        assertEquals(-1, session.getClientTime());
        assertTrue(session.getLastPingTime() > 0);
        assertEquals(900, session.getPlayerTimeSnapshot().playerTime());
        session.updateLastPingTime(2);
        assertEquals(2, session.getClientTime());
        nanos.set(1_200_000_000L);
        assertEquals(1200, session.getPlayerTimeSnapshot().playerTime());
    }

    @Test
    void pauseTransitionsSendTheSameTimeAndFlagUsedByTheCooldownClock() throws Exception {
        var nanos = new AtomicLong();
        var session = new GameSession(null, new PlayerTimeClock(nanos::get));
        var player =
                new AbilityTestFixture.TestPlayer() {
                    @Override
                    public void onPause() {}

                    @Override
                    public void onUnpause() {}
                };
        player.setSession(session);
        nanos.set(1_000_000_000L);
        player.setPaused(true);
        assertEquals(1, player.packets.size());
        var paused = PlayerTimeNotify.parseFrom(player.packets.get(0).getData());
        assertTrue(paused.getIsPaused());
        assertEquals(1000, paused.getPlayerTime());
        nanos.addAndGet(10_000_000_000L);
        player.setPaused(true);
        assertEquals(1, player.packets.size());
        player.setPaused(false);
        assertEquals(2, player.packets.size());
        var resumed = PlayerTimeNotify.parseFrom(player.packets.get(1).getData());
        assertFalse(resumed.getIsPaused());
        assertEquals(1000, resumed.getPlayerTime());
        nanos.addAndGet(250_000_000L);
        assertEquals(1250, player.getPlayerTimeMs());
    }

    @Test
    void packetUsesOneClockSnapshotAndDoesNotSignExtendThePingValue() throws Exception {
        var nanos = new AtomicLong();
        var session = new GameSession(null, new PlayerTimeClock(nanos::get));
        var player = new AbilityTestFixture.TestPlayer();
        player.setSession(session);
        session.updateLastPingTime(Integer.MIN_VALUE);
        nanos.set(4_294_967_296L * 1_000_000L);
        session.setPlayerTimePaused(true);
        var packet = PlayerTimeNotify.parseFrom(new PacketPlayerTimeNotify(player).getData());
        assertEquals(4_294_967_296L, packet.getPlayerTime());
        assertTrue(packet.getIsPaused());
        assertTrue(packet.getServerTime() > 0);
    }

    @Test
    void separateSessionsHaveSeparateTimeOriginsAndPauseState() {
        var nanos = new AtomicLong();
        var first = new GameSession(null, new PlayerTimeClock(nanos::get));
        nanos.set(500_000_000L);
        first.setPlayerTimePaused(true);
        var second = new GameSession(null, new PlayerTimeClock(nanos::get));
        nanos.set(1_000_000_000L);
        assertEquals(new PlayerTimeClock.Snapshot(500, true), first.getPlayerTimeSnapshot());
        assertEquals(new PlayerTimeClock.Snapshot(500, false), second.getPlayerTimeSnapshot());
    }

    @Test
    void satiationUpdatesUseThePausedPlayerClockInsteadOfTheNetworkSample() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var nanos = new AtomicLong();
            var clock = new PlayerTimeClock(nanos::get);
            var session =
                    new GameSession(null, clock) {
                        @Override
                        public void send(BasePacket packet) {
                            fixture.player.packets.add(packet);
                        }
                    };
            fixture.player.setSession(session);
            session.updateLastPingTime(999999999);
            nanos.set(10_000_000_000L);
            var depot =
                    JsonUtils.decode(
                            "{\"id\":7301,\"skills\":[],\"talents\":[],\"inherentProudSkillOpens\":[]}",
                            AvatarSkillDepotData.class);
            depot.onLoad();
            var avatar = new SkillTalentLevelTest.RecordingAvatar(fixture.player, 10000073, depot);
            avatar.setSatiation(600);
            avatar.setSatiationPenalty(3000);
            clock.setPaused(true);
            nanos.addAndGet(300_000_000_000L);
            new SatiationManager(fixture.player).updateSingleAvatar(avatar, 5);
            var data =
                    AvatarSatiationDataNotify.parseFrom(fixture.player.packets.get(1).getData())
                            .getSatiationDataList(0);
            assertEquals(35f, data.getFinishTime());
            assertEquals(45f, data.getPenaltyFinishTime());
        }
    }

    @Test
    void eatingAnnouncesFinishTimeOnTheSameClockAsPlayerTimeNotify() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            fixture.player.setWorld(fixture.scene.getWorld());
            var nanos = new AtomicLong();
            var session =
                    new GameSession(null, new PlayerTimeClock(nanos::get)) {
                        @Override
                        public void send(BasePacket packet) {
                            fixture.player.packets.add(packet);
                        }
                    };
            fixture.player.setSession(session);
            session.updateLastPingTime(Integer.MIN_VALUE);
            nanos.set(100_000_000_000L);
            var depot =
                    JsonUtils.decode(
                            "{\"id\":7301,\"skills\":[],\"talents\":[],\"inherentProudSkillOpens\":[]}",
                            AvatarSkillDepotData.class);
            depot.onLoad();
            var avatar = new SkillTalentLevelTest.RecordingAvatar(fixture.player, 10000073, depot);
            avatar.setSatiation(600);
            assertTrue(new SatiationManager(fixture.player).addSatiation(avatar, 3, 0));
            var timePacket =
                    fixture.player.packets.stream()
                            .filter(p -> p.getOpcode() == PacketOpcodes.PlayerTimeNotify)
                            .findFirst()
                            .orElseThrow();
            assertEquals(100000, PlayerTimeNotify.parseFrom(timePacket.getData()).getPlayerTime());
            var finishPacket =
                    fixture.player.packets.stream()
                            .filter(p -> p.getOpcode() == PacketOpcodes.AvatarSatiationDataNotify)
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    130f,
                    AvatarSatiationDataNotify.parseFrom(finishPacket.getData())
                            .getSatiationDataList(0)
                            .getFinishTime());
            assertEquals(900, avatar.getSatiation());
            assertEquals(1, avatar.saves);
        }
    }
}
