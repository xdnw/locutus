@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.config.Settings
@import link.locutus.discord.web.jooby.WebRoot
@import java.util.Map
@param WebStore ws
<nav class="navbar navbar-expand-md border-bottom">
    <div class="container-fluid p-0 m-0">
        <div class="row d-flex w-100 p-0 m-0">
            <div class="col-auto p-0 pe-1">
                <div class="btn-group">
                    <button type="button" data-bs-toggle="offcanvas" data-bs-target="#sidebar" class="btn btn btn-secondary d-md-none">
                        <i class="bi bi-layout-sidebar"></i>
                    </button>
                    <a href="/" class="d-flex align-items-center d-block btn btn-sm btn-secondary">
                        <img src="https://cdn.discordapp.com/avatars/672237266940198960/0d78b819d401a8f983ab16242de195da.webp" alt="Logo" width="18" height="18">
                        <span class="d-none d-md-block d-lg-block inline me-2">${Settings.INSTANCE.WEB.INTERFACE_NAME}</span>
                    </a>
                    <a type="button" class="btn btn btn-secondary d-md-block" aria-label="Toggle dark mode" onclick="toggleDarkMode()">
                        <i class="bi bi-moon-stars-fill" style="color:gray"></i>
                    </a>
                    <div class="d-inline text-truncate mt-1">
                        @for (Map.Entry<String, String> entry : ws.getPathLinks().entrySet())
                            <span class="mx-1">/</span>
                            <a href="${entry.getValue()}/" class="simple-link fw-bold">${entry.getKey()}</a>
                        @endfor
                    </div>
                </div>
            </div>
            <div class="col p-0">
                <form class="d-flex" role="search" id="navbar-search-form" onsubmit="return search()">
                    <input class="form-control rounded-end-0" type="search" placeholder="Search pages..." aria-label="Search">
                    <button type="submit" class="btn btn-light text-nowrap rounded-start-0"><i class="bi bi-search"></i></button>
                </form>
            </div>
            <div class="col-auto p-0 ps-1">
                <a class="btn btn btn-danger fw-bold" href="${WebRoot.REDIRECT}/logout" aria-label="logout"><i class="bi bi-box-arrow-left"></i></a>
            </div>
        </div>
    </div>
</nav>