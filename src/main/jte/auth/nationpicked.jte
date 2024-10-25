@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.db.entities.DBNation
@import link.locutus.discord.web.commands.WM
@param WebStore ws
@param DBNation nation

@template.main(ws = ws, title = "Login via in-game Mail", content = @`
<div>
    @template.nation.nation(ws = ws, nation = nation)
</div>
<form action="${WM.page.login_mail.cmd.toPageUrl()}" method="post">
    <input type="hidden" name="nationId" value="${nation.getNation_id()}">
    <button type="submit" class="btn btn-danger m-1">Login via Mail</button>
</form>
`, navbar = null)
