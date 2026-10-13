package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.*;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.GameEntity;

import java.io.IOException;

/** AstaPS capture: field 2 contains packed or unpacked selected entity IDs. */
@AbilityMixin(AbilityMixinData.Type.CheckSubTagScanEntityMixin)
public final class CheckSubTagScanEntityMixin extends AbilityMixinHandler {
    public static int firstScannedEntityId(ByteString payload) {
        if (payload == null || payload.size() > 4096) return 0;
        var input = payload.newCodedInput();
        int selected = 0, count = 0;
        try {
            for (int tag; (tag = input.readTag()) != 0; ) {
                if (tag == 16) {
                    int id = input.readUInt32();
                    if (++count > 128) return 0;
                    if (selected == 0) selected = id;
                } else if (tag == 18) {
                    int limit = input.pushLimit(input.readRawVarint32());
                    while (input.getBytesUntilLimit() > 0) {
                        int id = input.readUInt32();
                        if (++count > 128) return 0;
                        if (selected == 0) selected = id;
                    }
                    input.popLimit(limit);
                } else {
                    // Unsupported groups cannot terminate parsing early with a partial target.
                    if ((tag & 7) == 3 || (tag & 7) == 4 || !input.skipField(tag)) return 0;
                }
            }
            return input.isAtEnd() ? selected : 0;
        } catch (IOException | IllegalArgumentException failure) {
            return 0;
        }
    }

    @Override
    public boolean execute(
            Ability ability, AbilityMixinData data, ByteString payload, GameEntity target) {
        if (ability == null || ability.getManager() == null) return false;
        var player = ability.getPlayerOwner();
        var scene = player == null ? null : player.getScene();
        if (scene == null || target == null || target.getScene() != scene) return false;
        var selected = scene.getEntityById(firstScannedEntityId(payload));
        if (NatsaurusVehicleHelper.vehicleGadgetIdFor(selected) == 0) selected = null;
        ability.getManager().setScanTarget(selected);
        return ability.getManager()
                .executeCallbackActionsNow(
                        ability,
                        selected == null ? data.onSelectEnd : data.onSelectStart,
                        payload,
                        target,
                        target);
    }
}
