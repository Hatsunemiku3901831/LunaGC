package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.ActionGetFightProperty;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.net.proto.ServerGlobalValueChangeNotifyOuterClass.ServerGlobalValueChangeNotify;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class AbilityFightPropertyReadTest {
    private AbilityTestFixture fixture;
    private EntityAvatar caster, selected;
    private EntityClientGadget summon;
    private ObservedEntity bearer;
    private final AtomicLong now = new AtomicLong();
    private final ActionGetFightProperty handler = new ActionGetFightProperty();

    private class ObservedEntity extends AbilityTestFixture.TestEntity {
        int updates;
        final AbilityPeriodicHealing timer = new AbilityPeriodicHealing(this, now::get);

        ObservedEntity() {
            super(fixture.scene);
            setId(43);
            fixture.scene.getEntities().put(43, this);
        }

        @Override
        public void onAbilityValueUpdate() {
            updates++;
            super.onAbilityValueUpdate();
        }

        @Override
        public AbilityPeriodicHealing getAbilityPeriodicHealing() {
            return timer;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        caster = avatar(fixture.player);
        selected = avatar(fixture.player);
        fixture.player.getTeamManager().getActiveTeam().add(caster);
        fixture.player.getTeamManager().getActiveTeam().add(selected);
        fixture.player.getTeamManager().setCurrentCharacterIndex(1);
        summon = gadget(caster.getId(), 991);
        bearer = new ObservedEntity();
        caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, 650);
        selected.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, 120);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, 40);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private EntityAvatar avatar(Player player) {
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return player;
                    }

                    @Override
                    public void save() {}
                };
        var entity =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private EntityClientGadget gadget(int owner, int id) {
        var entity =
                new EntityClientGadget(
                        fixture.scene,
                        fixture.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(id)
                                .setOwnerEntityId(owner)
                                .build());
        fixture.scene.getEntities().put(id, entity);
        return entity;
    }

    private Ability ability(GameEntity owner) {
        return new Ability(
                JsonUtils.decode("{\"abilityName\":\"PropertyRead\"}", AbilityData.class),
                owner,
                fixture.player);
    }

    private AbilityModifierAction action(String fields) {
        return JsonUtils.decode(
                "{\"$type\":\"GetFightProperty\",\"globalValueKey\":\"cache\",\"fightProp\":\"FIGHT_PROP_CUR_DEFENSE\""
                        + fields
                        + "}",
                AbilityModifierAction.class);
    }

    private boolean read(Ability ability, AbilityModifierAction action, GameEntity destination) {
        return handler.execute(ability, action, ByteString.EMPTY, destination);
    }

    private AbilityModifierAction resource(String name, String pointer) throws Exception {
        JsonElement node =
                JsonParser.parseString(
                        Files.readString(
                                Path.of(
                                        "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"
                                                + name
                                                + ".json")));
        for (var segment : pointer.substring(1).split("/"))
            node =
                    node.isJsonArray()
                            ? node.getAsJsonArray().get(Integer.parseInt(segment))
                            : node.getAsJsonObject().get(segment);
        return JsonUtils.decode(node.toString(), AbilityModifierAction.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"/3/Default/onAdded/5", "/4/Default/onAdded/4", "/20/Default/onAdded/5"})
    void realChioriTurretsReadTheirCasterAndKeepTheCacheOnTheTurret(String pointer)
            throws Exception {
        var action = resource("Chiori", pointer);
        assertTrue(read(ability(summon), action, summon));
        assertEquals(650f, summon.getGlobalAbilityValues().get("_Chiori_Cur_Def"));
        assertFalse(caster.getGlobalAbilityValues().containsKey("_Chiori_Cur_Def"));
        assertFalse(selected.getGlobalAbilityValues().containsKey("_Chiori_Cur_Def"));
    }

    @Test
    void realAinoLegacyOnlySourceReadsTheLaunchCasterAfterSwitchingCharacters() throws Exception {
        var action = resource("Aino", "/15/Default/onAbilityStart/1");
        caster.setFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 310);
        selected.setFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 90);
        var ability = ability(summon);
        assertTrue(
                ability.getManager()
                        .executeCallbackActionsNow(
                                ability,
                                new AbilityModifierAction[] {action},
                                ByteString.EMPTY,
                                summon,
                                selected));
        assertEquals(310f, caster.getGlobalAbilityValues().get("_ABILITY_Aino_MasteryCache"));
        assertFalse(selected.getGlobalAbilityValues().containsKey("_ABILITY_Aino_MasteryCache"));
    }

    @Test
    void sourceSelectionAndDestinationSelectionAreIndependent() {
        var action = action(",\"fightPropSourceTarget\":\"Self\",\"target\":\"CasterOriginOwner\"");
        assertTrue(read(ability(summon), action, bearer));
        assertEquals(40f, bearer.getGlobalAbilityValues().get("cache"));
        assertFalse(caster.getGlobalAbilityValues().containsKey("cache"));
    }

    @Test
    void realGorouOriginSourceUsesTheSharedSourceResolver() throws Exception {
        var action =
                resource(
                        "Gorou",
                        "/25/Default/modifiers/Avatar_Gorou_ElementalBurst_Heal_Handler/onThinkInterval/0");
        assertTrue(read(ability(summon), action, bearer));
        assertEquals(120f, bearer.getGlobalAbilityValues().get("_ABILITY_Gorou_CurDefense"));
        fixture.player.getTeamManager().setCurrentCharacterIndex(0);
        assertTrue(read(ability(summon), action, bearer));
        assertEquals(650f, bearer.getGlobalAbilityValues().get("_ABILITY_Gorou_CurDefense"));
    }

    @Test
    void anAbsentSourceRetainsTheAbilityOwnerDefault() {
        assertTrue(read(ability(caster), action(""), bearer));
        assertEquals(650f, bearer.getGlobalAbilityValues().get("cache"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                ",\"fightPropSourceTarget\":\"Caster\",\"NKJNADEMKOG\":\"Self\"",
                ",\"NKJNADEMKOG\":\"Self\",\"fightPropSourceTarget\":\"Caster\""
            })
    void canonicalSourceWinsInBothJsonOrdersAndRoundTripsKeepBothValues(String fields) {
        var action = action(fields);
        assertTrue(read(ability(caster), action, bearer));
        assertEquals(650f, bearer.getGlobalAbilityValues().get("cache"));
        var json = JsonParser.parseString(JsonUtils.encode(action)).getAsJsonObject();
        assertEquals("Caster", json.get("fightPropSourceTarget").getAsString());
        assertEquals("Self", json.get("NKJNADEMKOG").getAsString());
    }

    @Test
    void legitimateZeroDoesNotTurnIntoSpecialEnergy() {
        caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, 0);
        caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, 73);
        assertTrue(read(ability(caster), action(""), bearer));
        assertEquals(0f, bearer.getGlobalAbilityValues().get("cache"));
    }

    @Test
    void changedReadsRefreshOnceAndNotifyTheActualDestination() throws Exception {
        var ability = ability(caster);
        assertTrue(read(ability, action(""), bearer));
        assertTrue(read(ability, action(""), bearer));
        assertEquals(1, bearer.updates);
        assertEquals(1, fixture.player.packets.size());
        var packet = fixture.player.packets.get(0);
        assertEquals(PacketOpcodes.ServerGlobalValueChangeNotify, packet.getOpcode());
        var body = ServerGlobalValueChangeNotify.parseFrom(packet.getData());
        assertEquals(bearer.getId(), body.getEntityId());
        assertEquals(Utils.abilityHash("cache"), body.getKeyHash());
        assertEquals(650, body.getValue());
        caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, 700);
        assertTrue(read(ability, action(""), bearer));
        assertEquals(2, bearer.updates);
        assertEquals(2, fixture.player.packets.size());
    }

    @Test
    void aReadRefreshesAnExistingGlobalValueWatcher() {
        bearer.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK, 100);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, 100);
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"ReadWatcher\",\"abilityMixins\":[{\"$type\":\"AttachModifierToGlobalValueMixin\",\"globalValueKey\":\"cache\",\"modifierNameSteps\":[\"Low\",\"High\"],\"valueSteps\":[0,500,1000]}],\"modifiers\":{\"Low\":{\"properties\":{\"Actor_AttackDelta\":10}},\"High\":{\"properties\":{\"Actor_AttackDelta\":30}}}}",
                        AbilityData.class);
        bearer.getInstancedAbilities().add(new Ability(data, bearer, fixture.player));
        assertEquals(110, bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        assertTrue(read(ability(caster), action(""), bearer));
        assertEquals(130, bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
    }

    @Test
    void aServerOwnedReadUpdatesBookkeepingWithoutSendingItBackToTheClient() {
        var ability = ability(caster);
        AbilityManager.runServerOwned(() -> assertTrue(read(ability, action(""), bearer)));
        assertEquals(650f, bearer.getGlobalAbilityValues().get("cache"));
        assertEquals(1, bearer.updates);
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void aFailedConditionKeepsTheExistingValueAndDoesNotNotify() {
        bearer.getGlobalAbilityValues().put("cache", 9f);
        assertTrue(
                read(
                        ability(caster),
                        action(
                                ",\"predicates\":[{\"$type\":\"ByUnlockTalentParam\",\"talentParam\":\"Locked\"}]"),
                        bearer));
        assertEquals(9f, bearer.getGlobalAbilityValues().get("cache"));
        assertEquals(0, bearer.updates);
        assertTrue(fixture.player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UnknownSource", "AllPlayerAvatars", ""})
    void unknownOrAmbiguousSourcesDoNotFallBackToTheDestination(String source) {
        bearer.getGlobalAbilityValues().put("cache", 9f);
        assertFalse(
                read(
                        ability(caster),
                        action(",\"fightPropSourceTarget\":\"" + source + "\""),
                        bearer));
        assertEquals(9f, bearer.getGlobalAbilityValues().get("cache"));
        assertEquals(0, bearer.updates);
    }

    @Test
    void aMissingSummonCasterDoesNotFallBackToTheSelectedAvatar() {
        var missing = gadget(123456, 992);
        assertFalse(
                read(ability(missing), action(",\"NKJNADEMKOG\":\"CasterOriginOwner\""), bearer));
        assertFalse(bearer.getGlobalAbilityValues().containsKey("cache"));
    }

    @Test
    void invalidatedSourcesAndDestinationsCannotWriteOldValues() {
        var ability = ability(summon);
        caster.invalidateAbilityLifecycle();
        assertFalse(
                read(ability, action(",\"fightPropSourceTarget\":\"CasterOriginOwner\""), bearer));
        bearer.invalidateAbilityLifecycle();
        assertFalse(read(ability, action(""), bearer));
        assertFalse(bearer.getGlobalAbilityValues().containsKey("cache"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"UnknownProperty\"", "[1,0,\"DIV\"]", "{\"unknown\":1}"})
    void invalidPropertyExpressionsDoNotEraseTheCache(String expression) {
        var action =
                JsonUtils.decode(
                        "{\"$type\":\"GetFightProperty\",\"globalValueKey\":\"cache\",\"fightProp\":"
                                + expression
                                + "}",
                        AbilityModifierAction.class);
        bearer.getGlobalAbilityValues().put("cache", 9f);
        assertFalse(read(ability(caster), action, bearer));
        assertEquals(9f, bearer.getGlobalAbilityValues().get("cache"));
    }

    @Test
    void nonFinitePropertiesAndBlankKeysLeaveExistingStateIntact() {
        bearer.getGlobalAbilityValues().put("cache", 9f);
        caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, Float.NaN);
        assertFalse(read(ability(caster), action(""), bearer));
        caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, 650);
        var blank = action("");
        blank.globalValueKey = " ";
        assertFalse(read(ability(caster), blank, bearer));
        assertEquals(9f, bearer.getGlobalAbilityValues().get("cache"));
        assertEquals(0, bearer.updates);
    }

    @Test
    void realSigewinneSumsOnlyHerPlayersCurrentTeamWithoutDuplicateMembers() throws Exception {
        var action =
                resource(
                        "Sigewinne",
                        "/34/Default/modifiers/Sigewinne_HP_Debts_Change_DoSomething/onAdded/0");
        var guest = avatar(new AbilityTestFixture.TestPlayer());
        caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 120);
        selected.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 330);
        guest.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 900);
        fixture.player.getTeamManager().getActiveTeam().add(caster);
        assertTrue(read(ability(caster), action, bearer));
        assertEquals(450f, bearer.getGlobalAbilityValues().get(action.globalValueKey));
        selected.invalidateAbilityLifecycle();
        assertTrue(read(ability(caster), action, bearer));
        assertEquals(120f, bearer.getGlobalAbilityValues().get(action.globalValueKey));
    }

    @Test
    void sumRejectsOverflowAndConflictingOrUnknownAggregation() throws Exception {
        var action =
                resource(
                        "Sigewinne",
                        "/34/Default/modifiers/Sigewinne_HP_Debts_Change_DoSomething/onAdded/0");
        var ability = ability(caster);
        caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, Float.MAX_VALUE);
        selected.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, Float.MAX_VALUE);
        bearer.getGlobalAbilityValues().put(action.globalValueKey, 9f);
        assertFalse(read(ability, action, bearer));
        action.fightPropAggregation = "Maximum";
        assertFalse(read(ability, action, bearer));
        action.legacyFightPropAggregation = "Maximum";
        assertFalse(read(ability, action, bearer));
        assertEquals(9f, bearer.getGlobalAbilityValues().get(action.globalValueKey));
    }

    @Test
    void aPeriodicReadFeedsTheFollowingConversionAndHealingInOrder() {
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, 80);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 1000);
        bearer.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"ReadPulse\",\"modifiers\":{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"GetFightProperty\",\"globalValueKey\":\"cache\",\"fightProp\":\"FIGHT_PROP_CUR_DEFENSE\"},{\"$type\":\"SetGlobalValueToOverrideMap\",\"globalValueKey\":\"cache\",\"overrideMapKey\":\"cachedDefense\"},{\"$type\":\"HealHP\",\"amount\":\"cachedDefense\"}]}}}",
                        AbilityData.class);
        var ability = new Ability(data, fixture.entity, fixture.player);
        ability.registerModifier(
                bearer, new AbilityModifierController(ability, data, data.modifiers.get("Pulse")));
        assertTrue(
                bearer.timer.ownsAction(
                        ability, 0, data.modifiers.get("Pulse").onThinkInterval[0]));
        now.set(1_000_000_000L);
        bearer.onTick(0);
        assertEquals(180, bearer.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(80, ability.getAbilitySpecials().getFloat("cachedDefense"));
        assertEquals(1, fixture.player.packets.size());
        assertEquals(
                PacketOpcodes.ServerGlobalValueChangeNotify,
                fixture.player.packets.get(0).getOpcode());
    }

    @Test
    void aSequenceWithAnUnknownReadSourceRemainsNative() {
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"UnknownReadPulse\",\"modifiers\":{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"GetFightProperty\",\"globalValueKey\":\"cache\",\"fightProp\":\"FIGHT_PROP_CUR_DEFENSE\",\"fightPropSourceTarget\":\"UnknownSource\"}]}}}",
                        AbilityData.class);
        var ability = new Ability(data, caster, fixture.player);
        ability.registerModifier(
                bearer, new AbilityModifierController(ability, data, data.modifiers.get("Pulse")));
        assertFalse(
                bearer.timer.ownsAction(
                        ability, 0, data.modifiers.get("Pulse").onThinkInterval[0]));
        now.set(1_000_000_000L);
        bearer.onTick(0);
        assertFalse(bearer.getGlobalAbilityValues().containsKey("cache"));
    }

    @Test
    void aBareModifierConfirmedByTheClientInitializesItsPropertyCache() throws Exception {
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"ConfirmedRead\",\"modifiers\":{\"Cache\":{\"onAdded\":[{\"$type\":\"GetFightProperty\",\"globalValueKey\":\"cache\",\"fightProp\":\"FIGHT_PROP_CUR_DEFENSE\",\"NKJNADEMKOG\":\"CasterOriginOwner\"}]}}}",
                        AbilityData.class);
        var ability = new Ability(data, summon, fixture.player);
        summon.getInstancedAbilities().add(ability);
        var change =
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                        .setParentAbilityName(AbilityString.newBuilder().setStr(data.abilityName))
                        .setModifierLocalId(0);
        ability.getManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(summon.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(71))
                                .setAbilityData(change.build().toByteString())
                                .build());
        assertEquals(650f, summon.getGlobalAbilityValues().get("cache"));
        assertTrue(fixture.player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mavuika", "SkirkNew"})
    void realBurstCachesKeepTheActualSpecialEnergy(String avatar) throws Exception {
        var action =
                resource(
                        avatar,
                        "Mavuika".equals(avatar)
                                ? "/8/Default/modifiers/Avatar_Mavuika_ElementalBurst_Initialize/onAdded/0"
                                : "/12/Default/modifiers/SkirkNew_ElementalBurst_BS_VFX/onAdded/0");
        caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, 88);
        assertTrue(read(ability(caster), action, caster));
        assertEquals(88f, caster.getGlobalAbilityValues().get(action.globalValueKey));
    }

    @Test
    void emptyTeamsAndNullInputsDoNotCreateOrEraseAValue() throws Exception {
        var action =
                resource(
                        "Sigewinne",
                        "/34/Default/modifiers/Sigewinne_HP_Debts_Change_DoSomething/onAdded/0");
        var ability = ability(caster);
        fixture.player.getTeamManager().getActiveTeam().clear();
        bearer.getGlobalAbilityValues().put(action.globalValueKey, 9f);
        assertFalse(read(ability, action, bearer));
        assertFalse(read(null, action, bearer));
        assertFalse(read(ability, null, bearer));
        assertFalse(read(ability, action, null));
        action.fightProp = null;
        assertFalse(read(ability, action, bearer));
        assertEquals(9f, bearer.getGlobalAbilityValues().get(action.globalValueKey));
        assertEquals(0, bearer.updates);
    }
}
