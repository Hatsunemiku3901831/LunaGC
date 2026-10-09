package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.*;
import emu.grasscutter.game.beyond.proto.assets.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraCameraJsonTest {
    @TempDir Path directory;

    static UnknownFieldSet unknown(int field) {
        return UnknownFieldSet.newBuilder()
                .addField(field, UnknownFieldSet.Field.newBuilder().addVarint(123).build())
                .build();
    }

    static byte[] container(byte[] payload) {
        return ByteBuffer.allocate(payload.length + 24)
                .putInt(payload.length + 20)
                .putInt(1)
                .putInt(0x326)
                .putInt(3)
                .putInt(payload.length)
                .put(payload)
                .putInt(0x679)
                .array();
    }

    static JsonObject doc(byte[] source) {
        return JsonParser.parseString(
                        new String(MiliastraCameraJson.decode(source), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    static byte[] encode(JsonObject doc) {
        return MiliastraCameraJson.encode(new Gson().toJson(doc).getBytes(StandardCharsets.UTF_8));
    }

    static JsonObject row(JsonObject doc, int n) {
        return doc.getAsJsonArray("cameras").get(n).getAsJsonObject();
    }

    static JsonObject data(JsonObject doc, int n) {
        return row(doc, n).getAsJsonObject("data");
    }

    static MiliastraAssetCollection.GIACollection root(byte[] source) throws Exception {
        return MiliastraAssetCollection.GIACollection.parseFrom(
                Arrays.copyOfRange(source, 20, source.length - 4));
    }

    static MiliastraAssetDefinitions.Asset camera() {
        var v =
                MiliastraAssetCamera.ViewpointOffset.newBuilder()
                        .setX(.6000001f)
                        .setY(.3f)
                        .setZ(-0.0f)
                        .setUnknownFields(unknown(88));
        var d =
                MiliastraAssetCamera.CameraData.newBuilder()
                        .setName("镜头 <测试>")
                        .setCameraMode(4)
                        .setDefaultDistance(6)
                        .setMinDistance(1)
                        .setMaxDistance(30)
                        .setFieldOfView(45)
                        .setViewpointOffset(v)
                        .setFollowRotation(true)
                        .setIgnoreCollision(.25f)
                        .setField14(.8f)
                        .setMinHorizontalAngle(-180)
                        .setMaxHorizontalAngle(180)
                        .setMinPitchAngle(-89)
                        .setMaxPitchAngle(89)
                        .setUnknownFields(unknown(89));
        var first =
                MiliastraAssetCamera.CameraDetail.newBuilder()
                        .setCameraId(42)
                        .setCameraData(d)
                        .setUnknownFields(unknown(90));
        var second =
                MiliastraAssetCamera.CameraDetail.newBuilder()
                        .setCameraId(43)
                        .setCameraData(
                                MiliastraAssetCamera.CameraData.newBuilder().setCameraMode(99).setField14(-2));
        return MiliastraAssetDefinitions.Asset.newBuilder()
                .setTypeValue(13)
                .setName("资产名不同")
                .setMeta(
                        MiliastraAssetDefinitions.AssetMeta.newBuilder()
                                .setAssetId(42)
                                .setField2(25)
                                .setUnknownFields(unknown(91)))
                .addDependentAssets(MiliastraAssetDefinitions.AssetMeta.newBuilder().setAssetId(1170000000))
                .setCameraConfig(
                        MiliastraAssetCamera.CameraConfig.newBuilder()
                                .addCameraDetails(first)
                                .addCameraDetails(second)
                                .setUnknownFields(unknown(92)))
                .setUnknownFields(unknown(93))
                .build();
    }

    static byte[] fixture() throws Exception {
        var b = new ByteArrayOutputStream();
        var o = CodedOutputStream.newInstance(b);
        var opaque =
                MiliastraAssetDefinitions.Asset.newBuilder()
                        .setTypeValue(49)
                        .setMeta(MiliastraAssetDefinitions.AssetMeta.newBuilder().setAssetId(55))
                        .setName("环境配置")
                        .setUnknownFields(
                                UnknownFieldSet.newBuilder()
                                        .addField(
                                                77,
                                                UnknownFieldSet.Field.newBuilder()
                                                        .addLengthDelimited(ByteString.copyFromUtf8("opaque content"))
                                                        .build())
                                        .build())
                        .build();
        o.writeByteArray(1, opaque.toByteArray());
        o.flush();
        b.write(new byte[] {(byte) 0x8a, 0});
        o.writeUInt32NoTag(camera().getSerializedSize());
        o.writeRawBytes(camera().toByteArray());
        o.writeString(3, "original/path");
        o.flush();
        b.write(new byte[] {(byte) 0x98, (byte) 0x86, 0, (byte) 0xaa, 0});
        return container(b.toByteArray());
    }

    @Test
    void uneditedProjectionRestoresAllBytesAndFractionalCollision() throws Exception {
        byte[] source = fixture();
        var d = doc(source);
        assertEquals("lunagc.camera.v1", d.get("format").getAsString());
        assertEquals(2, d.getAsJsonArray("cameras").size());
        assertEquals(1, row(d, 0).get("assetIndex").getAsInt());
        assertEquals(.25f, data(d, 0).get("ignoreCollision").getAsFloat());
        assertEquals(99, data(d, 1).get("cameraMode").getAsInt());
        assertArrayEquals(source, encode(d));
        var five =
                MiliastraCamera.encode(
                        MiliastraCamera.model(
                                "{\"cameras\":[{\"preset\":\"twoPointFiveD\"},{\"preset\":\"backCamera\"},{\"preset\":\"firstPerson\"},{\"preset\":\"classic\"},{\"preset\":\"thirdPerson\"}]}"
                                        .getBytes(StandardCharsets.UTF_8)),
                        1000);
        assertArrayEquals(five, encode(doc(five)));
    }

    @Test
    void editsKnownFieldsAndKeepsEveryUnknownLevelAndOtherAsset() throws Exception {
        byte[] source = fixture();
        var document = doc(source);
        var d = data(document, 0);
        d.addProperty("name", "编辑 <名称>");
        d.addProperty("cameraMode", 2);
        d.addProperty("defaultDistance", 12);
        d.addProperty("minDistance", 4);
        d.addProperty("maxDistance", 18);
        d.addProperty("fieldOfView", 55);
        d.addProperty("followRotation", false);
        d.addProperty("field14", .6);
        d.addProperty("ignoreCollision", 1);
        d.addProperty("horizontalAngle", 15);
        d.addProperty("minHorizontalAngle", -10);
        d.addProperty("maxHorizontalAngle", 20);
        d.addProperty("minPitchAngle", -30);
        d.addProperty("maxPitchAngle", 40);
        d.getAsJsonObject("viewpointOffset").addProperty("x", -2);
        d.getAsJsonObject("viewpointOffset").addProperty("y", 3);
        d.getAsJsonObject("viewpointOffset").addProperty("z", 4);
        for (var r : document.getAsJsonArray("cameras"))
            r.getAsJsonObject().addProperty("assetName", "新资产 <名>");
        var before = root(source);
        var after = root(encode(document));
        assertEquals(before.getAssets(0), after.getAssets(0));
        var a = after.getAssets(1);
        var old = before.getAssets(1);
        assertEquals(old.getMeta(), a.getMeta());
        assertEquals(old.getDependentAssetsList(), a.getDependentAssetsList());
        assertEquals(old.getUnknownFields(), a.getUnknownFields());
        assertEquals("新资产 <名>", a.getName());
        var c = a.getCameraConfig();
        assertEquals(old.getCameraConfig().getUnknownFields(), c.getUnknownFields());
        assertEquals(old.getCameraConfig().getCameraDetails(1), c.getCameraDetails(1));
        var first = c.getCameraDetails(0);
        assertEquals(42, first.getCameraId());
        assertEquals(
                old.getCameraConfig().getCameraDetails(0).getUnknownFields(), first.getUnknownFields());
        var edited = first.getCameraData();
        assertEquals("编辑 <名称>", edited.getName());
        assertEquals(2, edited.getCameraMode());
        assertEquals(12, edited.getDefaultDistance());
        assertEquals(55, edited.getFieldOfView());
        assertEquals(1, edited.getIgnoreCollision());
        assertEquals(15, edited.getHorizontalAngle());
        assertEquals(
                old.getCameraConfig().getCameraDetails(0).getCameraData().getUnknownFields(),
                edited.getUnknownFields());
        assertEquals(
                old.getCameraConfig()
                        .getCameraDetails(0)
                        .getCameraData()
                        .getViewpointOffset()
                        .getUnknownFields(),
                edited.getViewpointOffset().getUnknownFields());
        assertEquals(before.getUnknownFields(), after.getUnknownFields());
    }

    @Test
    void unrelatedRootBytesRemainExactWhenOnlyCameraChanges() throws Exception {
        byte[] source = fixture();
        var d = doc(source);
        data(d, 0).addProperty("fieldOfView", 50);
        byte[] result = encode(d);
        var before = CodedInputStream.newInstance(source, 20, source.length - 24);
        var after = CodedInputStream.newInstance(result, 20, result.length - 24);
        assertEquals(10, before.readTag());
        assertEquals(10, after.readTag());
        assertArrayEquals(before.readByteArray(), after.readByteArray());
        assertEquals(10, before.readTag());
        assertEquals(10, after.readTag());
        before.readByteArray();
        after.readByteArray();
        assertArrayEquals(
                Arrays.copyOfRange(source, 20 + before.getTotalBytesRead(), source.length - 4),
                Arrays.copyOfRange(result, 20 + after.getTotalBytesRead(), result.length - 4));
    }

    @Test
    void refusesIdentityReorderingAddingAndDeletingOrConflictingAssetNames() throws Exception {
        var source = fixture();
        for (String field : List.of("assetIndex", "detailIndex", "assetGuid", "cameraGuid")) {
            var d = doc(source);
            row(d, 0).addProperty(field, 999);
            assertThrows(IllegalArgumentException.class, () -> encode(d));
        }
        var removed = doc(source);
        removed.getAsJsonArray("cameras").remove(1);
        assertThrows(IllegalArgumentException.class, () -> encode(removed));
        var added = doc(source);
        added.getAsJsonArray("cameras").add(row(added, 0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> encode(added));
        var names = doc(source);
        row(names, 0).addProperty("assetName", "不同名称");
        assertThrows(IllegalArgumentException.class, () -> encode(names));
    }

    @Test
    void refusesMalformedSchemaMissingFieldsWrongTypesAndDuplicateJsonKeys() throws Exception {
        for (String field : List.of("format", "source", "cameras")) {
            var d = doc(fixture());
            d.remove(field);
            assertThrows(IllegalArgumentException.class, () -> encode(d));
        }
        var d = doc(fixture());
        d.addProperty("source", "bad");
        assertThrows(IllegalArgumentException.class, () -> encode(d));
        var wrong = doc(fixture());
        wrong.addProperty("format", "unknown");
        assertThrows(IllegalArgumentException.class, () -> encode(wrong));
        for (String field : List.of("name", "fieldOfView", "followRotation", "viewpointOffset")) {
            var t = doc(fixture());
            data(t, 0).add(field, JsonNull.INSTANCE);
            assertThrows(IllegalArgumentException.class, () -> encode(t));
        }
        var missing = doc(fixture());
        data(missing, 0).remove("fieldOfView");
        assertThrows(IllegalArgumentException.class, () -> encode(missing));
        var unknown = doc(fixture());
        data(unknown, 0).addProperty("unknown", 1);
        assertThrows(IllegalArgumentException.class, () -> encode(unknown));
        String text =
                new Gson()
                        .toJson(doc(fixture()))
                        .replace("\"format\":", "\"format\":\"duplicate\",\"format\":");
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraCameraJson.encode(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validatesChangedRangesWithoutReinterpretingLegacyValues() throws Exception {
        for (var v :
                Map.of(
                                "fieldOfView",
                                90,
                                "defaultDistance",
                                51,
                                "ignoreCollision",
                                2,
                                "cameraMode",
                                5,
                                "minPitchAngle",
                                90,
                                "minHorizontalAngle",
                                181)
                        .entrySet()) {
            var d = doc(fixture());
            data(d, 0).addProperty(v.getKey(), v.getValue());
            assertThrows(IllegalArgumentException.class, () -> encode(d), v.getKey());
        }
        var d = doc(fixture());
        data(d, 0).addProperty("minDistance", 7);
        assertThrows(IllegalArgumentException.class, () -> encode(d));
        var angles = doc(fixture());
        data(angles, 0).addProperty("minHorizontalAngle", 50);
        data(angles, 0).addProperty("maxHorizontalAngle", 20);
        assertThrows(IllegalArgumentException.class, () -> encode(angles));
        var legacy = doc(fixture());
        data(legacy, 1).addProperty("name", "旧模式只改名称");
        var after = root(encode(legacy));
        assertEquals(
                99,
                after.getAssets(1).getCameraConfig().getCameraDetails(1).getCameraData().getCameraMode());
    }

    @Test
    void boundsFloatsUint32NamesAndInputSize() throws Exception {
        for (var value :
                List.of(new JsonPrimitive(1e100), new JsonPrimitive(new java.math.BigDecimal("1e-100")))) {
            var d = doc(fixture());
            data(d, 0).getAsJsonObject("viewpointOffset").add("x", value);
            assertThrows(IllegalArgumentException.class, () -> encode(d));
        }
        var d = doc(fixture());
        data(d, 0).addProperty("cameraMode", 4294967296L);
        assertThrows(IllegalArgumentException.class, () -> encode(d));
        var n = doc(fixture());
        data(n, 0).addProperty("name", "x".repeat(4097));
        assertThrows(IllegalArgumentException.class, () -> encode(n));
        var unicode = doc(fixture());
        data(unicode, 0).addProperty("name", "\ud800");
        String escaped = new Gson().toJson(unicode).replace("\ud800", "\\ud800");
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraCameraJson.encode(escaped.getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                IllegalArgumentException.class,
                () -> MiliastraCameraJson.encode(new byte[MiliastraFile.MAX_BYTES + 1]));
    }

    @Test
    void rejectsNoCameraNewVersionMissingConfigAndNonfiniteSource() throws Exception {
        assertThrows(
                IllegalArgumentException.class, () -> MiliastraCameraJson.decode(container(new byte[0])));
        var v = fixture();
        ByteBuffer.wrap(v).putInt(4, 2);
        assertThrows(IllegalArgumentException.class, () -> MiliastraCameraJson.decode(v));
        for (var bad :
                List.of(
                        camera().toBuilder().clearCameraConfig().build(),
                        camera().toBuilder()
                                .setCameraConfig(
                                        MiliastraAssetCamera.CameraConfig.newBuilder()
                                                .addCameraDetails(
                                                        MiliastraAssetCamera.CameraDetail.newBuilder()
                                                                .setCameraId(42)
                                                                .setCameraData(
                                                                        MiliastraAssetCamera.CameraData.newBuilder()
                                                                                .setFieldOfView(Float.NaN))))
                                .build())) {
            var payload =
                    MiliastraAssetCollection.GIACollection.newBuilder().addAssets(bad).build().toByteArray();
            assertThrows(
                    IllegalArgumentException.class, () -> MiliastraCameraJson.decode(container(payload)));
        }
    }

    @Test
    void omittedDefaultDataAndOffsetKeepOriginalPresenceUntilEdited() throws Exception {
        var asset =
                camera().toBuilder()
                        .setCameraConfig(
                                MiliastraAssetCamera.CameraConfig.newBuilder()
                                        .addCameraDetails(
                                                MiliastraAssetCamera.CameraDetail.newBuilder().setCameraId(42)))
                        .build();
        byte[] source =
                container(
                        MiliastraAssetCollection.GIACollection.newBuilder()
                                .addAssets(asset)
                                .build()
                                .toByteArray());
        var document = doc(source);
        assertArrayEquals(source, encode(document));
        data(document, 0).addProperty("fieldOfView", 45);
        var edited = root(encode(document)).getAssets(0).getCameraConfig().getCameraDetails(0);
        assertTrue(edited.hasCameraData());
        assertFalse(edited.getCameraData().hasViewpointOffset());
        assertEquals(45, edited.getCameraData().getFieldOfView());
        assertEquals(0, edited.getCameraData().getDefaultDistance());
        data(document, 0).getAsJsonObject("viewpointOffset").addProperty("x", .25);
        assertTrue(
                root(encode(document))
                        .getAssets(0)
                        .getCameraConfig()
                        .getCameraDetails(0)
                        .getCameraData()
                        .hasViewpointOffset());
    }

    @Test
    void archivesProjectionRestartAndEditedNativeFileWithOriginalPreserved() throws Exception {
        var store = new MiliastraAssetStore(directory);
        byte[] source = fixture();
        var original = store.importFile("source.gia", source).asset();
        var json = store.convertCameraSource(original.id());
        assertEquals("CAMERA_JSON", json.asset().type());
        assertTrue(json.asset().filename().endsWith(".camera.json"));
        assertTrue(store.convertCameraSource(original.id()).duplicate());
        store = new MiliastraAssetStore(directory);
        var restored = store.convertNode(json.asset().id());
        assertTrue(restored.duplicate());
        assertEquals(original.id(), restored.asset().id());
        var d = doc(source);
        data(d, 0).addProperty("fieldOfView", 50);
        var edited =
                store.importFile(
                        "edited.camera.json", new Gson().toJson(d).getBytes(StandardCharsets.UTF_8));
        var nativeFile = store.convertNode(edited.asset().id());
        assertFalse(nativeFile.duplicate());
        assertArrayEquals(source, store.download(original.id()).bytes());
        assertEquals(
                50,
                root(store.download(nativeFile.asset().id()).bytes())
                        .getAssets(1)
                        .getCameraConfig()
                        .getCameraDetails(0)
                        .getCameraData()
                        .getFieldOfView());
    }

    @Test
    void cameraJsonOnlyArchivesReserveDependencyAndSecondaryGuids() throws Exception {
        var store = new MiliastraAssetStore(directory);
        store.importFile("only.camera.json", MiliastraCameraJson.decode(fixture()));
        byte[] model = "{\"cameras\":[{}]}".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> store.generateCamera("new.gia", model, 43));
        assertThrows(
                IllegalArgumentException.class, () -> store.generateCamera("new.gia", model, 1170000000));
        assertEquals(1170000001, store.generateCamera("auto.gia", model, null).firstAutomaticGuid());
    }

    @Test
    void extractsCameraAfterPreviewAndCompleteRelatedCamera() throws Exception {
        var b = new ByteArrayOutputStream();
        var o = CodedOutputStream.newInstance(b);
        for (int i = 0; i < 100; i++)
            o.writeByteArray(
                    1,
                    MiliastraAssetDefinitions.Asset.newBuilder()
                            .setTypeValue(49)
                            .setMeta(MiliastraAssetDefinitions.AssetMeta.newBuilder().setAssetId(1000 + i))
                            .build()
                            .toByteArray());
        o.writeByteArray(2, camera().toByteArray());
        o.flush();
        byte[] source = container(b.toByteArray());
        assertEquals(100, MiliastraFile.inspect(source, "test.gia").assets().size());
        var d = doc(source);
        assertEquals(100, row(d, 0).get("assetIndex").getAsInt());
        assertArrayEquals(source, encode(d));
        data(d, 0).addProperty("fieldOfView", 50);
        assertNotEquals(
                Base64.getEncoder().encodeToString(source), Base64.getEncoder().encodeToString(encode(d)));
    }
}
