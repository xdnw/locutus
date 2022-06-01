package link.locutus.discord.commands.sync;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.task.multi.GetUid;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SyncUid extends Command {
    public SyncUid() {
        super(CommandCategory.DEBUG, CommandCategory.LOCUTUS_ADMIN);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        Collection<DBNation> nations = Locutus.imp().getNationDB().getNations().values();

        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("true")) {
            int i = 0;
            for (DBNation nation : nations) {
                if (!Locutus.imp().getDiscordDB().getUuids(nation.getNation_id()).isEmpty()) continue;

                BigInteger uid = new GetUid(nation).call();
            }
        } else {
            Map<BigInteger, Set<Integer>> map = Locutus.imp().getDiscordDB().getUuidMap();
            for (Map.Entry<BigInteger, Set<Integer>> entry : map.entrySet()) {
                if (entry.getValue().size() <= 1) continue;

                for (int nationId : entry.getValue()) {
                    DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
                    if (nation != null) {
                        new GetUid(nation).call();
                    }
                }
            }
        }



        return "Done";
    }
}
