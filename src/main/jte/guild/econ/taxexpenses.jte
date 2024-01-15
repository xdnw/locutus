@import link.locutus.discord.Locutus
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
@import link.locutus.discord.util.PnwUtil
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
@import java.util.ArrayList
@import java.util.HashMap

@param GuildDB db
@param Set<Integer> alliances
@param boolean requireGrant
@param boolean requireExpiry
@param boolean requireTagged
@param Map<Integer, TaxBracket> brackets
@param List<BankDB.TaxDeposit> taxes
@param Map<Integer, TaxBracket> bracketsByNation
@param Map<Integer, List<DBNation>> nationsByBracket
@param List<DBNation> nations
@param Map<Integer, Map<Integer, Integer>> bracketToNationDepositCount
@param Map<Integer, Integer> allNationDepositCount
@param double[] incomeTotal
@param Map<Integer,double[]> incomeByBracket
@param Map<Integer,double[]> incomeByNation
@param Map<Integer, Map<Integer,double[]>> incomeByNationByBracket
@param Map<Integer, List<Transaction2>> transactionsByNation
@param Map<Integer, List<Transaction2>> transactionsByBracket
@param Map<Integer, Map<Integer, List<Transaction2>>> transactionsByNationByBracket
@param List<Transaction2> expenseTransfers
@param double[] expenseTotal
@param Map<Integer,double[]> expensesByBracket
@param Map<Integer,double[]> expensesByNation
@param Map<Integer, Map<Integer,double[]>> expensesByNationByBracket

@template.main(title = "Tax Expenses", navbar = null, content = @`
<div class="bg-white container mt-3 rounded shadow py-1">
@if(requireGrant)
    <kbd>requireGrant</kbd>
@endif
@if(requireExpiry)
    <kbd>requireExpiry</kbd>
@endif
@if(requireTagged)
    <kbd>requireTagged</kbd>
@endif
<kbd>#${taxes.size()} tax records</kbd>
<h2>Alliances:</h2>
<ul class="list-inline">
@for (int allianceId : alliances)
    <li class="list-inline-item"><a class="btn btn-sm border rounded" href="https://politicsandwar.com/alliance/id=${allianceId}">${PnwUtil.getName(allianceId, true)}</a></li>
@endfor
</ul>
</div>

<!--Total-->
@template.guild.econ.taxexpensebracket(-1, "Total Tax", db, null,
bracketsByNation, nations,
allNationDepositCount,
incomeTotal,
incomeByNation,
transactionsByNation,
expenseTransfers,
expenseTotal,
expensesByNation
)

@for (Map.Entry<Integer, TaxBracket> entry : brackets.entrySet())
    @if(expensesByBracket.get(entry.getKey()) != null || incomeByBracket.get(entry.getKey()) != null)
        @template.guild.econ.taxexpensebracket(entry.getKey(), "Bracket", db, entry.getValue(),
                bracketsByNation, nationsByBracket.getOrDefault(entry.getKey(), new ArrayList<>()),
                bracketToNationDepositCount.getOrDefault(entry.getKey(), new HashMap<>()),
                incomeByBracket.getOrDefault(entry.getKey(), ResourceType.getBuffer()),
                incomeByNationByBracket.getOrDefault(entry.getKey(), new HashMap<>()),
                transactionsByNationByBracket.getOrDefault(entry.getKey(), new HashMap<>()),
                transactionsByBracket.getOrDefault(entry.getKey(), new ArrayList<>()),
                expensesByBracket.getOrDefault(entry.getKey(), ResourceType.getBuffer()),
                expensesByNationByBracket.getOrDefault(entry.getKey(), new HashMap<>())
        )
    @endif
@endfor
`)