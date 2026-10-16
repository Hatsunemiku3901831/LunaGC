package emu.grasscutter.game.ability;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

class ModifierLedgerLifetimeTest {
    @ParameterizedTest
    @ValueSource(strings = {"same", "root-removed", "owner-life", "target-life"})
    void removalBookkeepingUsesTheEntityLifetimeAfterItsRootHasBeenUninstalled(String change) {
        var owner = new Actor(1);
        var target = new Actor(2);
        var data =
                JsonUtils.decode(
                        "{abilityName:RemovalLedgerLife,modifiers:{Pulse:{onRemoved:[{$type:ClearShareCD}]}}}",
                        AbilityData.class);
        var ability = new Ability(data, owner, player());
        owner.getInstancedAbilities().add(ability);
        var controller = new AbilityModifierController(ability, data, data.modifiers.get("Pulse"));
        ability.registerModifier(target, controller);
        var action = data.modifiers.get("Pulse").onRemoved[0];
        if (change.equals("root-removed")) owner.getInstancedAbilities().remove(ability);
        if (change.equals("owner-life")) {
            owner.invalidateAbilityLifecycle();
            owner.activateAbilityLifecycle();
        }
        if (change.equals("target-life")) {
            target.invalidateAbilityLifecycle();
            target.activateAbilityLifecycle();
        }
        var effects = new AtomicInteger();
        boolean expected = change.equals("same") || change.equals("root-removed");
        for (int i = 0; i < 2; i++)
            AbilityCooldownInvocation.run(
                    new AbilityCooldownInvocation.Scope(controller, true),
                    () -> {
                        assertEquals(expected, AbilityCooldownInvocation.allows(action));
                        assertEquals(
                                expected,
                                AbilityCooldownInvocation.recordApplication(
                                        ability,
                                        action,
                                        () -> {
                                            effects.incrementAndGet();
                                            return true;
                                        }));
                    });
        assertEquals(expected ? 1 : 0, effects.get());
    }

    private static Player player() {
        Grasscutter.getConfig();
        return new Player() {
            final TeamManager team = new TeamManager(this);

            @Override
            public TeamManager getTeamManager() {
                return team;
            }
        };
    }

    static final class Actor extends GameEntity {
        private final Int2FloatMap properties = new Int2FloatOpenHashMap();

        Actor(int id) {
            super(null);
            setId(id);
        }

        @Override
        public void initAbilities() {}

        @Override
        public int getEntityTypeId() {
            return 1;
        }

        @Override
        public Int2FloatMap getFightProperties() {
            return properties;
        }

        @Override
        public Position getPosition() {
            return new Position();
        }

        @Override
        public Position getRotation() {
            return new Position();
        }

