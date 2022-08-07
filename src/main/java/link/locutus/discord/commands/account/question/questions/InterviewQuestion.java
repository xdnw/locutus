package link.locutus.discord.commands.account.question.questions;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.account.question.Question;
import link.locutus.discord.commands.alliance.SetRank;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.domains.Alliance;
import link.locutus.discord.apiv1.domains.Nation;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public enum InterviewQuestion implements Question {
    INTRO("Hello, welcome to Gladiators! We'll run through the following:\n" +
            " - A short interview\n" +
            " - Learning the game\n" +
            " - Your path to become a Gladiator and joining the Council\n", false) {
        @Override
        public String format(Guild guild, User author, DBNation me, GuildMessageChannel channel, String message) {
            Role role = Roles.INTERVIEWER.toRole(guild);
            if (role != null) {
                message += "\nDon't be shy to ping an " + role.getAsMention() + " gov member if you want to walk through something with us!";
            }
            return super.format(guild, author, me, channel, message);
        }
    },

    VERIFY("please use `" + Settings.commandPrefix(true) + "verify <nation>` or tell us what your nation is so we can register you.", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) {
            return me != null;
        }
    },

    APPLY_INGAME("please apply ingame: <https://politicsandwar.com/alliance/join/id={guild.alliance_id}>", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            GuildDB db = Locutus.imp().getGuildDB(guild);
            Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId == null || aaId.equals(me.getAlliance_id())) return true;

            if (aaId.equals(me.getAlliance_id())) return true;
            if (me.getAlliance_id() == 0) return false;
            throw new IllegalArgumentException("please leave your alliance first then " + getContent());
        }
    },

//    REROLL("Why did you choose us?\n\n" +
//            "*please type out your answer*", false) {
//        @Override
//        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
//            long latestId = channel.getLatestMessageIdLong();
//        }
//    },

    LONG_TERM("Did you like the game so far? Are you planning to be a long term player?", false, "Y", "N") {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if (!input.equalsIgnoreCase("Y")) {
                Role role = Roles.TEMP.toRole(guild);
                if (role != null) {
                    Member member = guild.getMember(author);
                    RateLimitUtil.queue(guild.addRoleToMember(member, role));
                }
            }
            return true;
        }
    },

