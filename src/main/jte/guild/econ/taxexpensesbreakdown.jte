@import link.locutus.discord.Locutus
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
@import link.locutus.discord.apiv1.enums.ResourceType
@import java.util.UUID

@param WebStore ws
@param Object id
@param double[] a
@param double[] b
@param boolean showByResource

@template.guild.econ.incomeexpensebar(ws = ws, titleA = "Monetary Income", titleB = "Monetary Expense", a = PnwUtil.convertedTotal(a), b = PnwUtil.convertedTotal(b))
@if(showByResource)
    <div class="accordion" id="Accordion${id + ""}">
        <div class="accordion-item">
            <h2 class="accordion-header" id="heading${id + ""}">
                <button class="accordion-button collapsed p-1 btn-sm" type="button" data-bs-toggle="collapse"
                        data-bs-target="#collapse${id + ""}" aria-expanded="false" aria-controls="collapse${id + ""}">
                    Show by resource
                </button>
            </h2>
            <div id="collapse${id + ""}" class="accordion-collapse collapse" aria-labelledby="heading${id + ""}"
                 data-bs-parent="#Accordion${id + ""}">
                <div class="accordion-body bg-light">
                    @for (ResourceType type : ResourceType.values)
                        @if(a[type.ordinal()] != 0 || b[type.ordinal()] != 0)
                            @template.guild.econ.incomeexpensebar(ws = ws, titleA = type + " income", titleB = type + " expense", a = a[type.ordinal()], b = b[type.ordinal()])
                        @endif
                    @endfor
                </div>
            </div>
        </div>
    </div>
@endif