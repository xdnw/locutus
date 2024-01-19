@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@param String title
@param String message
@template.main(ws = ws, title = title, navbar = null, content = @`
<h1 class="text-primary">${title}</h1>
<p>${message}</p>
`)
