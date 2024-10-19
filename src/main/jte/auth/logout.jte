@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@param link.locutus.discord.web.commands.binding.DBAuthRecord auth
@template.messagebuttons(ws = ws, title = "Logout", message = @`
<div class="d-flex justify-content-center">
    @template.auth.authinfo(ws = ws)
</div>
To logout of your account please click the button below.
`, buttons = @`
<form action="/page/logout" method="POST" style="display: inline;">
    <button type="submit" class="btn btn-danger m-1">Logout</button>
</form>
`, navbar = null)