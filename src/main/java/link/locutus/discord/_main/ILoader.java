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

public interface ILoader {
    ILoader resolveFully();

    default String getApiKey() {
        return Settings.INSTANCE.API_KEY_PRIMARY;
    }

    default int getNationId() {
        return Settings.INSTANCE.NATION_ID;
    }

    default long getAdminUserId() {
        return Settings.INSTANCE.ADMIN_USER_ID;
    }
    JDA getJda();
    SlashCommandManager getSlashCommandManager();
    CommandManager getCommandManager();

    NationDB getNationDB();
    DiscordDB getDiscordDB();
    WarDB getWarDB();
    BaseballDB getBaseballDB();
    TradeManager getTradeManager();
    StockDB getStockDB();
    ForumDB getForumDB();
    BankDB getBankDB();

    PoliticsAndWarV3 getApiV3();
    PoliticsAndWarV2 getApiV2();
}
