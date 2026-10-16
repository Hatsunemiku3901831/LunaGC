package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.tps.*;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionTpsSupplyAmmo;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityMetaUpdateTpsWeaponAmmunitionOuterClass.AbilityMetaUpdateTpsWeaponAmmunition;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.net.proto.TpsAmmunitionChangeNotifyOuterClass.TpsAmmunitionChangeNotify;
import emu.grasscutter.net.proto.TpsAmmunitionChangeOuterClass.TpsAmmunitionChange;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

class TpsAmmunitionSupplyTest {
    static final Path RESOURCES = Path.of("resources");
    static final String BUFF = "UNIQUE_Avatar_TPS_GrenadeLauncher_Set_BulletOverride_1";
    final List<Runnable> restore = new ArrayList<>();
    AbilityTestFixture fixture;
    TpsEquipmentTest.RecordingPlayer player;
    EntityAvatar recipient;
    EntityWeapon weapon;
    Ability ability;

    <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        player = createPlayer();
        player.setScene(fixture.scene);
        player.setWorld(fixture.scene.getWorld());
        var avatar = new TpsEquipmentTest.RecordingAvatar(10000005);
        player.getAvatars().addAvatar(avatar);
        recipient =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        player.team.getActiveTeam().add(recipient);
        player.team.setCurrentCharacterIndex(0);
        fixture.scene.getEntities().put(recipient.getId(), recipient);
        put(
                GameData.getItemDataMap(),
                224003,
                JsonUtils.decode(
                        "{id:224003,itemType:ITEM_TPS_WEAPON,gadgetId:50016003}", ItemData.class));
        put(
                GameData.getTpsWeaponDataMap(),
                224003,
                JsonUtils.decode(
                        "{id:224003,wearSlotType:1,ammoSlotIds:[301]}", TpsWeaponData.class));
        for (var row :
                JsonUtils.loadToList(
                        RESOURCES.resolve("ExcelBinOutput/TpsAmmunitionExcelConfigData.json"),
                        TpsAmmunitionData.class))
            put(GameData.getTpsAmmunitionDataMap(), row.getId(), row);
        var gun = new TpsEquipmentTest.RecordingItem(224003);
        player.getInventory().addItem(gun);
        assertEquals(0, TpsWeaponSystem.wear(player, avatar.getGuid(), List.of(gun.getGuid())));
        TpsWeaponSystem.ensureWeaponEntities(avatar, fixture.scene);
        weapon = gun.getWeaponEntity();
        ability = new Ability(nativeLauncherAbility(), weapon, player);
        weapon.getInstancedAbilities().add(ability);
        for (var ammo : GameData.getTpsAmmunitionDataMap().values())
            player.getTpsAmmunition().put(ammo.getId(), 0);
        player.saves = 0;
        player.packets.clear();
    }

    TpsEquipmentTest.RecordingPlayer createPlayer() {
        return new TpsEquipmentTest.RecordingPlayer();
    }

    @AfterEach
    void close() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        if (fixture != null) fixture.close();
    }

    static AbilityData nativeLauncherAbility() throws Exception {
        var file =
                RESOURCES.resolve(
                        "BinOutput/Ability/Temp/EquipAbilities/TPSWeaponAbilities/ConfigAbility_TPSWeapon_GrenadeLauncher.json");
        for (var row : JsonParser.parseString(Files.readString(file)).getAsJsonArray()) {
            var data = row.getAsJsonObject().getAsJsonObject("Default");
            if (data.get("abilityName")
                    .getAsString()
                    .equals("Avatar_TPS_GrenadeLauncher_Normal_WeaponAdd_OnFire"))
                return JsonUtils.decode(data.toString(), AbilityData.class);
        }
        throw new AssertionError("Native launcher ability is missing");
    }

    static Stream<Arguments> resourceActions() throws Exception {
        var actions = new ArrayList<Arguments>();
        for (String file :
                List.of(
                        "Temp/EquipAbilities/TPSWeaponAbilities/ConfigAbility_TPSWeapon_GrenadeLauncher.json",
                        "Temp/GadgetAbilities/ConfigAbility_Scene_V7_0_ShadowArea.json")) {
            collect(
                    JsonParser.parseString(
                            Files.readString(RESOURCES.resolve("BinOutput/Ability").resolve(file))),
                    actions);
        }
        assertEquals(9, actions.size());
        return actions.stream();
    }

    static void collect(JsonElement value, List<Arguments> actions) {
        if (value.isJsonArray()) for (var child : value.getAsJsonArray()) collect(child, actions);
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("$type") && object.get("$type").getAsString().equals("TPSSupplyAmmo"))
                actions.add(
                        Arguments.of(
                                object.toString(),
                                object.get("NHCCEJKBOPB").getAsInt(),
                                object.get("EMNMAKMAKLM").getAsInt()));
            for (var child : object.entrySet()) collect(child.getValue(), actions);
        }
    }

    @ParameterizedTest
    @MethodSource("resourceActions")
    void realResourceSupplyActionsGrantKnownPoolsAndRejectMissingPool1004(
            String json, int id, int count) throws Exception {
        var action = JsonUtils.decode(json, AbilityModifierAction.class);
        boolean known = id != 1004;
        assertEquals(
                known,
                new ActionTpsSupplyAmmo().execute(ability, action, ByteString.EMPTY, recipient));
        assertEquals(known ? count : 0, TpsWeaponSystem.getReserve(player, id));
        assertEquals(known ? 3 : 0, player.saves);
        assertEquals(known ? count : 0, notifiedDelta(id));
    }

    int notifiedDelta(int id) throws Exception {
        int result = 0;
        for (var packet : player.packets)
            if (packet.getOpcode() == PacketOpcodes.TpsAmmunitionChangeNotify)
                for (var entry :
                        TpsAmmunitionChangeNotify.parseFrom(packet.getData())
                                .getAmmunitionListList())
                    if (entry.getAmmunitionConfigId() == id) result += entry.getChangeCount();
        return result;
    }

    @Test
    void resumingAnInterruptedNativeSupplyCannotBeRepeatedByItsOriginalLeafConfirmation()
            throws Exception {
        player.failNextSave = true;
        modifierChange(ModifierAction.MODIFIER_ACTION_ADDED);
        var pending = player.getPendingTpsSupply();
        assertNotNull(pending);
        assertEquals(0, TpsWeaponSystem.getReserve(player, 1003));
        var stored =
                new emu.grasscutter.game.tps.TpsSupplyTransaction.Stored(
                        player.getUid(), new HashMap<>(pending.getBefore()), null);
        var recovery =
                new emu.grasscutter.game.tps.TpsSupplyTransaction(
                        player, player::saveTpsAmmunition, () -> stored, System::currentTimeMillis);
        recovery.resume(pending.getOperationId());
        assertEquals(6, TpsWeaponSystem.getReserve(player, 1003));
        int saves = player.saves;
        var leaf = ability.getData().modifiers.get(BUFF).onAdded[2];
        int local =
                ability.getData().localIdToAction.entrySet().stream()
                        .filter(e -> e.getValue() == leaf)
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        player.getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(weapon.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_NONE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(abilityId())
                                                .setInstancedModifierId(91)
                                                .setLocalId(local))
                                .build());
        assertEquals(6, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(saves, player.saves);
    }

    AbilityModifierAction supply(String value) {
        return JsonUtils.decode(
                "{$type:TPSSupplyAmmo,target:CurLocalAvatar,FMGHPKANDIA:true,"
                        + "OJDCLLBDPNK:true,NHCCEJKBOPB:1003,EMNMAKMAKLM:"
                        + value
                        + "}",
                AbilityModifierAction.class);
    }

    @Test
    void aFullPoolDoesNotSaveOrEmitASecondGrantAndPartialSupplyUsesTheActualDelta()
            throws Exception {
        player.getTpsAmmunition().put(1003, 48);
        var action = supply("6");
        var handler = new ActionTpsSupplyAmmo();
        assertTrue(handler.execute(ability, action, ByteString.EMPTY, recipient));
        assertEquals(50, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(2, notifiedDelta(1003));
        assertEquals(3, player.saves);
        assertTrue(handler.execute(ability, action, ByteString.EMPTY, recipient));
        assertEquals(3, player.saves);
        assertEquals(2, notifiedDelta(1003));
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0.9,0", "1.9,1", "1.99999999,2", "2147483520,50"})
    void nativeFloatConversionPrecedesIntegerTruncation(String amount, int expected)
            throws Exception {
        assertTrue(
                new ActionTpsSupplyAmmo()
                        .execute(ability, supply(amount), ByteString.EMPTY, recipient));
        assertEquals(expected, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(expected, notifiedDelta(1003));
        assertEquals(expected == 0 ? 0 : 3, player.saves);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1", "2147483648", "2147483647", "[1,0,\"DIV\"]", "[\"ADD\"]"})
    void invalidAmountsDoNotGrantOrSave(String amount) {
        assertFalse(
                new ActionTpsSupplyAmmo()
                        .execute(ability, supply(amount), ByteString.EMPTY, recipient));
        assertEquals(0, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(0, player.saves);
        assertTrue(player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"explicit", "bonus", "pool", "target"})
    void unpairedNativeBranchesAreRejected(String variant) {
        var action = supply("6");
        switch (variant) {
            case "explicit" -> action.tpsSupplyUseExplicitAmmoId = false;
            case "bonus" -> action.tpsSupplyUseBonus = true;
            case "pool" -> action.tpsSupplyAmmunitionId = 1004;
            case "target" -> action.target = "UnknownTarget";
        }
        assertFalse(
                new ActionTpsSupplyAmmo().execute(ability, action, ByteString.EMPTY, recipient));
        assertEquals(0, player.saves);
        assertTrue(player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"source", "ability", "scene", "recipient", "team", "owner", "equipment"})
    void staleOrForeignSupplyCannotMutatePlayerState(String variant) {
        switch (variant) {
            case "source" -> fixture.scene.getWeaponEntities().remove(weapon.getId());
            case "ability" -> weapon.getInstancedAbilities().remove(ability);
            case "scene" -> player.setScene(null);
            case "recipient" -> fixture.scene.getEntities().remove(recipient.getId());
            case "team" -> player.team.getActiveTeam().clear();
            case "owner" -> ability = new Ability(ability.getData(), weapon, fixture.player);
            case "equipment" -> recipient.getAvatar().getTpsWeaponIds().clear();
        }
        assertFalse(
                new ActionTpsSupplyAmmo()
                        .executeResolved(ability, supply("6"), ByteString.EMPTY, recipient));
        assertEquals(0, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(0, player.saves);
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void installedDynamicSourceUsesAbilityPropertiesAndPreservesLoadedMagazines() throws Exception {
        weapon.getInstancedAbilities().remove(ability);
        weapon.getDynamicAbilities().put(ability.getData().abilityName, ability);
        ability.getAbilitySpecials().put("_NativeSupply", 6f);
        player.getTpsAmmunition().put(1003, 10);
        player.getTpsMagazineAmmunition().put(301, 5);
        assertTrue(
                new ActionTpsSupplyAmmo()
                        .execute(
                                ability,
                                supply("\"%_NativeSupply\""),
                                ByteString.EMPTY,
                                recipient));
        assertEquals(16, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(5, player.getTpsMagazineAmmunition().get(301));
        assertEquals(6, notifiedDelta(1003));
        assertEquals(3, player.saves);
        assertEquals(1, player.packets.size());
    }

    @Test
    void aClientPositiveSupplyConfirmationCannotRepeatTheResourceGrant() throws Exception {
        modifierChange(ModifierAction.MODIFIER_ACTION_ADDED);
        for (int type : new int[] {2, 3}) {
            var payload =
                    AbilityMetaUpdateTpsWeaponAmmunition.newBuilder()
                            .setUpdateTypeValue(type)
                            .addAmmunitionList(
                                    TpsAmmunitionChange.newBuilder()
                                            .setAmmunitionConfigId(1003)
                                            .setChangeCount(6));
            assertFalse(
                    TpsWeaponSystem.onAmmunitionInvoke(
                            player,
                            AbilityInvokeEntry.newBuilder()
                                    .setEntityId(weapon.getId())
                                    .setAbilityData(payload.build().toByteString())
                                    .build()));
        }
        assertEquals(6, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(6, notifiedDelta(1003));
        assertEquals(3, player.saves);
    }

    void modifierChange(ModifierAction action) throws Exception {
        modifierChange(action, weapon);
    }

    void modifierChange(ModifierAction action, GameEntity source) throws Exception {
        int local = new ArrayList<>(ability.getData().modifiers.keySet()).indexOf(BUFF);
        var change =
                AbilityMetaModifierChange.newBuilder()
                        .setAction(action)
                        .setParentAbilityName(
                                AbilityString.newBuilder().setStr(ability.getData().abilityName))
                        .setModifierLocalId(local);
        player.getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(source.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(
                                                        source.getInstancedAbilities()
                                                                        .indexOf(ability)
                                                                + 1)
                                                .setInstancedModifierId(91))
                                .setAbilityData(change.build().toByteString())
                                .build());
    }

    @Test
    void nativeLauncherModifierAwardsOnceAcrossRepeatedAddedAndLeafConfirmations()
            throws Exception {
        modifierChange(ModifierAction.MODIFIER_ACTION_ADDED);
        assertEquals(6, TpsWeaponSystem.getReserve(player, 1003));
        modifierChange(ModifierAction.MODIFIER_ACTION_ADDED);
        var action = ability.getData().modifiers.get(BUFF).onAdded[2];
        int local =
                ability.getData().localIdToAction.entrySet().stream()
                        .filter(entry -> entry.getValue() == action)
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElseThrow();
        player.getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(weapon.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_NONE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(abilityId())
                                                .setInstancedModifierId(91)
                                                .setLocalId(local))
                                .build());
        assertEquals(6, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(3, player.saves);
        // The unique live application also identifies a confirmation with no modifier ID.
        player.getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(weapon.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_NONE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(abilityId())
                                                .setLocalId(local))
                                .build());
        assertEquals(6, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(3, player.saves);
        modifierChange(ModifierAction.MODIFIER_ACTION_REMOVED);
        // A late explicit callback from the removed application cannot grant again.
        player.getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(weapon.getId())
                                .setArgumentType(
                                        AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_NONE)
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setInstancedAbilityId(abilityId())
                                                .setInstancedModifierId(91)
                                                .setLocalId(local))
                                .build());
        assertEquals(6, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(3, player.saves);
        modifierChange(ModifierAction.MODIFIER_ACTION_ADDED);
        assertEquals(12, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(6, player.saves);
    }

    int abilityId() {
        return weapon.getInstancedAbilities().indexOf(ability) + 1;
    }

    @ParameterizedTest
    @CsvSource({"lifetime,false", "entity-id,false", "lifetime,true", "entity-id,true"})
    void aReenteredSourceGetsOneFreshSupplyInsteadOfInheritingThePreviousLife(
            String change, boolean serverFirst) throws Exception {
        var source = fixture.entity;
        ability = new Ability(nativeLauncherAbility(), source, player);
        source.getInstancedAbilities().add(ability);
        emu.grasscutter.game.ability.AbilityModifierController previous;
        if (serverFirst) {
            var modifier = ability.getData().modifiers.get(BUFF);
            previous =
                    new emu.grasscutter.game.ability.AbilityModifierController(
                            ability, ability.getData(), modifier);
            ability.registerModifier(source, previous);
            emu.grasscutter.game.ability.AbilityCooldownInvocation.run(
                    previous,
                    () ->
                            ability.getManager()
                                    .executeActionNow(
                                            ability,
                                            modifier.onAdded[2],
                                            ByteString.EMPTY,
                                            recipient));
        } else {
            modifierChange(ModifierAction.MODIFIER_ACTION_ADDED, source);
            previous = source.getInstancedModifiers().get(91);
        }
        assertEquals(6, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(3, player.saves);
        if (change.equals("lifetime")) {
            fixture.scene.removeEntity(source);
            fixture.scene.addEntity(source);
        } else {
            assertTrue(fixture.scene.getEntities().remove(source.getId(), source));
            source.setId(84);
            fixture.scene.addEntity(source);
        }
        modifierChange(ModifierAction.MODIFIER_ACTION_ADDED, source);
        assertEquals(12, TpsWeaponSystem.getReserve(player, 1003));
        assertNotSame(previous, source.getInstancedModifiers().get(91));
        assertEquals(6, player.saves);
        modifierChange(ModifierAction.MODIFIER_ACTION_ADDED, source);
        assertEquals(12, TpsWeaponSystem.getReserve(player, 1003));
        assertEquals(6, player.saves);
    }
}
