/**
 * Experimental strategy primitives and rule-based actor implementations.
 * 
 * <p>This package is an isolated research surface for tactical concept inventory. It is not a
 * planner evaluation authority, and planner assignment/search code must not copy its score-era
 * attack valuation directly.</p>
 *
 * <p>Control-like heuristics in this package are intentionally local tactical rules, not the
 * canonical planner definition of control. Air-control bonuses, local timing bonuses, and similar
 * scripted preferences here must not be reused as planner terminal control semantics, future-war
 * leverage, or durable-control logic.</p>
 *
 * <p>The package contains composable decision-making components that drive scripted nation
 * behavior during simulation:
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
 * <p>Primitives are stateless, deterministic, and evaluated independently each turn.
 * They are composable: a nation's mix can change across turns based on game state.</p>
 * 
 * Dependencies: inbound from {@code sim/} layer only.
 */
package link.locutus.discord.sim.strategy;
