package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.AbilityActionCreateGadgetOuterClass.AbilityActionCreateGadget;
import emu.grasscutter.net.proto.AbilityActionSummonOuterClass.AbilityActionSummon;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;

class AbilitySpawnPositionTest {
    AbilityTestFixture fixture;
    LocatedEntity owner;
    Ability ability;
    MonsterData previousMonster;
    static final int MONSTER = 999881;
    static final int GADGET = 999882;

    @BeforeAll
    static void scripts() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        owner = new LocatedEntity(fixture.scene);
        owner.setId(43);
        fixture.scene.getEntities().put(43, owner);
        ability =
                new Ability(
                        JsonUtils.decode("{abilityName:'SpawnPositionTest'}", AbilityData.class),
                        owner,
                        fixture.player);
        previousMonster =
                GameData.getMonsterDataMap()
                        .put(
                                MONSTER,
                                JsonUtils.decode(
                                        "{id:999881,type:'MONSTER_ORDINARY',equips:[],affix:[],propGrowCurves:[],hpBase:100}",
                                        MonsterData.class));
        GameData.setConfigGlobalCombat(
                new Gson()
                        .fromJson(
                                "{defaultAbilities:{defaultMPLevelAbilities:[],levelElementAbilities:[],nonHumanoidMoveAbilities:[]}}",
                                emu.grasscutter.data.binout.config.ConfigGlobalCombat.class));
    }

    @AfterEach
    void close() {
        if (previousMonster == null) GameData.getMonsterDataMap().remove(MONSTER);
        else GameData.getMonsterDataMap().put(MONSTER, previousMonster);
        fixture.scene.getScriptManager().onDestroy();
        fixture.close();
    }

    static class LocatedEntity extends AbilityTestFixture.TestEntity {
        final Position pos = new Position(100, 20, 300);
        final Position rot = new Position(0, 90, 0);

        LocatedEntity(Scene scene) {
            super(scene);
        }

        @Override
        public Position getPosition() {
            return pos;
        }

        @Override
        public Position getRotation() {
            return rot;
        }
    }

    AbilityModifierAction action(String json) {
        return JsonUtils.decode(json, AbilityModifierAction.class);
    }

    void write(String born) {
        fixture.player
                .getAbilityManager()
                .executeActionNow(
                        ability,
                        action("{$type:'SetGlobalPos',key:'point',born:" + born + "}"),
                        ByteString.EMPTY,
                        owner);
    }

    Object positions(GameEntity entity) {
        return assertDoesNotThrow(
                () -> GameEntity.class.getMethod("getAbilityGlobalPositions").invoke(entity));
    }

    Position saved(GameEntity entity, String key) {
        var state = positions(entity);
        return assertDoesNotThrow(
                () ->
                        (Position)
                                state.getClass().getMethod("get", String.class).invoke(state, key));
    }

    void expect(Position actual, float x, float y, float z) {
        assertNotNull(actual);
        assertEquals(x, actual.getX(), 0.0001f);
        assertEquals(y, actual.getY(), 0.0001f);
        assertEquals(z, actual.getZ(), 0.0001f);
    }

    EntityGadget gadget(String born, ByteString payload) {
        var config =
                action(
                        "{$type:'CreateGadget',gadgetID:999882"
                                + (born == null ? "" : ",born:" + born)
                                + "}");
        assertTrue(new ActionCreateGadget().execute(ability, config, payload, owner));
        return fixture.scene.getEntities().values().stream()
                .filter(e -> e instanceof EntityGadget)
                .map(e -> (EntityGadget) e)
                .findFirst()
                .orElseThrow();
    }

    EntityMonster monsterOwner() {
        var entity =
                new EntityMonster(
                        fixture.scene,
                        GameData.getMonsterDataMap().get(MONSTER),
                        owner.pos,
                        owner.rot,
                        1) {
                    @Override
                    public void initAbilities() {}

                    @Override
                    public void recalcStats() {}
                };
        fixture.scene.getEntities().put(entity.getId(), entity);
        fixture.scene.getPlayers().add(fixture.player);
        return entity;
    }

    EntityMonster summon(EntityMonster caster, String born, ByteString payload) {
        var source = new Ability(ability.getData(), caster, fixture.player);
        var config =
                action(
                        "{$type:'Summon',monsterID:999881,summonTag:4"
                                + (born == null ? "" : ",born:" + born)
                                + "}");
        assertTrue(new ActionSummon().execute(source, config, payload, caster));
        return caster.getSummonTagMap().get(4);
    }

    @Test
    void birthConfigRetainsUnknownFieldsForLaterProtocolWork() {
        var born =
                JsonParser.parseString("{$type:'NABPLNFPHCK',offset:{y:2},future:{nested:[1,2]}}");
        var source = action("{$type:'CreateGadget',born:" + born + ",setTarget:true}");
        var encoded = JsonUtils.toJson(source).getAsJsonObject();
        assertEquals(born, encoded.get("born"));
        assertEquals(new JsonPrimitive(true), encoded.get("setTarget"));
    }

    @Test
    void selfPositionUsesAllThreeWorldAxes() {
        write("{$type:'ConfigBornBySelf',offset:{x:1,y:2,z:3}}");
        expect(saved(owner, "point"), 101, 22, 303);
        expect(owner.pos, 100, 20, 300);
    }

    @Test
    void yawRotatesLocalOffsetWithoutTurningHeightIntoHorizontalDistance() {
        write("{$type:'ConfigBornBySelf',offset:{z:5,y:2},useRotation:true}");
        expect(saved(owner, "point"), 105, 22, 300);
    }

    @Test
    void positionSnapshotsCannotBeMutatedByMovingOrByReaders() {
        write("{$type:'ConfigBornBySelf'}");
        owner.pos.set(1, 2, 3);
        saved(owner, "point").set(7, 8, 9);
        expect(saved(owner, "point"), 100, 20, 300);
    }

    @Test
    void lifecycleRemovalClearsPositionsBeforeAnEntityIsReused() {
        write("{$type:'ConfigBornBySelf'}");
        assertNotNull(saved(owner, "point"));
        owner.invalidateAbilityLifecycle();
        owner.activateAbilityLifecycle();
        assertNull(saved(owner, "point"));
    }

    @Test
    void falsePredicateDoesNotReplaceASavedPosition() {
        write("{$type:'ConfigBornBySelf'}");
        owner.pos.set(1, 2, 3);
        var config =
                action(
                        "{$type:'SetGlobalPos',key:'point',born:{$type:'ConfigBornBySelf'},predicates:[{$type:'ByTargetGlobalValue',key:'ready',compareType:'Equal',value:1}]}");
        fixture.player
                .getAbilityManager()
                .executeActionNow(ability, config, ByteString.EMPTY, owner);
        expect(saved(owner, "point"), 100, 20, 300);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{$type:'ConfigBornBySelf',onGround:true,offset:{y:-5}}",
                "{$type:'ConfigBornByAttachPoint',attachPointName:'Head'}",
                "{$type:'NABPLNFPHCK'}",
                "{$type:'ConfigBornBySelf',bornRandom:{$type:'Unknown'}}",
                "{$type:'ConfigBornBySelf',offset:{x:'not-a-number'}}",
                "{$type:'ConfigBornBySelf',futureMode:true}"
            })
    void unprovenBirthRulesDoNotOverwriteKnownPositions(String born) {
        write("{$type:'ConfigBornBySelf'}");
        owner.pos.set(1, 2, 3);
        write(born);
        expect(saved(owner, "point"), 100, 20, 300);
    }

    @Test
    void selectedTargetReceivesItsOwnSnapshotWhenExplicitlyRequested() {
        var target = new LocatedEntity(fixture.scene);
        target.setId(44);
        target.pos.set(400, 50, 600);
        fixture.scene.getEntities().put(44, target);
        var config =
                action(
                        "{$type:'SetGlobalPos',key:'point',setTarget:true,target:'Target',born:{$type:'ConfigBornByTarget'}}");
        fixture.player
                .getAbilityManager()
                .executeActionNow(ability, config, ByteString.EMPTY, target);
        expect(saved(target, "point"), 400, 50, 600);
        assertNull(saved(owner, "point"));
    }

    @Test
    void positionKeysStayIsolatedBetweenEntities() {
        write("{$type:'ConfigBornBySelf'}");
        var other = new LocatedEntity(fixture.scene);
        other.setId(44);
        fixture.scene.getEntities().put(44, other);
        assertNull(saved(other, "point"));
    }

    @Test
    void gadgetReadsThePreviouslyWrittenPositionWhenPayloadHasNoVector() {
        write("{$type:'ConfigBornBySelf',offset:{x:4,y:2,z:6}}");
        var entity =
                gadget(
                        "{$type:'ConfigBornByGlobalValue',positionKey:'point',offset:{z:1}}",
                        ByteString.EMPTY);
        expect(entity.getPosition(), 104, 22, 307);
        assertSame(owner, entity.getOwner());
    }

    @Test
    void explicitClientOriginIsARealPositionAndTakesPriority() {
        write("{$type:'ConfigBornBySelf',offset:{x:4}}");
        var entity =
                gadget(
                        "{$type:'ConfigBornByGlobalValue',positionKey:'point'}",
                        AbilityActionCreateGadget.newBuilder()
                                .setPos(new Position().toProto())
                                .build()
                                .toByteString());
        expect(entity.getPosition(), 0, 0, 0);
    }

    @Test
    void gadgetWithoutBirthRuleKeepsTheExistingOwnerFallback() {
        var entity = gadget(null, ByteString.EMPTY);
        expect(entity.getPosition(), 100, 20, 300);
        expect(entity.getRotation(), 0, 90, 0);
    }

    @Test
    void absentGlobalKeyDoesNotSilentlyCreateAGadgetAtTheCaster() {
        assertFalse(
                new ActionCreateGadget()
                        .execute(
                                ability,
                                action(
                                        "{$type:'CreateGadget',gadgetID:999882,born:{$type:'ConfigBornByGlobalValue',positionKey:'missing'}}"),
                                ByteString.EMPTY,
                                owner));
        assertTrue(
                fixture.scene.getEntities().values().stream()
                        .noneMatch(e -> e instanceof EntityGadget));
    }

    @Test
    void nonFiniteClientVectorDoesNotCreateAnEntity() {
        var payload =
                AbilityActionCreateGadget.newBuilder()
                        .setPos(new Position(Float.NaN, 0, 0).toProto())
                        .build()
                        .toByteString();
        assertFalse(
                new ActionCreateGadget()
                        .execute(
                                ability,
                                action("{$type:'CreateGadget',gadgetID:999882}"),
                                payload,
                                owner));
        assertTrue(
                fixture.scene.getEntities().values().stream()
                        .noneMatch(e -> e instanceof EntityGadget));
    }

    @Test
    void emptyMonsterPayloadUsesTheSummonersPositionAndRotation() {
        var caster = monsterOwner();
        var entity = summon(caster, null, ByteString.EMPTY);
        expect(entity.getPosition(), 100, 20, 300);
        expect(entity.getRotation(), 0, 90, 0);
        assertEquals(caster.getId(), entity.getOwnerEntityId());
    }

    @Test
    void monsterSpawnReadsItsOwnSavedPosition() {
        var caster = monsterOwner();
        var source = new Ability(ability.getData(), caster, fixture.player);
        fixture.player
                .getAbilityManager()
                .executeActionNow(
                        source,
                        action(
                                "{$type:'SetGlobalPos',key:'point',born:{$type:'ConfigBornBySelf',offset:{x:7}}}"),
                        ByteString.EMPTY,
                        caster);
        expect(
                summon(
                                caster,
                                "{$type:'ConfigBornByGlobalValue',positionKey:'point'}",
                                ByteString.EMPTY)
                        .getPosition(),
                107,
                20,
                300);
    }

    @Test
    void monsterSpawnDoesNotDependOnAnArbitraryFirstPlayer() {
        var caster = monsterOwner();
        fixture.scene.getPlayers().clear();
        assertDoesNotThrow(() -> summon(caster, null, ByteString.EMPTY));
    }

    @Test
    void explicitlyReportedMonsterPositionAndRotationAreKept() {
        var caster = monsterOwner();
        var entity =
                summon(
                        caster,
                        null,
                        AbilityActionSummon.newBuilder()
                                .setPos(new Position(12, 34, 56).toProto())
                                .setRot(new Position(0, 180, 0).toProto())
                                .build()
                                .toByteString());
        expect(entity.getPosition(), 12, 34, 56);
        expect(entity.getRotation(), 0, 180, 0);
    }

    @Test
    void falseSummonPredicateDoesNotSpawnOrReplaceATag() {
        var caster = monsterOwner();
        var source = new Ability(ability.getData(), caster, fixture.player);
        var config =
                action(
                        "{$type:'Summon',monsterID:999881,summonTag:4,predicates:[{$type:'ByTargetGlobalValue',key:'ready',compareType:'Equal',value:1}]}");
        assertFalse(new ActionSummon().execute(source, config, ByteString.EMPTY, caster));
        assertFalse(caster.getSummonTagMap().containsKey(4));
    }

    @Test
    void realExportKeepsDefaultEnvelopeAndWritesTheRequestedTarget() {
        var config =
                action(
                        "{$type:'SetGlobalPos',key:'point',setTarget:true,target:'Target',born:{$type:'ConfigBornByTarget',APKLAIJMEIL:{HHNLJKNBKPP:180},value:10,direction:{maxAngle:180},onGroundRaycastUpDist:2}}");
        fixture.player
                .getAbilityManager()
                .executeActionNow(ability, config, ByteString.EMPTY, owner);
        expect(saved(owner, "point"), 100, 20, 300);
    }

    @Test
    void consecutivePositionAndSpawnInvocationsFinishBeforeTheNextCastMovesTheOwner() {
        var manager = fixture.player.getAbilityManager();
        manager.executeAction(
                ability,
                action("{$type:'SetGlobalPos',key:'point',born:{$type:'ConfigBornBySelf'}}"),
                ByteString.EMPTY,
                owner);
        manager.executeAction(
                ability,
                action(
                        "{$type:'CreateGadget',gadgetID:999882,born:{$type:'ConfigBornByGlobalValue',positionKey:'point'}}"),
                ByteString.EMPTY,
                owner);
        var created =
                fixture.scene.getEntities().values().stream()
                        .filter(e -> e instanceof EntityGadget)
                        .findFirst()
                        .orElseThrow();
        owner.pos.set(9, 8, 7);
        expect(created.getPosition(), 100, 20, 300);
    }

    @Test
    void differentSceneTargetCannotReceivePositionOrSpawn() throws Exception {
        try (var elsewhere = new AbilityTestFixture()) {
            var target = new LocatedEntity(elsewhere.scene);
            target.setId(45);
            elsewhere.scene.getEntities().put(45, target);
            fixture.player
                    .getAbilityManager()
                    .executeActionNow(
                            ability,
                            action(
                                    "{$type:'SetGlobalPos',key:'point',setTarget:true,born:{$type:'ConfigBornBySelf'}}"),
                            ByteString.EMPTY,
                            target);
            assertNull(saved(target, "point"));
            assertFalse(
                    new ActionCreateGadget()
                            .execute(
                                    ability,
                                    action("{$type:'CreateGadget',gadgetID:999882}"),
                                    ByteString.EMPTY,
                                    target));
        }
    }

    @Test
    void inactiveOwnerCannotWriteOrCreateAGadget() {
        owner.invalidateAbilityLifecycle();
        write("{$type:'ConfigBornBySelf'}");
        assertNull(saved(owner, "point"));
        assertFalse(
                new ActionCreateGadget()
                        .execute(
                                ability,
                                action("{$type:'CreateGadget',gadgetID:999882}"),
                                ByteString.EMPTY,
                                owner));
    }

    @Test
    void groundOrUnresolvedRuleCanStillUseAnExplicitClientPosition() {
        var payload =
                AbilityActionCreateGadget.newBuilder()
                        .setPos(new Position(1, 2, 3).toProto())
                        .build()
                        .toByteString();
        expect(gadget("{$type:'ConfigBornBySelf',onGround:true}", payload).getPosition(), 1, 2, 3);
    }

    @Test
    void falseGadgetPredicateDoesNotRemoveAnExistingSummon() {
        var existing = gadget(null, ByteString.EMPTY);
        var config =
                action(
                        "{$type:'CreateGadget',gadgetID:999882,predicates:[{$type:'ByTargetGlobalValue',key:'ready',value:1,compareType:'Equal'}]}");
        assertFalse(new ActionCreateGadget().execute(ability, config, ByteString.EMPTY, owner));
        assertSame(existing, fixture.scene.getEntities().get(existing.getId()));
    }

    @Test
    void unknownActionTargetIsNotSilentlyTreatedAsTheCaster() {
        fixture.player
                .getAbilityManager()
                .executeActionNow(
                        ability,
                        action(
                                "{$type:'SetGlobalPos',key:'point',target:'FutureTarget',born:{$type:'ConfigBornBySelf'}}"),
                        ByteString.EMPTY,
                        owner);
        assertNull(saved(owner, "point"));
    }

    @Test
    void actualBounceBallResourcePositionActionRoundTripsAndExecutes() throws Exception {
        var tree =
                JsonParser.parseString(
                        Files.readString(
                                Path.of(
                                        "resources/BinOutput/Ability/Temp/ActivityAbilities/ConfigAbility_Activity_V3_4.json")));
        var node =
                tree.getAsJsonArray()
                        .get(19)
                        .getAsJsonObject()
                        .getAsJsonObject("Default")
                        .getAsJsonArray("onFieldEnter")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("successActions")
                        .get(1)
                        .getAsJsonObject();
        assertEquals("Activity_GadgetAB_EnterPoint", node.get("key").getAsString());
        var config = JsonUtils.decode(node, AbilityModifierAction.class);
        assertEquals(node.get("born"), JsonUtils.toJson(config).getAsJsonObject().get("born"));
        fixture.player
                .getAbilityManager()
                .executeActionNow(ability, config, ByteString.EMPTY, owner);
        expect(saved(owner, config.key), 100, 20, 300);
    }

    @Test
    void callbackTreeAdmitsKnownPositionWritesAndRejectsUnknownBirthBeforePartialApplication()
            throws Exception {
        var type = Class.forName("emu.grasscutter.game.ability.AbilityActionSequence");
        var validate =
                type.getDeclaredMethod(
                        "callbacks", AbilityData.class, AbilityModifierAction[].class);
        validate.setAccessible(true);
        var known = action("{$type:'SetGlobalPos',key:'point',born:{$type:'ConfigBornBySelf'}}");
        var unknown =
                action(
                        "{$type:'SetGlobalPos',key:'point',born:{$type:'ConfigBornByAttachPoint',attachPointName:'Head'}}");
        var mark = action("{$type:'AddGlobalValue',key:'mark',value:1}");
        assertNotNull(
                validate.invoke(
                        null, ability.getData(), new AbilityModifierAction[] {mark, known}));
        assertNull(
                validate.invoke(
                        null, ability.getData(), new AbilityModifierAction[] {mark, unknown}));
    }

    @Test
    void implicitCrossEntityPositionDestinationStaysUnresolved() {
        var target = new LocatedEntity(fixture.scene);
        target.setId(44);
        target.pos.set(400, 50, 600);
        fixture.scene.getEntities().put(44, target);
        fixture.player
                .getAbilityManager()
                .executeActionNow(
                        ability,
                        action(
                                "{$type:'SetGlobalPos',key:'point',setTarget:false,target:'Target',born:{$type:'ConfigBornByTarget'}}"),
                        ByteString.EMPTY,
                        target);
        assertNull(saved(target, "point"));
        assertNull(saved(owner, "point"));
    }

    @Test
    void unresolvedPitchDoesNotPretendToBeYawOnly() {
        write("{$type:'ConfigBornBySelf'}");
        owner.rot.set(30, 90, 0);
        write("{$type:'ConfigBornBySelf',offset:{z:5},useRotation:true}");
        expect(saved(owner, "point"), 100, 20, 300);
    }
}
