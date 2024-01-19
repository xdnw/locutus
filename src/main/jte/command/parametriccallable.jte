@import gg.jte.Content
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.manager.v2.command.CommandGroup
@import java.util.*
@import link.locutus.discord.commands.manager.v2.binding.ValueStore
@import link.locutus.discord.commands.manager.v2.command.ParametricCallable
@import link.locutus.discord.util.MarkupUtil
@param WebStore ws
@param ParametricCallable command
@param Content form
@template.main(ws = ws, title = "Command: " + command.getPrimaryCommandId(), content = @`
<div class="container-fluid rounded shadow bg-lightp-1">
@if(command.getParent() != null)
<a href="">Go back ${command.getParent().getPrimaryCommandId()}</a>
@endif
@if(command.help(ws.store()) != null)
<h2>Help</h2>
<p>$unsafe{MarkupUtil.markdownToHTML(command.help(ws.store()))}</p>
@endif
@if(command.simpleDesc() != null && !command.simpleDesc().isEmpty())
<h2>Description</h2>
<p>$unsafe{MarkupUtil.markdownToHTML(command.simpleDesc())}</p>
@endif
<h2>Execute</h2>
${form}
<div style="width:100%;height:80%;margin-top:10px" id="output" class=""></div>
</div>
`, navbar = null)