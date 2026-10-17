package emu.grasscutter.server.dispatch;

import com.google.gson.*;

import java.util.Set;

/** Strict internal payloads. Revisions travel as decimal strings to retain all 64 bits. */
final class CredentialRevocation {
    record Request(String accountId, long throughRevision) {
        Request {
            if (accountId == null || accountId.isBlank() || throughRevision <= 0)
                throw new IllegalArgumentException("Account and positive revision required");
        }

        JsonObject message() {
            var result = new JsonObject();
            result.addProperty("accountId", accountId);
            result.addProperty("throughRevision", Long.toString(throughRevision));
            return result;
        }

        JsonObject response(boolean ok) {
            var result = message();
            result.addProperty("ok", ok);
            return result;
        }

        void confirm(JsonElement packet) {
            var data = IDispatcher.decode(packet);
            if (data == null || !data.keySet().equals(Set.of("accountId", "throughRevision", "ok")))
                throw new IllegalStateException("Invalid credential revocation acknowledgment");
            var ok = data.get("ok");
            if (!accountId.equals(text(data, "accountId"))
                    || !Long.toString(throughRevision).equals(text(data, "throughRevision"))
                    || !ok.isJsonPrimitive()
                    || !ok.getAsJsonPrimitive().isBoolean()
                    || !ok.getAsBoolean())
                throw new IllegalStateException("Credential revocation was not confirmed");
        }
    }

    static Request decode(JsonElement packet) {
        var data = IDispatcher.decode(packet);
        if (data == null || !data.keySet().equals(Set.of("accountId", "throughRevision")))
            throw new IllegalArgumentException("Invalid credential revocation request");
        String revision = text(data, "throughRevision");
        long through = Long.parseLong(revision);
        if (!revision.equals(Long.toString(through)))
            throw new IllegalArgumentException("Canonical revision required");
        return new Request(text(data, "accountId"), through);
    }

    private static String text(JsonObject data, String name) {
        var field = data.get(name);
        if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string field " + name);
        return field.getAsString();
    }
}
