package emu.grasscutter.game.beyond;

import emu.grasscutter.utils.JsonUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Content-addressed source archives. Import never registers or executes a game asset. */
public final class MiliastraAssetStore {
    private static final int MAX_FILES = 2000;
    private static final long MAX_STORAGE = 512L * 1024 * 1024;
    private static final int MAX_METADATA = 4 * 1024 * 1024;
    private final Path directory;

    public record Entry(
            String id,
            String filename,
            String type,
            String name,
            long bytes,
            String importedAt,
            MiliastraFile.Preview binary,
            MiliastraGraph.Preview graph,
            List<String> warnings) {}

    public record ImportResult(Entry asset, boolean duplicate) {}

    public record Listing(int total, int offset, int limit, List<Entry> assets, int unreadable) {}

    public record Download(String filename, byte[] bytes) {}

    public record Generated(Entry asset, int count, int automaticCount, int firstAutomaticGuid) {}

    public record Remapped(
            Entry asset,
            boolean duplicate,
            int guidCount,
            int changedFields,
            List<MiliastraGuid.Pair> mapping,
            List<String> warnings) {}

    public synchronized Remapped remap(String id, Integer requested) throws IOException {
        var source = download(id);
        var entry = metadata(path(id));
        if (!Set.of("GIA", "GIA_JSON").contains(entry.type()))
            throw new IllegalArgumentException("请选择 GIA 或节点结构 .gia.json 文件。");
        byte[] bytes =
                entry.type().equals("GIA_JSON") ? MiliastraNodeJson.encode(source.bytes()) : source.bytes();
        var analysis = MiliastraGuid.analyze(bytes);
        if (requested != null && requested < 1) throw new IllegalArgumentException("GUID 起点应为正 int32。");
        String base = source.filename().replaceFirst("(?i)\\.gia(?:\\.json)?$", "");
        String suffix =
                "-"
                        + id.substring(0, 12)
                        + "-remapped"
                        + (requested == null ? "" : "-" + requested)
                        + ".gia";
        if (base.length() + suffix.length() > 180) base = base.substring(0, 180 - suffix.length());
        String filename = base + suffix;
        var listing = list("", "", 0, MAX_FILES);
        if (listing.unreadable() > 0)
            throw new IOException("Unreadable archives prevent safe GUID remapping");
        var occupied = new HashSet<>(analysis.references());
        long highest = 1078000000L - 1;
        for (var existing : listing.assets()) {
            if (!Set.of("GIA", "GIA_JSON", "CAMERA_JSON").contains(existing.type())) continue;
            byte[] archived = binarySource(existing);
            if (existing.type().equals("GIA") && existing.filename().equals(filename)) {
                var previous = MiliastraGuid.analyze(archived);
                if (previous.definitions().size() == analysis.definitions().size()
                        && (requested == null || previous.definitions().get(0) == requested.longValue())
                        && java.util.stream.IntStream.range(0, previous.definitions().size())
                                .allMatch(
                                        i -> previous.definitions().get(i) == previous.definitions().get(0) + i)) {
                    var mapping = new LinkedHashMap<Long, Long>();
                    for (int i = 0; i < analysis.definitions().size(); i++)
                        mapping.put(analysis.definitions().get(i), previous.definitions().get(i));
                    var restored = MiliastraGuid.remap(bytes, mapping);
                    if (sha256(restored.bytes()).equals(existing.id()))
                        return new Remapped(
                                existing,
                                true,
                                restored.guidCount(),
                                restored.changedFields(),
                                restored.mapping(),
                                restored.warnings());
                }
            }
            occupied.addAll(MiliastraGuid.knownReferences(archived));
        }
        for (long guid : occupied) if (guid <= Integer.MAX_VALUE) highest = Math.max(highest, guid);
        long first = requested == null ? highest + 1 : requested;
        if (first < 1 || first + analysis.definitions().size() - 1 > Integer.MAX_VALUE)
            throw new IllegalArgumentException("自动 GUID 或指定范围超过 int32 上限，请指定未占用的起点。");
        for (long guid : occupied)
            if (guid >= first && guid < first + analysis.definitions().size())
                throw new IllegalArgumentException("GUID 与已有资产或已知引用重合：" + guid + "。");
        var result = MiliastraGuid.remap(bytes, (int) first);
        var imported = importFile(filename, result.bytes());
        return new Remapped(
                imported.asset(),
                imported.duplicate(),
                result.guidCount(),
                result.changedFields(),
                result.mapping(),
                result.warnings());
    }

    public synchronized Generated generateModel(String filename, byte[] model, Integer firstGuid)
            throws IOException {
        return generate(filename, MiliastraGenerator.model(model), firstGuid);
    }

