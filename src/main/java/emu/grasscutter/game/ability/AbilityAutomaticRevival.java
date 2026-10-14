package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarSkillInfoNotify;

import java.util.*;

/** Barbara's resource-defined offstage revival; unrelated DoRevive profiles remain dormant. */
public final class AbilityAutomaticRevival {
    private static final String PRE = "Avatar_Barbara_ReBorn_Pre";
    private static final String REVIVE = "Avatar_Barbara_ReBorn";

    private AbilityAutomaticRevival() {}

    public static boolean nativeAbility(Ability ability) {
        return ability != null && REVIVE.equals(ability.getData().abilityName);
    }

    /**
     * The authoritative death callback already performed this use; confirmations cannot heal again.
     */
    public static boolean nativeConfirmation(Ability ability, GameEntity target) {
        return nativeAbility(ability) && AbilityGlobalValues.isCurrent(ability, target);
    }

    /** Capture a valid candidate before death callbacks can change party, scene or session. */
    public static Pending prepare(EntityAvatar fallen) {
        var player = fallen == null ? null : fallen.getPlayer();
        if (player == null || player.getScene() != fallen.getScene()) return null;
        var party = player.getTeamManager().getActiveTeamSnapshot();
        if (party.stream().noneMatch(member -> member == fallen) || party.isEmpty()) return null;
        int index = player.getTeamManager().getCurrentCharacterIndex();
        if (index < 0 || index >= party.size()) return null;
        var pre = GameData.getAbilityData(PRE);
        var data = GameData.getAbilityData(REVIVE);
        var skillStart =
                action(
                        data == null ? null : data.onAbilityStart,
                        AbilityModifierAction.Type.AvatarSkillStart);
        var heal =
                action(
                        data == null ? null : data.onAbilityStart,
                        AbilityModifierAction.Type.ReviveAvatar);
        if (pre == null
                || skillStart == null
                || heal == null
                || !skillStart.doOffStage
                || !"Self".equals(skillStart.target)
                || !heal.doOffStage
                || !"AllPlayerAvatars".equals(heal.target)
                || !conditionsSupported(skillStart)
                || !conditionsSupported(heal)
                || !profile(pre, skillStart)
                || !AbilityAvatarRevival.supported(heal)) return null;
        var skill = GameData.getAvatarSkillDataMap().get(skillStart.skillID);
        if (skill == null
                || !REVIVE.equals(skill.getAbilityName())
                || skill.getShareCDID() != 0
                || skill.getMaxChargeNum() != 1
                || skill.getCostElemVal() != 0
                || skill.getCostStamina() != 0
                || !Float.isFinite(skill.getCdTime())
                || skill.getCdTime() <= 0) return null;
        for (var caster : party) {
            if (caster == fallen
                    || caster == party.get(index)
                    || caster.getPlayer() != player
                    || !living(caster)
                    || !granted(caster, PRE)
                    || !granted(caster, REVIVE)
                    || !AbilitySkillCooldown.ownsSkill(caster.getAvatar(), skillStart.skillID))
                continue;
            var ability = new Ability(data, caster, player);
            if (!AbilityAvatarRevival.eligible(ability, heal, fallen)) continue;
            var ratio =
                    skillStart.cdTimeRatio != null
                            ? skillStart.cdTimeRatio
                            : skillStart.legacyCdRatio;
            var value =
                    ratio == null
                            ? OptionalDouble.of(1)
                            : ratio.tryGet(AbilityActionHandler.propertiesFor(ability, caster));
            var duration =
                    skill.isIgnoreCDMinusRatio()
                            ? OptionalDouble.of(skill.getCdTime())
                            : AbilityTalentResolver.cooldownSeconds(
                                    caster.getAvatar(), skillStart.skillID, skill.getCdTime());
            if (value.isEmpty() || duration.isEmpty()) continue;
            double seconds = value.getAsDouble() * duration.getAsDouble();
            if (!Double.isFinite(seconds)
                    || seconds <= 0
                    || seconds * 1000 > 0xffffffffL
                    || AbilityAvatarRevival.health(ability, heal, fallen).isEmpty()) continue;
            var pending = new Pending(ability, heal, fallen, caster, skillStart.skillID, seconds);
            if (pending.ready()) return pending;
        }
        return null;
    }

    private static AbilityModifierAction action(
            AbilityModifierAction[] actions, AbilityModifierAction.Type type) {
        if (actions == null) return null;
        AbilityModifierAction found = null;
        for (var action : actions)
            if (action != null && action.type == type) {
                if (found != null) return null;
                found = action;
            }
        return found;
    }

