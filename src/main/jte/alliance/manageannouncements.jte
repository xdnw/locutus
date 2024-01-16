@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.entities.announce.Announcement
@import link.locutus.discord.db.entities.DBNation
@import java.util.List
@import java.util.Map

@param WebStore ws
@param GuildDB db
@param List<Announcement> announcements

@template.main(ws = ws, content = @`
@for (Announcement announcement : announcements)
@template.alliance.announcement(ws = ws, db = db, announcement = announcement)
@endfor
`, title = "Manage Announcements", navbar = null)