//    ACTIVE("We value members that check in on discord regularly and seek advice on how to improve their nation or get more involved in the community. \n" +
//            "If you go inactive for 7 days without warning, we will set you as applicant and wont protect you if you are attacked.\n\n" +
//            "Can you be active on discord daily?", false, "Y", "N") {
//        @Override
//        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
//            if (input.equalsIgnoreCase("Y")) {
//                Role role = Roles.TEMP.toRole(guild);
//                if (role != null) {
//                    Member member = guild.getMember(author);
//                    guild.addRoleToMember(member, link.locutus.discord.util.RateLimitUtil.queue(role));
//                }
//            }
//            return true;
//        }
//    },

    TAXES("A portion of your city income will be deposited into the alliance bank via taxes, and included in your personal `" + Settings.commandPrefix(true) + "deposits` (updated weekly)\n" +
            "We want to provide an efficient centralized economy and can send you resources to run/grow your nation.\n" +
            "Note: your profit from raiding or trading is never taxed.\n\n" +
            "Are you fine with with the default rate of 50%? (Note: You can change it at any time using `" + Settings.commandPrefix(true) + "SetTaxRate`)", false, "Y", "N") {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if (!input.equalsIgnoreCase("Y")) {
                Role ia = Roles.INTERVIEWER.toRole(guild);
                if (ia != null) {
                    String mention = author.getAsMention();
                    String msg = "Please use `" + Settings.commandPrefix(true) + "SetTaxRate " + mention + " 25/25` or `" + Settings.commandPrefix(true) + "SetTaxRate " + mention + " 50/50`";
                    RateLimitUtil.queue(channel.sendMessage(msg));
                }
            }
            return true;
        }
    },

    FIGHTING("Do you like the idea of fighting with us during war, and defending allies when called upon?", false, "Y", "N") {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if (!input.equalsIgnoreCase("Y")) {
                Role ia = Roles.INTERVIEWER.toRole(guild);
                if (ia != null) {
                    String msg = ia.getAsMention() + "please discuss the importance of fighting in this game with " + author.getAsMention();
                    RateLimitUtil.queue(channel.sendMessage(msg));
                }
                throw new IllegalArgumentException("Fighting is an important part of this game, and a requirement of the alliance. Please discuss your stance with gov");
            }
            return true;
        }
    },

    RESPECT("We are trying to create a positive community, do you promise to be nice to all the other members? (i.e. no nazism, racism, homophobia)", false, "Y", "N") {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if (!input.equalsIgnoreCase("Y")) {
                Role ia = Roles.INTERVIEWER.toRole(guild);
                if (ia != null) {
                    String msg = ia.getAsMention() + " please discuss why we want to create a positive community with " + author.getAsMention();
                    RateLimitUtil.queue(channel.sendMessage(msg));
                }
                throw new IllegalArgumentException("**Please discuss with gov on why you think you can't be nice to others**\n\n" + getContent());
            }
            return true;
        }
    },

    DEBT("Do you agree to settle any debts if you are leaving the alliance?", false, "Y", "N") {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if (!input.equalsIgnoreCase("Y")) {
                throw new IllegalArgumentException("If you accept loans from the AA, and your taxes or deposits haven't paid that off, a repayment plan should be worked out when you leave. " +
                        "This is standard procedure. Discuss with gov if you have any concerns.\n\n" + getContent());


            }
            Role ia = Roles.INTERVIEWER.toRole(guild);
            if (ia != null) {
                GuildDB db = Locutus.imp().getGuildDB(guild);
                Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
                if (aaId != null && me.getAlliance_id() != aaId || me.getPosition() <= 1) {
                    String msg = ia.getAsMention() + " please conduct a short interview";
                    RateLimitUtil.queue(channel.sendMessage(msg));
                }
            }
            return true;
        }
    },

    CHECK_RANK("please wait for a gov member to conduct a short interview", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            GuildDB db = Locutus.imp().getGuildDB(guild);
            Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId != null) {
                if (me.getAlliance_id() != aaId || me.getPosition() <= 1) {
                    Role iaRole = Roles.INTERVIEWER.toRole(guild);
                    User sudoUser = sudoer.getUser();
                    if (sudoer != null && sudoer.getPosition() > 2 && sudoUser != null) {
                        Member sudoMember = guild.getMember(sudoUser);
                        if (sudoMember != null && sudoMember.getRoles().contains(iaRole)) {
                            try {
                                String result = new SetRank().onCommand(guild, channel, sudoUser, sudoer, me.getUser().getAsMention() + " MEMBER");
                                if (result != null) {
                                    RateLimitUtil.queue(channel.sendMessage(result));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            Member member = guild.getMember(author);
                            Role memberRole = Roles.MEMBER.toRole(guild);
                            if (member != null && memberRole != null) {
                                RateLimitUtil.queue(guild.addRoleToMember(member, memberRole));
                            }
                        }
                    } else {
                        if (me.getAlliance_id() != aaId || me.getPosition() <= 1) return false;
                    }
                }
            }

            Role memberRole = Roles.MEMBER.toRole(guild);
            if (memberRole != null) {
                if (!guild.getMember(author).getRoles().contains(memberRole)) return false;
            }
            return true;
        }
    },

    INTRODUCTION("By completing these queries, you can be promoted to GRADUATED and have the opportunity to become alliance gov\n\n" +
            "To start off, please introduce yourself in <#{guild.introduction_channel}>", false),

    INFO("Checkout <#{guild.info_channel}> to find out who your government members are, and who to ask for help", false),

    OBJECTIVES("Complete the objectives https://politicsandwar.com/nation/objectives/\n" +
            "If you'd like any funds or assistance, give us a ping", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if (me.getCities() <= 1) {
                if (me.getCities() <= 1) return false;
            }
            return true;
        }
    },

    COLOR("You can go to <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/>" + " and change your trade bloc from {color} to {alliance.color} (for trade block revenue)\n\n" +
            "(Note, if you can't change your policy yet, you can skip this step and do it later)", true, "\u27A1\uFE0F", "\uD83D\uDEAB") {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if ("\uD83D\uDEAB".equalsIgnoreCase(input)) return true;

            NationColor color = me.getColor();
            Alliance alliance = Locutus.imp().getPnwApi().getAlliance(me.getAlliance_id());
            if (!color.name().equalsIgnoreCase(alliance.getColor()) && color != NationColor.BEIGE) {
                if (!color.name().equalsIgnoreCase(alliance.getColor()) && color != NationColor.BEIGE) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String format(Guild guild, User author, DBNation me, GuildMessageChannel channel, String message) {
            try {
                Alliance alliance = Locutus.imp().getPnwApi().getAlliance(me.getAlliance_id());
                message = message.replace("{alliance.color}", alliance.getColor());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return super.format(guild, author, me, channel, message);
        }
    },

    PIRATE("Raiding is the best way to make $$ for new nations. Go to <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/> and set your war policy to pirate to increase your raiding profit by 40%\n\n" +
            "(Note, if you can't change your policy yet, you can skip this step and do it later)", true, "\u27A1\uFE0F", "\uD83D\uDEAB") {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if ("\uD83D\uDEAB".equalsIgnoreCase(input)) return true;

            if (me.getCities() >= 10) return true;
            if (me.getWarPolicy() != WarPolicy.PIRATE) {
                if (me.getWarPolicy() != WarPolicy.PIRATE) {
                    return false;
                }
            }
            return true;
        }
    },

    BARRACKS("Soldiers are the best unit for looting enemies, and are cheap. Get 5 barracks in each of your cities. <https://politicsandwar.com/cities/>\n\n" +
            "*Note: You can sell off buildings, or buy more infrastructure if you are lacking building slots*", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if (me.getCities() > 10) return true;

            Map<Integer, JavaCity> cityMap = me.getCityMap(true, true);
            if (cityMap.isEmpty()) return true;

            double totalBarracks = 0;
            for (Map.Entry<Integer, JavaCity> entry : cityMap.entrySet()) {
                totalBarracks += entry.getValue().get(Buildings.BARRACKS);
            }
            double avgBarracks = totalBarracks / cityMap.size();
            if (avgBarracks <= 4) {
                return false;
            }
            return true;
        }
    },


    SOLDIERS("You can buy some soldiers from the military tab: <https://politicsandwar.com/nation/military/>\n" +
            "It takes 3 days to max out soldiers from 0 (though you can start raiding right away)\n\n" +
            "Note: Your city needs to be powered to recruit units. Let us know if you need any help powering cities!", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if (me.getCities() > 10) return true;
            int perDay = me.getCities() * 5 * Buildings.BARRACKS.perDay();
            if (me.getSoldiers() < perDay * 0.3) {
                if (me.getSoldiers() < perDay * 0.3) {
                    throw new IllegalArgumentException("**You still only have " + me.getSoldiers() + " soldiers**\n\n" + getContent());
                }
            };
            return true;
        }
    },

    TARGETS("Type in `" + Settings.commandPrefix(true) + "raid` (or use the card in <#{guild.raid_info_channel}>) to get some targets to attack. You can declare FIVE offensive wars at a time.\n\n" +
            "You can checkout the raiding guide when you have time: <https://docs.google.com/document/d/1OAbR_pwza9bomKmJr7bjbRq40EPvbHmoj1_KdQM0GZs/edit>\n\n" +
            "tl;dr since you just have soldiers, do ground attacks. Inactive enemies don't generally fight back", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            GuildDB db = Locutus.imp().getGuildDB(guild);
            if (me.getOff() == 5 || me.getCities() >= 10) return true;

            return (me.getOff() >= 5);
        }
    },

