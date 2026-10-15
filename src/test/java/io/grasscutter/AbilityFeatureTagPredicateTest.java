package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.excels.FeatureTagGroupData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class AbilityFeatureTagPredicateTest {
    AbilityTestFixture fixture;
    Ability ability;
    static List<FeatureTagGroupData> groups;
    static List<AvatarData> avatars;
    static List<AvatarSkillDepotData> depots;
    final List<Runnable> restores = new ArrayList<>();

    @BeforeAll
    static void resources() throws Exception {
        var path = Path.of("resources/ExcelBinOutput");
        groups =
                JsonUtils.loadToList(
                        path.resolve("FeatureTagGroupExcelConfigData.json"),
                        FeatureTagGroupData.class);
        avatars =
                JsonUtils.loadToList(path.resolve("AvatarExcelConfigData.json"), AvatarData.class);
        depots =
                JsonUtils.loadToList(
                        path.resolve("AvatarSkillDepotExcelConfigData.json"),
                        AvatarSkillDepotData.class);
    }

    <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restores.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        ability =
                new Ability(
                        JsonUtils.decode("{abilityName:'FeatureTags'}", AbilityData.class),
                        fixture.entity,
                        fixture.player);
        groups.forEach(row -> put(GameData.getFeatureTagGroupDataMap(), row.getId(), row));
        avatars.forEach(row -> put(GameData.getAvatarDataMap(), row.getId(), row));
        depots.forEach(row -> put(GameData.getAvatarSkillDepotDataMap(), row.getId(), row));
    }

    @AfterEach
    void close() {
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
        fixture.close();
    }

    EntityAvatar avatar(int id) {
        return avatar(id, new AtomicInteger());
    }

    EntityAvatar avatar(int id, AtomicInteger depot) {
        return new EntityAvatar(
                fixture.scene,
                new Avatar() {
                    @Override
                    public int getAvatarId() {
                        return id;
                    }

                    @Override
                    public int getSkillDepotId() {
                        return depot.get();
                    }

                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public void save() {}
                }) {
            @Override
            public void initAbilities() {}
        };
    }

    @SuppressWarnings("unchecked")
    boolean check(String json, GameEntity target) {
        return PredicateEvaluator.evaluate(
                JsonUtils.decode(json, Map.class), ability, fixture.entity, target, null);
    }

    @Test
    void anEntityWithoutAResourceTagGroupCannotPass() {
        assertFalse(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10096]}", fixture.entity));
    }

    @Test
    void anEmptyAlternativeDoesNotPass() {
        assertFalse(check("{$type:'GPEMEIHPCCF',predicates:[]}", fixture.entity));
    }

    @Test
    void alternativesFailWhenEveryChildFails() {
        assertFalse(
                check(
                        """
                {$type:'GPEMEIHPCCF',predicates:[
                    {$type:'ByTargetGlobalValue',key:'a',value:1},
                    {$type:'ByTargetGlobalValue',key:'b',value:2}]}
                """,
                        fixture.entity));
    }

    @Test
    void alternativesCannotBypassTheMissingTargetGuard() {
        assertFalse(
                check(
                        """
                {$type:'GPEMEIHPCCF',predicates:[
                    {$type:'ByTargetGlobalValue',target:'Owner',key:'a',value:0}]}
                """,
                        null));
    }

    @Test
    void anUnknownAlternativeIsRejectedBeforeTheTrueChild() {
        assertFalse(
                check(
                        """
                {$type:'GPEMEIHPCCF',predicates:[
                    {$type:'ByTargetGlobalValue',key:'a',value:0},
                    {$type:'UnknownFeatureCondition'}]}
                """,
                        fixture.entity));
    }

    @Test
    void groupResourcesUseTheStandardResourceLoaderMap() {
        assertSame(
                GameData.getFeatureTagGroupDataMap(),
                GameData.getMapByResourceDef(FeatureTagGroupData.class));
        assertEquals(10000096, GameData.getAvatarDataMap().get(10000096).getFeatureTagGroupID());
        assertTrue(
                Arrays.stream(GameData.getFeatureTagGroupDataMap().get(10000096).getTagIds())
                        .anyMatch(tag -> tag == 10096));
    }

    @Test
    void aLeafRequiresEveryTagAndAllowsTheNativeEmptySet() {
        var member = avatar(10000096);
        assertTrue(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10096,1042]}", member));
        assertFalse(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10096,1044]}", member));
        assertFalse(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10089]}", member));
        assertTrue(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[]}", member));
        assertFalse(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[]}", null));
    }

    @Test
    void membershipComesFromTheGroupReferenceRatherThanTheAvatarNumber() {
        put(
                GameData.getAvatarDataMap(),
                10000096,
                JsonUtils.decode("{id:10000096,featureTagGroupID:10000089}", AvatarData.class));
        var member = avatar(10000096);
        assertTrue(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10089]}", member));
        assertFalse(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10096]}", member));
        GameData.getFeatureTagGroupDataMap().remove(10000089);
        assertFalse(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[]}", member));
    }

    @Test
    void theTravelersCurrentSkillDepotOverridesTheBaseGroupWithoutCaching() {
        var depot = new AtomicInteger(502);
        var traveler = avatar(10000005, depot);
        assertEquals(
                11000056, GameData.getAvatarSkillDepotDataMap().get(502).getFeatureTagGroupID());
        assertFalse(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[2104]}", traveler));
        depot.set(505);
        assertTrue(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[2104,2105]}", traveler));
        depot.set(0);
        assertFalse(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[2104]}", traveler));
    }

    @Test
    void monstersAndVehiclesUseTheirReferencedGroups() {
        var monsterData =
                JsonUtils.decode(
                        "{id:21001,featureTagGroupID:10000096,equips:[]}", MonsterData.class);
        var monster =
                new EntityMonster(fixture.scene, monsterData, new Position(), new Position(), 1) {
                    @Override
                    public void initAbilities() {}

                    @Override
                    public void recalcStats() {}
                };
        assertTrue(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10096]}", monster));
        var vehicle =
                new EntityVehicle(
                        fixture.scene,
                        fixture.player,
                        45002004,
                        0,
                        new Position(),
                        new Position()) {
                    @Override
                    public void initAbilities() {}
                };
        put(
                GameData.getVehicleDataMap(),
                45002004,
                JsonUtils.decode("{id:45002004,featureTagGroupID:10000089}", VehicleData.class));
        assertTrue(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10089]}", vehicle));
        assertFalse(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10096]}", vehicle));
        vehicle.setVehicleDataA(
                JsonUtils.decode("{id:45002004,featureTagGroupID:10000096}", VehicleData.class));
        assertTrue(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10096]}", vehicle));
    }

    @Test
    void leavesAndAlternativesResolveTheirOwnTargets() {
        var caster = avatar(10000096);
        var recipient = avatar(10000089);
        ability = new Ability(ability.getData(), caster, fixture.player);
        assertFalse(check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10096]}", recipient));
        assertTrue(
                check("{$type:'AJPLICCNACO',BFLILDJLGHP:[10096],forceByCaster:true}", recipient));
        assertTrue(
                check(
                        """
                {$type:'GPEMEIHPCCF',target:'Caster',predicates:[
                    {$type:'AJPLICCNACO',BFLILDJLGHP:[10096]}]}
                """,
                        recipient));
        assertTrue(
                check(
                        """
                {$type:'GPEMEIHPCCF',predicates:[
                    {$type:'AJPLICCNACO',BFLILDJLGHP:[10096],target:'Caster'}]}
                """,
                        recipient));
        assertFalse(
                check(
                        """
                {$type:'GPEMEIHPCCF',target:'Caster',predicates:[
                    {$type:'AJPLICCNACO',BFLILDJLGHP:[10089]}]}
                """,
                        recipient));
    }

    @Test
    void alternativesAllowAnyMatchIncludingTheNativeFieldAlias() {
        String children =
                "[{$type:'ByTargetGlobalValue',key:'a',value:1},"
                        + "{$type:'ByTargetGlobalValue',key:'b',value:0}]";
        assertTrue(check("{$type:'GPEMEIHPCCF',predicates:" + children + "}", fixture.entity));
        assertTrue(check("{$type:'GPEMEIHPCCF',MIMBECHCFKC:" + children + "}", fixture.entity));
        assertFalse(
                check(
                        "{$type:'GPEMEIHPCCF',predicates:[],MIMBECHCFKC:" + children + "}",
                        fixture.entity));
    }

    @Test
    void malformedLeavesAndCompositeBranchesDoNotPassOrOverflow() {
        var member = avatar(10000096);
        for (String fields :
                List.of(
                        "",
                        ",BFLILDJLGHP:null",
                        ",BFLILDJLGHP:'10096'",
                        ",BFLILDJLGHP:[null]",
                        ",BFLILDJLGHP:['10096']",
                        ",BFLILDJLGHP:[-1]",
                        ",BFLILDJLGHP:[10096.5]",
                        ",BFLILDJLGHP:[4294967296]",
                        ",BFLILDJLGHP:[10096],reject:true",
                        ",BFLILDJLGHP:[10096],forceByCaster:1",
                        ",BFLILDJLGHP:[10096],target:'Unknown'"))
            assertFalse(check("{$type:'AJPLICCNACO'" + fields + "}", member), fields);
        for (String fields :
                List.of(
                        "",
                        ",predicates:null",
                        ",predicates:[null]",
                        ",predicates:[1]",
                        ",predicates:[{}]",
                        ",predicates:[],reject:true"))
            assertFalse(check("{$type:'GPEMEIHPCCF'" + fields + "}", member), fields);
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("$type", "GPEMEIHPCCF");
        cyclic.put("predicates", List.of(cyclic));
        assertFalse(PredicateEvaluator.evaluate(cyclic, ability, fixture.entity, member, null));
    }

    @Test
    void anEmptyCurrentTeamDoesNotCreateAnAvatarWhileCheckingConditions() {
        assertTrue(fixture.player.getTeamManager().getActiveTeam().isEmpty());
        var recipient = avatar(10000096);
        assertFalse(
                check("{$type:'AJPLICCNACO',target:'CurLocalAvatar',BFLILDJLGHP:[]}", recipient));
        assertFalse(
                check(
                        "{$type:'GPEMEIHPCCF',target:'CurLocalAvatar',predicates:["
                                + "{$type:'ByTargetGlobalValue',key:'a',value:0}]}",
                        recipient));
        assertTrue(fixture.player.getTeamManager().getActiveTeam().isEmpty());
        fixture.player.getTeamManager().getActiveTeam().add(recipient);
        assertTrue(
                check(
                        "{$type:'AJPLICCNACO',target:'CurLocalAvatar',BFLILDJLGHP:[10096]}",
                        fixture.entity));
    }

    @Test
    void semanticTagAliasesAcceptMatchingExportDuplicatesAndRejectConflicts() {
        var member = avatar(10000096);
        assertTrue(check("{$type:'AJPLICCNACO',featureTagIDs:[10096]}", member));
        assertTrue(
                check("{$type:'AJPLICCNACO',featureTagIDs:[10096],BFLILDJLGHP:[10096]}", member));
        assertFalse(
                check("{$type:'AJPLICCNACO',featureTagIDs:[10089],BFLILDJLGHP:[10096]}", member));
        assertFalse(check("{$type:'AJPLICCNACO',featureTagIDs:[10096],BFLILDJLGHP:null}", member));
    }

    @Test
    void legacyCompositeLeafAnnotationsDoNotReplaceTheActualAlternativeList() {
        assertTrue(
                check(
                        """
                {$type:'GPEMEIHPCCF',talentParam:'Constellation_6',predicates:[
                    {$type:'ByTargetGlobalValue',key:'unlocked',value:0},
                    {$type:'ByUnlockTalentParam',talentParam:'Constellation_6'}]}
                """,
                        fixture.entity));
        assertTrue(
                check(
                        """
                {$type:'GPEMEIHPCCF',key:'first',value:1,compareType:'Equal',predicates:[
                    {$type:'ByTargetGlobalValue',key:'first',value:1},
                    {$type:'ByTargetGlobalValue',key:'second',value:0}]}
                """,
                        fixture.entity));
    }
}
