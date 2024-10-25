@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@param java.net.URL url

@template.main(ws = ws, content = @`
    Redirecting to <a href="${url.toString()}">${url.toString()}</a>
    If you are not redirected, click the link.
    <script>
        window.location.href = "${url.toString()}";
    </script>
`, title = "Redirect", navbar = null)