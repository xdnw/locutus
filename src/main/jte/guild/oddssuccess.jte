@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.util.MathMan
@import link.locutus.discord.util.PnwUtil
@import link.locutus.discord.apiv1.enums.SuccessType
@param WebStore ws
@param double odds
@param int success
@if(odds > 0)
<div class="overflow-hidden progress-bar @if(success == 0)
        bg-danger
    @elseif(success == 1)
        bg-warning
    @elseif(success == 2)
        bg-info
    @elseif(success == 3)
        bg-primary
    @endif" role="progressbar" style="width: ${odds}%" aria-valuenow="${odds}" aria-valuemin="0" aria-valuemax="100">
    <div style="width:0!important">
        ${(int) odds}% ${SuccessType.values[success]}
    </div>
    </div>
@endif