package emu.grasscutter.server.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;

import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class DispatchJsonDecodeTest {
    @BeforeAll
    static void setupConfiguration() {
        Grasscutter.getConfig();
    }

    private JsonObject payload() {
        var data = new JsonObject();
        data.addProperty("path", "C:\\fixture\\folder\\file.json");
        data.addProperty("quotes", "text \"quoted\" and \\ literal");
        data.addProperty("literal", "\\n \\t \\u4e2d");
        data.addProperty("control", "line\nnext\tcolumn\rreturn\bback\fform");
        data.addProperty("unicode", "中文 原神 🌙");
        var nested = new JsonArray();
        nested.add(data.deepCopy());
        nested.add(JsonNull.INSTANCE);
        data.add("nested", nested);
        return data;
    }

    private JsonElement wrapped(JsonElement value, int layers) {
        for (int i = 0; i < layers; i++) value = new JsonPrimitive(IDispatcher.JSON.toJson(value));
        return value;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void structuredPayloadRetainsEveryLiteralAcrossSupportedEnvelopeLayers(int layers) {
        var data = payload();
        assertEquals(data, assertDoesNotThrow(() -> IDispatcher.decode(wrapped(data, layers))));
    }

    @Test
    void aScalarStringKeepsItsQuotesBackslashesAndLineBreaks() {
        String text = "C:\\fixture\\name \"literal\"\n新行";
        assertEquals(
                text,
                assertDoesNotThrow(
                        () ->
                                IDispatcher.decode(
                                        new JsonPrimitive(IDispatcher.JSON.toJson(text)),
                                        String.class)));
    }

    @Test
    void aDirectArrayIsDecodedAsAnArray() {
        var data = new JsonArray();
        data.add(payload());
        data.add(1);
        assertEquals(data, assertDoesNotThrow(() -> IDispatcher.decode(data, JsonArray.class)));
    }

    @Test
    void aDirectNullIsAnAbsentResult() {
        assertNull(assertDoesNotThrow(() -> IDispatcher.decode(JsonNull.INSTANCE, Account.class)));
    }

    @Test
    void anEncodedNullIsAnAbsentResult() {
        assertNull(IDispatcher.decode(new JsonPrimitive("null"), Account.class));
    }

    @Test
    void bsonInt64MetadataAndLiteralStringsSurviveTheJsonTransport() {
        var row =
                new Document("_id", "fixture")
                        .append(
                                "saveStamp",
                                new Document("revision", Long.MAX_VALUE - 2)
                                        .append("token", UUID.randomUUID().toString()))
                        .append("reason", "quoted \"fixture\" at C:\\folder\\file");
        var data = new JsonObject();
        data.addProperty(
                "document",
                row.toJson(JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build()));
        var decoded = assertDoesNotThrow(() -> IDispatcher.decode(wrapped(data, 1)));
        var result = Document.parse(decoded.get("document").getAsString());
        assertEquals(row, result);
        assertInstanceOf(Long.class, result.get("saveStamp", Document.class).get("revision"));
    }

    @Test
    void accountFieldsDoNotLoseEscapedData() {
        var data = new JsonObject();
        data.addProperty("id", "fixture");
        data.addProperty("username", "fixture");
        data.addProperty("banReason", "reason \"quoted\" \\n C:\\fixture");
        var account = assertDoesNotThrow(() -> IDispatcher.decode(wrapped(data, 1), Account.class));
        assertEquals(data.get("banReason").getAsString(), account.getBanReason());
    }

    @Test
    void decodingDoesNotChangeTheCallerOwnedJsonTree() {
        var data = payload();
        String original = data.toString();
        IDispatcher.decode(data);
        assertEquals(original, data.toString());
    }

    @Test
    void excessiveNestedStringEnvelopesAreRejected() {
        assertThrows(RuntimeException.class, () -> IDispatcher.decode(wrapped(payload(), 5)));
    }

    @Test
    void malformedStructuredJsonIsRejected() {
        assertThrows(
                RuntimeException.class, () -> IDispatcher.decode(new JsonPrimitive("{broken")));
    }
}
