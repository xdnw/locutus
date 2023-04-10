package link.locutus.discord.commands.fun;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Borgomas extends Command {

    private final Map<Integer, Boolean> received = new ConcurrentHashMap<>();

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

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention() + "";

        if (me.getMeta(NationMeta.BORGMAS) != null || received.put(me.getNation_id(), true) != null) {
            return "You've already opened your presents this year. Merry Borgmas!";
        }

        Map.Entry<Integer, Integer> commends = me.getCommends();
        if ((commends.getKey() == 0 && commends.getValue() == 0) || me.getPosition() <= Rank.APPLICANT.id) {
            return me.getLeader() + "... is not on the list. How unfortunate \uD83D\uDE44 (try again later)";
        }

        me.setMeta(NationMeta.BORGMAS, (byte) 1);

        int down = commends.getValue();

        Map<ResourceType, Double> resources;
        String message;
        boolean good = down < 2;
        if (good) {
            message = "You open your present to find... 1,000,000 borg bucks! (Legal tender throughout Unicomplex, Orbis and all assimilated worlds).\nMerry Borgmas!\nhttps://dcassetcdn.com/w1k/submissions/160000/160404_d209.jpg";
            resources = Collections.singletonMap(ResourceType.MONEY, 1000000d);
        } else {
            GuildDB dbAA = Locutus.imp().getGuildDBByAA(me.getAlliance_id());
            if (true) {
                message = "You open your present to find a 10kg hunk of coal...\nMerry Borgmas!\nhttps://dcassetcdn.com/w1k/submissions/160000/160404_d209.jpg";
                resources = Collections.singletonMap(ResourceType.COAL, 0.01);
            } else {
                Set<DBNation> members = DBAlliance.getOrCreate(me.getAlliance_id()).getNations(true, 7200, true);
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
