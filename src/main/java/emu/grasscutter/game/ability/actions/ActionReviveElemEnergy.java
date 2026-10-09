package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.net.proto.ChangeEnergyReasonOuterClass.ChangeEnergyReason;

import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.ReviveElemEnergy)
public final class ActionReviveElemEnergy extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (!(target instanceof EntityAvatar avatar) || avatar.getScene()==null) return false;
        if (!conditionsPass(ability,action,target) || !Configuration.GAME_OPTIONS.energyUsage) return true;
        var depot=avatar.getAvatar().getSkillDepot();
        if (depot==null || depot.getElementType()==null) return false;
        float delta=action.writtenValue().get(propertiesFor(ability),0f);
        if (!Float.isFinite(delta)) return false;
        if (delta==0) return true;
        var element=depot.getElementType();
        var property=element.getCurEnergyProp();
        synchronized(avatar.getFightProperties()) {
            float current=avatar.getFightProperty(property);
            float maximum=avatar.getFightProperty(element.getMaxEnergyProp());
            if (!Float.isFinite(current) || !Float.isFinite(maximum) || maximum<0) return false;
            float next=(float)Math.max(0,Math.min(maximum,(double)current+delta));
            if (next==current) return true;
            avatar.getAvatar().setCurrentEnergy(property,next);
            avatar.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(avatar,property));
            avatar.getScene().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(
                    avatar,property,next-current,PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                    ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_ABILITY));
        }
        return true;
    }
}
