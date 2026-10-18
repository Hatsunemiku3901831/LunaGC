package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

/** Trial-party changes must not hold the party list while executing entity lifecycle callbacks. */
class TrialTeamRemovalLockTest {
    private AbilityTestFixture fixture;
    private TeamManager team;
    private final List<EntityAvatar> removed = new ArrayList<>();
    private final List<Boolean> lifecycleHeldTeamLock = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        team = fixture.player.getTeamManager();
    }

    @AfterEach
    void cleanup() {
        fixture.close();
    }

    private Avatar avatar(int id, int trialId) {
        var result =
                new Avatar() {
                    @Override
                    public int getAvatarId() {
                        return id;
                    }

                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public long getGuid() {
                        return id + 1_000_000_000L * trialId;
                    }

                    @Override
                    public void save() {}
                };
        result.setTrialAvatarId(trialId);
        return result;
    }

    private EntityAvatar member(Avatar avatar) {
        var entity =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public void invalidateAbilityLifecycle() {
                        // This callback can read Player.getScene(), taking the Player monitor.
                        lifecycleHeldTeamLock.add(Thread.holdsLock(team.getActiveTeam()));
                        super.invalidateAbilityLifecycle();
                    }

                    @Override
                    public void onRemoved() {
                        removed.add(this);
                        super.onRemoved();
                    }
                };
        team.getActiveTeam().add(entity);
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private void assertRemovedOutsideTeamLock(EntityAvatar... expected) {
        assertEquals(List.of(expected), removed, "Every old scene entity must still be removed");
        for (var entity : expected) {
            assertFalse(fixture.scene.getEntities().containsKey(entity.getId()));
            assertFalse(entity.isAbilityLifecycleCurrent(0), "Old abilities must be invalidated");
        }
        assertEquals(
                java.util.Collections.nCopies(expected.length, false),
                lifecycleHeldTeamLock,
                "Entity lifecycle callbacks must not acquire Player while holding the party list");
    }

    @Test
    void replacingTheRegularPartyWithTrialsReleasesTheListBeforeRemovalCallbacks() {
        team.getCurrentSinglePlayerTeamInfo().getAvatars().addAll(List.of(10000021, 10000015));
        var amber = member(avatar(10000021, 0));
        var kaeya = member(avatar(10000015, 0));
        team.setCurrentCharacterIndex(1);

        team.setupTrialAvatars(false);

        assertTrue(team.isUsingTrialTeam());
        assertEquals(1, team.getPreviousIndex());
        assertTrue(team.getActiveTeam().isEmpty());
        assertEquals(List.of(10000021, 10000015), team.getCurrentSinglePlayerTeamInfo().getAvatars());
        assertRemovedOutsideTeamLock(amber, kaeya);
    }

    @Test
    void addingATrialReleasesTheListAndReplacesOnlyTheMatchingPartyMember() {
        team.getCurrentSinglePlayerTeamInfo().getAvatars().addAll(List.of(10000021, 10000015));
        team.setupTrialAvatars(true);
        var amber = member(avatar(10000021, 0));
        var kaeya = member(avatar(10000015, 0));
        var trial = avatar(10000021, 71);

        team.addAvatarToTrialTeam(trial);

        assertEquals(2, team.getActiveTeam().size());
        assertSame(kaeya, team.getActiveTeam().get(0));
        assertSame(trial, team.getActiveTeam().get(1).getAvatar());
        assertSame(trial, team.getTrialAvatars().get(10000021));
        assertEquals(List.of(10000021, 10000015), team.getCurrentSinglePlayerTeamInfo().getAvatars());
        assertRemovedOutsideTeamLock(amber, kaeya);
    }

    @Test
    void removingTheLastTrialsReleasesTheListAndClearsTheirEntities() {
        team.setupTrialAvatars(false);
        var amber = member(avatar(10000021, 71));
        var kaeya = member(avatar(10000015, 72));
        for (var entity : List.of(amber, kaeya)) {
            var avatar = entity.getAvatar();
            team.getTrialAvatars().put(avatar.getAvatarId(), avatar);
            team.getTrialAvatarTeam().addAvatar(avatar);
        }

        team.removeTrialAvatarTeam(List.of(71, 72));

        assertFalse(team.isUsingTrialTeam());
        assertTrue(team.getTrialAvatars().isEmpty());
        assertTrue(team.getActiveTeam().isEmpty());
        assertEquals(-1, team.getPreviousIndex());
        assertRemovedOutsideTeamLock(amber, kaeya);
    }
}
