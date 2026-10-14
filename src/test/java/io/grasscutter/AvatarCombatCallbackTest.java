package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

class AvatarCombatCallbackTest {
    NatsaurusPossessionTest environment;
    Ability ability;
    AbilityMixinData mixin;
    int modifierId;

    @BeforeEach
    void setup() throws Exception {
        environment = new NatsaurusPossessionTest();
        environment.setup();
        var avatar = environment.avatar;
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.restoreChamberHealth(100, 0);
        var data =
                JsonUtils.decode(
                        "{abilityName:'CombatCallbackTest',modifiers:{},abilityMixins:[{$type:'AvatarCombatMixin',onEnterCombat:[{$type:'AddGlobalValue',key:'entered',value:1,target:'Self'}],onExitCombat:[{$type:'AddGlobalValue',key:'exited',value:1,target:'Self'}]}]}",
                        AbilityData.class);
        setAbility(data);
    }

    void setAbility(AbilityData data) {
        ability = new Ability(data, environment.avatar, environment.fixture.player);
        mixin = data.abilityMixins[0];
        data.localIdToMixin.put(2, mixin);
        environment.avatar.getInstancedAbilities().clear();
        environment.avatar.getInstancedAbilities().add(ability);
    }

    @AfterEach
    void close() {
        environment.close();
    }

    float value(String key) {
        return environment.avatar.getGlobalAbilityValues().getOrDefault(key, 0f);
    }

    void invoke(ByteString payload) {
        invoke(payload, AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_MIXIN_AVATAR_COMBAT);
    }

