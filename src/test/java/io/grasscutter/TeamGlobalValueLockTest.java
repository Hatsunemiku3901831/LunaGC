package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.ability.TeamGlobalValues;
import emu.grasscutter.game.entity.EntityTeam;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.*;

class TeamGlobalValueLockTest {
    @ParameterizedTest
    @ValueSource(strings = {"add", "set", "addServer", "setServer"})
    void waitingValueUpdateDoesNotHoldTeamAgainstSceneLockedAbilityCallback(String operation)
            throws Exception {
        try (var environment = new AbilityTestFixture()) {
            var scene = environment.scene;
            var player = environment.player;
            player.setUid(10001);
            player.setWorld(scene.getWorld());
            scene.getPlayers().add(player);
            var team =
                    new EntityTeam(player) {
                        @Override
                        public void onAbilityValueUpdate() {
                            // Same scene lookup as the modifier execution context in the captured
                            // deadlock.
                            scene.getScenePlayBattleManager().getType(133001413);
                            super.onAbilityValueUpdate();
                        }
                    };
            player.getTeamManager().setEntity(team);
            var workers = Executors.newFixedThreadPool(2);
            var attempted = new CountDownLatch(1);
            Future<Integer> updated;
            try {
                synchronized (scene) {
                    updated =
                            workers.submit(
                                    () -> {
                                        attempted.countDown();
                                        return switch (operation) {
                                            case "add" -> TeamGlobalValues.add(
                                                    scene, new int[] {10001}, "SGV_lock", 1);
                                            case "set" -> TeamGlobalValues.set(
                                                    scene, new int[] {10001}, "SGV_lock", 1);
                                            case "addServer" -> TeamGlobalValues.addServer(
                                                    scene, 10001, "SGV_lock", 1);
                                            default -> TeamGlobalValues.setServer(
                                                    scene, 10001, "SGV_lock", 1);
                                        };
                                    });
                    assertTrue(attempted.await(2, TimeUnit.SECONDS));
                    assertThrows(
                            TimeoutException.class, () -> updated.get(100, TimeUnit.MILLISECONDS));
                    // An ability holding the scene must still be able to acquire this team.
                    var canUseTeam =
                            workers.submit(
                                    () -> {
                                        synchronized (team) {
                                            return true;
                                        }
                                    });
                    assertTrue(canUseTeam.get(2, TimeUnit.SECONDS));
                }
                assertEquals(0, updated.get(5, TimeUnit.SECONDS));
                assertEquals(1f, TeamGlobalValues.get(scene, 10001, "SGV_lock"));
            } finally {
                workers.shutdown();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
                scene.getScriptManager().onDestroy();
            }
        }
    }
}
