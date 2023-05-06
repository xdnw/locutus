package link.locutus.discord.util.update;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.commands.external.guild.SyncBounties;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.GuildHandler;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.war.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.bounty.BountyCreateEvent;
import link.locutus.discord.event.bounty.BountyRemoveEvent;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.war.WarCard;
import com.google.common.eventbus.Subscribe;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.entities.AllianceMeta;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.CounterType;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static link.locutus.discord.apiv1.enums.AttackType.*;

public class WarUpdateProcessor {
    @Subscribe
    public void onBountyCreate(BountyCreateEvent event) {
        DBBounty bounty = event.bounty;
        AlertUtil.forEachChannel(SyncBounties.class, GuildDB.Key.BOUNTY_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                Guild guild = guildDB.getGuild();
                try {
                    bounty.toCard(channel, false);

                    Role bountyRole = Roles.BOUNTY_ALERT.toRole(guild);
                    if (bountyRole == null) return;
                    List<Member> members = guild.getMembersWithRoles(bountyRole);
                    StringBuilder mentions = new StringBuilder();

                    DBNation enemy = Locutus.imp().getNationDB().getNation(bounty.getNationId());
                    if (enemy == null || enemy.getDef() >= 3 || enemy.getVm_turns() != 0 || enemy.isBeige()) return;

                    double minScore = enemy.getScore() / 1.75;
                    double maxScore = enemy.getScore() / 0.75;

                    for (Member member : members) {
                        PNWUser pnwUser = Locutus.imp().getDiscordDB().getUserFromDiscordId(member.getIdLong());
                        if (pnwUser == null) continue;

                        DBNation nation = Locutus.imp().getNationDB().getNation(pnwUser.getNationId());
                        if (nation == null) continue;

                        if (nation.getScore() >= minScore && nation.getScore() <= maxScore) {
                            mentions.append(member.getAsMention() + " ");
                        }
                    }
                    if (mentions.length() != 0) {
                        RateLimitUtil.queueWhenFree(channel.sendMessage(mentions));
                    }
                } catch (Throwable ignore) {
                    ignore.printStackTrace();
                }
            }
        });
    }

    @Subscribe
    public void onBountyRemove(BountyRemoveEvent event) {
        // TODO idk
    }

    public static void processWars(List<Map.Entry<DBWar, DBWar>> wars, Consumer<Event> eventConsumer) {
        if (wars.isEmpty()) return;
//
        handleAlerts(wars);
        handleAudits(wars); // TODO (from legacy)

        Locutus.imp().getExecutor().submit(() -> handleWarRooms(wars));

        for (Map.Entry<DBWar, DBWar> entry : wars) {
            DBWar previous = entry.getKey();
            DBWar current = entry.getValue();

            if (eventConsumer != null) {
                if (previous == null) {
                    eventConsumer.accept(new WarCreateEvent(current));
                } else {
                    eventConsumer.accept(new WarStatusChangeEvent(previous, current));
                }
            }

            try {
                processLegacy(previous, current);
            }  catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Subscribe
    public void onWarStatus(WarStatusChangeEvent event) {
        DBWar current = event.getCurrent();
        if (event.getPrevious() != null && current != null) {
            WarStatus status1 = event.getPrevious().getStatus();
            WarStatus status2 = current.getStatus();

            if (status1 != status2) {
                boolean isPeace1 = status1 == WarStatus.PEACE || status1 == WarStatus.ATTACKER_OFFERED_PEACE || status1 == WarStatus.DEFENDER_OFFERED_PEACE;
                boolean isPeace2 = status2 == WarStatus.PEACE || status2 == WarStatus.ATTACKER_OFFERED_PEACE || status2 == WarStatus.DEFENDER_OFFERED_PEACE;

                if (isPeace1 || isPeace2) {
                    new WarPeaceStatusEvent(event.getPrevious(), event.getCurrent()).post();
                }
            }
        }
    }


    @Subscribe
    public void onAttack(AttackEvent event) {
        DBAttack root = event.getAttack();
        int nationId = root.attacker_nation_id;
        DBNation attacker = Locutus.imp().getNationDB().getNation(root.attacker_nation_id);
        DBNation defender = Locutus.imp().getNationDB().getNation(root.defender_nation_id);

        if (attacker == null || defender == null) {
            return;
        }

        if (attacker.getPosition() > 1) {
            GuildDB guildDb = Locutus.imp().getGuildDBByAA(attacker.getAlliance_id());
            if (guildDb != null) {
                try {
                    guildDb.getHandler().onAttack(attacker, root);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        if (defender.getPosition() > 1) {
            GuildDB guildDb = Locutus.imp().getGuildDBByAA(defender.getAlliance_id());
            if (guildDb != null) {
                try {
                    guildDb.getHandler().onAttack(defender, root);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        if (root.attack_type == AttackType.VICTORY) {
            int aaId = attacker.getAlliance_id();
            if (aaId != 0) {
                GuildDB db = Locutus.imp().getGuildDBByAA(aaId);
                if (db != null) {
                    db.getHandler().beigeAlert(root);
                }
                for (GuildDB other : Locutus.imp().getGuildDatabases().values()) {
                    if (other == db) continue;
                    if (!other.getAllies(false).contains(aaId)) continue;
                    other.getHandler().beigeAlert(root);
                }
            }
        }

        if (attacker.getAlliance_id() == 0) {
            return;
        }

        for (int allianceId : new int[] {attacker.getAlliance_id(), defender.getAlliance_id()}) {
            if (allianceId == 0) continue;
            GuildDB db = Locutus.imp().getGuildDBByAA(allianceId);
            if (db == null) continue;

            WarCategory warChannel = db.getWarChannel();
            if (warChannel == null) continue;
            try {
                warChannel.update(root);
            } catch (InsufficientPermissionException e) {
                db.disableWarChannel();
            }
        }

        if (attacker.getPosition() > 1) {
            GuildDB db = attacker.getGuildDB();
            Map.Entry<AttackTypeSubCategory, String> violation = checkViolation(root, db);
            if (violation != null) {
//                Locutus.imp().getWarDb().addSubCategory();

                if (db != null) {
                    AttackTypeSubCategory type = violation.getKey();
                    String msg = "<" + root.toUrl() + ">" + violation.getValue();
                    AlertUtil.auditAlert(attacker, type, msg);
                }
            }
        }
    }

    public static void handleWarRooms(List<Map.Entry<DBWar, DBWar>> wars) {
        try {
            Map<Integer, Set<WarCategory>> warCatsByAA = new HashMap<>();
            Map<Integer, Set<WarCategory>> warCatsByRoom = new HashMap<>();

            for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
                if (db.isDelegateServer()) continue;
                WarCategory warCat = db.getWarChannel();
                if (warCat != null) {
                    for (int ally : warCat.getTrackedAllianceIds()) {
                        warCatsByAA.computeIfAbsent(ally, f -> new HashSet<>()).add(warCat);
                    }
                    for (WarCategory.WarRoom room : warCat.getWarRoomMap().values()) {
                        if (room.channel != null && room.target != null) {
                            warCatsByRoom.computeIfAbsent(room.target.getId(), f -> new HashSet<>()).add(warCat);
                        }
                    }
                }
            }



            Set<WarCategory> toUpdate = new LinkedHashSet<>();
            for (Map.Entry<DBWar, DBWar> pair : wars) {
                DBWar current = pair.getValue();

                if (!toUpdate.isEmpty()) {
                    toUpdate.clear();
                }
                toUpdate.addAll(warCatsByAA.getOrDefault(current.attacker_aa, Collections.emptySet()));
                toUpdate.addAll(warCatsByAA.getOrDefault(current.defender_aa, Collections.emptySet()));
                toUpdate.addAll(warCatsByRoom.getOrDefault(current.attacker_id, Collections.emptySet()));
                toUpdate.addAll(warCatsByRoom.getOrDefault(current.defender_id, Collections.emptySet()));
                if (!toUpdate.isEmpty()) {
                    if (wars.size() > 25 && RateLimitUtil.getCurrentUsed() > 55) {
                        while (RateLimitUtil.getCurrentUsed(true) > 55) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                break;
                            }
                        }
                    }
                    for (WarCategory warCat : toUpdate) {
                        warCat.update(pair.getKey(), pair.getValue());
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void handleAudits(List<Map.Entry<DBWar, DBWar>> wars) {

    }

    public static void handleAlerts(List<Map.Entry<DBWar, DBWar>> wars) {
        List<DBWar> newWars = wars.stream().filter(f -> f.getKey() == null).map(f -> f.getValue()).collect(Collectors.toList());

        Map<Integer, Set<GuildHandler>> defGuildsByAA = new HashMap<>();
        Map<Integer, Set<GuildHandler>> offGuildsByAA = new HashMap<>();

        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            if (!db.isValidAlliance() && !db.isWhitelisted() && !db.isOwnerActive() || db.isDelegateServer()) continue;

            MessageChannel defChan = db.getOrNull(GuildDB.Key.DEFENSE_WAR_CHANNEL, false);
            MessageChannel offChan = db.getOrNull(GuildDB.Key.OFFENSIVE_WAR_CHANNEL, false);

            GuildHandler handler = db.getHandler();

            if (defChan != null) {
                Set<Integer> tracked = handler.getTrackedWarAlliances(false);
                for (Integer aaId : tracked) {
                    defGuildsByAA.computeIfAbsent(aaId, f -> new LinkedHashSet<>()).add(handler);
                }
            }
            if (offChan != null) {
                Set<Integer> tracked = handler.getTrackedWarAlliances(true);
                for (Integer aaId : tracked) {
                    offGuildsByAA.computeIfAbsent(aaId, f -> new LinkedHashSet<>()).add(handler);
                }
            }
        }

        Map<GuildHandler, List<Map.Entry<DBWar, DBWar>>> defWarsByGuild = new HashMap<>();
        Map<GuildHandler, List<Map.Entry<DBWar, DBWar>>> offWarsByGuild = new HashMap<>();
        Map<GuildHandler, BiFunction<DBWar, DBWar, Boolean>> shouldAlertWar = new HashMap<>();

        int toCreate = 0;

        for (Map.Entry<DBWar, DBWar> entry : wars) {
            DBWar war = entry.getValue();
            if (war.attacker_aa == 0 && war.defender_aa == 0) continue;
            if (war.defender_aa != 0) {
                for (GuildHandler guildHandler : defGuildsByAA.getOrDefault(war.defender_aa, Collections.emptySet())) {
                    boolean shouldAlert = shouldAlertWar.computeIfAbsent(guildHandler, GuildHandler::shouldAlertWar).apply(entry.getKey(), entry.getValue());
                    if (shouldAlert) {
                        toCreate++;
                        defWarsByGuild.computeIfAbsent(guildHandler, f -> new ArrayList<>()).add(entry);
                    }
                }
            }
            if (war.attacker_aa != 0) {
                for (GuildHandler guildHandler : offGuildsByAA.getOrDefault(war.attacker_aa, Collections.emptySet())) {
                    boolean shouldAlert = shouldAlertWar.computeIfAbsent(guildHandler, GuildHandler::shouldAlertWar).apply(entry.getKey(), entry.getValue());
                    if (shouldAlert) {
                        toCreate++;
                        offWarsByGuild.computeIfAbsent(guildHandler, f -> new ArrayList<>()).add(entry);
                    }
                }
            }
        }

        int free = (RateLimitUtil.getLimitPerMinute() - RateLimitUtil.getCurrentUsed());
        boolean rateLimit = toCreate < (free + 10) * 3;

        for (Map.Entry<GuildHandler, List<Map.Entry<DBWar, DBWar>>> entry : defWarsByGuild.entrySet()) {
            GuildHandler handler = entry.getKey();
            boolean limit = rateLimit || (!handler.getDb().hasAlliance() && (toCreate > 50 || toCreate < free));
            handler.onDefensiveWarAlert(entry.getValue(), limit);
        }

        for (Map.Entry<GuildHandler, List<Map.Entry<DBWar, DBWar>>> entry : offWarsByGuild.entrySet()) {
            GuildHandler handler = entry.getKey();
            boolean limit = rateLimit || (!handler.getDb().hasAlliance() && (toCreate > 50 || toCreate < free));
            handler.onOffensiveWarAlert(entry.getValue(), limit);
        }
    }

    public static AttackTypeSubCategory incrementCategory(DBAttack root, Map<AttackTypeSubCategory, Integer> sum) {
        if (root.improvements_destroyed != 0) {
            sum.put(AttackTypeSubCategory.IMPROVEMENTS_DESTROYED, sum.getOrDefault(AttackTypeSubCategory.IMPROVEMENTS_DESTROYED, 0) + root.improvements_destroyed);
        }
        AttackTypeSubCategory category = subCategorize(root);
        if (category != null) {
            sum.put(category, sum.getOrDefault(category, 0) + 1);
        }
        return category;
    }

    public static AttackTypeSubCategory subCategorize(DBAttack root) {
        switch (root.attack_type) {
            case FORTIFY:
                return AttackTypeSubCategory.FORTIFY;
            case GROUND:
                int attTanks = (int) (root.att_gas_used * 100);
                int defTanks = (int) (root.def_gas_used * 100);
                int attSoldiers = (int) ((root.att_mun_used - root.att_gas_used) * 5000);
                int defSoldiers = (int) ((root.def_mun_used - root.def_gas_used) * 5000);

                int defTankStr = defTanks * 40;
                int attTankStr = attTanks * 40;
                if (attSoldiers == 0 && root.attcas1 != 0) {
                    attSoldiers = (int) ((22 * ((root.defcas1 / 0.3125) - ((attTankStr * 0.7 + 1)/7.33)) - 1) / 0.7);
                }
                if(defSoldiers == 0 && root.defcas1 != 0) {
                    defSoldiers = (int) ((22 * ((root.attcas1 / 0.3125) - ((defTankStr * 0.7 + 1)/7.33)) - 1) / 0.7);
                }
                if (root.att_mun_used == 0) {
                    return AttackTypeSubCategory.GROUND_NO_MUNITIONS_NO_TANKS;
                }

                double enemyGroundStrength = defSoldiers * 1.75 + defTanks * 40;

                if (attTanks > 0) {
                    if (attSoldiers * 1.75 > enemyGroundStrength) {
                        return AttackTypeSubCategory.GROUND_TANKS_MUNITIONS_USED_UNNECESSARY;
                    }
                    return AttackTypeSubCategory.GROUND_TANKS_MUNITIONS_USED_NECESSARY;
                } else {
                    if (attSoldiers > enemyGroundStrength) {
                        if (root.att_mun_used == 0) {
                            return AttackTypeSubCategory.GROUND_NO_MUNITIONS_NO_TANKS;
                        }
                        return AttackTypeSubCategory.GROUND_NO_TANKS_MUNITIONS_USED_UNNECESSARY;
                    }
                    if (root.att_mun_used == 0) {
                        return AttackTypeSubCategory.GROUND_NO_MUNITIONS_NO_TANKS;
                    }
                    return AttackTypeSubCategory.GROUND_NO_TANKS_MUNITIONS_USED_NECESSARY;
                }
            case VICTORY:
                return null;
            case A_LOOT:
                return null;
            case AIRSTRIKE1:
                return AttackTypeSubCategory.AIRSTRIKE_INFRA;
            case AIRSTRIKE4:
                return AttackTypeSubCategory.AIRSTRIKE_MONEY;
            case AIRSTRIKE2:
            case AIRSTRIKE3:
            case AIRSTRIKE5:
                int attAir = (int) (root.att_gas_used * 4);
                if (attAir <= 3) {
                    return AttackTypeSubCategory.AIRSTRIKE_3_PLANE;
                }
                if (root.defcas2 == 0) {
                    if (root.attack_type == AIRSTRIKE2) {
                        return AttackTypeSubCategory.AIRSTRIKE_SOLDIERS_NONE;
                    }
                    else if (root.attack_type == AIRSTRIKE3) {
                        return AttackTypeSubCategory.AIRSTRIKE_TANKS_NONE;
                    }
                    else if (root.attack_type == AIRSTRIKE5) {
                        return AttackTypeSubCategory.AIRSTRIKE_SHIP_NONE;
                    }
                }
                if (root.success != 3) {
                    return AttackTypeSubCategory.AIRSTRIKE_NOT_DOGFIGHT_UNSUCCESSFUL;
                }
                return AttackTypeSubCategory.AIRSTRIKE_UNIT;
            case AIRSTRIKE6:
                attAir = (int) (root.att_gas_used * 4);
                if (attAir <= 3) {
                    return AttackTypeSubCategory.AIRSTRIKE_3_PLANE;
                }
                if (root.defcas1 == 0) {
                    return AttackTypeSubCategory.AIRSTRIKE_AIRCRAFT_NONE;
                }
                return AttackTypeSubCategory.AIRSTRIKE_UNIT;
            case NAVAL:
                int attShips = (int) (root.att_gas_used / 2);
                if (root.defcas1 == 0) {
                    if (attShips == 1) {
                        return AttackTypeSubCategory.NAVY_1_SHIP;
                    }
                    return AttackTypeSubCategory.NAVAL_MAX_VS_NONE;
                }
                return AttackTypeSubCategory.NAVY_KILL_UNITS;
            case PEACE:
                return null;
            case MISSILE:
                return AttackTypeSubCategory.MISSILE;
            case NUKE:
                return AttackTypeSubCategory.NUKE;
        }
        return null;
    }

    public static Map.Entry<AttackTypeSubCategory, String> checkViolation(DBAttack root, GuildDB db) {
        Set<Integer> enemies = db != null ? db.getCoalition("enemies") : new HashSet<>();

        DBNation attacker = Locutus.imp().getNationDB().getNation(root.attacker_nation_id);
        DBNation defender = Locutus.imp().getNationDB().getNation(root.defender_nation_id);

        if (root.success == 0) {
            switch (root.attack_type) {
                case GROUND:
                case VICTORY:
                case FORTIFY:
                case A_LOOT:
                case MISSILE:
                case NUKE:
                    break;
                case AIRSTRIKE1:
                case AIRSTRIKE2:
                case AIRSTRIKE3:
                case AIRSTRIKE4:
                case AIRSTRIKE5:
                    //
                case AIRSTRIKE6:


                default:
            }
        }

        switch (root.attack_type) {
            case GROUND: {
                int attTanks = (int) (root.att_gas_used * 100);
                int defTanks = (int) (root.def_gas_used * 100);
                int attSoldiers = (int) ((root.att_mun_used - root.att_gas_used) * 5000);
                int defSoldiers = (int) ((root.def_mun_used - root.def_gas_used) * 5000);

                double enemyStrength = defender.getGroundStrength(true, false);
                double groundStrength = attacker.getGroundStrength(false, false);

                double maxAir80pct = Buildings.HANGAR.max() * Buildings.HANGAR.cap(attacker::hasProject) * attacker.getCities() * 0.66;
                if (defender.getAircraft() == 0 && root.defcas2 == 0 && root.defcas3 == 0 && root.attcas2 > 0 && root.money_looted == 0 && defender.getSoldiers() > attacker.getSoldiers() && attacker.getAircraft() > maxAir80pct) {
                    double cost = root.getLossesConverted(true);
                    if (defender.getActive_m() < 10000) {
                        String message = "You performed a ground attack using tanks against an enemy with a high amount of soldiers (but no tanks), no loot, and no aircraft. An airstrike may be cheaper at getting the initial soldiers down and avoiding tank losses";
                        return Map.entry(AttackTypeSubCategory.GROUND_TANKS_NO_LOOT_NO_ENEMY_AIR, message);
                    } else {
                        return AttackTypeSubCategory.GROUND_TANKS_NO_LOOT_NO_ENEMY_AIR_INACTIVE.toPair();
                    }
                }
                if (defender.getActive_m() > 10000) {
                    if (attSoldiers > 0 && groundStrength > enemyStrength * 2.5 && (defSoldiers == 0 || groundStrength * 0.4 > enemyStrength * 1.75)) {
                        return AttackTypeSubCategory.GROUND_NO_TANKS_MUNITIONS_USED_UNNECESSARY_INACTIVE.toPair();
                    }
                    if (attTanks > 0 && groundStrength > enemyStrength * 2.5 && root.money_looted == 0) {
                        String message = AttackTypeSubCategory.GROUND_TANKS_USED_UNNECESSARY_INACTIVE.message;
                        if (attacker.getAvg_infra() <= 1700) {
                            message += "Note: For raiding inactives with no ground loot, you should not use tanks, as it just wastes gasoline & munitions";
                        }
                        return Map.entry(AttackTypeSubCategory.GROUND_TANKS_USED_UNNECESSARY_INACTIVE, message);
                    }
                } else if (attTanks > 0 && defender.getAircraft() == 0 && attSoldiers > enemyStrength * 2.5 && root.defcas3 == 0) {
                    String message = AttackTypeSubCategory.GROUND_TANKS_USED_UNNECESSARY.message;
                    if (attSoldiers * 0.4 > enemyStrength * 0.75) message += " (unarmed)";

                    double usageCostPerTank = (PnwUtil.convertedTotal(ResourceType.MUNITIONS, 1) + PnwUtil.convertedTotal(ResourceType.GASOLINE, 1)) / 100d;
                    double cost = MilitaryUnit.TANK.getConvertedCost() * root.attcas2 + usageCostPerTank * attTanks;

                    double extraInfraDestroyed = ((attTanks - (defTanks * 0.5)) * 0.01) * 0.95 * (root.success / 3d);
                    DBWar war = Locutus.imp().getWarDb().getWar(root.war_id);
                    if (war != null) {
                        if (war.warType == WarType.RAID) {
                            extraInfraDestroyed *= 0.25;
                        } else if (war.warType == WarType.ORD) {
                            extraInfraDestroyed *= 0.5;
                        }
                    }

                    double extraInfraDestroyedValue = root.city_infra_before > 0 ? PnwUtil.calculateInfra(root.city_infra_before - extraInfraDestroyed, root.city_infra_before) : 0;
                    if (cost > extraInfraDestroyedValue) {
                        message += "\nBy using tanks unnecessarily, you used $" + MathMan.format(cost) + " worth of resources, and only destroyed $" + MathMan.format(extraInfraDestroyedValue) + " extra worth of infra";
                    } else {
                        message += "\nNote: Infra gets destroyed anyway when the enemy is beiged";
                    }

                    return Map.entry(AttackTypeSubCategory.GROUND_TANKS_USED_UNNECESSARY, message);
                } else if (enemyStrength == 0) {
                    if (attSoldiers > 0) {
                        return AttackTypeSubCategory.GROUND_NO_TANKS_MUNITIONS_USED_UNNECESSARY.toPair();
                    }
                }
                break;
            }
            case VICTORY:
                break;
            case FORTIFY:
                List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacksByWarId(root.war_id, root.epoch);
                attacks.removeIf(f -> f.war_attack_id >= root.war_attack_id || f.attacker_nation_id != root.attacker_nation_id);
                if (attacks.size() > 0 && attacks.get(attacks.size() - 1).attack_type == AttackType.FORTIFY) {
                    return AttackTypeSubCategory.DOUBLE_FORTIFY.toPair();
                }
                // already fortified
                break;
            case A_LOOT:
                break;
            case AIRSTRIKE2:
                if (root.attack_type == AIRSTRIKE2 && root.defcas2 == 0) {
                    String message = "You performed an airstrike against enemy soldiers when the enemy has none";
                    return Map.entry(AttackTypeSubCategory.AIRSTRIKE_SOLDIERS_NONE, message);
                }
                if (defender.getTanks() > 0 && defender.getSoldiers() < root.defcas2 && attacker.getSoldiers() * 0.4 > defender.getSoldiers() && root.defcas1 == 0 && attacker.getGroundStrength(true, false) > defender.getGroundStrength(true, true)) {
                    int attAir = (int) (root.att_gas_used * 4);
                    if (attAir > 3) {
                        attacks = Locutus.imp().getWarDb().getAttacksByWarId(root.war_id, root.epoch);
                        attacks.remove(root);
                        DBWar war = Locutus.imp().getWarDb().getWar(root.war_id);
                        if (war != null) {
                            WarCard card = new WarCard(war, attacks, false);
                            if (card.airSuperiority == attacker.getNation_id()) {
                                // You already have air superiority
                                // enemy has less ground strength than enemies

                                boolean enemyIsWeaker = true;
                                List<DBWar> activeWars = defender.getActiveWars();
                                for (DBWar otherWars : activeWars) {
                                    DBNation other = war.getNation(!war.isAttacker(defender));
                                    if (other == null || other.getActive_m() > 2440) continue;
                                    if (other.getGroundStrength(true, false) < defender.getGroundStrength(true, true) || other.getCities() > other.getCities() * 1.8) {
                                        enemyIsWeaker = false;
                                        break;
                                    }
                                }

                                if (enemyIsWeaker) {
                                    return AttackTypeSubCategory.AIRSTRIKE_SOLDIERS_SHOULD_USE_GROUND.toPair();
                                }
                            }
                        }
                    }
                }
                // if you have more soldiers than the enemy does
            case AIRSTRIKE3:
                if (root.attack_type == AIRSTRIKE3 && root.defcas2 == 0 && defender.getTanks() == 0) {
                    return AttackTypeSubCategory.AIRSTRIKE_TANKS_NONE.toPair();
                }
            case AIRSTRIKE1:
            case AIRSTRIKE4:
            case AIRSTRIKE5:
                if (root.attack_type == AIRSTRIKE5 && root.defcas2 == 0 && defender.getShips() == 0) {
                    return AttackTypeSubCategory.AIRSTRIKE_SHIP_NONE.toPair();
                }
            case AIRSTRIKE6: {
                int attAir = (int) (root.att_gas_used * 4);
                int defAir = defender.getAircraft() + root.defcas1;

                if (defender.getActive_m() > 10000) {
                    double defGStr = defender.getGroundStrength(true, false) + 500 * defender.getCities();
                    double attGStr = attacker.getSoldiers();
                    if (attGStr > defGStr * 1.75) {
                        return AttackTypeSubCategory.AIRSTRIKE_INACTIVE_NO_GROUND.toPair();
                    }

                    if (defender.getShips() == 0) {
                        if (attacker.getShips() > 0 || attAir > 3) {
                            return AttackTypeSubCategory.AIRTRIKE_INACTIVE_NO_SHIP.toPair();
                        }
                    }
                }

                if (((defender.getAircraft() > attacker.getAircraft() * 0.6 && root.defcas1 < root.attcas1) || root.success < 3) && root.attack_type != AttackType.AIRSTRIKE6) {
                    return AttackTypeSubCategory.AIRSTRIKE_FAILED_NOT_DOGFIGHT.toPair();
                }

                if (root.attcas1 < root.defcas1 / 4 && root.attack_type == AttackType.AIRSTRIKE6 && attAir > 3) {
                    if (defender.getAircraft() == 0 && root.defcas1 == 0) {
                        String message = AttackTypeSubCategory.AIRSTRIKE_AIRCRAFT_NONE.message;
                        if (defender.getActive_m() > 10000) {
                            return AttackTypeSubCategory.AIRSTRIKE_AIRCRAFT_NONE_INACTIVE.toPair();
                        } else {
                            if (defender.getShips() > 0 || defender.getTanks() > 0 || defender.getSoldiers() > 0) {
                                message += "The enemy has other units that would be more useful to target.";
                            } else if (enemies.contains(defender.getAlliance_id()) && defender.getActive_m() < 1440) {
//                                        message += "You can get an immense triumph with just 3 aircraft";
                            } else {
                                message += "You can get an immense triumph with just 3 aircraft";
                            }
                        }
                        return Map.entry(AttackTypeSubCategory.AIRSTRIKE_AIRCRAFT_NONE, message);
                    } else {
                        if (defender.getShips() > 0 || defender.getSoldiers() > 0 || defender.getTanks() > 0) {
                            if (defender.getActive_m() > 10000) {
                                // its okay to airstrike an inactive
//                                String message = "You performed a dogfight using " + attAir + " against an inactive enemy with only " + defender.getAircraft() + " aircraft, but could have targeted other units";
//                                return message;
                            } else if (defender.getAircraft() <= 10) {
                                String message = AttackTypeSubCategory.AIRSTRIKE_AIRCRAFT_LOW.message
                                        .replace("{amt_att}", attAir + "")
                                        .replace("{amt_def}", defender.getAircraft() + "");
                                return Map.entry(AttackTypeSubCategory.AIRSTRIKE_AIRCRAFT_LOW, message);
                            }
                        }
                    }
                    break;
                }

                if (root.attack_type == AttackType.AIRSTRIKE1 && attAir > 3) {

                    String message = AttackTypeSubCategory.AIRSTRIKE_INFRA.message.replace("{amount}", attAir + "");

                    double usageCost = root.getLossesConverted(true, false, false, true, false);
                    if (usageCost > root.infra_destroyed_value) {
                        message += "\nYou used $" + MathMan.format(usageCost) + " worth of resources, and only destroyed $" + MathMan.format(root.infra_destroyed_value) + " extra worth of infra";
                    } else {
                        message += "\nNote: Infra gets destroyed anyway when the enemy is defeated";
                    }

                    return Map.entry(AttackTypeSubCategory.AIRSTRIKE_INFRA, message);
                }

                if (root.attack_type == AttackType.AIRSTRIKE4 && attAir > 3) {
                    String message = AttackTypeSubCategory.AIRSTRIKE_MONEY.message.replace("{amount}", attAir + "") + "\n";
                    if (defender.getActive_m() > 2880) {
                        message += " They are inactive, so you are destroying your own loot";
                    } else {
                        message += " Focus on killing units, and then defeating them efficiently.";
                    }
                    return Map.entry(AttackTypeSubCategory.AIRSTRIKE_MONEY, message);
                }
                break;
            }
            case NAVAL:
                int defShips = (int) (root.def_gas_used / 2);
                if (defender.getShips() == 0 && defShips == 0 && root.defcas1 == 0) {
                    int attShips = (int) (root.att_gas_used / 2);
                    if (attShips > 1 && defender.getAvg_infra() < 1850 && root.city_infra_before <= 1850) {
                        String message = AttackTypeSubCategory.NAVAL_MAX_VS_NONE.message;

                        double usageCost = root.getLossesConverted(true, false, false, true, false);
                        if (usageCost > root.infra_destroyed_value) {
                            message += "\nYou used $" + MathMan.format(usageCost) + " worth of resources, and only destroyed $" + MathMan.format(root.infra_destroyed_value) + " extra worth of infra";
                        } else {
                            message += "\nNote: Infra gets destroyed anyway when the enemy is beiged";
                        }

                        return Map.entry(AttackTypeSubCategory.NAVAL_MAX_VS_NONE, message);
                    }
                }
                if (defender.getBlockadedBy().contains(attacker.getNation_id()) && !defender.getBlockadedBy().contains(root.defender_nation_id)) {
                    if (defender.getActive_m() < 1440 && root.defcas1 == 0 &&
                            ((defender.getAircraft() > 0 && defender.getAircraft() < attacker.getAircraft() * 0.8) ||
                            (defender.getAircraft() < attacker.getAircraft() && defender.getGroundStrength(true, false) > 0 && defender.getGroundStrength(true, false) < attacker.getGroundStrength(true, true)))) {

                        attacks = Locutus.imp().getWarDb().getAttacksByWarId(root.war_id, root.epoch);
                        for (DBAttack attack : attacks) {
                            if (attack.attack_type == NAVAL && attack.war_attack_id != root.war_attack_id) {
                                return AttackTypeSubCategory.NAVAL_ALREADY_BLOCKADED.toPair();
                            }
                        }
                    }
                }
                break;
            case PEACE:
                break;
            case MISSILE:
                // if you have more of a specific military unit than them
                break;
            case NUKE:
                // if you have more of a specific military unit than them
                break;
        }
        return null;
    }

    public static void processLegacy(DBWar previous, DBWar current) throws IOException {
        try {
            DBNation attacker = Locutus.imp().getNationDB().getNation(current.attacker_id);
            DBNation defender = Locutus.imp().getNationDB().getNation(current.defender_id);

            if (defender != null && defender.getAlliance_id() == 0 && defender.getActive_m() > 10000) return;

//            if (attacker.getActive_m() < 2880 && defender.getActive_m() < 2880) {
//                for (int allianceId : new int[]{current.attacker_aa, current.defender_aa}) {
//                    if (allianceId == 0) continue;
//                    GuildDB db = Locutus.imp().getGuildDBByAA(allianceId);
//                    if (db == null) continue;
//
//                    WarCategory warChannel = db.getWarChannel();
//                    if (warChannel == null) continue;
//                    try {
//                        warChannel.update(previous, current);
//                    } catch (Throwable e) {
//                        db.disableWarChannel();
//                    }
//                }
//            }

//            if (attacker != null && attacker.getPosition() > 1) {
//                GuildDB guildDb = Locutus.imp().getGuildDBByAA(attacker.getAlliance_id());
//                if (guildDb != null) {
//                    guildDb.getHandler().onWar(attacker, current, previous == null);
//                }
//            }
//
//            if (defender != null && defender.getPosition() > 1) {
//                GuildDB guildDb = Locutus.imp().getGuildDBByAA(defender.getAlliance_id());
//                if (guildDb != null) {
//                    guildDb.getHandler().onWar(defender, current, previous == null);
//                }
//            }

            if (previous != null) {
                // gains GC
                // gain AC
                // blockaded
                return;
            }

            if (previous == null && attacker != null && attacker.getAlliance_id() != 0) {
                PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(attacker.getNation_id());
                if (user != null) {
                    GuildDB guildDb = Locutus.imp().getGuildDBByAA(attacker.getAlliance_id());
                    if (guildDb != null && guildDb.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) {
                        Guild guild = guildDb.getGuild();
                        if (guild != null) {
                            Member member = guild.getMemberById(user.getDiscordId());
                            if (member != null) {
                                checkWarPolicy(current, attacker, defender, user, guildDb, guild, member);
                                checkWarType(current, attacker, defender, user, guildDb, guild, member);
                            }
                        }
                    }
                }
            }

            if (current.defender_id == Settings.INSTANCE.NATION_ID) {
                String body = "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + Settings.INSTANCE.ALLIANCE_ID() + "&display=bank";
                AlertUtil.displayTray("" + Settings.INSTANCE.PNW_URL() + "/nation/war/timeline/war=" + current.warId, body);
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    try {
                        Desktop.getDesktop().browse(new URI("" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + Settings.INSTANCE.ALLIANCE_ID() + "&display=bank"));
                    } catch (IOException | URISyntaxException e) {
                        e.printStackTrace();
                    }
                }
            }

            // don't care about nones
            if (defender == null || defender.getAlliance_id() == 0) return;

            WarCard card = new WarCard(current.warId);
            CounterStat stat = card.getCounterStat();

            DBAlliance defAA = defender.getAlliance();
            ByteBuffer lastWarringBuf = defAA.getMeta(AllianceMeta.LAST_AT_WAR_TURN);

            if (lastWarringBuf == null || TimeUtil.getTurn() - lastWarringBuf.getLong() > 24) {
                if (stat != null && stat.type == CounterType.ESCALATION) {
                    AlertUtil.forEachChannel(f -> true, GuildDB.Key.ESCALATION_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                        @Override
                        public void accept(MessageChannel channel, GuildDB guildDB) {
                            card.embed(new DiscordChannelIO(channel), false, true);
                        }
                    });
                } else if (defender.getOff() > 0 && stat.type != CounterType.UNCONTESTED) {
                    List<DBWar> wars = defender.getActiveWars();
                    Set<DBWar> escalatedWars = null;
                    for (DBWar war : wars) {
                        if (war.attacker_id != defender.getNation_id()) continue;

                        DBNation warDef = DBNation.byId(war.defender_id);
                        if (warDef == null || warDef.getPosition() < 1) continue;
                        CounterStat stats = war.getCounterStat();
                        if (stats != null && stats.type == CounterType.IS_COUNTER) {
                            if (escalatedWars == null) escalatedWars = new HashSet<>();
                            escalatedWars.add(war);
                        }
                    }
                    if (escalatedWars != null) {
                        AlertUtil.forEachChannel(f -> true, GuildDB.Key.ESCALATION_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                            @Override
                            public void accept(MessageChannel channel, GuildDB guildDB) {
                                card.embed(new DiscordChannelIO(channel), false, true);
                            }
                        });
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void checkWarPolicy(DBWar current, DBNation attacker, DBNation defender, PNWUser user, GuildDB guildDb, Guild guild, Member member) {
        if (defender.getActive_m() < 10000) return;
        if (attacker.getWarPolicy() == WarPolicy.PIRATE) return;

        String message = AutoAuditType.WAR_POLICY.message.replace("{war}", current.toUrl());
        AlertUtil.auditAlert(attacker, AutoAuditType.WAR_POLICY, f -> message);
    }

    private static void checkWarType(DBWar current, DBNation attacker, DBNation defender, PNWUser user, GuildDB guildDb, Guild guild, Member member) {
        if (current.warType == WarType.RAID) return;
        if (defender.getAvg_infra() > 1700 && defender.getActive_m() < 10000) return;

        AlertUtil.auditAlert(attacker, AutoAuditType.WAR_TYPE_NOT_RAID, f -> {
            Set<DBBounty> bounties = Locutus.imp().getWarDb().getBounties(defender.getNation_id());
            for (DBBounty bounty : bounties) {
                if (bounty.getType() == current.warType) return null;
            }

            String message = AutoAuditType.WAR_TYPE_NOT_RAID.message.replace("{war}",  current.toUrl())
                    .replace("{type}", current.warType + "");
            if (defender.getActive_m() > 10000) message += " as the enemy is inactive";
            else if (defender.getAvg_infra() <= 1000) message += " as the enemy already has reduced infra";
            else if (defender.getAvg_infra() <= attacker.getAvg_infra()) message += " as the enemy has lower infra than you";
            else if (!defender.hasUnsetMil() && defender.getNukes() > 0) message += " as the enemy has nukes";
            else if (!defender.hasUnsetMil() && defender.getMissiles() > 0) message += " as the enemy has missiles";
            else return null;

            return message;
        });
    }

    public static void checkActiveConflicts() {
        int topX = 80;
        Set<DBAlliance> top = Locutus.imp().getNationDB().getAlliances(true, true, true, topX);

        Map<DBAlliance, Double> warRatio = new HashMap<>();
        for (DBAlliance alliance : top) {
            Set<DBNation> nations = alliance.getNations(true, 1440, true);
            nations.removeIf(DBNation::isGray);
            int numBeige = 0;
            int numDefending = 0;
            int numDefendingUnprovoked = 0;
            int numC10Plus = 0;
            for (DBNation nation : nations) {
                if (nation.isBeige()) numBeige++;
                if (nation.getDef() > 0) numDefending++;
                if (nation.getDef() > 0 && nation.getOff() == 0) numDefendingUnprovoked++;
                if (nation.getCities() >= 10) numC10Plus++;
            }
            if (numDefending == 0) continue;
            if (numDefendingUnprovoked > 4) {
                double ratio = (numDefendingUnprovoked * 5d + numDefending) / Math.max(1, (nations.size() - numBeige));
                warRatio.put(alliance, ratio);
//                warRatio.add(new AbstractMap.SimpleEntry<>(alliance, ratio));
            }
        }

        Map<DBAlliance, Boolean> isAtWar = new HashMap<>();
        Map<DBAlliance, String> warInfo = new HashMap<>();

        for (Map.Entry<DBAlliance, Double> entry : warRatio.entrySet()) {
            DBAlliance aa = entry.getKey();
            Set<DBNation> nations = aa.getNations(f -> f.isGray() || f.getPositionEnum().id <= Rank.APPLICANT.id || f.active_m() > 1440 || f.getVm_turns() > 0);

            List<DBWar> active = aa.getActiveWars();
            List<DBWar> notableWars = new ArrayList<>();
            Map<Integer, Integer> notableByAA = new HashMap<>();
            int max = 0;

            for (DBWar war : active) {
                int otherAA = war.attacker_aa;
                if (otherAA == 0) continue;
                if (war.defender_aa != aa.getAlliance_id()) continue;

                DBNation defender = war.getNation(false);
                if (!nations.contains(defender)) continue;
                if (defender.getOff() > 0) continue;

                CounterStat stat = war.getCounterStat();
                if (stat == null || (stat.type != CounterType.ESCALATION && stat.type != CounterType.UNCONTESTED && stat.type != CounterType.GETS_COUNTERED)) continue;

                notableWars.add(war);
                int amt = notableByAA.getOrDefault(otherAA, 0) + 1;
                notableByAA.put(otherAA, amt);
                max = Math.max(max, amt);
            }
            if (max >= 6) {
                StringBuilder body = new StringBuilder();
                body.append("#" + aa.getRank() + " | " + aa.getMarkdownUrl() + "\n");
                for (Map.Entry<Integer, Integer> warEntry : notableByAA.entrySet()) {
                    body.append(" - " + warEntry.getValue() + " unprovoked wars from " + PnwUtil.getMarkdownUrl(warEntry.getKey(), true) + "\n");
                }
                warInfo.put(aa, body.toString());
                isAtWar.put(aa, true);
            }
        }

        long currentTurn = TimeUtil.getTurn();
        long warTurnThresheld = 7 * 12;

        for (DBAlliance alliance : top) {
            ByteBuffer previousPctBuf = alliance.getMeta(AllianceMeta.LAST_BLITZ_PCT);
            ByteBuffer warringBuf = alliance.getMeta(AllianceMeta.IS_WARRING);
            ByteBuffer lastWarBuf = alliance.getMeta(AllianceMeta.LAST_AT_WAR_TURN);

            long lastWarTurn = lastWarBuf == null ? 0 : lastWarBuf.getLong();
            double lastWarRatio = previousPctBuf == null ? 0 : previousPctBuf.getDouble();
            boolean lastWarring = warringBuf == null ? false : warringBuf.get() == 1;

            double currentRatio = warRatio.getOrDefault(alliance, 0d);
            boolean warring = isAtWar.getOrDefault(alliance, false);
            if (lastWarring && lastWarRatio > 0.2) warring = true;

            if (currentRatio != lastWarRatio || warring != lastWarring) {
                alliance.setMeta(AllianceMeta.LAST_BLITZ_PCT, currentRatio);
            }
            if (warring != lastWarring) {
                alliance.setMeta(AllianceMeta.IS_WARRING, (byte) (warring ? 1 : 0));
            }
            if (lastWarTurn != currentTurn) alliance.setMeta(AllianceMeta.LAST_AT_WAR_TURN, currentTurn);

            String body = warInfo.get(alliance);

            if (body != null && !lastWarring && warring && currentTurn - lastWarTurn > warTurnThresheld) {
                String title = alliance.getName() + " is being Attacked";
                AlertUtil.forEachChannel(f -> true, GuildDB.Key.ESCALATION_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                    @Override
                    public void accept(MessageChannel channel, GuildDB guildDB) {
                        DiscordUtil.createEmbedCommand(channel, title, body);
                    }
                });
            }
        }
    }
}