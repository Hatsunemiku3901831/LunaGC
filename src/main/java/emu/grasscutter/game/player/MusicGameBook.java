package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.proto.MusicGameBookAllDataNotifyOuterClass.MusicGameBookAllDataNotify;
import emu.grasscutter.net.proto.MusicGameBookRecordOuterClass.MusicGameBookRecord;
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
    private byte[] completedRequest;
    private MusicGameBookSettleRsp completedResult;
    private PendingPerformance pendingPerformance;

    private record PendingPerformance(byte[] request, MusicGameBookSettleRsp result) {}

    public MusicGameBook(Player player) {
        this.player = Objects.requireNonNull(player);
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
                                .setNFHCCJALOAJ(data.isFullCombo()));
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
        completedRequest = pendingPerformance.request();
        completedResult = pendingPerformance.result();
        activePerformance = null;
        pendingPerformance = null;
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
                var map = MusicGameBookCatalog.current().beatmap(request.getIHPMDKPGJLB());
                if (request.getBCBDBEFONEF() != 0 || map == null)
                    return result.setRetcode(Retcode.RET_MUSIC_GAME_LEVEL_CONFIG_NOT_FOUND_VALUE)
                            .build();
                confirmPerformance();
                activePerformance = map;
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
                            || request.getDGBEKHBEHFN()
                                    && (score == 0
                                            || request.getKBBAMIPLHEJCount() != 3
                                            || request.getKBBAMIPLHEJ(2) != 0
                                            || Integer.toUnsignedLong(request.getKBBAMIPLHEJ(0))
                                                            + Integer.toUnsignedLong(
                                                                    request.getKBBAMIPLHEJ(1))
                                                    == 0))
                        return result.setRetcode(Retcode.RET_FAIL_VALUE).build();
                    var saved = player.getMusicGameBookRecords();
                    var old = saved == null ? null : saved.get(activePerformance.id());
                    int previous = old == null ? 0 : old.getMaximumScore();
                    boolean fullCombo =
                            request.getDGBEKHBEHFN() || old != null && old.isFullCombo();
                    result.setPHKLGIKKAMA(score > previous);
                    if (score > previous || fullCombo && (old == null || !old.isFullCombo())) {
                        var next =
                                saved == null
                                        ? new HashMap<Integer, MusicGameBookRecordData>()
                                        : new HashMap<>(saved);
                        next.put(
                                activePerformance.id(),
                                new MusicGameBookRecordData(
                                        Math.max((int) score, previous), fullCombo));
                        player.setMusicGameBookRecords(Map.copyOf(next));
                        pendingPerformance = new PendingPerformance(wire, result.build());
                        confirmPerformance();
                    }
                }
                completedRequest = wire;
                completedResult = result.build();
                activePerformance = null;
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
