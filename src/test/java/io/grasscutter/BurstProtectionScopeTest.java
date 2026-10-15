package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionLoseHP;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.ClientAbilitiesInitFinishCombineNotifyOuterClass.ClientAbilitiesInitFinishCombineNotify;
import emu.grasscutter.net.proto.ClientAbilityInitFinishNotifyOuterClass.ClientAbilityInitFinishNotify;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.CombatInvokeEntryOuterClass.CombatInvokeEntry;
import emu.grasscutter.net.proto.CombatTypeArgumentOuterClass.CombatTypeArgument;
import emu.grasscutter.net.proto.EvtBeingHitInfoOuterClass.EvtBeingHitInfo;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.ForwardTypeOuterClass.ForwardType;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.*;

/**
 * Real burst guards must protect their bearer without masking other damage or surviving removal.
 */
class BurstProtectionScopeTest {
    private static final Map<String, Integer> DEPOTS =
            Map.of("Nahida", 7301, "Furina", 8901, "Clorinde", 9801);
    private static final Map<String, AbilityData> GUARDS = new HashMap<>();
    private static List<AvatarSkillData> skills;
    private static List<AvatarSkillDepotData> depots;
    private final Map<Integer, AvatarSkillData> oldSkills = new HashMap<>();
    private final Map<Integer, AvatarSkillDepotData> oldDepots = new HashMap<>();
    private AbilityTestFixture fixture;
    private EntityAvatar caster;
    private Ability guard;
    private String guardName;
    private int skill;
    private GameSession session;

    @BeforeAll
    static void resources() throws Exception {
        var root = Path.of("resources");
        skills =
                JsonUtils.loadToList(
                        root.resolve("ExcelBinOutput/AvatarSkillExcelConfigData.json"),
                        AvatarSkillData.class);
        depots =
                JsonUtils.loadToList(
                                root.resolve("ExcelBinOutput/AvatarSkillDepotExcelConfigData.json"),
                                AvatarSkillDepotData.class)
                        .stream()
                        .filter(d -> DEPOTS.containsValue(d.getId()))
                        .toList();
        for (var character : DEPOTS.keySet()) {
            var data =
                    JsonUtils.loadToList(
                                    root.resolve(
                                            "BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"
                                                    + character
                                                    + ".json"),
                                    AbilityConfigData.class)
                            .stream()
                            .map(row -> row.Default)
                            .filter(
                                    a ->
                                            a.abilityName.contains("ElementalBurst")
                                                    && a.modifiers != null
                                                    && a.modifiers.values().stream()
                                                            .anyMatch(
                                                                    m ->
                                                                            m.state
                                                                                    == AbilityModifier
                                                                                            .State
                                                                                            .Invincible))
                            .findFirst()
                            .orElseThrow();
            GUARDS.put(character, data);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        for (var depot : depots) {
            oldDepots.put(
                    depot.getId(), GameData.getAvatarSkillDepotDataMap().put(depot.getId(), depot));
            var data =
                    skills.stream()
                            .filter(s -> s.getId() == depot.getEnergySkill())
                            .findFirst()
                            .orElseThrow();
            oldSkills.put(data.getId(), GameData.getAvatarSkillDataMap().put(data.getId(), data));
            depot.onLoad();
        }
        session =
                new GameSession(null) {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }
                };
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        character("Nahida");
    }

    @AfterEach
    void restore() {
        oldSkills.forEach((id, value) -> restore(GameData.getAvatarSkillDataMap(), id, value));
        oldDepots.forEach((id, value) -> restore(GameData.getAvatarSkillDepotDataMap(), id, value));
        fixture.close();
    }

    private static <K, V> void restore(Map<K, V> map, K id, V value) {
        if (value == null) map.remove(id);
        else map.put(id, value);
    }

    private EntityAvatar avatar(String character) {
        int depotId = DEPOTS.get(character);
        var avatar =
                new Avatar() {
                    {
                        setSkillDepot(GameData.getAvatarSkillDepotDataMap().get(depotId));
                    }

                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public int getAvatarId() {
                        return 10000000 + depotId / 100;
                    }

                    @Override
                    public int getSkillDepotId() {
                        return depotId;
                    }

                    @Override
                    public it.unimi.dsi.fastutil.ints.IntSet getTalentIdList() {
                        return it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET;
                    }

                    @Override
                    public Set<Integer> getProudSkillList() {
                        return Set.of();
                    }

                    @Override
                    public void save() {}
                };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        var entity = new EntityAvatar(fixture.scene, avatar);
        fixture.scene.getEntities().put(entity.getId(), entity);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        return entity;
    }

