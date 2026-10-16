package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ItemAddHintNotifyOuterClass.ItemAddHintNotify;
import emu.grasscutter.net.proto.ItemHintOuterClass.ItemHint;
import emu.grasscutter.net.proto.MusicGameBookAllDataNotifyOuterClass.MusicGameBookAllDataNotify;
import emu.grasscutter.net.proto.MusicGameBookRecordOuterClass.MusicGameBookRecord;
import emu.grasscutter.net.proto.MusicGameBookSaveModsReqOuterClass.MusicGameBookSaveModsReq;
import emu.grasscutter.net.proto.MusicGameBookSaveModsRspOuterClass.MusicGameBookSaveModsRsp;
import emu.grasscutter.net.proto.MusicGameBookSaveSettingsReqOuterClass.MusicGameBookSaveSettingsReq;
import emu.grasscutter.net.proto.MusicGameBookSaveSettingsRspOuterClass.MusicGameBookSaveSettingsRsp;
import emu.grasscutter.net.proto.MusicGameBookSettleReqOuterClass.MusicGameBookSettleReq;
import emu.grasscutter.net.proto.MusicGameBookSettleRspOuterClass.MusicGameBookSettleRsp;
import emu.grasscutter.net.proto.MusicGameBookStartReqOuterClass.MusicGameBookStartReq;
import emu.grasscutter.net.proto.MusicGameBookStartRspOuterClass.MusicGameBookStartRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;

import java.util.*;

/** Reusable UI entry, acknowledged theme grants and official performance records. */
public final class MusicGameBook {
    public static final int ITEM_ID = 220110;
    private final Player player;
    private final Set<Integer> pendingThemes = new TreeSet<>();
    private MusicGameBookCatalog.Beatmap activePerformance;
    private MusicGameBookCatalog activeCatalog;
    private byte[] completedRequest;
    private MusicGameBookSettleRsp completedResult;
    private PendingPerformance pendingPerformance;
    private boolean pendingSettings;
    private boolean pendingMods;

    private record PendingPerformance(
            byte[] request, MusicGameBookSettleRsp result, Map<Integer, Integer> rewards) {}

    public MusicGameBook(Player player) {
        this.player = Objects.requireNonNull(player);
    }

    private void confirmSettings() {
        if (!pendingSettings) return;
        // Keep the intended value while PlayerSaveGuard reconciles a possibly committed document.
        DatabaseHelper.saveGameSync(player);
        pendingSettings = false;
    }

    private void confirmMods() {
        if (!pendingMods) return;
        // Preserve the candidate until the guarded Player document is acknowledged.
        DatabaseHelper.saveGameSync(player);
        pendingMods = false;
    }

    public MusicGameBookSaveModsRsp saveMods(MusicGameBookSaveModsReq request) {
        synchronized (player.getInventory()) {
            var response = MusicGameBookSaveModsRsp.newBuilder();
            try {
                var catalog = MusicGameBookModCatalog.current();
                List<Integer> next;
                try {
                    next = catalog.select(request.getJNJDOLEBBOOList());
                } catch (IllegalArgumentException invalid) {
                    return response.setRetcode(Retcode.RET_FAIL_VALUE).build();
                }
                confirmSettings();
                confirmMods();
                confirmThemes();
                confirmPerformance();
                var previous = player.getMusicGameBookMods();
                if (!Objects.equals(previous == null ? List.of() : previous, next)) {
                    synchronized (player) {
                        player.setMusicGameBookMods(next);
                        pendingMods = true;
                        confirmMods();
                    }
                }
                return response.addAllJNJDOLEBBOO(next).build();
            } catch (RuntimeException unavailable) {
                Grasscutter.getLogger()
                        .warn(
                                "Music book mods unavailable for UID {}",
                                player.getUid(),
                                unavailable);
                return response.setRetcode(Retcode.RET_SVR_ERROR_VALUE).build();
            }
        }
    }

