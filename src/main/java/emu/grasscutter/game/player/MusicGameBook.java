package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.proto.MusicGameBookAllDataNotifyOuterClass.MusicGameBookAllDataNotify;
import emu.grasscutter.server.packet.send.*;

import java.util.*;

/**
 * Reusable UI entry and acknowledged theme grants. Performance and reward handling are separate.
 */
public final class MusicGameBook {
    public static final int ITEM_ID = 220110;
    private final Player player;
    private final Set<Integer> pendingThemes = new TreeSet<>();

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
        var themes = new TreeSet<Integer>();
        themes.add(catalog.defaultTheme());
        var saved = player.getMusicGameBookThemes();
        if (saved != null)
            for (var id : saved) {
                if (id != null && catalog.contains(id)) themes.add(id);
            }
        player.sendPacket(
                new PacketMusicGameBookAllDataNotify(
                        MusicGameBookAllDataNotify.newBuilder().addAllDHDNMAMDHIP(themes).build()));
    }

    public boolean open(GameItem item) {
        synchronized (player.getInventory()) {
            if (item == null
                    || item.getItemId() != ITEM_ID
                    || item.getCount() != 1
                    || item.getOwnerId() != player.getUid()
                    || item.getItemData() == null
                    || item.getItemData().getId() != ITEM_ID
                    || item.getItemType() != ItemType.ITEM_MATERIAL
                    || item.getItemData().getMaterialType() != MaterialType.MATERIAL_WIDGET
                    || player.getInventory().getItemById(ITEM_ID) != item
                    || player.getInventory().getItemByGuid(item.getGuid()) != item) return false;
            sendData(MusicGameBookThemeCatalog.current());
            return true;
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
