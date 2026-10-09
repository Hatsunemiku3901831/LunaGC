package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionLoseHP;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.EntityFightPropChangeReasonNotifyOuterClass.EntityFightPropChangeReasonNotify;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.net.proto.ChangeHpReasonOuterClass.ChangeHpReason;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class ScriptedHpTest {
    private void check(boolean locked, boolean respectLock, float expectedHp) throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
            fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
            fixture.entity.setLockHP(locked);
            var action = JsonUtils.decode("{\"$type\":\"LoseHP\",\"amount\":25,\"enableLockHP\":"
                    + respectLock + "}", AbilityModifier.AbilityModifierAction.class);
            var ability = new Ability(JsonUtils.decode("{\"abilityName\":\"Scripted_Hp\"}", AbilityData.class), fixture.entity, null);
            new ActionLoseHP().execute(ability, action, ByteString.EMPTY, fixture.entity);
            assertEquals(expectedHp, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            var changes = fixture.scene.packets.stream().filter(p -> p.getOpcode()
                    == PacketOpcodes.EntityFightPropChangeReasonNotify).toList();
            if (expectedHp == 100) assertTrue(changes.isEmpty(), "A blocked HP cost must not report damage");
            else {
                assertEquals(1, changes.size(), "HP costs must notify once, using their effective loss");
                var change = EntityFightPropChangeReasonNotify.parseFrom(changes.get(0).getData());
                assertEquals(PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY, change.getReason());
                assertEquals(ChangeHpReason.ChangeHpReason_CHANGE_HP_SUB_ABILITY, change.getChangeHpReason());
                assertEquals(-25, change.getPropDelta());
            }
        }
    }
    @Test void scriptedCostCanIgnoreLockHpWhenTheResourceSaysSo() throws Exception { check(true, false, 75); }
    @Test void lockedCostStaysBlockedWhenRequested() throws Exception { check(true, true, 100); }
    @Test void ordinaryCostSendsOneAbilityReasonPacket() throws Exception { check(false, false, 75); }
}