    public MusicGameBookSaveSettingsRsp saveSettings(MusicGameBookSaveSettingsReq request) {
        synchronized (player.getInventory()) {
            var response = MusicGameBookSaveSettingsRsp.newBuilder();
            if (!request.hasNKFEGNKDBBK())
                return response.setRetcode(Retcode.RET_FAIL_VALUE).build();
            try {
                confirmSettings();
                confirmMods();
                confirmThemes();
                confirmPerformance();
                var next = new MusicGameBookSettingsData(request.getNKFEGNKDBBK());
                var previous = player.getMusicGameBookSettings();
                if (previous == null || !previous.toProto().equals(next.toProto())) {
                    synchronized (player) {
                        player.setMusicGameBookSettings(next);
                        pendingSettings = true;
                        confirmSettings();
                    }
                }
                return response.setNKFEGNKDBBK(player.getMusicGameBookSettings().toProto()).build();
            } catch (RuntimeException unavailable) {
                Grasscutter.getLogger()
                        .warn(
                                "Music book settings unavailable for UID {}",
                                player.getUid(),
                                unavailable);
                return response.setRetcode(Retcode.RET_SVR_ERROR_VALUE).build();
            }
        }
    }

    private void confirmThemes() {
        if (pendingThemes.isEmpty()) return;
        // Keep the intended set after an uncertain write: PlayerSaveGuard may still commit its
        // frozen document. Repeating an acknowledgement is safe for this unique unlock.
        DatabaseHelper.saveGameSync(player);
        var confirmed = List.copyOf(pendingThemes);
        pendingThemes.clear();
        for (int id : confirmed) {
            try {
                player.sendPacket(new PacketMusicGameThemeUnlockNotify(id));
            } catch (RuntimeException disconnected) {
                // The acknowledged unlock is already granted. Reconnect receives the full set.
                Grasscutter.getLogger()
                        .warn(
                                "Music theme notification failed for UID {}",
                                player.getUid(),
                                disconnected);
            }
        }
    }

    public boolean unlockTheme(int id) {
        synchronized (player.getInventory()) {
            var catalog = MusicGameBookThemeCatalog.current();
            if (!catalog.contains(id)) return false;
            confirmSettings();
            confirmMods();
            var saved = player.getMusicGameBookThemes();
            if (id != catalog.defaultTheme() && (saved == null || !saved.contains(id))) {
                var next = saved == null ? new HashSet<Integer>() : new HashSet<>(saved);
                next.add(id);
                player.setMusicGameBookThemes(Collections.unmodifiableSet(next));
                pendingThemes.add(id);
            }
            confirmThemes();
            return true;
        }
    }

    private void sendData(MusicGameBookThemeCatalog catalog) {
        confirmSettings();
        confirmMods();
        confirmThemes();
        confirmPerformance();
        var themes = new TreeSet<Integer>();
        themes.add(catalog.defaultTheme());
        var saved = player.getMusicGameBookThemes();
        if (saved != null)
            for (var id : saved) {
                if (id != null && catalog.contains(id)) themes.add(id);
            }
        var notify = MusicGameBookAllDataNotify.newBuilder().addAllDHDNMAMDHIP(themes);
        var settings = player.getMusicGameBookSettings();
        if (settings != null) notify.setNKFEGNKDBBK(settings.toProto());
        var mods = player.getMusicGameBookMods();
        notify.addAllJNJDOLEBBOO(
                MusicGameBookModCatalog.current().select(mods == null ? List.of() : mods));
        var records = player.getMusicGameBookRecords();
        if (records != null && !records.isEmpty()) {
            var performances = MusicGameBookCatalog.current();
            for (var entry : new TreeMap<>(records).entrySet()) {
                var map = performances.beatmap(entry.getKey());
                var data = entry.getValue();
                if (map == null
                        || data == null
                        || data.getMaximumScore() < 0
                        || data.getMaximumScore() > map.maximumScore()) continue;
                notify.addPAEINCPKPHH(
                        MusicGameBookRecord.newBuilder()
                                .setIHPMDKPGJLB(entry.getKey())
                                .setMCPDKDGGBDD(data.getMaximumScore())
                                .setNFHCCJALOAJ(data.isNormalCompleted()));
            }
        }
        player.sendPacket(new PacketMusicGameBookAllDataNotify(notify.build()));
    }

    private boolean ownsBook(GameItem item) {
        return !(item == null
                || item.getItemId() != ITEM_ID
                || item.getCount() != 1
                || item.getOwnerId() != player.getUid()
                || item.getItemData() == null
                || item.getItemData().getId() != ITEM_ID
                || item.getItemType() != ItemType.ITEM_MATERIAL
                || item.getItemData().getMaterialType() != MaterialType.MATERIAL_WIDGET
                || player.getInventory().getItemById(ITEM_ID) != item
                || player.getInventory().getItemByGuid(item.getGuid()) != item);
    }

