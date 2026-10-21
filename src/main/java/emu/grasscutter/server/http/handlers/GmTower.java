package emu.grasscutter.server.http.handlers;

import com.google.gson.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tower.*;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.*;

/** Authenticated operator controls, sharing the normal tower/scene/checkpoint implementation. */
final class GmTower {
    private GmTower() {}

    static void applyRoutes(Javalin app) {
        app.get(
                "/gm/api/abyss",
                ctx ->
                        request(
                                ctx,
                                () -> {
                                    var system = system();
                                    var result = system.getCatalog().summary();
                                    result.add(
                                            "settings",
                                            new Gson().toJsonTree(system.getPresetStore().get()));
                                    var selected = system.getCurrentTowerScheduleData();
                                    result.addProperty(
                                            "activeScheduleId",
                                            selected == null ? 0 : selected.getScheduleId());
                                    result.addProperty(
                                            "protocolStatus",
                                            "7.1 原生全量请求、选队响应、切队通知及部分字段仍未恢复；GM"
                                                + " 入场复用服务端流程，不能证明原生界面完整可用。");
                                    result.add(
                                            "structurallyAvailable",
                                            new Gson().toJsonTree(system.getPlayableSchedules()));
                                    ctx.json(result);
                                }));
        app.get(
                "/gm/api/abyss/party",
                ctx ->
                        request(
                                ctx,
                                () -> {
                                    var player = player(Integer.parseInt(ctx.queryParam("uid")));
                                    var result = new JsonObject();
                                    var avatars = new JsonArray();
                                    for (var avatar : player.getAvatars().getAvatars().values()) {
                                        var a = new JsonObject();
                                        a.addProperty("avatarId", avatar.getAvatarId());
                                        a.addProperty("level", avatar.getLevel());
                                        a.addProperty(
                                                "name",
                                                name(
                                                        avatar.getAvatarData().getNameTextMapHash(),
                                                        "角色 " + avatar.getAvatarId()));
                                        avatars.add(a);
                                    }
                                    result.add("avatars", avatars);
                                    result.add(
                                            "selectedTeams",
                                            new Gson()
                                                    .toJsonTree(
                                                            player.getTowerData()
                                                                    .getAbyssAvatarIds()));
                                    ctx.json(result);
                                }));
        app.get(
                "/gm/api/abyss/period/{id}",
                ctx ->
                        request(
                                ctx,
                                () -> {
                                    var detail =
                                            system().getCatalog()
                                                    .details(Integer.parseInt(ctx.pathParam("id")));
                                    for (var f : detail.getAsJsonArray("floors")) {
                                        for (var c :
                                                f.getAsJsonObject().getAsJsonArray("chambers")) {
                                            var chamber = c.getAsJsonObject();
                                            int level = chamber.get("monsterLevel").getAsInt();
                                            var curve =
                                                    GameData.getMonsterCurveDataMap().get(level);
                                            for (var g :
                                                    chamber.getAsJsonObject("scene")
                                                            .getAsJsonArray("groups")) {
                                                for (var m :
                                                        g.getAsJsonObject()
                                                                .getAsJsonArray("monsters")) {
                                                    var monster = m.getAsJsonObject();
                                                    var data =
                                                            GameData.getMonsterDataMap()
                                                                    .get(
                                                                            monster.get("monsterId")
                                                                                    .getAsInt());
                                                    if (data == null) continue;
                                                    monster.addProperty(
                                                            "name",
                                                            name(
                                                                    data.getNameTextMapHash(),
                                                                    data.getMonsterName()));
                                                    double hp = data.getBaseHp();
                                                    if (curve != null)
                                                        for (var grow : data.getPropGrowCurves()) {
                                                            if (grow.getType()
                                                                    .equals("FIGHT_PROP_BASE_HP"))
                                                                hp *=
                                                                        curve.getMultByProp(
                                                                                grow
                                                                                        .getGrowCurve());
                                                        }
                                                    monster.addProperty(
                                                            "resourceBaseHp",
                                                            hp
                                                                    * f.getAsJsonObject()
                                                                            .get("baseHpMultiplier")
                                                                            .getAsDouble());
                                                }
                                            }
                                        }
                                    }
                                    ctx.json(detail);
                                }));
        app.post(
                "/gm/api/abyss/settings",
                ctx ->
                        request(
                                ctx,
                                () -> {
                                    var body = body(ctx);
                                    if (!body.has("revision") || !body.has("profile"))
                                        throw new IllegalArgumentException(
                                                "需要 revision 和 profile。");
                                    var profile = TowerBattleProfile.fromJson(body.get("profile"));
                                    if (profile == null)
                                        throw new IllegalArgumentException("配置不能为空。");
                                    var system = system();
                                    var saved =
                                            system.applyProfile(
                                                    body.get("revision")
                                                            .getAsBigDecimal()
                                                            .longValueExact(),
                                                    profile);
                                    ctx.json(
                                            Map.of(
                                                    "retcode",
                                                    0,
                                                    "settings",
                                                    saved,
                                                    "message",
                                                    "已保存；新选队使用此配置，已开始的本层与重试保留原配置。"));
                                }));
        app.post(
                "/gm/api/abyss/play",
                ctx ->
                        request(
                                ctx,
                                () -> {
                                    var body = body(ctx);
                                    var player = player(body.get("uid").getAsInt());
                                    int number = body.get("floor").getAsInt();
                                    if (number < 8 || number > 12)
                                        throw new IllegalArgumentException("请选择第 8–12 层。");
                                    var system = system();
                                    var selected = system.getCurrentTowerScheduleData();
                                    if (selected == null)
                                        throw new IllegalArgumentException("没有完整的深渊排期。");
                                    var profile = system.getBattleProfile();
                                    var effective =
                                            new TowerBattleProfile(
                                                    selected.getScheduleId(),
                                                    profile.hp(),
                                                    profile.attack(),
                                                    profile.defense(),
                                                    profile.levelOffset(),
                                                    profile.monsters());
                                    system.getCatalog().validate(effective);
                                    int floorId =
                                            system.getAllFloors().stream()
                                                    .filter(
                                                            id ->
                                                                    GameData.getTowerFloorDataMap()
                                                                                    .get(id)
                                                                                    .getFloorIndex()
                                                                            == number)
                                                    .findFirst()
                                                    .orElseThrow(
                                                            () ->
                                                                    new IllegalArgumentException(
                                                                            "本期没有该楼层。"));
                                    var parties = new ArrayList<List<Long>>();
                                    for (var team : body.getAsJsonArray("teams")) {
                                        var guids = new ArrayList<Long>();
                                        for (var member : team.getAsJsonArray()) {
                                            var avatar =
                                                    player.getAvatars()
                                                            .getAvatarById(member.getAsInt());
                                            if (avatar == null)
                                                throw new IllegalArgumentException("角色不属于目标玩家。");
                                            guids.add(avatar.getGuid());
                                        }
                                        parties.add(guids);
                                    }
                                    synchronized (player.getSceneTransferLock()) {
                                        if (player.getScene().getDungeonManager() != null)
                                            throw new IllegalStateException("请先退出当前副本再开始新的楼层。");
                                        if (!player.getTowerManager()
                                                .tryTeamSelect(floorId, parties))
                                            throw new IllegalArgumentException(
                                                    "双队校验失败：每队 1–4 名角色，队内及两队之间不得重复。");
                                        if (!player.getTowerManager().tryEnterLevel(0))
                                            throw new IllegalStateException("入场被场景保护拒绝，请检查日志。");
                                    }
                                    ctx.json(Map.of("retcode", 0, "message", "已进入，使用游戏内工作台开始上半场。"));
                                }));
        app.post(
                "/gm/api/abyss/action",
                ctx ->
                        request(
                                ctx,
                                () -> {
                                    var body = body(ctx);
                                    var player = player(body.get("uid").getAsInt());
                                    synchronized (player.getSceneTransferLock()) {
                                        var manager = player.getScene().getDungeonManager();
                                        if (manager == null || !manager.isTowerDungeon())
                                            throw new IllegalArgumentException("玩家当前不在深渊。");
                                        switch (body.get("action").getAsString()) {
                                            case "retry" -> {
                                                if (!player.getServer()
                                                        .getDungeonSystem()
                                                        .tryRestartDungeon(player))
                                                    throw new IllegalStateException("当前检查点不能重试。");
                                            }
                                            case "next" -> {
                                                if (!manager.isFinishedSuccessfully()
                                                        || !player.getTowerManager().hasNextLevel()
                                                        || !player.getTowerManager()
                                                                .tryEnterLevel(0))
                                                    throw new IllegalStateException(
                                                            "须完成当前间后再进入下一间；本层结束后请退出并重新选层。");
                                            }
                                            case "exit" -> player.getServer()
                                                    .getDungeonSystem()
                                                    .exitDungeon(player);
                                            default -> throw new IllegalArgumentException(
                                                    "未知深渊操作。");
                                        }
                                    }
                                    ctx.json(Map.of("retcode", 0, "message", "操作已执行。"));
                                }));
    }

