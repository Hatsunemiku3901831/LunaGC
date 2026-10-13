package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketAvatarEscoffierSkillCookDataNotify;
import emu.grasscutter.utils.GameCalendar;

import java.time.Instant;

/** Weekly quota synchronization. Cooking triggers and reward delivery use a separate path. */
public final class EscoffierCooking {
    private EscoffierCooking() {}

    public static boolean sync(Player player) {
        return sync(player, Instant.now());
    }

    public static boolean sync(Player player, Instant now) {
        if (player == null || now == null) return false;
        synchronized (player) {
            var config = GameData.getEscoffierCookConfig();
            if (config == null) return false;
            var avatar = player.getAvatars().getAvatarById(config.avatarId());
            // Avatar's normal type is 1; its package-private enum marks trials as 2.
            if (avatar == null
                    || avatar.getTrialAvatarId() != 0
                    || avatar.getAvatarType() != 1) return false;

            long currentWeek = week(now);
            if (currentWeek <= 0 || currentWeek > 0xffffffffL) return false;
            var previous = player.getEscoffierCookState();
            if (previous != null) {
                long savedWeek = Integer.toUnsignedLong(previous.getLastRefreshTime());
                if (previous.getFormat() != 1
                        || previous.getFinishedWeeklyCookNum() < 0
                        || savedWeek > currentWeek
                        || savedWeek == 0 && previous.getFinishedWeeklyCookNum() != 0
                        || savedWeek != 0 && week(Instant.ofEpochSecond(savedWeek)) != savedWeek) {
                    Grasscutter.getLogger()
                            .warn("Invalid Escoffier cooking quota for UID {}", player.getUid());
                    return false;
                }
            }

            if (previous == null
                    || Integer.toUnsignedLong(previous.getLastRefreshTime()) < currentWeek) {
                var replacement = new EscoffierCookState((int) currentWeek, 0);
                player.setEscoffierCookState(replacement);
                try {
                    DatabaseHelper.saveGameSync(player);
                } catch (RuntimeException error) {
                    player.setEscoffierCookState(previous);
                    Grasscutter.getLogger()
                            .error(
                                    "Could not persist Escoffier cooking quota for UID {}",
                                    player.getUid(),
                                    error);
                    return false;
                }
            }
            var state = player.getEscoffierCookState();
            player.sendPacket(
                    new PacketAvatarEscoffierSkillCookDataNotify(
                            state.getLastRefreshTime(), state.getFinishedWeeklyCookNum()));
            return true;
        }
    }

    private static long week(Instant now) {
        return GameCalendar.weekStart(GameCalendar.resetDay(now))
                .atTime(GameCalendar.RESET_HOUR, 0)
                .atZone(GameCalendar.SERVER_ZONE)
                .toEpochSecond();
    }
}
