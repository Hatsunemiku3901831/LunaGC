package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.quest.conditions.ConditionActivityCond;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Real login prefix and cold Mongo load; stop at World admission before unrelated scene setup. */
class PlayerActivityLoginTest extends QuestTestFixture {
    static class AdmissionProbe extends RuntimeException {}

    @Entity(value = "players", useDiscriminator = false)
    static class LoginPlayer extends Player {
        @Transient final TeamManager team = new TeamManager(this);
        @Transient final List<BasePacket> packets = new ArrayList<>();
        @Transient GameServer server;
        @Transient boolean probe;
        @Transient ActivityManager admittedActivities;

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }

        @Override
        public GameServer getServer() {
            if (probe) {
                var condition =
                        JsonUtils.decode(
                                "{param:[2147483000,0]}", QuestData.QuestAcceptCondition.class);
                // This is the same real condition handler that crashed PlayerLoginReq.
                assertTrue(new ConditionActivityCond().execute(this, null, condition, null));
                admittedActivities = getActivityManager();
                assertNotNull(admittedActivities);
                assertTrue(
                        packets.stream()
                                .noneMatch(
                                        p ->
                                                p.getOpcode()
                                                                == PacketOpcodes
                                                                        .ActivityScheduleInfoNotify
                                                        || p.getOpcode()
                                                                == PacketOpcodes
                                                                        .ActivityInfoNotify),
                        "Activity packets must follow player/avatar login state");
                throw new AdmissionProbe();
            }
            return server;
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void newAndColdLoadedPlayersHaveActivityConditionsBeforeWorldAdmission(boolean cold)
            throws Exception {
        store.getMapper().map(LoginPlayer.class);
        var model = new LoginPlayer();
        model.setUid(20113);
        if (cold) {
            model.save();
            drain();
            model = store.find(LoginPlayer.class).filter(Filters.eq("id", 20113)).first();
            assertNotNull(model);
        }
        model.server = player.server;
        model.setSession(player.getSession());
        assertNull(model.getActivityManager());
        model.probe = true;
        try {
            var stopped = assertThrows(AdmissionProbe.class, model::onLogin);
            assertTrue(
                    Arrays.stream(stopped.getStackTrace())
                            .anyMatch(
                                    frame ->
                                            frame.getClassName().equals(World.class.getName())
                                                    && frame.getMethodName().equals("<init>")));
            assertNotNull(model.admittedActivities);
        } finally {
            model.probe = false;
            model.getSotsManager().stopRecovery();
        }
    }

    @Test
    void deferredRefreshAndDailyResetWaitForTheActivityLoginStage() throws Exception {
        var model = new LoginPlayer();
        model.setUid(20114);
        model.server = player.server;
        var manager = new ActivityManager(model, true);
        replaceField(Player.class, model, "activityManager", manager);
        manager.refreshActivities();
        manager.onDailyReset(System.currentTimeMillis());
        assertTrue(model.packets.isEmpty());
        assertNotNull(manager.getConditionExecutor());
        manager.onPlayerLogin();
        assertSame(manager, model.getActivityManager());
        assertEquals(
                1,
                model.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ActivityScheduleInfoNotify)
                        .count());
        manager.onDailyReset(System.currentTimeMillis());
        assertEquals(
                2,
                model.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ActivityScheduleInfoNotify)
                        .count());
    }

    @Test
    void ordinaryManagerConstructionKeepsImmediateActivityPublication() {
        var model = new LoginPlayer();
        model.setUid(20115);
        model.server = player.server;
        var manager = new ActivityManager(model);
        assertNotNull(manager.getConditionExecutor());
        assertEquals(
                1,
                model.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ActivityScheduleInfoNotify)
                        .count());
    }
}
