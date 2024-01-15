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
@import link.locutus.discord.db.entities.TaxRecordCategorizer2

@param String title
@param GuildDB db
@param long start
@param long end
@param TaxRecordCategorizer2 categorized
@param Map<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> categorizedByTurnByBracket
@param Map<Integer, TaxBracket> brackets

@template.main(title = title, content = @`
@for (Map.Entry<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> entry : categorizedByTurnByBracket.entrySet())
<div class="bg-white container mt-3 rounded shadow py-1">
    @if(entry.getKey() == -1)
        <h3>Total</h3>
    @else
        <h3><a href="${brackets.get(entry.getKey()).getUrl()}">Bracket: ${brackets.get(entry.getKey()).getName()} - #${entry.getKey()}</a></h3>
        <p>
            Taxrate: ${brackets.get(entry.getKey()).moneyRate}/${brackets.get(entry.getKey()).rssRate}<br>
            Nations: ${brackets.get(entry.getKey()).getNations().size()}<br>
        </p>
    @endif
    @template.data.timechartdatasrc(title = "",
        data = categorized.createTable("", entry.getValue(), null).convertTurnsToEpochSeconds(start).toHtmlJson(),
    time = true)

    @if(entry.getKey() == -1)
    <div class="bg-white mt-3 rounded shadow py-1 searchable accordion" id="Accordion${entry.getKey()}">
        <div class="accordion-item">
            <div class="accordion-header" id="heading${entry.getKey()}">
                <button class="accordion-button collapsed p-1 btn-lg" type="button" data-bs-toggle="collapse" data-bs-target="#collapse${entry.getKey()}" aria-expanded="false" aria-controls="collapse${entry.getKey()}">
                    <h3>Show by resource</h3>
                </button>
            </div>
            <div id="collapse${entry.getKey()}" class="accordion-collapse collapse" aria-labelledby="heading${entry.getKey()}" data-bs-parent="#Accordion${entry.getKey()}">
                <div class="accordion-body bg-light">
                    @for (ResourceType type : ResourceType.values) {
                        @if(type != ResourceType.CREDITS) {
                            <div class="bg-light border border-3 border-secondary rounded">
                                @template.data.timechartdatasrc(title = type.name(),
                                data = categorized.createTable(type.name(), entry.getValue(),
                                type).convertTurnsToEpochSeconds(start).toHtmlJson(),
                                time = true)
                            </div>
                            <hr>
                        @endif
                    @endfor
                </div>
            </div>
        </div>
    </div>
    @endif
</div>
<br>
@endfor
`, navbar = null)