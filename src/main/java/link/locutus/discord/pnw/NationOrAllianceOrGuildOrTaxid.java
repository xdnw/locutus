package link.locutus.discord.pnw;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.command.shrink.IShrink;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface NationOrAllianceOrGuildOrTaxid {

    default IShrink toShrink() {
        return toShrink(1);
    }
    default IShrink toShrink(int priority) {
        return IShrink.of(getName(), getMarkdownUrl(), priority);
    }

    default int getId() {
        return (int) getIdLong();
    }

    default long getIdLong() {
        return getId();
    }

    default int getReceiverType() {
        if (isNation()) return 1;
        if (isAlliance()) return 2;
        if (isGuild()) return 3;
        if (isTaxid()) return 4;
        throw new IllegalArgumentException("Invalid state: " + this);
    }

    default String getQualifiedId() {
        return getTypePrefix() + ":" + getIdLong();
    }

    default String getQualifiedName() {
        String name = getName();
        return getTypePrefix() + ":" + (name == null || name.isEmpty() ? getIdLong() : name);
    }

    default String getTypePrefix() {
        if (isNation()) return "Nation";
        if (isAlliance()) return "AA";
        if (isGuild()) return "Guild";
        if (isTaxid()) return "tax_id";
        throw new IllegalArgumentException("Invalid state: " + this);
    }

    default boolean isTaxid() {
        return this instanceof TaxBracket;
    }

    default boolean isAlliance() {
        return this instanceof DBAlliance;
    }

    default int getAlliance_id() {
        return getId();
    }

    String getName();

    default Map.Entry<Long, Integer> getTransferIdAndType() {
        long sender_id;
        int sender_type;
        if (isGuild()) {
            sender_id = getIdLong();
            sender_type = 3;
        } else if (isAlliance()) {
            sender_id = getAlliance_id();
            sender_type = 2;
        } else if (isNation()) {
            sender_id = getId();
            sender_type = 1;
        } else if (isTaxid()) {
            sender_id = getId();
            sender_type = 4;
        } else throw new IllegalArgumentException("Invalid receiver: " + this);
        return new AbstractMap.SimpleEntry<>(sender_id, sender_type);
    }

    default boolean isGuild() {
        return this instanceof GuildDB;
    }

    default DBAlliance asAlliance() {
        return (DBAlliance) this;
    }

    default boolean isNation() {
        return this instanceof DBNation;
    }

    default Set<DBNation> getMemberDBNations() {
        Set<DBNation> nations = new HashSet<>();

        if (isNation()) nations.add(asNation());
        else if (isGuild()) {
            GuildDB db = asGuild();
            AllianceList aaList = db.getAllianceList();
            if (aaList != null && !aaList.isEmpty()) {
                nations.addAll(aaList.getNations(true, 0, true));
            } else {
                Set<Member> membersWithRole = Roles.MEMBER.getAll(db);
                for (Member member : membersWithRole) {
                    DBNation nation = DiscordUtil.getNation(member.getUser());
                    if (nation != null) {
                        nations.add(nation);
                    }
                }
            }
        }
        else if (isAlliance()) {
            nations.addAll(asAlliance().getNations(true, 0, true));
        } else {
            throw new IllegalArgumentException("Unknwon type " + getIdLong());
        }
        return nations;
    }

    default Set<DBNation> getDBNations() {
        Set<DBNation> nations = new HashSet<>();

        if (isNation()) nations.add(asNation());
        else if (isGuild()) {
            GuildDB db = asGuild();
            AllianceList aaList = db.getAllianceList();
            if (aaList == null && !aaList.isEmpty()) {
                for (Member member : db.getGuild().getMembers()) {
                    DBNation nation = DiscordUtil.getNation(member.getUser());
                    if (nation != null) {
                        nations.add(nation);
                    }
                }
            }
            return aaList.getNations();
        }
        else if (isAlliance()) {
            nations.addAll(asAlliance().getNations(false, 0, false));
        } else if (isTaxid()) {
            return asBracket().getNations();
        } else {
            throw new IllegalArgumentException("Unknwon type " + getIdLong());
        }
        return nations;
    }

    default DBNation asNation() {
        return (DBNation) this;
    }

    default GuildDB asGuild() {
        return (GuildDB) this;
    }

    default TaxBracket asBracket() {
        return (TaxBracket) this;
    }

    String getUrl();

    @Command(desc = "Get the sheet url for this")
    default String getSheetUrl() {
        if (isGuild()) return asGuild().getGuild().toString();
        return MarkupUtil.sheetUrl(getName(), getUrl());
    }

    @Command(desc = "Get the markdown url for this")
    default String getMarkdownUrl() {
        if (isGuild()) return asGuild().getGuild().toString();
        return MarkupUtil.markdownUrl(getName(), getUrl());
    }
}
