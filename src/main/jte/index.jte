@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@param String message
@template.main(ws = ws, content = @`
<h1 class="text-primary">Hello ${message}!</h1>
`, title = "Home", navbar = null)