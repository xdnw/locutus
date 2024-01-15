@import link.locutus.discord.commands.manager.v2.binding.ValueStore
@import link.locutus.discord.commands.manager.v2.command.CommandGroup
@import link.locutus.discord.commands.manager.v2.perm.PermissionHandler

@param ValueStore store
@param PermissionHandler permHandler
@param String commandEndpoint
@param CommandGroup commands
@param String pageEndpoint
@param CommandGroup pages
@template.main(title = "Commands/Pages", content = @`
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
@template.command.commandgroup(store = store, command = pages, subcommands = pages.getAllowedCommands(store, permHandler), endpoint = pageEndpoint)

@template.command.commandgroup(store = store, command = commands, subcommands = commands.getAllowedCommands(store, permHandler), endpoint = commandEndpoint)
`, navbar = null)


