@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.util.discord.DiscordUtil

@param WebStore ws
@param long userId
@template.user.userlink(ws = ws, userId = userId, user = DiscordUtil.getUser(userId), nation = DiscordUtil.getNation(userId))
