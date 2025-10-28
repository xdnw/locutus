package link.locutus.discord.commands.sync;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.entities.PwUid;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.task.multi.GetUid;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SyncUid extends Command {
    public SyncUid() {
        super(CommandCategory.DEBUG, CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.sync2.uid.cmd);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        Collection<DBNation> nations = Locutus.imp().getNationDB().getAllNations();

        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("true")) {
            int i = 0;
            for (DBNation nation : nations) {
                if (!Locutus.imp().getDiscordDB().getUuids(nation.getNation_id()).isEmpty()) continue;

                PwUid uid = new GetUid(nation, false).call();
            }
        } else {
            Map<PwUid, Set<Integer>> map = Locutus.imp().getDiscordDB().getUuidMap();
            for (Map.Entry<PwUid, Set<Integer>> entry : map.entrySet()) {
                if (entry.getValue().size() <= 1) continue;

                for (int nationId : entry.getValue()) {
                    DBNation nation = Locutus.imp().getNationDB().getNationById(nationId);
                    if (nation != null) {
                        new GetUid(nation, false).call();
                    }
                }
            }
        }



        return "Done";
    }
}
