package link.locutus.discord.commands.sync;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.Event;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.update.WarUpdateProcessor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class SyncAttacks extends Command {
    public SyncAttacks() {
        super("syncattacks", CommandCategory.DEBUG, CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.sync.attacks.cmd);
    }

    @Override
    public String help() {
        return "syncattacks";
    }

    @Override
    public String desc() {
        return "debug";
    }



    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }
    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        WarUpdateProcessor.checkActiveConflicts();
        if (args.size() == 0) {
            Locutus.imp().getWarDb().updateAttacksAndWarsV3(true, Event::post, Settings.USE_V2);
        } else if (args.size() == 1) {
            Locutus.imp().getWarDb().updateAttacksAndWarsV3(false, Event::post, Settings.USE_V2);
        } else {
            return usage();
        }
        return "Done!";
    }
}