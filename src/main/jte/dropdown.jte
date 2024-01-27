@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.util.PnwUtil
@import link.locutus.discord.util.discord.DiscordUtil
@import link.locutus.discord.web.commands.binding.AuthBindings
@import link.locutus.discord.web.jooby.WebRoot
@param WebStore ws
@if (ws.getNation() == null && ws.getUser() == null)
<div class="dropstart">
    <button class="btn btn-primary dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" data-bs-auto-close="outside">
        Login
    </button>
    <ul class="dropdown-menu">
        <li><a class="dropdown-item" href="${WebRoot.REDIRECT}/page/login?nation"><i class="bi bi-envelope-fill"></i>&nbsp;Nation Login</a></li>
        <li><hr class="dropdown-divider"></li>
        <li><a class="dropdown-item" href="${AuthBindings.getDiscordAuthUrl()}"><i class="bi bi-discord"></i>&nbsp;Discord Login</a></li>
        <li><a class="dropdown-item" href="https://discord.com/download "><i class="bi bi-box-arrow-up-right"></i>&nbsp;Download Discord</a></li>
    </ul>
</div>
@else
<div class="dropstart">
    <button class="btn btn-primary dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false"  data-bs-auto-close="outside" aria-label="user menu">
        <i class="bi bi-list"></i>
    </button>
    <ul class="dropdown-menu">
        @if (ws.getUser() == null)
        <li><a class="dropdown-item-text btn btn-sm btn-outline-primary border border-2 border-primary" href="${AuthBindings.getDiscordAuthUrl()}"><i class="bi bi-discord"></i>&nbsp;Link Discord</a></li>
        <li><a class="dropdown-item-text btn btn-sm btn-outline-primary border border-2 border-primary" href="https://discord.com/download"><i class="bi bi-box-arrow-up-right"></i>&nbsp;Download Discord</a></li>
        @else
        <li>
            <span class="dropdown-item">
                ${DiscordUtil.getUserName(ws.getUser().getIdLong())}
            </span>
        </li>
        @endif
        <li><hr class="dropdown-divider"></li>
        <li>
            @if (ws.getNation() == null)
            <a class="dropdown-item-text border border-primary" href="${WebRoot.REDIRECT}/page/login?nation"><i class="bi bi-envelope-fill"></i>&nbsp;Link Nation</a>
            @else
                <a class="dropdown-item" href="${ws.getNation().getNationUrl()}"><i class="bi bi-box-arrow-up-right"></i>&nbsp;Nation <kbd>${ws.getNation().getName()}</kbd></a>
                @if (ws.getNation().getAlliance() != null)
                <a class="dropdown-item" href="${ws.getNation().getAllianceUrl()}"><i class="bi bi-box-arrow-up-right"></i>&nbsp;Alliance <kbd>${ws.getNation().getAllianceName()}</kbd></a>
                @endif
            @endif
        </li>
        <li><hr class="dropdown-divider"></li>
        @if (ws.getGuild() == null)
            <li><a class="dropdown-item-text btn btn-sm btn-outline-primary border border-2 border-primary" href="${WebRoot.REDIRECT}/page/guildselect"><i class="bi bi-geo-alt-fill"></i>&nbsp;Select Guild</a></li>
        @else
            <li><a class="dropdown-item" href="${WebRoot.REDIRECT}/page/guildmemberindex">Guild <kbd>${ws.getGuild().getName()}</kbd></a></li>
            <li><a class="dropdown-item-text btn btn-sm btn-outline-primary border border-2 border-primary" href="${WebRoot.REDIRECT}/page/guildselect"><i class="bi bi-geo-alt-fill"></i>&nbsp;Change Guild</a></li>
        @endif
        <li><hr class="dropdown-divider"></li>
        <li>
            <a class="dropdown-item-text btn btn-sm btn-outline-danger border border-2 border-danger" href="${WebRoot.REDIRECT}/logout" aria-label="logout"><i class="bi bi-box-arrow-left"></i>&nbsp;Logout</a>
        </li>
    </ul>
</div>
@endif
