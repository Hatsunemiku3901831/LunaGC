package emu.grasscutter.game.activity;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import org.bson.Document;

import java.util.Objects;
import java.util.UUID;

/** A revision and unique write token belonging to one activity document incarnation. */
@Entity(useDiscriminator = false)
@Getter
public final class ActivitySaveStamp {
    private long revision;
    private String token;

    private ActivitySaveStamp() {}

    private ActivitySaveStamp(long revision, String token) {
        this.revision = revision;
        this.token = token;
    }

    static ActivitySaveStamp next(ActivitySaveStamp before) {
        if (before != null) before.validate();
        return new ActivitySaveStamp(
                before == null ? 1 : Math.addExact(before.revision, 1),
                UUID.randomUUID().toString());
    }

    void validate() {
        if (revision <= 0 || token == null || !UUID.fromString(token).toString().equals(token))
            throw new IllegalStateException("Invalid activity save stamp");
    }

    Document document() {
        return new Document("revision", revision).append("token", token);
    }

    static ActivitySaveStamp from(Document row) {
        Object value = row.get("saveStamp");
        if (value == null) return null;
        if (!(value instanceof Document stamp)
                || !(stamp.get("revision") instanceof Long revision)
                || !(stamp.get("token") instanceof String token)
                || stamp.size() != 2)
            throw new IllegalStateException("Invalid stored activity save stamp");
        var result = new ActivitySaveStamp(revision, token);
        result.validate();
        return result;
    }

    static boolean same(ActivitySaveStamp a, ActivitySaveStamp b) {
        return a == b
                || a != null
                        && b != null
                        && a.revision == b.revision
                        && Objects.equals(a.token, b.token);
    }
}