//    PERFORM_ATTACKS("Now that you have declared your raids, you can perform your attacks. Inactive enemies won't fight back, and if you've picked targets with no ground, you can perform ground attacks to defeat them.\n" +
//            "https://politicsandwar.com/nation/war/", true) {
//        @Override
//        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
//            return super.validate(guild, author, me, channel, input);
//        }
//    },

    DEPOSIT_RESOURCES("Having unnecessary resources or $$ on your nation will attract raiders. It is important to safekeep so it wont get stolen when you lose a war. Visit the alliance bank page and store funds for safekeeping:\n" +
            "https://politicsandwar.com/alliance/id={guild.alliance_id}&display=bank\n\n" +
            "*Note: deposit $1 if you don't need to safekeep*", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            GuildDB db = Locutus.imp().getGuildDB(guild);
            Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId != null) {
                List<Transaction2> transactions = me.getTransactions(0);
                for (Transaction2 transaction : transactions) {
                    if (Objects.equals(transaction.receiver_id, aaId)) return true;
                }
            }
            return false;
        }
    },

    CHECK_DEPOSITS("You can check your deposits using:\n" +
            Settings.commandPrefix(true) + "deposits <@{userid}>\n\n" +
            "> We value your deposits using current market prices. You can withdraw any type of resource so long as your total is positive\n" +
            "> Likewise, debt can be repaid with any resource or $$.\n\n" +
            "Try checking your deposits now", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            return me.getMeta(NationMeta.INTERVIEW_DEPOSITS) != null;
        }
    },

    WITHDRAW_DEPOSITS("You can request your funds by asking in <#{guild.resource_request_channel}>\n\n" +
            "*Note: Below 7 cities, we can provide funds to get your two cities up and running, we don't do city grants initially since we want members to start off by raiding for $$$*", false) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            GuildDB db = Locutus.imp().getGuildDB(guild);
            Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId != null) {
                List<Transaction2> transactions = me.getTransactions(0);
                for (Transaction2 transaction : transactions) {
                    if (Objects.equals(transaction.receiver_id, me.getNation_id())) return true;
                }
            }
            throw new IllegalArgumentException("**Please request e.g. $1 to continue.**\n\n" + getContent());
        }

        @Override
        public String format(Guild guild, User author, DBNation me, GuildMessageChannel channel, String message) {
            Role econ = Roles.ECON.toRole(guild);
            if (econ != null) {
                message += " . Ping " + econ.getAsMention() + " to request funds";
            }
            return super.format(guild, author, me, channel, message);
        }
    },

