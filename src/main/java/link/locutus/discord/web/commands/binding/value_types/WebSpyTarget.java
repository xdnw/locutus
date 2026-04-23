package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.commands.war.SpyOpsService;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Safety;
import link.locutus.discord.util.Operation;

public class WebSpyTarget {
    public int id;
    public String nation;
    public int alliance_id;
    public String alliance;
    public int spies;
    public double score;
    public long active_ms;
    public int color_id;
    
    public int operation;
    public int safety;
    public int enemy_spies;
    public int attacker_spies;
    public double odds;
    public double kills;
    public double damage;

    public WebSpyTarget(SpyOpsService.SpyOpRecommendation recommendation) {
        DBNation target = recommendation.target();
        this.id = target.getId();
        this.nation = target.getName();
        this.alliance_id = target.getAlliance_id();
        this.alliance = target.getAllianceName();
        this.spies = target.getSpies();
        this.score = target.getScore();
        this.active_ms = target.lastActiveMs();
        this.color_id = target.getColor().ordinal();
        
        this.operation = recommendation.operation().ordinal();
        this.safety = Safety.byId(recommendation.safety()).ordinal();
        this.enemy_spies = recommendation.enemySpies();
        this.attacker_spies = recommendation.attackerSpiesUsed();
        this.odds = recommendation.odds();
        this.kills = recommendation.kills();
        this.damage = recommendation.netDamage();
    }

    public WebSpyTarget(SpyOpsService.IntelRecommendation recommendation) {
        DBNation target = recommendation.target();
        this.id = target.getId();
        this.nation = target.getName();
        this.alliance_id = target.getAlliance_id();
        this.alliance = target.getAllianceName();
        this.spies = target.getSpies();
        this.score = target.getScore();
        this.active_ms = target.lastActiveMs();
        this.color_id = target.getColor().ordinal();

        this.operation = Operation.INTEL.ordinal();
        this.safety = 0;
        this.enemy_spies = 0;
        this.attacker_spies = 0;
        this.odds = 0;
        this.kills = 0;
        this.damage = recommendation.intelValue();
    }
}