    private void character(String character) {
        fixture.player.getTeamManager().getActiveTeam().clear();
        fixture.player.getTeamManager().setCurrentCharacterIndex(0);
        caster = avatar(character);
        skill = GameData.getAvatarSkillDepotDataMap().get(DEPOTS.get(character)).getEnergySkill();
        var data = JsonUtils.decode(JsonUtils.encode(GUARDS.get(character)), AbilityData.class);
        guardName =
                data.modifiers.entrySet().stream()
                        .filter(e -> e.getValue().state == AbilityModifier.State.Invincible)
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElseThrow();
        guard = new Ability(data, caster, fixture.player);
        caster.getInstancedAbilities().add(guard);
    }

    private void add(Ability ability, String name, int modifierId) throws Exception {
        var change =
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                        .setParentAbilityName(
                                AbilityString.newBuilder().setStr(ability.getData().abilityName))
                        .setModifierLocalId(
                                new ArrayList<>(ability.getData().modifiers.keySet())
                                        .indexOf(name));
        invoke(modifierId, change.build().toByteString());
    }

    private void invoke(int modifierId, ByteString data) throws Exception {
        fixture.player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(caster.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(modifierId)
                                                .setTargetId(caster.getId()))
                                .setAbilityData(data)
                                .build());
    }

    private void protect() throws Exception {
        fixture.player.getAbilityManager().onSkillStart(fixture.player, skill, caster.getId());
        add(guard, guardName, 71);
    }

    private void remove(int id) throws Exception {
        invoke(
                id,
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_REMOVED)
                        .build()
                        .toByteString());
    }

    private float hp(GameEntity target) {
        return target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    private AttackResult attack(GameEntity source, GameEntity target) {
        return AttackResult.newBuilder()
                .setAttackerId(source.getId())
                .setDefenseId(target.getId())
                .setDamage(25)
                .build();
    }

    private EntityClientGadget gadget() {
        var gadget =
                new EntityClientGadget(
                        fixture.scene,
                        fixture.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(192)
                                .setOwnerEntityId(caster.getId())
                                .build());
        fixture.scene.getEntities().put(gadget.getId(), gadget);
        return gadget;
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nahida", "Furina", "Clorinde"})
    void actual_burst_guards_protect_only_until_their_removal(String character) throws Exception {
        character(character);
        protect();
        assertTrue(fixture.player.getAbilityManager().isAbilityInvulnerable());
        fixture.scene.handleAttack(attack(fixture.entity, caster));
        assertEquals(100, hp(caster));
        remove(71);
        fixture.scene.handleAttack(attack(fixture.entity, caster));
        assertEquals(75, hp(caster));
        assertFalse(fixture.player.getAbilityManager().isAbilityInvulnerable());
    }

    @Test
    void an_unprotected_character_takes_ordinary_damage() {
        fixture.scene.handleAttack(attack(fixture.entity, caster));
        assertEquals(75, hp(caster));
        assertFalse(fixture.player.getAbilityManager().isAbilityInvulnerable());
    }

    @Test
    void a_guard_does_not_require_an_energy_cost_or_burst_guess() throws Exception {
        add(guard, guardName, 71);
        assertTrue(fixture.player.getAbilityManager().isAbilityInvulnerable());
        fixture.scene.handleAttack(attack(fixture.entity, caster));
        assertEquals(100, hp(caster));
    }

    @Test
    void switching_characters_does_not_give_the_new_character_the_old_guard() throws Exception {
        protect();
        var next = avatar("Furina");
        fixture.player.getTeamManager().setCurrentCharacterIndex(1);
        fixture.scene.handleAttack(attack(fixture.entity, next));
        assertEquals(75, hp(next));
        fixture.scene.handleAttack(attack(fixture.entity, caster));
        assertEquals(100, hp(caster));
        assertFalse(fixture.player.getAbilityManager().isAbilityInvulnerable());
    }

    @Test
    void a_summon_can_damage_an_enemy_while_its_owner_has_a_burst_guard() throws Exception {
        protect();
        var summon = gadget();
        var ability =
                new Ability(
                        JsonUtils.decode("{abilityName:'Summon_Drain'}", AbilityData.class),
                        summon,
                        fixture.player);
        summon.getInstancedAbilities().add(ability);
        var action =
                JsonUtils.decode(
                        "{$type:'LoseHP',amount:15,lethal:true}", AbilityModifierAction.class);
        assertTrue(new ActionLoseHP().execute(ability, action, ByteString.EMPTY, fixture.entity));
        assertEquals(85, hp(fixture.entity));
        assertEquals(100, hp(caster));
    }

    @Test
    void combat_hit_queue_keeps_summon_hits_against_other_targets() throws Exception {
        protect();
        var hit = attack(gadget(), fixture.entity);
        var entry =
                CombatInvokeEntry.newBuilder()
                        .setArgumentType(CombatTypeArgument.CombatTypeArgument_COMBAT_EVT_BEING_HIT)
                        .setForwardType(ForwardType.ForwardType_FORWARD_TO_ALL)
                        .setCombatData(
                                EvtBeingHitInfo.newBuilder()
                                        .setAttackResult(hit)
                                        .build()
                                        .toByteString());
        new HandlerCombatInvocationsNotify()
                .handle(
                        session,
                        new byte[0],
                        CombatInvocationsNotify.newBuilder()
                                .addInvokeList(entry)
                                .build()
                                .toByteArray());
        assertEquals(1, fixture.player.getAttackResults().size());
        fixture.scene.handleAttack(fixture.player.getAttackResults().poll());
        assertEquals(75, hp(fixture.entity));
    }

    @Test
    void a_summon_without_its_caster_cannot_apply_an_unresolved_hp_cost() {
        var summon = gadget();
        fixture.scene.getEntities().remove(caster.getId());
        var ability =
                new Ability(
                        JsonUtils.decode("{abilityName:'Departed_Summon'}", AbilityData.class),
                        summon,
                        fixture.player);
        var action =
                JsonUtils.decode(
                        "{$type:'LoseHP',amount:15,lethal:true}", AbilityModifierAction.class);
        assertFalse(
                assertDoesNotThrow(
                        () ->
                                new ActionLoseHP()
                                        .execute(
                                                ability,
                                                action,
                                                ByteString.EMPTY,
                                                fixture.entity)));
        assertEquals(100, hp(fixture.entity));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ability_initialization_notifications_do_not_end_an_active_guard(boolean combined)
            throws Exception {
        protect();
        if (combined)
            new HandlerClientAbilitiesInitFinishCombineNotify()
                    .handle(
                            session,
                            new byte[0],
                            ClientAbilitiesInitFinishCombineNotify.getDefaultInstance()
                                    .toByteArray());
        else
            new HandlerClientAbilityInitFinishNotify()
                    .handle(
                            session,
                            new byte[0],
                            ClientAbilityInitFinishNotify.newBuilder()
                                    .setEntityId(fixture.entity.getId())
                                    .build()
                                    .toByteArray());
        assertTrue(fixture.player.getAbilityManager().isAbilityInvulnerable());
        fixture.scene.handleAttack(attack(fixture.entity, caster));
        assertEquals(100, hp(caster));
        remove(71);
        fixture.scene.handleAttack(attack(fixture.entity, caster));
        assertEquals(75, hp(caster));
    }

    @Test
    void replacing_the_guard_controller_restores_damage() throws Exception {
        protect();
        var replacement =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'Replacement',modifiers:{Other:{}}}",
                                AbilityData.class),
                        caster,
                        fixture.player);
        caster.getInstancedAbilities().add(replacement);
        add(replacement, "Other", 71);
        fixture.scene.handleAttack(attack(fixture.entity, caster));
        assertEquals(75, hp(caster));
        assertFalse(fixture.player.getAbilityManager().isAbilityInvulnerable());
    }

    @Test
    void explicit_skill_end_keeps_a_still_attached_guard_owned_by_its_modifier() throws Exception {
        protect();
        fixture.player.getAbilityManager().onSkillEnd(fixture.player);
        assertTrue(fixture.player.getAbilityManager().isAbilityInvulnerable());
        fixture.scene.handleAttack(attack(fixture.entity, caster));
        assertEquals(100, hp(caster));
    }

    @Test
    void an_older_guard_removal_cannot_end_another_live_guard() throws Exception {
        protect();
        add(guard, guardName, 72);
        remove(71);
        assertTrue(fixture.player.getAbilityManager().isAbilityInvulnerable());
        fixture.scene.handleAttack(attack(fixture.entity, caster));
        assertEquals(100, hp(caster));
        remove(72);
        fixture.scene.handleAttack(attack(fixture.entity, caster));
        assertEquals(75, hp(caster));
    }

    @Test
    void empty_team_status_does_not_recreate_a_character_or_keep_the_old_guard() throws Exception {
        protect();
        fixture.player.getTeamManager().getActiveTeam().clear();
        assertFalse(fixture.player.getAbilityManager().isAbilityInvulnerable());
        assertTrue(fixture.player.getTeamManager().getActiveTeam().isEmpty());
    }

    @Test
    void a_departed_or_inactive_entity_does_not_protect_the_player() throws Exception {
        protect();
        caster.invalidateAbilityLifecycle();
        assertFalse(fixture.player.getAbilityManager().isAbilityInvulnerable());
        caster.activateAbilityLifecycle();
        fixture.player.setScene(null);
        assertFalse(fixture.player.getAbilityManager().isAbilityInvulnerable());
    }
}