    private static boolean profile(AbilityData pre, AbilityModifierAction start) {
        if (!start.doOffStage || pre.abilityMixins == null || pre.onAdded == null) return false;
        var unlock = action(pre.onAdded, AbilityModifierAction.Type.UnlockSkill);
        if (unlock == null || unlock.skillID != start.skillID) return false;
        for (var mixin : pre.abilityMixins) {
            if (mixin == null
                    || mixin.type != AbilityMixinData.Type.DoReviveMixin
                    || !"OffStage".equals(mixin.eventExecutionType)
                    || mixin.predicates != null && !mixin.predicates.isEmpty()) continue;
            var callbacks =
                    mixin.onKillActions != null ? mixin.onKillActions : mixin.rawReviveKillActions;
            var use = action(callbacks, AbilityModifierAction.Type.EntityDoSkill);
            if (use == null
                    || use.skillID != start.skillID
                    || !use.doOffStage
                    || !conditionsSupported(use)
                    || use.predicates != null && !use.predicates.isEmpty()) continue;
            if (Arrays.stream(callbacks)
                    .allMatch(
                            a ->
                                    a != null
                                            && (a == use
                                                    || a.type
                                                            == AbilityModifierAction.Type
                                                                    .DebugLog))) return true;
        }
        return false;
    }

    private static boolean conditionsSupported(AbilityModifierAction action) {
        return action.otherTargets == null
                && AbilityActionSequence.supportedPredicates(action.predicates, 0);
    }

    private static boolean granted(EntityAvatar caster, String name) {
        var depot = caster.getAvatar().getSkillDepot();
        if (depot == null || depot.getTalents() == null) return false;
        for (int id : caster.getAvatar().getTalentIdList()) {
            if (!depot.getTalents().contains(id)) continue;
            var talent = GameData.getAvatarTalentDataMap().get(id);
            var config =
                    talent == null
                            ? null
                            : GameData.getOpenConfigEntries().get(talent.getOpenConfig());
            if (config != null
                    && config.getAddAbilities() != null
                    && Arrays.asList(config.getAddAbilities()).contains(name)) return true;
        }
        return false;
    }

    private static boolean living(EntityAvatar caster) {
        float hp = caster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        return caster.isAlive() && Float.isFinite(hp) && hp > 0;
    }

    public static final class Pending {
        private final Ability ability;
        private final AbilityModifierAction heal;
        private final EntityAvatar fallen, caster;
        private final int skill;
        private final double seconds;
        private final AbilityExecutionContext context;
        private final GameSession session;

        private Pending(
                Ability ability,
                AbilityModifierAction heal,
                EntityAvatar fallen,
                EntityAvatar caster,
                int skill,
                double seconds) {
            this.ability = ability;
            this.heal = heal;
            this.fallen = fallen;
            this.caster = caster;
            this.skill = skill;
            this.seconds = seconds;
            context = new AbilityExecutionContext(ability, fallen);
            session = ability.getPlayerOwner().getSession();
        }

        private boolean ready() {
            var player = ability.getPlayerOwner();
            var party = player.getTeamManager().getActiveTeamSnapshot();
            int index = player.getTeamManager().getCurrentCharacterIndex();
            if (!context.isCurrent()
                    || session != player.getSession()
                    || !living(caster)
                    || index < 0
                    || index >= party.size()
                    || party.get(index) == caster
                    || !granted(caster, PRE)
                    || !granted(caster, REVIVE)
                    || !AbilityAvatarRevival.eligible(ability, heal, caster)
                    || !AbilityAvatarRevival.eligible(ability, heal, fallen)
                    || caster.getAvatar().getSkillInfoCharges().getOrDefault(skill, 1) != 1)
                return false;
            var start =
                    action(
                            ability.getData().onAbilityStart,
                            AbilityModifierAction.Type.AvatarSkillStart);
            if (start == null || !conditions(start, caster) || !conditions(heal, fallen))
                return false;
            var state =
                    player.getAbilityManager()
                            .getSkillCooldown()
                            .state(caster.getAvatar(), skill, 1);
            return state != null && state.getFullCdTimeListCount() == 0;
        }

        @SuppressWarnings("unchecked")
        private boolean conditions(AbilityModifierAction action, GameEntity target) {
            return PredicateEvaluator.all(
                    (List<Map<String, Object>>) (List<?>) action.predicates,
                    ability,
                    ability.getOwner(),
                    target,
                    action);
        }

        /**
         * Death and energy loss must already be settled. Revalidate after plugin death callbacks.
         */
        public boolean revive() {
            synchronized (caster.getAvatar()) {
                if (!ready()) return false;
                var health = AbilityAvatarRevival.health(ability, heal, fallen);
                if (health.isEmpty() || !fallen.revive((float) health.getAsDouble())) return false;
                var player = ability.getPlayerOwner();
                var cooldown = player.getAbilityManager().getSkillCooldown();
                if (cooldown.start(caster.getAvatar(), skill, seconds)) {
                    var state = cooldown.state(caster.getAvatar(), skill, 1);
                    if (state != null)
                        player.sendPacket(
                                new PacketAvatarSkillInfoNotify(
                                        caster.getAvatar().getGuid(), Map.of(skill, state)));
                }
                return true;
            }
        }
    }
}
