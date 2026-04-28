package link.locutus.discord.sim;

public enum BlitzObjective {
    NET_DAMAGE(new DamageObjective()),
    DAMAGE(new DamageDealtObjective()),
    MINIMUM_DAMAGE_RECEIVED(new DamageAvoidanceObjective()),
    CONTROL(new ControlObjective()),
    BALANCED(new BalancedBlitzObjective());

    private final TeamScoreObjective objective;

    BlitzObjective(TeamScoreObjective objective) {
        this.objective = objective;
    }

    public TeamScoreObjective objective() {
        return objective;
    }

    public static BlitzObjective defaultObjective() {
        return NET_DAMAGE;
    }
}