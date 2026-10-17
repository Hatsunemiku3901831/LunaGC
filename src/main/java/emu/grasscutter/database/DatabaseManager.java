package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

import static emu.grasscutter.config.Configuration.DATABASE;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteConcern;
import com.mongodb.client.*;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;

import dev.morphia.*;
import dev.morphia.annotations.Entity;
import dev.morphia.mapping.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.game.Account;

import org.bson.BsonType;
import org.bson.Document;

public final class DatabaseManager {
    private static volatile Datastore gameDatastore;
    private static volatile Datastore dispatchDatastore;

    public static Datastore getGameDatastore() {
        return gameDatastore;
    }

    public static Datastore getAccountDatastore() {
        if (Grasscutter.getRunMode() == ServerRunMode.HYBRID) return gameDatastore;
        else return dispatchDatastore;
    }

    public static MongoDatabase getGameDatabase() {
        return getGameDatastore().getDatabase();
    }

    public static void initialize() {
        // Initialize
        MongoClient gameMongoClient = MongoClients.create(DATABASE.game.connectionUri);

        // Set mapper options.
        MapperOptions mapperOptions =
                MapperOptions.builder().storeEmpties(true).storeNulls(false).build();

        // Create data store.
        gameDatastore =
                Morphia.createDatastore(gameMongoClient, DATABASE.game.collection, mapperOptions);

        // Map classes.
        var entities =
                Grasscutter.reflector.getTypesAnnotatedWith(Entity.class).stream()
                        .filter(
                                cls -> {
                                    Entity e = cls.getAnnotation(Entity.class);
                                    return e != null && !e.value().equals(Mapper.IGNORED_FIELDNAME);
                                })
                        .toArray(Class<?>[]::new);

        gameDatastore.getMapper().map(entities);

        // Ensure indexes for the game datastore
        ensureIndexes(gameDatastore);

        if (Grasscutter.getRunMode() != ServerRunMode.HYBRID) {
            MongoClient dispatchMongoClient = MongoClients.create(DATABASE.server.connectionUri);

            dispatchDatastore =
                    Morphia.createDatastore(
                            dispatchMongoClient, DATABASE.server.collection, mapperOptions);
            dispatchDatastore
                    .getMapper()
                    .map(new Class<?>[] {DatabaseCounter.class, Account.class});

            // Ensure indexes for dispatch datastore
            ensureIndexes(dispatchDatastore);
        }
    }

    /**
     * Ensures the database indexes exist and rebuilds them if there is an error with them
     *
     * @param datastore The datastore to ensure indexes on
     */
    private static void ensureIndexes(Datastore datastore) {
        try {
            datastore.ensureIndexes();
        } catch (MongoCommandException e) {
            Grasscutter.getLogger().info("Mongo index error: ", e);
            // Duplicate index error
            if (e.getCode() == 85) {
                // Drop all indexes and re add them
                MongoIterable<String> collections = datastore.getDatabase().listCollectionNames();
                for (String name : collections) {
                    datastore.getDatabase().getCollection(name).dropIndexes();
                }
                // Add back indexes
                datastore.ensureIndexes();
            }
        }
    }

    /** Reserve in Mongo before returning; a queued counter save cannot reserve an ID. */
    static int reserveNextId(Datastore datastore, String name, int floor) {
        if (floor < 0 || floor == Integer.MAX_VALUE)
            throw new IllegalStateException("No more IDs are available for " + name);
        var counters =
                datastore
                        .getDatabase()
                        .getCollection("counters")
                        .withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        try {
            counters.insertOne(new Document("_id", name).append("count", (long) floor));
        } catch (MongoWriteException duplicate) {
            if (duplicate.getError() == null || duplicate.getError().getCode() != 11000)
                throw duplicate;
        }
        var valid =
                and(
                        eq("_id", name),
                        or(type("count", BsonType.INT32), type("count", BsonType.INT64)),
                        gte("count", 0L));
        // Raise old counters without rolling back another server's already reserved IDs.
        if (counters.updateOne(valid, max("count", (long) floor)).getMatchedCount() != 1)
            throw new IllegalStateException("Invalid ID counter for " + name);
        var reserved =
                counters.findOneAndUpdate(
                        and(valid, lt("count", (long) Integer.MAX_VALUE)),
                        inc("count", 1L),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (reserved == null
                || !(reserved.get("count") instanceof Number number)
                || number.longValue() <= 0
                || number.longValue() > Integer.MAX_VALUE)
            throw new IllegalStateException("ID counter exhausted for " + name);
        return number.intValue();
    }

    public static int getNextId(Class<?> c) {
        // Preserve the historical game-database counter in split dispatch installations.
        return reserveNextId(getGameDatastore(), c.getSimpleName(), 10000);
    }

    public static int getNextId(Object o) {
        return getNextId(o.getClass());
    }
}
