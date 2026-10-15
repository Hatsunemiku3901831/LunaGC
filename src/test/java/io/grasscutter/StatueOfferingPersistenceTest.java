package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.SotSManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.world.*;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;

/** Actual payment, rewards and current/legacy city codecs using isolated Mongo. */
class StatueOfferingPersistenceTest extends WidgetPersistenceTest {
    final List<Runnable> dataRestores = new ArrayList<>();

    @BeforeAll
    static void resources() throws Exception {
        StatueOfferingTest.resources();
    }

    <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        dataRestores.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @BeforeEach
    void cityResources() {
        StatueOfferingTest.cities.forEach(x -> put(GameData.getCityDataMap(), x.getId(), x));
        StatueOfferingTest.levels.forEach(x -> put(GameData.getCityLevelupDataMap(), x.getId(), x));
        StatueOfferingTest.legacy.forEach(
                x -> put(GameData.getStatuePromoteDataMap(), x.getId(), x));
        StatueOfferingTest.rewards.forEach(x -> put(GameData.getRewardDataMap(), x.getId(), x));
        StatueOfferingTest.items.forEach(x -> put(GameData.getItemDataMap(), x.getId(), x));
    }

    @AfterEach
    void restoreCityResources() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        Collections.reverse(dataRestores);
        dataRestores.forEach(Runnable::run);
    }

    void ownWorld(Player actual) {
        var world = new World(player.server, actual);
        boolean scripts = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        try {
            Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
            actual.setWorld(world);
            actual.setScene(
                    new Scene(world, gson.fromJson("{id:3,type:'SCENE_WORLD'}", SceneData.class)));
        } finally {
            Grasscutter.getConfig().server.game.enableScriptInBigWorld = scripts;
        }
    }

    Player offerPlayer() throws Exception {
        var actual = actual();
        ownWorld(actual);
        actual.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 20);
        actual.setProperty(PlayerProperty.PROP_MAX_STAMINA, 10000);
        return actual;
    }

    void stock(Player actual, int id, int count) {
        var item = new GameItem(GameData.getItemDataMap().get(id), count);
        item.setOwner(actual);
        actual.getInventory().getItems().put(item.getGuid(), item);
        actual.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(item);
        store.save(item);
    }

    @Test
    void defaultDeliveryPersistsTheActualCostCurrencyItemsAndStamina() throws Exception {
        var actual = offerPlayer();
        stock(actual, 107001, 2);
        actual.getSotsManager().levelUpSotS(1, 3, 1);
        var restored = read();
        assertEquals(2, restored.getSotsManager().getCityInfo(1).getLevel());
        assertNull(restored.getSotsManager().getCityInfo(1).getPendingOffer());
        assertEquals(1, restored.getInventory().getItemCountById(107001));
        assertEquals(5, restored.getInventory().getItemCountById(305));
        assertEquals(10, restored.getPrimogems());
        assertEquals(80, restored.getExp());
        assertEquals(10700, restored.getProperty(PlayerProperty.PROP_MAX_STAMINA));
    }

    @Test
    void adminCityLevelPersistsWithoutPaymentOrRewardSideEffects() throws Exception {
        var actual = offerPlayer();
        stock(actual, 107030, 40);
        actual.getSotsManager().getCityInfo(7).setNumCrystal(17);
        var command =
                (emu.grasscutter.command.CommandHandler)
                        Class.forName("emu.grasscutter.command.commands.StatueCommand")
                                .getConstructor()
                                .newInstance();
        command.execute(null, actual, List.of("level", "7", "6"));
        var restored = read();
        assertEquals(6, restored.getSotsManager().getCityInfo(7).getLevel());
        assertEquals(0, restored.getSotsManager().getCityInfo(7).getNumCrystal());
        assertEquals(40, restored.getInventory().getItemCountById(107030));
        assertEquals(0, restored.getPrimogems());
        assertEquals(0, restored.getExp());
        assertEquals(10000, restored.getProperty(PlayerProperty.PROP_MAX_STAMINA));
    }

    @Test
    void incompleteContributionSurvivesReloadAndCompletesWithTheRemainingCost() throws Exception {
        var actual = offerPlayer();
        stock(actual, 107023, 25);
        actual.getSotsManager().levelUpSotS(33, 3, 7);
        var restored = read();
        ownWorld(restored);
        assertEquals(1, restored.getSotsManager().getCityInfo(5).getLevel());
        assertEquals(7, restored.getSotsManager().getCityInfo(5).getNumCrystal());
        assertEquals(18, restored.getInventory().getItemCountById(107023));
        restored.getSotsManager().levelUpSotS(33, 3, 13);
        var completed = read();
        assertEquals(2, completed.getSotsManager().getCityInfo(5).getLevel());
        assertEquals(0, completed.getSotsManager().getCityInfo(5).getNumCrystal());
        assertEquals(5, completed.getInventory().getItemCountById(107023));
        assertEquals(60, completed.getPrimogems());
        assertEquals(1, completed.getInventory().getItemCountById(107025));
    }

    @Test
    void unresolvedPaidReceiptSurvivesReloadAndBlocksAnotherAutomaticGrant() throws Exception {
        var actual = offerPlayer();
        stock(actual, 107001, 1);
        var manager =
                new SotSManager(
                        actual,
                        () -> DatabaseHelper.saveGameSync(actual),
                        items -> {
                            throw new IllegalStateException("Injected interrupted statue reward");
                        },
                        DatabaseHelper::saveGameSync);
        manager.levelUpSotS(1, 3, 1);
        var restored = read();
        ownWorld(restored);
        var receipt = restored.getSotsManager().getCityInfo(1).getPendingOffer();
        assertNotNull(receipt);
        assertEquals(2, receipt.getState());
        assertEquals(1, receipt.getConsumed());
        assertEquals(1, receipt.getLevelBefore());
        assertEquals(2, receipt.getLevelAfter());
        assertEquals(10000, receipt.getStaminaBefore());
        assertEquals(10700, receipt.getStaminaAfter());
        restored.getSotsManager().levelUpSotS(1, 3, 1);
        assertEquals(0, restored.getPrimogems());
        assertEquals(0, restored.getInventory().getItemCountById(107001));
        assertEquals(
                receipt.getOperationId(),
                restored.getSotsManager().getCityInfo(1).getPendingOffer().getOperationId());
    }
}
