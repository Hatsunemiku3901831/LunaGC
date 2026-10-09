package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.MongoClients;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.friends.Friendship;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.GameServer;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseDatastoreScopeTest {
    private static final Duration TIMEOUT=Duration.ofSeconds(10);
    private Field game,dispatch;
    private Object originalGame,originalDispatch;
    private ServerRunMode originalMode;

    @BeforeEach void setup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        game=DatabaseManager.class.getDeclaredField("gameDatastore"); game.setAccessible(true);
        dispatch=DatabaseManager.class.getDeclaredField("dispatchDatastore"); dispatch.setAccessible(true);
        originalGame=game.get(null); originalDispatch=dispatch.get(null); originalMode=Grasscutter.getRunMode();
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
    }
    @AfterEach void restore() throws Exception {
        try { assertTrue(DatabaseHelper.awaitIdle(TIMEOUT)); }
        finally { game.set(null,originalGame); dispatch.set(null,originalDispatch); Grasscutter.setRunModeOverride(originalMode); }
    }
    private void game(Datastore value) { try { game.set(null,value); } catch(Exception e) { throw new AssertionError(e); } }
    private void dispatch(Datastore value) { try { dispatch.set(null,value); } catch(Exception e) { throw new AssertionError(e); } }

    private static class HeldQueue implements AutoCloseable {
        private final DatabaseWriteQueue pool;
        private final CountDownLatch release=new CountDownLatch(1);
        HeldQueue(DatabaseWriteQueue pool) throws Exception {
            this.pool=pool;
            var ready=new CountDownLatch(pool.getCorePoolSize());
            for(int i=0;i<pool.getCorePoolSize();i++) pool.submit(()->{
                ready.countDown();
                try { assertTrue(release.await(30,TimeUnit.SECONDS)); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            });
            assertTrue(ready.await(10,TimeUnit.SECONDS));
        }
        @Override public void close() throws Exception { release.countDown(); assertTrue(pool.awaitIdle(TIMEOUT)); }
    }
    private static class Recorder {
        final AtomicInteger saves=new AtomicInteger(),deletes=new AtomicInteger();
        Runnable firstSave=()->{};
        final Datastore store=(Datastore)Proxy.newProxyInstance(Datastore.class.getClassLoader(),new Class<?>[]{Datastore.class},(proxy,method,args)->{
            return switch(method.getName()) {
                case "save" -> { if(saves.incrementAndGet()==1) firstSave.run(); yield args[0]; }
                case "delete" -> { deletes.incrementAndGet(); yield null; }
                case "equals" -> true; // Equal stores must still keep independent queue scopes.
                case "hashCode" -> 1;
                case "toString" -> "scope-test-store";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
    }
    @Test void queuedDefaultSaveKeepsItsOriginalDatastore() throws Exception {
        var first=new Recorder(); var second=new Recorder(); game(first.store);
        try(var held=new HeldQueue(DatabaseHelper.getEventExecutor())) {
            DatabaseHelper.saveGameAsync(new Object()); game(second.store);
        }
        assertEquals(1,first.saves.get()); assertEquals(0,second.saves.get());
    }
    @Test void queuedAccountSaveKeepsTheSelectedDispatchStore() throws Exception {
        var first=new Recorder(); var second=new Recorder(); dispatch(first.store);
        try(var held=new HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            DatabaseHelper.saveAccountAsync(new Account()); dispatch(second.store);
        }
        assertEquals(1,first.saves.get()); assertEquals(0,second.saves.get());
    }
    @Test void queuedAccountSaveDoesNotChangeTargetsWhenRunModeChanges() throws Exception {
        var first=new Recorder(); var second=new Recorder(); game(first.store); dispatch(second.store);
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
        try(var held=new HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            DatabaseHelper.saveAccountAsync(new Account()); Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        }
        assertEquals(1,first.saves.get()); assertEquals(0,second.saves.get());
    }
    @Test void queuedAccountObjectOnGameApiKeepsTheGameStore() throws Exception {
        var first=new Recorder(); var second=new Recorder(); game(first.store);
        try(var held=new HeldQueue(DatabaseHelper.getEventExecutorAccount())) {
            DatabaseHelper.saveGameAsync(new Account()); game(second.store);
        }
        assertEquals(1,first.saves.get()); assertEquals(0,second.saves.get());
    }
    @Test void concurrentModificationRetryKeepsTheOriginalStore() throws Exception {
        var first=new Recorder(); var second=new Recorder(); game(first.store);
        first.firstSave=()->{ game(second.store); throw new ConcurrentModificationException("retry scope"); };
        DatabaseHelper.saveGameAsync(new Object()); assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(2,first.saves.get()); assertEquals(0,second.saves.get());
    }
    @Test void duplicateKeyRetryKeepsTheOriginalStore() throws Exception {
        var first=new Recorder(); var second=new Recorder(); game(first.store);
        first.firstSave=()->{ game(second.store); throw new MongoWriteException(new WriteError(11000,"retry scope",new BsonDocument()),new ServerAddress()); };
        DatabaseHelper.saveGameAsync(new Object()); assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertEquals(2,first.saves.get()); assertEquals(0,second.saves.get());
    }
    @Test void theSameObjectCanQueueIndependentWritesForEqualButDistinctStores() throws Exception {
        var first=new Recorder(); var second=new Recorder(); var value=new Object(); game(first.store);
        assertEquals(first.store,second.store); assertNotSame(first.store,second.store);
        try(var held=new HeldQueue(DatabaseHelper.getEventExecutor())) {
            DatabaseHelper.saveGameAsync(value); game(second.store); DatabaseHelper.saveGameAsync(value);
        }
        assertEquals(1,first.saves.get()); assertEquals(1,second.saves.get());
    }
    @Test void repeatedWritesForTheSameObjectAndStoreStillCoalesce() throws Exception {
        var first=new Recorder(); var value=new Object(); game(first.store);
        long before=DatabaseHelper.getEventExecutor().snapshot().coalesced();
        try(var held=new HeldQueue(DatabaseHelper.getEventExecutor())) {
            for(int i=0;i<20;i++) DatabaseHelper.saveGameAsync(value);
        }
        assertEquals(1,first.saves.get()); assertEquals(before+19,DatabaseHelper.getEventExecutor().snapshot().coalesced());
    }
    @ParameterizedTest @ValueSource(strings={"friendship","mail","quest"})
    void queuedTypedDeletesKeepTheirOriginalStore(String kind) throws Exception {
        var first=new Recorder(); var second=new Recorder(); game(first.store);
        try(var held=new HeldQueue(DatabaseHelper.getEventExecutor())) {
            switch(kind) {
                case "friendship" -> DatabaseHelper.deleteFriendship(new Friendship());
                case "mail" -> DatabaseHelper.deleteMail(new Mail());
                case "quest" -> DatabaseHelper.deleteQuest(new GameMainQuest());
                default -> fail(kind);
            }
            game(second.store);
        }
        assertEquals(1,first.deletes.get()); assertEquals(0,second.deletes.get());
    }
    @Test void aWriteSubmittedBeforeInitializationCannotDriftIntoALaterStore() throws Exception {
        var later=new Recorder(); game(null);
        long failures=DatabaseHelper.getEventExecutor().snapshot().failed();
        try(var held=new HeldQueue(DatabaseHelper.getEventExecutor())) {
            DatabaseHelper.saveGameAsync(new Object()); game(later.store);
        }
        assertEquals(0,later.saves.get()); assertEquals(failures+1,DatabaseHelper.getEventExecutor().snapshot().failed());
    }

    private static class Stores implements AutoCloseable {
        final String primary=Optional.ofNullable(System.getenv("LUNAGC_TEST_MONGO_URI")).orElse("mongodb://localhost:27017");
        final String secondary=Optional.ofNullable(System.getenv("LUNAGC_TEST_MONGO_SECONDARY_URI")).orElse(primary);
        final String name="lunagc_scope_"+UUID.randomUUID().toString().replace("-","");
        final com.mongodb.client.MongoClient firstClient=MongoClients.create(primary),secondClient=MongoClients.create(secondary);
        final Datastore first=Morphia.createDatastore(firstClient,name+"_a"),second=Morphia.createDatastore(secondClient,name+"_b");
        Stores() { first.getMapper().map(GameItem.class); second.getMapper().map(GameItem.class); }
        Document reconnect(boolean a) {
            try(var client=MongoClients.create(a?primary:secondary)) {
                return client.getDatabase(name+(a?"_a":"_b")).getCollection("items").find().first();
            }
        }
        @Override public void close() {
            try { first.getDatabase().drop(); second.getDatabase().drop(); }
            finally { firstClient.close(); secondClient.close(); }
        }
    }
    private static GameItem item() { var value=new GameItem(); value.setItemId(202); value.setCount(7); return value; }
    private static GameItem seedBoth(Stores stores) throws Exception {
        var value=item(); stores.first.save(value); var copy=item();
        var id=GameItem.class.getDeclaredField("id"); id.setAccessible(true); id.set(copy,id.get(value)); stores.second.save(copy);
        return value;
    }
    @Test void actualMongoSaveStaysInTheSubmittedInstanceAndSurvivesReconnect() throws Exception {
        try(var stores=new Stores()) {
            game(stores.first);
            try(var held=new HeldQueue(DatabaseHelper.getEventExecutorItem())) {
                DatabaseHelper.saveGameAsync(item()); game(stores.second);
            }
            assertNotNull(stores.reconnect(true)); assertNull(stores.reconnect(false));
            assertEquals(7,stores.reconnect(true).getInteger("count"));
        }
    }
    @Test void actualMongoExplicitDeleteDoesNotDeleteTheOtherInstancesItem() throws Exception {
        try(var stores=new Stores()) {
            var value=seedBoth(stores); game(stores.first);
            try(var held=new HeldQueue(DatabaseHelper.getEventExecutorItem())) {
                DatabaseHelper.deleteItem(value); game(stores.second);
            }
            assertNull(stores.reconnect(true)); assertNotNull(stores.reconnect(false));
        }
    }
    @Test void actualMongoZeroCountSaveDeletesOnlyTheSubmittedInstancesItem() throws Exception {
        try(var stores=new Stores()) {
            var value=seedBoth(stores); value.setCount(0); game(stores.first);
            try(var held=new HeldQueue(DatabaseHelper.getEventExecutorItem())) {
                DatabaseHelper.saveGameAsync(value); game(stores.second);
            }
            assertNull(stores.reconnect(true)); assertEquals(7,stores.reconnect(false).getInteger("count"));
        }
    }
    @Test void actualMongoCoalescingKeepsAWritesForEachSubmittedInstance() throws Exception {
        try(var stores=new Stores()) {
            var value=item(); game(stores.first);
            try(var held=new HeldQueue(DatabaseHelper.getEventExecutorItem())) {
                DatabaseHelper.saveGameAsync(value); game(stores.second); DatabaseHelper.saveGameAsync(value);
            }
            assertNotNull(stores.reconnect(true)); assertNotNull(stores.reconnect(false));
            assertEquals(7,stores.reconnect(true).getInteger("count")); assertEquals(7,stores.reconnect(false).getInteger("count"));
        }
    }
    @Test void actualMongoAccountCleanupKeepsAllCollectionsAndAccountOnTheirOriginalStores() throws Exception {
        var serverField=Grasscutter.class.getDeclaredField("gameServer"); serverField.setAccessible(true);
        var previousServer=serverField.get(null);
        try(var stores=new Stores()) {
            stores.first.getMapper().map(Player.class,Account.class);
            stores.second.getMapper().map(Player.class,Account.class);
            int uid=54321; String accountId="scope-account";
            var ownerFields=Map.of("achievements","uid","activities","uid","homes","ownerUid","mail","ownerUid",
                    "avatars","ownerId","gachas","ownerId","items","ownerId","quests","ownerUid","battlepass","ownerUid","friendships","ownerId");
            for(var datastore:List.of(stores.first,stores.second)) {
                var database=datastore.getDatabase();
                database.getCollection("players").insertOne(new Document("_id",uid).append("accountId",accountId).append("teamManager",new Document()));
                database.getCollection("players").insertOne(new Document("_id",uid+1).append("accountId","other"));
                database.getCollection("accounts").insertOne(new Document("_id",accountId).append("username","scope-user"));
                database.getCollection("accounts").insertOne(new Document("_id","changed").append("username","other-user"));
                for(var row:ownerFields.entrySet()) {
                    database.getCollection(row.getKey()).insertOne(new Document(row.getValue(),uid));
                    database.getCollection(row.getKey()).insertOne(new Document(row.getValue(),uid+1));
                }
                database.getCollection("friendships").insertOne(new Document("ownerId",uid+2).append("friendId",uid));
            }
            var server=new GameServer();
            var players=GameServer.class.getDeclaredField("players"); players.setAccessible(true); players.set(server,new HashMap<>());
            serverField.set(null,server);
            game(stores.first); dispatch(stores.first);
            var account=new Account(); account.setId(accountId);
            try(var held=new HeldQueue(DatabaseHelper.getEventExecutor())) {
                DatabaseHelper.deleteAccount(account);
                game(stores.second); dispatch(stores.second); account.setId("changed");
            }
            for(var row:ownerFields.entrySet()) {
                assertEquals(0,stores.first.getDatabase().getCollection(row.getKey()).countDocuments(new Document(row.getValue(),uid)),row.getKey());
                assertEquals(1,stores.first.getDatabase().getCollection(row.getKey()).countDocuments(new Document(row.getValue(),uid+1)),row.getKey());
                assertEquals(1,stores.second.getDatabase().getCollection(row.getKey()).countDocuments(new Document(row.getValue(),uid)),row.getKey());
            }
            assertEquals(0,stores.first.getDatabase().getCollection("friendships").countDocuments(new Document("friendId",uid)));
            assertEquals(1,stores.second.getDatabase().getCollection("friendships").countDocuments(new Document("friendId",uid)));
            for(String collection:List.of("players","accounts")) {
                Object id=collection.equals("players")?uid:accountId;
                assertEquals(0,stores.first.getDatabase().getCollection(collection).countDocuments(new Document("_id",id)),collection);
                assertEquals(1,stores.second.getDatabase().getCollection(collection).countDocuments(new Document("_id",id)),collection);
            }
            assertEquals(1,stores.first.getDatabase().getCollection("accounts").countDocuments(new Document("_id","changed")));
            assertEquals(1,stores.first.getDatabase().getCollection("players").countDocuments(new Document("_id",uid+1)));
        } finally { serverField.set(null,previousServer); }
    }
}
