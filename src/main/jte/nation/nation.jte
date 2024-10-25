@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.db.entities.DBNation
@param WebStore ws
@param DBNation nation
<table class="table table-striped table-bordered table-responsive table-sm" style="width: auto;">
    <tr>
        <td>Nation</td>
        <td><a href="https://politicsandwar.com/nation/id=${nation.getNation_id()}">${nation.getName()}</a></td>
    </tr>
    <tr>
        <td>Alliance</td>
        <td>
            @if (nation.getAlliance_id() == 0)
            None
            @else
            <a href="https://politicsandwar.com/alliance/id=${nation.getAlliance_id()}">${nation.getAllianceName()}</a>
            @endif
        </td>
    </tr>
</table>