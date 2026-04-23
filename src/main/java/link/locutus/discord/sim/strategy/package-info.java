/**
 * Strategy primitives and rule-based actor implementations.
 * 
 * This package contains composable decision-making components that drive nation behavior
 * during simulation:
 * 
 * - {@link link.locutus.discord.sim.strategy.StrategyPrimitive}: base contract for tactical concepts
 * - {@link link.locutus.discord.sim.strategy.RuleBasedActor}: menu-based actor that combines primitives
 * - Concrete primitives: {@code AirControlBuild}, {@code GroundUnderAir},
 *   {@code AttritionInfraMode}, {@code RaidLootMode}, {@code ShipShortageNaval},
 *   {@code MapReserveForCoordination}, {@code MapReserveForReaction},
 *   {@code DoubleBuyWindow}, {@code SaveAndStrike},
 *   {@code BalancedCasualtyRotation}, {@code WeakSubsetFocus},
 *   {@code HighScoreEarlyStrike}
 * 
 * Primitives are stateless, deterministic, and evaluated independently each turn.
 * They are composable: a nation's mix can change across turns based on game state.
 * 
 * Dependencies: inbound from {@code sim/} layer only.
 */
package link.locutus.discord.sim.strategy;
