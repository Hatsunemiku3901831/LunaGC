package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.excels.BuffData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerBuffManager;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.props.ItemUseTarget;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Real food recipes must heal their recipient once and keep their configured cadence. */
class FoodBuffEffectsTest {
    static List<BuffData> buffs;
    static List<AbilityData> abilities;
    static List<ItemData> foods;
    PhlogistonMutationTest env;
    PlayerBuffManager manager;
    EntityAvatar first;
    final AtomicLong now = new AtomicLong(100000);

    @BeforeAll
    static void resources() throws Exception {
        buffs =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/BuffExcelConfigData.json"),
                                BuffData.class)
                        .stream()
                        .filter(
                                b ->
                                        Set.of(100304, 105203, 200102, 106301, 108102, 400102)
                                                .contains(b.getId()))
                        .toList();
        buffs.forEach(BuffData::onLoad);
        abilities =
                JsonUtils.loadToList(
                                Path.of("resources/BinOutput/Ability/Temp/ItemAbilities.json"),
                                AbilityConfigData.class)
                        .stream()
                        .map(x -> x.Default)
                        .filter(
                                d ->
                                        Set.of("ReHP_Ins&Con", "revive_buff", "ReHP_SpecialEffects")
                                                .contains(d.abilityName))
                        .toList();
        assertEquals(6, buffs.size());
        assertEquals(3, abilities.size());
        foods =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(d -> Set.of(108033, 108511, 108005).contains(d.getId()))
                        .toList();
        foods.forEach(ItemData::onLoad);
        assertEquals(3, foods.size());
    }

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMutationTest();
        env.setup();
        first = env.caster;
        first.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 10000);
        first.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1000);
        var guid = Avatar.class.getDeclaredField("guid");
        guid.setAccessible(true);
        guid.setLong(first.getAvatar(), 50001);
        buffs.forEach(b -> env.environment.put(GameData.getBuffDataMap(), b.getId(), b));
        abilities.forEach(d -> env.environment.put(GameData.getAbilityDataMap(), d.abilityName, d));
        foods.forEach(d -> env.environment.put(GameData.getItemDataMap(), d.getId(), d));
        manager = new PlayerBuffManager(env.fixture.player, now::get);
        var field = Player.class.getDeclaredField("buffManager");
        field.setAccessible(true);
        field.set(env.fixture.player, manager);
    }

    @AfterEach
    void close() {
        env.close();
    }

    boolean use(int id, String duration, EntityAvatar target) {
        var params =
                new UseItemParams(
                        env.fixture.player,
                        ItemUseTarget.ITEM_USE_TARGET_SPECIFY_ALIVE_AVATAR,
                        target.getAvatar(),
                        1,
                        0,
                        false);
        return new ItemUseAddServerBuff(new String[] {String.valueOf(id), duration})
                .useItem(params);
    }

    void tick(double seconds) {
        now.set(100000 + (long) (seconds * 1000));
        manager.onTick();
    }

    float hp(EntityAvatar entity) {
        return entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    EntityAvatar second() {
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return env.fixture.player;
                    }

                    @Override
                    public long getGuid() {
                        return 50002;
                    }

                    @Override
                    public int getAvatarId() {
                        return 10000114;
                    }

                    @Override
                    public void save() {}
                };
        var entity =
                new EntityAvatar(env.fixture.scene, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        env.fixture.player.getTeamManager().getActiveTeam().add(entity);
        env.fixture.scene.getEntities().put(entity.getId(), entity);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 10000);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1000);
        return entity;
    }

    @Test
    void instantRecipeIncludesTargetMaxHpRatio() {
        assertTrue(use(105203, "", first));
        assertEquals(4900, hp(first)); // 1000 + 1500 + 24% of 10000.
    }

    emu.grasscutter.game.inventory.GameItem stock(int id) throws Exception {
        var player = env.fixture.player;
        var field = player.getAvatars().getClass().getDeclaredField("avatarsGuid");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var avatars =
                (it.unimi.dsi.fastutil.longs.Long2ObjectMap<Avatar>) field.get(player.getAvatars());
        avatars.put(first.getAvatar().getGuid(), first.getAvatar());
        var item =
                new emu.grasscutter.game.inventory.GameItem(GameData.getItemDataMap().get(id), 2) {
                    @Override
                    public void save() {}
                };
        item.setOwner(player);
        player.getInventory().getItems().put(item.getGuid(), item);
        player.getInventory()
                .getInventoryTab(emu.grasscutter.game.inventory.ItemType.ITEM_MATERIAL)
                .onAddItem(item);
        return item;
    }

    @Test
    void realInventoryUseConsumesOneFoodAndSchedulesItsPulses() throws Exception {
        var item = stock(108511);
        var inventory =
                new emu.grasscutter.game.systems.InventorySystem(
                        env.fixture.scene.getWorld().getServer());
        assertSame(item, inventory.useItem(env.fixture.player, 50001, item.getGuid(), 1, 0, false));
        assertEquals(1, item.getCount());
        assertEquals(4400, hp(first));
        tick(5);
        assertEquals(5380, hp(first));
    }

    @Test
    void realRevivalFoodRevivesThenExecutesItsHealingRecipe() throws Exception {
        var item = stock(108005);
        first.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        first.getAvatar().setCurrentHp(0);
        var dead =
                emu.grasscutter.game.entity.GameEntity.class.getDeclaredMethod(
                        "setDead", boolean.class);
        dead.setAccessible(true);
        dead.invoke(first, true);
        var inventory =
                new emu.grasscutter.game.systems.InventorySystem(
                        env.fixture.scene.getWorld().getServer());
        assertSame(item, inventory.useItem(env.fixture.player, 50001, item.getGuid(), 1, 0, false));
        assertTrue(first.isAlive());
        assertEquals(101, hp(first));
        assertEquals(1, item.getCount());
    }

    @Test
    void randomRecipeExecutesOnlyOneConfiguredHealingOutcome() {
        for (int i = 0; i < 30; i++) {
            first.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1000);
            assertTrue(use(106301, "", first));
            float actual = hp(first);
            assertTrue(
                    java.util.stream.DoubleStream.of(3850, 4600, 5300)
                            .anyMatch(x -> Math.abs(x - actual) < 0.01),
                    "Unexpected random food healing: " + actual);
        }
    }

    @Test
    void unpairedConditionalRecipeIsRejectedBeforeApplyingAnEffect() {
        assertFalse(use(108102, "", first));
        assertEquals(1000, hp(first));
        assertFalse(manager.hasBuff(10));
    }

    @Test
    void nestedSpecialEffectIsNotSilentlyAcceptedAsAnEmptyBuff() {
        assertFalse(use(400102, "", first));
        assertEquals(1000, hp(first));
    }

    @Test
    void recipeAmountsDoNotAcquireCharacterSkillHealingBonuses() {
        first.setFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD, 0.5f);
        first.setFightProperty(FightProperty.FIGHT_PROP_HEALED_ADD, 0.5f);
        assertTrue(use(105203, "", first));
        assertEquals(4900, hp(first));
    }

    @Test
    void continuousRecipeHasInitialRatioAndFiveSecondPulses() {
        assertTrue(use(100304, "31", first));
        assertEquals(4400, hp(first));
        tick(4.999);
        assertEquals(4400, hp(first));
        tick(5);
        assertEquals(5380, hp(first));
        tick(5);
        assertEquals(5380, hp(first));
        tick(10);
        assertEquals(6360, hp(first));
    }

    @Test
    void delayedTickCoalescesMissedPulses() {
        use(100304, "31", first);
        float initial = hp(first);
        tick(14);
        assertEquals(initial + 980, hp(first));
        tick(14.5);
        assertEquals(initial + 980, hp(first));
        tick(15);
        assertEquals(initial + 1960, hp(first));
    }

    @Test
    void expirationStopsHealing() {
        use(100304, "31", first);
        tick(32);
        assertFalse(manager.hasBuff(10));
        first.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1000);
        tick(35);
        assertEquals(1000, hp(first));
    }

    @Test
    void explicitRemovalStopsHealing() {
        use(100304, "31", first);
        assertTrue(manager.removeBuff(10));
        first.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1000);
        tick(5);
        assertEquals(1000, hp(first));
    }

    @Test
    void replacementRestartsOnlyThatRecipientsCadence() {
        use(100304, "31", first);
        tick(4);
        first.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1000);
        use(100304, "31", first);
        float refreshed = hp(first);
        tick(5);
        assertEquals(refreshed, hp(first));
        tick(9);
        assertEquals(refreshed + 980, hp(first));
    }

    @Test
    void twoRecipientsHaveIndependentInstancesInTheSameFoodGroup() {
        var other = second();
        use(100304, "31", first);
        tick(2);
        use(100304, "31", other);
        float initial = hp(first), otherInitial = hp(other);
        tick(5);
        assertEquals(initial + 980, hp(first));
        assertEquals(otherInitial, hp(other));
        tick(7);
        assertEquals(otherInitial + 980, hp(other));
    }

    @Test
    void notificationNamesOnlyTheFoodRecipient() throws Exception {
        second();
        use(100304, "31", first);
        var packet =
                env.fixture.player.packets.stream()
                        .filter(
                                p ->
                                        p.getOpcode()
                                                == emu.grasscutter.net.packet.PacketOpcodes
                                                        .ServerBuffChangeNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        var message =
                ServerBuffChangeNotifyOuterClass.ServerBuffChangeNotify.parseFrom(packet.getData());
        assertEquals(List.of(50001L), message.getAvatarGuidListList());
    }

    @Test
    void absentRecipientCannotTurnHealingFoodIntoAPartyBuff() {
        assertFalse(manager.addBuff(100304, 31, null));
        assertFalse(manager.hasBuff(10));
    }

    @Test
    void invalidDurationCannotApplyTheInitialHeal() {
        assertFalse(manager.addBuff(105203, Float.NaN, first.getAvatar()));
        assertEquals(1000, hp(first));
    }

    @Test
    void fractionalResourceDurationIsNotTruncatedToZero() {
        assertTrue(manager.addBuff(105203, -1, first.getAvatar()));
        tick(0.001);
        assertTrue(manager.hasBuff(10));
        tick(0.011);
        assertFalse(manager.hasBuff(10));
    }

    @Test
    void omittedDurationUsesTheResourceDuration() {
        assertTrue(use(105203, "", first));
        assertTrue(manager.hasBuff(10));
    }

    @Test
    void clientCallbackCannotApplyAnAlreadyExecutedFoodHealAgain() throws Exception {
        use(105203, "", first);
        float original = hp(first);
        clientHeal(105203, false);
        assertEquals(original, hp(first));
    }

    @Test
    void lateClientPulseAfterRemovalCannotRecreateFoodHealing() throws Exception {
        use(100304, "31", first);
        manager.clearBuffs();
        float original = hp(first);
        clientHeal(100304, true);
        assertEquals(original, hp(first));
    }

    @Test
    void queuedClientHealIsRecheckedAfterFoodBecomesServerOwned() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(4);
        var release = new java.util.concurrent.CountDownLatch(1);
        var jobs = new ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < 4; i++)
            jobs.add(
                    emu.grasscutter.game.ability.AbilityManager.eventExecutor.submit(
                            () -> {
                                entered.countDown();
                                release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                                return null;
                            }));
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            enqueueClientHeal(105203, false);
            use(105203, "", first);
        } finally {
            release.countDown();
        }
        for (var job : jobs) job.get(6, java.util.concurrent.TimeUnit.SECONDS);
        env.drain();
        assertEquals(4900, hp(first));
    }

    @Test
    void clientModifierAttachmentCannotRepeatTheFoodOnAdded() throws Exception {
        use(105203, "", first);
        float original = hp(first);
        var buff = GameData.getBuffDataMap().get(105203);
        var data = GameData.getAbilityData(buff.getAbilityName());
        var ability = new Ability(data, first, env.fixture.player);
        first.getInstancedAbilities().add(ability);
        var message =
                AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange.newBuilder()
                        .setAction(ModifierActionOuterClass.ModifierAction.MODIFIER_ACTION_ADDED)
                        .setParentAbilityName(
                                AbilityStringOuterClass.AbilityString.newBuilder()
                                        .setStr(data.abilityName))
                        .setModifierLocalId(
                                new ArrayList<>(data.modifiers.keySet())
                                        .indexOf(buff.getModifierName()));
        env.fixture
                .player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                                .setEntityId(first.getId())
                                .setArgumentType(
                                        AbilityInvokeArgumentOuterClass.AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                .setHead(
                                        AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead
                                                .newBuilder()
                                                .setTargetId(first.getId())
                                                .setInstancedAbilityId(
                                                        first.getInstancedAbilities()
                                                                        .indexOf(ability)
                                                                + 1)
                                                .setInstancedModifierId(1234))
                                .setAbilityData(message.build().toByteString())
                                .build());
        env.drain();
        assertEquals(original, hp(first));
    }

    void clientHeal(int id, boolean periodic) throws Exception {
        enqueueClientHeal(id, periodic);
        env.drain();
    }

    void enqueueClientHeal(int id, boolean periodic) throws Exception {
        var buff = GameData.getBuffDataMap().get(id);
        var data = GameData.getAbilityData(buff.getAbilityName());
        var modifier = data.modifiers.get(buff.getModifierName());
        var action =
                periodic
                        ? modifier.onThinkInterval[0]
                        : modifier.onAdded[modifier.onAdded.length - 1];
        var ability = new Ability(data, first, env.fixture.player);
        first.getInstancedAbilities().add(ability);
        int local =
                data.localIdToAction.entrySet().stream()
                        .filter(e -> e.getValue() == action)
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        env.fixture
                .player
                .getAbilityManager()
                .handleServerInvoke(
                        AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                                .setEntityId(first.getId())
                                .setHead(
                                        AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead
                                                .newBuilder()
                                                .setInstancedAbilityId(
                                                        first.getInstancedAbilities()
                                                                        .indexOf(ability)
                                                                + 1)
                                                .setTargetId(first.getId())
                                                .setLocalId(local))
                                .build());
    }
}
