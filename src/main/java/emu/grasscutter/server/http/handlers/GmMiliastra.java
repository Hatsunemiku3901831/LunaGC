package emu.grasscutter.server.http.handlers;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.beyond.MiliastraAssetStore;
import emu.grasscutter.game.beyond.MiliastraCamera;
import emu.grasscutter.game.beyond.MiliastraFile;
import emu.grasscutter.game.beyond.MiliastraGenerator;
import emu.grasscutter.game.beyond.MiliastraVoxel;
import io.javalin.http.Context;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.Map;

final class GmMiliastra {
    private final MiliastraAssetStore store;

    GmMiliastra(MiliastraAssetStore store) {
        this.store = store;
    }

    void list(Context ctx) throws IOException {
        int offset = parameter(ctx, "offset", 0), limit = parameter(ctx, "limit", 20);
        if (limit > 100) throw new IllegalArgumentException("每页最多 100 条。");
        ctx.json(store.list(value(ctx, "q"), value(ctx, "type"), offset, limit));
    }

    void palette(Context ctx) {
        ctx.json(
                Map.of(
                        "blocks",
                        MiliastraGenerator.palette(),
                        "imageFormats",
                        javax.imageio.ImageIO.getReaderFormatNames()));
    }

    void generateModel(Context ctx) throws IOException {
        byte[] bytes = read(ctx, 8 * 1024 * 1024);
        if (bytes != null)
            ctx.status(201).json(store.generateModel(filename(ctx), bytes, firstGuid(ctx)));
    }

    void cameraTemplates(Context ctx) {
        ctx.json(MiliastraCamera.templates());
    }

    void generateCamera(Context ctx) throws IOException {
        byte[] bytes = read(ctx, 8 * 1024 * 1024);
        if (bytes != null)
            ctx.status(201).json(store.generateCamera(filename(ctx), bytes, firstGuid(ctx)));
    }

    void generateVoxel(Context ctx) throws IOException {
        byte[] bytes = read(ctx, 8 * 1024 * 1024);
        if (bytes == null) return;
        var options =
                new MiliastraVoxel.Options(
                        decimal(ctx, "unit", .01f),
                        parameter(ctx, "alphaThreshold", 128),
                        new MiliastraGenerator.Vector(
                                decimal(ctx, "x", 0), decimal(ctx, "y", 0), decimal(ctx, "z", 0)));
        ctx.status(201).json(store.generateVoxel(filename(ctx), bytes, options, firstGuid(ctx)));
    }

    void generateImage(Context ctx) throws IOException {
        byte[] bytes = read(ctx, 16 * 1024 * 1024);
        if (bytes == null) return;
        boolean keepAspect = !"false".equals(ctx.queryParam("keepAspect"));
        if (ctx.queryParam("keepAspect") != null
                && !java.util.Set.of("true", "false").contains(ctx.queryParam("keepAspect"))) {
            throw new IllegalArgumentException("keepAspect 应为 true 或 false。");
        }
        var options =
                new MiliastraGenerator.ImageOptions(
                        parameter(ctx, "width", 240),
                        parameter(ctx, "height", 240),
                        keepAspect,
                        decimal(ctx, "unit", .1f),
                        parameter(ctx, "alphaThreshold", 128),
                        ctx.queryParam("plane") == null ? "XY" : ctx.queryParam("plane"),
                        new MiliastraGenerator.Vector(
                                decimal(ctx, "x", 0), decimal(ctx, "y", 0), decimal(ctx, "z", 0)));
        ctx.status(201).json(store.generateImage(filename(ctx), bytes, options, firstGuid(ctx)));
    }

    private static byte[] read(Context ctx, int limit) throws IOException {
        if (ctx.req().getContentLengthLong() > limit) {
            ctx.status(413).json(Map.of("message", "生成输入超过 " + (limit / 1024 / 1024) + " MiB。"));
            return null;
        }
        var bytes = ctx.req().getInputStream().readNBytes(limit + 1);
        if (bytes.length > limit) {
            ctx.status(413).json(Map.of("message", "生成输入超过 " + (limit / 1024 / 1024) + " MiB。"));
            return null;
        }
        return bytes;
    }

    private static String filename(Context ctx) {
        return ctx.queryParam("name") == null ? "generated.gia" : ctx.queryParam("name");
    }

    private static Integer firstGuid(Context ctx) {
        return ctx.queryParam("firstGuid") == null ? null : parameter(ctx, "firstGuid", 0);
    }

    private static float decimal(Context ctx, String key, float fallback) {
        try {
            float value = ctx.queryParam(key) == null ? fallback : Float.parseFloat(ctx.queryParam(key));
            if (!Float.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " 应为有限数值。");
        }
    }

    void upload(Context ctx) throws IOException {
        long length = ctx.req().getContentLengthLong();
        if (length > MiliastraFile.MAX_BYTES) {
            tooLarge(ctx);
            return;
        }
        byte[] bytes = ctx.req().getInputStream().readNBytes(MiliastraFile.MAX_BYTES + 1);
        if (bytes.length > MiliastraFile.MAX_BYTES) {
            tooLarge(ctx);
            return;
        }
        var result = store.importFile(ctx.queryParam("name"), bytes);
        ctx.status(result.duplicate() ? 200 : 201).json(result);
    }

    void convert(Context ctx) throws IOException {
        var result = store.convertNode(ctx.pathParam("id"));
        ctx.status(result.duplicate() ? 200 : 201).json(result);
    }

    void remap(Context ctx) throws IOException {
        var result = store.remap(ctx.pathParam("id"), firstGuid(ctx));
        ctx.status(result.duplicate() ? 200 : 201).json(result);
    }

    void canvas(Context ctx) throws IOException {
        var result = store.convertCanvasSource(ctx.pathParam("id"));
        ctx.status(result.duplicate() ? 200 : 201).json(result);
    }

    void camera(Context ctx) throws IOException {
        var result = store.convertCameraSource(ctx.pathParam("id"));
        ctx.status(result.duplicate() ? 200 : 201).json(result);
    }

    void download(Context ctx) throws IOException {
        var file = store.download(ctx.pathParam("id"));
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        ctx.contentType("application/octet-stream")
                .header(
                        "Content-Disposition",
                        "attachment; filename=\"miliastra-export\"; filename*=UTF-8''" + encoded)
                .header("X-Content-Type-Options", "nosniff")
                .result(file.bytes());
    }

    @FunctionalInterface
    interface Request {
        void run(Context ctx) throws Exception;
    }

    static void request(Context ctx, Request request) throws Exception {
        if (!GmHandler.authorize(ctx)) return;
        ctx.header("Cache-Control", "no-store");
        try {
            request.run(ctx);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("message", e.getMessage() == null ? "资产参数不合法。" : e.getMessage()));
        } catch (NoSuchFileException e) {
            ctx.status(404).json(Map.of("message", "资产不存在。"));
        } catch (Exception e) {
            Grasscutter.getLogger().error("GM Miliastra archive operation failed", e);
            ctx.status(500).json(Map.of("message", "资产操作失败，请检查日志。"));
        }
    }

    private static void tooLarge(Context ctx) {
        ctx.status(413).json(Map.of("message", "单文件最大为 32 MiB。"));
    }

    private static String value(Context ctx, String key) {
        return ctx.queryParam(key) == null ? "" : ctx.queryParam(key);
    }

    private static int parameter(Context ctx, String key, int fallback) {
        try {
            return ctx.queryParam(key) == null ? fallback : Integer.parseInt(ctx.queryParam(key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " 应为 int32 整数。");
        }
    }
}
