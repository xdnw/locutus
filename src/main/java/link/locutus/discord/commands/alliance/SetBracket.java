package link.locutus.discord.commands.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class SetBracket extends Command {
    public SetBracket() {
        super("SetBracket", "SetTaxes", "SetTaxRate", "SetTaxBracket", CommandCategory.MEMBER, CommandCategory.ECON);
    }

    @Override
    public String help() {
        return super.help() + " <nation> <tax-id/url> [internal-taxrate]";
    }

    @Override
    public String desc() {
        return "List or set your tax bracket." +
                "e.g. `" + Settings.commandPrefix(true) + "SetTaxes @user 25/25`\n" +
                "or to also set internal: `" + Settings.commandPrefix(true) + "SetTaxes @user 100/100 25/25`\n" +
                "Notes:\n" +
                " - Internal tax rate affects what portion of taxes are not included in " + CM.deposits.check.cmd.toSlashMention() + " (typically used when 100/100 taxes)\n" +
                " - Set the alliance internal tax rate with: " + CM.settings.cmd.create(GuildDB.Key.TAX_BASE.name(), null) + " (retroactive)\n" +
                " - This command is not retroactive and overrides the alliance internal taxrate";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ECON_LOW_GOV.has(user, server) || (Locutus.imp().getGuildDB(server).getOrNull(GuildDB.Key.MEMBER_CAN_SET_BRACKET) == Boolean.TRUE && Roles.MEMBER.has(user, server));
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 3) return usage(event);

        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation == null) {
            return usage(event);
        }
        GuildDB db = Locutus.imp().getGuildDB(guild);
        boolean isGov = Roles.ECON_LOW_GOV.has(author, guild) || Roles.INTERNAL_AFFAIRS.has(author, guild);
        if (!isGov) {
            if (me != nation) return "You are only allowed to set your own tax rate";
        }
        TaxRate taxBase = db.getHandler().getInternalTaxrate(nation.getNation_id());
        if (isGov) taxBase = new TaxRate(-1, -1);

        DBAlliance alliance = nation.getAlliance();
        if (!db.isAllianceId(alliance.getId())) return nation.getNation() + " is not in " + alliance;

        Map<Integer, TaxBracket> brackets = alliance.getTaxBrackets(false);

        if (args.size() == 1) {
            StringBuilder response = new StringBuilder();
            response.append("Brackets:");
            for (Map.Entry<Integer, TaxBracket> entry : brackets.entrySet()) {
                TaxBracket bracket = entry.getValue();
                String url = bracket.getUrl();
                response.append("\n - " + MarkupUtil.markdownUrl("#" + bracket.taxId, url) + ": " + bracket.moneyRate + "/" + bracket.rssRate + " (" + bracket.getNations().size() + " nations) - " + bracket.getName());
            }
            return usage(event, response.toString());
        }

        String arg = args.get(1);
        TaxBracket bracket = null;
        if (MathMan.isInteger(arg) || arg.contains("tax_id=")) {
            String[] split = arg.split("=");
            int taxId = Integer.parseInt(split[split.length - 1]);
            bracket = brackets.get(taxId);
        } else {
            for (TaxBracket other : brackets.values()) {
                if (other.getName().equalsIgnoreCase(arg)) {
                    bracket = other;
                    break;
                }
                if (arg.contains("/")) {
                    String[] split = arg.split("/");
                    int moneyRate = Integer.parseInt(split[0]);
                    int rssRate = Integer.parseInt(split[1]);
                    if (other.moneyRate == moneyRate && other.rssRate == rssRate) {
                        bracket = other;
                        break;
                    }
                }
            }
        }
        if (bracket == null) return "No alliance bracket found for: `" + arg + "`";

        if (taxBase != null && !Roles.INTERNAL_AFFAIRS.has(author, guild)) {
            if (bracket.moneyRate < taxBase.money || bracket.rssRate < taxBase.resources) {
                return "The minimum taxrate you can set is: " + taxBase;
            }
        }

        if (!isGov) {
            double depo = me.getNetDepositsConverted(db);
            if (depo < -200_000_000) {
                if (bracket.moneyRate < 100 || bracket.rssRate < 100) {
                    return "Nations in >200m debt must have a gov change their tax rate";
                }
            }
        }

        StringBuilder response = new StringBuilder();

        if (args.size() >= 3) {
            if (!isGov) {
                return "You are only allowed to set your ingame taxrate";
            }
            TaxRate internalRate = new TaxRate(args.get(2));
            if (internalRate.money < -1 || internalRate.money > 100) return "Invalid taxrate: " + internalRate;
            if (internalRate.resources < -1 || internalRate.resources > 100) return "Invalid taxrate: " + internalRate;
            db.setMeta(nation.getNation_id(), NationMeta.TAX_RATE, new byte[]{(byte) internalRate.money, (byte) internalRate.resources});
            response.append("Set internal taxrate to " + internalRate + "\n");
        }

        response.append(alliance.setTaxBracket(bracket, nation));
        return response.toString();
    }
}
