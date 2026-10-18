package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionHealHP;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;

class AbilityPartyIterationConcurrencyTest {
    private AbilityTestFixture fixture;
    private final CountDownLatch lookup = new CountDownLatch(1);
    private final CountDownLatch resume = new CountDownLatch(1);
    private final AtomicBoolean pauseLookup = new AtomicBoolean();

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private EntityAvatar member(int id, boolean pause) {
        var data = JsonUtils.decode("{id:" + id + ",name:'Other" + id + "'}", AvatarData.class);
        var avatar = new Avatar() {
            @Override public Player getPlayer() { return fixture.player; }
            @Override public int getAvatarId() { return id; }
            @Override public long getGuid() { return id; }
            @Override public AvatarData getAvatarData() { return data; }
            @Override public void save() {}
        };
        var entity = new EntityAvatar(fixture.scene, avatar) {
            @Override public void initAbilities() {}
            @Override public Avatar getAvatar() {
                if (pause && pauseLookup.compareAndSet(true, false)) {
                    lookup.countDown();
                    try {
                        assertTrue(resume.await(3, TimeUnit.SECONDS), "Party mutation did not finish");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
                return super.getAvatar();
            }
        };
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100f);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 50f);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private <T> T changingParty(Callable<T> operation, Runnable change) throws Exception {
        var workers = Executors.newSingleThreadExecutor();
        try {
            pauseLookup.set(true);
            var result = workers.submit(operation);
            assertTrue(lookup.await(2, TimeUnit.SECONDS), "Ability did not reach the first member callback");
            change.run();
            resume.countDown();
            return result.get(3, TimeUnit.SECONDS);
        } finally {
            resume.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void abilityCasterDiscoverySurvivesConcurrentPartyChange() throws Exception {
        member(10000001, true);
        member(10000002, false);
        var data = JsonUtils.decode("{abilityName:UnmatchedCasterAbility}", AbilityData.class);

        var ability = changingParty(
                () -> new Ability(data, fixture.entity, fixture.player),
                () -> member(10000003, false));

        assertNotNull(ability);
        assertNull(ability.getCasterEntity(), "An unrelated party member cannot become the caster");
        assertEquals(3, fixture.player.getTeamManager().getActiveTeam().size());
    }

    @Test
    void furinaPartyHealingUsesOneRosterSnapshotAcrossConcurrentChange() throws Exception {
        var furina = member(10000089, false);
        var firstRecipient = member(10000002, true);
        var secondRecipient = member(10000003, false);
        var ability = new Ability(
                JsonUtils.decode("{abilityName:Avatar_Furina_Constellation_2}", AbilityData.class),
                furina, fixture.player);
        var action = JsonUtils.decode("{$type:HealHP,amount:5,healRatio:1}", AbilityModifierAction.class);
        var added = new EntityAvatar[1];

        assertTrue(changingParty(
                () -> new ActionHealHP().execute(ability, action, ByteString.EMPTY, furina),
                () -> added[0] = member(10000004, false)));

        assertEquals(55f, furina.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(55f, firstRecipient.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(55f, secondRecipient.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(50f, added[0].getFightProperty(FightProperty.FIGHT_PROP_CUR_HP),
                "A member added during the callback belongs to the next party operation");
    }
}
