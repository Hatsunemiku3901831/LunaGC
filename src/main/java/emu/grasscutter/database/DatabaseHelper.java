package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.MongoWriteException;
import com.mongodb.WriteConcern;
import dev.morphia.DeleteOptions;
import dev.morphia.Datastore;
import dev.morphia.InsertOneOptions;
import dev.morphia.query.*;
import dev.morphia.query.experimental.filters.Filters;
import emu.grasscutter.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.achievement.Achievements;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.activity.musicgame.MusicGameBeatmap;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.friends.Friendship;
import emu.grasscutter.game.gacha.GachaRecord;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.utils.objects.Returnable;
import emu.grasscutter.config.ConfigContainer.Database.Pool;
import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Getter;

public final class DatabaseHelper {
    private static volatile boolean mailCounterInitialized;

    private static final emu.grasscutter.config.ConfigContainer.Database.AsyncWrites queueConfig =
            Grasscutter.getConfig().databaseInfo.asyncWrites;
    @Getter private static final DatabaseWriteQueue eventExecutor = queue("default", queueConfig.defaults);
    @Getter private static final DatabaseWriteQueue eventExecutorAccount = queue("account", queueConfig.accounts);
    @Getter private static final DatabaseWriteQueue eventExecutorItem = queue("item", queueConfig.items);
    @Getter private static final DatabaseWriteQueue eventExecutorGroup = queue("group", queueConfig.groups);
    private static final List<DatabaseWriteQueue> queues =
            List.of(eventExecutor, eventExecutorAccount, eventExecutorItem, eventExecutorGroup);

    private static DatabaseWriteQueue queue(String name, Pool settings) {
        return new DatabaseWriteQueue(name, settings.threads, settings.capacity);
    }

    public static List<DatabaseWriteQueue.Snapshot> queueStatus() {
        return queues.stream().map(DatabaseWriteQueue::snapshot).toList();
    }

