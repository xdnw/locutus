package link.locutus.discord.commands.rankings;

import de.erichseifert.gral.data.DataTable;
import de.erichseifert.gral.graphics.Insets2D;
import de.erichseifert.gral.graphics.Location;
import de.erichseifert.gral.io.plots.DrawableWriter;
import de.erichseifert.gral.io.plots.DrawableWriterFactory;
import de.erichseifert.gral.plots.BarPlot;
import de.erichseifert.gral.plots.colors.ColorMapper;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.SheetBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.CIEDE2000;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.*;
import java.util.function.Function;

public class MilitaryRanking extends Command {
    public MilitaryRanking() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.alliance.stats.militarization.cmd);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return """
                Get the militarization levels of top 80 alliances.
                Each bar is segmented into four sections, from bottom to top: (soldiers, tanks, planes, ships)
                Each alliance is grouped by sphere and color coded.
                Use `-t` to remove untaxable nations.
                Use `-i` to remove inactive nations.
                Use `-a` to include applicant nations.""";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        var db = guild == null ? null : Locutus.imp().getGuildDB(guild);

        boolean removeUntaxable = flags != null && flags.contains('t');
        boolean removeInactive = flags != null && flags.contains('i');
        boolean includeApplicants = flags != null && flags.contains('a');

        Integer top_n_alliances = null;
        SpreadSheet sheet = null;
        NationList nations = null;
        Long snapshotDate = null;

        for (String tok : args) {
            if (tok == null || tok.isBlank()) continue;

            Optional<Integer> oi = top_n_alliances == null ? tryParseTopN(tok) : Optional.empty();
            if (oi.isPresent()) { top_n_alliances = oi.get(); continue; }

            Optional<SpreadSheet> os = sheet == null ? tryParseSheet(tok) : Optional.empty();
            if (os.isPresent()) { sheet = os.get(); continue; }

            Optional<NationList> on = nations == null ? tryParseNations(tok, guild, author, me) : Optional.empty();
            if (on.isPresent()) { nations = on.get(); continue; }

            Optional<Long> ot = snapshotDate == null ? tryParseTimestamp(tok) : Optional.empty();
            if (ot.isPresent()) { snapshotDate = ot.get(); continue; }

            throw new IllegalArgumentException("Could not parse argument: `" + tok + "`");
        }

        return StatCommands.militaryRanking(db, channel, nations, top_n_alliances, sheet, removeUntaxable, removeInactive, includeApplicants, snapshotDate);
    }

    private static Optional<Integer> tryParseTopN(String tok) {
        try {
            int v = Integer.parseInt(tok);
            if (v < 1 || v > 500) {
                throw new IllegalArgumentException("Invalid value for -n / top_n_alliances: must be between 1 and 500");
            }
            return Optional.of(v);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<SpreadSheet> tryParseSheet(String tok) {
        try {
            return Optional.ofNullable(SheetBindings.sheet(tok));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        } catch (Exception ignored) { // tolerate other failures
            return Optional.empty();
        }
    }

    private static Optional<NationList> tryParseNations(String tok, Guild guild, User author, DBNation me) {
        try {
            NationList parsed = PWBindings.nationList(null, guild, tok, author, me);
            return Optional.ofNullable(parsed);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Long> tryParseTimestamp(String tok) {
        try {
            return Optional.of(PrimitiveBindings.timestamp(tok));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}