@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.db.entities.DBNation
@param WebStore ws
@param DBNation nation
@template.main(title = "nation", navbar = null, ws = ws, content = @`
hello world
    ${nation.getNation()}
`)