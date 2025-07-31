package link.locutus.discord.db.entities;

public class TaxRecordSummary {
    public final double[] no_internal;
    public final double[] internal_applied;
    public final double[] internal_unapplied;
    public boolean dirty;

    public TaxRecordSummary(
            double[] no_internal,
            double[] internal_applied,
            double[] internal_unapplied,
            boolean dirty
    ) {
        this.no_internal       = no_internal;
        this.internal_applied   = internal_applied;
        this.internal_unapplied = internal_unapplied;
        this.dirty              = dirty;
    }

}
