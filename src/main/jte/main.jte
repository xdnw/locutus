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
@template.head(title = title)
</head>
<body class="ps-md-sbwidth">
@template.navbar(ws = ws)
@template.sidebar(ws = ws)
<div class="container-fluid" style="min-height: calc(100vh - 203px);">
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