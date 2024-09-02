package link.locutus.discord._main;

import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.commands.manager.CommandManager;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.*;
import link.locutus.discord.util.trade.TradeManager;
import net.dv8tion.jda.api.JDA;

import java.sql.SQLException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public class FinalizedLoader implements ILoader {
    private volatile BaseballDB baseBallDB;

    private final SlashCommandManager slashCommandManager;
    private final JDA jda;
    private final ForumDB forumDb;
    private final DiscordDB discordDB;
    private final NationDB nationDB;
    private final WarDB warDb;
    private final StockDB stockDB;
    private final BankDB bankDb;
    private final TradeManager tradeManager;
    private final CommandManager commandManager;
    private final PoliticsAndWarV2 apiV2;
    private final PoliticsAndWarV3 apiV3;

    public FinalizedLoader(PreLoader loader) {
        this.slashCommandManager = loader.getSlashCommandManager();
        this.jda = loader.getJda();
        this.forumDb = loader.getForumDB();
        this.discordDB = loader.getDiscordDB();
        this.nationDB = loader.getNationDB();
        this.warDb = loader.getWarDB();
        this.stockDB = loader.getStockDB();
        this.bankDb = loader.getBankDB();
        this.tradeManager = loader.getTradeManager();
        this.commandManager = loader.getCommandManager();
        this.apiV2 = loader.getApiV2();
        this.apiV3 = loader.getApiV3();
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

    @Override
    public SlashCommandManager getSlashCommandManager() {
        return slashCommandManager;
    }

    @Override
    public ILoader resolveFully(long timeout) {
        return this;
    }

    @Override
    public void initialize() {
        // Do nothing
    }

    @Override
    public JDA getJda() {
        return jda;
    }

    @Override
    public ForumDB getForumDB() {
        return forumDb;
    }

    @Override
    public DiscordDB getDiscordDB() {
        return discordDB;
    }

    @Override
    public NationDB getNationDB() {
        return nationDB;
    }

    @Override
    public WarDB getWarDB() {
        return warDb;
    }

    @Override
    public StockDB getStockDB() {
        return stockDB;
    }

    @Override
    public BankDB getBankDB() {
        return bankDb;
    }

    @Override
    public TradeManager getTradeManager() {
        return tradeManager;
    }

    @Override
    public CommandManager getCommandManager() {
        return commandManager;
    }

    @Override
    public PoliticsAndWarV2 getApiV2() {
        return apiV2;
    }

    @Override
    public PoliticsAndWarV3 getApiV3() {
        return apiV3;
    }


}
