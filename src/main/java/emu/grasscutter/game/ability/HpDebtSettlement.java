package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;

/** Bond changes use the same entity monitor as ordinary healing and publish the settled delta. */
public final class HpDebtSettlement {
    private HpDebtSettlement() {}

    public static float add(GameEntity target, float amount) {
        return change(target, amount, false);
    }

    public static float repay(GameEntity target, float amount) {
        return change(target, amount, true);
    }

    private static float change(GameEntity target, float amount, boolean payment) {
        if (target == null || !Float.isFinite(amount) || amount <= 0f) return 0f;
        synchronized (target) {
            if (target.getFightProperties() == null) return 0f;
            if (target instanceof EntityAvatar avatar) {
                float hp = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
                if (!avatar.isAlive() || !Float.isFinite(hp) || hp <= 0f) return 0f;
            }
            float current = target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            float maximumHp = target.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            if (!Float.isFinite(current)
                    || current < 0f
                    || !Float.isFinite(maximumHp)
                    || maximumHp <= 0f
                    || maximumHp > Float.MAX_VALUE / 2f) return 0f;
            float next =
                    payment
                            ? Math.max(0f, current - amount)
                            : (float) Math.min(2d * maximumHp, (double) current + amount);
            float delta = next - current;
            if (delta == 0f) return 0f;
            target.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, next);
            var scene = target.getScene();
            if (scene != null) {
                scene.broadcastPacket(
                        new PacketEntityFightPropUpdateNotify(target, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
                scene.broadcastPacket(
                        new PacketEntityFightPropChangeReasonNotify(
                                target,
                                FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                                delta,
                                PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                                next == 0f
                                        ? ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY_FINISH
                                        : delta < 0f
                                                ? ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY
                                                : ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_ADD_ABILITY));
            }
            return delta;
        }
    }
}
