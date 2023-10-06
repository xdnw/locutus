package link.locutus.discord.util.update;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.commands.external.guild.SyncBounties;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.GuildHandler;
import link.locutus.discord.db.guild.GuildKey;
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
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
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
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
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
        AlertUtil.forEachChannel(SyncBounties.class, GuildKey.BOUNTY_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
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
                        DBNation nation = DiscordUtil.getNation(member.getUser());
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

        long start = System.currentTimeMillis();
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
        try {
            WarUpdateProcessor.checkActiveConflicts();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        long diff = System.currentTimeMillis() - start;
        if (diff > 500) {
            System.out.println("Took " + diff + "ms to process " + wars.size() + " wars");
            AlertUtil.error("Took " + diff + "ms to process " + wars.size() + " wars", StringMan.stacktraceToString(new Exception().getStackTrace()));
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
        AbstractCursor root = event.getAttack();
        int nationId = root.getAttacker_id();
        DBNation attacker = Locutus.imp().getNationDB().getNation(root.getAttacker_id());
        DBNation defender = Locutus.imp().getNationDB().getNation(root.getDefender_id());

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

        if (root.getAttack_type() == AttackType.VICTORY) {
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
            try {
                Map.Entry<AttackTypeSubCategory, String> violation = checkViolation(root, db);
                if (violation != null) {
//                Locutus.imp().getWarDb().addSubCategory();
                    if (db != null) {
                        AttackTypeSubCategory type = violation.getKey();
                        String msg = "<" + root.toUrl() + ">" + violation.getValue();
                        AlertUtil.auditAlert(attacker, type, msg);
                    }
                }
            } catch (Throwable ignore) {
                ignore.printStackTrace();
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
            MessageChannel defChan = db.getOrNull(GuildKey.DEFENSE_WAR_CHANNEL, false);
            MessageChannel offChan = db.getOrNull(GuildKey.OFFENSIVE_WAR_CHANNEL, false);

            if (defChan == null && offChan == null) continue;

            if (!db.isValidAlliance()) {
                if (!db.isWhitelisted() || !db.isOwnerActive()) continue;
            }

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

    public static AttackTypeSubCategory incrementCategory(AbstractCursor root, Map<AttackTypeSubCategory, Integer> sum) {
        if (root.getImprovements_destroyed() != 0) {
            sum.put(AttackTypeSubCategory.IMPROVEMENTS_DESTROYED, sum.getOrDefault(AttackTypeSubCategory.IMPROVEMENTS_DESTROYED, 0) + root.getImprovements_destroyed());
        }
        AttackTypeSubCategory category = subCategorize(root);
        if (category != null) {
            sum.put(category, sum.getOrDefault(category, 0) + 1);
        }
        return category;
    }

    public static AttackTypeSubCategory subCategorize(AbstractCursor root) {
        switch (root.getAttack_type()) {
            case FORTIFY:
                return AttackTypeSubCategory.FORTIFY;
            case GROUND:
                int attTanks = (int) (root.getAtt_gas_used() * 100);
                int defTanks = (int) (root.getDef_gas_used() * 100);
                int attSoldiers = (int) ((root.getAtt_mun_used() - root.getAtt_gas_used()) * 5000);
                int defSoldiers = (int) ((root.getDef_mun_used() - root.getDef_gas_used()) * 5000);

                int defTankStr = defTanks * 40;
                int attTankStr = attTanks * 40;
                if (attSoldiers == 0 && root.getAttcas1() != 0) {
                    attSoldiers = (int) ((22 * ((root.getDefcas1() / 0.3125) - ((attTankStr * 0.7 + 1)/7.33)) - 1) / 0.7);
                }
                if(defSoldiers == 0 && root.getDefcas1() != 0) {
                    defSoldiers = (int) ((22 * ((root.getAttcas1() / 0.3125) - ((defTankStr * 0.7 + 1)/7.33)) - 1) / 0.7);
                }
                if (root.getAtt_mun_used() == 0) {
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
                        if (root.getAtt_mun_used() == 0) {
                            return AttackTypeSubCategory.GROUND_NO_MUNITIONS_NO_TANKS;
                        }
                        return AttackTypeSubCategory.GROUND_NO_TANKS_MUNITIONS_USED_UNNECESSARY;
                    }
                    if (root.getAtt_mun_used() == 0) {
                        return AttackTypeSubCategory.GROUND_NO_MUNITIONS_NO_TANKS;
                    }
                    return AttackTypeSubCategory.GROUND_NO_TANKS_MUNITIONS_USED_NECESSARY;
                }
            case VICTORY:
                return null;
            case A_LOOT:
                return null;
            case AIRSTRIKE_INFRA:
                return AttackTypeSubCategory.AIRSTRIKE_INFRA;
            case AIRSTRIKE_MONEY:
                return AttackTypeSubCategory.AIRSTRIKE_MONEY;
            case AIRSTRIKE_SOLDIER:
            case AIRSTRIKE_TANK:
            case AIRSTRIKE_SHIP:
                int attAir = (int) (root.getAtt_gas_used() * 4);
                if (attAir <= 3) {
                    return AttackTypeSubCategory.AIRSTRIKE_3_PLANE;
                }
                if (root.getDefcas2() == 0) {
                    if (root.getAttack_type() == AIRSTRIKE_SOLDIER) {
                        return AttackTypeSubCategory.AIRSTRIKE_SOLDIERS_NONE;
                    }
                    else if (root.getAttack_type() == AIRSTRIKE_TANK) {
                        return AttackTypeSubCategory.AIRSTRIKE_TANKS_NONE;
                    }
                    else if (root.getAttack_type() == AIRSTRIKE_SHIP) {
                        return AttackTypeSubCategory.AIRSTRIKE_SHIP_NONE;
                    }
                }
                if (root.getSuccess() != SuccessType.IMMENSE_TRIUMPH) {
                    return AttackTypeSubCategory.AIRSTRIKE_NOT_DOGFIGHT_UNSUCCESSFUL;
                }
                return AttackTypeSubCategory.AIRSTRIKE_UNIT;
            case AIRSTRIKE_AIRCRAFT:
                attAir = (int) (root.getAtt_gas_used() * 4);
                if (attAir <= 3) {
                    return AttackTypeSubCategory.AIRSTRIKE_3_PLANE;
                }
                if (root.getDefcas1() == 0) {
                    return AttackTypeSubCategory.AIRSTRIKE_AIRCRAFT_NONE;
                }
                return AttackTypeSubCategory.AIRSTRIKE_UNIT;
            case NAVAL:
                int attShips = (int) (root.getAtt_gas_used() / 2);
                if (root.getDefcas1() == 0) {
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

    public static Map.Entry<AttackTypeSubCategory, String> checkViolation(AbstractCursor root, GuildDB db) {
        if (root.getWar() == null) return null;

        Set<Integer> enemies = db != null ? db.getCoalition("enemies") : new HashSet<>();

        DBNation attacker = Locutus.imp().getNationDB().getNation(root.getAttacker_id());
        DBNation defender = Locutus.imp().getNationDB().getNation(root.getDefender_id());

        if (root.getSuccess() == SuccessType.UTTER_FAILURE) {
            switch (root.getAttack_type()) {
                case GROUND:
                case VICTORY:
                case FORTIFY:
                case A_LOOT:
                case MISSILE:
                case NUKE:
                    break;
                case AIRSTRIKE_INFRA:
                case AIRSTRIKE_SOLDIER:
                case AIRSTRIKE_TANK:
                case AIRSTRIKE_MONEY:
                case AIRSTRIKE_SHIP:
                    //
                case AIRSTRIKE_AIRCRAFT:


                default:
            }
        }

        switch (root.getAttack_type()) {
            case GROUND: {
                int attTanks = (int) (root.getAtt_gas_used() * 100);
                int defTanks = (int) (root.getDef_gas_used() * 100);
                int attSoldiers = (int) ((root.getAtt_mun_used() - root.getAtt_gas_used()) * 5000);
                int defSoldiers = (int) ((root.getDef_mun_used() - root.getDef_gas_used()) * 5000);

                double enemyStrength = defender.getGroundStrength(true, false);
                double groundStrength = attacker.getGroundStrength(false, false);

                double maxAir80pct = Buildings.HANGAR.max() * Buildings.HANGAR.cap(attacker::hasProject) * attacker.getCities() * 0.66;
                if (defender.getAircraft() == 0 && root.getDefcas2() == 0 && root.getDefcas3() == 0 && root.getAttcas2() > 0 && root.getMoney_looted() == 0 && defender.getSoldiers() > attacker.getSoldiers() && attacker.getAircraft() > maxAir80pct) {
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
                    if (attTanks > 0 && groundStrength > enemyStrength * 2.5 && root.getMoney_looted() == 0) {
                        String message = AttackTypeSubCategory.GROUND_TANKS_USED_UNNECESSARY_INACTIVE.message;
                        if (attacker.getAvg_infra() <= 1700) {
                            message += "Note: For raiding inactives with no ground loot, you should not use tanks, as it just wastes gasoline & munitions";
                        }
                        return Map.entry(AttackTypeSubCategory.GROUND_TANKS_USED_UNNECESSARY_INACTIVE, message);
                    }
                } else if (attTanks > 0 && defender.getAircraft() == 0 && attSoldiers > enemyStrength * 2.5 && root.getDefcas3() == 0) {
                    String message = AttackTypeSubCategory.GROUND_TANKS_USED_UNNECESSARY.message;
                    if (attSoldiers * 0.4 > enemyStrength * 0.75) message += " (unarmed)";

                    double usageCostPerTank = (PnwUtil.convertedTotal(ResourceType.MUNITIONS, 1) + PnwUtil.convertedTotal(ResourceType.GASOLINE, 1)) / 100d;
                    double cost = MilitaryUnit.TANK.getConvertedCost() * root.getAttcas2() + usageCostPerTank * attTanks;

                    double extraInfraDestroyed = ((attTanks - (defTanks * 0.5)) * 0.01) * 0.95 * (root.getSuccess().ordinal() / 3d);
                    DBWar war = Locutus.imp().getWarDb().getWar(root.getWar_id());
                    if (war != null) {
                        if (war.warType == WarType.RAID) {
                            extraInfraDestroyed *= 0.25;
                        } else if (war.warType == WarType.ORD) {
                            extraInfraDestroyed *= 0.5;
                        }
                    }

                    double extraInfraDestroyedValue = root.getCity_infra_before() > 0 ? PnwUtil.calculateInfra(root.getCity_infra_before() - extraInfraDestroyed, root.getCity_infra_before()) : 0;
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
                List<AbstractCursor> attacks = root.getWar().getAttacks2(false);
                attacks.removeIf(f -> f.getWar_attack_id() >= root.getWar_attack_id() || f.getAttacker_id() != root.getAttacker_id());
                if (attacks.size() > 0 && attacks.get(attacks.size() - 1).getAttack_type() == AttackType.FORTIFY) {
                    return AttackTypeSubCategory.DOUBLE_FORTIFY.toPair();
                }
                // already fortified
                break;
            case A_LOOT:
                break;
            case AIRSTRIKE_SOLDIER:
                if (root.getAttack_type() == AIRSTRIKE_SOLDIER && root.getDefcas2() == 0) {
                    String message = "You performed an airstrike against enemy soldiers when the enemy has none";
                    return Map.entry(AttackTypeSubCategory.AIRSTRIKE_SOLDIERS_NONE, message);
                }
                if (defender.getTanks() > 0 && defender.getSoldiers() < root.getDefcas2() && attacker.getSoldiers() * 0.4 > defender.getSoldiers() && root.getDefcas1() == 0 && attacker.getGroundStrength(true, false) > defender.getGroundStrength(true, true)) {
                    int attAir = (int) (root.getAtt_gas_used() * 4);
                    if (attAir > 3) {
                        attacks = root.getWar().getAttacks2(false);
                        attacks.remove(root);
                        DBWar war = Locutus.imp().getWarDb().getWar(root.getWar_id());
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
            case AIRSTRIKE_TANK:
                if (root.getAttack_type() == AIRSTRIKE_TANK && root.getDefcas2() == 0 && defender.getTanks() == 0) {
                    return AttackTypeSubCategory.AIRSTRIKE_TANKS_NONE.toPair();
                }
            case AIRSTRIKE_INFRA:
            case AIRSTRIKE_MONEY:
            case AIRSTRIKE_SHIP:
                if (root.getAttack_type() == AIRSTRIKE_SHIP && root.getDefcas2() == 0 && defender.getShips() == 0) {
                    return AttackTypeSubCategory.AIRSTRIKE_SHIP_NONE.toPair();
                }
            case AIRSTRIKE_AIRCRAFT: {
                int attAir = (int) (root.getAtt_gas_used() * 4);
                int defAir = defender.getAircraft() + root.getDefcas1();

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

                if (((defender.getAircraft() > attacker.getAircraft() * 0.6 && root.getDefcas1() < root.getAttcas1()) || root.getSuccess().ordinal() < 3) && root.getAttack_type() != AttackType.AIRSTRIKE_AIRCRAFT) {
                    return AttackTypeSubCategory.AIRSTRIKE_FAILED_NOT_DOGFIGHT.toPair();
                }

                if (root.getAttcas1() < root.getDefcas1() / 4 && root.getAttack_type() == AttackType.AIRSTRIKE_AIRCRAFT && attAir > 3) {
                    if (defender.getAircraft() == 0 && root.getDefcas1() == 0) {
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
                                        .replace("{amt_def}", (defender.getAircraft() + root.getDefcas1()) + "");
                                return Map.entry(AttackTypeSubCategory.AIRSTRIKE_AIRCRAFT_LOW, message);
                            }
                        }
                    }
                    break;
                }

                if (root.getAttack_type() == AttackType.AIRSTRIKE_INFRA && attAir > 3) {

                    String message = AttackTypeSubCategory.AIRSTRIKE_INFRA.message.replace("{amount}", attAir + "");

                    double usageCost = root.getLossesConverted(ResourceType.getBuffer(), true, false, false, true, false, false);
                    if (usageCost > root.getInfra_destroyed_value()) {
                        message += "\nYou used $" + MathMan.format(usageCost) + " worth of resources, and only destroyed $" + MathMan.format(root.getInfra_destroyed_value()) + " extra worth of infra";
                    } else {
                        message += "\nNote: Infra gets destroyed anyway when the enemy is defeated";
                    }

                    return Map.entry(AttackTypeSubCategory.AIRSTRIKE_INFRA, message);
                }

                if (root.getAttack_type() == AttackType.AIRSTRIKE_MONEY && attAir > 3) {
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
                int defShips = (int) (root.getDef_gas_used() / 2);
                if (defender.getShips() == 0 && defShips == 0 && root.getDefcas1() == 0) {
                    int attShips = (int) (root.getAtt_gas_used() / 2);
                    if (attShips > 1 && defender.getAvg_infra() < 1850 && root.getCity_infra_before() <= 1850) {
                        String message = AttackTypeSubCategory.NAVAL_MAX_VS_NONE.message;

                        double usageCost = root.getLossesConverted(ResourceType.getBuffer(), true, false, false, true, false, false);
                        if (usageCost > root.getInfra_destroyed_value()) {
                            message += "\nYou used $" + MathMan.format(usageCost) + " worth of resources, and only destroyed $" + MathMan.format(root.getInfra_destroyed_value()) + " extra worth of infra";
                        } else {
                            message += "\nNote: Infra gets destroyed anyway when the enemy is beiged";
                        }

                        return Map.entry(AttackTypeSubCategory.NAVAL_MAX_VS_NONE, message);
                    }
                }
                if (defender.getBlockadedBy().contains(attacker.getNation_id()) && !defender.getBlockadedBy().contains(root.getDefender_id())) {
                    if (defender.getActive_m() < 1440 && root.getDefcas1() == 0 &&
                            ((defender.getAircraft() > 0 && defender.getAircraft() < attacker.getAircraft() * 0.8) ||
                            (defender.getAircraft() < attacker.getAircraft() && defender.getGroundStrength(true, false) > 0 && defender.getGroundStrength(true, false) < attacker.getGroundStrength(true, true)))) {

                        attacks = root.getWar().getAttacks2(false);
                        for (AbstractCursor attack : attacks) {
                            if (attack.getAttack_type() == NAVAL && attack.getWar_attack_id() != root.getWar_attack_id()) {
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
            DBAlliance attAA = attacker.getAlliance();
            if (defAA == null || attAA == null) return;

            ByteBuffer defWarringBuf = defAA.getMeta(AllianceMeta.IS_WARRING);
            if (defWarringBuf != null && defWarringBuf.get() > 0) return;
            ByteBuffer attWarringBuf = attAA.getMeta(AllianceMeta.IS_WARRING);
            if (attWarringBuf != null && attWarringBuf.get() > 0) return;

            if (stat != null && stat.type == CounterType.ESCALATION) {
                AlertUtil.forEachChannel(f -> true, GuildKey.ESCALATION_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
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

                    DBNation warDef = DBNation.getById(war.defender_id);
                    if (warDef == null || warDef.getPosition() < 1) continue;
                    CounterStat stats = war.getCounterStat();
                    if (stats != null && stats.type == CounterType.IS_COUNTER) {
                        if (escalatedWars == null) escalatedWars = new HashSet<>();
                        escalatedWars.add(war);
                    }
                }
                if (escalatedWars != null && escalatedWars.size() > 2) {
                    AlertUtil.forEachChannel(f -> true, GuildKey.ESCALATION_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                        @Override
                        public void accept(MessageChannel channel, GuildDB guildDB) {
                            card.embed(new DiscordChannelIO(channel), false, true);
                        }
                    });
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
        Map<Integer, DBWar> activeWars = Locutus.imp().getWarDb().getActiveWars();
        Map<Integer, Set<DBWar>> defWarsByAA = new HashMap<>();

        for (DBWar war : activeWars.values()) {
            DBNation attacker = war.getNation(true);
            DBNation defender = war.getNation(false);
            if (attacker == null || attacker.getAlliance_id() == 0 || attacker.getVm_turns() > 0 || attacker.active_m() > 2880) continue;
            if (defender == null || defender.getPositionEnum().id <= Rank.APPLICANT.id || defender.getVm_turns() > 0 || defender.active_m() > 2000 || defender.isGray() || defender.getAlliance_id() != war.defender_aa) continue;
            int aaId = war.defender_aa;
            defWarsByAA.computeIfAbsent(aaId, f -> new HashSet<>()).add(war);
        }

        Map<DBAlliance, Double> warRatio = new HashMap<>();
        for (Map.Entry<Integer, Set<DBWar>> entry : defWarsByAA.entrySet()) {
            DBAlliance alliance = DBAlliance.get(entry.getKey());
            if (alliance == null) continue;
            Set<DBWar> wars = entry.getValue();
            if (wars.size() < 6) continue;
            Set<DBNation> nations = alliance.getNations(f -> !f.isGray() && f.getPositionEnum().id > Rank.APPLICANT.id && f.active_m() <= 2000 && f.getVm_turns() == 0);
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
            if (numDefending > 4) {
                double ratio = (numDefendingUnprovoked * 5d + numDefending) / Math.max(1, (nations.size() - numBeige));
                warRatio.put(alliance, ratio);
            }
        }

        Map<DBAlliance, Boolean> isAtWar = new HashMap<>();
        Map<DBAlliance, String> warInfo = new HashMap<>();

        for (Map.Entry<DBAlliance, Double> entry : warRatio.entrySet()) {
            DBAlliance alliance = entry.getKey();
            Set<DBNation> nations = alliance.getNations(f -> !f.isGray() && f.getPositionEnum().id > Rank.APPLICANT.id && f.active_m() <= 2000 && f.getVm_turns() == 0);

            List<DBWar> active = alliance.getActiveWars();
            Map<Integer, Integer> notableByAA = new HashMap<>();
            int max = 0;

            outer:
            for (DBWar war : active) {
                int otherAA = war.attacker_aa;
                if (otherAA == 0) continue;
                if (war.defender_aa != alliance.getAlliance_id()) continue;

                DBNation attacker = war.getNation(true);
                DBNation defender = war.getNation(false);
                if (attacker == null || defender == null) continue;
                if (!nations.contains(defender)) continue;
                if (defender.getActive_m() > 2000 || defender.getPositionEnum().id <= Rank.APPLICANT.id || (defender.getSoldiers() == 0 && defender.getTanks() == 0)) continue;

                int amt = notableByAA.getOrDefault(otherAA, 0) + 1;
                notableByAA.put(otherAA, amt);
                max = Math.max(max, amt);
            }
            if (max >= 6) {
                StringBuilder body = new StringBuilder();
                body.append("#" + alliance.getRank() + " | " + alliance.getMarkdownUrl() + "\n");
                for (Map.Entry<Integer, Integer> warEntry : notableByAA.entrySet()) {
                    body.append("- " + warEntry.getValue() + " unprovoked wars from " + PnwUtil.getMarkdownUrl(warEntry.getKey(), true) + "\n");
                }
                warInfo.put(alliance, body.toString());
                isAtWar.put(alliance, true);
            }
        }

        long currentTurn = TimeUtil.getTurn();
        long warTurnThresheld = 7 * 12;

        Set<DBAlliance> top = new HashSet<>(isAtWar.keySet());
        top.addAll(Locutus.imp().getNationDB().getAlliances(true, true, true, 80));

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

            alliance.setMeta(AllianceMeta.LAST_BLITZ_PCT, currentRatio);
            alliance.setMeta(AllianceMeta.IS_WARRING, (byte) (warring ? 1 : 0));
            if (warring) alliance.setMeta(AllianceMeta.LAST_AT_WAR_TURN, currentTurn);
            String body = warInfo.get(alliance);

            if (body != null && !lastWarring && warring && currentTurn - lastWarTurn > warTurnThresheld) {
                String title = alliance.getName() + " is being Attacked";
                AlertUtil.forEachChannel(f -> true, GuildKey.ESCALATION_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                    @Override
                    public void accept(MessageChannel channel, GuildDB guildDB) {
                        DiscordUtil.createEmbedCommand(channel, title, body);
                    }
                });
            }
        }
    }
}