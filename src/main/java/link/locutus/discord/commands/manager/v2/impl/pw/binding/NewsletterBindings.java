package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.newsletter.Newsletter;
import link.locutus.discord.db.entities.newsletter.NewsletterManager;
import link.locutus.discord.util.StringMan;

public class NewsletterBindings extends BindingHelper {
    @Binding
    public NewsletterManager manager(@Me GuildDB db) {
        return db.getNewsletterManager();
    }
    @Binding
    public Newsletter newsletter(@Me GuildDB db, String input) {
        Newsletter newsletter = db.getNewsletterManager().getNewsletter(input);
        if (newsletter == null) {
            String[] options = db.getNewsletterManager().getNewsletters().values().stream().map(Newsletter::getName).toArray(String[]::new);
            throw new IllegalArgumentException("No newsletter found with name `" + input + "`. Options: " + StringMan.getString(options));
        } else {
            return newsletter;
        }
    }
}
