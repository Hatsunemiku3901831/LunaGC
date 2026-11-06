import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.proto.QueryCurrRegionHttpRspOuterClass.QueryCurrRegionHttpRsp;
import emu.grasscutter.net.proto.QueryRegionListHttpRspOuterClass.QueryRegionListHttpRsp;
import emu.grasscutter.server.http.HttpServer;
import emu.grasscutter.server.http.dispatch.RegionHandler;
import emu.grasscutter.utils.Crypto;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/** Actual CN72 HTTP request against the fixed pre-migration JAR; no version override. */
public final class Client72HttpRouteAudit {
    private static final String JAR = "dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3";
    private static final List<String> PINNED_CLASSES = List.of(
        "emu/grasscutter/server/http/HttpServer", "emu/grasscutter/server/http/dispatch/RegionHandler",
        "emu/grasscutter/server/http/objects/QueryCurRegionRspJson", "emu/grasscutter/utils/Crypto",
        "emu/grasscutter/GameConstants",
        "emu/grasscutter/utils/Utils", "emu/grasscutter/utils/FileUtils", "emu/grasscutter/utils/JsonUtils",
        "emu/grasscutter/config/Configuration", "emu/grasscutter/config/ConfigContainer",
        "emu/grasscutter/server/event/Event", "emu/grasscutter/server/event/dispatch/QueryAllRegionsEvent",
        "emu/grasscutter/server/event/dispatch/QueryCurrentRegionEvent",
        "emu/grasscutter/net/proto/QueryCurrRegionHttpRspOuterClass",
        "emu/grasscutter/net/proto/QueryRegionListHttpRspOuterClass",
        "emu/grasscutter/net/proto/RegionInfoOuterClass", "emu/grasscutter/net/proto/RegionSimpleInfoOuterClass");

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("repository required; run from a fresh isolated work directory");
        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        Path work = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        require(work.startsWith(repo.resolve("local/migration72")) && !Files.exists(work.resolve("result.json")), "fresh isolated output required");
        require(System.getenv("LUNAGC_RESOURCES_DIR") == null || System.getenv("LUNAGC_RESOURCES_DIR").isBlank(), "clear resource override");
        Path jar = Path.of(HttpServer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        require(sha(jar).equals(JAR), "fixed JAR mismatch");
        Map<String, String> classHashes = checkClasses(repo, jar);
        var config = Grasscutter.getConfig();
        require(Grasscutter.configFile.getCanonicalFile().toPath().equals(work.resolve("config.json")), "isolated config required");
        for (String name : List.of(config.folderStructure.resources, config.folderStructure.data, config.folderStructure.cache)) {
            require(Path.of(name).toAbsolutePath().normalize().startsWith(work), "isolated folders required");
        }
        require(config.server.http.bindAddress.equals("127.0.0.1") && config.server.http.bindPort == 0, "loopback ephemeral listener required");
        require(!config.server.http.encryption.useEncryption && !config.server.http.encryption.useInRouting, "local HTTP fixture configuration");
        require(config.server.game.useXorEncryption, "formal dispatch encryption branch required");
        require(Grasscutter.getGameServer() == null && Grasscutter.getDispatchServer() == null && Grasscutter.getPluginManager() == null, "game/dispatch/plugins absent");
        Crypto.loadKeys();
        require(Crypto.CUR_SIGNING_KEY != null && Crypto.EncryptionKeys.containsKey(4), "real packaged keys unavailable");
        require(GameConstants.VERSION.equals("7.1.0"), "fixed archive version changed; do not override admission");
        HttpServer server = new HttpServer();
        int port = 0;
        var rows = new ArrayList<Map<String, Object>>();
        try {
            new RegionHandler().applyRoutes(server.getHandle());
            server.start();
            port = server.getHandle().port();
            require(port > 0 && port != 8088 && port != 22101 && port != 27017 && port != 27027, "isolated assigned port");
            config.server.http.accessPort = port;
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            URI base = URI.create("http://127.0.0.1:" + port);
            var listResponse = get(client, base.resolve("/query_region_list?version=CNRELWin7.2.0&platform=1"));
            require(listResponse.statusCode() == 200, "region list HTTP status");
            Files.writeString(work.resolve("region-list.base64.txt"), listResponse.body());
            byte[] listWire = Base64.getDecoder().decode(listResponse.body());
            Files.write(work.resolve("region-list.response.bin"), listWire);
            var list = QueryRegionListHttpRsp.parseFrom(listWire);
            require(list.getRetcode() == 0 && list.getRegionListCount() == 1 && list.getEnableLoginPc(), "actual region list fields");
            var entry = list.getRegionList(0);
            require(entry.getName().equals("os_usa") && entry.getTitle().equals("os_usa") && entry.getType().equals("DEV_PUBLIC"), "default actual region identity");
            require(entry.getDispatchUrl().equals(base + "/query_cur_region/os_usa"), "actual current-region URL");
            require(Arrays.equals(list.getClientSecretKey().toByteArray(), Crypto.DISPATCH_SEED), "actual list seed");
            byte[] cnConfig = list.getClientCustomConfigEncrypted().toByteArray();
            Crypto.xor(cnConfig, Crypto.DISPATCH_KEY);
            JsonObject custom = JsonParser.parseString(new String(cnConfig, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            require(custom.get("sdkenv").getAsString().equals("0"), "CN configuration branch");
            rows.add(Map.of("name", "region-list", "httpStatus", listResponse.statusCode(), "regions", 1,
                "wireSHA256", sha(listWire), "cnSdkenv", "0"));

            URI currentUri = URI.create(entry.getDispatchUrl() + "?version=CNRELWin7.2.0&key_id=4&dispatchSeed=fixture");
            var currentResponse = get(client, currentUri);
            require(currentResponse.statusCode() == 200, "current region HTTP status");
            Files.writeString(work.resolve("current-region.json"), currentResponse.body());
            JsonObject currentJson = JsonParser.parseString(currentResponse.body()).getAsJsonObject();
            require(currentJson.keySet().equals(java.util.Set.of("content", "sign")), "actual response JSON envelope");
            byte[] cipher = Base64.getDecoder().decode(currentJson.get("content").getAsString());
            byte[] signature = Base64.getDecoder().decode(currentJson.get("sign").getAsString());
            require(cipher.length > 0 && cipher.length % 256 == 0 && signature.length == 256, "encrypted and signed response lengths");
            QueryCurrRegionHttpRsp baseQuery = RegionHandler.getCurrentRegion().toBuilder()
                .setRegionInfo(RegionHandler.getCurrentRegion().getRegionInfo().toBuilder()
                    .setGateserverIp(config.server.game.accessAddress).setGameBiz("hk4e_cn")).build();
            require(baseQuery.getRegionInfo().getGateserverPort() == 22101, "fixed game port advertised only; no game listener");
            require(Arrays.equals(baseQuery.getClientSecretKey().toByteArray(), Crypto.DISPATCH_SEED), "actual base-query seed");
            // This base query precedes admission. It is not the actual signed plaintext:
            // the fixed archive rejects a 7.2 request and constructs a separate error body.
            Files.write(work.resolve("current-region.base-query.bin"), baseQuery.toByteArray());
            JsonObject bad = currentJson.deepCopy();
            signature[0] ^= 1;
            bad.addProperty("sign", Base64.getEncoder().encodeToString(signature));
            Files.writeString(work.resolve("current-region.bad-sign.json"), bad.toString());
            rows.add(Map.of("name", "current-region", "httpStatus", currentResponse.statusCode(),
                "cipherBytes", cipher.length, "signatureBytes", signature.length,
                "keyId", 4, "dispatchSeedPresent", true, "expectedAdmission", "version-rejection",
                "nativeSignatureAndDecryption", "pending"));
        } finally {
            server.getHandle().stop();
        }
        var result = new TreeMap<String, Object>();
        result.put("task", "D-01b5h"); result.put("jarSHA256", JAR); result.put("matchedClassSHA256", classHashes);
        result.put("javaPid", ProcessHandle.current().pid());
        result.put("sourceSHA256", sha(repo.resolve("tools/Client72HttpRouteAudit.java")));
        result.put("requestedVersion", "CNRELWin7.2.0"); result.put("actualArchiveVersion", GameConstants.VERSION);
        result.put("httpPort", port); result.put("cases", rows); result.put("normalHttpStopCompleted", true);
        result.put("gameServerStarted", false); result.put("databaseStarted", false); result.put("runtimeAcceptance", false);
        result.put("scope", "Actual isolated CN72 HTTP requests against fixed 7.1 archive; native decryption/signature and admission verification separate; no version override");
        Files.writeString(work.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        System.out.println("actualHttpRoutes=" + rows.size() + " normalStop=true nativeVerification=pending");
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, String> checkClasses(Path repo, Path jar) throws Exception {
        var hashes = new TreeMap<String, String>();
        try (var zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); String name = entry.getName();
                if (!name.endsWith(".class") || PINNED_CLASSES.stream().noneMatch(x -> name.equals(x + ".class") || name.startsWith(x + "$"))) continue;
                byte[] bytes = zip.getInputStream(entry).readAllBytes();
                require(Arrays.equals(bytes, Files.readAllBytes(repo.resolve("build/classes/java/main").resolve(name))), "current class differs: " + name);
                hashes.put(name, sha(bytes));
            }
        }
        for (String name : PINNED_CLASSES) require(hashes.containsKey(name + ".class"), "missing pinned class " + name);
        return hashes;
    }

    private static String sha(Path path) throws Exception { return sha(Files.readAllBytes(path)); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
