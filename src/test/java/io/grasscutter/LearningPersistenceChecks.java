package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.*;

import dev.morphia.Datastore;

import emu.grasscutter.command.*;
import emu.grasscutter.command.commands.ItemUseCommand;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.utils.JsonUtils;

import org.bson.Document;
import org.bson.json.*;
import org.bson.types.ObjectId;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Raw cold reads and business-phase failures shared by the six native learning actions. */
final class LearningPersistenceChecks {
    static boolean unlockNotification(emu.grasscutter.net.packet.BasePacket packet) {
        int op = packet.getOpcode();
        return Set.of(
                        emu.grasscutter.net.packet.PacketOpcodes.CombineFormulaDataNotify,
                        emu.grasscutter.net.packet.PacketOpcodes.ForgeFormulaDataNotify,
                        emu.grasscutter.net.packet.PacketOpcodes.CookRecipeDataNotify,
                        emu.grasscutter.net.packet.PacketOpcodes.UnlockedFurnitureFormulaDataNotify,
                        emu.grasscutter.net.packet.PacketOpcodes.UnlockedFurnitureSuiteDataNotify,
                        emu.grasscutter.net.packet.PacketOpcodes.HomeNewUnlockedBgmIdListNotify,
                        emu.grasscutter.net.packet.PacketOpcodes.HomeAllUnlockedBgmIdListNotify)
                .contains(op);
    }

    static final class MissingUnlockNotification extends InvestigationRewardTest.ClaimSession {
        MissingUnlockNotification(emu.grasscutter.server.game.GameServer server, Player player) {
            super(server, player);
        }

        @Override
        public void send(emu.grasscutter.net.packet.BasePacket packet) {
            if (unlockNotification(packet))
                throw new IllegalStateException("Learning notification unavailable");
            super.send(packet);
        }
    }

    static void commandResume(Player player, Datastore store) {
        var previous = emu.grasscutter.Grasscutter.getPermissionHandler();
        var checks = new ArrayList<String>();
        String op = player.getPendingItemUse().getOperationId();
        String before = profile(store, player).toJson();
        var commands = new CommandMap(false).registerCommand("itemuse", new ItemUseCommand());
        try {
            emu.grasscutter.Grasscutter.setPermissionHandler(
                    new PermissionHandler() {
                        public boolean EnablePermissionCommand() {
                            return true;
                        }

                        public boolean checkPermission(
                                Player sender, Player target, String own, String others) {
                            assertSame(player, target);
                            checks.add(own);
                            checks.add(others);
                            return false;
                        }
                    });
            commands.invoke(null, player, "itemuse resume " + op);
            assertEquals(List.of("server.itemuse", "server.itemuse.others"), checks);
            assertEquals(before, profile(store, player).toJson());
            emu.grasscutter.Grasscutter.setPermissionHandler(new DefaultPermissionHandler());
            commands.invoke(null, player, "itemuse status");
            assertEquals(before, profile(store, player).toJson());
            commands.invoke(null, player, "itemuse resume " + op);
            assertNull(player.getPendingItemUse());
            assertNull(receipt(profile(store, player)));
        } finally {
            emu.grasscutter.Grasscutter.setPermissionHandler(previous);
        }
    }

    static void realAcknowledgementLoss(
            Player player, Datastore store, boolean clearing, AtomicBoolean fired)
            throws Exception {
        var sawIntent = new AtomicBoolean();
        GuardedPlayerTestStore.loadSeededProfile(
                player,
                store,
                () -> {},
                row -> {
                    var pending = row.get("pendingItemUse", Document.class);
                    if (pending != null && "recipe".equals(pending.getString("kind")))
                        sawIntent.set(true);
                    boolean selected =
                            clearing
                                    ? pending == null && sawIntent.get()
                                    : pending != null
                                            && "recipe".equals(pending.getString("kind"))
                                            && pending.getInteger("state", 0) == 2;
                    if (selected && fired.compareAndSet(false, true))
                        throw new IllegalStateException("Lost actual player write acknowledgement");
                });
    }

