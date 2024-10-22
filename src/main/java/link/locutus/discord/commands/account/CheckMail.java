package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.mail.Mail;
import link.locutus.discord.util.task.mail.SearchMailTask;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;

public class CheckMail extends Command {
    public CheckMail() {
        super(CommandCategory.LOCUTUS_ADMIN, CommandCategory.INTERNAL_AFFAIRS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && (Roles.INTERNAL_AFFAIRS.has(user, server) && Locutus.imp().getGuildDB(server).isWhitelisted());
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.mail.search.cmd);
    }

    @Override
    public String help() {
        return super.help() + " <query> <checkUnread=true> <checkRead=false> <readContent=true>";
    }

    @Override
    public String desc() {
        return """
                Search inbox for messages.
                Add -g to group responses by nation
                Add -c to count responses""";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 4) return usage(args.size(), 4, channel);

        String query = args.get(0);
        if (query.equalsIgnoreCase("*")) query = "";

        channel.sendMessage("Please wait...");

        GuildDB db = Locutus.imp().getGuildDB(guild);


        boolean checkUnread = Boolean.parseBoolean(args.get(1));
        boolean checkRead = Boolean.parseBoolean(args.get(2));
        boolean readContent = Boolean.parseBoolean(args.get(3));
        boolean group = flags.contains('g');
        boolean count = flags.contains('c');

        Map<DBNation, Map<Mail, List<String>>> results = new LinkedHashMap<>();

        SearchMailTask task = new SearchMailTask(me.getAuth(true), query, checkUnread, checkRead, readContent, (mail, strings) -> {
            DBNation nation = Locutus.imp().getNationDB().getNationById(mail.nationId);
            if (nation != null) {
                results.computeIfAbsent(nation, f -> new LinkedHashMap<>()).put(mail, strings);
            }
        });
        task.call();

        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.MAIL_RESPONSES_SHEET);

        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "mail-id",
                "subject",
                "response"
        ));

        sheet.setHeader(header);

        List<Object> row = new ArrayList<>();
        for (Map.Entry<DBNation, Map<Mail, List<String>>> entry : results.entrySet()) {
            row.clear();
            DBNation nation = entry.getKey();

            int countVal = 0;
            for (Map.Entry<Mail, List<String>> mailListEntry : entry.getValue().entrySet()) {
                if (!group) row.clear();

                Mail mail = mailListEntry.getKey();
                List<String> strings = mailListEntry.getValue();

                if (row.isEmpty()) {
                    int allianceId = nation != null ? nation.getAlliance_id() : 0;
                    row.add(MarkupUtil.sheetUrl(PW.getName(mail.nationId, false), PW.getUrl(mail.nationId, false)));
                    row.add(MarkupUtil.sheetUrl(PW.getName(allianceId, true), PW.getUrl(allianceId, true)));
                    row.add(mail.id + "");
                    row.add(mail.subject);
                }

                if (count) {
                    strings.removeIf(f -> f.equalsIgnoreCase("false"));
                    if (group) {
                        countVal += strings.size();
                    } else {
                        row.add(strings.size());
                    }
                } else {
                    row.addAll(strings);
                }

                if (!group) sheet.addRow(row);
            }
            if (group) {
                if (count) {
                    row.add(countVal);
                }
                sheet.addRow(row);
            }
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(channel.create(), "mail", null, false, 0).send();
        return null;
    }
}