    public boolean open(GameItem item) {
        synchronized (player.getInventory()) {
            if (!ownsBook(item)) return false;
            sendData(MusicGameBookThemeCatalog.current());
            return true;
        }
    }

    private void confirmPerformance() {
        if (pendingPerformance == null) return;
        // A frozen Player document may already be committed after an uncertain acknowledgement.
        // Keep the monotone record and confirm it before admitting another run or publishing it.
        DatabaseHelper.saveGameSync(player);
        var confirmed = pendingPerformance;
        completedRequest = confirmed.request();
        completedResult = confirmed.result();
        activePerformance = null;
        activeCatalog = null;
        pendingPerformance = null;
        if (!confirmed.rewards().isEmpty()) {
            try {
                var hint = ItemAddHintNotify.newBuilder();
                for (var item : new TreeMap<>(confirmed.rewards()).entrySet()) {
                    player.sendPacket(
                            new PacketPlayerPropNotify(player, rewardProperty(item.getKey())));
                    hint.addItemList(
                            ItemHint.newBuilder()
                                    .setItemId(item.getKey())
                                    .setCount(item.getValue()));
                }
                var packet = new BasePacket(PacketOpcodes.ItemAddHintNotify);
                packet.setData(hint.build());
                player.sendPacket(packet);
            } catch (RuntimeException disconnected) {
                Grasscutter.getLogger()
                        .warn(
                                "Music book reward notification unavailable for UID {}",
                                player.getUid(),
                                disconnected);
            }
        }
    }

    private static PlayerProperty rewardProperty(int itemId) {
        return switch (itemId) {
            case 201 -> PlayerProperty.PROP_PLAYER_HCOIN;
            case 207 -> PlayerProperty.PROP_PLAYER_MUSIC_GAME_BOOK_COIN;
            default -> throw new IllegalArgumentException("Unsupported music book currency");
        };
    }

    public MusicGameBookStartRsp startPerformance(MusicGameBookStartReq request) {
        synchronized (player.getInventory()) {
            var result =
                    MusicGameBookStartRsp.newBuilder()
                            .setIHPMDKPGJLB(request.getIHPMDKPGJLB())
                            .setBCBDBEFONEF(request.getBCBDBEFONEF());
            if (!ownsBook(player.getInventory().getItemById(ITEM_ID)))
                return result.setRetcode(Retcode.RET_ITEM_NOT_EXIST_VALUE).build();
            try {
                var catalog = MusicGameBookCatalog.current();
                var map = catalog.beatmap(request.getIHPMDKPGJLB());
                if (request.getBCBDBEFONEF() != 0 || map == null)
                    return result.setRetcode(Retcode.RET_MUSIC_GAME_LEVEL_CONFIG_NOT_FOUND_VALUE)
                            .build();
                confirmSettings();
                confirmMods();
                confirmPerformance();
                activePerformance = map;
                activeCatalog = catalog;
                completedRequest = null;
                completedResult = null;
                return result.build();
            } catch (RuntimeException unavailable) {
                Grasscutter.getLogger()
                        .warn(
                                "Music book start unavailable for UID {}",
                                player.getUid(),
                                unavailable);
                return result.setRetcode(Retcode.RET_SVR_ERROR_VALUE).build();
            }
        }
    }

