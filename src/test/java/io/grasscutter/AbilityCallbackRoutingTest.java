package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.ActionApplyModifier;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

class AbilityCallbackRoutingTest {
    private AbilityTestFixture fixture;
    private EntityAvatar caster, selected;
    private EntityClientGadget summon;
    private final List<Runnable> restore = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        caster = avatar();
        selected = avatar();
        fixture.player.getTeamManager().getActiveTeam().add(caster);
        fixture.player.getTeamManager().getActiveTeam().add(selected);
        fixture.player.getTeamManager().setCurrentCharacterIndex(1);
        summon =
                new EntityClientGadget(
                        fixture.scene,
                        fixture.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(991)
                                .setOwnerEntityId(caster.getId())
                                .build());
        fixture.scene.getEntities().put(991, summon);
    }

    @AfterEach
    void close() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    private EntityAvatar avatar() {
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
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

    private AbilityData data(String fields) {
        return JsonUtils.decode(
                "{\"abilityName\":\"CallbackRouting\"" + fields + "}", AbilityData.class);
    }

    private Ability ability(String fields) {
        return new Ability(data(fields), summon, fixture.player);
    }

    private String write(String target) {
        return "{\"$type\":\"AddGlobalValue\",\"key\":\"seen\",\"value\":1,\"target\":\""
                + target
                + "\"}";
    }

    private float value(GameEntity entity) {
        return entity.getGlobalAbilityValues().getOrDefault("seen", 0f);
    }

    private void register(AbilityData data) {
        var previous = GameData.getAbilityDataMap().put(data.abilityName, data);
        restore.add(
                () -> {
                    if (previous == null) GameData.getAbilityDataMap().remove(data.abilityName);
                    else GameData.getAbilityDataMap().put(data.abilityName, previous);
                });
    }

    private void trigger(AbilityData data) {
        register(data);
        assertTrue(fixture.player.getAbilityManager().triggerAbility(summon, data.abilityName));
    }

    private void run(Ability ability, AbilityModifierAction[] actions, GameEntity self) {
        assertTrue(
                ability.getManager()
                        .executeCallbackActionsNow(ability, actions, ByteString.EMPTY, self, self));
    }

    private static class Workers implements AutoCloseable {
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Future<?>> blockers = new ArrayList<>();

        Workers() throws Exception {
            var started = new CountDownLatch(4);
            for (int i = 0; i < 4; i++)
                blockers.add(
                        AbilityManager.eventExecutor.submit(
                                () -> {
                                    started.countDown();
                                    try {
                                        assertTrue(release.await(5, TimeUnit.SECONDS));
                                    } catch (InterruptedException ex) {
                                        throw new AssertionError(ex);
                                    }
                                }));
            assertTrue(started.await(3, TimeUnit.SECONDS));
        }

        @Override
        public void close() throws Exception {
            var tasks =
                    new ArrayList<>(((ThreadPoolExecutor) AbilityManager.eventExecutor).getQueue());
            release.countDown();
            for (var task : tasks) ((Future<?>) task).get(3, TimeUnit.SECONDS);
            for (var task : blockers) task.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void asynchronousInitializationRunsRootBeforeDefaultOnItsCaster() throws Exception {
        var data =
                data(
                        ",\"onAdded\":[{\"$type\":\"SetGlobalValue\",\"key\":\"seen\",\"value\":10,\"target\":\"CasterOriginOwner\"}],\"modifiers\":{\"Default\":{\"onAdded\":["
                                + write("CasterOriginOwner")
                                + "]}}");
        try (var workers = new Workers()) {
            fixture.player.getAbilityManager().addAbilityToEntity(summon, data);
        }
        assertEquals(11, value(caster));
        assertEquals(0, value(summon));
    }

    @Test
    void asynchronousInitializationRetainsServerOwnershipAndHealingTag() throws Exception {
        var data =
                data(
                        ",\"onAdded\":[{\"$type\":\"Predicated\",\"target\":\"CasterOriginOwner\",\"targetPredicates\":[{\"$type\":\"ONJEHDDFFCH\",\"LAIFKEGOFJO\":[\"Impale\"]}],\"successActions\":[{\"$type\":\"SetGlobalValue\",\"key\":\"seen\",\"value\":1}]}]");
        fixture.player.packets.clear();
        try (var workers = new Workers()) {
            AbilityHealContext.run(
                    "Impale",
                    () ->
                            AbilityManager.runServerOwned(
                                    () ->
                                            fixture.player
                                                    .getAbilityManager()
                                                    .addAbilityToEntity(summon, data)));
        }
        assertEquals(1, value(caster));
        assertEquals(0, value(summon));
        assertTrue(fixture.player.packets.isEmpty());
        assertNull(AbilityHealContext.currentTag());
    }

    @Test
    void asynchronousInitializationStopsAfterItsOwnerIsInvalidated() throws Exception {
        var data = data(",\"onAdded\":[" + write("CasterOriginOwner") + "]");
        try (var workers = new Workers()) {
            fixture.player.getAbilityManager().addAbilityToEntity(summon, data);
            summon.invalidateAbilityLifecycle();
        }
        assertEquals(0, value(caster));
        assertEquals(0, value(summon));
    }

    @ParameterizedTest
    @ValueSource(strings = {"onAdded", "onAbilityStart"})
    void triggeredRootCallbacksUseTheDeclaredCaster(String phase) {
        trigger(data(",\"" + phase + "\":[" + write("CasterOriginOwner") + "]"));
        assertEquals(1, value(caster));
        assertEquals(0, value(summon));
        assertEquals(0, value(selected));
    }

    @Test
    void triggeredDefaultModifierCallbacksUseTheDeclaredCaster() {
        trigger(
                data(
                        ",\"modifiers\":{\"Default\":{\"onAdded\":["
                                + write("CasterOriginOwner")
                                + "]}}"));
        assertEquals(1, value(caster));
        assertEquals(0, value(summon));
    }

    @Test
    void actualFischlRootCallbacksSetTheCrowFlagsOnTheCaster() throws Exception {
        var rows =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Fischl.json")))
                        .getAsJsonArray()
                        .get(37)
                        .getAsJsonObject()
                        .getAsJsonObject("Default");
        var added = rows.getAsJsonArray("onAdded");
        var data = data(",\"onAdded\":[" + added.get(4) + "," + added.get(5) + "]");
        trigger(data);
        assertEquals(1f, caster.getGlobalAbilityValues().get("_Fischl_Crow_Flag"));
        assertEquals(1f, caster.getGlobalAbilityValues().get("_Fischl_Crow_Flag_Hexenzirkel"));
        assertFalse(summon.getGlobalAbilityValues().containsKey("_Fischl_Crow_Flag"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CasterOriginOwner", "CurLocalAvatar", "Self"})
    void queuedHealingCallbacksChooseTheirRecipientAndRetainTheHealTag(String target)
            throws Exception {
        var ability = ability("");
        var row =
                JsonUtils.decode(
                        "{\"$type\":\"Predicated\",\"target\":\""
                                + target
                                + "\",\"targetPredicates\":[{\"$type\":\"ONJEHDDFFCH\",\"LAIFKEGOFJO\":[\"Impale\"]}],\"successActions\":["
                                + write("Self")
                                + "]}",
                        AbilityModifierAction.class);
        try (var workers = new Workers()) {
            ability.getManager()
                    .executeHealingCallbacks(
                            ability, new AbilityModifierAction[] {row}, summon, "Impale");
        }
        assertEquals("CasterOriginOwner".equals(target) ? 1 : 0, value(caster));
        assertEquals("CurLocalAvatar".equals(target) ? 1 : 0, value(selected));
        assertEquals("Self".equals(target) ? 1 : 0, value(summon));
        assertNull(AbilityHealContext.currentTag());
    }

    @Test
    void anInvalidatedQueuedHealingCallbackCannotReachTheCaster() throws Exception {
        var ability = ability("");
        var row = JsonUtils.decode(write("CasterOriginOwner"), AbilityModifierAction.class);
        try (var workers = new Workers()) {
            ability.getManager()
                    .executeHealingCallbacks(
                            ability, new AbilityModifierAction[] {row}, summon, null);
            summon.invalidateAbilityLifecycle();
        }
        assertEquals(0, value(caster));
        assertEquals(0, value(summon));
    }

    @Test
    void appliedModifierCallbacksResolveTheirOwnTargets() {
        var ability =
                ability(
                        ",\"modifiers\":{\"Child\":{\"onAdded\":["
                                + write("CasterOriginOwner")
                                + "]}}");
        var row =
                JsonUtils.decode(
                        "{\"$type\":\"ApplyModifier\",\"modifierName\":\"Child\"}",
                        AbilityModifierAction.class);
        assertTrue(new ActionApplyModifier().execute(ability, row, ByteString.EMPTY, summon));
        assertEquals(1, value(caster));
        assertEquals(0, value(summon));
    }

    @Test
    void globalWatcherChildCallbacksResolveTheCasterAndDoNotRepeat() {
        var ability =
                ability(
                        ",\"modifiers\":{\"Low\":{\"onAdded\":["
                                + write("CasterOriginOwner")
                                + "]},\"Listener\":{\"modifierMixins\":[{\"$type\":\"AttachModifierToGlobalValueMixin\",\"globalValueKey\":\"mode\",\"modifierNameSteps\":[\"Low\"],\"valueSteps\":[0,1]}]}}");
        ability.registerModifier(
                summon,
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Listener")));
        for (int i = 0; i < 4; i++) summon.onAbilityValueUpdate();
        assertEquals(1, value(caster));
        assertEquals(0, value(summon));
        ability.removeModifier(summon, "Listener");
        summon.onAbilityValueUpdate();
        assertEquals(1, value(caster));
    }

    private AbilityData allPartyData() {
        return data(
                ",\"onAdded\":[{\"$type\":\"AttachModifier\",\"target\":\"AllPlayerAvatars\",\"modifierName\":\"Child\"}],\"modifiers\":{\"Child\":{\"onAdded\":["
                        + write("Self")
                        + "]}}");
    }

    @Test
    void allPartyAttachInAResolvedCallbackAppliesOnceToEachDistinctRecipient() {
        var data = allPartyData();
        var ability = new Ability(data, summon, fixture.player);
        fixture.player.getTeamManager().getActiveTeam().add(caster);
        run(ability, data.onAdded, summon);
        assertEquals(1, value(caster));
        assertEquals(1, value(selected));
        assertEquals(0, value(summon));
    }

    @Test
    void triggeredAllPartyAttachKeepsItsExistingSingleFanOut() {
        trigger(allPartyData());
        assertEquals(1, value(caster));
        assertEquals(1, value(selected));
        assertEquals(0, value(summon));
    }

    @Test
    void nativeActionKeepsTheRecipientAlreadyResolvedByTheClient() throws Exception {
        var ability = ability(",\"onAdded\":[" + write("Self") + "]");
        summon.getInstancedAbilities().add(ability);
        int local =
                ability.getData().localIdToAction.entrySet().stream()
                        .filter(e -> e.getValue() == ability.getData().onAdded[0])
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        try (var workers = new Workers()) {
            ability.getManager()
                    .handleServerInvoke(
                            AbilityInvokeEntry.newBuilder()
                                    .setEntityId(summon.getId())
                                    .setHead(
                                            AbilityInvokeEntryHead.newBuilder()
                                                    .setTargetId(selected.getId())
                                                    .setInstancedAbilityId(1)
                                                    .setLocalId(local))
                                    .build());
        }
        assertEquals(1, value(selected));
        assertEquals(0, value(summon));
        assertEquals(0, value(caster));
    }

    @Test
    void modifierInitializationFromClientConfirmationRoutesItsChildCallback() throws Exception {
        var ability =
                ability(
                        ",\"modifiers\":{\"Listener\":{\"onAdded\":[{\"$type\":\"SetGlobalValue\",\"key\":\"seen\",\"value\":1,\"target\":\"CasterOriginOwner\"},{\"$type\":\"AttachModifier\",\"modifierName\":\"Child\",\"target\":\"Self\"}]},\"Child\":{}}");
        summon.getInstancedAbilities().add(ability);
        int local = new ArrayList<>(ability.getData().modifiers.keySet()).indexOf("Listener");
        var change =
                AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierAction.MODIFIER_ACTION_ADDED)
                        .setParentAbilityName(
                                AbilityString.newBuilder().setStr(ability.getData().abilityName))
                        .setModifierLocalId(local);
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
        assertEquals(1, value(caster));
        assertEquals(0, value(summon));
    }
}
