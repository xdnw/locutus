package link.locutus.discord.util.battle;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.sim.planners.DBNationSnapshot;

public interface BlitzDraftNation {
    int nationId();

    int allianceId();

    String nationName();

    double score();

    int cities();

    int tanks();

    int aircraft();

    int defensiveWars();

    int vmTurns();

    int activeMinutes();

    boolean espionageAvailable();

    boolean beige();

    static BlitzDraftNation of(DBNation nation) {
        return new DBNationBlitzDraftNation(nation);
    }

    static BlitzDraftNation of(DBNation nation, DBNationSnapshot snapshot) {
        if (snapshot == null) {
            return of(nation);
        }
        return new SnapshotBackedBlitzDraftNation(nation, snapshot, null);
    }

    static BlitzDraftNation of(DBNation nation, DBNationSnapshot snapshot, Boolean forceActive) {
        if (snapshot == null) {
            return of(nation);
        }
        return new SnapshotBackedBlitzDraftNation(nation, snapshot, forceActive);
    }

    final class DBNationBlitzDraftNation implements BlitzDraftNation {
        private final DBNation nation;

        private DBNationBlitzDraftNation(DBNation nation) {
            this.nation = nation;
        }

        @Override
        public int nationId() {
            return nation.getNation_id();
        }

        @Override
        public int allianceId() {
            return nation.getAlliance_id();
        }

        @Override
        public String nationName() {
            return nation.getNation();
        }

        @Override
        public double score() {
            return nation.getScore();
        }

        @Override
        public int cities() {
            return nation.getCities();
        }

        @Override
        public int tanks() {
            return nation.getTanks();
        }

        @Override
        public int aircraft() {
            return nation.getAircraft();
        }

        @Override
        public int defensiveWars() {
            return nation.getDef();
        }

        @Override
        public int vmTurns() {
            return nation.getVm_turns();
        }

        @Override
        public int activeMinutes() {
            return nation.active_m();
        }

        @Override
        public boolean espionageAvailable() {
            return nation.isEspionageAvailable();
        }

        @Override
        public boolean beige() {
            return nation.isBeige();
        }
    }

    final class SnapshotBackedBlitzDraftNation implements BlitzDraftNation {
        private final DBNation nation;
        private final DBNationSnapshot snapshot;
        private final Boolean forceActive;

        private SnapshotBackedBlitzDraftNation(DBNation nation, DBNationSnapshot snapshot, Boolean forceActive) {
            this.nation = nation;
            this.snapshot = snapshot;
            this.forceActive = forceActive;
        }

        @Override
        public int nationId() {
            return snapshot.nationId();
        }

        @Override
        public int allianceId() {
            return snapshot.allianceId();
        }

        @Override
        public String nationName() {
            return nation.getNation();
        }

        @Override
        public double score() {
            return snapshot.score();
        }

        @Override
        public int cities() {
            return snapshot.cities();
        }

        @Override
        public int tanks() {
            return snapshot.unit(link.locutus.discord.apiv1.enums.MilitaryUnit.TANK);
        }

        @Override
        public int aircraft() {
            return snapshot.unit(link.locutus.discord.apiv1.enums.MilitaryUnit.AIRCRAFT);
        }

        @Override
        public int defensiveWars() {
            return snapshot.currentDefensiveWars();
        }

        @Override
        public int vmTurns() {
            return snapshot.vmTurns();
        }

        @Override
        public int activeMinutes() {
            if (Boolean.TRUE.equals(forceActive)) {
                return 0;
            }
            if (Boolean.FALSE.equals(forceActive)) {
                return Integer.MAX_VALUE;
            }
            return nation.active_m();
        }

        @Override
        public boolean espionageAvailable() {
            return nation.isEspionageAvailable();
        }

        @Override
        public boolean beige() {
            return snapshot.beigeTurns() > 0;
        }
    }
}
