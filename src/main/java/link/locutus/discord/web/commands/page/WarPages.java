package link.locutus.discord.web.commands.page;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.CounterType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.web.builder.PageBuilder;
import link.locutus.discord.web.builder.TableBuilder;
import link.locutus.discord.apiv1.enums.Rank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class WarPages {
    @Command()
    @RolePermission(Roles.MILCOM)
    public String defensive(@Me GuildDB db) {
        return "TODO";
    }

    @Command()
    @RolePermission(Roles.MILCOM)
    public String offensive(@Me GuildDB db) {
        return "TODO";
    }

    @Command()
    @RolePermission(Roles.MILCOM)
    public String enemies(@Me GuildDB db) {
        return "TODO";
    }

    @Command(desc = "Wars which are currently uncountered")
    @RolePermission(Roles.MILCOM)
    public String counter(@Me GuildDB db) {
        Set<Integer> offshore = db.getCoalition(Coalition.OFFSHORE);
        Set<Integer> allies = db.getAllies();
        Set<Integer> aaIds = db.getAllianceIds();
        Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);

        List<DBWar> wars = Locutus.imp().getWarDb().getActiveWars(allies, WarStatus.ACTIVE, WarStatus.DEFENDER_OFFERED_PEACE, WarStatus.ATTACKER_OFFERED_PEACE);
        wars.removeIf(f -> {
            DBNation attacker = f.getNation(true);
            if (attacker == null || attacker.hasUnsetMil() || attacker.isBeige() || attacker.getDef() >= 3 || attacker.getVm_turns() > 0) return true;
            DBNation defender = f.getNation(false);
            return defender == null || defender.hasUnsetMil() || !allies.contains(defender.getAlliance_id());
        });

        Map<DBWar, CounterStat> counterStatMap = new HashMap<>();
        for (DBWar war : wars) {
            counterStatMap.put(war, war.getCounterStat());
        }

        wars.removeIf(f -> {
            CounterStat stat = counterStatMap.get(f);
            if (stat != null && !enemies.contains(f.attacker_aa) && stat.type == CounterType.IS_COUNTER) {
                return true;
            }
            return false;
        });

        Map<DBWar, WarCard> warCardMap = new HashMap<>();
        for (DBWar war : wars) {
            warCardMap.put(war, new WarCard(war, false));
        }
        Collections.sort(wars, new Comparator<DBWar>() {
            @Override
            public int compare(DBWar o1, DBWar o2) {
                return Long.compare(o2.date, o1.date);
            }
        });

        TableBuilder<DBWar> table = new TableBuilder<>();

        WarCategory warCat = db.getWarChannel();
        if (warCat != null) {
            table.addColumn("warroom", false, false, f -> {
                DBNation attacker = f.getNation(true);
                WarCategory.WarRoom room = warCat.get(attacker, false, false);
                if (room != null && room.channel != null) {
                    return MarkupUtil.htmlUrl("#" + room.channel.getName(), room.url());
                }
                return "";
            });
        }

        table.addColumn("type", false, false, f -> f.warType);
        table.addColumn("ctrl", true, false, f -> {
            String a = "";
            String b = "";
            WarCard card = warCardMap.get(f);
            if (card.blockaded == f.attacker_id) a += "<span data-toggle=\"tooltip\" title=\"attacker blockaded\">\u26F5</span>";
            if (card.blockaded == f.defender_id) a += "<span data-toggle=\"tooltip\" title=\"defender blockaded\">\u26F5</span>";

            if (card.airSuperiority == f.attacker_id) a += "<span data-toggle=\"tooltip\" title=\"attacker air control\">\u2708</span>";
            if (card.airSuperiority == f.defender_id) a += "<span data-toggle=\"tooltip\" title=\"defender air control\">\u2708</span>";

            if (card.groundControl == f.attacker_id) a += "<span data-toggle=\"tooltip\" title=\"attacker ground control\">\uD83D\uDC82</span>";
            if (card.groundControl == f.defender_id) a += "<span data-toggle=\"tooltip\" title=\"attacker ground control\">\uD83D\uDC82</span>";

            if (card.attackerFortified) a += "<span data-toggle=\"tooltip\" title=\"attacker fortified\">\uD83D\uDEE1</span>";
            if (card.defenderFortified) a += "<span data-toggle=\"tooltip\" title=\"defender fortified\">\uD83D\uDEE1</span>";

            if (f.status == WarStatus.ATTACKER_OFFERED_PEACE) {
                a += "<span data-toggle=\"tooltip\" title=\"attacker offers peace\">\uD83D\uDC95</span>";
            }
            if (f.status == WarStatus.DEFENDER_OFFERED_PEACE) {
                b += "<span data-toggle=\"tooltip\" title=\"defender offers peace\">\uD83D\uDC95</span>";
            }
            return a + "<br>" + b;
        });
        table.addColumn("alliance", true, false, f -> {
            String a = f.getAllianceHtmlUrl(true);
            String b = f.getAllianceHtmlUrl(false);
            return a + "<br>" + b;
        });
        table.addColumn("nation", false, false, f -> f.getNationHtmlUrl(true) + "<br>" + f.getNationHtmlUrl(false));
        table.addColumn("score", true, false, f -> f.getNation(true).getScore() + "<br>" + f.getNation(false).getScore()); // infra
        table.addColumn("\uD83C\uDFD9", true, false, f -> f.getNation(true).getCities() + "<br>" + f.getNation(false).getCities());
        table.addColumn("\uD83C\uDFD7", false, false, f -> f.getNation(true).getAvg_infra() + "<br>" + f.getNation(false).getAvg_infra()); // infra
        table.addColumn("\uD83D\uDC82", true, false, f -> f.getNation(true).getSoldiers() + "<br>" + f.getNation(false).getSoldiers());
        table.addColumn("\u2699", true, false, f -> f.getNation(true).getTanks() + "<br>" + f.getNation(false).getTanks());
        table.addColumn("\u2708", true, false, f -> f.getNation(true).getAircraft() + "<br>" + f.getNation(false).getAircraft());
        table.addColumn("\u26F5", true, false, f -> f.getNation(true).getShips() + "<br>" + f.getNation(false).getShips());

        table.addColumn("\uD83D\uDEE1", true, false, f -> f.getNation(true).getDef() + "<br>" + f.getNation(false).getDef());
        table.addColumn("url", false, false, f -> MarkupUtil.htmlUrl(f.warId + "", f.toUrl()));
        table.addColumn("\uD83D\uDDE1", false, false, f -> f.getNation(true).getOff() + "<br>" + f.getNation(false).getOff());

        table.addColumn("blockaded", false, false, f -> {
            WarCard card = warCardMap.get(f);
            if (card.blockaded == f.attacker_id) return "attacker";
            if (card.blockaded == f.defender_id) return "defender";
            return "none";
        });
        table.addColumn("AC", false, false, f -> {
            WarCard card = warCardMap.get(f);
            if (card.airSuperiority == f.attacker_id) return "attacker";
            if (card.airSuperiority == f.defender_id) return "defender";
            return "none";
        });
        table.addColumn("GC", false, false, f -> {
            WarCard card = warCardMap.get(f);
            if (card.groundControl == f.attacker_id) return "attacker";
            if (card.groundControl == f.defender_id) return "defender";
            return "none";
        });
        table.addColumn("resistance", false, false, f -> {
            WarCard card = warCardMap.get(f);
            return card.attackerResistance + "<br>" + card.defenderResistance;
        });
        table.addColumn("MAP", false, false, f -> {
            WarCard card = warCardMap.get(f);
            return card.attackerMAP + "<br>" + card.defenderMAP;
        });
        table.addColumn("def active", false, false, f -> {
            return TimeUtil.secToTime(TimeUnit.MINUTES, f.getNation(false).getActive_m());
        });
        table.addColumn("actions", false, false, f -> {
            String cmd = CM.war.counter.auto.cmd.create(f.attacker_id + "", null, null, null, null, null).toCommandArgs();
            String button = "<button cmd=\"" + cmd + "\" type=\"button\" class=\"btn-sm btn-primary\">Autocounter</button>";
            return button;
        });

        /*
        Partially countered
         */
        List<DBWar> partial = new ArrayList<>();
        List<DBWar> members = new ArrayList<>();

        List<DBWar> activeApps = new ArrayList<>();
        List<DBWar> inactiveApps = new ArrayList<>();

        List<DBWar> allyMembers = new ArrayList<>();
        List<DBWar> allyApps = new ArrayList<>();

        for (DBWar war : wars) {
            DBNation attacker = war.getNation(true);
            DBNation defender = war.getNation(false);
            if (attacker.getDef() > 0 && attacker.getRelativeStrength() < 1) {
                partial.add(war);
                continue;
            }
            if (offshore.contains(defender.getAlliance_id()) && defender.getPosition() >= Rank.MEMBER.id) {
                members.add(war);
                continue;
            }
            if (aaIds.contains(defender.getAlliance_id())) {
                if (defender.getPosition() >= Rank.MEMBER.id) {
                    members.add(war);
                    continue;
                }
                if (defender.getActive_m() < 7200) {
                    activeApps.add(war);
                    continue;
                }
                inactiveApps.add(war);
                continue;
            }
            if (allies.contains(defender.getAlliance_id())) {
                if (defender.getPosition() >= Rank.MEMBER.id) {
                    allyMembers.add(war);
                    continue;
                }
                allyApps.add(war);
            }
        }


//        // add offshores
//        for (DBWar war : wars) {
//            DBNation defender = war.getNation(false);
//            if (offshore.contains(defender.getAlliance_id()) && defender.getPosition() >= Rank.MEMBER.id) members.add(war);
//        }
//        // add active members
//        for (DBWar war : wars) {
//            DBNation defender = war.getNation(false);
//            if (aaId != null && defender.getAlliance_id() == aaId && defender.getPosition() >= Rank.MEMBER.id) members.add(war);
//        }

        // Offshore
        // Active members
        // Inactive members
        // Active applicants
        // Protectorate members
        // Ally members

        // inactive apps
        // slotted/beiged targets
        // protectorate apps
        // ally apps
        PageBuilder builder = new PageBuilder();
        builder.title("Uncountered wars");
        if (!partial.isEmpty()) {
            builder.spoiler("Partially countered (" + partial.size() + ")", table.buildHtml("", partial));
        }
        if (!members.isEmpty()) {
            builder.spoiler("Members (" + members.size() + ")", table.buildHtml("", members));
        }
        if (!activeApps.isEmpty()) {
            builder.spoiler("Active Applicants (" + activeApps.size() + ")", table.buildHtml("", activeApps));
        }
        if (!inactiveApps.isEmpty()) {
            builder.spoiler("Inactive Applicants (" + inactiveApps.size() + ")", table.buildHtml("", inactiveApps));
        }
        if (!allyMembers.isEmpty()) {
            builder.spoiler("Ally members (" + allyMembers.size() + ")", table.buildHtml("", allyMembers));
        }
        if (!allyApps.isEmpty()) {
            builder.spoiler("Ally Applicants (" + allyApps.size() + ")", table.buildHtml("", allyApps));
        }

        return builder.buildWithContainer();
    }
}
