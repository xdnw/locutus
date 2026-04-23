/**
 * Pure wrappers over existing PnW combat math.
 *
 * Rules:
 * <ul>
 *   <li>No hidden state. Every public method is a pure function of its inputs.</li>
 *   <li>Delegates to {@link link.locutus.discord.apiv1.enums.AttackType},
 *       {@link link.locutus.discord.util.PW}, and
 *       {@link link.locutus.discord.apiv1.enums.MilitaryUnit} – never reimplements formulas.</li>
 *   <li>May not import {@code link.locutus.discord.sim} (outer sim layer) or any planner
 *       package. DBNation is accepted because AttackType.getCasualties requires it; this is
 *       the only DB coupling the combat layer owns and is the seam sim snapshots will bridge
 *       in a later milestone.</li>
 * </ul>
 */
package link.locutus.discord.sim.combat;
