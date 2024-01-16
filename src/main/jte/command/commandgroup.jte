@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.manager.v2.command.CommandCallable
@import link.locutus.discord.commands.manager.v2.command.CommandGroup
@import java.util.*
@import link.locutus.discord.commands.manager.v2.binding.ValueStore
@import link.locutus.discord.util.StringMan

@param WebStore ws
@param CommandGroup command
@param Map<String, CommandCallable> subcommands
@param String endpoint

@if(command.getParent() != null)
    <div class="alert alert-light">
    <a href="..">Go back ${command.getParent().getPrimaryCommandId()}</a>
    </div>
@endif
<div class="bg-white container mt-3 rounded shadow py-1">
@if(command.help(ws.store()) != null)
<h2>Help</h2>
<p>${command.help(ws.store())}</p>
@endif

@if(command.desc(ws.store()) != null)
<h2>Description</h2>
<p>${command.desc(ws.store())}</p>
@endif
<h2>Subcommands</h2>
<ul class="list-group">
    @for (Map.Entry<String, CommandCallable> entry : subcommands.entrySet())
        <li class="command list-group-item">
            <b><a href="${endpoint}${entry.getKey()}">${entry.getKey()}</a></b><br>
            <p>
                Aliases: ${StringMan.getString(entry.getValue().aliases())}
            </p>
            <p>
                Help: ${entry.getValue().simpleDesc()}
            </p>
        </li>
    @endfor
</ul>
</div>