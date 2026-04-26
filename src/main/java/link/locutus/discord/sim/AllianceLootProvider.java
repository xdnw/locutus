package link.locutus.discord.sim;

@FunctionalInterface
public interface AllianceLootProvider {
    AllianceLootProvider NO_OP = (winner, loser, war) -> {
        // Intentionally no-op.
    };

    void applyAllianceLoot(SimNation winner, SimNation loser, SimWar war);
}
