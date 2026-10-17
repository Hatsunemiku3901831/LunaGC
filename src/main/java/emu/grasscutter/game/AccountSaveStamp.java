package emu.grasscutter.game;

import dev.morphia.annotations.Entity;

import org.bson.Document;

import java.util.Objects;
import java.util.UUID;

/** A confirmed Account revision and a unique write identity, including recreated account rows. */
@Entity(useDiscriminator = false)
public final class AccountSaveStamp {
    private long revision;
    private String token;

    private AccountSaveStamp() {}

    private AccountSaveStamp(long revision, String token) {
        this.revision = revision;
        this.token = token;
    }

    static AccountSaveStamp next(AccountSaveStamp previous) {
        if (previous != null) previous.validate();
        return new AccountSaveStamp(
                previous == null ? 1 : Math.addExact(previous.revision, 1),
                UUID.randomUUID().toString());
    }

    void validate() {
        if (revision <= 0 || token == null || !UUID.fromString(token).toString().equals(token))
            throw new IllegalStateException("Invalid account save stamp");
    }

    Document document() {
        return new Document("revision", revision).append("token", token);
    }

    static AccountSaveStamp from(Document row) {
        Object value = row.get("saveStamp");
        if (value == null) return null;
        if (!(value instanceof Document stamp)
                || !(stamp.get("revision") instanceof Long revision)
                || !(stamp.get("token") instanceof String token)
                || stamp.size() != 2)
            throw new IllegalStateException("Invalid stored account save stamp");
        var result = new AccountSaveStamp(revision, token);
        result.validate();
        return result;
    }

    static Document condition(String id, AccountSaveStamp stamp) {
        var filter = new Document("_id", id);
        if (stamp == null) return filter.append("saveStamp", null);
        stamp.validate();
        return filter.append("saveStamp.revision", stamp.revision)
                .append("saveStamp.token", stamp.token);
    }

    static boolean same(AccountSaveStamp a, AccountSaveStamp b) {
        return a == b
                || a != null
                        && b != null
                        && a.revision == b.revision
                        && Objects.equals(a.token, b.token);
    }
}
