package link.locutus.discord._main;

import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BaseballDB;

import java.sql.SQLException;

public class FinalizedLoader implements ILoader {
    private volatile BaseballDB baseBallDB;

    public FinalizedLoader(PreLoader loader) {

    }

    @Override
    public BaseballDB getBaseballDB() {
        if (this.baseBallDB == null) {
            synchronized (this) {
                if (this.baseBallDB == null) {
                    try {
                        baseBallDB = new BaseballDB(Settings.INSTANCE.DATABASE);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return this.baseBallDB;
    }
}
