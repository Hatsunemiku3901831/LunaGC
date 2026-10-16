package emu.grasscutter.game.player;

import dev.morphia.annotations.Entity;

import lombok.Getter;

import org.bson.Document;

import java.util.Objects;
import java.util.UUID;

/**
 * A row revision plus a unique write identity; a new player incarnation starts with a new token.
 */
@Entity(useDiscriminator = false)
@Getter
public final class PlayerSaveStamp {
    private long revision;
    private String token;

    private PlayerSaveStamp() {}

    private PlayerSaveStamp(long revision, String token) {
        this.revision = revision;
        this.token = token;
    }

    static PlayerSaveStamp next(PlayerSaveStamp previous) {
        if (previous != null) previous.validate();
        return new PlayerSaveStamp(
                previous == null ? 1 : Math.addExact(previous.revision, 1),
                UUID.randomUUID().toString());
    }

    void validate() {
        if (revision <= 0 || token == null || !UUID.fromString(token).toString().equals(token))
            throw new IllegalStateException("Invalid player save stamp");
    }

    Document document() {
        return new Document("revision", revision).append("token", token);
    }

    static PlayerSaveStamp from(Document row) {
        Object value = row.get("saveStamp");
        if (value == null) return null;
        if (!(value instanceof Document stamp)
                || !(stamp.get("revision") instanceof Long revision)
                || !(stamp.get("token") instanceof String token)
                || stamp.size() != 2)
            throw new IllegalStateException("Invalid stored player save stamp");
        var result = new PlayerSaveStamp(revision, token);
        result.validate();
        return result;
    }

    static boolean same(PlayerSaveStamp first, PlayerSaveStamp second) {
        return first == second
                || first != null
                        && second != null
                        && first.revision == second.revision
                        && Objects.equals(first.token, second.token);
    }
}
