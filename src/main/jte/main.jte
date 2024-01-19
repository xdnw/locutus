@import java.util.List
@import java.util.Map
@import java.util.Set
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.web.jooby.WebRoot
@import link.locutus.discord.config.Settings
@import gg.jte.Content
@param WebStore ws
@param Content content
@param String title
@param List<Map.Entry<String, String>> navbar
<!DOCTYPE html>
<html lang="en" data-bs-core="modern">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="light">
    <title>${title}</title>
    <link rel="icon" type="image/x-icon" href="https://raw.githubusercontent.com/xdnw/locutus/master/src/main/resources/img/favicon.ico">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/Halfmoon/2.0.1/css/halfmoon.min.css"/>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/halfmoon@2.0.1/css/cores/halfmoon.modern.css">
    <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css">
    <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta3/dist/css/bootstrap-select.min.css">
<%--    <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/all.css">--%>
    <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/command.css">
    <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/embed.css">
    <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="https://github.com/xdnw/locutus/raw/master/src/main/resources/css/datatables.css">

    <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="https://cdn.jsdelivr.net/npm/uplot@1.6.28/dist/uPlot.min.css">

    <%--    <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/2.4.4/umd/popper.js"></script>--%>
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
<%--    <script src="https://cdnjs.cloudflare.com/ajax/libs/list.js/2.3.1/list.min.js"></script>--%>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.7.1/jquery.min.js"></script>
    <!-- Latest compiled and minified JavaScript -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta3/dist/js/bootstrap-select.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.4.1/highlight.min.js"></script>
    <script src="https://unpkg.com/twemoji@14.0.2/dist/twemoji.min.js"></script>
    <script src="https://cdn.datatables.net/1.13.7/js/jquery.dataTables.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/uplot@1.6.28/dist/uPlot.iife.min.js"></script>
    <script src="/js/default.js"></script>
    <script src="/js/command.js"></script>
    <script src="/js/embed.js"></script>
    <script src="/js/components.js"></script>
    <script src="/js/pnwtable.js"></script>
    <script src="/js/colorutil.js"></script>
    <script src="/js/pnwtimechart.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
<%--    <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.rawgit.com/afeld/bootstrap-toc/v0.3.0/dist/bootstrap-toc.min.css">--%>
</head>
<body class="ps-md-sbwidth">
@template.navbar(ws = ws)
@template.sidebar(ws = ws)
<div class="container" style="min-height: calc(100vh - 203px);">
    ${content}
</div>
<footer class="bd-footer border-top border-1 mt-0 pt-3 bg-light">
    <div class="container-md">
        <div class="row g-4">
            <div class="col-4">
                <img src="https://cdn.discordapp.com/avatars/672237266940198960/0d78b819d401a8f983ab16242de195da.webp" style="position:absolute;" alt="Logo" width="18" height="18">
                <h5 class="fw-medium ms-4">${Settings.INSTANCE.WEB.INTERFACE_NAME}</h5>
                <hr>
                <ul class="list-unstyled">
                    <li class="mb-2"><a href="https://github.com/xdnw/locutus/"><i class="bi bi-git"></i>&nbsp;Source Code</a></li>
                    <li class="mb-2"><a href="https://github.com/xdnw/locutus/wiki"><i class="bi bi-wikipedia"></i>&nbsp;Wiki</a></li>
                </ul>
            </div>
            <div class="col-4">
                <h5 class="fw-medium m-0">Get in Touch</h5>
                <hr>
                <ul class="list-unstyled">
                    <li class="mb-2"><a href="https://github.com/xdnw/locutus/issues"><i class="bi bi-github"></i>&nbsp;Issue Tracker</a></li>
                    <li class="mb-2"><a href="https://discord.gg/cUuskPDrB7"><i class="bi bi-discord"></i>&nbsp;Discord Server</a></li>
                    <li class="mb-2"><a href="discord://discord.com/users/${Settings.INSTANCE.ADMIN_USER_ID}"><i class="bi bi-person-circle"></i>&nbsp;Discord User</a></li>
                    <li class="mb-2"><a href="https://politicsandwar.com/nation/id=${Settings.INSTANCE.NATION_ID}"><i class="bi bi-joystick"></i>&nbsp;In-Game</a></li>
                    <li class="mb-2"><a href="mailto:jessepaleg@gmail.com"><i class="bi bi-at"></i>&nbsp;Email</a></li>
                </ul>
            </div>
            <div class="col-4">
                <h5 class="fw-medium m-0">Legal</h5>
                <hr>
                <ul class="list-unstyled">
                    <li class="mb-2"><a href="https://github.com/xdnw/locutus/blob/master/LICENSE"><i class="bi bi-list-columns-reverse"></i>&nbsp;License</a></li>
                    <li class="mb-2"><a href="https://github.com/xdnw/locutus/blob/master/ToS.MD"><i class="bi bi-list-check"></i>&nbsp;Terms Of Service</a></li>
                    <li class="mb-2"><a href="https://github.com/xdnw/locutus/blob/master/PRIVACY.MD"><i class="bi bi-list-stars"></i>&nbsp;Privacy Policy</a></li>
                    <li class="mb-2"><a href="https://github.com/xdnw/locutus/blob/master/SECURITY.md"><i class="bi bi-incognito"></i>&nbsp;Vulnerability Disclosure</a></li>
                </ul>
            </div>
        </div>
    </div>
</footer>
<%--  Add navbar  --%>
<%--@template.navbar(ws = ws)--%>
<%--add sidebar jte --%>
<%--@template.sidebar(ws = ws)--%>
<%--<script>--%>
<%--var pathname = window.location.pathname;--%>
<%--if (pathname.startsWith("/")) pathname = pathname.substring(1);--%>
<%--var pathSplit = pathname.split("/");--%>

<%--var guild_id = "";--%>
<%--if (/^[0-9]+$/.test(pathSplit[0])) {--%>
<%--    guild_id = (pathSplit[0]);--%>
<%--}--%>
<%--if (guild_id == "") document.getElementById("navbar-search-form").remove();--%>

<%--function search() {--%>
<%--    var value = document.getElementById("navbar-search").value;--%>
<%--    var url = "${WebRoot.REDIRECT}/" + guild_id + "/search/" + value;--%>
<%--    window.location.href = url;--%>
<%--    return false;--%>
<%--}--%>
<%--</script>--%>
</body>
</html>