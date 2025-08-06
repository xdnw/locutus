package link.locutus.discord.db.entities;

import link.locutus.discord.util.MathMan;

public class TaxRecordSummary {
    public final double[] no_internal_applied;
    public final double[] no_internal_unapplied;
    public final double[] internal_applied;
    public final double[] internal_unapplied;
    public final byte moneyTaxRate, rssTaxRate;

    public boolean dirty;

    public TaxRecordSummary(
            short taxPair,
            double[] no_internal_applied,
            double[] no_internal_unapplied,
            double[] internal_applied,
            double[] internal_unapplied,
            boolean dirty
    ) {
        this.moneyTaxRate = MathMan.unpairShortX(taxPair);
        this.rssTaxRate = MathMan.unpairShortY(taxPair);
        this.no_internal_applied = no_internal_applied;
        this.no_internal_unapplied = no_internal_unapplied;
        this.internal_applied   = internal_applied;
        this.internal_unapplied = internal_unapplied;
        this.dirty              = dirty;
    }

}
