package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class IASheet extends Command {
    public IASheet() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return super.help() + " <nations>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return (super.checkPermission(server, user) && Roles.INTERNAL_AFFAIRS.has(user, server));
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db == null) return "Not in guild";
        Set<Integer> aaIds = db.getAllianceIds();
        if (aaIds.isEmpty()) return "Please use " + GuildKey.ALLIANCE_ID.getCommandMention() + "";

        if (args.size() != 1) return usage();
        List<DBNation> nations = new ArrayList<>(DiscordUtil.parseNations(guild, args.get(0)));
        for (DBNation nation : nations) {
            if (!db.isAllianceId(nation.getAlliance_id())) {
                return "Nation `" + nation.getName() + "` is in " + nation.getAlliance().getQualifiedId() + " but this server is registered to: "
                        + StringMan.getString(db.getAllianceIds()) + "\nSee: " + CM.settings.info.cmd.toSlashMention() + " with key `" + GuildKey.ALLIANCE_ID.name() + "`";
            }
        }
        nations.removeIf(f -> f.getPosition() <= 1 || f.getVm_turns() != 0);
        nations.sort(Comparator.comparingInt(DBNation::getCities));


        CompletableFuture<IMessageBuilder> msgFuture = channel.sendMessage("Updating...");

        boolean individual = flags.contains('f') || nations.size() == 1;
        IACheckup checkup = new IACheckup(db, db.getAllianceList().subList(aaIds), false);
        Map<DBNation, Map<IACheckup.AuditType, Map.Entry<Object, String>>> allianceAuditMap = checkup.checkup(nations, nation -> new Consumer<DBNation>() {
                    long start = System.currentTimeMillis();
                    @Override
                    public void accept(DBNation nation) {
                        if (-start + (start = System.currentTimeMillis()) > 5000) {
                            try {
                                IMessageBuilder msg = msgFuture.get();
                                if (msg != null && msg.getId() > 0) {
                                    msg.clear().append("Updating for: " + nation.getNation()).sendIfFree();
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                },
                individual
        );

        SpreadSheet sheet = SpreadSheet.create(db, SheetKeys.IA_SHEET);

        IACheckup.AuditType[] audits = IACheckup.AuditType.values();
        int headerLength = audits.length + 5;

        Map<String, Map<DBNation, String>> auditTypeToNationMap = new LinkedHashMap<>();
        Map<DBNation, String> notes = new HashMap<>();
        auditTypeToNationMap.put("note", notes);

        List<List<Object>> existing = sheet.get("A1:B");
        if (existing != null && existing.size() >= 2) {
            List<Object> nationNames = existing.get(0);
            List<Object> noteRow = existing.get(1);
            for (int i = 0; i < Math.min(noteRow.size(), nationNames.size()); i++) {
                Object noteObj = noteRow.get(i);
                Object nameObj = nationNames.get(i);
                if (noteObj == null || nameObj == null) continue;
                DBNation nation = Locutus.imp().getNationDB().getNationByName(nameObj.toString());
                if (nation == null) continue;
                notes.put(nation, nameObj + "");
            }
        }

        for (Map.Entry<DBNation, Map<IACheckup.AuditType, Map.Entry<Object, String>>> entry : allianceAuditMap.entrySet()) {
            DBNation nation = entry.getKey();
            Map<IACheckup.AuditType, Map.Entry<Object, String>> auditResult = entry.getValue();
            for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> auditTypeEntryEntry : auditResult.entrySet()) {
                IACheckup.AuditType type = auditTypeEntryEntry.getKey();
                Map.Entry<Object, String> value = auditTypeEntryEntry.getValue();
                String valueStr = value != null ? value.getValue() : null;
                if (valueStr == null) continue;

                auditTypeToNationMap.computeIfAbsent(type.name(), f -> new HashMap<>()).put(nation, valueStr);
            }
        }

        sheet.clear("A:ZZ");

        ArrayList<String> header = new ArrayList<>();
        header.add("");
        for (DBNation nation : nations) {
            String nationUrl = "=HYPERLINK(\"" + Settings.INSTANCE.PNW_URL() + "/nation/id=%s\",\"%s\")";
            nationUrl = String.format(nationUrl, nation.getNation_id(), nation.getNation());
            header.add(nationUrl);
        }

        sheet.setHeader(header);

        for (Map.Entry<String, Map<DBNation, String>> entry : auditTypeToNationMap.entrySet()) {
            String key = entry.getKey();
            Map<DBNation, String> auditResults = entry.getValue();

            ArrayList<Object> row = new ArrayList<>();
            row.add(key);
            for (DBNation nation : nations) {
                String nationAuditResult = auditResults.get(nation);
                if (nationAuditResult == null) nationAuditResult = "";
                row.add(nationAuditResult);
            }

            sheet.addRow(row);
        }

        sheet.set(0, 0);

        sheet.attach(channel.create(), "ia").send();
        return null;
    }
}
