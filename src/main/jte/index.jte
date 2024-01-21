@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@param link.locutus.discord.db.entities.DBNation nation
@template.main(ws = ws, content = @`
<h1 class="text-primary">Hello ${nation.getNation()}!</h1>
`, title = "Home", navbar = null)