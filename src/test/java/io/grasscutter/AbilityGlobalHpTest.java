package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ServerGlobalValueChangeNotifyOuterClass.ServerGlobalValueChangeNotify;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;
import java.nio.charset.StandardCharsets;
import javax.script.SimpleBindings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AbilityGlobalHpTest {
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestEntity target;

    @BeforeAll static void scripts() throws Exception { if (ScriptLoader.getEngine()==null) ScriptLoader.init(); }
    @BeforeEach void setup() throws Exception {
        fixture=new AbilityTestFixture();
        target=new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43); target.setConfigId(77); target.setGroupId(88);
        fixture.scene.getEntities().put(43,target);
        stats(target); stats(fixture.entity);
    }
    @AfterEach void close() {
        ScriptLoader.getScriptLib().removeCurrentGroup();
        ScriptLoader.getScriptLib().removeSceneScriptManager();
        fixture.scene.getScriptManager().onDestroy();
        fixture.close();
    }
    private void stats(AbilityTestFixture.TestEntity entity) {
        entity.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP,100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_HP,20);
        entity.setFightProperty(FightProperty.FIGHT_PROP_HP_PERCENT,0.5f);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP,170);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP,85);
    }
    private float max(AbilityTestFixture.TestEntity entity) { return entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP); }
    private Ability ability(String expression) {
        var data=JsonUtils.decode("{\"abilityName\":\"Global_Hp\",\"modifiers\":{\"Buff\":{\"properties\":{\"Actor_MaxHPRatio\":"+expression+"}}}}",AbilityData.class);
        var ability=new Ability(data,fixture.entity,fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
        return ability;
    }
    private void apply(Ability ability,AbilityTestFixture.TestEntity entity) {
        ability.registerModifier(entity,new AbilityModifierController(ability,ability.getData(),ability.getData().modifiers.get("Buff")));
    }
    private AbilityModifier.AbilityModifierAction action(String json) { return JsonUtils.decode(json,AbilityModifier.AbilityModifierAction.class); }
    private void invoke(String key,float value,AbilityInvokeArgument type) throws Exception {
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder().setEntityId(43).setArgumentType(type)
                .setAbilityData(AbilityScalarValueEntry.newBuilder().setKey(AbilityString.newBuilder().setStr(key)).setFloatValue(value).build().toByteString()).build());
    }
    private void global(String key,float value) throws Exception { invoke(key,value,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE); }
    private void clear(String key) throws Exception { invoke(key,0,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_CLEAR_GLOBAL_FLOAT_VALUE); }
    private void hashed(String key,float value,AbilityInvokeArgument type) throws Exception {
        fixture.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder().setEntityId(43).setArgumentType(type)
                .setAbilityData(AbilityScalarValueEntry.newBuilder().setKey(AbilityString.newBuilder().setHash(Utils.abilityHash(key))).setFloatValue(value).build().toByteString()).build());
    }
    private int lua(String expression) throws Exception {
        var lib=ScriptLoader.getScriptLib();
        lib.setSceneScriptManager(fixture.scene.getScriptManager()); lib.setCurrentGroup(SceneGroup.of(88));
        var bindings=new SimpleBindings(); bindings.put("ScriptLib",ScriptLoader.getScriptLibLua());
        return ((Number)ScriptLoader.getEngine().eval("return "+expression,bindings)).intValue();
    }

    @Test void aModifierReadsItsBearerGlobalsAndKeepsAbilityConstantsAheadOfThem() {
        fixture.entity.getGlobalAbilityValues().put("GV_ratio",0.8f);
        target.getGlobalAbilityValues().put("GV_ratio",0.2f);
        var ability=ability("\"GV_ratio\""); apply(ability,target);
        assertEquals(190,max(target),0.0001f);
        ability.getAbilitySpecials().put("GV_ratio",0.1f); ability.refreshModifierTargets(true);
        assertEquals(180,max(target),0.0001f);
        target.getGlobalAbilityValues().put("GV_ratio",0.4f); target.onAbilityValueUpdate();
        assertEquals(180,max(target),0.0001f);
        ability.getAbilitySpecials().removeFloat("GV_ratio"); ability.refreshModifierTargets(true);
        assertEquals(210,max(target),0.0001f);
    }
    @Test void postfixExpressionsCanMixGlobalValuesWithAbilityConstants() {
        var ability=ability("[\"GV_layers\",\"perLayer\",\"MUL\"]");
        ability.getAbilitySpecials().put("perLayer",0.1f);
        target.getGlobalAbilityValues().put("GV_layers",2f); apply(ability,target);
        assertEquals(190,max(target),0.0001f);
        target.getGlobalAbilityValues().put("GV_layers",3f); target.onAbilityValueUpdate();
        assertEquals(200,max(target),0.0001f);
    }
    @Test void clientGlobalUpdatesAndClearsRefreshTheAttachedHpLayer() throws Exception {
        var ability=ability("\"GV_ratio\""); apply(ability,target);
        global("GV_ratio",0.2f); assertEquals(190,max(target),0.0001f);
        global("GV_ratio",0.4f); assertEquals(210,max(target),0.0001f);
        clear("GV_ratio"); assertEquals(170,max(target),0.0001f);
        assertEquals(85,target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP),0.0001f);
    }
    @Test void aClientCannotOverwriteOrClearAServerGlobalValue() throws Exception {
        target.getGlobalAbilityValues().put("SGV_ratio",0.2f);
        global("SGV_ratio",0.8f); assertEquals(0.2f,target.getGlobalAbilityValues().get("SGV_ratio"));
        clear("SGV_ratio"); assertEquals(0.2f,target.getGlobalAbilityValues().get("SGV_ratio"));
    }
    @Test void nonFiniteClientValuesDoNotReplaceAnExistingValidValue() throws Exception {
        target.getGlobalAbilityValues().put("GV_ratio",0.2f);
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
            global("GV_ratio",invalid); assertEquals(0.2f,target.getGlobalAbilityValues().get("GV_ratio"));
        }
    }
    @Test void hashedGlobalKeysCanResolveAnExistingEntityValue() throws Exception {
        target.getGlobalAbilityValues().put("GV_cached",0.1f);
        hashed("GV_cached",0.4f,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE);
        assertEquals(0.4f,target.getGlobalAbilityValues().get("GV_cached"));
        hashed("GV_cached",0,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_CLEAR_GLOBAL_FLOAT_VALUE);
        assertFalse(target.getGlobalAbilityValues().containsKey("GV_cached"));
    }
    @Test void hashedGlobalKeysCanResolveTheFirstValueFromAnAttachedHpExpression() throws Exception {
        var ability=ability("\"GV_first\""); apply(ability,target);
        assertFalse(target.getGlobalAbilityValues().containsKey("GV_first"));
        hashed("GV_first",0.3f,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE);
        assertEquals(200,max(target),0.0001f);
        hashed("GV_first",0,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_CLEAR_GLOBAL_FLOAT_VALUE);
        assertEquals(170,max(target),0.0001f);
    }
    @Test void hashedServerGlobalsRetainTheSameClientWriteAndClearProtection() throws Exception {
        target.getGlobalAbilityValues().put("SGV_ratio",0.2f);
        hashed("SGV_ratio",0.8f,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE);
        hashed("SGV_ratio",0,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_CLEAR_GLOBAL_FLOAT_VALUE);
        assertEquals(0.2f,target.getGlobalAbilityValues().get("SGV_ratio"));
    }
    @Test void ambiguousHashedGlobalNamesLeaveBothValuesUntouched() throws Exception {
        String first="GV_A\u0100", second="GV_B}";
        assertNotEquals(first,second);
        assertEquals(Utils.abilityHash(first),Utils.abilityHash(second));
        target.getGlobalAbilityValues().put(first,0.2f);
        target.getGlobalAbilityValues().put(second,0.4f);
        hashed(first,0.8f,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE);
        hashed(first,0,AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_CLEAR_GLOBAL_FLOAT_VALUE);
        assertEquals(0.2f,target.getGlobalAbilityValues().get(first));
        assertEquals(0.4f,target.getGlobalAbilityValues().get(second));
    }
    @Test void serverSetAddAndClearActionsRefreshTheHpLayer() {
        var ability=ability("\"SGV_ratio\""); apply(ability,target);
        assertTrue(new ActionSetGlobalValue().execute(ability,action("{\"$type\":\"SetGlobalValueV2\",\"key\":\"SGV_ratio\",\"value\":0.2}"),ByteString.EMPTY,target));
        assertEquals(190,max(target),0.0001f);
        assertTrue(new ActionAddGlobalValue().execute(ability,action("{\"$type\":\"AddGlobalValue\",\"key\":\"SGV_ratio\",\"value\":0.1}"),ByteString.EMPTY,target));
        assertEquals(200,max(target),0.0001f);
        assertTrue(new ActionClearGlobalValue().execute(ability,action("{\"$type\":\"ClearGlobalValue\",\"key\":\"SGV_ratio\"}"),ByteString.EMPTY,target));
        assertEquals(170,max(target),0.0001f);
    }
    @Test void copyingAGlobalValueNotifiesAndRefreshesItsActualDestination() throws Exception {
        var ability=ability("\"GV_ratio\""); apply(ability,fixture.entity);
        target.getGlobalAbilityValues().put("incoming",0.25f);
        var action=action("{\"$type\":\"CopyGlobalValue\",\"srcKey\":\"incoming\",\"srcTarget\":\"Self\",\"dstKey\":\"GV_ratio\",\"dstTarget\":\"Caster\"}");
        fixture.player.packets.clear();
        assertTrue(new ActionCopyGlobalValue().execute(ability,action,ByteString.EMPTY,target));
        assertEquals(195,max(fixture.entity),0.0001f); assertEquals(170,max(target));
        var packets=fixture.player.packets.stream().filter(p->p.getOpcode()==PacketOpcodes.ServerGlobalValueChangeNotify).toList();
        assertEquals(1,packets.size());
        assertEquals(42,ServerGlobalValueChangeNotify.parseFrom(packets.get(0).getData()).getEntityId());
    }
    @Test void copyingInAServerOwnedChainKeepsGlobalValueBookkeepingLocal() {
        var ability=ability("\"GV_ratio\""); apply(ability,target);
        fixture.entity.getGlobalAbilityValues().put("incoming",0.25f); fixture.player.packets.clear();
        var action=action("{\"$type\":\"CopyGlobalValue\",\"srcKey\":\"incoming\",\"srcTarget\":\"Caster\",\"dstKey\":\"GV_ratio\",\"dstTarget\":\"Self\"}");
        AbilityManager.runServerOwned(()->assertTrue(new ActionCopyGlobalValue().execute(ability,action,ByteString.EMPTY,target)));
        assertEquals(195,max(target),0.0001f);
        assertTrue(fixture.player.packets.stream().noneMatch(p->p.getOpcode()==PacketOpcodes.ServerGlobalValueChangeNotify));
    }
    @Test void globalValueToOverrideMapRefreshesAnAbilitysSeparateTarget() {
        var ability=ability("\"amount\""); ability.getAbilitySpecials().put("amount",0.1f); apply(ability,target);
        fixture.entity.getGlobalAbilityValues().put("incoming",0.4f);
        assertTrue(new ActionSetGlobalValueToOverrideMap().execute(ability,action("{\"$type\":\"SetGlobalValueToOverrideMap\",\"globalValueKey\":\"incoming\",\"overrideMapKey\":\"amount\"}"),ByteString.EMPTY,fixture.entity));
        assertEquals(210,max(target),0.0001f);
        assertEquals(170,max(fixture.entity));
    }
    @Test void theLuaEntityIdSetterStoresFractionalValuesAndRefreshesHp() throws Exception {
        var ability=ability("\"SGV_ratio\""); apply(ability,target); fixture.scene.finishLoading();
        assertEquals(0,lua("ScriptLib.SetEntityServerGlobalValueByEntityId(ScriptLib,43,'SGV_ratio',0.25)"));
        assertEquals(0.25f,target.getGlobalAbilityValues().get("SGV_ratio"));
        assertEquals(195,max(target),0.0001f);
        var packet=fixture.scene.packets.stream().filter(p->p.getOpcode()==PacketOpcodes.ServerGlobalValueChangeNotify).findFirst().orElseThrow();
        assertEquals(0.25f,ServerGlobalValueChangeNotify.parseFrom(packet.getData()).getValue());
    }
    @Test void theLuaConfigIdSetterStoresItsValueWithinTheCurrentGroup() throws Exception {
        var ability=ability("\"SGV_ratio\""); apply(ability,target);
        assertEquals(0,lua("ScriptLib.SetEntityServerGlobalValueByConfigId(ScriptLib,77,'SGV_ratio',0.375)"));
        assertEquals(0.375f,target.getGlobalAbilityValues().get("SGV_ratio"));
        assertEquals(207.5f,max(target),0.0001f);
    }
    @Test void unknownLuaTargetsFailWithoutSendingAnOrphanGlobalValue() throws Exception {
        fixture.scene.finishLoading(); fixture.scene.packets.clear();
        assertEquals(2,lua("ScriptLib.SetEntityServerGlobalValueByEntityId(ScriptLib,9999,'SGV_ratio',1)"));
        assertEquals(2,lua("ScriptLib.SetEntityServerGlobalValueByConfigId(ScriptLib,9999,'SGV_ratio',1)"));
        assertTrue(fixture.scene.packets.isEmpty());
    }
    @Test void aDeferredLuaNotificationDoesNotReachARemovedEntity() throws Exception {
        assertEquals(0,lua("ScriptLib.SetEntityServerGlobalValueByEntityId(ScriptLib,43,'SGV_ratio',1)"));
        assertEquals(1f,target.getGlobalAbilityValues().get("SGV_ratio"));
        fixture.scene.getEntities().remove(43); target.invalidateAbilityLifecycle();
        fixture.scene.playerSceneInitialized(fixture.player);
        assertTrue(fixture.scene.packets.stream().noneMatch(p->p.getOpcode()==PacketOpcodes.ServerGlobalValueChangeNotify));
    }
    @Test void readdingTheSameEntityInvalidatesItsOlderDeferredLuaNotification() throws Exception {
        assertEquals(0,lua("ScriptLib.SetEntityServerGlobalValueByEntityId(ScriptLib,43,'SGV_ratio',0.2)"));
        target.invalidateAbilityLifecycle(); target.activateAbilityLifecycle();
        assertEquals(0,lua("ScriptLib.SetEntityServerGlobalValueByEntityId(ScriptLib,43,'SGV_ratio',0.4)"));
        fixture.scene.playerSceneInitialized(fixture.player);
        var packets=fixture.scene.packets.stream().filter(p->p.getOpcode()==PacketOpcodes.ServerGlobalValueChangeNotify).toList();
        assertEquals(1,packets.size());
        assertEquals(0.4f,ServerGlobalValueChangeNotify.parseFrom(packets.get(0).getData()).getValue());
    }
    @Test void luaSettersRejectInvalidNamesAndValuesBeforeChangingState() throws Exception {
        fixture.scene.finishLoading();
        for(String expression:new String[]{
                "ScriptLib.SetEntityServerGlobalValueByEntityId(ScriptLib,43,'',1)",
                "ScriptLib.SetEntityServerGlobalValueByEntityId(ScriptLib,43,'SGV_ratio',math.huge)",
                "ScriptLib.SetEntityServerGlobalValueByConfigId(ScriptLib,77,'SGV_ratio',0/0)"}) {
            assertEquals(1,lua(expression));
        }
        assertTrue(target.getGlobalAbilityValues().isEmpty());
        assertTrue(fixture.scene.packets.isEmpty());
    }
    @Test void luaSetterJavaEntriesRejectMissingSceneOrGroupContext() {
        var lib=ScriptLoader.getScriptLib();
        assertEquals(1,lib.SetEntityServerGlobalValueByEntityId(43,"SGV_ratio",1));
        assertEquals(1,lib.SetEntityServerGlobalValueByConfigId(77,"SGV_ratio",1));
        lib.setSceneScriptManager(fixture.scene.getScriptManager());
        assertEquals(1,lib.SetEntityServerGlobalValueByConfigId(77,"SGV_ratio",1));
        assertTrue(target.getGlobalAbilityValues().isEmpty());
    }

    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5})
    void everyMissingResourceKeyCanBindToItsBearer(int index) throws Exception {
        try(var dataStream=getClass().getResourceAsStream("/ability/global-hp-modifiers.json");
            var sourceStream=getClass().getResourceAsStream("/ability/global-hp-sources.json")) {
            assertNotNull(dataStream); assertNotNull(sourceStream);
            var rows=JsonUtils.decode(new String(dataStream.readAllBytes(),StandardCharsets.UTF_8),AbilityData[].class);
            var sources=com.google.gson.JsonParser.parseString(new String(sourceStream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray();
            assertEquals(6,rows.length);
            var ability=new Ability(rows[index],fixture.entity,fixture.player);
            String name=rows[index].modifiers.keySet().iterator().next();
            String key=sources.get(index).getAsJsonObject().get("globalKey").getAsString();
            fixture.entity.getGlobalAbilityValues().put(key,0.9f); target.getGlobalAbilityValues().put(key,0.2f);
            ability.registerModifier(target,new AbilityModifierController(ability,rows[index],rows[index].modifiers.get(name)));
            assertEquals(190,max(target),0.0001f,rows[index].abilityName);
            target.getGlobalAbilityValues().put(key,0.3f); target.onAbilityValueUpdate();
            assertEquals(200,max(target),0.0001f,rows[index].abilityName);
            ability.removeModifier(target,name); assertEquals(170,max(target),0.0001f);
        }
    }
}
