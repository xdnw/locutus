package link.locutus.discord.commands.fun;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandManager;
import link.locutus.discord.config.Settings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DaxxCommand extends Command {
    private final CommandManager manager;
    private List<String> c0,c1,c2,c3;

    public DaxxCommand(CommandManager manager) {
        super("daxx");
        this.manager = manager;
        this.c0 = Arrays.asList("You are a %s.", "You %s!", "Why you little %s!!!1", "Yo mamma is a %s.", "Look peasants, I found the %s!", "%s has more moxie than thou", "Is.. is that a... %s?");
        this.c3 = Arrays.asList("apple-john", "baggage", "barnacle", "bladder", "boar-pig", "bugbear", "bum-bailey", "canker-blossom", "clack-dish", "clotpole", "coxcomb", "codpiece", "death-token", "dewberry", "flap-dragon", "flax-wench", "flirt-gill", "foot-licker", "fustilarian", "giglet", "gudgeon", "haggard", "harpy", "hedge-pig", "horn-beast", "hugger-mugger", "joithead", "lewdster", "lout", "maggot-pie", "malt-worm", "mammet", "measle", "minnow", "miscreant", "moldwarp", "mumble-news", "nut-hook", "pigeon-egg", "pignut", "puttock", "pumpion", "ratsbane", "scut", "skainsmate", "strumpet", "varlot", "vassal", "whey-face", "wagtail");
        this.c2 = Arrays.asList("base-court", "bat-fowling", "beef-witted", "beetle-headed", "boil-brained", "clapper-clawed", "clay-brained", "common-kissing", "crook-pated", "dismal-dreaming", "dizzy-eyed", "doghearted", "dread-bolted", "earth-vexing", "elf-skinned", "fat-kidneyed", "fen-sucked", "flap-mouthed", "fly-bitten", "folly-fallen", "fool-born", "full-gorged", "guts-griping", "half-faced", "hasty-witted", "hedge-born", "hell-hated", "idle-headed", "ill-breeding", "ill-nurtured", "knotty-pated", "milk-livered", "motley-minded", "onion-eyed", "plume-plucked", "pottle-deep", "pox-marked", "reeling-ripe", "rough-hewn", "rude-growing", "rump-fed", "shard-borne", "sheep-biting", "spur-galled", "swag-bellied", "tardy-gaited", "tickle-brained", "toad-spotted", "unchin-snouted", "weather-bitten");
        this.c1 = Arrays.asList("artless", "bawdy", "beslubbering", "bootless", "churlish", "cockered", "clouted", "craven", "currish", "dankish", "dissembling", "droning", "errant", "fawning", "fobbing", "froward", "frothy", "gleeking", "goatish", "gorbellied", "impertinent", "infectious", "jarring", "loggerheaded", "lumpish", "mammering", "mangled", "mewling", "paunchy", "pribbling", "puking", "puny", "qualling", "rank", "reeky", "roguish", "ruttish", "saucy", "spleeny", "spongy", "surly", "tottering", "unmuzzled", "vain", "venomed", "villainous", "warped", "wayward", "weedy", "yeasty");
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "daxx";
    }

    @Override
    public String desc() {
        return "daxx ";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String insult = c1.get(r.nextInt(c1.size())) + " " + c2.get(r.nextInt(c2.size())) + " " + c3.get(r.nextInt(c3.size()));
        String form = c0.get(r.nextInt(c0.size()));
        return String.format(form, insult);
    }
}