    public synchronized Generated generateImage(
            String filename, byte[] image, MiliastraGenerator.ImageOptions options, Integer firstGuid)
            throws IOException {
        return generate(filename, MiliastraGenerator.image(image, options), firstGuid);
    }

    private Generated generate(
            String filename, List<MiliastraGenerator.Block> blocks, Integer requested)
            throws IOException {
        return generateAssets(
                filename,
                blocks.stream().map(MiliastraGenerator.Block::guid).toList(),
                requested,
                "实体",
                false,
                first -> MiliastraGenerator.encode(blocks, first));
    }

    public synchronized Generated generateCamera(String filename, byte[] model, Integer firstGuid)
            throws IOException {
        var cameras = MiliastraCamera.model(model);
        return generateAssets(
                filename,
                cameras.stream().map(MiliastraCamera.Camera::guid).toList(),
                firstGuid,
                "镜头",
                true,
                first -> MiliastraCamera.encode(cameras, first));
    }

    public synchronized Generated generateVoxel(
            String filename, byte[] model, MiliastraVoxel.Options options, Integer firstGuid)
            throws IOException {
        var blocks = MiliastraVoxel.model(model, options);
        return generateAssets(
                filename,
                blocks.stream().map(MiliastraGenerator.Block::guid).toList(),
                firstGuid,
                "体素",
                true,
                first -> MiliastraGenerator.encode(blocks, first));
    }

    @FunctionalInterface
    private interface AssetEncoder {
        byte[] encode(int first) throws IOException;
    }