    void invoke(ByteString payload, AbilityInvokeArgument type) {
        environment
                .fixture
                .player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(environment.avatar.getId())
                                .setArgumentType(type)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(modifierId)
                                                .setLocalId(2))
                                .setAbilityData(payload)
                                .build());
    }

    ByteString state(boolean combat) {
        return ByteString.copyFrom(new byte[] {8, (byte) (combat ? 1 : 0)});
    }

    boolean modifier(String name) throws Exception {
        var method = AbilityHpModifier.class.getDeclaredMethod("activeControllers");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var controllers =
                (java.util.List<AbilityModifierController>)
                        method.invoke(environment.avatar.getAbilityHpModifier());
        return controllers.stream().anyMatch(c -> c.getName().equals(name));
    }

    @Test
    void booleanPayloadChoosesEnterThenExitWithoutGlobalValueGuessing() {
        invoke(state(true));
        assertEquals(1, value("entered"));
        assertEquals(0, value("exited"));
        invoke(state(false));
        assertEquals(1, value("entered"));
        assertEquals(1, value("exited"));
    }

    @Test
    void duplicateEnterDoesNotToggleToExitOrRepeatActions() {
        invoke(state(true));
        invoke(state(true));
        assertEquals(1, value("entered"));
        assertEquals(0, value("exited"));
    }

    @Test
    void duplicateExitDoesNotRepeatActions() {
        invoke(state(true));
        invoke(state(false));
        invoke(state(false));
        assertEquals(1, value("entered"));
        assertEquals(1, value("exited"));
    }

    @Test
    void absentBooleanUsesTheProtobufFalseDefault() {
        invoke(ByteString.EMPTY);
        assertEquals(0, value("entered"));
        assertEquals(1, value("exited"));
    }

    @Test
    void staleForbidHealGlobalCannotReverseAnExplicitEnter() {
        environment.avatar.getGlobalAbilityValues().put("_ABILITY_Avatar_ForbidFoodHeal", 1f);
        invoke(state(true));
        assertEquals(1, value("entered"));
        assertEquals(0, value("exited"));
    }

    @Test
    void theStateBelongsToAnAbilityInstance() {
        invoke(state(true));
        setAbility(ability.getData());
        invoke(state(true));
        assertEquals(2, value("entered"));
    }

    @Test
    void aLaterReturnToCombatCanTriggerAgain() {
        invoke(state(true));
        invoke(state(false));
        invoke(state(true));
        assertEquals(2, value("entered"));
        assertEquals(1, value("exited"));
    }

    @Test
    void removedEntityDoesNotRunCallbacks() {
        environment.fixture.scene.removeEntity(environment.avatar);
        invoke(state(true));
        assertEquals(0, value("entered"));
    }

    @Test
    void disabledMixinDoesNotRunCallbacks() {
        mixin.enable = false;
        invoke(state(true));
        assertEquals(0, value("entered"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"08", "0802", "0a0101", "1001", "08010800", "0001"})
    void malformedOrUnverifiedPayloadDoesNotChangeState(String hex) {
        invoke(ByteString.copyFrom(java.util.HexFormat.of().parseHex(hex)));
        assertEquals(0, value("entered"));
        assertEquals(0, value("exited"));
        invoke(state(true));
        assertEquals(1, value("entered"));
    }

    @Test
    void unsupportedExitBranchCannotLeaveAnIrreversibleEnterEffect() {
        mixin.onExitCombat =
                JsonUtils.decode(
                        "[{$type:'UnknownFutureAction'}]",
                        AbilityModifier.AbilityModifierAction[].class);
        invoke(state(true));
        assertEquals(0, value("entered"));
    }

    @Test
    void wrongInvokeArgumentCannotReachTheCombatCallback() {
        invoke(state(true), AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_MIXIN_COST_STAMINA);
        assertEquals(0, value("entered"));
    }

    @Test
    void actualArlecchinoCombatResourceAttachesAndRemovesTheHealingState() throws Exception {
        var data =
                JsonUtils.loadToList(
                                Path.of(
                                        "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Arlecchino.json"),
                                AbilityConfigData.class)
                        .stream()
                        .map(r -> r.Default)
                        .filter(r -> r.abilityName.equals("Avatar_Arlecchino_HealToHpDebts"))
                        .findFirst()
                        .orElseThrow();
        setAbility(data);
        var parent = data.modifiers.get("Avatar_Arlecchino_HealToHpDebts_Handler");
        mixin = parent.modifierMixins[0];
        data.localIdToMixin.put(2, mixin);
        modifierId = 5;
        environment
                .avatar
                .getInstancedModifiers()
                .put(modifierId, new AbilityModifierController(ability, data, parent));
        invoke(state(true));
        assertTrue(modifier("Avatar_Arlecchino_HealToHpDebts_State"));
        invoke(state(false));
        assertFalse(modifier("Avatar_Arlecchino_HealToHpDebts_State"));
    }

    @Test
    void anotherPlayersAbilityCannotBeActivatedByThisSession() {
        var guest = new AbilityTestFixture.TestPlayer();
        guest.setUid(20001);
        guest.setScene(environment.fixture.scene);
        guest.setWorld(environment.fixture.scene.getWorld());
        var avatar =
                new emu.grasscutter.game.avatar.Avatar() {
                    @Override
                    public emu.grasscutter.game.player.Player getPlayer() {
                        return guest;
                    }

                    @Override
                    public void save() {}
                };
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_MAX_HP.getId(), 100f);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_CUR_HP.getId(), 100f);
        var owner = new emu.grasscutter.game.entity.EntityAvatar(environment.fixture.scene, avatar);
        var source = new Ability(ability.getData(), owner, guest);
        owner.getInstancedAbilities().add(source);
        guest.getTeamManager().getActiveTeam().add(owner);
        environment.fixture.scene.getEntities().put(owner.getId(), owner);
        environment
                .fixture
                .player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(owner.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_MIXIN_AVATAR_COMBAT)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(1)
                                                .setLocalId(2))
                                .setAbilityData(state(true))
                                .build());
        assertEquals(0, owner.getGlobalAbilityValues().getOrDefault("entered", 0f));
    }

    @Test
    void missingExplicitModifierDoesNotFallBackToTheRootAbility() {
        modifierId = 99;
        invoke(state(true));
        assertEquals(0, value("entered"));
    }

    @Test
    void canonicalAndRawCallbacksRemainSeparateAndCanonicalWins() {
        var data =
                JsonUtils.decode(
                        "{abilityName:'CombatAliases',modifiers:{},abilityMixins:[{$type:'AvatarCombatMixin',OJIADBPIBAO:[{$type:'AddGlobalValue',key:'rawEnter',value:1}],IBMKAAGPNAK:[{$type:'AddGlobalValue',key:'rawExit',value:1}],onEnterCombat:[{$type:'AddGlobalValue',key:'entered',value:1}],onExitCombat:[{$type:'AddGlobalValue',key:'exited',value:1}]}]}",
                        AbilityData.class);
        setAbility(data);
        invoke(state(true));
        invoke(state(false));
        assertEquals(1, value("entered"));
        assertEquals(1, value("exited"));
        assertEquals(0, value("rawEnter"));
        assertEquals(0, value("rawExit"));
        var tree =
                com.google.gson.JsonParser.parseString(JsonUtils.encode(mixin)).getAsJsonObject();
        assertTrue(tree.has("OJIADBPIBAO"));
        assertTrue(tree.has("IBMKAAGPNAK"));
    }

    @Test
    void newSessionCanReconcileTheSameCombatValueAgain() {
        invoke(state(true));
        environment.fixture.player.setSession(new emu.grasscutter.server.game.GameSession(null));
        invoke(state(true));
        assertEquals(2, value("entered"));
    }

    void nested() {
        setAbility(
                JsonUtils.decode(
                        "{abilityName:'NestedCombat',abilityMixins:[{}],modifiers:{Parent:{modifierMixins:[{$type:'AvatarCombatMixin',onEnterCombat:[{$type:'AddGlobalValue',key:'entered',value:1}],onExitCombat:[{$type:'AddGlobalValue',key:'exited',value:1}]}]}}}",
                        AbilityData.class));
        var parent = ability.getData().modifiers.get("Parent");
        mixin = parent.modifierMixins[0];
        ability.getData().localIdToMixin.put(2, mixin);
        environment
                .avatar
                .getInstancedModifiers()
                .put(5, new AbilityModifierController(ability, ability.getData(), parent));
        environment
                .avatar
                .getInstancedModifiers()
                .put(6, new AbilityModifierController(ability, ability.getData(), parent));
    }

    @Test
    void modifierInstancesHaveIndependentTransitionHistories() {
        nested();
        modifierId = 5;
        invoke(state(true));
        modifierId = 6;
        invoke(state(true));
        assertEquals(2, value("entered"));
        modifierId = 5;
        invoke(state(true));
        assertEquals(2, value("entered"));
    }

    @Test
    void removedParentCannotRunItsExitThroughTheRootFallback() {
        nested();
        modifierId = 5;
        invoke(state(true));
        assertEquals(1, value("entered"));
        environment.avatar.getInstancedModifiers().remove(5);
        invoke(state(false));
        assertEquals(0, value("exited"));
    }

    @Test
    void modifierCallbackWithoutItsParentCannotBeUsedAsARootCallback() {
        nested();
        modifierId = 0;
        invoke(state(true));
        assertEquals(0, value("entered"));
    }
}
