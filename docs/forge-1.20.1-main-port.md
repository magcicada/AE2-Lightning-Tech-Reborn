# Forge 1.20.1 main integration

Reference: main `5e12a0d` (final state), applied on the existing Forge port.

## Included

- Provider cache/dispatch ownership fixes, live input allocation, closed-loop seed handling and manual Tianshu CPU selection across CPU-list changes.
- Machine energy recharge batching, standalone main-core compute budgets, updated multiblock recipes, tooltips and guides.
- Arbitrary-precision storage/crafting integration using Thunderbolt 2.0.0, with bounded Forge SimpleChannel messages and extensions to the existing AE2 menus.
- Guarded NeoECO integration. Forge NeoECO 20.3.0 lacks the allocated FastPath facade: ordinary dispatch remains active. The adapter links reflectively only when the complete public allocated API exists; no NeoForge dependency is required.
- Official Useless 1.20.1 lacks the BigInteger and Smart Doubling APIs. The inactive Thunderbolt adapters and the corresponding LT protocol probe were removed after the 2026-10-04 source audit; ordinary machine execution remains in use.

## Intentionally deferred

The replacement crafting-confirmation screen, routing mixin, report-only state and visual assets are not included. Native AE2 confirmation remains in use. AE2 15 has no scheduling-pause button, so no injection targets the newer `toggleScheduling` method.

## Port contracts

Java 17; Minecraft 1.20.1; Forge 47; AE2 15. Preserve Forge registry/bootstrap behavior, array-based pattern outputs, NBT codecs, existing optional integrations, licenses and attribution. No remote development branches or process commits are published.

## Validation

- Thunderbolt: 837 JUnit tests; build and local Maven publication.
- AE2LT: 1182 behavior tests plus 12 binary Mixin-selector cases; Forge build.
- Forge dedicated-server Mixin audit and 35 GameTests, including exact status/CPU-list/confirmation packet round trips.
- The replacement confirmation screen is absent. Client target signatures are checked without loading client classes; a full interactive client session and a full process restart have not been exercised.
