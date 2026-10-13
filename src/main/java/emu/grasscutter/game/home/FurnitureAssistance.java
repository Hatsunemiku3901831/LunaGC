package emu.grasscutter.game.home;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.FurnitureMakeSlotOuterClass.FurnitureMakeSlot;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketFurnitureMakeBeHelpedNotify;
import emu.grasscutter.utils.GameCalendar;

import java.time.Instant;
import java.util.*;

/**
 * Host-document assistance operation. The caller must authenticate the guest's home visit before
 * invoking this operation; request routing is kept separate from the durable business state.
 */
public final class FurnitureAssistance {
    // CONST_VALUE_HOME_ACCELERATE_PARAM[1] in the current resource export.
    public static final int MAX_ACCELERATION_SECONDS = 14400;

    record Pending(int guestUid) {}

    private FurnitureAssistance() {}

    /** Uses the authenticated visitor's actual world; no caller-supplied target UID. */
    public static int assist(Player guest, int now) {
        if (guest == null || !guest.isOnline() || !(guest.getWorld() instanceof HomeWorld world))
            return Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE;
        var home = world.getHome();
        if (home == null) return Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE;
        synchronized (home) {
            var scene = guest.getScene();
            if (guest.getWorld() != world
                    || world.getPlayers().stream().noneMatch(member -> member == guest)
                    || !(scene instanceof HomeScene)
                    || scene.getWorld() != world
                    || world.getScenes().get(scene.getId()) != scene
                    || scene.getPlayers().stream().noneMatch(member -> member == guest)
                    || home.getOwnerUid() != world.getOwnerUid()
                    || home.getOwnerUid() == guest.getUid()
                    || world.getHost().isOnline() && world.getHost().getHome() != home)
                return Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE;
            var record = FurnitureAssistanceRecord.capture(guest, now);
            if (!record.isValid()) return Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE;
            int result = assist(home, guest.getUid(), now, record);
            if (result != 0) return result;
            var profiles = home.getFurnitureAssistanceProfiles();
            var confirmed = profiles == null ? null : profiles.get(guest.getUid());
            // An older internal UID-only attempt has no frozen profile to publish.
            if (confirmed == null) return 0;
            try {
                world.sendPacketToHostIfOnline(
                        new PacketFurnitureMakeBeHelpedNotify(
                                confirmed.toProto(),
                                FurnitureMakeSlot.newBuilder()
                                        .addAllFurnitureMakeDataList(
                                                home.getFurnitureMakeSlotItemList().stream()
                                                        .map(FurnitureMakeSlotItem::toProto)
                                                        .toList())
                                        .build()));
            } catch (RuntimeException error) {
                // Persistence is confirmed; login/status will deliver the saved record again.
                Grasscutter.getLogger()
                        .warn(
                                "Could not deliver furniture assistance notification for home UID"
                                        + " {}",
                                home.getOwnerUid(),
                                error);
            }
            return 0;
        }
    }

    public static int assist(GameHome home, int guestUid, int now) {
        return assist(home, guestUid, now, null);
    }

    /** A status request can finish an uncertain home write without another assistance effect. */
    public static boolean confirmPending(GameHome home) {
        if (home == null) return false;
        synchronized (home) {
            if (home.getPendingFurnitureAssistanceWrite() == null) return true;
            if (!save(home)) return false;
            home.setPendingFurnitureAssistanceWrite(null);
            return true;
        }
    }

    private static int assist(
            GameHome home, int guestUid, int now, FurnitureAssistanceRecord record) {
        if (home == null
                || home.getOwnerUid() <= 0
                || guestUid <= 0
                || home.getOwnerUid() == guestUid)
            return Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE;
        if (now <= 0) return Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE;
        synchronized (home) {
            if (home.getPendingFurnitureStart() != null
                    || home.getPendingFurnitureTake() != null
                    || home.getPendingFurnitureCancel() != null
                    || home.getPendingFurnitureBulk() != null) return Retcode.RET_SVR_ERROR_VALUE;

            var pending = home.getPendingFurnitureAssistanceWrite();
            if (pending != null) {
                if (!save(home)) return Retcode.RET_SVR_ERROR_VALUE;
                home.setPendingFurnitureAssistanceWrite(null);
                if (pending.guestUid() == guestUid) return 0;
            }

            var days = home.getFurnitureAssistanceDays();
            if (days != null
                    && days.entrySet().stream()
                            .anyMatch(
                                    entry ->
                                            entry.getKey() == null
                                                    || entry.getKey() <= 0
                                                    || entry.getKey() == home.getOwnerUid()
                                                    || entry.getValue() == null
                                                    || entry.getValue() <= 0))
                return Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE;
            int today = GameCalendar.dateKey(GameCalendar.resetDay(Instant.ofEpochSecond(now)));
            // A backwards wall-clock adjustment must not reopen a previously consumed day.
            if (days != null && days.getOrDefault(guestUid, 0) >= today)
                return Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE;

            var profiles = home.getFurnitureAssistanceProfiles();
            if (profiles != null
                    && profiles.entrySet().stream()
                            .anyMatch(
                                    entry ->
                                            entry.getKey() == null
                                                    || entry.getValue() == null
                                                    || entry.getKey() != entry.getValue().getUid()
                                                    || !entry.getValue().isValid()))
                return Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE;

            var slots = home.getFurnitureMakeSlotItemList();
            if (slots == null || slots.isEmpty())
                return Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA_VALUE;
            Set<Integer> indexes = new HashSet<>();
            List<FurnitureMakeSlotItem> eligible = new ArrayList<>();
            boolean unfinished = false;
            for (var slot : slots) {
                if (slot == null
                        || slot.getIndex() < 0
                        || !indexes.add(slot.getIndex())
                        || slot.getMakeId() <= 0
                        || slot.getAvatarId() < 0
                        || slot.getBeginTime() <= 0
                        || slot.getDurTime() < 0
                        || slot.getAccelerateTime() < 0)
                    return Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE;
                if (slot.getCompletionTime() <= now) continue;
                unfinished = true;
                if (slot.getAccelerateTime() < MAX_ACCELERATION_SECONDS) eligible.add(slot);
            }
            if (eligible.isEmpty())
                return unfinished
                        ? Retcode.RET_FURNITURE_MAKE_ACCELERATE_LIMIT_VALUE
                        : Retcode.RET_FURNITURE_MAKE_IS_FINISH_VALUE;

            // All effects share one Mongo document and one acknowledged write. Retain the complete
            // candidate after an uncertain acknowledgement; never roll back quota independently of
            // the queue, or subtract time again while retrying the same candidate.
            if (days == null) days = new HashMap<>();
            else days = new HashMap<>(days);
            days.put(guestUid, today);
            eligible.forEach(slot -> slot.setAccelerateTime(MAX_ACCELERATION_SECONDS));
            home.setFurnitureAssistanceDays(days);
            if (record != null) {
                profiles = profiles == null ? new HashMap<>() : new HashMap<>(profiles);
                profiles.put(guestUid, record);
                home.setFurnitureAssistanceProfiles(profiles);
            }
            home.setPendingFurnitureAssistanceWrite(new Pending(guestUid));
            if (!save(home)) return Retcode.RET_SVR_ERROR_VALUE;
            home.setPendingFurnitureAssistanceWrite(null);
            return 0;
        }
    }

    private static boolean save(GameHome home) {
        try {
            DatabaseHelper.saveGameSync(home);
            return true;
        } catch (RuntimeException error) {
            Grasscutter.getLogger()
                    .error(
                            "Could not confirm furniture assistance for home UID {}",
                            home.getOwnerUid(),
                            error);
            return false;
        }
    }
}
