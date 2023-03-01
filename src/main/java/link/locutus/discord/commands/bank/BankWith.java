package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AllianceMeta;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.JsonUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BankWith extends Command {
    boolean disabled = false;
    public BankWith() {
        super("transfer", "withdraw", CommandCategory.ECON);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "transfer <alliance|nation> <resource> <amount>";
    }

    @Override
    public String desc() {
        return "withdraw from the alliance bank\n" +
                "Use `-f` to bypass all checks\n" +
                "Use `-o` to subtract their existing funds from the transfer amount";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        if (Roles.ECON.hasOnRoot(user)) return true;
        if (!Roles.MEMBER.has(user, server)) return false;
        GuildDB db = Locutus.imp().getGuildDB(server);
        return db.getOffshore() != null;
    }

    public static final Set<UUID> authorized = new HashSet<>();

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 3) return usage();
        /*
        @Me IMessageIO channel,
                           @Me User author, @Me DBNation me, @Me GuildDB guildDb, NationOrAlliance receiver, @AllianceDepositLimit Map<ResourceType, Double> transfer, DepositType depositType,

                           @Switch("n") DBNation depositsAccount,
                           @Switch("a") DBAlliance useAllianceBank,
                           @Switch("o") DBAlliance useOffshoreAccount,

                           @Switch("m") boolean onlyMissingFunds,
                           @Switch("e") @Timediff Long expire,
                           @Switch("g") UUID token,
                           @Switch("c") boolean convertCash,
                           @Switch("f") boolean force
         */
        IMessageIO channel = new DiscordChannelIO(event.getChannel());
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        NationOrAlliance receiver = PWBindings.nationOrAlliance(args.get(0));
        Map<ResourceType, Double> transfer = PnwUtil.parseResources(args.get(1));
        DepositType depositType = PWBindings.DepositType(args.get(2));

        boolean onlyMissingFunds = flags.contains('o');
        boolean convertCash = flags.contains('c');
        boolean force = flags.contains('f');

        Long expire = null;
        UUID token = null;
        JSONObject command = null;
        // token
        // nationAccount
        // allianceAccount
        // senderAlliance

        return BankCommands.transfer(channel, command, author, me, guildDb, receiver, transfer, depositType, null, null, null, onlyMissingFunds, expire, token, convertCash, force);
    }
}
