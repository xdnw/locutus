package com.boydti.discord.commands.account;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.sheet.SpreadSheet;
import com.boydti.discord.util.task.mail.SearchMailTask;
import com.boydti.discord.util.task.mail.Mail;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class CheckMail extends Command {
    public CheckMail() {
        super(CommandCategory.LOCUTUS_ADMIN, CommandCategory.INTERNAL_AFFAIRS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && (Roles.INTERNAL_AFFAIRS.has(user, server) && Locutus.imp().getGuildDB(server).isWhitelisted());
    }

    @Override
    public String help() {
        return super.help() + " <query> <checkUnread=true> <checkRead=false> <readContent=true>";
    }

    @Override
    public String desc() {
        return "Search inbox for messages.\n" +
                "Add -g to group responses by nation\n" +
                "Add -c to count responses";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 4) return usage(event);

        String query = args.get(0);
        if (query.equalsIgnoreCase("*")) query = "";

        MessageChannel channel = event.getChannel();
        RateLimitUtil.queue(channel.sendMessage("Please wait..."));

        GuildDB db = Locutus.imp().getGuildDB(guild);
        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.MAIL_RESPONSES_SHEET);

        List<String> header = new ArrayList<>(Arrays.asList(
            "nation",
            "alliance",
            "mail-id",
            "subject",
            "response"
        ));

        sheet.setHeader(header);

        boolean checkUnread = Boolean.parseBoolean(args.get(1));
        boolean checkRead = Boolean.parseBoolean(args.get(2));
        boolean readContent = Boolean.parseBoolean(args.get(3));

        Map<DBNation, Map<Mail, List<String>>> results = new LinkedHashMap<>();

        SearchMailTask task = new SearchMailTask(me.getAuth(null), query, checkUnread, checkRead, readContent, new BiConsumer<Mail, List<String>>() {
            @Override
            public void accept(Mail mail, List<String> strings) {
                DBNation nation = Locutus.imp().getNationDB().getNation(mail.nationId);
                if (nation != null) {
                    results.computeIfAbsent(nation, f -> new LinkedHashMap<>()).put(mail, strings);
                }
            }
        });
        task.call();

        boolean group = flags.contains('g');
        boolean count = flags.contains('g');

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
                    row.add(MarkupUtil.sheetUrl(PnwUtil.getName(mail.nationId, false), PnwUtil.getUrl(mail.nationId, false)));
                    row.add(MarkupUtil.sheetUrl(PnwUtil.getName(allianceId, true), PnwUtil.getUrl(allianceId, true)));
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

        sheet.clear("A:Z");
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }
}