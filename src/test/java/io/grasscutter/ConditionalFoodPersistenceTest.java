package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.systems.InventorySystem;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Conditional healing must persist the resource outcome together with its real inventory debit. */
class ConditionalFoodPersistenceTest extends FoodUsePaymentPersistenceTest {
    @BeforeAll
    static void conditionalResources() throws Exception {
        ConditionalFoodEffectsTest.resources();
    }

    @ParameterizedTest
    @ValueSource(ints = {10000096, 10000103})
    void actualItemPersistsTheSelectedBranchAndOnePaidConsumption(int avatarId) throws Exception {
        AbilityFeatureTagPredicateTest.groups.forEach(
                row -> resource(GameData.getFeatureTagGroupDataMap(), row.getId(), row));
        var data =
                AbilityFeatureTagPredicateTest.avatars.stream()
                        .filter(row -> row.getId() == avatarId)
                        .findFirst()
                        .orElseThrow();
        resource(GameData.getAvatarDataMap(), avatarId, data);
        resource(
                GameData.getItemDataMap(),
                ConditionalFoodEffectsTest.food.getId(),
                ConditionalFoodEffectsTest.food);
        target.getAvatars().getAvatars().remove(avatar.getAvatarId());
        replaceField(Avatar.class, avatar, "avatarId", avatarId);
        target.getAvatars().getAvatars().put(avatarId, avatar);
        store.save(avatar);
        var item = new GameItem(ConditionalFoodEffectsTest.food, 1);
        item.setOwner(target);
        target.getInventory().getItems().put(item.getGuid(), item);
        target.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(item);
        emu.grasscutter.database.DatabaseHelper.saveGameSync(item);
        assertSame(
                item,
                new InventorySystem(player.server)
                        .useItem(target, avatar.getGuid(), item.getGuid(), 1, 0, false));
        var restored = read();
        assertNull(restored.getPendingItemUse());
        assertEquals(0, restored.getInventory().getItemCountById(item.getItemId()));
        assertEquals(1, restored.getInventory().getItemCountById(108033));
        assertEquals(
                avatarId == 10000096 ? 7933 : 6300,
                savedAvatar().get("currentHp", Number.class).doubleValue(),
                0.01);
        assertEquals(4050, savedAvatar().getInteger("satiation"));
        var consumed = store.find(GameItem.class).field("id").equal(item.getObjectId()).first();
        assertNotNull(consumed);
        assertTrue(consumed.isDeleted());
        assertEquals(0, consumed.getCount());
    }
}