//    TRAINING_DISCORD("Checkout our training discord: <https://discord.gg/ZqbNBvb>", true) {
//        @Override
//        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
//            Member member = Locutus.imp().getDiscordApi().getGuildById(710321760519848009L).getMemberById(author.getIdLong());
//            return member != null;
//        }
//    },

    USEFUL_LINKS("Other useful links\n" +
            "Wiki: https://politicsandwar.fandom.com/\n" +
            "Forums: https://forum.politicsandwar.com/\n" +
            "P&W Discord: https://discord.gg/H9XnGxc", false),


    SPIES("Spies can discover enemy resource amounts and perform various sabotage operations (like destroying planes). " +
            "You can do so daily, without using a war slot, or bringing you out of beige protection, and even against enemies who are under beige protection.\n\n" +
            "You should always purchase max spies every day. Without Intelligence Agency you can buy up to 50 spies.\n\n" +
            "Please purchase spies from the military tab if you have not:\n" +
            "https://politicsandwar.com/nation/military/spies/", false) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            return me.updateSpies(true) > 0;
        }
    },

    ESPIONAGE("Using the 2 spies you purchased, please perform a gather intelligence op against one of the nations you are fighting (covert)\n" +
            " - go to their nation page, and click the espionage button\n" +
            " - Copy the results and post them in any channel here (if you accidentally leave the page, the intel op still is in your notifications)\n\n" +
            "Remember to purchase max spies every day", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            return me.getMeta(NationMeta.INTERVIEW_SPYOP) != null;
        }
    },

    SPY_COMMAND("For intel, we also have tools to estimate enemy spy count and resources without wasting a spy op. " +
            "Try using the commands e.g.:\n" +
            "`" + Settings.commandPrefix(true) + "spies https://politicsandwar.com/nation/id=6`\n" +
            "and\n" +
            "`" + Settings.commandPrefix(true) + "loot https://politicsandwar.com/nation/id=6`\n\n*note: loot estimates are a work in progress*", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            return me.getMeta(NationMeta.INTERVIEW_SPIES) != null && me.getMeta(NationMeta.INTERVIEW_LOOT) != null;
        }
    },

    BUY_LAND("Too little land and a lot of infrastructure will give you a high Population Density. " +
            "Too high population density means your citizens are congested and very prone to Disease.\n" +
            "For simplicity, we recommend purchasing land equal to your infrastructure level", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            Map<Integer, JavaCity> cities = me.getCityMap(true, true);
            for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
                JavaCity city = entry.getValue();
                if (city.getLand() < city.getInfra()) {
                    String url = PnwUtil.getCityUrl(entry.getKey());
                    throw new IllegalArgumentException("**City " + url + " has < " + city.getInfra() + " land**\n\n" + getContent());
                }
            }
            return true;
        }
    },

    GENERATE_CITY_BUILDS("", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            return me.getMeta(NationMeta.INTERVIEW_OPTIMALBUILD) != null;
        }

        @Override
        public String format(Guild guild, User author, DBNation me, GuildMessageChannel channel, String message) {
            double maxInfra = 0;
            Set<Integer> infraLevels = new HashSet<>();

            boolean oddInfraAmounts = false;
            boolean inefficientAmount = false;

            Map<Integer, JavaCity> cities = me.getCityMap(true, true);
            for (JavaCity city : cities.values()) {
                double infra = city.getInfra();
                maxInfra = Math.max(maxInfra, infra);
                infraLevels.add((int) infra);
                if (infra % 50 != 0) {
                    oddInfraAmounts = true;
                }
                if (infra % 100 != 0) {
                    inefficientAmount = true;
                }
            }

            StringBuilder response = new StringBuilder();

            if (inefficientAmount) {
//                response.append("Infrastructure is cheapest when purchased");
            }

            if (infraLevels.size() > 1) {
                response.append("By having different amounts of infrastructure in each city, you cannot import the same build into all of them.\n");
            }

            int maxAllowed = me.getOff() > 0 || me.getDef() > 0 || me.getCities() < 10 ? 1700 : 2000;
            maxInfra = Math.min(maxAllowed, (50 * (((int) maxInfra + 49) / 50)));

            if (oddInfraAmounts) {
                response.append("Each building requires 50 infrastructure to be built, but will continue to operate if infrastructure is lost. " +
                        "It is a waste to purchase infrastructure up to an amount that is not divisible by 50.\n" +
                        "Note: You can purchase up to a specified infra level by entering e.g. `@" + maxInfra + "`\n\n");
            }

            int[] mmr = {0, 0, 0, 0};
            if (me.getCities() < 10 || me.getOff() > 0) mmr[0] = 5;
            else mmr[2] = 5;
            if (me.getAvg_infra() > 1700) mmr[2] = 5;
            if (me.getCities() > 10 && me.getAvg_infra() > 1700 && mmr[0] == 0) mmr[1] = 2;

            response.append("Minimum military requirement (MMR) is what military buildings to have in a city and is in the format e.g. `mmr=1234` (1 barracks, 2 factories, 3 hangars, and 4 drydock) (don't actually use mmr=1234, this is an example)\n\n");

            Integer cityId = cities.keySet().iterator().next();
            String cityUrl = PnwUtil.getCityUrl(cityId);
            String mmrStr = StringMan.join(mmr, "");
            response.append("The `" + Settings.commandPrefix(true) + "OptimalBuild <city>` command can be used to generate a build for a city. Let's try the command now, e.g.:\n" +
                    "`" + Settings.commandPrefix(true) + "OptimalBuild " + cityUrl + " infra=" + maxInfra + " mmr=" + mmrStr + "`\n\n" +
                    "*Note: For help on using the command, use `" + Settings.commandPrefix(true) + "? optimalbuild`, you can also use the card in <#695194325193195580>");

            return response.toString();
        }
    },

    QUIZ("While you are waiting for your raids to finish, we can checkout the guides in <#{guild.info_channel}>\n" +
            "Please be encouraged to ask gov any questions you have about the game!\n\n" +
            "You can take a quick quiz when you are ready:\n" +
            "https://docs.google.com/forms/d/e/1FAIpQLSdnD6MnbGNqW5ldrdpSD6wS2YDpoxraM_vkkz4XQ2eBraFZrg/viewform?usp=sf_link", false) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if (Roles.GRADUATED.has(author, guild)) return true;

            throw new IllegalArgumentException("**Please ping an IA gov to check your test**\n\n" + getContent());
        }
    },

    CHECKUP("You can use the the command:\n" +
            "> " + Settings.commandPrefix(true) + "checkup %user%\n" +
            "To perform an automated audit on yourself", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            return me.getMeta(NationMeta.INTERVIEW_CHECKUP) != null;
        }
    },

    ROI_COMMAND("National Projects provide nation level benefits:\n" +
            "https://politicsandwar.com/nation/projects/\n" +
            "Cities (past your 10th) OR Projects can be purchased every 10 days. You start with 1 project slot, and get more for every 5k infra in your nation.\n\n" +
            "To see which projects the bot recommends (for a 120 day period), use:\n" +
            "> " + Settings.commandPrefix(true) + "roi %user% 120\n\n" +
            "We recommend getting two resource projects after your 10th city", false),

    BEIGE_LOOT("At higher city counts, there are less nations available to raid. You will need to find and hit nations as the come off of the beige protection color.\n" +
            "To list raid targets currently on beige, use:\n" +
            "> `" + Settings.commandPrefix(true) + "raid * 15 -beige`", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            return me.getMeta(NationMeta.INTERVIEW_RAID_BEIGE) != null;
        }
    },


    RAID_TURN_CHANGE("Let's declare on a target as they come off beige:\n" +
            "1. Use e.g. `" + Settings.commandPrefix(true) + "raid * 15 -beige<12` to find a target that ends beige in the next 12 turns\n" +
            "2. Set a reminder on your phone, or on discord using `" + Settings.commandPrefix(false) + "beigeReminder`\n" +
            "3. Get the war declaration page ready, and declare DURING turn change\n\n" +
            "*Note:*\n" +
            " - *If you don't get them on your first shot, try again later*\n" +
            " - *If you can't be active enough, just hit any gray nation during turn change*\n\n" +
            "See also:\n" +
            " - `" + Settings.commandPrefix(false) + "removeBeigeReminder`\n" +
            " - `" + Settings.commandPrefix(false) + "beigeReminders`\n" +
            " - `" + Settings.commandPrefix(false) + "setBeigeAlertRequiredStatus`\n" +
            " - `" + Settings.commandPrefix(false) + "setBeigeAlertMode`\n" +
            " - `" + Settings.commandPrefix(false) + "setBeigeAlertRequiredLoot`\n" +
            " - `" + Settings.commandPrefix(false) + "setBeigeAlertScoreLeeway`", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            List<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(me.getNation_id());
            wars.removeIf(w -> w.attacker_id != me.getNation_id());

            for (DBWar war : wars) {
                long date = war.date;
                if (TimeUtil.getTurn(date) != TimeUtil.getTurn(date - 120000)) {
                    return true;
                }
            }
            throw new IllegalArgumentException("**You have not completed this task (note: the bot checks wars every 15m)**\n\n" + getContent());
        }
    },

    GET_YOURSELF_ROLLED("Get yourself rolled. e.g. Find an inactive raid target in an alliance, attack them and get some counters on yourself.\n" +
            "Remember to deposit your resources so nothing of value gets looted", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if (me.getCities() >= 10) return true;
            if (me.getDef() == 3) return true;
            throw new IllegalArgumentException("**You have not have 3 defensive wars yet. Note: the bot checks wars every 15m**\n\n" + getContent());
//            List<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(me.getNation_id());
//            wars.removeIf(w -> w.attacker_id == me.getNation_id() || w.status != WarStatus.ATTACKER_VICTORY);
        }
    },

    PLAN_A_RAID_WITH_FRIENDS("Raiding is always better with friends. Find a good raid target. Use the command\n" +
            "> `" + Settings.commandPrefix(true) + "counter <nation>`\n" +
            "And see who is online and in range to raid that person with you.", false) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            if (me.getMeta(NationMeta.INTERVIEW_COUNTER) == null) {
                throw new IllegalArgumentException("**Please use `" + Settings.commandPrefix(true) + "counter`**\n\n" + getContent());
            }
            return true;
        }
    },

    CREATE_A_WAR_ROOM("War rooms are channels created to coordinate a war against an enemy target. They will be created automatically by the bot against active enemies.\n" +
            "To manually create a war room, use: `" + Settings.commandPrefix(true) + "WarRoom`", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            return me.getMeta(NationMeta.INTERVIEW_WAR_ROOM) != null;
        }
    },

    // Be involved in a beige cycle of an enemy (1 round, as example)
    // counterspy
    // take out an enemy missile
    // take out an enemy nuke

    // learn about gov
    // ia: sit in an interview
    // ia: help conduct an interview
    // econ: do some trading
    //
    // milcom: organize a counter
    // organize a spy op
    // reimbursed a member for a counter
    // fa: sit in a peace negotiation

    // apply for gov

    // trading guide
    // grant command
    // Requesting a grant for a build
    // Importing a city build

    DONE("That's all  for now. Check back later.", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            return false;
        }
    }

    ;

    private final String content;
    private final boolean validateOnInit;
    private final String[] options;

    InterviewQuestion(String content, boolean validateOnInit, String... options) {
        this.content = content;
        this.validateOnInit = validateOnInit;
        this.options = options;
    }

    public String getContent() {
        return content;
    }

    public boolean isValidateOnInit() {
        return validateOnInit;
    }

    public String[] getOptions() {
        return options;
    }
}
