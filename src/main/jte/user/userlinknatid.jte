@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.util.discord.DiscordUtil
@import link.locutus.discord.db.entities.DBNation

@param WebStore ws
@param int nationId
@template.user.userlink(ws = ws, userId = DiscordUtil.getUserIdByNationId(nationId), user = DiscordUtil.getUserByNationId(nationId), nation = DBNation.getById(nationId))