package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.avatar.AvatarTalentData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Real C1 resource and talent through the native reaction callback and actual HP settlement. */
class LaumaHealingTest {
    private static final String ROOT = "Avatar_Lauma_Constellation_1";
    private static final String HANDLER = "UNIQUE_Avatar_Lauma_Constellation_1_AvatarHandler";
    private static final String HEAL = "UNIQUE_Avatar_Lauma_Constellation_1_HealHP";
    private AbilityTestFixture fixture;
    private EntityAvatar lauma, active;
    private Ability ability;
    private final AtomicLong clock = new AtomicLong();
    private final List<Runnable> restores = new ArrayList<>();

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var previous = map.put(key, value);
        restores.add(
                () -> {
                    if (previous == null) map.remove(key);
                    else map.put(key, previous);
                });
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        fixture.player.setUid(10001);
        var talent =
                JsonUtils.loadToList(
                                Path.of(
                                        "resources/ExcelBinOutput/AvatarTalentExcelConfigData.json"),
                                AvatarTalentData.class)
                        .stream()
                        .filter(row -> row.getId() == 1191)
                        .findFirst()
                        .orElseThrow();
        put(GameData.getAvatarTalentDataMap(), talent.getId(), talent);
        var config =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Talent/AvatarTalents/ConfigTalent_Lauma.json")))
                        .getAsJsonObject()
                        .get("Lauma_Constellation_1");
        put(
                GameData.getOpenConfigEntries(),
                "Lauma_Constellation_1",
                new OpenConfigEntry(
                        "Lauma_Constellation_1", JsonUtils.decode(config, OpenConfigData[].class)));
        lauma = avatar(fixture.player, 10000119, 0, 0, 0);
        lauma.getAvatar().getTalentIdList().add(1191);
        lauma.setFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 100);
        active = avatar(fixture.player, 10000006, 0, 0, 0);
        fixture.player.getTeamManager().setCurrentCharacterIndex(1);
        fixture.scene.getEntities().remove(lauma.getId());
        var data =
                JsonUtils.loadToList(
                                Path.of(
                                        "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Lauma.json"),
                                AbilityConfigData.class)
                        .stream()
                        .map(row -> row.Default)
                        .filter(row -> ROOT.equals(row.abilityName))
                        .findFirst()
                        .orElseThrow();
        put(GameData.getAbilityDataMap(), ROOT, data);
        ability = new Ability(data, lauma, fixture.player);
        lauma.getInstancedAbilities().add(ability);
        assertEquals(5f, ability.getAbilitySpecials().get("Constellation_1_Heal_Ratio"));
        assertEquals(1.9f, ability.getAbilitySpecials().get("Constellation_1_Heal_CD"));
        assertEquals(20f, ability.getAbilitySpecials().get("Constellation_1_Dura"));
        var duration = GameEntity.class.getDeclaredField("abilityModifierDuration");
        duration.setAccessible(true);
        duration.set(lauma, new AbilityModifierDuration(lauma, clock::get));
        var periodic = GameEntity.class.getDeclaredField("abilityPeriodicHealing");
        periodic.setAccessible(true);
        periodic.set(lauma, new AbilityPeriodicHealing(lauma, clock::get));
    }

    @AfterEach
    void close() throws Exception {
        drain();
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
        fixture.close();
    }

    private EntityAvatar avatar(Player owner, int avatarId, float x, float y, float z) {
        owner.setScene(fixture.scene);
        owner.setWorld(fixture.scene.getWorld());
        var position = new Position(x, y, z);
        var talents = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return owner;
                    }

                    @Override
                    public int getAvatarId() {
                        return avatarId;
                    }

                    @Override
                    public it.unimi.dsi.fastutil.ints.IntSet getTalentIdList() {
                        return talents;
                    }

                    @Override
                    public void save() {}
                };
        var entity =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public void initAbilities() {}

                    @Override
                    public Position getPosition() {
                        return position;
                    }
                };
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 5000);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        owner.getTeamManager().getActiveTeam().add(entity);
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private void reaction() throws Exception {
        var controller =
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get(HANDLER));
        active.getInstancedModifiers().put(71, controller);
        ability.registerModifier(active, controller);
        int localId =
                4 + (new ArrayList<>(ability.getData().modifiers.keySet()).indexOf(HANDLER) << 3);
        fixture.player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(active.getId())
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(71)
                                                .setLocalId(localId))
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_MIXIN_DO_ACTION_BY_ELEMENT_REACTION)
                                .build());
        drain();
    }

    private void drain() throws Exception {
        var barrier = new CyclicBarrier(4);
        var tasks = new ArrayList<Future<?>>();
        for (int i = 0; i < 4; i++)
            tasks.add(
                    AbilityManager.eventExecutor.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return null;
                            }));
        for (var task : tasks) task.get(6, TimeUnit.SECONDS);
    }

    private float hp(EntityAvatar entity) {
        return entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    @Test
    void c1_heals_the_active_lauma_including_the_selector_origin() throws Exception {
        fixture.player.getTeamManager().setCurrentCharacterIndex(0);
        fixture.scene.getEntities().put(lauma.getId(), lauma);
        fixture.scene.getEntities().remove(active.getId());
        active = lauma;
        reaction();
        assertEquals(600, hp(lauma));
    }

    @Test
    void the_heal_is_centered_on_the_current_avatar_after_switching_away_from_lauma()
            throws Exception {
        lauma.getPosition().setX(100);
        var nearby = avatar(new AbilityTestFixture.TestPlayer(), 10000015, 3, 0, 0);
        var nearOldCaster = avatar(new AbilityTestFixture.TestPlayer(), 10000016, 100, 0, 0);
        reaction();
        assertEquals(600, hp(active));
        assertEquals(600, hp(nearby));
        assertEquals(100, hp(nearOldCaster));
        assertEquals(100, hp(lauma));
    }

    @Test
    void repeated_reaction_callbacks_cannot_bypass_the_resource_interval() throws Exception {
        reaction();
        assertEquals(600, hp(active));
        var controller = ability.getModifiers().get(HEAL);
        reaction();
        assertEquals(600, hp(active));
        assertSame(controller, ability.getModifiers().get(HEAL));
        clock.set(1_899_999_000L);
        lauma.getAbilityModifierDuration().tick();
        reaction();
        assertEquals(600, hp(active));
        clock.set(1_900_001_000L);
        lauma.getAbilityModifierDuration().tick();
        reaction();
        assertEquals(1100, hp(active));
    }

    @Test
    void mastery_and_healing_bonuses_come_from_the_caster_and_each_recipient() throws Exception {
        lauma.setFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 120);
        active.setFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 999);
        lauma.setFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD, 0.25f);
        active.setFightProperty(FightProperty.FIGHT_PROP_HEALED_ADD, 0.1f);
        reaction();
        assertEquals(910, hp(active), 0.01);
    }

    @Test
    void the_actual_six_meter_shape_excludes_distant_high_and_dead_avatars() throws Exception {
        var edge = avatar(new AbilityTestFixture.TestPlayer(), 10000015, 6, 0, 0);
        var far = avatar(new AbilityTestFixture.TestPlayer(), 10000016, 6.01f, 0, 0);
        var high = avatar(new AbilityTestFixture.TestPlayer(), 10000020, 0, 6.01f, 0);
        var dead = avatar(new AbilityTestFixture.TestPlayer(), 10000021, 2, 0, 0);
        dead.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        reaction();
        assertEquals(600, hp(edge));
        assertEquals(100, hp(far));
        assertEquals(100, hp(high));
        assertEquals(0, hp(dead));
    }

    private void confirmHeal(boolean add, int id) throws Exception {
        var change =
                emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass
                        .AbilityMetaModifierChange.newBuilder()
                        .setAction(
                                add
                                        ? emu.grasscutter.net.proto.ModifierActionOuterClass
                                                .ModifierAction.MODIFIER_ACTION_ADDED
                                        : emu.grasscutter.net.proto.ModifierActionOuterClass
                                                .ModifierAction.MODIFIER_ACTION_REMOVED)
                        .setParentAbilityName(
                                emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString
                                        .newBuilder()
                                        .setStr(ROOT))
                        .setModifierLocalId(
                                new ArrayList<>(ability.getData().modifiers.keySet())
                                        .indexOf(HEAL));
        fixture.player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(lauma.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setTargetId(lauma.getId())
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(id))
                                .setAbilityData(change.build().toByteString())
                                .build());
        drain();
    }

    @Test
    void client_confirmation_and_native_healing_callback_do_not_repeat_the_server_heal()
            throws Exception {
        fixture.player.getTeamManager().setCurrentCharacterIndex(0);
        fixture.scene.getEntities().put(lauma.getId(), lauma);
        fixture.scene.getEntities().remove(active.getId());
        active = lauma;
        reaction();
        assertEquals(600, hp(lauma));
        var original = ability.getModifiers().get(HEAL);
        confirmHeal(true, 91);
        confirmHeal(true, 91);
        assertSame(original, lauma.getInstancedModifiers().get(91));
        var action = ability.getData().modifiers.get(HEAL).onAdded[2];
        int local =
                ability.getData().localIdToAction.entrySet().stream()
                        .filter(e -> e.getValue() == action)
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        fixture.player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(lauma.getId())
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(91)
                                                .setLocalId(local))
                                .build());
        drain();
        assertEquals(600, hp(lauma));
        clock.set(1_900_001_000L);
        lauma.getAbilityPeriodicHealing().tick();
        reaction();
        assertEquals(1100, hp(lauma));
        var next = ability.getModifiers().get(HEAL);
        assertNotSame(original, next);
        confirmHeal(false, 91);
        assertSame(next, ability.getModifiers().get(HEAL));
        assertEquals(1100, hp(lauma));
    }

    @Test
    void a_switch_during_the_interval_cannot_grant_an_extra_heal() throws Exception {
        reaction();
        assertEquals(600, hp(active));
        fixture.player.getTeamManager().setCurrentCharacterIndex(0);
        fixture.scene.getEntities().put(lauma.getId(), lauma);
        fixture.scene.getEntities().remove(active.getId());
        active = lauma;
        reaction();
        assertEquals(100, hp(lauma));
        clock.set(1_900_001_000L);
        lauma.getAbilityModifierDuration().tick();
        reaction();
        assertEquals(600, hp(lauma));
    }

    @Test
    void concurrent_reaction_callbacks_admit_one_heal() throws Exception {
        var action = ability.getData().modifiers.get(HANDLER).modifierMixins[0].actions[0];
        var pool = Executors.newFixedThreadPool(4);
        try {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 20; i++)
                tasks.add(
                        pool.submit(
                                () ->
                                        ability.getManager()
                                                .executeActionNow(
                                                        ability,
                                                        action,
                                                        com.google.protobuf.ByteString.EMPTY,
                                                        lauma)));
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
            assertEquals(600, hp(active));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void a_second_ability_instance_on_the_same_caster_cannot_restart_the_interval()
            throws Exception {
        reaction();
        assertEquals(600, hp(active));
        var repeated = new Ability(ability.getData(), lauma, fixture.player);
        lauma.getInstancedAbilities().add(repeated);
        var action = ability.getData().modifiers.get(HANDLER).modifierMixins[0].actions[0];
        repeated.getManager()
                .executeActionNow(repeated, action, com.google.protobuf.ByteString.EMPTY, lauma);
        assertEquals(600, hp(active));
    }

    @Test
    void the_same_resource_action_keeps_its_interval_in_a_periodic_callback() {
        var pulse = JsonUtils.decode("{thinkInterval:0.5,duration:10}", AbilityModifier.class);
        pulse.onThinkInterval = ability.getData().modifiers.get(HANDLER).modifierMixins[0].actions;
        ability.getData().modifiers.put("TestPulse", pulse);
        ability.registerModifier(
                lauma, new AbilityModifierController(ability, ability.getData(), pulse));
        clock.set(500_000_000L);
        lauma.getAbilityPeriodicHealing().tick();
        assertEquals(600, hp(active));
        clock.set(1_000_000_000L);
        lauma.getAbilityPeriodicHealing().tick();
        assertEquals(600, hp(active));
        clock.set(2_400_001_000L);
        lauma.getAbilityModifierDuration().tick();
        lauma.getAbilityPeriodicHealing().tick();
        assertEquals(1100, hp(active));
    }
}
