package link.locutus.discord.sim.strategy;

import link.locutus.discord.sim.SimNation;

final class StrategyTiming {

    private StrategyTiming() {
    }

    static boolean isResetWindow(SimNation self) {
        int phase = self.dayPhaseTurn();
        return phase == 0 || phase == 11;
    }

    static boolean isSaveWindow(SimNation self) {
        return self.dayPhaseTurn() == 11;
    }

    static boolean isStrikeWindow(SimNation self) {
        return self.dayPhaseTurn() == 0;
    }
}
