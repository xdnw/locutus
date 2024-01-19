@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@param String title
@param String endpoint
@template.main(ws = ws, title = title, navbar = null, content = @`
    @template.data.timechartendpoint(ws = ws, title = title, endpoint = endpoint)
`)