package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AvatarEscoffierSkillCookDataNotifyOuterClass.AvatarEscoffierSkillCookDataNotify;
import emu.grasscutter.utils.GameCalendar;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

class SkillCookQuotaTest {
    final Gson gson = new Gson();
    static final Instant MONDAY =
            Instant.parse(
                    "2026-10-12T20:00:00Z"); // Tuesday 04:00 China; Monday week anchor is one day
    // earlier.
    static final Instant RESET = Instant.parse("2026-10-11T20:00:00Z");
    static final String PARAM = "CONST_VALUE_AVATAR_ESCOFFIER_SKILL_COOK_PARAM",
            DROP = "CONST_VALUE_AVATAR_ESCOFFIER_SKILL_COOK_DROP_PARAM";
    AbilityTestFixture environment;
    InvestigationRewardTest.ClaimPlayer player;
    Field datastore;
    Object oldStore, oldConfig;
    boolean oldConfigPresent, failSave;
    int saves;
    JsonObject saved;
    @TempDir Path directory;

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new InvestigationRewardTest.ClaimPlayer();
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        try {
            oldConfig = GameData.class.getMethod("getEscoffierCookConfig").invoke(null);
            oldConfigPresent = true;
        } catch (NoSuchMethodException old) {
        }
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldStore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")) {
                                if (args.length == 2) {
                                    var options = (dev.morphia.InsertOneOptions) args[1];
                                    assertEquals("majority", options.writeConcern().getWString());
                                    assertEquals(Boolean.TRUE, options.writeConcern().getJournal());
                                    assertTrue(
                                            player.packets.isEmpty(),
                                            "Quota notification preceded persistence");
                                    saves++;
                                    if (failSave) throw new IllegalStateException("Write fault");
                                    saved = gson.toJsonTree(state()).getAsJsonObject();
                                }
                                return args[0];
                            }
                            if (method.getName().equals("delete"))
                                return com.mongodb.client.result.DeleteResult.acknowledged(1);
                            return null;
                        }));
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        datastore.set(null, oldStore);
        if (oldConfigPresent)
            GameData.class
                    .getMethod("setEscoffierCookConfig", configType())
                    .invoke(null, oldConfig);
        environment.close();
    }

    Class<?> type(String name) {
        Class<?> result = null;
        try {
            result = Class.forName(name);
        } catch (ClassNotFoundException old) {
        }
        assertNotNull(result, "Missing cooking quota implementation " + name);
        return result;
    }

    Class<?> configType() {
        return type("emu.grasscutter.game.managers.cooking.EscoffierCookConfig");
    }

    Class<?> stateType() {
        return type("emu.grasscutter.game.managers.cooking.EscoffierCookState");
    }

    JsonArray constants() {
        return JsonParser.parseString(
                        "[{name:'"
                                + PARAM
                                + "',value:['10','200000','9','10000112','3','1.6']},{name:'"
                                + DROP
                                + "',value:['60','30','10','21600016','21600017','21600018']},{name:'OTHER',value:['broken']}]"
                                + " ")
                .getAsJsonArray();
    }

    Object config(JsonArray values) throws Exception {
        return configType().getMethod("parse", JsonArray.class).invoke(null, values);
    }

    void install(Object config) throws Exception {
        GameData.class.getMethod("setEscoffierCookConfig", configType()).invoke(null, config);
    }

    Object state() throws Exception {
        return Player.class.getMethod("getEscoffierCookState").invoke(player);
    }

    void state(int timestamp, int count) throws Exception {
        var value =
                gson.fromJson(
                        "{format:1,lastRefreshTime:"
                                + timestamp
                                + ",finishedWeeklyCookNum:"
                                + count
                                + "}",
                        stateType());
        Player.class.getMethod("setEscoffierCookState", stateType()).invoke(player, value);
    }

    boolean sync(Instant now) throws Exception {
        return (Boolean)
                type("emu.grasscutter.game.managers.cooking.EscoffierCooking")
                        .getMethod("sync", Player.class, Instant.class)
                        .invoke(null, player, now);
    }

    Object getter(Object value, String name) throws Exception {
        return value.getClass().getMethod(name).invoke(value);
    }

    boolean load(Path path) throws Exception {
        configType();
        return (Boolean)
                ResourceLoader.class
                        .getMethod("loadEscoffierCookConfig", Path.class)
                        .invoke(null, path);
    }

    void owner() {
        var depot =
                emu.grasscutter.utils.JsonUtils.decode(
                        JsonParser.parseString(
                                "{id:11201,skills:[],subSkills:[],talents:[],extraAbilities:[],inherentProudSkillOpens:[]}"),
                        AvatarSkillDepotData.class);
        depot.onLoad();
        assertTrue(
                player.getAvatars()
                        .addAvatar(
                                new SkillTalentLevelTest.RecordingAvatar(player, 10000112, depot)));
    }

    AvatarEscoffierSkillCookDataNotify notifyQuota() throws Exception {
        var rows =
                player.packets.stream()
                        .filter(
                                p ->
                                        p.getOpcode()
                                                == PacketOpcodes.AvatarEscoffierSkillCookDataNotify)
                        .toList();
        assertEquals(1, rows.size());
        return AvatarEscoffierSkillCookDataNotify.parseFrom(rows.get(0).getData());
    }

    int week(Instant now) {
        return (int)
                GameCalendar.weekStart(GameCalendar.resetDay(now))
                        .atTime(4, 0)
                        .atZone(GameCalendar.SERVER_ZONE)
                        .toEpochSecond();
    }

    @Test
    void actualCurrentConstantsReadTenAndAllThreeDropTiers() throws Exception {
        var rows =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "/resources/ExcelBinOutput/ConstValueExcelConfigData.json")))
                        .getAsJsonArray();
        var cfg = config(rows);
        assertEquals(10, getter(cfg, "weeklyLimit"));
        assertEquals(10000112, getter(cfg, "avatarId"));
        assertEquals(100L, getter(cfg, "totalWeight"));
        var tiers = (List<?>) getter(cfg, "tiers");
        assertEquals(3, tiers.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(List.of(60, 30, 10).get(i), getter(tiers.get(i), "weight"));
            assertEquals(21600016 + i, getter(tiers.get(i), "dropId"));
        }
    }

    @Test
    void configurationIsImmutable() throws Exception {
        var cfg = config(constants());
        var tiers = (List<?>) getter(cfg, "tiers");
        assertThrows(UnsupportedOperationException.class, () -> tiers.clear());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missingParam",
                "missingDrop",
                "duplicateParam",
                "duplicateDrop",
                "shortParam",
                "shortDrop",
                "zeroLimit",
                "negativeLimit",
                "overflowLimit",
                "fractionalLimit",
                "wrongAvatar",
                "negativeAvatar",
                "negativeWeight",
                "zeroWeights",
                "overflowWeights",
                "fractionalWeight",
                "badDrop",
                "wrongContainer"
            })
    void malformedSettingsAreNotReplacedByPermissiveDefaults(String kind) throws Exception {
        var rows = constants();
        var param = rows.get(0).getAsJsonObject().getAsJsonArray("value");
        var drop = rows.get(1).getAsJsonObject().getAsJsonArray("value");
        switch (kind) {
            case "missingParam" -> rows.remove(0);
            case "missingDrop" -> rows.remove(1);
            case "duplicateParam" -> rows.add(rows.get(0).deepCopy());
            case "duplicateDrop" -> rows.add(rows.get(1).deepCopy());
            case "shortParam" -> {
                while (param.size() > 3) param.remove(param.size() - 1);
            }
            case "shortDrop" -> drop.remove(5);
            case "zeroLimit" -> param.set(0, new JsonPrimitive("0"));
            case "negativeLimit" -> param.set(0, new JsonPrimitive("-1"));
            case "overflowLimit" -> param.set(0, new JsonPrimitive("2147483648"));
            case "fractionalLimit" -> param.set(0, new JsonPrimitive("10.1"));
            case "wrongAvatar" -> param.set(3, new JsonPrimitive("0"));
            case "negativeAvatar" -> param.set(3, new JsonPrimitive("-1"));
            case "negativeWeight" -> drop.set(0, new JsonPrimitive("-1"));
            case "zeroWeights" -> {
                for (int i = 0; i < 3; i++) drop.set(i, new JsonPrimitive("0"));
            }
            case "overflowWeights" -> drop.set(1, new JsonPrimitive("2147483648"));
            case "fractionalWeight" -> drop.set(0, new JsonPrimitive("60.5"));
            case "badDrop" -> drop.set(3, new JsonPrimitive("0"));
            case "wrongContainer" -> rows.get(0).getAsJsonObject().addProperty("value", "10");
        }
        var failure = assertThrows(InvocationTargetException.class, () -> config(rows));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    @Test
    void customPositiveWeightsAreKeptExactlyWithoutARewrittenPool() throws Exception {
        var rows = constants();
        rows.get(1).getAsJsonObject().getAsJsonArray("value").set(0, new JsonPrimitive("0"));
        var cfg = config(rows);
        assertEquals(40L, getter(cfg, "totalWeight"));
        assertEquals(0, getter(((List<?>) getter(cfg, "tiers")).get(0), "weight"));
    }

    @Test
    void actualResourceLoadInstallsValidatedSettings() throws Exception {
        assertTrue(load(Path.of("/resources/ExcelBinOutput/ConstValueExcelConfigData.json")));
        assertEquals(
                10,
                getter(
                        GameData.class.getMethod("getEscoffierCookConfig").invoke(null),
                        "weeklyLimit"));
    }

    @Test
    void malformedReloadClearsOldSettings() throws Exception {
        install(config(constants()));
        assertFalse(
                load(
                        Files.writeString(
                                directory.resolve("broken.json"),
                                "[{name:'" + PARAM + "',value:['1000']}]")));
        assertNull(GameData.class.getMethod("getEscoffierCookConfig").invoke(null));
    }

    @Test
    void missingResourceClearsOldSettings() throws Exception {
        install(config(constants()));
        assertFalse(load(directory.resolve("missing.json")));
        assertNull(GameData.class.getMethod("getEscoffierCookConfig").invoke(null));
    }

    @Test
    void wrongResourceContainerClearsOldSettings() throws Exception {
        install(config(constants()));
        assertFalse(load(Files.writeString(directory.resolve("object.json"), "{}")));
        assertNull(GameData.class.getMethod("getEscoffierCookConfig").invoke(null));
    }

    @Test
    void freshOwnerGetsConfirmedWeekAndZeroUsesOnLogin() throws Exception {
        install(config(constants()));
        owner();
        assertTrue(sync(MONDAY));
        var message = notifyQuota();
        assertEquals(week(MONDAY), message.getLastRefreshTime());
        assertEquals(0, message.getFinishedWeeklyCookNum());
        assertEquals(1, saves);
        assertNotNull(saved);
    }

    @Test
    void sameWeekRetainsActualUsedCountWithoutSavingAgain() throws Exception {
        install(config(constants()));
        owner();
        state(week(MONDAY), 7);
        assertTrue(sync(MONDAY));
        assertEquals(7, notifyQuota().getFinishedWeeklyCookNum());
        assertEquals(0, saves);
    }

    @Test
    void exactMondayFourResetsCounts() throws Exception {
        install(config(constants()));
        owner();
        state(week(RESET.minusSeconds(1)), 10);
        assertTrue(sync(RESET));
        assertEquals(0, notifyQuota().getFinishedWeeklyCookNum());
        assertEquals(week(RESET), notifyQuota().getLastRefreshTime());
        assertEquals(1, saves);
    }

    @Test
    void oneSecondBeforeResetPreservesOldWeek() throws Exception {
        install(config(constants()));
        owner();
        var now = RESET.minusSeconds(1);
        state(week(now), 10);
        assertTrue(sync(now));
        assertEquals(10, notifyQuota().getFinishedWeeklyCookNum());
        assertEquals(0, saves);
    }

    @Test
    void missedWeeksResetOnLogin() throws Exception {
        install(config(constants()));
        owner();
        state(week(MONDAY.minus(Duration.ofDays(21))), 10);
        assertTrue(sync(MONDAY));
        assertEquals(0, notifyQuota().getFinishedWeeklyCookNum());
    }

    @Test
    void futureWeekDoesNotRollTheCounterBack() throws Exception {
        install(config(constants()));
        owner();
        state(week(MONDAY.plus(Duration.ofDays(7))), 9);
        assertFalse(sync(MONDAY));
        assertTrue(player.packets.isEmpty());
        assertEquals(0, saves);
    }

    @Test
    void negativeCounterIsNotSilentlyReset() throws Exception {
        install(config(constants()));
        owner();
        state(week(MONDAY), -1);
        assertFalse(sync(MONDAY));
        assertTrue(player.packets.isEmpty());
        assertEquals(0, saves);
    }

    @Test
    void overLimitSavedCounterIsPreservedAndReported() throws Exception {
        install(config(constants()));
        owner();
        state(week(MONDAY), 1000);
        assertTrue(sync(MONDAY));
        assertEquals(1000, notifyQuota().getFinishedWeeklyCookNum());
        assertEquals(0, saves);
    }

    @Test
    void foreignNonAnchorTimeDoesNotResetUses() throws Exception {
        install(config(constants()));
        owner();
        state(week(MONDAY) + 1, 9);
        assertFalse(sync(MONDAY));
        assertTrue(player.packets.isEmpty());
        assertEquals(0, saves);
    }

    @Test
    void unownedCharacterCannotCreateQuotaOrSync() throws Exception {
        install(config(constants()));
        assertFalse(sync(MONDAY));
        assertNull(state());
        assertTrue(player.packets.isEmpty());
        assertEquals(0, saves);
    }

    @Test
    void absentConfigurationDoesNotInventAQuota() throws Exception {
        install(null);
        owner();
        assertFalse(sync(MONDAY));
        assertNull(state());
        assertEquals(0, saves);
    }

    @Test
    void failedFirstSaveSendsNoSpeculativeQuota() throws Exception {
        install(config(constants()));
        owner();
        failSave = true;
        assertFalse(sync(MONDAY));
        assertTrue(player.packets.isEmpty());
        assertNull(state());
    }

    @Test
    void failedResetSaveRetainsPreviousCounter() throws Exception {
        install(config(constants()));
        owner();
        state(week(RESET.minusSeconds(1)), 10);
        failSave = true;
        assertFalse(sync(RESET));
        assertTrue(player.packets.isEmpty());
        assertEquals(10, getter(state(), "getFinishedWeeklyCookNum"));
        assertEquals(week(RESET.minusSeconds(1)), getter(state(), "getLastRefreshTime"));
    }

    @Test
    void zeroTimestampWithPositiveUsesIsRejected() throws Exception {
        install(config(constants()));
        owner();
        state(0, 4);
        assertFalse(sync(MONDAY));
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void emptyLegacyStateCanInitializeAConfirmedWeek() throws Exception {
        install(config(constants()));
        owner();
        state(0, 0);
        assertTrue(sync(MONDAY));
        assertEquals(0, notifyQuota().getFinishedWeeklyCookNum());
        assertEquals(1, saves);
    }

    @Test
    void unsupportedStateFormatCannotResetUses() throws Exception {
        install(config(constants()));
        owner();
        state(week(MONDAY), 6);
        var field = stateType().getDeclaredField("format");
        field.setAccessible(true);
        field.setInt(state(), 2);
        assertFalse(sync(MONDAY));
        assertTrue(player.packets.isEmpty());
        assertEquals(0, saves);
    }

    @Test
    void trialAvatarCannotCreateQuota() throws Exception {
        install(config(constants()));
        owner();
        player.getAvatars().getAvatarById(10000112).setTrialAvatarId(999);
        assertFalse(sync(MONDAY));
        assertNull(state());
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void unsignedFutureEpochRemainsValidWithinProtocolRange() throws Exception {
        install(config(constants()));
        owner();
        var now = Instant.parse("2042-10-13T00:00:00Z");
        assertTrue(sync(now));
        assertEquals(
                Integer.toUnsignedLong(week(now)),
                Integer.toUnsignedLong(notifyQuota().getLastRefreshTime()));
    }

    @Test
    void epochBeyondUint32CannotWrapIntoAPastWeek() throws Exception {
        install(config(constants()));
        owner();
        assertFalse(sync(Instant.parse("2200-10-13T00:00:00Z")));
        assertTrue(player.packets.isEmpty());
        assertEquals(0, saves);
    }

    @Test
    void sameWeekQuotaSurvivesMongoWithItsPlayer() throws Exception {
        install(config(constants()));
        owner();
        state(week(MONDAY), 6);
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        assertNotNull(uri);
        var name = "lunagc_test_skillcook189_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = com.mongodb.client.MongoClients.create(uri)) {
            var store = dev.morphia.Morphia.createDatastore(client, name);
            store.getMapper().map(Player.class);
            try {
                store.getDatabase()
                        .getCollection("players")
                        .insertOne(
                                new org.bson.Document("_id", 10001)
                                        .append("teamManager", new org.bson.Document())
                                        .append(
                                                "escoffierCookState",
                                                org.bson.Document.parse(gson.toJson(state()))));
                var restoredPlayer = store.find(Player.class).first();
                assertNotNull(restoredPlayer);
                store.save(restoredPlayer);
                var restored =
                        Player.class
                                .getMethod("getEscoffierCookState")
                                .invoke(store.find(Player.class).first());
                assertNotNull(restored);
                assertEquals(week(MONDAY), getter(restored, "getLastRefreshTime"));
                assertEquals(6, getter(restored, "getFinishedWeeklyCookNum"));
                Player.class
                        .getMethod("setEscoffierCookState", stateType())
                        .invoke(player, restored);
                assertTrue(sync(MONDAY));
                assertEquals(6, notifyQuota().getFinishedWeeklyCookNum());
            } finally {
                assertTrue(name.startsWith("lunagc_test_skillcook189_"));
                client.getDatabase(name).drop();
            }
        }
    }
}
