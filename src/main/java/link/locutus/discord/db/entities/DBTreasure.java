package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.Treasure;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class DBTreasure {
    private int id = -1;
    private String name;
    private NationColor color;
    private int bonus;
    private Continent continent;
    private int nation_id;
    private long spawn_date;

    private long last_respawn_alert;

    public DBTreasure set(Treasure treasure) {
        this.name = treasure.getName();
        if (treasure.getColor() != null  && !treasure.getColor().isEmpty() && !treasure.getColor().equalsIgnoreCase("any")) {
            this.color = NationColor.valueOf(treasure.getColor().toUpperCase(Locale.ROOT));

            // todo treasure in db read/write null for color/continent
        }
        this.bonus = treasure.getBonus();
        if (treasure.getContinent() != null) {
            if (!treasure.getContinent().equalsIgnoreCase("n") && !treasure.getContinent().isEmpty()) {
                this.continent = Continent.parseV3(treasure.getContinent());
            }
        }
        this.nation_id = treasure.getNation_id() != null ? treasure.getNation_id() : 0;
        this.spawn_date = treasure.getSpawn_date().getTime();
        return this;
    }

    public Predicate<DBNation> getFilter(boolean checkColor, boolean checkContinent) {

        return getFilter(getMaxNationScore(), checkColor, checkContinent);
    }

    public double getMaxNationScore() {
        DBNation maxNation = null;
        for (DBNation nation : Locutus.imp().getNationDB().getNations().values()) {
            if (maxNation == null || maxNation.getScore() < nation.getScore()) {
                maxNation = nation;
            }
        }
        if (maxNation == null) {
            throw new IllegalArgumentException("No nations found in database");
        }
        return maxNation.getScore();
    }

    public long getTimeUntilNextSpawn() {
        return getSpawnDate() + TimeUnit.DAYS.toMillis(60) - System.currentTimeMillis();
    }

    public long getTimeSinceLastAlert() {
        return System.currentTimeMillis() - getRespawnAlertDate();
    }

    public Set<DBNation> getNationsInRange() {
        return getNationsInRange(getMaxNationScore());
    }

    public Set<DBNation> getNationsInRange(double maxNationScore) {
        return Locutus.imp().getNationDB().getNationsMatching(getFilter(true, true));
    }

    public Predicate<DBNation> getFilter(double maxNationScore, boolean checkColor, boolean checkContinent) {
        double maxScore = maxNationScore * 0.65;
        double minScore = maxNationScore * 0.15;
        return (f -> {
            if (f.getVm_turns() > 0) return false;
            if (checkColor && color != null && color != f.getColor()) return false;
            if (checkContinent && continent != null && continent != f.getContinent()) return false;
            return f.getScore() <= maxScore && f.getScore() >= minScore;
        });
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public DBTreasure() {}

    public DBTreasure(int id, String name, NationColor color, int bonus, Continent continent, int nation_id, long spawn_date, long last_respawn_alert) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.bonus = bonus;
        this.continent = continent;
        this.nation_id = (nation_id);
        this.spawn_date = spawn_date;
        this.last_respawn_alert = last_respawn_alert;
    }


    public String getName() {
        return name;
    }

    public NationColor getColor() {
        return color;
    }

    public int getBonus() {
        return bonus;
    }

    public Continent getContinent() {
        return continent;
    }

    public int getNation_id() {
        return nation_id;
    }

    public void setNation_id(int nation_id) {
        this.nation_id = nation_id;
    }

    public long getSpawnDate() {
        return spawn_date;
    }

    public long getDaysRemaining() {
        return getTimeUntilNextSpawn() / TimeUnit.DAYS.toMillis(1);
    }

    public void setSpawnDate(long spawn_date) {
        this.spawn_date = spawn_date;
    }

    public boolean equalsExact(DBTreasure other) {
        return this.name.equals(other.name) && this.color == other.color && this.bonus == other.bonus && this.continent == other.continent && this.nation_id == other.nation_id && this.spawn_date == other.spawn_date;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DBTreasure other = (DBTreasure) obj;
        return other.name.equals(this.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public DBTreasure copy() {
        return new DBTreasure(id, name, color, bonus, continent, nation_id, spawn_date, last_respawn_alert);
    }

    public long getRespawnAlertDate() {
        return last_respawn_alert;
    }

    public void setRespawnAlertDate(long last_respawn_alert) {
        this.last_respawn_alert = last_respawn_alert;
    }
}
