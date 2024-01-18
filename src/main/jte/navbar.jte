@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.web.jooby.WebRoot
@param WebStore ws
<div>
    <nav class="navbar navbar-expand-lg navbar-light bg-primary bg-gradient shadow p-0 mb-3 border-bottom border-3 border-dark text-white">
        <div class="container-fluid">
            <img src="https://cdn.discordapp.com/avatars/672237266940198960/0d78b819d401a8f983ab16242de195da.webp" width="30" height="30" class="d-inline-block" alt="">
            <a class="navbar-brand text-white bold" href="${WebRoot.REDIRECT}">
                Locutus-web
            </a>
            <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarSupportedContent" aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
            </button>
            <div class="collapse navbar-collapse" id="navbarSupportedContent">
                <ul class="navbar-nav me-auto mb-2 mb-lg-0">
                </ul>
                <form class="d-flex" id="navbar-search-form" onsubmit="return search()">
                    <input id="navbar-search" class="form-control me-2" type="search" placeholder="Search" aria-label="Search" required>
                    <button class="btn btn-success shadow text-light" type="submit">submit</button>
                </form>
                <a class="btn btn-danger shadow text-light" href="${WebRoot.REDIRECT}/logout">Logout</a>
            </div>
        </div>
    </nav>
</div>