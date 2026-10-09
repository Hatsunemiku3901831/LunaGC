package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ClorindeBondTest {
    private static final Path RESOURCES = Path.of("resources");
    private static final String LIFETIME = "UNIQUE_Clorinde_ElementalArt_LifeTimeHandler";
    private static List<AbilityConfigData> configs;
    private static List<ProudSkillData> proudSkills;
    private static List<AvatarSkillData> skills;
    private static AvatarSkillDepotData depot;
    private static Map<String, OpenConfigEntry> openConfigs;
    private AbilityTestFixture fixture;
    private EntityAvatar clorinde;
    private Ability activate;
    private Ability healer;
    private final Map<Integer, ProudSkillData> oldProud = new HashMap<>();
    private final Map<Integer, AvatarSkillData> oldSkills = new HashMap<>();
    private final Map<String, OpenConfigEntry> oldOpenConfigs = new HashMap<>();
    private AvatarSkillDepotData oldDepot;

    @BeforeAll
    static void resources() throws Exception {
        configs = JsonUtils.loadToList(RESOURCES.resolve(
                "BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Clorinde.json"), AbilityConfigData.class);
        proudSkills = JsonUtils.loadToList(RESOURCES.resolve("ExcelBinOutput/ProudSkillExcelConfigData.json"),
                ProudSkillData.class).stream().filter(p -> p.getOpenConfig() != null
                        && p.getOpenConfig().startsWith("Clorinde_")).toList();
        skills = JsonUtils.loadToList(RESOURCES.resolve("ExcelBinOutput/AvatarSkillExcelConfigData.json"),
                AvatarSkillData.class).stream().filter(s -> List.of(10981, 10982, 10985).contains(s.getId())).toList();
        depot = JsonUtils.loadToList(RESOURCES.resolve("ExcelBinOutput/AvatarSkillDepotExcelConfigData.json"),
                AvatarSkillDepotData.class).stream().filter(d -> d.getId() == 9801).findFirst().orElseThrow();
        openConfigs = new HashMap<>();
        var raw = JsonParser.parseString(Files.readString(RESOURCES.resolve(
                "BinOutput/Talent/AvatarTalents/ConfigTalent_Clorinde.json"))).getAsJsonObject();
        for (var entry : raw.entrySet()) openConfigs.put(entry.getKey(), new OpenConfigEntry(entry.getKey(),
                JsonUtils.decode(entry.getValue().toString(), OpenConfigData[].class)));
    }

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        for (var p : proudSkills) oldProud.put(p.getId(), GameData.getProudSkillDataMap().put(p.getId(), p));
        for (var s : skills) oldSkills.put(s.getId(), GameData.getAvatarSkillDataMap().put(s.getId(), s));
        openConfigs.forEach((key, value) -> oldOpenConfigs.put(key, GameData.getOpenConfigEntries().put(key, value)));
        oldDepot = GameData.getAvatarSkillDepotDataMap().put(9801, depot);
        clorinde = avatar(10, 40);
        fixture.player.getTeamManager().getActiveTeam().add(clorinde);
        activate = ability("Avatar_Clorinde_ElementalArt_Activate", clorinde);
        clorinde.getInstancedAbilities().add(activate);
        healer = new Ability(JsonUtils.decode("{\"abilityName\":\"Test_Healer\"}", AbilityData.class),
                fixture.entity, fixture.player);
    }

    @AfterEach
    void restore() {
        oldProud.forEach((key, value) -> restore(GameData.getProudSkillDataMap(), key, value));
        oldSkills.forEach((key, value) -> restore(GameData.getAvatarSkillDataMap(), key, value));
        oldOpenConfigs.forEach((key, value) -> restore(GameData.getOpenConfigEntries(), key, value));
        restore(GameData.getAvatarSkillDepotDataMap(), 9801, oldDepot);
        fixture.close();
    }

    private static <K, V> void restore(Map<K, V> map, K key, V value) {
        if (value == null) map.remove(key); else map.put(key, value);
    }

    private EntityAvatar avatar(float hp, float debt) {
        var avatar = new Avatar() {
            private final Map<Integer, Integer> levels = new HashMap<>();
            private final Set<Integer> proud = new HashSet<>();
            @Override public Player getPlayer() { return fixture.player; }
            @Override public int getAvatarId() { return 10000098; }
            @Override public int getSkillDepotId() { return 9801; }
            @Override public IntSet getTalentIdList() { return IntSets.EMPTY_SET; }
            @Override public Map<Integer, Integer> getSkillLevelMap() { return levels; }
            @Override public Set<Integer> getProudSkillList() { return proud; }
            @Override public void save() {}
        };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, debt);
        var entity = new EntityAvatar(fixture.scene, avatar);
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private Ability ability(String name, EntityAvatar owner) {
        var raw = configs.stream().map(row -> row.Default).filter(d -> name.equals(d.abilityName))
                .findFirst().orElseThrow();
        return new Ability(JsonUtils.decode(JsonUtils.encode(raw), AbilityData.class), owner, fixture.player);
    }

    private void execute(Ability ability, AbilityModifierAction action, EntityAvatar target) {
        ability.getManager().executeActionNow(ability, action, ByteString.EMPTY, target);
    }

    private void install(Ability ability, EntityAvatar target) {
        ability.registerModifier(target, new AbilityModifierController(ability, ability.getData(),
                ability.getData().modifiers.get(LIFETIME)));
        for (var action : ability.getData().modifiers.get(LIFETIME).onAdded)
            if (action.type == AbilityModifierAction.Type.Predicated)
                execute(ability, action, target);
    }

    private void heal(float amount) { heal(healer, amount, null, clorinde); }

    private void heal(Ability ability, float amount, String tag, EntityAvatar target) {
        var action = JsonUtils.decode("{\"$type\":\"HealHP\",\"amount\":" + amount + "}", AbilityModifierAction.class);
        action.healTag = tag;
        execute(ability, action, target);
    }

    private float debt(EntityAvatar target) { return target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS); }
    private float hp(EntityAvatar target) { return target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP); }

    @Test
    void externalHealingAddsEightyPercentBondWithoutRestoringHp() {
        install(activate, clorinde);
        heal(10);
        assertEquals(48f, debt(clorinde));
        assertEquals(10f, hp(clorinde));
    }

    @Test
    void unlockedAscensionPassiveConvertsOneHundredPercent() {
        clorinde.getAvatar().getProudSkillList().add(982201);
        activate = ability("Avatar_Clorinde_ElementalArt_Activate", clorinde);
        install(activate, clorinde);
        assertEquals(1f, activate.getAbilitySpecials().getFloat("SwitchHealToHPDebtsRatio_Final"));
        heal(10);
        assertEquals(50f, debt(clorinde));
    }

    @Test
    void healingConversionCapsBondAtTwoHundredPercent() {
        install(activate, clorinde);
        heal(1000);
        assertEquals(200f, debt(clorinde));
        assertEquals(10f, hp(clorinde));
    }

    @Test
    void aFullHealthAvatarStillReceivesConvertedBond() {
        clorinde.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        install(activate, clorinde);
        heal(10);
        assertEquals(48f, debt(clorinde));
    }

    @Test
    void theResourceMediumImpaleHealsAfterRepayingBond() {
        install(activate, clorinde);
        var dodge = ability("Avatar_Clorinde_ElementalArt_Dodge", clorinde);
        var action = Arrays.stream(dodge.getData().onAbilityStart[1].successActions)
                .filter(a -> a.type == AbilityModifierAction.Type.HealHP).findFirst().orElseThrow();
        execute(dodge, action, clorinde);
        assertEquals(0f, debt(clorinde));
        assertEquals(11.6f, hp(clorinde), 0.0001f);
    }

    @Test
    void theResourceFullImpaleHealsAfterRepayingBond() {
        clorinde.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 120);
        install(activate, clorinde);
        var dodge = ability("Avatar_Clorinde_ElementalArt_Dodge", clorinde);
        var action = Arrays.stream(dodge.getData().onAbilityStart[0].successActions)
                .filter(a -> a.type == AbilityModifierAction.Type.HealHP).findFirst().orElseThrow();
        execute(dodge, action, clorinde);
        assertEquals(0f, debt(clorinde));
        assertEquals(22f, hp(clorinde), 0.0001f);
    }

    @Test
    void removingTheLifetimeModifierRestoresOrdinaryHealing() {
        install(activate, clorinde);
        assertTrue(activate.removeModifier(clorinde, LIFETIME));
        heal(50);
        assertEquals(0f, debt(clorinde));
        assertEquals(20f, hp(clorinde));
    }

    @Test
    void conversionIsAttachedToTheRecipientAndKeepsItsOwnRatio() {
        install(activate, clorinde);
        var second = avatar(10, 40);
        var otherAbility = ability("Avatar_Clorinde_ElementalArt_Activate", second);
        otherAbility.getAbilitySpecials().put("SwitchHealToHPDebtsRatio", 1f);
        install(otherAbility, second);
        heal(healer, 10, null, second);
        heal(10);
        assertEquals(50f, debt(second));
        assertEquals(48f, debt(clorinde));
        assertNotSame(activate, otherAbility);
    }

    @Test
    void duplicateNamedAndInstancedRegistrationsDoNotDoubleTheConversion() {
        install(activate, clorinde);
        var controller = activate.getModifiers(clorinde).get(LIFETIME);
        clorinde.getInstancedModifiers().put(77, controller);
        heal(10);
        assertEquals(48f, debt(clorinde));
    }

    @Test
    void zeroOrInvalidConversionRatiosDoNotFallThroughToHealing() {
        install(activate, clorinde);
        for (float ratio : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            activate.getAbilitySpecials().put("SwitchHealToHPDebtsRatio_Final", ratio);
            heal(100);
            assertEquals(40f, debt(clorinde));
            assertEquals(10f, hp(clorinde));
        }
    }

    @Test
    void aDeadAvatarCannotGainBondThroughConversion() {
        install(activate, clorinde);
        clorinde.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        clorinde.checkIfDead();
        heal(10);
        assertEquals(0f, debt(clorinde));
        assertEquals(0f, hp(clorinde));
    }

    @Test
    void aSkillStartDoesNotAddBondOnTopOfItsResourceActions() {
        for (int skill : new int[]{10982, 10985})
            fixture.player.getAbilityManager().onSkillStart(fixture.player, skill, clorinde.getId());
        assertEquals(40f, debt(clorinde));
    }

    @Test
    void bookkeepingGlobalWritesDoNotGrantAnExtraGunshotBond() throws Exception {
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder()
                .setEntityId(clorinde.getId())
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE)
                .setAbilityData(AbilityScalarValueEntry.newBuilder().setFloatValue(0)
                        .setKey(AbilityString.newBuilder().setStr("_ABILITY_Clorinde_Dodge_HealFlag")).build().toByteString())
                .build());
        assertEquals(40f, debt(clorinde));
    }

    @Test
    void ordinaryHitsDoNotPayAnInventedPercentageOfBond() {
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 1000);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1000);
        for (String name : new String[]{"NormalAttack", "ElementalArt", "ElementalBurst"})
            fixture.scene.handleAttack(AttackResult.newBuilder().setAttackerId(clorinde.getId())
                    .setDefenseId(fixture.entity.getId()).setDamage(1).setAnimEventId(name).setElementType(4).build());
        assertEquals(40f, debt(clorinde));
    }

    @Test
    void theActualGunshotResourceAddsBondOnlyBelowItsNativeStrictGreaterGate() {
        var gun = ability("Avatar_Clorinde_ElementalArt_GunShot", clorinde);
        execute(gun, gun.getData().onAbilityStart[0], clorinde);
        assertEquals(75f, debt(clorinde));
        execute(gun, gun.getData().onAbilityStart[0], clorinde);
        assertEquals(110f, debt(clorinde));
        execute(gun, gun.getData().onAbilityStart[0], clorinde);
        assertEquals(110f, debt(clorinde));
    }

    @Test
    void theExactGunshotBoundaryFollowsCurrentClientResources() {
        clorinde.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 100);
        var gun = ability("Avatar_Clorinde_ElementalArt_GunShot", clorinde);
        execute(gun, gun.getData().onAbilityStart[0], clorinde);
        assertEquals(135f, debt(clorinde));
    }

    @Test
    void burstBondUsesTheActualTalentLevelInsteadOfAlwaysSixtySixPercent() {
        clorinde.getAvatar().getSkillLevelMap().put(10985, 10);
        var burst = ability("Avatar_Clorinde_ElementalBurst_Camera", clorinde);
        execute(burst, burst.getData().onAbilityStart[0], clorinde);
        assertEquals(160f, debt(clorinde), 0.0001f);
    }

    @Test
    void aBareSwitchMixinInvocationDoesNotLeaveAStickyEntityFlag() {
        activate.getManager().executeMixin(activate, JsonUtils.decode(
                "{\"$type\":\"SwitchHealToHPDebtsMixin\"}", AbilityMixinData.class), ByteString.EMPTY);
        assertFalse(clorinde.isConvertToHpDebt());
    }

    @Test
    void clientModifierRemovalEndsConversionWithoutRunningHealingTwice() throws Exception {
        install(activate, clorinde);
        clorinde.getInstancedModifiers().put(77, activate.getModifiers(clorinde).get(LIFETIME));
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder()
                .setEntityId(clorinde.getId())
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedModifierId(77))
                .setAbilityData(AbilityMetaModifierChange.newBuilder().setAction(ModifierAction.MODIFIER_ACTION_REMOVED)
                        .build().toByteString()).build());
        heal(50);
        assertEquals(0f, debt(clorinde));
        assertEquals(20f, hp(clorinde));
    }

    @Test
    void theActualClientModifierAdditionInitializesItsConversionRatio() throws Exception {
        int localId = new ArrayList<>(activate.getData().modifiers.keySet()).indexOf(LIFETIME);
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder()
                .setEntityId(clorinde.getId())
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(AbilityInvokeEntryHead.newBuilder().setTargetId(clorinde.getId())
                        .setInstancedAbilityId(1).setInstancedModifierId(77))
                .setAbilityData(AbilityMetaModifierChange.newBuilder().setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                        .setParentAbilityName(AbilityString.newBuilder().setStr(activate.getData().abilityName))
                        .setModifierLocalId(localId).build().toByteString()).build());
        assertSame(activate, clorinde.getInstancedModifiers().get(77).getAbility());
        heal(10);
        assertEquals(48f, debt(clorinde));
        assertEquals(10f, hp(clorinde));
    }

    @Test
    void anOldClientInstanceCannotReviveAnExpiredReplacement() {
        install(activate, clorinde);
        var old = activate.getModifiers(clorinde).get(LIFETIME);
        clorinde.getInstancedModifiers().put(76, old);
        var replacement = new AbilityModifierController(activate, activate.getData(),
                activate.getData().modifiers.get(LIFETIME));
        activate.registerModifier(clorinde, replacement);
        clorinde.getInstancedModifiers().put(77, replacement);
        activate.unregisterModifier(clorinde, replacement);
        clorinde.getInstancedModifiers().remove(77);
        heal(50);
        assertEquals(0f, debt(clorinde));
        assertEquals(20f, hp(clorinde));
    }

    @Test
    void aHealersSameNamedOverrideDoesNotReplaceTheRecipientsConversionRatio() {
        install(activate, clorinde);
        healer.getAbilitySpecials().put("SwitchHealToHPDebtsRatio_Final", 100f);
        heal(10);
        assertEquals(48f, debt(clorinde));
    }

    @Test
    void concurrentExternalHealsKeepEveryBondAdditionWithoutChangingHp() throws Exception {
        install(activate, clorinde);
        clorinde.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 0);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
            for (int i = 0; i < 100; i++) tasks.add(() -> { heal(1); return null; });
            for (var future : pool.invokeAll(tasks)) future.get();
        } finally { pool.shutdownNow(); }
        assertEquals(80f, debt(clorinde), 0.001f);
        assertEquals(10f, hp(clorinde));
    }
}
