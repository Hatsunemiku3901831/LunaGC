package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionChangePhlogiston;
import emu.grasscutter.game.player.Phlogiston;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class PhlogistonResourceActionTest {
    PhlogistonMutationTest env;

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMutationTest();
        env.setup();
    }

    @AfterEach
    void close() {
        env.close();
    }

    boolean change(String config) {
        return new ActionChangePhlogiston()
                .execute(
                        env.ability,
                        JsonUtils.decode(
                                "{$type:'ChangePhlogiston'," + config + "}",
                                AbilityModifierAction.class),
                        ByteString.EMPTY,
                        env.caster);
    }

    @Test
    void currentDefaultModeCostsWrittenValue() {
        assertTrue(change("value:2.5"));
        assertEquals(47.5f, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void currentAddModeRestoresWrittenValue() {
        assertTrue(change("value:12,costType:'Add'"));
        assertEquals(62, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void rawModeIsAcceptedWhenCanonicalIsAbsent() {
        assertTrue(change("value:12,DHDDHBBIHEJ:'Add'"));
        assertEquals(62, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void canonicalModeWinsOverRawAndLegacyModes() {
        assertTrue(
                change("value:12,costType:'Cost',DHDDHBBIHEJ:'Add',determineType:'Add',ratio:99"));
        assertEquals(38, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void setModeSetsRatherThanAdds() {
        assertTrue(change("value:12,costType:'Set'"));
        assertEquals(12, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void zeroSetEmptiesTank() {
        assertTrue(change("value:0,costType:'Set'"));
        assertEquals(0, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void missingCurrentAmountCannotUseLegacyDefaultOne() {
        assertFalse(change("costType:'Add'"));
        assertEquals(50, env.fixture.player.getPhlogistonValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,2,'Unknown']", "[1,0,'DIV']", "[1,'ADD']"})
    void invalidAmountsDoNotMutate(String value) {
        assertFalse(change("value:" + value + ",costType:'Add'"));
        assertEquals(50, env.fixture.player.getPhlogistonValue());
        assertTrue(env.fixture.player.packets.isEmpty());
    }

    @Test
    void unknownCanonicalModeDoesNotFallbackToRaw() {
        assertFalse(change("value:5,costType:'Unknown',DHDDHBBIHEJ:'Add'"));
        assertEquals(50, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void dynamicValueReadsCurrentCasterOverride() {
        assertTrue(change("value:['fuel',3,'MUL'],costType:'Cost'"));
        assertEquals(44, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void writesUpdateServerTeamGlobalValue() {
        Phlogiston.change(env.fixture.player, -4);
        assertEquals(
                46,
                env.fixture
                        .player
                        .getTeamManager()
                        .getEntity()
                        .getGlobalAbilityValues()
                        .get(Phlogiston.TEAM_KEY));
        assertEquals(1, env.fixture.player.packets.size());
    }

    @Test
    void sceneEnterSynchronizesExistingFuelWithoutRefill() throws Exception {
        for (var name : List.of("proudSkillList", "talentIdList")) {
            var field = emu.grasscutter.game.avatar.Avatar.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(env.caster.getAvatar(), new HashSet<Integer>());
        }
        var packet = new PacketPlayerEnterSceneInfoNotify(env.fixture.player);
        assertEquals(50, env.fixture.player.getPhlogistonValue());
        var value =
                PlayerEnterSceneInfoNotifyOuterClass.PlayerEnterSceneInfoNotify.parseFrom(
                                packet.getData())
                        .getTeamEnterInfo()
                        .getTeamAbilityInfo()
                        .getSgvDynamicValueMapList()
                        .stream()
                        .filter(v -> Phlogiston.TEAM_KEY.equals(v.getKey().getStr()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(50, value.getFloatValue());
    }

    @Test
    void saurianSetChangesOnlyItsOwnTank() {
        var vehicle = env.environment.candle(NatsaurusPossessionTest.DRILL);
        var source = new Ability(env.ability.getData(), vehicle, env.fixture.player);
        assertTrue(
                new ActionChangePhlogiston()
                        .execute(
                                source,
                                JsonUtils.decode(
                                        "{$type:'ChangePhlogiston',value:7,costType:'Set'}",
                                        AbilityModifierAction.class),
                                ByteString.EMPTY,
                                vehicle));
        assertEquals(7, vehicle.getCurPhlogiston());
        assertEquals(50, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void defaultAbilityEmbryosIncludePhlogistonInSharedOverworld() {
        var phlo = emu.grasscutter.utils.Utils.abilityHash("DynamicAbility_Phlogiston");
        assertTrue(
                env
                        .fixture
                        .player
                        .getTeamManager()
                        .getAbilityControlBlock()
                        .getAbilityEmbryoListList()
                        .stream()
                        .anyMatch(e -> e.getAbilityNameHash() == phlo));
    }

    @Test
    void multiplayerSnapshotUsesEachPlayersOwnPool() throws Exception {
        var other = new AbilityTestFixture.TestPlayer();
        other.setUid(20001);
        other.setWorld(env.fixture.scene.getWorld());
        other.setScene(env.fixture.scene);
        other.getTeamManager().setEntity(new emu.grasscutter.game.entity.EntityTeam(other));
        other.setPhlogistonValue(17);
        env.fixture.scene.getWorld().getPlayers().add(other);
        var mp = emu.grasscutter.game.world.World.class.getDeclaredField("isMultiplayer");
        mp.setAccessible(true);
        mp.set(env.fixture.scene.getWorld(), true);
        var sync =
                SyncTeamEntityNotifyOuterClass.SyncTeamEntityNotify.parseFrom(
                        new PacketSyncTeamEntityNotify(env.fixture.player).getData());
        var entry =
                sync.getTeamEntityInfoListList().stream()
                        .filter(
                                p ->
                                        p.getTeamEntityId()
                                                == other.getTeamManager().getEntity().getId())
                        .findFirst()
                        .orElseThrow();
        assertEquals(17, entry.getTeamAbilityInfo().getSgvDynamicValueMap(0).getFloatValue());
        assertEquals(50, env.fixture.player.getPhlogistonValue());
    }

    @Test
    void currentResourceFuelRestoreActionWorks() throws Exception {
        var path =
                java.nio.file.Path.of(
                        "resources/BinOutput/Ability/Temp/ActivityAbilities/ConfigAbility_Activity_V6_2.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(path));
        var rows = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path));
        var node = findRestore(rows);
        assertNotNull(node);
        assertTrue(
                new ActionChangePhlogiston()
                        .execute(
                                env.ability,
                                JsonUtils.decode(node, AbilityModifierAction.class),
                                ByteString.EMPTY,
                                env.caster));
        assertEquals(100, env.fixture.player.getPhlogistonValue());
    }

    com.google.gson.JsonObject findRestore(com.google.gson.JsonElement value) {
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("$type")
                    && "ChangePhlogiston".equals(object.get("$type").getAsString())
                    && object.has("costType")
                    && "Add".equals(object.get("costType").getAsString())
                    && object.has("value")
                    && object.get("value").isJsonPrimitive()
                    && object.get("value").getAsFloat() == 100) return object;
            for (var item : object.entrySet()) {
                var found = findRestore(item.getValue());
                if (found != null) return found;
            }
        } else if (value.isJsonArray())
            for (var item : value.getAsJsonArray()) {
                var found = findRestore(item);
                if (found != null) return found;
            }
        return null;
    }
}
