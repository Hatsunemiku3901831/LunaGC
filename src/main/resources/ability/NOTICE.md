# Modifier stacking reference

The bounded oldest-slot refresh rule in `AbilityModifierStacking` was adapted from
`pkg/core/stacks/refresh.go` in genshinsim/gcsim, release v2.47.2
(commit caf738dc807a646e2eee7e7b572f225dd6deb510, MIT license).
https://github.com/genshinsim/gcsim/blob/caf738dc807a646e2eee7e7b572f225dd6deb510/pkg/core/stacks/refresh.go

The inspected current commit a3b24946babe26eb5b9e9101d0e0a55c20a2ea59 has identical
stack tracker and tests. The MIT notice is retained in `LICENSE.gcsim-stacks`.
LunaGC uses its own entity tick, instance registry, callback ownership, and timers.
This reference does not prove the current game's complete modifier protocol or animations.
