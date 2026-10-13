package emu.grasscutter.game.ability.mixins;

import emu.grasscutter.data.binout.AbilityMixinData;

/** Executes a client-selected local callback; event detection remains with its source. */
@AbilityMixin(AbilityMixinData.Type.DoActionByEventMixin)
public final class DoActionByEventMixin extends DoActionByElementReactionMixin {}
