package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

class MiliastraCanvasTypesTest {
    private final MiliastraCanvasTypes types;

    MiliastraCanvasTypesTest() throws Exception {
        try (var stream = getClass().getResourceAsStream("/miliastra/node-protocol.json")) {
            types =
                    new MiliastraCanvasTypes(
                            MiliastraGraph.readJson(stream.readAllBytes(), 2000000)
                                    .getAsJsonObject()
                                    .getAsJsonObject("data"));
        }
    }

    @Test
    void nativeScalarAndListIdsDistinguishServerAndClient() {
        assertEquals(4, types.code("Bol", true));
        assertEquals(5, types.code("Bol", false));
        assertEquals(5, types.code("Flt", true));
        assertEquals(7, types.code("Flt", false));
        assertEquals(6, types.code("Str", true));
        assertEquals(9, types.code("Str", false));
        assertEquals(13, types.code("L<Ety>", true));
        assertEquals(2, types.code("L<Ety>", false));
        assertEquals(17, types.code("L<E<24>>", false));
    }

    @Test
    void nestedDictionaryAndListConstantsRetainEveryPair() {
        var result =
                types.value(
                        "D<Str,L<Int>>", JsonParser.parseString("[[\"coins\",[1,2]],[\"empty\",[]]]"), true);
        assertEquals(10003, result.get("class").getAsInt());
        assertEquals(
                2,
                result.getAsJsonObject("itemType").getAsJsonObject("type_server").get("kind").getAsInt());
        var pairs = result.getAsJsonObject("bMap").getAsJsonArray("mapPairs");
        assertEquals(2, pairs.size());
        var list =
                pairs
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("bMapPair")
                        .getAsJsonObject("value")
                        .getAsJsonObject("bArray")
                        .getAsJsonArray("entries");
        assertEquals(2, list.size());
        assertEquals(2, list.get(1).getAsJsonObject().getAsJsonObject("bInt").get("val").getAsInt());
    }

    @Test
    void constantsRejectLossyValuesAndDuplicateDictionaryKeys() {
        assertThrows(
                IllegalArgumentException.class, () -> types.value("Int", new JsonPrimitive(1.5), true));
        assertThrows(
                IllegalArgumentException.class,
                () -> types.value("Int", new JsonPrimitive(2147483648L), true));
        assertThrows(
                IllegalArgumentException.class, () -> types.value("Flt", new JsonPrimitive(1e40), true));
        assertThrows(
                IllegalArgumentException.class, () -> types.value("Bol", new JsonPrimitive(1), false));
        assertThrows(
                IllegalArgumentException.class,
                () -> types.value("Vec", JsonParser.parseString("[1,2]"), true));
        assertThrows(
                IllegalArgumentException.class,
                () -> types.value("D<Str,Int>", JsonParser.parseString("[[\"a\",1],[\"a\",2]]"), true));
        assertThrows(
                IllegalArgumentException.class, () -> types.value("E<1016>", new JsonPrimitive(0), true));
    }

    @Test
    void enumListsAndVectorObjectsUseClientTypes() {
        var list = types.value("L<E<24>>", JsonParser.parseString("[0,1]"), false);
        assertEquals(
                17, list.getAsJsonObject("itemType").getAsJsonObject("type_client").get("type").getAsInt());
        var vector = types.value("Vec", JsonParser.parseString("{\"x\":1,\"y\":2,\"z\":3}"), false);
        assertEquals(3, vector.getAsJsonObject("bVector").getAsJsonObject("val").get("z").getAsInt());
        assertThrows(
                IllegalArgumentException.class,
                () -> types.value("E<24>", new JsonPrimitive("unknown-enum-label"), false));
    }

    @Test
    void compoundBindingsAreParsedWithoutSplittingInnerTypes() {
        var bindings = MiliastraCanvasTypes.bindings("S<T:D<Str,L<Int>>,U:Flt>");
        assertEquals("L<D<Str,L<Int>>>", MiliastraCanvasTypes.resolve("L<R<T>>", bindings));
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraCanvasTypes.arguments("D<Int,Str>>", 'D'));
        assertThrows(IllegalArgumentException.class, () -> types.code("D<Str,Int>", false));
        assertThrows(IllegalArgumentException.class, () -> types.code("S<?>", true));
    }
}
