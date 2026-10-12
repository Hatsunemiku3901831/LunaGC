package emu.grasscutter.game.player;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.InvestigationData;
import emu.grasscutter.data.excels.InvestigationTargetData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.InvestigationOuterClass.Investigation;
import emu.grasscutter.net.proto.InvestigationTargetOuterClass.InvestigationTarget;
import emu.grasscutter.net.proto.PlayerInvestigationAllInfoNotifyOuterClass.PlayerInvestigationAllInfoNotify;
import emu.grasscutter.net.proto.PlayerInvestigationNotifyOuterClass.PlayerInvestigationNotify;
import emu.grasscutter.net.proto.PlayerInvestigationTargetNotifyOuterClass.PlayerInvestigationTargetNotify;

import java.util.*;

/** Resource-backed adventure/education handbook progress and notification transport. */
public final class InvestigationHandbook {
    private InvestigationHandbook() {}

    private static final Set<String> COUNTERS =
            Set.of(
                    "TRIGGER_DO_COOK",
                    "TRIGGER_DO_FORGE",
                    "TRIGGER_DO_COMPOUND",
                    "TRIGGER_DO_COMBINE",
                    "TRIGGER_AVATAR_EXPEDITION",
                    "TRIGGER_MONSTER_DIE",
                    "TRIGGER_OPEN_CHEST_WITH_GADGET_ID",
                    "TRIGGER_FINISH_DUNGEON",
                    "TRIGGER_WORLD_BOSS_REWARD",
                    "TRIGGER_OPEN_BLOSSOM_CHEST",
                    "TRIGGER_COST_MATERIAL",
                    "TRIGGER_BUY_SHOP_GOODS",
                    "TRIGGER_UPGRADE_TALENT",
                    "TRIGGER_FORGE_WEAPON");

    private static boolean supported(InvestigationData chapter) {
        return chapter != null
                && ("INVESTIGATION_ADVENTURE".equals(chapter.getInvestigationType())
                        || "INVESTIGATION_EDCATION".equals(chapter.getInvestigationType()));
    }

    private static boolean open(Player player, InvestigationData chapter) {
        if (!supported(chapter)
                || player.getLevel() < chapter.getUnlockLevel()
                || player.getOpenStates().getOrDefault(1100, 0) <= 0) return false;
        if ("INVESTIGATION_EDCATION".equals(chapter.getInvestigationType())
                && player.getOpenStates().getOrDefault(2801, 0) <= 0) return false;
        String gate = chapter.getUnlockOpenStateType();
        if (gate == null || "OPEN_STATE_NONE".equals(gate)) return true;
        int id =
                switch (gate) {
                    case "OPEN_ADVENTURE_MANUAL" -> 1100;
                    case "OPEN_ADVENTURE_MANUAL_CITY_MENGDE" -> 1101;
                    case "OPEN_ADVENTURE_MANUAL_CITY_LIYUE" -> 1102;
                    case "OPEN_ADVENTURE_MANUAL_EDUCATION" -> 2801;
                    default -> -1;
                };
        return id > 0 && player.getOpenStates().getOrDefault(id, 0) > 0;
    }

    private static List<InvestigationTargetData> targets(Player player) {
        return GameData.getInvestigationTargetDataMap().values().stream()
                .filter(t -> t != null && t.getId() > 0 && t.getProgress() > 0 && !t.isDisuse())
                .filter(
                        t ->
                                open(
                                        player,
                                        GameData.getInvestigationDataMap()
                                                .get(t.getInvestigationId())))
                .sorted(Comparator.comparingInt(InvestigationTargetData::getId))
                .toList();
    }

    private static String parameter(InvestigationTargetData target, int index) {
        var trigger = target.getTriggerConfig();
        var list = trigger == null ? null : trigger.getParamList();
        return list == null || list.size() <= index || list.get(index) == null
                ? ""
                : list.get(index).trim();
    }

