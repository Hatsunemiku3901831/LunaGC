package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Client-selected event callbacks share the existing scoped, ordered invocation pipeline. */
class AbilityEventMixinTest {
    AbilityTestFixture fixture;
    Ability ability;
    AbilityTestFixture.TestEntity target;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        target = new AbilityTestFixture.TestEntity(fixture.scene);
        target.setId(43);
        fixture.scene.addEntity(target);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    void install(String fields) {
        var data =
                JsonUtils.decode(
                        "{abilityName:'Event_Callback_Test',abilityMixins:[{$type:'DoActionByEventMixin',onEvent:'HPDown',type:'Client',"
                                + fields
                                + "}]}",
                        AbilityData.class);
        ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
    }

    void invoke(int targetId, int id) {
        fixture.player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(42)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(1)
                                                .setLocalId(id)
                                                .setTargetId(targetId))
                                .build());
    }

    void drain() throws Exception {
        var gate = new CyclicBarrier(4);
        var tasks = new ArrayList<Future<?>>();
        for (int i = 0; i < 4; i++)
            tasks.add(
                    AbilityManager.eventExecutor.submit(
                            () -> {
                                gate.await(5, TimeUnit.SECONDS);
                                return null;
                            }));
        for (var task : tasks) task.get(6, TimeUnit.SECONDS);
    }

    @Test
    void callbackRunsTheSelectedOrderedActions() throws Exception {
        install(
                "actions:[{$type:'SetGlobalValue',key:'event',value:1},{$type:'AddGlobalValue',key:'event',value:2}]");
        invoke(0, 2);
        drain();
        assertEquals(3f, fixture.entity.getGlobalAbilityValues().get("event"));
    }

    @Test
    void explicitTargetDoesNotRewriteTheOwnersState() throws Exception {
        install("actions:[{$type:'SetGlobalValue',key:'event',value:7}]");
        invoke(43, 2);
        drain();
        assertEquals(7f, target.getGlobalAbilityValues().get("event"));
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("event"));
    }

    @Test
    void primaryActionsTakePrecedenceOverTheFallbackQueue() throws Exception {
        install(
                "actions:[{$type:'SetGlobalValue',key:'primary',value:1}],actionQueue:[{$type:'SetGlobalValue',key:'fallback',value:1}]");
        invoke(0, 2);
        drain();
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("primary"));
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("fallback"));
    }

    @Test
    void emptyPrimaryQueueUsesItsConfiguredFallback() throws Exception {
        install("actions:[],actionQueue:[{$type:'SetGlobalValue',key:'fallback',value:1}]");
        invoke(0, 2);
        drain();
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("fallback"));
    }

    @Test
    void eventMetadataDoesNotOverrideTheMixinKind() throws Exception {
        install("actions:[],type:'Both'");
        var mixin = ability.getData().abilityMixins[0];
        assertEquals(AbilityMixinData.Type.DoActionByEventMixin, mixin.type);
        var json = JsonUtils.toJson(mixin).getAsJsonObject();
        assertEquals("HPDown", json.get("onEvent").getAsString());
        assertEquals("Both", json.get("type").getAsString());
    }

    @Test
    void knownHpPredicateRejectsTheCallbackAboveItsThreshold() throws Exception {
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        install(
                "predicates:[{$type:'ByTargetHPRatio',HPRatio:0.25,logic:'Lesser'}],actions:[{$type:'SetGlobalValue',key:'low',value:1}]");
        invoke(0, 2);
        drain();
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("low"));
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 20);
        invoke(0, 2);
        drain();
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("low"));
    }

    @Test
    void anUnknownLocalIdCannotSelectThisCallback() throws Exception {
        install("actions:[{$type:'SetGlobalValue',key:'wrong',value:1}]");
        invoke(0, 999);
        drain();
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("wrong"));
    }

    @Test
    void creatingTheAbilityDoesNotInventAnHpEvent() throws Exception {
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        install("actions:[{$type:'SetGlobalValue',key:'event',value:1}]");
        fixture.entity.damage(10);
        assertEquals(90, fixture.entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        drain();
        assertFalse(fixture.entity.getGlobalAbilityValues().containsKey("event"));
        invoke(0, 2);
        drain();
        assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("event"));
    }

    @Test
    void callbackCanUseTheExistingTriggerAbilityAction() throws Exception {
        var child =
                JsonUtils.decode(
                        "{abilityName:'Event_Child',onAbilityStart:[{$type:'SetGlobalValue',key:'triggered',value:1}]}",
                        AbilityData.class);
        var map = emu.grasscutter.data.GameData.getAbilityDataMap();
        var old = map.put("Event_Child", child);
        try {
            install("actions:[{$type:'TriggerAbility',abilityName:'Event_Child'}]");
            invoke(0, 2);
            drain();
            assertEquals(1f, fixture.entity.getGlobalAbilityValues().get("triggered"));
        } finally {
            if (old == null) map.remove("Event_Child");
            else map.put("Event_Child", old);
        }
    }

    @Test
    void actualSixthConstellationEventMetadataIsRetained() throws Exception {
        int total = 0;
        for (String name : List.of("Hutao", "Shinobu")) {
            var rows =
                    JsonParser.parseString(
                                    Files.readString(
                                            Path.of(
                                                    "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"
                                                            + name
                                                            + ".json")))
                            .getAsJsonArray();
            for (var row : rows) {
                var root = row.getAsJsonObject().getAsJsonObject("Default");
                if (!root.get("abilityName").getAsString().contains("Limbo")) continue;
                var modifiers = root.getAsJsonObject("modifiers");
                if (modifiers == null) continue;
                for (var value : modifiers.entrySet()) {
                    var mixins =
                            value.getValue().getAsJsonObject().getAsJsonArray("modifierMixins");
                    if (mixins == null) continue;
                    for (var mixin : mixins)
                        if ("DoActionByEventMixin"
                                .equals(mixin.getAsJsonObject().get("$type").getAsString())) {
                            var decoded = JsonUtils.decode(mixin, AbilityMixinData.class);
                            var json = JsonUtils.toJson(decoded).getAsJsonObject();
                            assertEquals("HPDown", json.get("onEvent").getAsString());
                            assertEquals("Both", json.get("type").getAsString());
                            assertNotNull(decoded.actions);
                            total++;
                        }
                }
            }
        }
        assertEquals(3, total);
    }
}
