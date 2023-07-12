package link.locutus.discord.gpt.pwembed;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.gpt.imps.EmbeddingType;
import link.locutus.discord.gpt.GptHandler;
import net.dv8tion.jda.api.entities.User;

public class SettingEmbedding extends PWEmbedding<GuildSetting> {
    public SettingEmbedding(GuildSetting obj) {
        super(EmbeddingType.Configuration, obj.name().replace("_", " "), obj, false);
    }

    @Override
    public String apply(String query, GptHandler handler) {
        return null;
    }

    @Override
    public String getContent() {
        return getObj().help();
    }

    @Override
    public boolean hasPermission(ValueStore store, CommandManager2 manager) {
        GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class));
        User author = (User) store.getProvided(Key.of(User.class, Me.class));
        return getObj().hasPermission(db, author, null);
    }
}
