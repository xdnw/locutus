package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.db.entities.DBNation;

public class WebTarget {

    public int id;
    public String nation;
    public int alliance_id;
    public String alliance;
    public double avg_infra;
    public int cities;
    public int soldier;
    public int tank;
    public int aircraft;
    public int ship;
    public int missile;
    public int nuke;
    public int spies;
    public int position;
    public long active_ms;
    public int color_id;
    public int beige_turns;
    public int off;
    public int def;
    public double score;
    public double expected;
    public double actual;
    public double strength;

    public WebTarget(DBNation nation, double expected, double actual, double strength) {
        this.id = nation.getId();
        this.nation = nation.getName();
        this.alliance_id = nation.getAlliance_id();
        this.alliance = nation.getAllianceName();
        this.avg_infra = nation.getAvg_infra();
        this.cities = nation.getCities();
        this.soldier = nation.getSoldiers();
        this.tank = nation.getTanks();
        this.aircraft = nation.getAircraft();
        this.ship = nation.getShips();
        this.missile = nation.getMissiles();
        this.nuke = nation.getNukes();
        this.spies = nation.getSpies();
        this.position = nation.getPositionEnum().ordinal();
        this.active_ms = nation.lastActiveMs();
        this.color_id = nation.getColor().ordinal();
        this.beige_turns = nation.getColor() == NationColor.BEIGE ? nation.getBeigeTurns() : 0;
        this.off = nation.getOff();
        this.def = nation.getDef();
        this.score = nation.getScore();
        this.expected = Math.min(Integer.MAX_VALUE, expected);
        this.actual = Math.min(Integer.MAX_VALUE, actual);
        this.strength = Math.min(Integer.MAX_VALUE, strength);
    }
}
