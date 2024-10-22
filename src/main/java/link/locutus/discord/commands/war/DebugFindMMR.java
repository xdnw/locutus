package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DebugFindMMR extends Command {
    @Override
    public String help() {
        return super.help() + " <mmr> <num-cities>";
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of();
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) return usage(args.size(), 2, channel);
        String mmr = args.get(0);
        if (mmr.length() != 4 && !MathMan.isInteger(mmr)) return usage("MMR must be 4 numbers, not `" + mmr + "`", channel);

        Integer cities = MathMan.parseInt(args.get(1));
        if (cities == null) return "Invalid city count: `" + args.get(1) + "`";

        List<String> found = new ArrayList<>();
        for (DBNation nation : Locutus.imp().getNationDB().getNationsByAlliance().values()) {
            if (nation.getCities() != cities || nation.hasUnsetMil()) continue;

            int reqSoldiers = (mmr.charAt(0) - '0') * cities * Buildings.BARRACKS.getUnitCap();
            int reqTanks = (mmr.charAt(1) - '0') * cities * Buildings.FACTORY.getUnitCap();
            int reqAircraft = (mmr.charAt(2) - '0') * cities * Buildings.HANGAR.getUnitCap();
            int reqShips = (mmr.charAt(3) - '0') * cities * Buildings.DRYDOCK.getUnitCap();

            if (nation.getSoldiers() != reqSoldiers) continue;
            if (nation.getTanks() != reqTanks) continue;
            if (nation.getAircraft() != reqAircraft) continue;
            if (nation.getShips() != reqShips) continue;

            found.add("<" + nation.getUrl() + ">");
        }
        if (!found.isEmpty()) {
            found = found.subList(0, Math.min(2, found.size()));
            return StringMan.join(found, "\n");
        }
        return "No result";
    }
}
