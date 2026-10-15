package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.systems.InventorySystem;

import org.bson.Document;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;

/**
 * Real food, Avatar and GameItem documents on the isolated Mongo, without a fake storage writer.
 */
class FoodUsePaymentPersistenceTest extends WidgetPersistenceTest {
    final List<Runnable> resourceRestores = new ArrayList<>();
    Player target;
    Avatar avatar;
    GameItem food;

    @BeforeAll
    static void resources() throws Exception {
        FoodBuffEffectsTest.resources();
    }

    <K, V> void resource(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        resourceRestores.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @BeforeEach
    void actualFood() throws Exception {
        FoodBuffEffectsTest.buffs.forEach(x -> resource(GameData.getBuffDataMap(), x.getId(), x));
        FoodBuffEffectsTest.abilities.forEach(
                x -> resource(GameData.getAbilityDataMap(), x.abilityName, x));
        FoodBuffEffectsTest.foods.forEach(x -> resource(GameData.getItemDataMap(), x.getId(), x));
        target = actual();
        store.getMapper().map(Avatar.class);
        avatar = new Avatar();
        replaceField(Avatar.class, avatar, "avatarId", 10000999);
        avatar.setOwner(target);
        avatar.setCurrentHp(1000);
        var entity =
                new EntityAvatar(target.getScene(), avatar) {
                    @Override
                    public void initAbilities() {}
                };
        target.getTeamManager().getActiveTeam().add(entity);
        target.getScene().getEntities().put(entity.getId(), entity);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 10000);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1000);
        target.getAvatars().getAvatars().put(avatar.getAvatarId(), avatar);
        var guidField = target.getAvatars().getClass().getDeclaredField("avatarsGuid");
        guidField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var byGuid =
                (it.unimi.dsi.fastutil.longs.Long2ObjectMap<Avatar>)
                        guidField.get(target.getAvatars());
        byGuid.put(avatar.getGuid(), avatar);
        store.save(avatar);
        food = new GameItem(GameData.getItemDataMap().get(108033), 1);
        food.setOwner(target);
        target.getInventory().getItems().put(food.getGuid(), food);
        target.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(food);
        store.save(food);
    }

    @AfterEach
    void restoreFoodResources() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        Collections.reverse(resourceRestores);
        resourceRestores.forEach(Runnable::run);
    }

    Document savedAvatar() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        return store.getDatabase()
                .getCollection("avatars")
                .find(new Document("_id", avatar.getObjectId()))
                .first();
    }

    GameItem use(InventorySystem engine) {
        return engine.useItem(target, avatar.getGuid(), food.getGuid(), 1, 0, false);
    }

    @Test
    void realRecipePersistsTheDebitHealingSatiationAndClearedIntent() throws Exception {
        var id = food.getObjectId();
        assertSame(food, use(new InventorySystem(player.server)));
        var restored = read();
        assertEquals(0, restored.getInventory().getItemCountById(108033));
        assertNull(restored.getPendingItemUse());
        assertEquals(4900, savedAvatar().get("currentHp", Number.class).doubleValue());
        assertEquals(3000, savedAvatar().getInteger("satiation"));
        assertNull(store.find(GameItem.class).field("id").equal(id).first());
    }

    @Test
    void aRejectedEffectRestoresADeletedStackWithItsOriginalGuidAndObjectId() throws Exception {
        replaceField(
                emu.grasscutter.data.excels.ItemData.class,
                food.getItemData(),
                "itemUseActions",
                List.of(new ItemUseAction()));
        long guid = food.getGuid();
        var objectId = food.getObjectId();
        assertNull(use(new InventorySystem(player.server)));
        assertSame(food, target.getInventory().getItemByGuid(guid));
        var restored = read();
        assertEquals(1, restored.getInventory().getItemCountById(108033));
        assertEquals(objectId, restored.getInventory().getItemById(108033).getObjectId());
        assertNull(restored.getPendingItemUse());
        assertEquals(1000, savedAvatar().get("currentHp", Number.class).doubleValue());
        assertEquals(0, savedAvatar().getInteger("satiation"));
    }

    @Test
    void anUnacknowledgedEffectStartHasNoHealingAndPersistsABlockingPaidRecord() throws Exception {
        var engine =
                new InventorySystem(
                        player.server,
                        () -> 100000,
                        value -> {
                            if (value == target
                                    && target.getPendingItemUse() != null
                                    && target.getPendingItemUse().getState() == 4)
                                throw new IllegalStateException(
                                        "Before effect-start acknowledgement");
                            DatabaseHelper.saveGameSync(value);
                        });
        assertNull(use(engine));
        var restored = read();
        assertEquals(0, restored.getInventory().getItemCountById(108033));
        assertEquals("food", restored.getPendingItemUse().getKind());
        assertEquals(2, restored.getPendingItemUse().getState());
        assertEquals(avatar.getAvatarId(), restored.getPendingItemUse().getTargetAvatarId());
        assertEquals(1000, savedAvatar().get("currentHp", Number.class).doubleValue());
        assertNull(
                new InventorySystem(player.server)
                        .useItem(restored, avatar.getGuid(), food.getGuid(), 1, 0, false));
    }

    @Test
    void partialEffectFailureSurvivesColdLoadAndCannotReplayTheMeal() throws Exception {
        var actions = new ArrayList<>(food.getItemData().getItemUseActions());
        actions.add(
                new ItemUseAction() {
                    @Override
                    public boolean useItem(UseItemParams params) {
                        throw new IllegalStateException("After real healing");
                    }
                });
        replaceField(
                emu.grasscutter.data.excels.ItemData.class,
                food.getItemData(),
                "itemUseActions",
                actions);
        assertNull(use(new InventorySystem(player.server)));
        assertEquals(4900, savedAvatar().get("currentHp", Number.class).doubleValue());
        var restored = read();
        assertEquals(0, restored.getInventory().getItemCountById(108033));
        assertEquals(4, restored.getPendingItemUse().getState());
        assertNull(
                new InventorySystem(player.server)
                        .useItem(restored, avatar.getGuid(), food.getGuid(), 1, 0, false));
        assertEquals(4900, savedAvatar().get("currentHp", Number.class).doubleValue());
    }

    @Test
    void healingATrialAvatarNeverRecreatesADurableTrialAvatarRecord() throws Exception {
        replaceField(Avatar.class, avatar, "trialAvatarId", 999);
        store.delete(avatar);
        assertNull(savedAvatar());
        assertSame(food, use(new InventorySystem(player.server)));
        assertEquals(4900, avatar.getAsEntity().getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertNull(
                savedAvatar(),
                "Food acknowledgement must preserve the trial-avatar save exclusion");
        assertNull(read().getPendingItemUse());
    }
}
