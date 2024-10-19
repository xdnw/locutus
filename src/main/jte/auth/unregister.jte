@param link.locutus.discord.commands.manager.v2.binding.WebStore ws

@template.messagebuttons(ws = ws, title = "Unregister", message = @`
<div class="d-flex justify-content-center">
    @template.auth.authinfo(ws = ws)
</div>
    You are already registered. If you would like to unregister your account, or logout, please click the appropriate button below.
`, buttons = @`
    <form action="/page/unregister" method="POST" style="display: inline;">
        <button type="submit" class="btn btn-danger m-1">Unregister</button>
    </form>
    <form action="/page/logout" method="POST" style="display: inline;">
        <button type="submit" class="btn btn-danger m-1">Logout</button>
    </form>
`, navbar = null)