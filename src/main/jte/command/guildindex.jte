@import link.locutus.discord.commands.manager.v2.binding.ValueStore
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.manager.v2.command.CommandGroup
@import link.locutus.discord.commands.manager.v2.perm.PermissionHandler

@param WebStore ws
@param PermissionHandler permHandler
@param String commandEndpoint
@param CommandGroup commands
@param String pageEndpoint
@param CommandGroup pages
@template.main(ws = ws, title = "Commands/Pages", content = @`
<script>
    $(document).ready(function(){
      $("#myInput").on("keyup", function() {
        var value = $(this).val().toLowerCase();
        $(".command").filter(function() {
          $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)
        });
      });
    });
</script>
<div class="container input-group input-group-lg">
    <input class="form-control form-control-lg" id="myInput" type="text" placeholder="Filter..">
</div>
@template.command.commandgroup(ws = ws, command = pages, subcommands = pages.getAllowedCommands(ws.store(), permHandler), endpoint = pageEndpoint)

@template.command.commandgroup(ws = ws, command = commands, subcommands = commands.getAllowedCommands(ws.store(), permHandler), endpoint = commandEndpoint)
`, navbar = null)


