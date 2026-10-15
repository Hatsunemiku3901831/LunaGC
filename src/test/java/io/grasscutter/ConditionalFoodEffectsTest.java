package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionHealHP;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;

class ConditionalFoodEffectsTest {
    FoodBuffEffectsTest env;
    static ItemData food;

    @BeforeAll
    static void resources() throws Exception {
        FoodBuffEffectsTest.resources();
        AbilityFeatureTagPredicateTest.resources();
        food =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(row -> row.getId() == 108983)
                        .findFirst()
                        .orElseThrow();
        food.onLoad();
    }

    @BeforeEach
    void setup() throws Exception {
        env = new FoodBuffEffectsTest();
        env.setup();
        var resources = env.env.environment;
        AbilityFeatureTagPredicateTest.groups.forEach(
                row -> resources.put(GameData.getFeatureTagGroupDataMap(), row.getId(), row));
        AbilityFeatureTagPredicateTest.avatars.forEach(
                row -> resources.put(GameData.getAvatarDataMap(), row.getId(), row));
        AbilityFeatureTagPredicateTest.depots.forEach(
                row -> resources.put(GameData.getAvatarSkillDepotDataMap(), row.getId(), row));
        resources.put(GameData.getItemDataMap(), food.getId(), food);
    }

    @AfterEach
    void close() {
        env.close();
    }

    @Test
    void actualConditionalRecipeHealsAnOrdinaryRecipientUsingItsBaseBranch() {
        assertTrue(env.use(108102, "", env.first));
        assertEquals(6300, env.hp(env.first), 0.01);
    }

    EntityAvatar member(int id) {
        long guid = 100000L + id;
        var entity =
                new EntityAvatar(
                        env.env.fixture.scene,
                        new Avatar() {
                            @Override
                            public int getAvatarId() {
                                return id;
                            }

                            @Override
                            public long getGuid() {
                                return guid;
                            }

                            @Override
                            public Player getPlayer() {
                                return env.env.fixture.player;
                            }

                            @Override
                            public void save() {}
                        }) {
                    @Override
                    public void initAbilities() {}
                };
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 10000);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1000);
        env.env.fixture.player.getTeamManager().getActiveTeam().add(entity);
        env.env.fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    @Test
    void everyRecipientListedInTheActualRecipeReceivesItsExtraHealingOffStage() {
        var recipe = GameData.getAbilityDataMap().get("ReHP_Ins&Con");
        var root = recipe.modifiers.get("SERVER_ReHP_Ins&Con_Modifier_lv53_3_Extra");
        var alternatives = (List<?>) root.onAdded[0].targetPredicates.get(0).get("predicates");
        assertEquals(17, alternatives.size());
        for (var alternative : alternatives) {
            var tag =
                    ((Number) ((List<?>) ((Map<?, ?>) alternative).get("BFLILDJLGHP")).get(0))
                            .longValue();
            var data =
                    AbilityFeatureTagPredicateTest.avatars.stream()
                            .filter(
                                    row -> {
                                        var group =
                                                GameData.getFeatureTagGroupDataMap()
                                                        .get(row.getFeatureTagGroupID());
                                        return group != null
                                                && Arrays.stream(group.getTagIds())
                                                        .anyMatch(value -> value == tag);
                                    })
                            .findFirst()
                            .orElseThrow();
            var recipient = member(data.getId());
            assertTrue(env.use(108102, "", recipient), "Avatar " + data.getId());
            assertEquals(7933, env.hp(recipient), 0.01, "Avatar " + data.getId());
            assertEquals(1000, env.hp(env.first));
        }
    }

    @Test
    void anEligibleCurrentAvatarDoesNotGrantTheBonusToAnotherRecipient() {
        var current = member(10000096);
        var party = env.env.fixture.player.getTeamManager().getActiveTeam();
        party.clear();
        party.add(current);
        party.add(env.first);
        assertTrue(env.use(108102, "", env.first));
        assertEquals(6300, env.hp(env.first), 0.01);
        assertEquals(1000, env.hp(current));
    }

    @Test
    void theActualItemPaysOneUnitAndItsSatiationForTheBaseOutcome() throws Exception {
        var item = env.stock(food.getId());
        var inventory =
                new InventorySystem(
                        env.env.fixture.scene.getWorld().getServer(),
                        System::currentTimeMillis,
                        ignored -> {});
        assertSame(
                item,
                inventory.useItem(env.env.fixture.player, 50001, item.getGuid(), 1, 0, false));
        assertEquals(1, item.getCount());
        assertEquals(4050, env.first.getAvatar().getSatiation()); // (27 + 135000 / 10000) * 100.
        assertEquals(6300, env.hp(env.first), 0.01);
        assertNull(env.env.fixture.player.getPendingItemUse());
    }

    @Test
    void lateNativeHealingCannotRepeatEitherConditionalBranchAfterTheBuffExpires() {
        var recipient = member(10000096);
        assertTrue(env.use(108102, "", recipient));
        env.tick(1);
        var recipe = GameData.getAbilityDataMap().get("ReHP_Ins&Con");
        var condition =
                recipe.modifiers.get("SERVER_ReHP_Ins&Con_Modifier_lv53_3_Extra").onAdded[0];
        var nativeAbility = new Ability(recipe, recipient, env.env.fixture.player);
        for (var action :
                java.util.stream.Stream.concat(
                                Arrays.stream(condition.successActions),
                                Arrays.stream(condition.failActions))
                        .toList()) {
            assertTrue(env.manager.ownsHealing(nativeAbility, action));
            new ActionHealHP().execute(nativeAbility, action, ByteString.EMPTY, recipient);
        }
        assertEquals(7933, env.hp(recipient), 0.01);
    }
}
