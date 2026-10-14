package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.server.event.entity.EntityDamageEvent;

import org.junit.jupiter.api.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

class DamageLogTest {
    NatsaurusPossessionTest environment;
    Object log;
    AtomicLong clock = new AtomicLong(1000);
    AbilityTestFixture.TestEntity victim;

    Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            return target.getClass().getMethod(name, types).invoke(target, args);
        } catch (InvocationTargetException e) {
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    Object newLog(World world) {
        try {
            return Class.forName("emu.grasscutter.game.combat.DamageLog")
                    .getConstructor(World.class, LongSupplier.class)
                    .newInstance(world, (LongSupplier) clock::get);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new NatsaurusPossessionTest();
        environment.setup();
        var scene = environment.fixture.scene;
        scene.getEntities().put(environment.avatar.getId(), environment.avatar);
        victim =
                new AbilityTestFixture.TestEntity(scene) {
                    @Override
                    public void runLuaCallbacks(EntityDamageEvent event) {}
                };
        victim.setId(43);
        scene.getEntities().put(victim.getId(), victim);
        victim.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        victim.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        log = newLog(scene.getWorld());
    }

    @AfterEach
    void close() {
        if (environment != null) environment.close();
    }

    JsonObject snapshot(Object value) {
        return (JsonObject) invoke(value, "snapshot", new Class<?>[] {});
    }

    JsonObject snapshot() {
        return snapshot(log);
    }

    void record(Object value, GameEntity target, int attacker, float damage) {
        invoke(
                value,
                "record",
                new Class<?>[] {GameEntity.class, int.class, float.class, ElementType.class},
                target,
                attacker,
                damage,
                ElementType.Fire);
    }

    void hit(float damage) {
        record(log, victim, environment.avatar.getId(), damage);
    }

    void track(EntityClientGadget gadget) {
        invoke(log, "trackGadget", new Class<?>[] {EntityClientGadget.class}, gadget);
    }

    EntityClientGadget gadget(int id, int owner) {
        var g =
                new EntityClientGadget(
                        environment.fixture.scene,
                        environment.fixture.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(id)
                                .setOwnerEntityId(owner)
                                .build());
        environment.fixture.scene.getEntities().put(id, g);
        return g;
    }

    JsonObject row() {
        return snapshot().getAsJsonArray("rows").get(0).getAsJsonObject();
    }

    Object worldLog() {
        return invoke(environment.fixture.scene.getWorld(), "getDamageLog", new Class<?>[] {});
    }

    @Test
    void acceptedDamageHasPlayerAndAvatarAttribution() {
        hit(25);
        assertEquals(25, snapshot().get("totalDamage").getAsDouble());
        assertEquals(10001, row().get("uid").getAsInt());
        assertEquals(1, row().get("hits").getAsInt());
    }

    @Test
    void invalidAndZeroDamageCannotCreateRows() {
        for (float v :
                new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            hit(v);
        assertEquals(0, snapshot().getAsJsonArray("rows").size());
    }

    @Test
    void missingAttackerIsNotAttributedToTheCurrentCharacter() {
        record(log, victim, 999, 20);
        assertEquals(0, snapshot().get("totalDamage").getAsDouble());
    }

    @Test
    void worldCannotCountTargetsFromAnotherWorld() throws Exception {
        try (var other = new AbilityTestFixture()) {
            record(log, other.entity, environment.avatar.getId(), 20);
        }
        assertEquals(0, snapshot().get("totalDamage").getAsDouble());
    }

    @Test
    void removedAttackerIsNotCounted() {
        environment.fixture.scene.getEntities().remove(environment.avatar.getId());
        hit(20);
        assertEquals(0, snapshot().get("totalDamage").getAsDouble());
    }

    @Test
    void gadgetDamageUsesItsCapturedOwner() {
        var g = gadget(50, environment.avatar.getId());
        track(g);
        record(log, victim, 50, 30);
        assertEquals(30, snapshot().get("totalDamage").getAsDouble());
        assertEquals(10001, row().get("uid").getAsInt());
    }

    @Test
    void capturedGadgetOwnerRemainsAttributableWhileOffStageInTheSameTeam() {
        var g = gadget(50, environment.avatar.getId());
        track(g);
        environment.fixture.scene.getEntities().remove(environment.avatar.getId());
        record(log, victim, 50, 30);
        assertEquals(30, snapshot().get("totalDamage").getAsDouble());
    }

    @Test
    void gadgetCannotRetainAnOwnerRemovedFromTheTeam() {
        var g = gadget(50, environment.avatar.getId());
        track(g);
        environment.fixture.player.getTeamManager().getActiveTeam().clear();
        record(log, victim, 50, 30);
        assertEquals(0, snapshot().get("totalDamage").getAsDouble());
    }

    @Test
    void gadgetOwnerCycleDoesNotRecurseOrInventAnOwner() throws Exception {
        var a = gadget(50, environment.avatar.getId());
        var b = gadget(51, 50);
        var owner = EntityClientGadget.class.getDeclaredField("ownerEntityId");
        owner.setAccessible(true);
        owner.setInt(a, 51);
        track(a);
        record(log, victim, 50, 30);
        assertEquals(0, snapshot().get("totalDamage").getAsDouble());
    }

    @Test
    void removedGadgetCannotKeepProducingHitsFromItsCache() {
        var g = gadget(50, environment.avatar.getId());
        track(g);
        environment.fixture.scene.getEntities().remove(50);
        record(log, victim, 50, 30);
        assertEquals(0, snapshot().get("totalDamage").getAsDouble());
    }

    @Test
    void incomingDamageIsSeparateFromOutgoingDamage() {
        record(log, environment.avatar, victim.getId(), 15);
        assertEquals(0, snapshot().get("totalDamage").getAsDouble());
        assertEquals(15, snapshot().get("totalTaken").getAsDouble());
    }

    @Test
    void longIdleGapsDoNotInflateActiveFightDuration() {
        hit(10);
        clock.addAndGet(2000);
        hit(20);
        clock.addAndGet(60000);
        hit(30);
        assertEquals(2, snapshot().get("activeSeconds").getAsDouble());
        assertEquals(30, snapshot().get("dps").getAsDouble());
    }

    @Test
    void firstHitDpsHasAFiniteOneSecondFloor() {
        hit(20);
        assertEquals(20, snapshot().get("dps").getAsDouble());
    }

    @Test
    void recentHitsHaveABoundedFortyEntryWindow() {
        for (int i = 0; i < 55; i++) hit(i + 1);
        var s = snapshot();
        assertEquals(40, s.getAsJsonArray("recent").size());
        assertEquals(55, row().get("hits").getAsInt());
        assertEquals(55, row().get("maxHit").getAsDouble());
    }

    @Test
    void resetClearsRowsHistoryAndClockButAdvancesRevision() {
        hit(20);
        int revision = snapshot().get("revision").getAsInt();
        invoke(log, "reset", new Class<?>[] {});
        var s = snapshot();
        assertEquals(0, s.getAsJsonArray("rows").size());
        assertEquals(0, s.getAsJsonArray("recent").size());
        assertEquals(0, s.get("totalDamage").getAsDouble());
        assertTrue(s.get("revision").getAsInt() > revision);
    }

    @Test
    void callerCannotMutateTheLiveSnapshot() {
        hit(20);
        snapshot().getAsJsonArray("rows").get(0).getAsJsonObject().addProperty("damage", 999);
        assertEquals(20, row().get("damage").getAsDouble());
    }

    @Test
    void concurrentHitsAreNotLost() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 100; i++) jobs.add(pool.submit(() -> hit(1)));
            for (var job : jobs) job.get(10, TimeUnit.SECONDS);
            assertEquals(100, snapshot().get("totalDamage").getAsDouble());
            assertEquals(100, row().get("hits").getAsInt());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void normalSettlementAutomaticallyRecordsTheAppliedLoss() {
        victim.damage(25, environment.avatar.getId(), ElementType.Fire);
        assertEquals(25, snapshot(worldLog()).get("totalDamage").getAsDouble());
    }

    @Test
    void lockedHpProducesNoDamageStatistics() {
        victim.setLockHP(true);
        victim.damage(10, environment.avatar.getId(), ElementType.Fire);
        assertEquals(0, snapshot(worldLog()).get("totalDamage").getAsDouble());
    }

    @Test
    void lethalOverkillCountsOnlyTheHealthActuallyLost() {
        victim.damage(1000, environment.avatar.getId(), ElementType.Fire);
        assertEquals(100, snapshot(worldLog()).get("totalDamage").getAsDouble());
    }

    @Test
    void abilitySelfHpPaymentIsNotCountedAsIncomingCombatDamage() {
        environment.avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        environment.avatar.loseHpByAbility(10, false);
        assertEquals(0, snapshot(worldLog()).get("totalTaken").getAsDouble());
    }

    @Test
    void dpsCommandResetUsesTheSelectedWorld() throws Exception {
        victim.damage(25, environment.avatar.getId(), ElementType.Fire);
        var command =
                (emu.grasscutter.command.CommandHandler)
                        Class.forName("emu.grasscutter.command.commands.DpsCommand")
                                .getConstructor()
                                .newInstance();
        command.execute(null, environment.fixture.player, List.of("reset"));
        assertEquals(0, snapshot(worldLog()).get("totalDamage").getAsDouble());
    }

    @Test
    void canceledPluginHitProducesNoStatistics() {
        emu.grasscutter.Grasscutter.getPluginManager()
                .registerListener(
                        new emu.grasscutter.server.event.EventHandler<>(EntityDamageEvent.class)
                                .listener(EntityDamageEvent::cancel));
        victim.damage(25, environment.avatar.getId(), ElementType.Fire);
        assertEquals(0, snapshot(worldLog()).get("totalDamage").getAsDouble());
    }

    @Test
    void statisticsUseThePluginsAppliedAmount() {
        emu.grasscutter.Grasscutter.getPluginManager()
                .registerListener(
                        new emu.grasscutter.server.event.EventHandler<>(EntityDamageEvent.class)
                                .listener(event -> event.setDamage(12)));
        victim.damage(25, environment.avatar.getId(), ElementType.Fire);
        assertEquals(12, snapshot(worldLog()).get("totalDamage").getAsDouble());
    }

    @Test
    void replacingAGadgetEntityIdCannotReuseThePreviousCapturedOwner() {
        var g = gadget(50, environment.avatar.getId());
        track(g);
        var replacement = new AbilityTestFixture.TestEntity(environment.fixture.scene);
        replacement.setId(50);
        environment.fixture.scene.getEntities().put(50, replacement);
        record(log, victim, 50, 30);
        assertEquals(0, snapshot().get("totalDamage").getAsDouble());
    }

    @Test
    void normalSceneRegistrationCapturesGadgetOwnerBeforeItGoesOffStage() {
        var g = gadget(50, environment.avatar.getId());
        environment.fixture.scene.addEntity(g);
        environment.fixture.scene.getEntities().remove(environment.avatar.getId());
        victim.damage(15, 50, ElementType.Fire);
        assertEquals(15, snapshot(worldLog()).get("totalDamage").getAsDouble());
    }

    @Test
    void missingDisplayTextDoesNotInterruptRealDamageSettlement() throws Exception {
        var language = emu.grasscutter.utils.lang.Language.class;
        var texts = language.getDeclaredField("textMapStrings");
        texts.setAccessible(true);
        var scanned = language.getDeclaredField("scannedTextmaps");
        scanned.setAccessible(true);
        var oldTexts = texts.get(null);
        boolean oldScan = scanned.getBoolean(null);
        var data =
                emu.grasscutter.utils.JsonUtils.decode(
                        "{id:10000032,nameTextMapHash:12345678}",
                        emu.grasscutter.data.excels.avatar.AvatarData.class);
        var avatarData = emu.grasscutter.game.avatar.Avatar.class.getDeclaredField("avatarData");
        avatarData.setAccessible(true);
        var avatar = environment.avatar.getAvatar();
        var oldData = avatarData.get(avatar);
        try {
            texts.set(null, new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>());
            scanned.setBoolean(null, true);
            avatarData.set(avatar, data);
            assertDoesNotThrow(
                    () -> victim.damage(25, environment.avatar.getId(), ElementType.Fire));
            assertEquals(25, snapshot(worldLog()).get("totalDamage").getAsDouble());
            assertTrue(victim.isAlive());
            assertEquals(75, victim.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        } finally {
            avatarData.set(avatar, oldData);
            texts.set(null, oldTexts);
            scanned.setBoolean(null, oldScan);
        }
    }
}
