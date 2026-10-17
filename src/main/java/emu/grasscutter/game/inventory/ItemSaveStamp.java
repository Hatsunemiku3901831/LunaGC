package emu.grasscutter.game.inventory;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import org.bson.Document;

import java.util.*;

/** Revision and write identity belonging to one persisted inventory instance. */
@Entity(useDiscriminator = false)
@Getter
public final class ItemSaveStamp {
    private long revision;
    private String token;

    private ItemSaveStamp() {}

    private ItemSaveStamp(long revision, String token) {
        this.revision = revision;
        this.token = token;
    }

    static ItemSaveStamp next(ItemSaveStamp previous) {
        if (previous != null) previous.validate();
        return new ItemSaveStamp(
                previous == null ? 1 : Math.addExact(previous.revision, 1),
                UUID.randomUUID().toString());
    }

    void validate() {
        if (revision <= 0 || token == null || !UUID.fromString(token).toString().equals(token))
            throw new IllegalStateException("Invalid item save stamp");
    }

    Document document() {
        return new Document("revision", revision).append("token", token);
    }

    static ItemSaveStamp from(Document row) {
        Object raw = row.get("saveStamp");
        if (raw == null) return null;
        if (!(raw instanceof Document d)
                || d.size() != 2
                || !(d.get("revision") instanceof Long revision)
                || !(d.get("token") instanceof String token))
            throw new IllegalStateException("Invalid stored item save stamp");
        var result = new ItemSaveStamp(revision, token);
        result.validate();
        return result;
    }

    static boolean same(ItemSaveStamp a, ItemSaveStamp b) {
        return a == b
                || a != null
                        && b != null
                        && a.revision == b.revision
                        && Objects.equals(a.token, b.token);
    }
}
