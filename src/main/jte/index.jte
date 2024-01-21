@import link.locutus.discord.config.Settings
@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@param link.locutus.discord.db.entities.DBNation nation
@template.main(ws = ws, content = @`
    <div class="container">
        <div class="row">
            <h1>Locutus</h1>
            <div class="btn-group" style="margin-bottom: 20px;" role="group"  aria-label="Basic example">
                <a class="btn btn-outline-primary" href="https://discord.com/api/oauth2/authorize?client_id=${Settings.INSTANCE.APPLICATION_ID}&permissions=395606879321&scope=bot">Invite Locutus</a>
                <a class="btn btn-outline-primary" href="/page/guildselect">Select Guild</a>
            </div>
            <h1>Nation</h1>
            <div class="btn-group" style="margin-bottom: 20px;" role="group"  aria-label="Basic example">
                <a class="btn btn-outline-primary" href="https://politicsandwar.com/nation/id=${nation.getNation_id()}" target="_blank">Your Nation</a>
                <a class="btn btn-outline-primary" href="https://politicsandwar.com/nation/id=${nation.getNation_id()}&display=trade" target="_blank">Your Trades</a>
                <a class="btn btn-outline-primary" href="https://politicsandwar.com/nation/war/" target="_blank">Your Wars</a>

            </div>
            <h1>Alliance</h1>
            <div class="btn-group" style="margin-bottom: 20px;" role="group"  aria-label="Basic example">
                <a class="btn btn-outline-primary" href="https://politicsandwar.com/alliance/id=${nation.getAlliance_id()}" target="_blank">Your Alliance</a>
                <a class="btn btn-outline-primary" href="https://politicsandwar.com/index.php?id=15&keyword=${nation.getAllianceName()}&cat=alliance&ob=score&od=DESC&maximum=15&minimum=0&search=Go&memberview=true" target="_blank">Members</a>
                <a class="btn btn-outline-primary" href="https://politicsandwar.com/alliance/id=${nation.getAlliance_id()}&display=war" target="_blank">Alliance Wars</a>
            </div>
        </div>
    </div>
`, title = "Home", navbar = null)