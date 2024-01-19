@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.entities.announce.Announcement
@import link.locutus.discord.db.entities.DBNation
@import java.util.List
@param WebStore ws
@param GuildDB db
@param DBNation me
@param List<Announcement.PlayerAnnouncement> announcements
@template.main(ws = ws, content = @`
@for (Announcement.PlayerAnnouncement plrAnn : announcements)
    @template.alliance.playerannouncement(ws = ws, db = db, plrAnn = plrAnn, showFooter = true, showReceiver = false, includeDate = true)
@endfor
`, title = "Announcements", navbar = null)
