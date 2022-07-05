package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CountererSheet extends Command {
    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        Set<DBNation> nations;
        if (args.isEmpty()) {
            int allianceId = db.getOrThrow(GuildDB.Key.ALLIANCE_ID);
            nations = Locutus.imp().getNationDB().getNations(Collections.singleton(allianceId));
            nations.removeIf(n -> n.getPosition() <= 1);
        } else if (args.size() == 1) {
            nations = (DiscordUtil.parseNations(guild, args.get(0)));
        } else {
            return usage(event);
        }

        nations.removeIf(f -> f.getActive_m() > 2440);

        List<DBNation> safe = new ArrayList<>();

        outer:
        for (DBNation nation : nations) {
            if (nation.getDef() == 0) {
                safe.add(nation);
            } else {
                double totalStr = 0;
                int numWars = 0;

                List<DBWar> wars = nation.getActiveWars();
                if (wars.isEmpty()) {
                    safe.add(nation);
                    continue;
                }
                for (DBWar war : wars) {
                    DBNation other = war.getNation(!war.isAttacker(nation));
                    if (other.getAircraft() > nation.getAircraft()) continue outer;
                    totalStr += Math.pow(other.getAircraft(), 4);
                    numWars++;
                }
                totalStr = Math.pow(totalStr / numWars, 0.25);

                if (totalStr < nation.getAircraft()) {
                    safe.add(nation);
                }
            }
        }



        return super.onCommand(event, guild, author, me, args, flags);
    }
}
