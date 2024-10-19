@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@template.main(ws = ws, content = @`
<h1 class="text-primary">Hello!</h1>
    @template.auth.authinfo(ws = ws)
`, title = "Home", navbar = null)