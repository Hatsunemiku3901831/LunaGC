package emu.grasscutter.game.tps;

import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaUpdateTpsWeaponAmmunitionOuterClass.AbilityMetaUpdateTpsWeaponAmmunition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Recent complete timed magazine reports belong to this player's actual entity lifetimes. */
public final class TpsAmmunitionInvocations {
    private static final int MAX_REPORTS = 256;
    private static final int MAX_REPORT_BYTES = 4096;

    private record EntityScope(GameEntity entity, int id, long lifetime) {
        EntityScope(GameEntity entity) {
            this(entity, entity.getId(), entity.getAbilityLifecycleVersion());
        }

        boolean current(Scene scene) {
            return scene != null
                    && entity.getId() == id
                    && entity.getScene() == scene
                    && entity.isAbilityLifecycleCurrent(lifetime)
                    && (!(entity instanceof EntityWeapon)
                            || scene.getWeaponEntities().get(id) == entity);
        }
    }

    private record Report(
            EntityScope source,
            EntityScope avatar,
            List<EntityScope> weapons,
            Scene scene,
            AbilityInvokeEntry invocation) {
        boolean current() {
            return source.current(scene)
                    && avatar.current(scene)
                    && weapons.stream().allMatch(weapon -> weapon.current(scene));
        }
    }

    private final Set<Report> recent = new LinkedHashSet<>();

    /**
     * Called after all state validation, before the first mutation or potentially ambiguous save.
     */
    public synchronized boolean begin(
            GameEntity source,
            EntityAvatar avatar,
            AbilityInvokeEntry invocation,
            AbilityMetaUpdateTpsWeaponAmmunition update) {
        var scene = avatar.getScene();
        if (scene == null) return false;
        var weapons = new ArrayList<EntityScope>();
        for (var snapshot : update.getAccessoryListList()) {
            if (!(scene.getWeaponEntities().get(snapshot.getEntityId())
                            instanceof EntityWeapon weapon)
                    || weapon.getCasterAvatar() != avatar.getAvatar()) return false;
            weapons.add(new EntityScope(weapon));
        }
        var report =
                new Report(
                        new EntityScope(source),
                        new EntityScope(avatar),
                        List.copyOf(weapons),
                        scene,
                        invocation);
        if (!report.current()) return false;
        recent.removeIf(previous -> !previous.current());
        double time = invocation.getTotalTickTime();
        // An untimed total-only fee has no proven operation identity. Keep native handling.
        if (weapons.isEmpty()
                || !Double.isFinite(time)
                || time <= 0
                || invocation.getSerializedSize() > MAX_REPORT_BYTES
                || invocation.getArgumentType()
                        != AbilityInvokeArgument.ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION)
            return true;
        if (recent.contains(report)) return false;
        if (recent.size() >= MAX_REPORTS) recent.remove(recent.iterator().next());
        recent.add(report);
        return true;
    }
}
