package emu.grasscutter.game.managers.forging;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.ItemHintOuterClass.ItemHint;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.*;

/** Exact experience transitions, using the existing adventure and companionship gain routines. */
final class ForgeExperienceDelivery {
    private ForgeExperienceDelivery() {}

    static int validate(Player player, List<ItemParamData> products) {
        try {
            for (var item : products) {
                var data = GameData.getItemDataMap().get(item.getId());
                if (item.getCount() <= 0
                        || data == null
                        || data.getId() != item.getId()
                        || data.getItemType() != ItemType.ITEM_VIRTUAL
                        || data.isUseOnGain()) return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
                if (item.getId() == 102) adventure(player, item.getCount());
                else if (item.getId() == 105) friendship(player, item.getCount());
                else return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
            }
            return 0;
        } catch (ArithmeticException overflow) {
            return Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
        } catch (IllegalArgumentException invalid) {
            return Retcode.RET_NOT_FOUND_CONFIG_VALUE;
        }
    }

    private record Progress(int level, int exp) {}

    private record Friend(Avatar avatar, Progress expected, int amount) {}

    private static Progress adventure(Player player, int amount) {
        int level = player.getLevel();
        int exp = Math.addExact(player.getExp(), amount);
        if (level <= 0 || player.getExp() < 0)
            throw new IllegalArgumentException("Invalid adventure EXP");
        while (true) {
            var row = GameData.getPlayerLevelDataMap().get(level);
            if (row == null || row.getId() != level || row.getExp() < 0)
                throw new IllegalArgumentException("Missing adventure level");
            if (row.getExp() == 0 || exp < row.getExp()) return new Progress(level, exp);
            exp -= row.getExp();
            level = Math.addExact(level, 1);
        }
    }

    private static List<Friend> friendship(Player player, int amount) {
        int gain = Math.multiplyExact(amount, player.isInMultiplayer() ? 2 : 1);
        var result = new ArrayList<Friend>();
        var unique = Collections.newSetFromMap(new IdentityHashMap<Avatar, Boolean>());
        for (var entity : player.getTeamManager().getActiveTeamSnapshot()) {
            if (entity == null || entity.getAvatar() == null)
                throw new IllegalArgumentException("Invalid forge friendship recipient");
            var avatar = entity.getAvatar();
            if (!unique.add(avatar)) continue;
            int level = avatar.getFetterLevel(), exp = avatar.getFetterExp(), remaining = gain;
            if (level <= 0 || level > 10 || exp < 0)
                throw new IllegalArgumentException("Invalid friendship EXP");
            while (remaining > 0 && level < 10) {
                var row = GameData.getAvatarFetterLevelDataMap().get(level);
                if (row == null || row.getId() != level || row.getExp() <= 0 || exp >= row.getExp())
                    throw new IllegalArgumentException("Missing friendship level");
                int step = Math.min(remaining, row.getExp() - exp);
                exp += step;
                remaining -= step;
                if (exp == row.getExp()) {
                    level++;
                    exp = 0;
                }
            }
            // The shared routine reads the next level after advancing, even when the gain ended.
            if (level < 10 && GameData.getAvatarFetterLevelExpRequired(level) <= 0)
                throw new IllegalArgumentException("Missing final friendship level");
            result.add(new Friend(avatar, new Progress(level, exp), gain));
        }
        if (!result.isEmpty() && player.getServer().getInventorySystem() == null)
            throw new IllegalArgumentException("Missing friendship inventory system");
        return result;
    }

    static List<ItemHint> grant(Player player, List<ItemParamData> products) {
        if (validate(player, products) != 0)
            throw new IllegalStateException("Forge EXP preflight changed");
        var hints = new ArrayList<ItemHint>();
        for (var item : products) {
            if (item.getId() == 102) {
                var expected = adventure(player, item.getCount());
                player.addExpDirectly(item.getCount());
                if (player.getLevel() != expected.level || player.getExp() != expected.exp)
                    throw new IllegalStateException("Forge adventure EXP changed");
                DatabaseHelper.saveGameSync(player);
            } else {
                var recipients = friendship(player, item.getCount());
                for (var recipient : recipients) {
                    player.getServer()
                            .getInventorySystem()
                            .upgradeAvatarFetterLevel(player, recipient.avatar, recipient.amount);
                    if (recipient.avatar.getFetterLevel() != recipient.expected.level
                            || recipient.avatar.getFetterExp() != recipient.expected.exp)
                        throw new IllegalStateException("Forge friendship EXP changed");
                    DatabaseHelper.saveGameSync(recipient.avatar);
                }
            }
            hints.add(
                    ItemHint.newBuilder()
                            .setItemId(item.getId())
                            .setCount(item.getCount())
                            .build());
        }
        return hints;
    }
}
