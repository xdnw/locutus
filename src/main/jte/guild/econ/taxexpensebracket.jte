@import link.locutus.discord.Locutus
@import java.util.ArrayList
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.manager.v2.binding.annotation.Command
@import link.locutus.discord.commands.manager.v2.binding.annotation.Me
@import link.locutus.discord.commands.manager.v2.binding.annotation.Switch
@import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp
@import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance
@import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission
@import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission
@import link.locutus.discord.db.BankDB
@import link.locutus.discord.db.GuildDB
@import link.locutus.discord.db.entities.Coalition
@import link.locutus.discord.db.entities.TaxBracket
@import link.locutus.discord.util.PW
@import link.locutus.discord.db.entities.Transaction2
@import link.locutus.discord.db.entities.DBNation
@import link.locutus.discord.user.Roles
@import link.locutus.discord.util.MathMan
@import link.locutus.discord.util.TimeUtil
@import link.locutus.discord.util.math.ArrayUtil
@import link.locutus.discord.util.offshore.Auth
@import link.locutus.discord.util.offshore.test.IACategory
@import link.locutus.discord.apiv1.enums.ResourceType
@import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
@import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
@import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
@import net.dv8tion.jda.api.entities.Guild
@import java.util.List
@import java.util.Map
@import java.util.Set
@import java.util.UUID

@param WebStore ws
@param Object id
@param String title
@param GuildDB db
@param TaxBracket bracket
@param Map<Integer, TaxBracket> bracketsByNation
@param List<DBNation> nationsByBracket
@param Map<Integer, Integer> bracketToNationDepositCount
@param double[] income
@param Map<Integer,double[]> incomeByNation
@param Map<Integer, List<Transaction2>> transactionsByNation
@param List<Transaction2> transactions
@param double[] expense
@param Map<Integer,double[]> expensesByNation

<div class="bg-lightcontainer mt-3 rounded shadow py-1">
    @if(bracket != null)
        <h3><a href="https://politicsandwar.com/index.php?id=15&tax_id=${bracket.taxId}">${title} #${bracket.taxId} ${bracket.getName()}</a></h3>
        <p>Taxrate: ${bracket.moneyRate}/${bracket.rssRate}</p><br>
    @else
        <h3>${title}</h3>
    @endif
    <h4>Bracket Income/Expense breakdown</h4>
    @template.guild.econ.taxexpensesbreakdown(ws = ws, id = id, a = income, b = expense, showByResource = true)
    @if(!nationsByBracket.isEmpty())
    <a class="btn btn-primary btn-sm" data-bs-toggle="collapse" href="#collapseNations${id + ""}" role="button" aria-expanded="false" aria-controls="collapseNations${id + ""}">
        Show ${nationsByBracket.size()} nations
    </a>
    <div class="collapse" id="collapseNations${id + ""}">
        <div class="card card-body">
        <table class="table">
            <thead>
                <th>Nation</th>
                <th>Cities</th>
                <th>Off/Def</th>
                <th>Timers[City/Project]</th>
                <th>Project Slots</th>
                <th>mmr[unit]</th>
                <th>mmr[build]</th>
                <th>avg_infra</th>
                <th>avg_land</th>
                <th>action</th>
            </thead>
            <tbody>
                @for (DBNation nation : nationsByBracket)
                <tr>
                    <td><a href="${nation.getUrl()}">${nation.getNation()}</a></td>
                    <td>${nation.getCities()}</td>
                    <td>${nation.getOff()}/${nation.getDef()}</td>
                    @if(nation.getPosition() <= 1)
                    <td colspan="100" class="bg-warning text-center text-bold">nation is applicant</td>
                    @elseif (nation.getVm_turns() > 0)
                    <td colspan="100" class="bg-warning text-center text-bold">nation is VM</td>
                    @elseif(nation.active_m() > 7200)
                    <td colspan="100" class="bg-warning text-center text-bold">nation is inactive</td>
                    @elseif(nation.isGray())
                    <td colspan="100" class="bg-warning text-center text-bold">nation is gray (untaxable)</td>
                    @elseif(nation.isBeige())
                    <td colspan="100" class="bg-warning text-center text-bold">nation is beige (untaxable)</td>
                    @else
                    <td>${nation.getCityTurns()}/${nation.getProjectTurns()}</td>
                    <td>${nation.getNumProjects()}/${nation.projectSlots()}</td>
                    <td>${nation.getMMR()}</td>
                    <td>${nation.getMMRBuildingStr()}</td>
                    <td>${nation.getAvg_infra()}</td>
                    <td>${(int) nation.getAvgLand()}</td>
                    <td>
                        @if(incomeByNation.containsKey(nation.getNation_id()) || expensesByNation.containsKey(nation.getNation_id()))
                        <a class="btn btn-secondary btn-sm" data-bs-toggle="collapse" href="#collapseInfo${id + ""}${nation.getNation_id()}" role="button" aria-expanded="false" aria-controls="collapseInfo${id + ""}${nation.getNation_id()}">
                            more info
                        </a>
                        @endif
                    </td>
                </tr>
                <tr colspan="100" class="collapse border border-secondary rounded" id="collapseInfo${id + ""}${nation.getNation_id()}">
                    @if(incomeByNation.containsKey(nation.getNation_id()) || expensesByNation.containsKey(nation.getNation_id()))
                    <td colspan="100">
                        @if(bracket == null)
                        @if(bracketsByNation.containsKey(nation.getNation_id()))
                        <b>Current taxrate: </b> ${bracketsByNation.get(nation.getNation_id()).taxId}<br>
                        @else
                        <b>Unknown taxrate</b><br>
                        @endif
                        @endif
                        <b># tax records (turns): </b> ${bracketToNationDepositCount.getOrDefault(nation.getNation_id(), 0)}<br>
                        <b># transactions (AA -&gt; nation): </b> ${transactionsByNation.getOrDefault(nation.getNation_id(), new ArrayList<>()).size()}<br>
                        <hr>
                        <b>nation income/expense breakdown:</b>
                        @template.guild.econ.taxexpensesbreakdown(ws = ws, id = id + "" + nation.getNation_id(), a = incomeByNation.computeIfAbsent(nation.getNation_id(), f -> ResourceType.getBuffer()), b = expensesByNation.computeIfAbsent(nation.getNation_id(), f -> ResourceType.getBuffer()), showByResource = true)
                        @if(transactionsByNation.containsKey(nation.getNation_id()))
                        <hr>
                        <a class="btn btn-primary btn-sm" data-bs-toggle="collapse" href="#collapseTX${id + ""}${nation.getNation_id()}" role="button" aria-expanded="false" aria-controls="collapseTX${id + ""}${nation.getNation_id()}">
                            Show transactions
                        </a>
                        <div class="collapse border border-secondary rounded" id="collapseTX${id + ""}${nation.getNation_id()}">
                            <table class="table card card-body">
                                @for (Transaction2 transfer : transactionsByNation.get(nation.getNation_id()))
                                <tr>
                                    <td>${transfer.toSimpleString()}</td>
                                </tr>
                                @endfor
                            </table>
                        </div>
                        @endif
                    </td>
                        @endif
                    @endif
                </tr>
                @endfor
            </tbody>
        </table>
        </div>
    </div>
    @endif

</div>