package link.locutus.discord.gpt.pwembed;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.gpt.imps.EmbeddingAdapter;
import link.locutus.discord.gpt.imps.EmbeddingType;
import net.dv8tion.jda.api.entities.User;

import java.util.Set;

public class SettingEmbeddingAdapter extends PWAdapter<GuildSetting> {
    public SettingEmbeddingAdapter(EmbeddingSource source, Set<GuildSetting> objects) {
        super(source, objects);
    }

    @Override
    public EmbeddingType getType() {
        return EmbeddingType.Configuration;
    }

    @Override
    public String getDescription(GuildSetting obj) {
        return obj.help();
    }

    @Override
    public String getExpanded(GuildSetting obj) {
        return null;
    }

    @Override
    public boolean hasPermission(GuildSetting obj, ValueStore store, CommandManager2 manager) {
        GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class));
        User author = (User) store.getProvided(Key.of(User.class, Me.class));
        return obj.hasPermission(db, author, null);
    }
}
