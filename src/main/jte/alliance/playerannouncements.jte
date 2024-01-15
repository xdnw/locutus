@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.entities.announce.Announcement
@import link.locutus.discord.db.entities.DBNation
@import java.util.List

@param GuildDB db
@param DBNation me
@param List<Announcement.PlayerAnnouncement> announcements
@template.main(content = @`
@for (Announcement.PlayerAnnouncement plrAnn : announcements)
    @template.alliance.playerannouncement(db = db, plrAnn = plrAnn, showFooter = true, showReceiver = false, includeDate = true)
@endfor
`, title = "Announcements", navbar = null)