    public MusicGameBookSettleRsp settlePerformance(MusicGameBookSettleReq request) {
        synchronized (player.getInventory()) {
            var result =
                    MusicGameBookSettleRsp.newBuilder()
                            .setIHPMDKPGJLB(request.getIHPMDKPGJLB())
                            .setBCBDBEFONEF(request.getBCBDBEFONEF());
            try {
                confirmSettings();
                confirmMods();
                confirmPerformance();
                var wire = request.toByteArray();
                if (completedResult != null && Arrays.equals(completedRequest, wire))
                    return completedResult;
                if (activePerformance == null)
                    return result.setRetcode(Retcode.RET_MUSIC_GAME_LEVEL_NOT_STARTED_VALUE)
                            .build();
                if (request.getBCBDBEFONEF() != 0
                        || request.getIHPMDKPGJLB() != activePerformance.id()
                                && (request.getFNCOKHKCDDG() || request.getIHPMDKPGJLB() != 0))
                    return result.setRetcode(Retcode.RET_MUSIC_GAME_LEVEL_ID_NOT_MATCH_VALUE)
                            .build();
                result.setIHPMDKPGJLB(activePerformance.id());
                long score = Integer.toUnsignedLong(request.getCAEKFNKKEPG());
                if (request.getFNCOKHKCDDG()) {
                    if (score > activePerformance.maximumScore()
                            || request.getFJDDGBILJCF() != 0 && request.getFJDDGBILJCF() != 1
                            || request.getFJDDGBILJCF() == 1
                                    && (score == 0
                                            || request.getKBBAMIPLHEJCount() != 3
                                            || request.getKBBAMIPLHEJ(2) != 0
                                            || Integer.toUnsignedLong(request.getKBBAMIPLHEJ(0))
                                                            + Integer.toUnsignedLong(
                                                                    request.getKBBAMIPLHEJ(1))
                                                    == 0))
                        return result.setRetcode(Retcode.RET_FAIL_VALUE).build();
                    synchronized (player) {
                        var saved = player.getMusicGameBookRecords();
                        var old = saved == null ? null : saved.get(activePerformance.id());
                        int previous = old == null ? 0 : old.getMaximumScore();
                        boolean fullCombo =
                                request.getFJDDGBILJCF() == 1 || old != null && old.isFullCombo();
                        result.setPHKLGIKKAMA(score > previous);
                        // Native completion reason 1 is an ordinary single-player finish.
                        // The other list and booleans are telemetry/settings, not eligibility.
                        var targets =
                                request.getLKIMDBMGKEM() == 1
                                                && activeCatalog.passes(activePerformance, score)
                                        ? activeCatalog
                                                .standardClearTargets(activePerformance)
                                                .stream()
                                                .filter(
                                                        map ->
                                                                saved == null
                                                                        || saved.get(map.id())
                                                                                == null
                                                                        || !saved.get(map.id())
                                                                                .isNormalCompleted())
                                                .toList()
                                        : List.<MusicGameBookCatalog.Beatmap>of();
                        var rewards = activeCatalog.rewards(targets);
                        var balances = new EnumMap<PlayerProperty, Integer>(PlayerProperty.class);
                        for (var item : rewards.entrySet()) {
                            var property = rewardProperty(item.getKey());
                            int before = player.getProperties().getOrDefault(property.getId(), 0);
                            if (before < 0)
                                throw new IllegalStateException("Negative music reward balance");
                            balances.put(property, Math.addExact(before, item.getValue()));
                        }
                        if (score > previous
                                || fullCombo && (old == null || !old.isFullCombo())
                                || !targets.isEmpty()) {
                            var next =
                                    saved == null
                                            ? new HashMap<Integer, MusicGameBookRecordData>()
                                            : new HashMap<>(saved);
                            next.put(
                                    activePerformance.id(),
                                    new MusicGameBookRecordData(
                                            Math.max((int) score, previous),
                                            fullCombo,
                                            old != null && old.isNormalCompleted()));
                            for (var map : targets) {
                                var record = next.get(map.id());
                                next.put(
                                        map.id(),
                                        new MusicGameBookRecordData(
                                                record == null ? 0 : record.getMaximumScore(),
                                                record != null && record.isFullCombo(),
                                                true));
                            }
                            // Both currencies and every paid clear share the same guarded Player
                            // document. Do not publish success or grant notifications before ack.
                            balances.forEach(
                                    (property, value) ->
                                            player.getProperties().put(property.getId(), value));
                            player.setMusicGameBookRecords(Map.copyOf(next));
                            pendingPerformance =
                                    new PendingPerformance(wire, result.build(), rewards);
                            confirmPerformance();
                        }
                    }
                }
                completedRequest = wire;
                completedResult = result.build();
                activePerformance = null;
                activeCatalog = null;
                try {
                    sendData(MusicGameBookThemeCatalog.current());
                } catch (RuntimeException disconnected) {
                    Grasscutter.getLogger()
                            .warn(
                                    "Music book score snapshot unavailable for UID {}",
                                    player.getUid(),
                                    disconnected);
                }
                return completedResult;
            } catch (RuntimeException unavailable) {
                Grasscutter.getLogger()
                        .warn(
                                "Music book settlement unavailable for UID {}",
                                player.getUid(),
                                unavailable);
                return result.setRetcode(Retcode.RET_SVR_ERROR_VALUE).build();
            }
        }
    }

    public void onLogin() {
        synchronized (player.getInventory()) {
            try {
                sendData(MusicGameBookThemeCatalog.current());
            } catch (RuntimeException unavailable) {
                Grasscutter.getLogger()
                        .warn(
                                "Music book state unavailable for UID {}",
                                player.getUid(),
                                unavailable);
            }
        }
    }
}
