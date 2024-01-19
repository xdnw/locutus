@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.user.Roles
@import link.locutus.discord.db.GuildDB
@import net.dv8tion.jda.api.entities.Guild
@import net.dv8tion.jda.api.entities.User
@import link.locutus.discord.db.entities.DBAlliance
@param WebStore ws
@param GuildDB db
@param Guild guild
@param DBAlliance alliance
@param User user
@template.main(ws = ws, content = @`
    <a class="m-1 btn btn-primary btn-lg" href="allianceleaves/${alliance.getId()}">View Rank Changes</a>
    <a class="m-1 btn btn-primary btn-lg" href="alliancewars/${alliance.getId()}">View Rank Changes</a>
`, title = alliance.getName() + " Alliance Index", navbar = null)