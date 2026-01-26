package link.locutus.discord.db;

import com.politicsandwar.graphql.model.BBGame;
import com.ptsmods.mysqlw.query.QueryOrder;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TablePreset;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.config.Settings;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.baseball.BaseballGameEvent;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

public class BaseballDB extends DBMainV2{
    public BaseballDB(Settings.DATABASE config) throws SQLException {
        super(config, "baseball");
    }

    @Override
    protected void createTables() {
        TablePreset.create("games")
                .putColumn("id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("date", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("home_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("away_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("home_nation_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("away_nation_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("home_score", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("away_score", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("home_revenue", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("spoils", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("open", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("wager", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .create(getDb())
        ;
    }

    public Integer getMaxGameId() {
        List<BBGame> latestGame = getBaseballGames(f -> f.order("id", QueryOrder.OrderDirection.DESC).limit(1));
        return latestGame.isEmpty() ? null : latestGame.get(0).getId();
    }

    public Integer getMinGameId() {
        List<BBGame> latestGame = getBaseballGames(f -> f.order("id", QueryOrder.OrderDirection.ASC).limit(1));
        return latestGame.isEmpty() ? null : latestGame.get(0).getId();
    }

    public void updateGames(Consumer<Event> eventConsumer) {
        BaseballDB db = Locutus.imp().getBaseballDB();
        Integer minId = db.getMinGameId();
        if (minId != null) minId++;
        db.updateGames(eventConsumer, false, minId, null);
    }

    public synchronized void updateGames(Consumer<Event> eventConsumer, boolean wagered, Integer minId, Integer maxId) {
        if (minId == null) eventConsumer = null;

        PoliticsAndWarV3 v3 = Locutus.imp().getApiPool();
        List<BBGame> games = v3.fetchBaseballGames(req -> {
            if (wagered) req.setMin_wager(1d);
            if (minId != null) req.setMin_id(minId);
            if (maxId != null) req.setMax_id(maxId);
        }, game -> {
            game.id();
            game.date();
            game.home_id();
            game.away_id();
            game.home_nation_id();
            game.away_nation_id();
            game.home_score();
            game.away_score();
            game.home_revenue();
            game.spoils();
            game.open();
            game.wager();
        });

        if (games.isEmpty()) return;
        final String sql = "INSERT OR IGNORE INTO `games` " +
                "(`id`,`date`,`home_id`,`away_id`,`home_nation_id`,`away_nation_id`,`home_score`,`away_score`,`home_revenue`,`spoils`,`open`,`wager`) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Connection con = getConnection();
        try (PreparedStatement stmt = con.prepareStatement(sql)) {
            con.setAutoCommit(false);
            try {
                for (BBGame game : games) {
                    if (game.getDate() == null || game.getId() == null) continue;

                    if (game.getHome_revenue() == null) game.setHome_revenue(0d);
                    if (game.getSpoils() == null) game.setSpoils(0d);
                    if (game.getWager() == null) game.setWager(0d);
                    if (game.getHome_score() == null) game.setHome_score(0);
                    if (game.getAway_score() == null) game.setAway_score(0);
                    if (game.getHome_id() == null) game.setHome_id(0);
                    if (game.getAway_id() == null) game.setAway_id(0);
                    if (game.getHome_nation_id() == null) game.setHome_nation_id(0);
                    if (game.getAway_nation_id() == null) game.setAway_nation_id(0);

                    stmt.setInt(1, game.getId());
                    stmt.setLong(2, game.getDate().toEpochMilli());
                    stmt.setInt(3, game.getHome_id());
                    stmt.setInt(4, game.getAway_id());
                    stmt.setInt(5, game.getHome_nation_id());
                    stmt.setInt(6, game.getAway_nation_id());
                    stmt.setInt(7, game.getHome_score());
                    stmt.setInt(8, game.getAway_score());
                    stmt.setLong(9, (long) (game.getHome_revenue() * 100));
                    stmt.setLong(10, (long) (game.getSpoils() * 100));
                    stmt.setInt(11, game.getOpen());
                    stmt.setLong(12, (long) (game.getWager() * 100));

                    int affected = stmt.executeUpdate();
                    boolean inserted = (affected == 1);

                    if (eventConsumer != null && inserted) {
                        eventConsumer.accept(new BaseballGameEvent(game));
                    }
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<BBGame> getBaseballGames(Consumer<SelectBuilder> query) {
        List<BBGame> result = new ArrayList<>();
        SelectBuilder builder = getDb().selectBuilder("games")
                .select("*");
        if (query != null) query.accept(builder);
        try (ResultSet rs = builder.executeRaw()) {
                while (rs.next()) {
                    BBGame game = new BBGame();
                    game.setId(rs.getInt("id"));
                    game.setDate(Instant.EPOCH.plusMillis(rs.getLong("date")));
                    game.setHome_id(rs.getInt("home_id"));
                    game.setAway_id(rs.getInt("away_id"));
                    game.setHome_nation_id(rs.getInt("home_nation_id"));
                    game.setAway_nation_id(rs.getInt("away_nation_id"));
                    game.setHome_score(rs.getInt("home_score"));
                    game.setAway_score(rs.getInt("away_score"));
                    game.setHome_revenue(rs.getLong("home_revenue") * 0.01d);
                    game.setSpoils(rs.getLong("spoils") * 0.01d);
                    game.setOpen(rs.getInt("open"));
                    game.setWager(rs.getLong("wager") * 0.01d);

                    result.add(game);

                }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