        @Override
        public SceneEntityInfo toProto() {
            return SceneEntityInfo.getDefaultInstance();
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "same",
                "owner-life",
                "target-life",
                "owner-id",
                "target-id",
                "ability",
                "modifier"
            })
    void onlyTheSameCurrentApplicationMayShareItsConsumptionAndAttachmentIdentity(String change) {
        var player = player();
        var owner = new Actor(1);
        var target =
                new Actor(2); // Attached effects can have a target different from their root owner.
        var data =
                JsonUtils.decode(
                        "{abilityName:LedgerLife,modifiers:{Pulse:{onAdded:[{$type:TPSSupplyAmmo}]}}}",
                        AbilityData.class);
        var ability = new Ability(data, owner, player);
        owner.getInstancedAbilities().add(ability);
        var modifier = data.modifiers.get("Pulse");
        var action = modifier.onAdded[0];
        var previous = new AbilityModifierController(ability, data, modifier);
        previous.bindClientInstance(
                target, AbilityInvokeEntry.newBuilder().setEntityId(target.getId()).build());
        var effects = new AtomicInteger();
        AbilityCooldownInvocation.run(
                previous,
                () ->
                        assertTrue(
                                AbilityCooldownInvocation.recordPreparedApplication(
                                        ability,
                                        action,
                                        () ->
                                                java.util.Optional.of(
                                                        () -> {
                                                            effects.incrementAndGet();
                                                            return true;
                                                        }))));
        assertEquals(1, effects.get());
        assertTrue(previous.startVehicleAttachment());
        long order = previous.getSkillSlotSourceOrder();
        if (change.equals("owner-life")) {
            owner.invalidateAbilityLifecycle();
            owner.activateAbilityLifecycle();
        }
        if (change.equals("target-life")) {
            target.invalidateAbilityLifecycle();
            target.activateAbilityLifecycle();
        }
        if (change.equals("owner-id")) owner.setId(11);
        if (change.equals("target-id")) target.setId(12);
        var nextAbility = change.equals("ability") ? new Ability(data, owner, player) : ability;
        if (nextAbility != ability) owner.getInstancedAbilities().add(nextAbility);
        var nextModifier = modifier;
        if (change.equals("modifier")) {
            nextModifier = new AbilityModifier();
            nextModifier.onAdded = modifier.onAdded;
            data.modifiers.put("Replacement", nextModifier);
        }
        var replacement = new AbilityModifierController(nextAbility, data, nextModifier);
        replacement.inheritCooldownLedger(previous, target);
        replacement.bindClientInstance(
                target, AbilityInvokeEntry.newBuilder().setEntityId(target.getId()).build());
        AbilityCooldownInvocation.run(
                replacement,
                () ->
                        assertTrue(
                                AbilityCooldownInvocation.recordPreparedApplication(
                                        nextAbility,
                                        action,
                                        () ->
                                                java.util.Optional.of(
                                                        () -> {
                                                            effects.incrementAndGet();
                                                            return true;
                                                        }))));
        assertEquals(change.equals("same") ? 1 : 2, effects.get());
        assertEquals(!change.equals("same"), replacement.startVehicleAttachment());
        if (change.equals("same")) assertEquals(order, replacement.getSkillSlotSourceOrder());
        else assertNotEquals(order, replacement.getSkillSlotSourceOrder());
    }

    @ParameterizedTest
    @ValueSource(strings = {"same", "owner-life", "target-life"})
    void pendingServerApplicationsCannotBeConfirmedInAnotherLife(String change) {
        var owner = new Actor(1);
        var target = new Actor(2);
        var data =
                JsonUtils.decode(
                        "{abilityName:PendingLedgerLife,modifiers:{Pulse:{onAdded:[{$type:TPSSupplyAmmo}]}}}",
                        AbilityData.class);
        var ability = new Ability(data, owner, player());
        owner.getInstancedAbilities().add(ability);
        var controller = new AbilityModifierController(ability, data, data.modifiers.get("Pulse"));
        ability.registerModifier(target, controller);
        assertSame(
                controller,
                target.getAbilityHpModifier().pendingCooldownController(ability, "Pulse"));
        if (change.equals("owner-life")) {
            owner.invalidateAbilityLifecycle();
            owner.activateAbilityLifecycle();
        }
        if (change.equals("target-life")) {
            target.invalidateAbilityLifecycle();
            target.activateAbilityLifecycle();
        }
        var pending = target.getAbilityHpModifier().pendingCooldownController(ability, "Pulse");
        if (change.equals("same")) assertSame(controller, pending);
        else
            assertNull(
                    pending,
                    "A controller from a previous life must not be adopted by a new client"
                            + " instance");
        var addition =
                AbilityInvokeEntry.newBuilder()
                        .setEntityId(target.getId())
                        .setTotalTickTime(1)
                        .build();
        controller.bindClientInstance(target, addition);
        assertEquals(
                change.equals("same"),
                controller.isClientInstanceCurrent(target),
                "A client confirmation cannot renew an expired registration lifetime");
        assertEquals(
                change.equals("same"),
                controller.isDuplicateClientAdd(target, addition),
                "Replay detection must use the registration lifetime as well as the latest"
                    + " confirmation");
    }
}
