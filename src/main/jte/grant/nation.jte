@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.db.entities.DBNation
@import link.locutus.discord.util.TimeUtil
@import java.util.*
@param WebStore ws
@param DBNation nation
<div class="card alert p-1 my-1 alert-dismissible">
    <div class="card-body">
        <h5 class="card-title"><a href="${nation.getUrl()}">
            ${nation.getNation()}</a> | <a href="${nation.getAllianceUrl()}">${nation.getAllianceName()}</a>
            @if(nation.getPosition() == 1)
            <span class="badge badge-secondary">APP</span>
            @endif
        </h5>
        <table class="table">
            <tbody>
            <tr>
                <th scope="row">Cities</th>
                <td>${nation.getCities()}</td>
            </tr>
            <tr>
                <th scope="row">avg_infra</th>
                <td>${nation.getAvg_infra()}</td>
            </tr>
            <tr>
                <th scope="row">MMR (build/units)</th>
                <td>${nation.getMMRBuildingStr()}/${nation.getMMR()}</td>
            </tr>
            <tr>
                <th scope="row">Last login</th>
                <td>@if(nation.active_m() <= 3)Online @else ${TimeUtil.minutesToTime(nation.active_m())}@endif</td>
            </tr>
            <tr>
                <th scope="row">Daily Login</th>
                <td>${100 * nation.avg_daily_login()}%</td>
            </tr>
            <tr>
                <th scope="row">Seniority</th>
                <td>${nation.allianceSeniority()} days</td>
            </tr>
            <tr>
                <th scope="row">Wars (off/def)</th>
                <td>${nation.getOff()}/${nation.getDef()}</td>
            </tr>
            <tr>
                <th scope="row">Domestic Policy</th>
                <td>${nation.getDomesticPolicy()}</td>
            </tr>
            </tbody>
        </table>
    </div>
    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
</div>