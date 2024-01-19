@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.util.PnwUtil
@import link.locutus.discord.util.MathMan
@param WebStore ws
@param String titleA
@param String titleB
@param double a
@param double b
<div class="progress" style="height:20px">
<div class="progress-bar bg-success" role="progressbar" style="width: ${100 * a / (a + b)}%" aria-valuenow="${100 * a / (a + b)}" aria-valuemin="0" aria-valuemax="100"
     data-bs-toggle="tooltip" data-bs-placement="bottom" title="${titleA}: ${MathMan.format(a)} (${MathMan.format(100 * a / (a + b))}%)">
    <div style="width:0!important" class="text-center">
        ${titleA}: ${MathMan.format(a)} (${MathMan.format(100 * a / (a + b))}%)
    </div>
</div>
<div class="progress-bar bg-danger" role="progressbar" style="width: ${100 * b / (a + b)}%" aria-valuenow="${100 * b / (a + b)}" aria-valuemin="0" aria-valuemax="100"
     data-bs-toggle="tooltip" data-bs-placement="bottom" title="${titleB}: ${MathMan.format(b)} (${MathMan.format(100 * b / (a + b))}%)">
    <div style="width:0!important" class="text-center">
        ${titleB}: ${MathMan.format(b)} (${MathMan.format(100 * b / (a + b))}%)
    </div>
</div>
</div>