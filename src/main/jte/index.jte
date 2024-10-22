@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@template.main(ws = ws, content = @`
<h1 class="text-primary">Welcome to Locutus Web!</h1>
    @template.auth.authinfo(ws = ws)
    <h4>See here</h4>
    <ul>
        <li><a href="/page/">View Pages</a></li>
        <li><a href="/command/">View Commands</a></li>
    </ul>
`, title = "Home", navbar = null)