package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AvatarSkillInfoNotifyOuterClass.AvatarSkillInfoNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

class AbilityAvatarSkillInfoTest {
    private static List<AvatarSkillData> skills;
    private AbilityTestFixture fixture;
    private EntityAvatar entity;
    private SkillTalentLevelTest.RecordingAvatar avatar;
    private Ability ability;
    private final AtomicLong now = new AtomicLong();
    private final ActionAvatarSkillInfo handler = new ActionAvatarSkillInfo();
    private final List<Runnable> restore = new ArrayList<>();

    @BeforeAll
    static void resources() throws Exception {
        skills =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarSkillExcelConfigData.json"),
                        AvatarSkillData.class);
        AbilityManager.registerHandlers();
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setUid(76201);
        fixture.player.setWorld(fixture.scene.getWorld());
        for (var skill : skills) {
            var old = GameData.getAvatarSkillDataMap().put(skill.getId(), skill);
            restore.add(
                    () -> {
                        if (old == null) GameData.getAvatarSkillDataMap().remove(skill.getId());
                        else GameData.getAvatarSkillDataMap().put(skill.getId(), old);
                    });
        }
        entity = avatar(fixture.player);
        avatar = (SkillTalentLevelTest.RecordingAvatar) entity.getAvatar();
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{\"abilityName\":\"SkillInformation\"}", AbilityData.class),
                        entity,
                        fixture.player);
        fixture.player.packets.clear();
        avatar.saves = 0;
    }

    @AfterEach
    void close() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    private EntityAvatar avatar(Player player) {
        var depot =
                JsonUtils.decode(
                        "{\"id\":7301,\"skills\":[20011,20012],\"subSkills\":[11306],\"talents\":[],\"inherentProudSkillOpens\":[]}",
                        AvatarSkillDepotData.class);
        depot.onLoad();
        var value = new SkillTalentLevelTest.RecordingAvatar(player, 10000073, depot);
        var result =
                new EntityAvatar(fixture.scene, value) {
                    final AbilityPeriodicHealing periodic =
                            new AbilityPeriodicHealing(this, now::get);

                    @Override
                    public void initAbilities() {}

                    @Override
                    public AbilityPeriodicHealing getAbilityPeriodicHealing() {
                        return periodic;
                    }
                };
        fixture.scene.getEntities().put(result.getId(), result);
        return result;
    }

    private static AbilityModifierAction action(String type, int id, String extra) {
        return JsonUtils.decode(
                "{\"$type\":\"" + type + "\",\"skillID\":" + id + extra + "}",
                AbilityModifierAction.class);
    }

    private boolean execute(String type, int id) {
        return handler.execute(ability, action(type, id, ""), ByteString.EMPTY, entity);
    }

    private AvatarSkillInfoNotify lastPacket() throws Exception {
        var packets = fixture.player.packets;
        var last = packets.get(packets.size() - 1);
        assertEquals(PacketOpcodes.AvatarSkillInfoNotify, last.getOpcode());
        return AvatarSkillInfoNotify.parseFrom(last.getData());
    }

    @Test
    void additionSendsChargesForTheRecipientsGuidWithoutWritingTalentData() throws Exception {
        assertTrue(execute("AddAvatarSkillInfo", 20050));
        var packet = lastPacket();
        assertEquals(avatar.getGuid(), packet.getGuid());
        assertEquals(2, packet.getSkillMapOrThrow(20050).getMaxChargeCount());
        assertEquals(Map.of(20050, 2), avatar.getSkillInfoCharges());
        assertTrue(avatar.getSkillExtraChargeMap().isEmpty());
        assertEquals(0, avatar.saves);
    }

    @Test
    void duplicateConfirmationDoesNotRepeatTheNotification() {
        for (int i = 0; i < 10; i++) assertTrue(execute("AddAvatarSkillInfo", 20050));
        assertEquals(1, fixture.player.packets.size());
        assertEquals(Map.of(20050, 2), avatar.getSkillInfoCharges());
    }

    @Test
    void temporaryChargesJoinBothAvatarSerializationAndResynchronization() throws Exception {
        assertTrue(execute("AddAvatarSkillInfo", 20050));
        assertEquals(2, avatar.toProto().getSkillMapOrThrow(20050).getMaxChargeCount());
        fixture.player.packets.clear();
        assertTrue(avatar.sendSkillExtraChargeMap());
        assertEquals(2, lastPacket().getSkillMapOrThrow(20050).getMaxChargeCount());
        assertTrue(avatar.getSkillExtraChargeMap().isEmpty());
    }

    @Test
    void removalClearsExternalInformationWithoutInventingAZeroChargeDelete() {
        assertTrue(execute("AddAvatarSkillInfo", 20050));
        int sent = fixture.player.packets.size();
        assertTrue(execute("RemoveAvatarSkillInfo", 20050));
        assertTrue(execute("RemoveAvatarSkillInfo", 20050));
        assertTrue(avatar.getSkillInfoCharges().isEmpty());
        assertFalse(avatar.toProto().containsSkillMap(20050));
        assertEquals(sent, fixture.player.packets.size());
        assertFalse(avatar.sendSkillExtraChargeMap());
    }

    @Test
    void additionAndRemovalPreserveConstellationCharges() throws Exception {
        avatar.getSkillExtraChargeMap().put(20011, 3);
        assertTrue(execute("AddAvatarSkillInfo", 20011));
        assertEquals(3, lastPacket().getSkillMapOrThrow(20011).getMaxChargeCount());
        assertTrue(execute("RemoveAvatarSkillInfo", 20011));
        assertEquals(Map.of(20011, 3), avatar.getSkillExtraChargeMap());
        assertEquals(3, lastPacket().getSkillMapOrThrow(20011).getMaxChargeCount());
        assertEquals(3, avatar.toProto().getSkillMapOrThrow(20011).getMaxChargeCount());
    }

    @Test
    void constellationRecalculationKeepsTheSceneInformationAndRefreshesItsCount() {
        avatar.getSkillExtraChargeMap().put(20011, 3);
        assertTrue(execute("AddAvatarSkillInfo", 20011));
        avatar.recalcConstellations();
        assertTrue(avatar.getSkillExtraChargeMap().isEmpty());
        assertEquals(Map.of(20011, 1), avatar.getSkillInfoCharges());
        avatar.getSkillExtraChargeMap().put(20011, 4);
        assertEquals(4, avatar.getSkillInfoCharges().get(20011));
    }

    @Test
    void sceneLifecycleInvalidationClearsTemporarySkills() {
        assertTrue(execute("AddAvatarSkillInfo", 20050));
        entity.invalidateAbilityLifecycle();
        assertTrue(avatar.getSkillInfoCharges().isEmpty());
        assertFalse(execute("AddAvatarSkillInfo", 20050));
        entity.activateAbilityLifecycle();
        assertTrue(avatar.getSkillInfoCharges().isEmpty());
        assertTrue(execute("AddAvatarSkillInfo", 20050));
        assertEquals(Map.of(20050, 2), avatar.getSkillInfoCharges());
    }

    @Test
    void newSceneEntityDoesNotInheritThePreviousEntitiesTemporarySkills() {
        assertTrue(execute("AddAvatarSkillInfo", 20050));
        var replacement =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        replacement.setId(entity.getId());
        fixture.scene.getEntities().put(entity.getId(), replacement);
        fixture.player.getTeamManager().getActiveTeam().set(0, replacement);
        assertTrue(avatar.getSkillInfoCharges().isEmpty());
        assertFalse(execute("AddAvatarSkillInfo", 20050));
        assertTrue(replacement.getAbilityAvatarSkillInfo().snapshot().isEmpty());
    }

    @Test
    void anExplicitRecipientSurvivesChangingTheSelectedAvatar() throws Exception {
        var other = avatar(fixture.player);
        fixture.player.getTeamManager().getActiveTeam().add(other);
        fixture.player.getTeamManager().setCurrentCharacterIndex(1);
        assertTrue(execute("AddAvatarSkillInfo", 20050));
        assertEquals(avatar.getGuid(), lastPacket().getGuid());
        assertTrue(other.getAvatar().getSkillInfoCharges().isEmpty());
        assertEquals(Map.of(20050, 2), avatar.getSkillInfoCharges());
    }

    @Test
    void aPartnersAvatarIsNotModifiedByTheExecutingPlayersAction() {
        var partner = new AbilityTestFixture.TestPlayer();
        partner.setUid(76202);
        partner.setWorld(fixture.scene.getWorld());
        partner.setScene(fixture.scene);
        var other = avatar(partner);
        partner.getTeamManager().getActiveTeam().add(other);
        assertFalse(
                handler.execute(
                        ability, action("AddAvatarSkillInfo", 20050, ""), ByteString.EMPTY, other));
        assertTrue(other.getAbilityAvatarSkillInfo().snapshot().isEmpty());
        assertTrue(fixture.player.packets.isEmpty());
        assertTrue(partner.packets.isEmpty());
    }

    @Test
    void conditionsAndUnknownTargetsCannotActivateInformation() {
        var blocked =
                action(
                        "AddAvatarSkillInfo",
                        20050,
                        ",\"predicates\":[{\"$type\":\"DEOFBICNFHF\",\"entityTypes\":[\"Monster\"]}]");
        assertTrue(handler.execute(ability, blocked, ByteString.EMPTY, entity));
        assertFalse(
                handler.execute(
                        ability,
                        action("AddAvatarSkillInfo", 20050, ",\"target\":\"UnknownTarget\""),
                        ByteString.EMPTY,
                        entity));
        assertTrue(avatar.getSkillInfoCharges().isEmpty());
    }

    @Test
    void invalidDataHasNoSideEffects() {
        for (int id : new int[] {0, -1, Integer.MAX_VALUE})
            assertFalse(execute("AddAvatarSkillInfo", id));
        assertFalse(
                handler.execute(
                        null, action("AddAvatarSkillInfo", 20050, ""), ByteString.EMPTY, entity));
        assertFalse(handler.execute(ability, null, ByteString.EMPTY, entity));
        assertFalse(
                handler.executeResolved(
                        ability, action("AddAvatarSkillInfo", 20050, ""), ByteString.EMPTY, null));
        assertFalse(
                handler.execute(ability, action("HealHP", 20050, ""), ByteString.EMPTY, entity));
        assertFalse(
                handler.execute(
                        ability,
                        action("AddAvatarSkillInfo", 20050, ""),
                        ByteString.EMPTY,
                        fixture.entity));
        assertTrue(avatar.getSkillInfoCharges().isEmpty());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void validatedModifierAdditionAndRemovalRunTheRegisteredHandlersOnce() {
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"TemporarySkill\",\"modifiers\":{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Temporary\"}]},\"Temporary\":{\"onAdded\":[{\"$type\":\"AddAvatarSkillInfo\",\"skillID\":20050}],\"onRemoved\":[{\"$type\":\"RemoveAvatarSkillInfo\",\"skillID\":20050}]}}}",
                        AbilityData.class);
        var selected = new Ability(data, entity, fixture.player);
        var pulse = data.modifiers.get("Pulse");
        selected.registerModifier(entity, new AbilityModifierController(selected, data, pulse));
        assertTrue(
                entity.getAbilityPeriodicHealing()
                        .ownsAction(selected, 0, pulse.onThinkInterval[0]));
        now.set(1_000_000_000L);
        entity.onTick(0);
        var controller = selected.getModifiers(entity).get("Temporary");
        assertNotNull(controller);
        assertTrue(controller.hasServerCallbacks());
        assertEquals(Map.of(20050, 2), avatar.getSkillInfoCharges());
        assertEquals(1, fixture.player.packets.size());
        selected.removeModifier(entity, "Temporary");
        assertTrue(avatar.getSkillInfoCharges().isEmpty());
        assertEquals(1, fixture.player.packets.size());
    }

    @Test
    void anAvatarWithoutAnOwnerStillExposesItsPermanentCharges() {
        var detached = new emu.grasscutter.game.avatar.Avatar();
        detached.getSkillExtraChargeMap().put(20011, 3);
        assertNull(detached.getAsEntity());
        assertEquals(Map.of(20011, 3), detached.getSkillInfoCharges());
    }

    @Test
    void weaponInformationBelongsToItsCasterAndExpiresWhenTheWeaponLeaves() throws Exception {
        var weapon =
                new EntityWeapon(fixture.scene, 0, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getWeaponEntities().put(weapon.getId(), weapon);
        var other = avatar(fixture.player);
        fixture.player.getTeamManager().getActiveTeam().add(other);
        fixture.player.getTeamManager().setCurrentCharacterIndex(1);
        var weaponAbility = new Ability(ability.getData(), weapon, fixture.player);
        assertTrue(
                handler.execute(
                        weaponAbility,
                        action("AddAvatarSkillInfo", 20050, ""),
                        ByteString.EMPTY,
                        weapon));
        assertEquals(avatar.getGuid(), lastPacket().getGuid());
        assertTrue(other.getAbilityAvatarSkillInfo().snapshot().isEmpty());
        var item = new GameItem();
        item.setWeaponEntity(weapon);
        TpsWeaponSystem.removeWeaponEntity(item);
        assertNull(item.getWeaponEntity());
        assertFalse(fixture.scene.getWeaponEntities().containsKey(weapon.getId()));
        assertTrue(avatar.getSkillInfoCharges().isEmpty());
        assertFalse(
                handler.execute(
                        weaponAbility,
                        action("AddAvatarSkillInfo", 20050, ""),
                        ByteString.EMPTY,
                        weapon));
    }

    @Test
    void oneExpiredSourceDoesNotEraseAnotherActiveSourcesInformation() {
        var weapon =
                new EntityWeapon(fixture.scene, 0, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getWeaponEntities().put(weapon.getId(), weapon);
        var weaponAbility = new Ability(ability.getData(), weapon, fixture.player);
        assertTrue(
                handler.execute(
                        weaponAbility,
                        action("AddAvatarSkillInfo", 20050, ""),
                        ByteString.EMPTY,
                        weapon));
        assertTrue(execute("AddAvatarSkillInfo", 20050));
        assertEquals(1, fixture.player.packets.size());
        weapon.invalidateAbilityLifecycle();
        assertEquals(Map.of(20050, 2), avatar.getSkillInfoCharges());
        assertTrue(execute("RemoveAvatarSkillInfo", 20050));
        assertTrue(avatar.getSkillInfoCharges().isEmpty());
    }

    @Test
    void anUnownedWeaponCannotModifyTheSelectedAvatar() {
        var weapon =
                new EntityWeapon(fixture.scene, 0) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getWeaponEntities().put(weapon.getId(), weapon);
        var weaponAbility = new Ability(ability.getData(), weapon, fixture.player);
        assertFalse(
                handler.execute(
                        weaponAbility,
                        action("AddAvatarSkillInfo", 20050, ""),
                        ByteString.EMPTY,
                        weapon));
        assertTrue(avatar.getSkillInfoCharges().isEmpty());
    }

    @Test
    void handlerRegistrationAndStateWhitelistsIncludeBothResourceActions() {
        var annotation = ActionAvatarSkillInfo.class.getAnnotation(AbilityAction.class);
        assertEquals(
                Set.of(
                        AbilityModifierAction.Type.AddAvatarSkillInfo,
                        AbilityModifierAction.Type.RemoveAvatarSkillInfo),
                Set.of(annotation.value()));
        for (var type : annotation.value())
            assertTrue(AbilityManager.isAllowedInServerOwnedChain(type));
    }

    static Stream<Arguments> actualSkillInfoActions() throws Exception {
        var result = new ArrayList<Arguments>();
        var base = Path.of("resources/BinOutput/Ability/Temp");
        for (var name : List.of("QuestAbilities", "Linnea", "ColumbinaRP01", "TPS_Common")) {
            var path = base.resolve("AvatarAbilities/ConfigAbility_Avatar_" + name + ".json");
            collect(
                    JsonParser.parseString(Files.readString(path)),
                    path.getFileName().toString(),
                    result);
        }
        try (var paths = Files.walk(base.resolve("EquipAbilities/TPSWeaponAbilities"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList())
                collect(
                        JsonParser.parseString(Files.readString(path)),
                        path.getFileName().toString(),
                        result);
        }
        assertEquals(44, result.size());
        return result.stream();
    }

    private static void collect(JsonElement value, String path, List<Arguments> result) {
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            var type = object.get("$type");
            if (type != null
                    && List.of("AddAvatarSkillInfo", "RemoveAvatarSkillInfo")
                            .contains(type.getAsString()))
                result.add(Arguments.of(path, object.toString()));
            for (var entry : object.entrySet())
                collect(entry.getValue(), path + "/" + entry.getKey(), result);
        } else if (value.isJsonArray()) {
            int index = 0;
            for (var element : value.getAsJsonArray())
                collect(element, path + "/" + (index++), result);
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("actualSkillInfoActions")
    void actualResourceActionsSynchronizeAndRemoveTheDefinedSkill(String path, String json)
            throws Exception {
        var selected = JsonUtils.decode(json, AbilityModifierAction.class);
        assertNotNull(GameData.getAvatarSkillDataMap().get(selected.skillID), path);
        if (selected.type == AbilityModifierAction.Type.RemoveAvatarSkillInfo)
            assertTrue(execute("AddAvatarSkillInfo", selected.skillID));
        assertTrue(handler.execute(ability, selected, ByteString.EMPTY, entity), path);
        if (selected.type == AbilityModifierAction.Type.AddAvatarSkillInfo) {
            assertTrue(avatar.getSkillInfoCharges().containsKey(selected.skillID));
            assertEquals(avatar.getGuid(), lastPacket().getGuid());
            assertTrue(lastPacket().containsSkillMap(selected.skillID));
        } else
            assertFalse(
                    entity.getAbilityAvatarSkillInfo().snapshot().containsKey(selected.skillID));
        assertTrue(avatar.getSkillExtraChargeMap().isEmpty());
    }
}
