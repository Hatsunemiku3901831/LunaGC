package emu.grasscutter.game.ability.mixins;

import emu.grasscutter.data.binout.AbilityMixinData;

/** Runs an incoming callback; registering this handler does not create a server value watcher. */
@AbilityMixin(AbilityMixinData.Type.DoActionOnGlobalValueChangeMixin)
public final class DoActionOnGlobalValueChangeMixin extends DoActionByElementReactionMixin {}