    private static TowerSystem system() {
        var server = Grasscutter.getGameServer();
        if (server == null) throw new IllegalStateException("游戏服务尚未就绪。");
        return server.getTowerSystem();
    }

    private static String name(long hash, String fallback) {
        var text = emu.grasscutter.utils.lang.Language.getTextMapStrings().get((int) hash);
        var chinese = text == null ? null : text.get("CHS");
        return chinese == null || chinese.isBlank() ? fallback : chinese;
    }

    private static Player player(int uid) {
        var server = Grasscutter.getGameServer();
        var player = server == null ? null : server.getPlayerByUid(uid);
        if (player == null || player.getScene() == null || player.getWorld() == null)
            throw new IllegalArgumentException("目标玩家不在线。");
        return player;
    }

    private static JsonObject body(Context ctx) {
        if (ctx.body().length() > 200_000) throw new IllegalArgumentException("配置过大。");
        return JsonParser.parseString(ctx.body()).getAsJsonObject();
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void request(Context ctx, Action action) throws Exception {
        if (!GmHandler.authorize(ctx)) return;
        ctx.header("Cache-Control", "no-store");
        try {
            action.run();
        } catch (IllegalArgumentException
                | JsonParseException
                | NullPointerException
                | ArithmeticException e) {
            ctx.status(400)
                    .json(
                            Map.of(
                                    "retcode",
                                    400,
                                    "message",
                                    e.getMessage() == null ? "深渊参数不完整。" : e.getMessage()));
        } catch (IllegalStateException e) {
            ctx.status(409).json(Map.of("retcode", 409, "message", e.getMessage()));
        } catch (Exception e) {
            Grasscutter.getLogger().error("GM Abyss operation failed", e);
            ctx.status(500).json(Map.of("retcode", 500, "message", "深渊操作失败，请检查服务日志。"));
        }
    }
}
