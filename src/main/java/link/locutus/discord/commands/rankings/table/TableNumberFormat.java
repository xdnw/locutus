package link.locutus.discord.commands.rankings.table;

import link.locutus.discord.util.MathMan;

public enum TableNumberFormat {
    SI_UNIT {
        @Override
        public String toString(Number number) {
            if (Math.abs(number.doubleValue()) < 10) {
                return MathMan.format(number);
            }
            return MathMan.formatSig(number.doubleValue());
        }
    },
    PERCENTAGE_ONE {
        @Override
        public String toString(Number number) {
            return MathMan.format(Math.round(number.doubleValue() * 100 * 100) / 100d) + "%";
        }
    },
    PERCENTAGE_100 {
        @Override
        public String toString(Number number) {
            return MathMan.format(number.doubleValue()) + "%";
        }
    },
    DECIMAL_ROUNDED {
        @Override
        public String toString(Number number) {
            return MathMan.format(number);
        }
    },

    ;

    public abstract String toString(Number number);
}
