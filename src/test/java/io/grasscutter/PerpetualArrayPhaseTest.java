package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;

import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityCombatState;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.ability.AbilityModifierDuration;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PerpetualArrayPhaseTest {
    static AbilityData data(String suffix) throws Exception {
        return JsonUtils.loadToList(Path.of("resources/BinOutput/Ability/Temp/MonsterAbilities/ConfigAbility_Monster_Apparatus_Perpetual.json"),
                AbilityConfigData.class).stream().map(row -> row.Default)
                .filter(row -> row.abilityName.equals("Monster_Apparatus_Perpetual_" + suffix))
                .findFirst().orElseThrow();
    }

    private void change(AbilityTestFixture f, Ability ability, String name, int id, boolean add) throws Exception {
        var change = AbilityMetaModifierChange.newBuilder()
                .setAction(add ? ModifierAction.MODIFIER_ACTION_ADDED : ModifierAction.MODIFIER_ACTION_REMOVED)
                .setParentAbilityName(AbilityString.newBuilder().setStr(ability.getData().abilityName))
                .setModifierLocalId(new ArrayList<>(ability.getData().modifiers.keySet()).indexOf(name));
        f.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder()
                .setEntityId(f.entity.getId())
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedAbilityId(1).setInstancedModifierId(id))
                .setAbilityData(change.build().toByteString()).build());
    }

    private void nativeChange(AbilityTestFixture f, Ability ability, String name, int id,
                              boolean add, Integer parent, boolean invalidParent) throws Exception {
        nativeChange(f, ability, name, id, add, parent, invalidParent, ability.getOwner().getId());
    }

    private void nativeChange(AbilityTestFixture f, Ability ability, String name, int id,
                              boolean add, Integer parent, boolean invalidParent, int parentOwner) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var writer = CodedOutputStream.newInstance(bytes);
        if (parent != null) {
            var attachment = new ByteArrayOutputStream();
            var child = CodedOutputStream.newInstance(attachment);
            if (invalidParent) child.writeBool(1, true);
            child.writeUInt32(2, parentOwner);
            child.writeUInt32(3, parent);
            child.flush();
            writer.writeByteArray(1, attachment.toByteArray());
        }
        if (!add) writer.writeEnum(8, 1);
        writer.writeInt32(11, new ArrayList<>(ability.getData().modifiers.keySet()).indexOf(name));
        writer.flush();
        f.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder()
                .setEntityId(ability.getOwner().getId())
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedAbilityId(add ? 1 : 0).setInstancedModifierId(id))
                .setAbilityData(ByteString.copyFrom(bytes.toByteArray())).build());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeFirstSplitTransitionReleasesAllProtectionAfterEnhancedMinionDies(boolean tickWhileClientTempIsLive) throws Exception {
        try (var f = new AbilityTestFixture()) {
            var now = new java.util.concurrent.atomic.AtomicLong();
            var boss = new AbilityTestFixture.TestEntity(f.scene) {
                final AbilityModifierDuration duration = new AbilityModifierDuration(this, now::get);
                @Override public AbilityModifierDuration getAbilityModifierDuration() { return duration; }
            };
            boss.setId(43);
            f.scene.getEntities().put(boss.getId(), boss);
            boss.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 1000);
            boss.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 300);
            var ability = new Ability(data("ShaderState"), boss, f.player);
            boss.getInstancedAbilities().add(ability);
            boss.getAbilityGlobalValueWatchers().refresh();
            nativeChange(f, ability, "UNIQUE_Monster_Apparatus_Perpetual_FirstSplitManager", 3, true, null, false);
            nativeChange(f, ability, "Monster_Apparatus_Perpetual_FirstSplit_Normal", 4, true, null, false);
            assertEquals(0.3f, AbilityCombatState.read(boss).hpThreshold());

            // 2026-10-23 live order: native temporary protection arrives BEFORE the
            // global-value binding's onRemoved creates its server-side counterpart.
            nativeChange(f, ability, "Split_Temp_LockHP", 12, true, null, false);
            nativeChange(f, ability, "Monster_Apparatus_Perpetual_FirstSplit_Normal", 4, false, null, false);
            nativeChange(f, ability, "UNIQUE_Monster_Apparatus_Perpetual_FirstSplitManager", 3, false, null, false);
            nativeChange(f, ability, "UNIQUE_Monster_Apparatus_Perpetual_SecondSplitManager", 3, true, null, false);
            AbilityGlobalValues.copy(ability, boss, "_TRIGGER_TIMES", 1);
            nativeChange(f, ability, "Apparatus_Perpetual_LockHP", 4, true, null, false);
            nativeChange(f, ability, "DenyLockOn", 5, true, 4, false, 0);
            nativeChange(f, ability, "GhostToEmeny", 14, true, 4, false, 0);
            nativeChange(f, ability, "LockHP", 16, true, 4, false, 0);
            assertTrue(boss.isLockHP(), "the split stage still protects the boss");
            assertFalse(AbilityCombatState.read(boss).limbo());

            if (tickWhileClientTempIsLive) boss.onTick(0);
            now.set(1_000_000_000L);
            nativeChange(f, ability, "Split_Temp_LockHP", 12, false, null, false);
            boss.onTick(0);
            assertFalse(ability.getModifiers().containsKey("Split_Temp_LockHP"),
                    "the half-second temporary lock must expire even when a client instance masked it");
            assertTrue(boss.isLockHP(), "the real split-stage lock must remain until the phase ends");
            boss.damage(50);
            assertEquals(300, boss.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));

            nativeChange(f, ability, "Apparatus_Perpetual_ShaderState_WeakStart", 8, true, null, false);
            nativeChange(f, ability, "LockHP", 16, false, null, false);
            nativeChange(f, ability, "GhostToEmeny", 14, false, null, false);
            nativeChange(f, ability, "DenyLockOn", 5, false, null, false);
            nativeChange(f, ability, "Apparatus_Perpetual_LockHP", 4, false, null, false);
            nativeChange(f, ability, "Apparatus_Perpetual_Weak", 2, true, null, false);
            assertEquals(new AbilityCombatState(false, false, false, 0), AbilityCombatState.read(boss));
            boss.damage(50);
            assertEquals(250, boss.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP), "hits during weakness reduce HP again");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeAttachedChildrenFollowThePhaseWithoutRepeatingTheParentName(boolean invalidParent) throws Exception {
        try (var f = new AbilityTestFixture()) {
            var ability = new Ability(data("ShaderState"), f.entity, f.player);
            f.entity.getInstancedAbilities().add(ability);
            nativeChange(f, ability, "Apparatus_Perpetual_LockHP", 71, true, null, false);
            nativeChange(f, ability, "LockHP", 72, true, 71, invalidParent);
            assertTrue(f.entity.isLockHP());
            nativeChange(f, ability, "Apparatus_Perpetual_LockHP", 71, false, null, false);
            assertEquals(invalidParent, f.entity.isLockHP(),
                    "only a valid native parent reference owns the child's lifetime");
            nativeChange(f, ability, "LockHP", 72, false, null, false);
            assertFalse(f.entity.isLockHP());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void endingSplitPhaseRemovesItsAttachedLockHp(boolean clientAlsoConfirmsChild) throws Exception {
        try (var f = new AbilityTestFixture()) {
            var ability = new Ability(data("ShaderState"), f.entity, f.player);
            f.entity.getInstancedAbilities().add(ability);
            change(f, ability, "Apparatus_Perpetual_LockHP", 71, true);
            if (clientAlsoConfirmsChild) change(f, ability, "LockHP", 72, true);
            assertTrue(f.entity.isLockHP(), "split phase must protect the boss");
            change(f, ability, "Apparatus_Perpetual_LockHP", 71, false);
            assertFalse(f.entity.isLockHP(), "parent removal must also remove its attached LockHP child");
            change(f, ability, "Apparatus_Perpetual_LockHP", 73, true);
            change(f, ability, "Apparatus_Perpetual_LockHP", 71, false);
            assertTrue(f.entity.isLockHP(), "a duplicate old removal must not end the next phase");
            change(f, ability, "Apparatus_Perpetual_LockHP", 73, false);
            assertFalse(f.entity.isLockHP());
        }
    }

    @Test
    void splitSummonsHaveDistinctResourceTagsAndTheBossAsOwner() throws Exception {
        try (var f = new AbilityTestFixture()) {
            GameData.setConfigGlobalCombat(JsonUtils.decode(
                    "{defaultAbilities:{nonHumanoidMoveAbilities:[],defaultMPLevelAbilities:[],levelElementAbilities:[]}}",
                    emu.grasscutter.data.binout.config.ConfigGlobalCombat.class));
            if (emu.grasscutter.scripts.ScriptLoader.getEngine() == null)
                emu.grasscutter.scripts.ScriptLoader.init();
            var old = new HashMap<Integer, MonsterData>();
            var split = data("Split");
            var bossData = JsonUtils.decode("{id:24021101,type:'MONSTER_BOSS',equips:[],affix:[],propGrowCurves:[],hpBase:100}", MonsterData.class);
            var boss = new EntityMonster(f.scene, bossData, new Position(), new Position(), 95);
            f.scene.getEntities().put(boss.getId(), boss);
            var ability = new Ability(split, boss, f.player);
            split.initialize();
            boss.getInstancedAbilities().add(ability);
            try {
                for (int position = 1; position <= 4; position++) {
                    var name = "Position" + position + (position == 3 ? "_Enhanced" : "_Normal");
                    var action = java.util.Arrays.stream(split.modifiers.get(name).onRemoved)
                            .filter(a -> a.type == emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction.Type.Summon)
                            .findFirst().orElseThrow();
                    old.put(action.monsterID, GameData.getMonsterDataMap().put(action.monsterID,
                            JsonUtils.decode("{id:" + action.monsterID + ",type:'MONSTER_ORDINARY',equips:[],affix:[],propGrowCurves:[],hpBase:100}", MonsterData.class)));
                    // Native GEPFCBAPAMC writer: position tag 0x0a, rotation tag 0x22.
                    // Encode independently of the server schema so wrong tags cannot self-validate.
                    var bytes = new ByteArrayOutputStream();
                    var writer = CodedOutputStream.newInstance(bytes);
                    writer.writeMessage(1, Vector.newBuilder().setX(position * 7).setY(-0.102f).setZ(7).build());
                    writer.writeMessage(4, Vector.newBuilder().setY(90).build());
                    writer.flush();
                    var payload = ByteString.copyFrom(bytes.toByteArray());
                    int local = split.localIdToAction.entrySet().stream()
                            .filter(entry -> entry.getValue() == action).mapToInt(java.util.Map.Entry::getKey)
                            .findFirst().orElseThrow();
                    assertEquals(new int[] {33315, 33347, 98899, 33411}[position - 1], local);
                    f.player.getAbilityManager().onAbilityInvoke(AbilityInvokeEntry.newBuilder()
                            .setEntityId(boss.getId())
                            .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_ACTION_SUMMON)
                            .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedAbilityId(1).setLocalId(local))
                            .setAbilityData(payload).build());
                    var child = boss.getSummonTagMap().get(action.monsterID);
                    assertNotNull(child, "resource summon tag must not collapse to zero");
                    assertEquals(boss.getId(), child.getOwnerEntityId());
                    assertEquals(action.monsterID, child.getSummonedTag());
                    assertEquals(95, child.getLevel());
                    assertEquals(position * 7, child.getPosition().getX());
                    assertEquals(-0.102f, child.getPosition().getY());
                    assertEquals(90, child.getRotation().getY());
                }
                assertEquals(4, boss.getSummonTagMap().values().stream().filter(java.util.Objects::nonNull).count());
            } finally {
                old.forEach((id, previous) -> {
                    if (previous == null) GameData.getMonsterDataMap().remove(id);
                    else GameData.getMonsterDataMap().put(id, previous);
                });
            }
        }
    }
}
