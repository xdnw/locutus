package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTopic;
import link.locutus.discord.web.commands.page.PageHelper;
import net.dv8tion.jda.api.entities.User;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class VoteCommands {
//    public static void main(String[] args) {
//        String url = "https://forum.politicsandwar.com/index.php?/topic/46903-reverse-nuke-auction-the-voting-game-round-1/";
//    }
//
//    // TODO FIXME  guildkey bribe_alerts
//
////    /vote bribe voter <url> <option> <price_per_vote> <num_votes> <force>
//    @Command
//    public String bribeVoter(@Me GuildDB db, @Me DBNation nation, @Me User user, DBTopic forum_link, @Range(min=1) int price_per_vote, int num_votes, boolean force) throws IOException {
//        // check url is valid
//        String url = forum_link.getUrl();
//        // fetch with jsoup
//        Document doc = Jsoup.connect(url).get();
//
//        // check their balance
//
//        // deduct balance (sync offshoreinstance bank)
//        // create db entry in guild bribe table + with remaining funds
//    }
//
////
/////vote bribe list
//    @Command
//    public String bribeList(@Me GuildDB db) {
//
//    }
////
/////vote bribe take <option>
////    After you have voted, run `/vote bribe confirm`
//
//    private static final Map<Integer, AtomicInteger> temporaryVotes = new ConcurrentHashMap<>();
//    private static final Map<Long, UUID> temporaryVotesToken = new ConcurrentHashMap<>();
//    private static final Map<Long, Integer> pendingBribes = new ConcurrentHashMap<>();
//
//    private void expireBribeTask(IMessageIO io, int timeoutSec, UUID expectedToken, User user) {
//        Locutus.cmd().getExecutor().schedule(new Runnable() {
//            @Override
//            public void run() {
//                cancelBribeTask(expectedToken, user);
//                if (io != null) {
//                    io.send(user.getAsMention() + " Your bribe has expired. Please try again, see TODO CM REF");
//                }
//            }
//        }, timeoutSec, TimeUnit.SECONDS);
//    }
//
//    private Integer cancelBribeTask(UUID expectedToken, User user) {
//        synchronized (temporaryVotes) {
//            UUID token = temporaryVotesToken.get(user.getIdLong());
//            Integer bribeId;
//            if (token != null && token.equals(token)) {
//                bribeId = pendingBribes.remove(user.getIdLong());
//                temporaryVotesToken.remove(user.getIdLong());
//                AtomicInteger counter = bribeId == null ? null : temporaryVotes.get(bribeId);
//                if (counter != null) {
//                    int val = counter.decrementAndGet();
//                    if (val == 0) {
//                        temporaryVotes.remove(bribeId);
//                    }
//                }
//            } else bribeId = null;
//            return bribeId;
//        }
//    }
//
//    private Integer cancelBribeTask(long userId) {
//        synchronized (temporaryVotes) {
//            Integer bribeId = pendingBribes.remove(userId);
//            if (bribeId == null) return null;
//            temporaryVotesToken.remove(userId);
//
//            AtomicInteger counter = temporaryVotes.get(bribeId);
//            if (counter != null) {
//                int val = counter.decrementAndGet();
//                if (val == 0) {
//                    temporaryVotes.remove(bribeId);
//                }
//            }
//
//            return bribeId;
//        }
//    }
//
//    @Command
//    public String takeBribe(@Me GuildDB db, @Me User user, @Me DBNation me, @Me IMessageIO io, DBBribe bribe) {
//        try {
//            UUID token = UUID.randomUUID();
//            synchronized (temporaryVotes) {
//                Integer existing = pendingBribes.get(user.getIdLong());
//                boolean isExistingSame = existing != null && existing == bribe.getId();
//
//                AtomicInteger tempVotesCounter = temporaryVotes.get(bribe.getId());
//                int tempVotesCount = tempVotesCounter == null ? 0 : tempVotesCounter.get();
//                if (isExistingSame) tempVotesCount = Math.max(0, tempVotesCount - 1);
//
//                if (bribe.getRemainingUses() - tempVotesCount <= 0) {
//                    if (tempVotesCount > 0) {
//                        return "This bribe has already been fully taken. Please try again in 60s if you wish to take it again.";
//                    }
//                    return "There are no remaining funds available for this bribe.";
//                }
//
//                if (existing != null && !isExistingSame) {
//                    DBBribe existingBribe = db.getBribe(existing);
//                    String url = existingBribe == null ? "bribe:" + existing : existingBribe.getUrl();
//                    return "You already have a pending bribe for <" + url + ">. Please confirm that bribe first using TODO CM REF, or cancel it with TODO CM REF";
//                }
//
//                pendingBribes.put(user.getIdLong(), bribe.getId());
//                temporaryVotesToken.put(user.getIdLong(), token);
//
//                expireBribeTask(io, 120, token, user);
//            }
//            // increment temporaryVotes and get new value
//            // check the bribe has remaining funds matching that
//
//            // add to pendingBribes
//
//            StringBuilder message = new StringBuilder("After reading this message, you have 120s to vote. If you are not signed in with the voting ready, please do so and run this command again.");
//        }
//        // have temporary votes
//    }
//
////
////            /vote bribe confirm
//    @Command
//    public String confirmBribe(@Me GuildDB db, @Me User user, @Me DBNation me) {
//        synchronized (temporaryVotes) {
//            Integer bribe = pendingBribes.remove(user.getIdLong());
//            if (bribe == null) {
//                return "You have no pending bribes. Use TODO CM REF to take a bribe";
//            }
//            // decrement
//            AtomicInteger votes = temporaryVotes.get(bribe);
//            if (votes != null) {
//                int val = votes.decrementAndGet();
//                if (val == 0) {
//                    temporaryVotes.remove(bribe);
//                }
//            }
//        }
//    }
//
//    @Command
//    public String cancelBribe(@Me GuildDB db, @Me User user, @Me DBNation me) {
//        Integer cancelled = cancelBribeTask(user.getIdLong());
//        if (cancelled == null) return "You have no pending bribes. Use TODO CM REF to take a bribe";
//        DBBribe bribe = db.getBribe(cancelled);
//        String url = bribe == null ? "bribe:" + cancelled : bribe.getUrl();
//        return "Bribe cancelled: <" + url + ">";
//    }
////

}
