package emu.grasscutter.server.http.handlers;

import emu.grasscutter.Grasscutter;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

final class GmCombat {
    static void applyRoutes(Javalin javalin) {
        javalin.get("/gm/api/combat", ctx -> request(ctx, false));
        javalin.post("/gm/api/combat/reset", ctx -> request(ctx, true));
    }

    private static void request(Context ctx, boolean reset) throws Exception {
        if (!GmHandler.authorize(ctx)) return;
        ctx.header("Cache-Control", "no-store");
        int uid;
        try {
            uid = Integer.parseInt(ctx.queryParam("uid"));
            if (uid <= 0) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("retcode", 400, "message", "请指定目标 UID。"));
            return;
        }
        var server = Grasscutter.getGameServer();
        var player =
                server == null || server.getPlayers() == null ? null : server.getPlayerByUid(uid);
        if (player == null || player.getWorld() == null) {
            ctx.status(404).json(Map.of("retcode", 404, "message", "目标玩家不在线。"));
            return;
        }
        var world = player.getWorld();
        if (reset) world.getDamageLog().reset();
        var data = world.getDamageLog().snapshot();
        data.addProperty("retcode", 0);
        data.addProperty("uid", uid);
        ctx.contentType("application/json; charset=utf-8").result(data.toString());
    }
}
