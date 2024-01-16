@import java.util.List
@import java.util.Map
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.web.jooby.WebRoot
@import link.locutus.discord.config.Settings
@import gg.jte.Content

@param WebStore ws
@param Content content
@param String title
@param List<Map.Entry<String, String>> navbar

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
    <meta name="color-scheme" content="light dark">

    <title>${title}</title>
     <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.jsdelivr.net/npm/bootstrap@5.1.1/dist/css/bootstrap.min.css">

     <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.5.0/font/bootstrap-icons.css">

    <!-- Latest compiled and minified CSS -->
     <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta2/dist/css/bootstrap-select.min.css">

     <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/all.css">
     <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/dark.css">
     <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/command.css">
     <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/embed.css">
     <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/datatables.css">

     <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="https://leeoniya.github.io/uPlot/dist/uPlot.min.css">

    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/2.4.4/umd/popper.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.1.1/dist/js/bootstrap.bundle.min.js"></script>
    <!-- Latest compiled and minified JavaScript -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta2/dist/js/bootstrap-select.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.4.1/highlight.min.js"></script>
    <script src="https://unpkg.com/twemoji@14.0.2/dist/twemoji.min.js"></script>

    <script src="https://cdn.datatables.net/1.11.3/js/jquery.dataTables.min.js"></script>

    <script src="https://leeoniya.github.io/uPlot/dist/uPlot.iife.min.js"></script>

    <script src="/js/default.js"></script>
    <script src="/js/command.js"></script>
    <script src="/js/embed.js"></script>
    <script src="/js/components.js"></script>
    <script src="/js/pnwtable.js"></script>

    <script src="/js/colorutil.js"></script>
    <script src="/js/pnwtimechart.js"></script>

    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>

     <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.rawgit.com/afeld/bootstrap-toc/v0.3.0/dist/bootstrap-toc.min.css">
</head>
<body>
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
<script>
var pathname = window.location.pathname;
if (pathname.startsWith("/")) pathname = pathname.substring(1);
var pathSplit = pathname.split("/");

var guild_id = "";
if (/^[0-9]+$/.test(pathSplit[0])) {
    guild_id = (pathSplit[0]);
}
if (guild_id == "") document.getElementById("navbar-search-form").remove();

function search() {
    var value = document.getElementById("navbar-search").value;
    var url = "${WebRoot.REDIRECT}/" + guild_id + "/search/" + value;
    window.location.href = url;
    return false;
}
</script>
<h1 class="display-4 m-0 text-white font-weight-bold d-flex justify-content-center">${title}</h1>
<div class="container-fluid">
@if(content != null)
    ${content}
@endif
</div>
</body>
</html>