    static void assertOneUnlockNotification(Player player, boolean bgm) {
        var packets =
                new ArrayList<emu.grasscutter.net.packet.BasePacket>(
                        ((AbilityTestFixture.TestPlayer) player).packets);
        if (player.getSession() instanceof InvestigationRewardTest.ClaimSession session)
            packets.addAll(session.responses);
        var groups =
                packets.stream()
                        .filter(LearningPersistenceChecks::unlockNotification)
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        emu.grasscutter.net.packet.BasePacket::getOpcode,
                                        java.util.stream.Collectors.counting()));
        assertEquals(bgm ? 2 : 1, groups.size());
        assertTrue(groups.values().stream().allMatch(n -> n == 1));
    }

    static Document profile(Datastore ds, Player player) {
        return ds.getMapper()
                .getCollection(Player.class)
                .withDocumentClass(Document.class)
                .withReadPreference(ReadPreference.primary())
                .withReadConcern(ReadConcern.MAJORITY)
                .find(new Document("_id", player.getUid()))
                .first();
    }

    static Document item(Datastore ds, ObjectId id) {
        return ds.getMapper()
                .getCollection(GameItem.class)
                .withDocumentClass(Document.class)
                .withReadPreference(ReadPreference.primary())
                .withReadConcern(ReadConcern.MAJORITY)
                .find(new Document("_id", id))
                .first();
    }

    static ItemUseReceipt receipt(Document row) {
        var pending = row.get("pendingItemUse", Document.class);
        return pending == null
                ? null
                : JsonUtils.decode(
                        pending.toJson(
                                JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()),
                        ItemUseReceipt.class);
    }

    static boolean known(Player player, ItemUseReceipt receipt) {
        int id = receipt.getRecipeTargetId();
        return switch (ItemUseOp.getTypeByValue(receipt.getOperation())) {
            case ITEM_USE_UNLOCK_COMBINE -> player.getUnlockedCombines().contains(id);
            case ITEM_USE_UNLOCK_FORGE -> player.getUnlockedForgingBlueprints().contains(id);
            case ITEM_USE_UNLOCK_COOK_RECIPE -> player.getUnlockedRecipies().containsKey(id);
            case ITEM_USE_UNLOCK_FURNITURE_FORMULA -> player.getUnlockedFurniture().contains(id);
            case ITEM_USE_UNLOCK_FURNITURE_SUITE -> player.getUnlockedFurnitureSuite().contains(id);
            case ITEM_USE_UNLOCK_HOME_BGM -> player.getHome().getUnlockedHomeBgmList().contains(id);
            default -> false;
        };
    }

    static Consumer<Object> failure(
            Player player, String phase, boolean after, AtomicBoolean fired) {
        var observed = new AtomicReference<ItemUseReceipt>();
        return value -> {
            var pending = player.getPendingItemUse();
            if (pending != null && "recipe".equals(pending.getKind())) observed.set(pending);
            var seen = observed.get();
            boolean selected =
                    seen != null
                            && switch (phase) {
                                case "intent" -> value == player
                                        && pending != null
                                        && pending.getState() == 1;
                                case "debit" -> value instanceof GameItem
                                        && pending != null
                                        && pending.getState() == 1;
                                case "paid" -> value == player
                                        && pending != null
                                        && pending.getState() == 2
                                        && !known(player, pending);
                                case "flag" -> pending != null
                                        && pending.getState() == 2
                                        && known(player, pending)
                                        && (value == player || value instanceof GameHome);
                                case "confirmed" -> value == player
                                        && pending != null
                                        && pending.getState() == 3;
                                case "clear" -> value == player && pending == null;
                                default -> throw new IllegalArgumentException(phase);
                            };
            boolean fail = selected && fired.compareAndSet(false, true);
            if (!after && fail) throw new IllegalStateException("Before learning " + phase);
            DatabaseHelper.saveGameSync(value);
            if (after && fail) throw new IllegalStateException("After learning " + phase);
        };
    }

    static GameHome restore(Player player, Datastore store) throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        player.setUid(player.getUid());
        var row = profile(store, player);
        assertNotNull(row);
        player.setPendingItemUse(receipt(row));
        player.getUnlockedCombines().clear();
        player.getUnlockedCombines()
                .addAll(row.getList("unlockedCombines", Integer.class, List.of()));
        player.getUnlockedForgingBlueprints().clear();
        player.getUnlockedForgingBlueprints()
                .addAll(row.getList("unlockedForgingBlueprints", Integer.class, List.of()));
        player.getUnlockedRecipies().clear();
        var recipes = row.get("unlockedRecipies", Document.class);
        if (recipes != null)
            recipes.forEach(
                    (key, value) ->
                            player.getUnlockedRecipies()
                                    .put(Integer.parseInt(key), ((Number) value).intValue()));
        player.getUnlockedFurniture().clear();
        player.getUnlockedFurniture()
                .addAll(row.getList("unlockedFurniture", Integer.class, List.of()));
        player.getUnlockedFurnitureSuite().clear();
        player.getUnlockedFurnitureSuite()
                .addAll(row.getList("unlockedFurnitureSuite", Integer.class, List.of()));
        GuardedPlayerTestStore.loadSeededProfile(player, store);
        for (var item :
                store
                        .find(GameItem.class)
                        .filter(
                                dev.morphia.query.experimental.filters.Filters.eq(
                                        "ownerId", player.getUid()),
                                dev.morphia.query.experimental.filters.Filters.ne("deleted", true),
                                dev.morphia.query.experimental.filters.Filters.gt("count", 0))
                        .stream()
                        .toList()) {
            item.setOwner(player);
            GuardedItemTestStore.bind(
                    item,
                    store,
                    DatabaseManager.getGameDatastore(),
                    true,
                    ignored -> {},
                    ignored -> {});
            player.getInventory().getItems().put(item.getGuid(), item);
            player.getInventory().getInventoryTab(item.getItemType()).onAddItem(item);
        }
        var home =
                store.find(GameHome.class)
                        .filter(
                                dev.morphia.query.experimental.filters.Filters.eq(
                                        "ownerUid", player.getUid()))
                        .first();
        if (home == null)
            home =
                    GameHome.of()
                            .ownerUid(player.getUid())
                            .unlockedHomeBgmList(new HashSet<>())
                            .build();
        home.setPlayer(player);
        return home;
    }

    static void finishOrRetry(Player player, Datastore store, ObjectId sourceId, Runnable retry)
            throws Exception {
        var pending = player.getPendingItemUse();
        if (pending != null)
            new ItemUseRecovery(player).resolve(pending.getOperationId(), "resume");
        else if (!Boolean.TRUE.equals(item(store, sourceId).getBoolean("deleted"))
                && item(store, sourceId).getString("recipeUseOperationId") == null) retry.run();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        assertNull(receipt(profile(store, player)));
        assertNull(player.getPendingItemUse());
        var marker = item(store, sourceId).getString("recipeUseOperationId");
        assertNotNull(marker);
        assertEquals(marker, UUID.fromString(marker).toString());
    }
}
