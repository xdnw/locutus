@import gg.jte.Content
@import java.util.List
@import java.util.Map

@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@param String title
@param Content message
@param Content buttons
@param List<Map.Entry<String, String>> navbar

@template.main(ws = ws, title = title, navbar = navbar, content = @`
<div class="container">
    <div class="row">
        <div class="col text-center">
            ${message}
        </div>
    </div>
    <div class="row">
        <div class="col d-flex justify-content-center">
            ${buttons}
        </div>
    </div>
</div>
`)