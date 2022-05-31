package com.boydti.discord.commands.external.guild;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.entities.NationMeta;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public class Meta extends Command {
    public Meta() {
        super("NationMeta", "Meta", CommandCategory.USER_INFO, CommandCategory.INTERNAL_AFFAIRS, CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public String help() {
        return super.help() + " <nation> <key>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.INTERNAL_AFFAIRS.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) {
            return usage(event);
        }

        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation == null) return "Invalid nation: `" + args.get(0) + "`";

        NationMeta meta;
        try {
            meta = NationMeta.valueOf(args.get(1).toUpperCase());
        } catch (IllegalArgumentException e) {
            return usage(event, "Keys: " + StringMan.getString(NationMeta.values()));
        }

        ByteBuffer buf = nation.getMeta(meta);
        if (buf == null) return "No value set";

        byte[] arr = new byte[buf.remaining()];
        buf.get(arr);
        buf = ByteBuffer.wrap(arr);

        switch (arr.length) {
            case 0:
                return "" + (buf.get() & 0xFF);
            case 4:
                return "" + (buf.getInt());
            case 8:
                ByteBuffer buf2 = ByteBuffer.wrap(arr);
                return buf.getLong() + "/" + MathMan.format(buf2.getDouble());
            default:
                return new String(arr, StandardCharsets.ISO_8859_1);
        }
    }
}
