//package link.locutus.discord.commands.external.guild;
//
//import link.locutus.discord.Locutus;
//import link.locutus.discord.commands.manager.Command;
//import link.locutus.discord.commands.manager.CommandCategory;
//import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
//import link.locutus.discord.config.Settings;
//import link.locutus.discord.db.GuildDB;
//import link.locutus.discord.db.entities.Coalition;
//import link.locutus.discord.user.Roles;
//import net.dv8tion.jda.api.entities.Guild;
//import net.dv8tion.jda.api.entities.User;
//import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
//
//import java.util.LinkedHashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//import static link.locutus.discord.db.GuildKey.values;
//
//public class Setup extends Command {
//    public Setup() {
//        super("setup", "locutus", CommandCategory.GUILD_MANAGEMENT);
//    }
//
//    @Override
//    public String help() {
//        return super.help();
//    }
//
//    @Override
//    public String desc() {
//        return super.desc();
//    }
//
//    @Override
//    public boolean checkPermission(Guild server, User user) {
//        return Roles.ADMIN.has(user, server);
//    }
//
//    @Override
//    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
//        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
//        for (GuildKey key : values()) {
//            if (!key.requiresSetup) continue;
//            if (key.requires != null) {
//                if (guildDb.getOrNull(key.requires) == null) continue;
//            }
//            String value = guildDb.getInfo(key, false);
//            if (value == null) {
//                return "Please use `" + Settings.commandPrefix(true) + "KeyStore " + key.name() + " <value>`";
//            }
//            try {
//                key.validate(guildDb, value);
//            } catch (Throwable e) {
//                return e.getMessage() + " for " + key.name();
//            }
//        }
//
//        for (Roles role : Roles.values()) {
//            if (role.toRole(guild) == null) {
//                return "Please use `" + Settings.commandPrefix(true) + "AliasRole " + role.name() + " <discord-role>`";
//            }
//        }
//
//        Map<String, Set<Integer>> coalitions = guildDb.getCoalitions();
//        {
//            Set<Integer> offshores = coalitions.getOrDefault("offshore", new LinkedHashSet<>());
//            boolean hasValidOffshore = false;
//            for (int allianceId : offshores) {
//                String name = Locutus.imp().getNationDB().getAllianceName(allianceId);
//                if (name != null) hasValidOffshore = true;
//            }
//            if (!hasValidOffshore) {
//                return "Please set an offshore using " + CM.coalition.add.cmd.create("", Coalition.OFFSHORE.name()).toSlashCommand() + "";
//            }
//
//            if (coalitions.getOrDefault("allies", new LinkedHashSet<>()).isEmpty()) {
//                return "Please set allies using `" + Settings.commandPrefix(true) + "setcoalition <alliance> allies";
//            }
//        }
//
//        return "All set! If you are lacking permission for a command, please ping `@borg`";
//    }
//}