    private Generated generateAssets(
            String filename,
            List<Integer> guidsToAssign,
            Integer requested,
            String label,
            boolean reserveReferences,
            AssetEncoder encoder)
            throws IOException {
        filename = validateFilename(filename);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".gia"))
            throw new IllegalArgumentException("生成文件名必须以 .gia 结尾。");
        if (requested != null && requested < 1) throw new IllegalArgumentException("GUID 起点应为正 int32。");
        var explicit = new HashSet<Long>();
        for (int guid : guidsToAssign)
            if (guid != 0 && !explicit.add((long) guid)) {
                throw new IllegalArgumentException(label + " JSON 中的 GUID 重复。");
            }
        long count = guidsToAssign.stream().filter(guid -> guid == 0).count();
        long highest = 1078000000L - 1;
        for (long guid : explicit) highest = Math.max(highest, guid);
        var listing = list("", "", 0, MAX_FILES);
        if (listing.unreadable() > 0)
            throw new IOException("Unreadable archives prevent safe GUID allocation");
        for (var entry : listing.assets()) {
            if (entry.binary() == null) continue;
            var guids =
                    reserveReferences
                            ? MiliastraGuid.knownReferences(binarySource(entry))
                            : primaryGuids(entry);
            for (long guid : guids) {
                if (explicit.contains(guid)
                        || (requested != null && guid >= requested && guid < requested + count)) {
                    throw new IllegalArgumentException("GUID 与已有归档重合：" + guid + "。请使用自动分配或其他起点。");
                }
                if (guid <= Integer.MAX_VALUE) highest = Math.max(highest, guid);
            }
        }
        long first = requested == null ? (count == 0 ? 1078000000L : highest + 1) : requested;
        if (first > Integer.MAX_VALUE || first + count > (long) Integer.MAX_VALUE + 1) {
            throw new IllegalArgumentException("自动 GUID 已达 int32 上限，请指定其他未占用的起点。");
        }
        if (explicit.stream().anyMatch(guid -> guid >= first && guid < first + count)) {
            throw new IllegalArgumentException("自动 GUID 范围与本次指定的" + label + " GUID 重合。");
        }
        var imported = importFile(filename, encoder.encode((int) first));
        return new Generated(imported.asset(), guidsToAssign.size(), (int) count, (int) first);
    }

    public MiliastraAssetStore(Path directory) {
        this.directory = directory;
    }

    public synchronized ImportResult convertNode(String id) throws IOException {
        var source = download(id);
        var entry = metadata(path(id));
        if (entry.type().equals("CAMERA_JSON"))
            return importFile(
                    source.filename().substring(0, source.filename().length() - 12) + ".gia",
                    MiliastraCameraJson.encode(source.bytes()));
        if (entry.type().equals("GRAPH_JSON")) return convertCanvas(id, source);
        if (entry.type().equals("GIA_JSON")) {
            return importFile(
                    source.filename().substring(0, source.filename().length() - 5),
                    MiliastraNodeJson.encode(source.bytes()));
        }
        if (!entry.type().equals("GIA"))
            throw new IllegalArgumentException("请选择节点 GIA、.gia.json 结构文件或编辑器画布 JSON。");
        String filename = source.filename();
        if (filename.length() + 5 > 180) filename = filename.substring(0, 171) + ".gia";
        return importFile(filename + ".json", MiliastraNodeJson.decode(source.bytes()));
    }

    public synchronized ImportResult convertCanvasSource(String id) throws IOException {
        var source = download(id);
        var entry = metadata(path(id));
        if (!Set.of("GIA", "GIA_JSON").contains(entry.type()))
            throw new IllegalArgumentException("请选择节点 GIA 或节点结构 JSON。");
        byte[] gia =
                entry.type().equals("GIA_JSON") ? MiliastraNodeJson.encode(source.bytes()) : source.bytes();
        String base = source.filename().replaceFirst("(?i)\\.gia(?:\\.json)?$", "");
        if (base.length() > 155) base = base.substring(0, 155);
        return importFile(
                base + "-" + id.substring(0, 12) + ".canvas.json", MiliastraCanvasReverse.decode(gia));
    }

    public synchronized ImportResult convertCameraSource(String id) throws IOException {
        var source = download(id);
        if (!metadata(path(id)).type().equals("GIA"))
            throw new IllegalArgumentException("请选择镜头 GIA 文件。");
        String base = source.filename().substring(0, source.filename().length() - 4);
        if (base.length() > 155) base = base.substring(0, 155);
        return importFile(
                base + "-" + id.substring(0, 12) + ".camera.json",
                MiliastraCameraJson.decode(source.bytes()));
    }

    private ImportResult convertCanvas(String sourceId, Download source) throws IOException {
        if (MiliastraGraph.readJson(source.bytes()).getAsJsonObject().has("giaSource")) {
            int guid = MiliastraCanvasReverse.sourceGuid(source.bytes());
            String base = source.filename().substring(0, source.filename().length() - 5);
            if (base.length() > 176) base = base.substring(0, 176);
            return importFile(base + ".gia", MiliastraCanvas.encode(source.bytes(), guid));
        }
        boolean server = MiliastraCanvas.server(source.bytes());
        long base = server ? 0x40000000L : 0x40800000L;
        int range = 0x999A;
        int start = (int) (Long.parseLong(sourceId.substring(0, 8), 16) % range);
        String basename = source.filename().substring(0, source.filename().length() - 5);
        if (basename.length() > 162) basename = basename.substring(0, 162);
        String filename = basename + "-" + sourceId.substring(0, 12) + ".gia";
        var listing = list("", "", 0, MAX_FILES);
        if (listing.unreadable() > 0)
            throw new IOException("Unreadable archives prevent safe graph GUID allocation");
        var occupied = new HashSet<Long>();
        for (var existing : listing.assets()) {
            if (existing.binary() == null) continue;
            var guids = primaryGuids(existing);
            if (existing.type().equals("GIA")
                    && existing.filename().equals(filename)
                    && guids.size() == 1) {
                long guid = guids.iterator().next();
                if (guid >= base && guid < base + range) {
                    byte[] bytes = MiliastraCanvas.encode(source.bytes(), (int) guid);
                    if (sha256(bytes).equals(existing.id())) return importFile(filename, bytes);
                }
            }
            occupied.addAll(guids);
        }
        for (int i = 0; i < range; i++) {
            int guid = (int) (base + (start + i) % range);
            if (!occupied.contains((long) guid))
                return importFile(filename, MiliastraCanvas.encode(source.bytes(), guid));
        }
        throw new IllegalArgumentException("参考协议的图 GUID 范围已被占满，请整理归档或重映射已有文件。");
    }

    private Set<Long> primaryGuids(Entry entry) throws IOException {
        var source = download(entry.id());
        boolean json = entry.type().equals("GIA_JSON");
        return MiliastraFile.inspectDetailed(
                        entry.type().equals("CAMERA_JSON")
                                ? MiliastraCameraJson.encode(source.bytes())
                                : json ? MiliastraNodeJson.encode(source.bytes()) : source.bytes(),
                        json
                                ? source.filename().substring(0, source.filename().length() - 5)
                                : entry.type().equals("CAMERA_JSON") ? "source.gia" : source.filename())
                .primaryGuids();
    }

    private byte[] binarySource(Entry entry) throws IOException {
        byte[] bytes = download(entry.id()).bytes();
        return entry.type().equals("CAMERA_JSON")
                ? MiliastraCameraJson.encode(bytes)
                : entry.type().equals("GIA_JSON") ? MiliastraNodeJson.encode(bytes) : bytes;
    }

    public synchronized ImportResult importFile(String filename, byte[] bytes) throws IOException {
        filename = validateFilename(filename);
        if (bytes.length > MiliastraFile.MAX_BYTES)
            throw new IllegalArgumentException("单文件最大为 32 MiB。");
        String lower = filename.toLowerCase(Locale.ROOT);
        MiliastraFile.Preview binary = null;
        Set<Long> primaryGuids = Set.of();
        MiliastraGraph.Preview graph = null;
        boolean nodeJson = lower.endsWith(".gia.json");
        boolean cameraJson = lower.endsWith(".camera.json");
        if (cameraJson) {
            var inspection =
                    MiliastraFile.inspectDetailed(MiliastraCameraJson.encode(bytes), "source.gia");
            binary = inspection.preview();
            primaryGuids = inspection.primaryGuids();
        } else if (nodeJson) {
            byte[] encoded = MiliastraNodeJson.encode(bytes);
            var inspection =
                    MiliastraFile.inspectDetailed(encoded, filename.substring(0, filename.length() - 5));
            binary = inspection.preview();
            primaryGuids = inspection.primaryGuids();
            graph = MiliastraNodeJson.inspect(bytes);
        } else if (lower.endsWith(".json")) graph = MiliastraGraph.inspect(bytes);
        else {
            var inspection = MiliastraFile.inspectDetailed(bytes, filename);
            binary = inspection.preview();
            primaryGuids = inspection.primaryGuids();
        }
        String id = sha256(bytes);
        Path target = path(id);
        if (Files.exists(target)) {
            var existing = metadata(target);
            download(id); // A damaged existing archive must not be reported as a successful duplicate.
            return new ImportResult(existing, true);
        }
        var listing = list("", "", 0, MAX_FILES);
        long used = listing.assets().stream().mapToLong(Entry::bytes).sum();
        if (listing.total() + listing.unreadable() >= MAX_FILES || used + bytes.length > MAX_STORAGE) {
            throw new IllegalArgumentException("资产库达到 2000 个文件或 512 MiB 存储上限，请先整理本地归档。");
        }
        var warnings = new ArrayList<>(binary == null ? graph.warnings() : binary.warnings());
        if (binary != null && graph != null) warnings.addAll(graph.warnings());
        if (binary != null) {
            for (var existing : listing.assets()) {
                if (existing.binary() == null) continue;
                var guids = primaryGuids(existing);
                if (!Collections.disjoint(primaryGuids, guids)) {
                    if (warnings.size() < 100)
                        warnings.add("与另一归档的 GUID 重合：" + existing.filename() + "。合并存档前需在编辑器处理 ID 冲突。");
                }
            }
        }
        var entry =
                new Entry(
                        id,
                        filename,
                        cameraJson
                                ? "CAMERA_JSON"
                                : nodeJson ? "GIA_JSON" : binary == null ? "GRAPH_JSON" : binary.type(),
                        binary == null ? graph.name() : binary.name(),
                        bytes.length,
                        Instant.now().toString(),
                        binary,
                        graph,
                        List.copyOf(warnings));
        byte[] metadata = JsonUtils.encode(entry).getBytes(StandardCharsets.UTF_8);
        if (metadata.length > MAX_METADATA) {
            throw new IllegalArgumentException("资产元数据超过 4 MiB，未保存此文件。");
        }
        Files.createDirectories(directory);
        Path pending = Files.createTempDirectory(directory, ".import-");
        try {
            Files.write(pending.resolve("source.bin"), bytes);
            Files.write(pending.resolve("metadata.json"), metadata);
            try {
                Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException | DirectoryNotEmptyException e) {
                // Another writer may archive the same content; preserve its complete directory.
                var existing = metadata(target);
                download(id);
                return new ImportResult(existing, true);
            }
            return new ImportResult(entry, false);
        } finally {
            Files.deleteIfExists(pending.resolve("metadata.json"));
            Files.deleteIfExists(pending.resolve("source.bin"));
            Files.deleteIfExists(pending);
        }
    }

    public synchronized Listing list(String query, String type, int offset, int limit)
            throws IOException {
        if (offset < 0 || limit < 1 || limit > MAX_FILES)
            throw new IllegalArgumentException("分页参数不合法。");
        String search = query.toLowerCase(Locale.ROOT);
        var entries = new ArrayList<Entry>();
        int unreadable = 0;
        if (Files.exists(directory)) {
            try (var paths = Files.list(directory)) {
                for (var path : paths.sorted().toList()) {
                    if (!path.getFileName().toString().matches("[0-9a-f]{64}")) continue;
                    try {
                        var entry = metadata(path);
                        if ((type.isBlank() || entry.type().equals(type))
                                && (entry.filename() + " " + entry.name() + " " + entry.id())
                                        .toLowerCase(Locale.ROOT)
                                        .contains(search)) entries.add(entry);
                    } catch (IOException | RuntimeException e) {
                        unreadable++;
                    }
                }
            }
        }
        entries.sort(Comparator.comparing(Entry::importedAt).reversed().thenComparing(Entry::id));
        int start = Math.min(offset, entries.size()),
                end = (int) Math.min((long) start + limit, entries.size());
        return new Listing(
                entries.size(), offset, limit, List.copyOf(entries.subList(start, end)), unreadable);
    }

    public synchronized Download download(String id) throws IOException {
        Path target = path(id);
        if (!Files.exists(target)) throw new NoSuchFileException(id);
        var entry = metadata(target);
        long size = Files.size(target.resolve("source.bin"));
        if (size > MiliastraFile.MAX_BYTES || size != entry.bytes())
            throw new IOException("Archive size mismatch");
        byte[] bytes = Files.readAllBytes(target.resolve("source.bin"));
        if (!sha256(bytes).equals(id)) throw new IOException("Archive checksum mismatch");
        return new Download(entry.filename(), bytes);
    }

    private Entry metadata(Path path) throws IOException {
        Path file = path.resolve("metadata.json");
        if (Files.size(file) > MAX_METADATA) throw new IOException("Archive metadata exceeds limit");
        final Entry entry;
        try {
            var json = MiliastraGraph.readJson(Files.readAllBytes(file)).getAsJsonObject();
            MiliastraFile.Preview binary = null;
            MiliastraGraph.Preview graph = null;
            if (json.has("binary")) {
                var data = json.getAsJsonObject("binary");
                var assets = new ArrayList<MiliastraFile.Asset>();
                for (var row : data.getAsJsonArray("assets")) {
                    var asset = row.getAsJsonObject();
                    assets.add(
                            new MiliastraFile.Asset(
                                    asset.get("guid").getAsLong(),
                                    asset.get("category").getAsLong(),
                                    asset.get("type").getAsLong(),
                                    asset.get("name").getAsString(),
                                    asset.get("related").getAsBoolean()));
                }
                binary =
                        new MiliastraFile.Preview(
                                data.get("type").getAsString(),
                                data.get("version").getAsLong(),
                                data.get("name").getAsString(),
                                data.get("payloadBytes").getAsInt(),
                                data.has("assetCount") ? data.get("assetCount").getAsInt() : assets.size(),
                                List.copyOf(assets),
                                strings(data.getAsJsonArray("warnings")));
            }
            if (json.has("graph")) {
                var data = json.getAsJsonObject("graph");
                graph =
                        new MiliastraGraph.Preview(
                                data.get("name").getAsString(),
                                data.get("environment").getAsString(),
                                data.get("nodes").getAsInt(),
                                data.get("edges").getAsInt(),
                                strings(data.getAsJsonArray("warnings")));
            }
            entry =
                    new Entry(
                            json.get("id").getAsString(),
                            json.get("filename").getAsString(),
                            json.get("type").getAsString(),
                            json.get("name").getAsString(),
                            json.get("bytes").getAsLong(),
                            json.get("importedAt").getAsString(),
                            binary,
                            graph,
                            strings(json.getAsJsonArray("warnings")));
        } catch (RuntimeException e) {
            throw new IOException("Invalid archive metadata", e);
        }
        if (entry == null
                || !path.getFileName().toString().equals(entry.id())
                || entry.filename() == null
                || entry.name() == null
                || entry.type() == null
                || entry.importedAt() == null
                || entry.bytes() < 0
                || entry.bytes() > MiliastraFile.MAX_BYTES
                || entry.warnings() == null) {
            throw new IOException("Incomplete archive metadata");
        }
        return entry;
    }

    private static List<String> strings(com.google.gson.JsonArray array) {
        var values = new ArrayList<String>();
        for (var value : array) values.add(value.getAsString());
        return List.copyOf(values);
    }

    private Path path(String id) {
        if (id == null || !id.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("资产 ID 应为 SHA-256。");
        return directory.resolve(id);
    }

    private static String validateFilename(String filename) {
        if (filename == null
                || filename.isBlank()
                || filename.length() > 180
                || filename
                        .chars()
                        .anyMatch(c -> c < 32 || c == 127 || c == '/' || c == '\\' || c == ':')) {
            throw new IllegalArgumentException("请提供不含目录的有效文件名。");
        }
        return filename;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
