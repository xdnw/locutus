@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@template.main(ws = ws, title = "Unregister", navbar = null, content = @`
You are already registered. Do you want to unregister, or logout?
    <a href="/page/unregister" class="btn btn-danger">Unregister</a>
    <a href="/page/logout" class="btn btn-danger">Logout</a>
`)