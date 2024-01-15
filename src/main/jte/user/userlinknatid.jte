@import link.locutus.discord.util.discord.DiscordUtil
@import link.locutus.discord.db.entities.DBNation

@param int nationId
@template.user.userlink(userId = DiscordUtil.getUserIdByNationId(nationId), user = DiscordUtil.getUserByNationId(nationId), nation = DBNation.getById(nationId))