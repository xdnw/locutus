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
</head>
<body class="ps-md-sbwidth">
@template.navbar(ws = ws)
@template.sidebar(ws = ws)
<div class="container" style="min-height: calc(100vh - 203px);">
    <h1>hello world 6</h1>
    ${content}
</div>
@template.footer()
<script>
function navsearch() {
    window.location.href = "${WebRoot.REDIRECT}/page/search/" + document.getElementById("navbar-search").value;
    return false;
}
</script>
</body>
</html>