    private static int number(InvestigationTargetData target, int index) {
        String value = parameter(target, index);
        if (value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    private static Set<Integer> identifiers(String text) {
        var values = new HashSet<Integer>();
        if (text.isEmpty()) return values;
        for (var token : text.split("[;,]", -1)) {
            try {
                int value = Integer.parseInt(token.trim());
                if (value <= 0) return null;
                values.add(value);
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        return values;
    }

    private static int snapshot(Player player, InvestigationTargetData target) {
        var config = target.getTriggerConfig();
        String type = config == null ? "" : Objects.toString(config.getTriggerType(), "");
        if ("TRIGGER_TAKE_DUNGEON_FIRST_PASS_REWARD".equals(type)) {
            var dungeons = identifiers(parameter(target, 0));
            if (dungeons == null || dungeons.isEmpty()) return 0;
            var history = player.getPlayerProgress().getInvestigation();
            return dungeons.stream().anyMatch(history::hasFirstDungeonReward) ? 1 : 0;
        }
        int first = number(target, 0), second = number(target, 1);
        if (first < 0 || second < 0) return 0;
        return switch (type) {
            case "TRIGGER_UNLOCK_AREA" -> first > 0
                            && player.getUnlockedSceneAreas().values().stream()
                                    .filter(Objects::nonNull)
                                    .anyMatch(areas -> areas.contains(first))
                    ? 1
                    : 0;
            case "TRIGGER_UNLOCK_TRANS_POINT" -> (int)
                    Math.min(
                            Integer.MAX_VALUE,
                            player.getUnlockedScenePoints().values().stream()
                                    .filter(Objects::nonNull)
                                    .mapToLong(Set::size)
                                    .sum());
            case "TRIGGER_CITY_LEVEL_UP" -> first > 0
                            && second > 0
                            && player.getSotsManager() != null
                            && player.getSotsManager().getCityInfo(first).getLevel() >= second
                    ? 1
                    : 0;
            case "TRIGGER_WEAPON_UPGRADE",
                    "TRIGGER_SPECIFIED_WEAPON_UPGRADE",
                    "TRIGGER_WEAPON_PROMOTE" -> {
                int needed = type.equals("TRIGGER_SPECIFIED_WEAPON_UPGRADE") ? second : first;
                if (needed <= 0) yield 0;
                boolean found = false;
                for (var item : player.getInventory()) {
                    if (item.getItemType() != ItemType.ITEM_WEAPON) continue;
                    if (type.equals("TRIGGER_SPECIFIED_WEAPON_UPGRADE")
                            && item.getItemId() != first) continue;
                    int actual =
                            type.equals("TRIGGER_WEAPON_PROMOTE")
                                    ? item.getPromoteLevel()
                                    : item.getLevel();
                    if (actual >= needed) {
                        found = true;
                        break;
                    }
                }
                yield found ? 1 : 0;
            }
            case "TRIGGER_RELIQUARY_UPGRADE", "TRIGGER_RELIQUARY_UPGRADE_EQUAL_RANK_LEVEL" -> {
                int neededCount = number(target, 2);
                if (first <= 0 || neededCount < 0) yield 0;
                if (neededCount == 0) neededCount = 1;
                int count = 0;
                for (var item : player.getInventory()) {
                    if (item.getItemType() != ItemType.ITEM_RELIQUARY || item.getItemData() == null)
                        continue;
                    int rank = item.getItemData().getRankLevel();
                    boolean matchRank =
                            second == 0
                                    || (type.equals("TRIGGER_RELIQUARY_UPGRADE_EQUAL_RANK_LEVEL")
                                            ? rank == second
                                            : rank >= second);
                    // Both the Excel milestone and GameItem level include the zero-level slot.
                    if (matchRank && item.getLevel() >= first) count++;
                }
                yield count >= neededCount ? 1 : 0;
            }
            case "TRIGGER_AVATAR_UPGRADE",
                    "TRIGGER_AVATAR_PROMOTE",
                    "TRIGGER_AVATAR_PROMOTE_EXCLUDING_PLAYER" -> {
                if (first <= 0 || player.getAvatars() == null) yield 0;
                boolean found = false;
                for (var avatar : player.getAvatars()) {
                    if (avatar.getTrialAvatarId() != 0) continue;
                    if (type.equals("TRIGGER_AVATAR_PROMOTE_EXCLUDING_PLAYER")
                            && (avatar.getAvatarId() == 10000005
                                    || avatar.getAvatarId() == 10000007)) continue;
                    int actual =
                            type.equals("TRIGGER_AVATAR_UPGRADE")
                                    ? avatar.getLevel()
                                    : avatar.getPromoteLevel();
                    if (actual < first) continue;
                    if (second > 0
                            && (avatar.getSkillDepot() == null
                                    || avatar.getSkillDepot().getElementType() == null
                                    || avatar.getSkillDepot().getElementType().getValue()
                                            != second)) continue;
                    found = true;
                    break;
                }
                yield found ? 1 : 0;
            }
            default -> 0;
        };
    }

    private static boolean matches(InvestigationTargetData target, int param) {
        var values = identifiers(parameter(target, 0));
        return values != null && (values.isEmpty() || param > 0 && values.contains(param));
    }

    public static void onPlayerLogin(Player player) {
        update(player, null, 0, 0, true);
    }

    public static void refreshAndNotify(Player player) {
        if (GameData.getInvestigationTargetDataMap().isEmpty()) return;
        update(player, null, 0, 0, true);
    }

    public static void trigger(Player player, String type, int param, int amount) {
        if (type == null || amount <= 0 || !COUNTERS.contains(type)) return;
        update(player, type, param, amount, false);
    }

    private static void update(Player player, String type, int param, int amount, boolean full) {
        if (player == null) return;
        if (!full && GameData.getInvestigationTargetDataMap().isEmpty()) return;
        synchronized (player.getInventory()) {
            var progress = player.getPlayerProgress().getInvestigation();
            synchronized (progress) {
                var active = targets(player);
                var changed = new ArrayList<InvestigationTargetData>();
                for (var target : active) {
                    boolean raised =
                            type == null
                                    && progress.raise(
                                            target.getId(),
                                            snapshot(player, target),
                                            target.getProgress());
                    if (type != null
                            && target.getTriggerConfig() != null
                            && type.equals(target.getTriggerConfig().getTriggerType())
                            && matches(target, param))
                        raised |= progress.add(target.getId(), amount, target.getProgress());
                    if (raised) changed.add(target);
                }
                if (!changed.isEmpty()) player.save();
                if (!full && changed.isEmpty()) return;
                var targetInfo = active.stream().map(t -> targetInfo(progress, t)).toList();
                var chapters =
                        GameData.getInvestigationDataMap().values().stream()
                                .filter(c -> open(player, c))
                                .sorted(Comparator.comparingInt(InvestigationData::getId))
                                .map(c -> chapterInfo(c.getId(), targetInfo))
                                .toList();
                if (full)
                    send(
                            player,
                            PacketOpcodes.PlayerInvestigationAllInfoNotify,
                            PlayerInvestigationAllInfoNotify.newBuilder()
                                    .addAllInvestigationList(chapters)
                                    .addAllInvestigationTargetList(targetInfo)
                                    .build());
                send(
                        player,
                        PacketOpcodes.PlayerInvestigationTargetNotify,
                        PlayerInvestigationTargetNotify.newBuilder()
                                .addAllInvestigationTargetList(
                                        full
                                                ? targetInfo
                                                : changed.stream()
                                                        .map(t -> targetInfo(progress, t))
                                                        .toList())
                                .build());
                send(
                        player,
                        PacketOpcodes.PlayerInvestigationNotify,
                        PlayerInvestigationNotify.newBuilder()
                                .addAllInvestigationList(chapters)
                                .build());
            }
        }
    }

    private static InvestigationTarget targetInfo(
            InvestigationProgress state, InvestigationTargetData target) {
        int current = Math.min(target.getProgress(), state.get(target.getId()));
        return InvestigationTarget.newBuilder()
                .setInvestigationId(target.getInvestigationId())
                .setQuestId(target.getId())
                .setTotalProgress(target.getProgress())
                .setProgress(current)
                .setStateValue(current >= target.getProgress() ? 2 : 1)
                .build();
    }

    private static Investigation chapterInfo(int id, List<InvestigationTarget> targets) {
        var children = targets.stream().filter(t -> t.getInvestigationId() == id).toList();
        int complete = (int) children.stream().filter(t -> t.getStateValue() >= 2).count();
        return Investigation.newBuilder()
                .setId(id)
                .setProgress(complete)
                .setTotalProgress(children.size())
                .setStateValue(!children.isEmpty() && complete == children.size() ? 2 : 1)
                .build();
    }

    private static void send(Player player, int opcode, com.google.protobuf.Message message) {
        var packet = new BasePacket(opcode);
        packet.setData(message.toByteArray());
        player.sendPacket(packet);
    }
}
