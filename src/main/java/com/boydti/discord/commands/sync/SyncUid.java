package com.boydti.discord.commands.sync;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.task.multi.GetUid;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
