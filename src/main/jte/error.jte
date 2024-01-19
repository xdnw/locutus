@param link.locutus.discord.commands.manager.v2.binding.WebStore ws
@param String error
@param String stacktrace
@template.main(ws = ws, content = @`
<link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/styles/default.min.css">
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/highlight.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/languages/java.min.js"></script>
<div class="bg-lightcontainer mt-3 rounded shadow py-1">
    <h1 class="text-primary">${error}</h1>
    <pre><code class="language-java">${stacktrace}</code></pre>
    <script>hljs.highlightAll();</script>
</div>
`, title = "Home", navbar = null)