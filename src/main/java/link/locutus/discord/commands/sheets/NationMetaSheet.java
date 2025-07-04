package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.account.question.questions.InterviewQuestion;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class NationMetaSheet extends Command {
    public NationMetaSheet() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of();
    }

    @Override
    public String help() {
        return super.help() + " <nations> <meta>";
    }

    @Override
    public String desc() {
        return "List nations and their interview progress";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(args.size(), 1, channel);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.NATION_META_SHEET);

        List<String> header = new ArrayList<>(Arrays.asList(
                "Nation",
                "Alliance",
                "Cities"
        ));


        sheet.setHeader(header);

        Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, false);

        for (DBNation nation : nations) {
            User user = nation.getUser();
            if (user == null) continue;
            Member member = guild.getMember(user);
            if (member == null) continue;

            List<Object> row = new ArrayList<>();
            row.add(MarkupUtil.sheetUrl(nation.getNation(), PW.getUrl(nation.getNation_id(), false)));
            row.add(MarkupUtil.sheetUrl(nation.getAllianceName(), PW.getUrl(nation.getAlliance_id(), true)));
            row.add(nation.getCities());
            row.add(nation.getAvg_infra());
            row.add("=\"" + nation.getMMR() + "\"");
            row.add(nation.getOff());
            row.add(nation.getWarPolicy());

            ByteBuffer interviewMeta = nation.getMeta(NationMeta.INTERVIEW_INDEX);
            if (interviewMeta == null) row.add("");
            else row.add(InterviewQuestion.values()[interviewMeta.getInt()].name());

            Set<Role> roles = member.getUnsortedRoles();
            List<String> rolesStr = new ArrayList<>();
            for (Role role : roles) rolesStr.add(role.getName());
            row.add(StringMan.join(rolesStr, ","));


            sheet.addRow(row);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(channel.create(), "nation_meta").send();
        return null;
    }
}
