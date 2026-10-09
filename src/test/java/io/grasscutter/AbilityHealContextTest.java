package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.JsonUtils;
import java.time.Duration;
import org.junit.jupiter.api.*;

class AbilityHealContextTest {
    private AbilityTestFixture fixture;
    private Ability ability;

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 50);
        var data = JsonUtils.decode("""
                {"abilityName":"Test_HealingCallbacks","modifiers":{"Listener":{"onHeal":[
                  {"$type":"Predicated","targetPredicates":[
                    {"$type":"ONJEHDDFFCH","LAIFKEGOFJO":["Impale"]}],
                    "successActions":[{"$type":"SetGlobalValue","key":"right","value":1}],
                    "failActions":[{"$type":"SetGlobalValue","key":"wrong","value":1}]},
                  {"$type":"SetGlobalValue","key":"finished","value":1}
                ]}}}
                """, AbilityData.class);
        ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedModifiers().put(1,
                new AbilityModifierController(ability, data, data.modifiers.get("Listener")));
    }

    @AfterEach
    void restore() { fixture.close(); }

    private void heal(String tag) {
        var action = JsonUtils.decode("{\"$type\":\"HealHP\",\"amount\":1}", AbilityModifierAction.class);
        action.healTag = tag;
        ability.getManager().executeActionNow(ability, action, ByteString.EMPTY, fixture.entity);
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (!fixture.entity.getGlobalAbilityValues().containsKey("finished")) Thread.sleep(5);
        });
    }

    @Test
    void actualAsyncHealingCallbacksCarryTheTagIntoNestedPredicateBlocks() {
        heal("Impale");
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("right"));
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("wrong"));
        assertNull(AbilityHealContext.currentTag());
    }

    @Test
    void theNextCallbackWithoutATagCannotReuseThePreviousEvent() {
        heal("Impale");
        fixture.entity.getGlobalAbilityValues().clear();
        heal(null);
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("wrong"));
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("right"));
    }

    @Test
    void aNestedCallbackExceptionRestoresTheSurroundingEvent() {
        AbilityHealContext.run("outer", () -> {
            assertThrows(IllegalStateException.class, () -> AbilityHealContext.run("inner", () -> {
                assertEquals("inner", AbilityHealContext.currentTag());
                throw new IllegalStateException("test");
            }));
            assertEquals("outer", AbilityHealContext.currentTag());
        });
        assertNull(AbilityHealContext.currentTag());
    }
}
