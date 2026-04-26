package link.locutus.discord.util.battle;

import link.locutus.discord.db.entities.DBNation;

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
}
