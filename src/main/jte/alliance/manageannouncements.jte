@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.entities.announce.Announcement
@import link.locutus.discord.db.entities.DBNation
@import java.util.List
@import java.util.Map

@param GuildDB db
@param List<Announcement> announcements

@template.main(content = @`
@for (Announcement announcement : announcements)
@template.alliance.announcement(db = db, announcement = announcement)
@endfor
`, title = "Manage Announcements", navbar = null)