    /** Quiescence check for callers that have stopped producing database work. */
    public static boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (var queue : queues) {
            if (!queue.awaitIdle(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())))) return false;
        }
        return true;
    }

    /** Stop every pool together, then drain accepted work without discarding queued writes. */
    public static boolean shutdown() throws InterruptedException {
        queues.forEach(DatabaseWriteQueue::shutdown);
        boolean drained = awaitIdle(Duration.ofSeconds(Math.max(1, queueConfig.shutdownTimeoutSeconds)));
        if (!drained) Grasscutter.getLogger().error("Database shutdown timed out with unfinished writes: {}", queueStatus());
        return drained;
    }

    /**
     * Saves an object on the account datastore.
     *
     * @param object The object to save.
     */
    public static void saveAccountAsync(Object object) {
        var datastore = DatabaseManager.getAccountDatastore();
        eventExecutorAccount.saveSnapshot(object, datastore, () -> {
            synchronized (object) { datastore.save(object); }
        });
    }

    /**
     * Saves an object on the game datastore.
     *
     * @param object The object to save.
     */
    public static void saveGameAsync(Object object) {
        var datastore = DatabaseManager.getGameDatastore();
        var queue = object instanceof GameItem ? eventExecutorItem
                : object instanceof SceneGroupInstance ? eventExecutorGroup
                : object instanceof Account ? eventExecutorAccount : eventExecutor;
        queue.saveSnapshot(object, datastore,
                () -> {
                    synchronized (object) {
                        saveWithRetry(datastore, object);
                    }
                });
    }

    /** Acknowledged writes for economic reservations; errors must reach the caller. */
    public static void saveGameSync(Object object) {
        synchronized (object) {
            if (object instanceof GameItem item) {
                item.getSaveGuard().save(DatabaseManager.getGameDatastore());
                return;
            }
            if (object instanceof Player player) {
                player.getSaveGuard().save(DatabaseManager.getGameDatastore());
                return;
            }
            if (object instanceof PlayerActivityData activity) {
                activity.getSaveGuard().save(DatabaseManager.getGameDatastore());
                return;
            }
            var concern = WriteConcern.MAJORITY.withJournal(true);
            DatabaseManager.getGameDatastore()
                    .save(object, new InsertOneOptions().writeConcern(concern));
        }
    }

    /**
     * Saves on the executor, retrying the two failures that are races rather than real errors.
     *
     * <p>An unguarded save swallows both: a duplicate key means something else inserted the entity
     * first, and a ConcurrentModificationException means a player collection was being mutated on
     * another thread while Morphia walked it - typically during login. Both used to lose the write
     * silently, so progress simply disappeared. Guarded document duplicate keys are conflicts and
     * must never be retried as unguarded replacements.
     */
    private static void saveWithRetry(Datastore datastore, Object object) {
        var name = object.getClass().getSimpleName();
        try {
            saveGameObject(datastore, object);
        } catch (MongoWriteException e) {
            if (object instanceof Player || object instanceof PlayerActivityData || object instanceof GameItem
                    || e.getError() == null || e.getError().getCode() != 11000) {
                throw e;
            }
            // The id is reflected back onto the object by the failed insert, so the retry
            // becomes a replace.
            try {
                datastore.save(object);
            } catch (RuntimeException t) {
                throw new IllegalStateException("Failed to save " + name + " after a duplicate key", t);
            }
        } catch (ConcurrentModificationException e) {
            for (var attempt = 0; attempt < 8; attempt++) {
                try {
                    Thread.sleep(100);
                    saveGameObject(datastore, object);
                    return;
                } catch (ConcurrentModificationException ignored) {
                    // The other thread has not settled yet.
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while saving " + name, interrupted);
                }
            }
            throw new IllegalStateException("Failed to save " + name + " - it kept being modified", e);
        }
    }

    private static void saveGameObject(Datastore datastore, Object object) {
        if (object instanceof Player player) player.getSaveGuard().save(datastore);
        else if (object instanceof PlayerActivityData activity) activity.getSaveGuard().save(datastore);
        else if (object instanceof GameItem item) item.getSaveGuard().save(datastore);
        else datastore.save(object);
    }

    /**
     * Runs a runnable on the event executor. Should be limited to database-related operations.
     *
     * @param runnable The runnable to run.
     */
    public static void asyncOperation(Runnable runnable) {
        DatabaseHelper.eventExecutor.submit(runnable);
    }

    /**
     * Fetches an object asynchronously.
     *
     * @param task The task to run.
     * @return The future.
     */
    public static <T> CompletableFuture<T> fetchAsync(Returnable<T> task) {
        var future = new CompletableFuture<T>();

        // Run the task on the event executor.
        try { DatabaseHelper.eventExecutor.submit(
                () -> {
                    try {
                        future.complete(task.invoke());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
        } catch (RejectedExecutionException exception) {
            future.completeExceptionally(exception);
        }

        return future;
    }

    public static Account createAccount(String username) {
        return createAccountWithUid(username, 0);
    }

    public static Account createAccountWithUid(String username, int reservedUid) {
        return createAccountWithUid(username, reservedUid, null, List.of());
    }

    /** Insert initial credentials and permissions together. The caller supplies the password hash. */
    public static Account createAccountWithUid(String username, int reservedUid, String password,
            List<String> permissions) {
        var game = DatabaseManager.getGameDatastore();
        var accounts = DatabaseManager.getAccountDatastore();
        return AccountCreationService.create(game, accounts, username, reservedUid, password, permissions);
    }

    @Deprecated
    public static Account createAccountWithPassword(String username, String password) {
        return createAccountWithUid(username, 0, password, List.of());
    }

    public static void saveAccount(Account account) {
        DatabaseHelper.saveAccountAsync(account);
    }

    public static Account getAccountByName(String username) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("username", username))
                .first();
    }

    public static Account getAccountByToken(String token) {
        if (token == null) return null;
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("token", token))
                .first();
    }

    public static Account getAccountBySessionKey(String sessionKey) {
        if (sessionKey == null) return null;
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("sessionKey", sessionKey))
                .first();
    }

    public static Account getAccountById(String uid) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("_id", uid))
                .first();
    }

    public static Account getAccountByPlayerId(int playerId) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("reservedPlayerId", playerId))
                .first();
    }

    public static boolean checkIfAccountExists(String name) {
        return DatabaseManager.getAccountDatastore()
                        .find(Account.class)
                        .filter(Filters.eq("username", name))
                        .count()
                > 0;
    }

    public static boolean checkIfAccountExists(int reservedUid) {
        return DatabaseManager.getAccountDatastore()
                        .find(Account.class)
                        .filter(Filters.eq("reservedPlayerId", reservedUid))
                        .count()
                > 0;
    }

    public static synchronized void deleteAccount(Account target) {
        // To delete an account, we need to also delete all the other documents in the database that
        // reference the account.
        // This should optimally be wrapped inside a transaction, to make sure an error thrown mid-way
        // does not leave the
        // database in an inconsistent state, but unfortunately Mongo only supports that when we have a
        // replica set ...

        var gameDatastore = DatabaseManager.getGameDatastore();
        var accountDatastore = DatabaseManager.getAccountDatastore();
        String accountId = target.getId();
        Player player = Grasscutter.getGameServer().getPlayerByAccountId(accountId);

        // Close session first
        if (player != null) {
            player.getSession().close();
        } else {
            player = gameDatastore.find(Player.class).filter(Filters.eq("accountId", accountId)).first();
            if (player == null) return;
        }
        int uid = player.getUid();
        var gameDatabase = gameDatastore.getDatabase();

        DatabaseHelper.asyncOperation(
                () -> {
                    // Delete data from collections
                    gameDatabase
                            .getCollection("achievements")
                            .deleteMany(eq("uid", uid));
                    gameDatabase.getCollection("activities").deleteMany(eq("uid", uid));
                    gameDatabase.getCollection("homes").deleteMany(eq("ownerUid", uid));
                    gameDatabase.getCollection("scene_buildings").deleteMany(eq("ownerUid", uid));
                    gameDatabase.getCollection("mail").deleteMany(eq("ownerUid", uid));
                    gameDatabase.getCollection("avatars").deleteMany(eq("ownerId", uid));
                    gameDatabase.getCollection("gachas").deleteMany(eq("ownerId", uid));
                    gameDatabase.getCollection("items").deleteMany(eq("ownerId", uid));
                    gameDatabase.getCollection("quests").deleteMany(eq("ownerUid", uid));
                    gameDatabase
                            .getCollection("battlepass")
                            .deleteMany(eq("ownerUid", uid));

                    // Delete friendships.
                    // Here, we need to make sure to not only delete the deleted account's friendships,
                    // but also all friendship entries for that account's friends.
                    gameDatabase
                            .getCollection("friendships")
                            .deleteMany(eq("ownerId", uid));
                    gameDatabase
                            .getCollection("friendships")
                            .deleteMany(eq("friendId", uid));

                    // Delete the player last.
                    gameDatastore
                            .find(Player.class)
                            .filter(Filters.eq("id", uid))
                            .delete();

                    // Finally, delete the account itself.
                    accountDatastore
                            .find(Account.class)
                            .filter(Filters.eq("id", accountId))
                            .delete();
                });
    }

    public static <T> Stream<T> getByGameClass(Class<T> classType) {
        return DatabaseManager.getGameDatastore().find(classType).stream();
    }

    @Deprecated(forRemoval = true)
    public static List<Player> getAllPlayers() {
        return DatabaseManager.getGameDatastore().find(Player.class).stream().toList();
    }

    public static Player getPlayerByUid(int id) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("_id", id))
                .first();
    }

    @Deprecated
    public static Player getPlayerByAccount(Account account) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("accountId", account.getId()))
                .first();
    }

    public static Player getPlayerByAccount(Account account, Class<? extends Player> playerClass) {
        return DatabaseManager.getGameDatastore()
                .find(playerClass)
                .filter(Filters.eq("accountId", account.getId()))
                .first();
    }

    /**
     * Use {@link DatabaseHelper#getPlayerByAccount(Account, Class)} for creating a real player. This
     * method is used for fetching the player's data.
     *
     * @param accountId The account's ID.
     * @return The player.
     */
    public static Player getPlayerByAccount(String accountId) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("accountId", accountId))
                .first();
    }

    public static boolean checkIfPlayerExists(int uid) {
        return DatabaseManager.getGameDatastore()
                        .find(Player.class)
                        .filter(Filters.eq("_id", uid))
                        .count()
                > 0;
    }

    public static void generatePlayerUid(Player character, int reservedId) {
        PlayerUidAllocator.assignLegacy(character, reservedId);
    }

    /** Legacy unbound allocation. Login must use the account-owned creation entry below. */
    public static int getNextPlayerId(int reservedId) {
        return PlayerUidAllocator.anonymous(reservedId);
    }

    public static Player getOrCreatePlayer(Account account, Class<? extends Player> type,
            java.util.function.Supplier<? extends Player> factory) {
        return PlayerUidAllocator.createOrLoad(PlayerUidAllocator.reserve(account), type, factory);
    }

    public static void savePlayer(Player character) {
        DatabaseHelper.saveGameAsync(character);
    }

    public static void saveAvatar(Avatar avatar) {
        if (avatar.getTrialAvatarId() != 0) return;
        DatabaseHelper.saveGameAsync(avatar);
    }

    public static void deleteTrialAvatar(Avatar avatar) {
        if (avatar.getTrialAvatarId() == 0 || avatar.getObjectId() == null)
            throw new IllegalArgumentException("Only a persisted trial avatar can be deleted here.");
        DatabaseManager.getGameDatastore().delete(avatar);
    }

    /**
     * Fetches all avatars of a player.
     *
     * @param player The player.
     * @return The list of avatars.
     */
    public static List<Avatar> getAvatars(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Avatar.class)
                .filter(Filters.eq("ownerId", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveItem(GameItem item) {
        DatabaseHelper.saveGameAsync(item);
    }

    public static void deleteItem(GameItem item) {
        var datastore = DatabaseManager.getGameDatastore();
        eventExecutorItem.submit(
                () -> {
                    synchronized (item) {
                        item.getSaveGuard().delete(datastore);
                    }
                });
    }

    /**
     * Fetches all items of a player.
     *
     * @param player The player.
     * @return The list of items.
     */
    public static List<GameItem> getInventoryItems(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(GameItem.class)
                .filter(Filters.eq("ownerId", player.getUid()), Filters.ne("deleted", true), Filters.gt("count", 0))
                .stream()
                .toList();
    }

    public static List<Friendship> getFriends(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(Filters.eq("ownerId", player.getUid()))
                .stream()
                .toList();
    }

    public static List<Friendship> getReverseFriends(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(Filters.eq("friendId", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveFriendship(Friendship friendship) {
        DatabaseHelper.saveGameAsync(friendship);
    }

    public static void deleteFriendship(Friendship friendship) {
        var datastore = DatabaseManager.getGameDatastore();
        DatabaseHelper.asyncOperation(() -> datastore.delete(friendship));
    }

    public static Friendship getReverseFriendship(Friendship friendship) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(
                        Filters.and(
                                Filters.eq("ownerId", friendship.getFriendId()),
                                Filters.eq("friendId", friendship.getOwnerId())))
                .first();
    }

    public static List<GachaRecord> getGachaRecords(int ownerId, int page, int gachaType) {
        return getGachaRecords(ownerId, page, gachaType, 10);
    }

    public static List<GachaRecord> getGachaRecords(
            int ownerId, int page, int gachaType, int pageSize) {
        return DatabaseManager.getGameDatastore()
                .find(GachaRecord.class)
                .filter(Filters.eq("ownerId", ownerId), Filters.eq("gachaType", gachaType))
                .iterator(
                        new FindOptions()
                                .sort(Sort.descending("transactionDate"))
                                .skip(pageSize * page)
                                .limit(pageSize))
                .toList();
    }

    public static long getGachaRecordsMaxPage(int ownerId, int page, int gachaType) {
        return getGachaRecordsMaxPage(ownerId, page, gachaType, 10);
    }

    public static long getGachaRecordsMaxPage(int ownerId, int page, int gachaType, int pageSize) {
        long count =
                DatabaseManager.getGameDatastore()
                        .find(GachaRecord.class)
                        .filter(Filters.eq("ownerId", ownerId), Filters.eq("gachaType", gachaType))
                        .count();
        return count / 10 + (count % 10 > 0 ? 1 : 0);
    }

    public static void saveGachaRecord(GachaRecord gachaRecord) {
        DatabaseHelper.saveGameAsync(gachaRecord);
    }

    public static List<Mail> getAllMail(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Mail.class)
                .filter(Filters.eq("ownerUid", player.getUid()))
                .stream()
                .toList();
    }

    /** Atomic across offline deliveries and live sessions; IDs never depend on mailbox positions. */
    public static int nextMailId() {
        var counters =
                DatabaseManager.getGameDatabase()
                        .getCollection("mailCounters")
                        .withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        if (!mailCounterInitialized)
            synchronized (DatabaseHelper.class) {
                if (!mailCounterInitialized) {
                    var highest =
                            DatabaseManager.getGameDatabase()
                                    .getCollection("mail")
                                    .find()
                                    .sort(com.mongodb.client.model.Sorts.descending("mailId"))
                                    .limit(1)
                                    .first();
                    long max =
                            highest != null && highest.get("mailId") instanceof Number number
                                    ? number.longValue()
                                    : 0;
                    counters.updateOne(
                            eq("_id", "sequence"),
                            new org.bson.Document("$max", new org.bson.Document("value", max)),
                            new com.mongodb.client.model.UpdateOptions().upsert(true));
                    mailCounterInitialized = true;
                }
            }
        var result =
                counters.findOneAndUpdate(
                        eq("_id", "sequence"),
                        new org.bson.Document("$inc", new org.bson.Document("value", 1L)),
                        new com.mongodb.client.model.FindOneAndUpdateOptions()
                                .upsert(true)
                                .returnDocument(com.mongodb.client.model.ReturnDocument.AFTER));
        long value = ((Number) result.get("value")).longValue();
        if (value <= 0 || value > Integer.MAX_VALUE)
            throw new IllegalStateException("Mail ID sequence exhausted");
        return (int) value;
    }

    public static Mail getMailByDeliveryKey(String key) {
        return DatabaseManager.getGameDatastore()
                .find(Mail.class)
                .filter(Filters.eq("deliveryKey", key))
                .first();
    }

    public static int assignLegacyMailId(Mail message, int proposed) {
        var collection =
                DatabaseManager.getGameDatabase()
                        .getCollection("mail")
                        .withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        var changed =
                collection.findOneAndUpdate(
                        com.mongodb.client.model.Filters.and(
                                eq("_id", message.getId()),
                                com.mongodb.client.model.Filters.or(
                                        eq("mailId", 0), com.mongodb.client.model.Filters.exists("mailId", false))),
                        new org.bson.Document("$set", new org.bson.Document("mailId", proposed)),
                        new com.mongodb.client.model.FindOneAndUpdateOptions()
                                .returnDocument(com.mongodb.client.model.ReturnDocument.AFTER));
        if (changed == null) changed = collection.find(eq("_id", message.getId())).first();
        if (changed == null || !(changed.get("mailId") instanceof Number))
            throw new IllegalStateException("Legacy mail disappeared while assigning its ID");
        return ((Number) changed.get("mailId")).intValue();
    }

    public static void saveMail(Mail mail) {
        DatabaseHelper.saveGameAsync(mail);
    }

    public static void deleteMail(Mail mail) {
        var datastore = DatabaseManager.getGameDatastore();
        DatabaseHelper.asyncOperation(() -> datastore.delete(mail));
    }

    public static List<GameMainQuest> getAllQuests(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(GameMainQuest.class)
                .filter(Filters.eq("ownerUid", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveQuest(GameMainQuest quest) {
        DatabaseHelper.saveGameAsync(quest);
    }

    public static void deleteQuest(GameMainQuest quest) {
        var datastore = DatabaseManager.getGameDatastore();
        DatabaseHelper.asyncOperation(() -> datastore.delete(quest));
    }

    public static GameHome getHomeByUid(int id) {
        return DatabaseManager.getGameDatastore()
                .find(GameHome.class)
                .filter(Filters.eq("ownerUid", id))
                .first();
    }

    public static void saveHome(GameHome gameHome) {
        DatabaseHelper.saveGameAsync(gameHome);
    }

    public static emu.grasscutter.game.dailytask.DailyTaskManager loadDailyTaskManager(
            Player player) {
        var manager =
                DatabaseManager.getGameDatastore()
                        .find(emu.grasscutter.game.dailytask.DailyTaskManager.class)
                        .filter(Filters.eq("ownerUid", player.getUid()))
                        .first();

        if (manager == null) {
            manager = new emu.grasscutter.game.dailytask.DailyTaskManager(player);
            manager.save();
        } else {
            manager.setPlayer(player);
        }

        return manager;
    }

    public static void saveDailyTaskManager(emu.grasscutter.game.dailytask.DailyTaskManager manager) {
        DatabaseManager.getGameDatastore().save(manager);
    }

    public static BattlePassManager loadBattlePass(Player player) {
        BattlePassManager manager =
                DatabaseManager.getGameDatastore()
                        .find(BattlePassManager.class)
                        .filter(Filters.eq("ownerUid", player.getUid()))
                        .first();
        if (manager == null) {
            manager = new BattlePassManager(player);
            manager.save();
        } else {
            manager.setPlayer(player);
        }
        return manager;
    }

    public static void saveBattlePass(BattlePassManager manager) {
        DatabaseHelper.saveGameAsync(manager);
    }

    public static PlayerActivityData getPlayerActivityData(int uid, int activityId) {
        return DatabaseManager.getGameDatastore()
                .find(PlayerActivityData.class)
                .filter(Filters.and(Filters.eq("uid", uid), Filters.eq("activityId", activityId)))
                .first();
    }

    public static void savePlayerActivityData(PlayerActivityData playerActivityData) {
        DatabaseHelper.saveGameAsync(playerActivityData);
    }

    public static MusicGameBeatmap getMusicGameBeatmap(long musicShareId) {
        return DatabaseManager.getGameDatastore()
                .find(MusicGameBeatmap.class)
                .filter(Filters.eq("musicShareId", musicShareId))
                .first();
    }

    public static void saveMusicGameBeatmap(MusicGameBeatmap musicGameBeatmap) {
        DatabaseHelper.saveGameAsync(musicGameBeatmap);
    }

    @Nullable public static Achievements getAchievementData(int uid) {
        try {
            return DatabaseManager.getGameDatastore()
                    .find(Achievements.class)
                    .filter(Filters.and(Filters.eq("uid", uid)))
                    .first();
        } catch (IllegalArgumentException e) {
            Grasscutter.getLogger()
                    .debug("Error occurred while getting uid " + uid + "'s achievement data", e);
            DatabaseManager.getGameDatabase().getCollection("achievements").deleteMany(eq("uid", uid));
            return null;
        }
    }

    public static void saveAchievementData(Achievements achievements) {
        DatabaseHelper.saveGameAsync(achievements);
    }

    public static void saveGroupInstance(SceneGroupInstance instance) {
        DatabaseHelper.saveGameAsync(instance);
    }

    public static SceneGroupInstance loadGroupInstance(int groupId, Player owner) {
        return DatabaseManager.getGameDatastore()
                .find(SceneGroupInstance.class)
                .filter(Filters.and(Filters.eq("ownerUid", owner.getUid()), Filters.eq("groupId", groupId)))
                .first();
    }
}
