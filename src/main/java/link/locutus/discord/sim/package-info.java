/**
 * Root of the war-simulation / target-assignment layers described in docs/WAR_SIM_ROADMAP.md.
 *
 * Three one-way layers live underneath:
 * <ul>
 *   <li>{@code sim.combat} – pure wrappers over existing PnW math (AttackType, PW, MilitaryUnit).
 *       May be called from {@code sim} and {@code sim.planners}; must not import either.</li>
 *   <li>{@code sim} – deterministic turn engine and provider interfaces (not yet introduced).</li>
 *   <li>{@code sim.planners} – user-facing use cases that translate DB entities into sim input
 *       (not yet introduced).</li>
 * </ul>
 */
package link.locutus.discord.sim;
