package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.managers.SotSManager;
import emu.grasscutter.game.player.Player;

import org.junit.jupiter.api.Test;

class StatuePlayerTickTest extends QuestTestFixture {
    static final class ReachedStatueTick extends RuntimeException {}

    @Test
    void actualPlayerTickCallsStatueRecovery() throws Exception {
        var recovery =
                new SotSManager(player, () -> {}, rewards -> {}, item -> {}) {
                    @Override
                    public void onTick(long now) {
                        throw new ReachedStatueTick();
                    }
                };
        replaceField(Player.class, player, "sotsManager", recovery);
        assertThrows(ReachedStatueTick.class, player::onTick);
    }
}
