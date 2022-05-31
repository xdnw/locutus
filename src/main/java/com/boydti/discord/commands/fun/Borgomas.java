package com.boydti.discord.commands.fun;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.offshore.Auth;
import com.boydti.discord.util.offshore.OffshoreInstance;
import com.boydti.discord.apiv1.enums.Rank;
import com.boydti.discord.apiv1.enums.ResourceType;
import com.boydti.discord.apiv1.enums.WarType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.boydti.discord.db.entities.NationMeta.BORGMAS;

public class Borgomas extends Command {

    public Borgomas() {
        super("Borgmas", "Christmas", CommandCategory.FUN);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return "He's making a list, And checking it twice; Gonna find out Who's naughty and nice. Saint Borgolas is coming to town. **RESISTANCE IS FUTILE**";
    }

    private Map<Integer, Boolean> received = new ConcurrentHashMap<>();

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) return "Please use `!verify`";

        if (me.getMeta(BORGMAS) != null || received.put(me.getNation_id(), true) != null) {
            return "You've already opened your presents this year. Merry Borgmas!";
        }

        Map.Entry<Integer, Integer> commends = me.getCommends();
        if ((commends.getKey() == 0 && commends.getValue() == 0) || me.getPosition() <= Rank.APPLICANT.id) {
            return me.getLeader() + "... is not on the list. How unfortunate \uD83D\uDE44 (try again later)";
        }

        me.setMeta(BORGMAS, (byte) 1);

        int up = commends.getKey();
        int down = commends.getValue();

        Map<ResourceType, Double> resources;
        String message;
        boolean good = down < 2;
        if (good) {
            message = "You open your present to find... 1,000,000 borg bucks! (Legal tender throughout Unicomplex, Orbis and all assimilated worlds).\nMerry Borgmas!\nhttps://dcassetcdn.com/w1k/submissions/160000/160404_d209.jpg";
            resources = Collections.singletonMap(ResourceType.MONEY, 1000000d);
        } else {
            GuildDB dbAA = Locutus.imp().getGuildDBByAA(me.getAlliance_id());
            if (dbAA != null && (dbAA.isWhitelisted() || dbAA.getOffshore() != null)) {
                message = "You open your present to find a 10kg hunk of coal...\nMerry Borgmas!\nhttps://dcassetcdn.com/w1k/submissions/160000/160404_d209.jpg";
                resources = Collections.singletonMap(ResourceType.COAL, 0.01);
            } else {
                List<DBNation> members = new Alliance(me.getAlliance_id()).getNations(true, 7200, true);
                members.removeIf(f -> f.getActive_m() > 2880 && f.isGray());
                members.add(me);
                DBNation maxNation = null;
                double maxInfra = 0;
                for (DBNation member : members) {
                    double myInfra = member.maxCityInfra();
                    if (myInfra > maxInfra) {
                        maxInfra = myInfra;
                        maxNation = member;
                    }
                }

                Auth auth = Locutus.imp().getRootAuth();
                String result = auth.setBounty(maxNation, WarType.NUCLEAR, 1000000);
                result += "\nMerry Borgmas!\nhttps://dcassetcdn.com/w1k/submissions/160000/160404_d209.jpg";
                return "You open your present to find a receipt:\n" + result;
            }
        }

        String note = "Merry Borgmas!";
        Map.Entry<OffshoreInstance.TransferStatus, String> result = Locutus.imp().getRootBank().transfer(me, resources, note);
        return message;
    }
}
