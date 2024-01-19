@import gg.jte.Content
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@param WebStore ws
@param String type
@param Content content
@template.main(ws = ws, title = "Alert", navbar = null, content = @`
<div class="alert p-1 my-1 alert-${type}" role="alert">
    ${content}
</div>
`)