package emu.grasscutter.game.tps;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.GrantReasonOuterClass.GrantReason;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.util.*;

/** Resource-backed borrowed travelers and loadouts for the scene allowlist. */
public final class TpsAvatarSystem {
    public static final int MALE = 10000134;
    public static final int FEMALE = 10000135;
    public static final int INITIAL_WEAPON = 224001;

    private TpsAvatarSystem() {}

    public static boolean isTpsAvatar(int id) {
        return id == MALE || id == FEMALE;
    }

    public static int getAvatarId(Player player) {
        return player.getMainCharacterId() == 10000007 ? FEMALE : MALE;
    }

    public static boolean isTpsScene(SceneData data) {
        return data != null
                && data.getSpecifiedAvatarList() != null
                && data.getSpecifiedAvatarList().stream().anyMatch(id -> id != null && isTpsAvatar(id));
    }

    /** Use the effective trial parameters; a custom override can change the avatar ID. */
    public static int getTrialAvatarId(Player player, SceneData scene) {
        int avatarId = getAvatarId(player);
        if (!isTpsScene(scene) || !scene.getSpecifiedAvatarList().contains(avatarId)) return 0;
        return GameData.getTrialAvatarDataMap().keySet().stream()
                .mapToInt(id -> id)
                .sorted()
                .filter(
                        id -> {
                            var params = player.getTeamManager().getTrialAvatarParam(id);
                            return params.size() >= 2
                                    && params.get(0) == avatarId
                                    && validTrialEquipment(id, avatarId);
                        })
                .findFirst()
                .orElse(0);
    }

    private static boolean validTrialEquipment(int trialId, int avatarId) {
        var data = GameData.getAvatarDataMap().get(avatarId);
        if (data == null) return false;
        int weaponId = data.getInitialWeapon();
        var custom = GameData.getTrialAvatarCustomData().get(trialId);
        if (custom == null) {
            if (GameData.getItemDataMap().containsKey(weaponId + 100)) weaponId += 100;
        } else {
            var params = custom.getTrialAvatarParamList();
            try {
                if (params.size() >= 2) weaponId = Integer.parseInt(params.get(1).split(";")[0]);
                if (params.size() >= 3)
                    for (String relic : params.get(2).split(";")) Integer.parseInt(relic);
            } catch (NumberFormatException
                    | NullPointerException
                    | ArrayIndexOutOfBoundsException ignored) {
                return false;
            }
        }
        var weapon = GameData.getItemDataMap().get(weaponId);
        return weapon != null && weapon.getItemType() == ItemType.ITEM_WEAPON;
    }

    /** Reject an unavailable forced party before the old scene is removed. */
    public static boolean canEnter(Player player, SceneData scene) {
        if (!isTpsScene(scene)) return true;
        var data = GameData.getAvatarDataMap().get(getAvatarId(player));
        var item = GameData.getItemDataMap().get(INITIAL_WEAPON);
        return data != null
                && data.getSkillDepot() != null
                && GameData.getItemDataMap().containsKey(data.getInitialWeapon())
                && getTrialAvatarId(player, scene) > 0
                && item != null
                && item.getItemType() == ItemType.ITEM_TPS_WEAPON
                && GameData.getTpsWeaponDataMap().containsKey(INITIAL_WEAPON);
    }

    public static void prepareSceneTeam(Player player, Scene scene) {
        if (!isTpsScene(scene.getSceneData())) {
            player.getTeamManager().restoreTpsSelection();
            return;
        }
        synchronized (player) {
            if (player.getTeamManager().getTpsSceneAvatar() != null) return;
            if (!canEnter(player, scene.getSceneData()))
                throw new IllegalStateException("TPS scene resources unavailable: " + scene.getId());
            int trialId = getTrialAvatarId(player, scene.getSceneData());
            var params = player.getTeamManager().getTrialAvatarParam(trialId);
            var avatar = new Avatar(params.get(0));
            avatar.setTrialAvatarInfo(
                    params.get(1), trialId, GrantReason.GRANT_REASON_BY_TRIAL_AVATAR_ACTIVITY, 0);
            avatar.setOwner(player);
            avatar.equipTrialItems(false);
            avatar.recalcStats(false, false);

            var loadout = normalizeLoadout(player);
            if (!player.isTpsLoadoutInitialized() && loadout.isEmpty()) {
                var weapon = TpsWeaponSystem.findOwnedWeapon(player, INITIAL_WEAPON);
                if (weapon == null
                        && player
                                .getInventory()
                                .addItem(new GameItem(INITIAL_WEAPON), false, player.hasSentLoginPackets()))
                    weapon = TpsWeaponSystem.findOwnedWeapon(player, INITIAL_WEAPON);
                if (weapon != null) loadout.add(weapon.getItemId());
                else {
                    Grasscutter.getLogger()
                            .warn("Unable to grant initial TPS weapon to player {}", player.getUid());
                    // Keep the first-entry flag unset so a full backpack can be retried later.
                    player.getTeamManager().beginTpsSceneTeam(scene, avatar);
                    player.save();
                    return;
                }
            }
            player.getTeamManager().beginTpsSceneTeam(scene, avatar);
            var guids =
                    loadout.stream()
                            .map(id -> TpsWeaponSystem.findOwnedWeapon(player, id).getGuid())
                            .toList();
            if (TpsWeaponSystem.wear(player, avatar.getGuid(), guids, false) != Retcode.RET_SUCC_VALUE)
                throw new IllegalStateException("Unable to restore validated TPS loadout");
            player.save();
        }
    }

    private static ArrayList<Integer> normalizeLoadout(Player player) {
        var result = new ArrayList<Integer>();
        var slots = new HashMap<Integer, Integer>();
        for (Integer id : player.getTpsLoadout()) {
            if (id == null || result.contains(id) || TpsWeaponSystem.findOwnedWeapon(player, id) == null)
                continue;
            var data = GameData.getTpsWeaponDataMap().get((int) id);
            if (data == null) continue;
            int slot = data.getWearSlotType();
            int limit = slot == 1 ? 2 : slot == 2 ? 1 : 0;
            if (slots.getOrDefault(slot, 0) >= limit) continue;
            slots.merge(slot, 1, Integer::sum);
            result.add(id);
        }
        return result;
    }
}
