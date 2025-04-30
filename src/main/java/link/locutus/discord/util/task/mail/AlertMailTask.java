package link.locutus.discord.util.task.mail;

import link.locutus.discord.event.mail.MailReceivedEvent;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.offshore.Auth;

import java.util.List;
import java.util.function.BiConsumer;

public class AlertMailTask extends CaughtRunnable implements BiConsumer<Mail, List<String>> {
    private final SearchMailTask task;
    private long outputChannel;
    private final Auth auth;

    public AlertMailTask(Auth auth, long channel) {
        this.auth = auth;
        this.task = new SearchMailTask(auth, null, true, false, true, this);
        this.outputChannel = channel;
    }
    @Override
    public void runUnsafe() {
        try {
            this.task.call();
        } catch (Throwable e) {
            AlertUtil.error("Error reading mail for: " + auth.getNationId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void accept(Mail mail, List<String> strings) {
        try {
            if (strings.size() == 0) {
                return;
            }
            new MailReceivedEvent(auth, mail, strings, outputChannel).post();
//
//            long output = outputChannel;
//            String[] split = mail.subject.split("/");
//            if (split.length > 1 && MathMan.isInteger(split[split.length - 1])) {
//                output = Long.parseLong(split[split.length - 1]);
//            }
//
//            GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(output);
//            if (channel == null) {
//                channel = Locutus.imp().getDiscordApi().getGuildChannelById(outputChannel);
//            }
//
//            if (channel != null) {
//                Guild guild = channel.getGuild();
//                processCommands(guild, mail, strings);
//            }
//
//            if (nation == null) return;
//
//            if (strings.isEmpty()) return;
//            String msg = strings.get(0);
//
//            if (mail.subject.toLowerCase().startsWith("targets-")) {
//                if (msg.toLowerCase().startsWith("more")) {
//
//                    Set<Integer> tracked = new HashSet<>();
//
//
//                    String targets = null;
//                    GuildDB db = null;
//                    if (channel != null) {
//                        db = Locutus.imp().getGuildDB(channel.getGuild());
//                    }
//                    if (db == null) {
//                        db = Locutus.imp().getGuildDB(nation.getAlliance_id());
//                    }
////                    if (rootDB != null && rootDB.getCoalition("spyops").contains(nation.getAlliance_id())) {
////                        db = rootDB;
////                    }
//                    try {
//                        if (db != null && !db.getCoalition(Coalition.ENEMIES).isEmpty()) {
//                            Spyops cmd = new Spyops();
//
//                            split = msg.split(" ");
//                            String type = "*";
//                            if (split.length >= 2) {
//                                try {
//                                    type = MilitaryUnit.valueOf(split[1].toUpperCase()).name();
//                                } catch (IllegalArgumentException igniore) {
//                                }
//                            }
//                            ArrayList<String> args = new ArrayList<>(Arrays.asList("#wars>0,enemies", type));
//                            Set<Character> flags = new HashSet<>(Arrays.asList('s', 'r'));
//                            targets = cmd.run(null, nation.getUser(), nation, nation, db, args, flags);
//                        } else if (db == null) {
//                            targets = "Your alliance does not have Locutus setup. Use the command on discord instead:\n" + CM.spy.find.target.cmd.toSlashMention() +  "";
//                        } else {
//                            targets = "Your alliance does not have any enemies set. Use the command on discord instead:\n" + CM.spy.find.target.cmd.toSlashMention() + "";
//                        }
//                        if (targets != null) {
//                            String response = new MailRespondTask(auth, mail.leader, mail.id, MarkupUtil.bbcodeToHTML(targets), null).call();
//                            if (channel != null) {
//                                RateLimitUtil.queueWhenFree(channel.sendMessage("Sending target messages to " + nation.getNation() + ": " + response));
//                            }
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
//
//    private void processCommands(Guild guild, Mail mail, List<String> strings) {
//        String reply = strings.get(0);
//        if (reply.isEmpty() || reply.charAt(0) != (Settings.commandPrefix(true)).charAt(0)) return;
//
//        DBNation nation = DBNation.getById(mail.nationId);
//        if (nation == null) return;
//
//        GuildDB db = Locutus.imp().getGuildDB(guild);
//        if (db == null) return;
//
//        if (nation.getPosition() <= 1 || !db.isAllianceId(nation.getAlliance_id())) return;
//    }
}
