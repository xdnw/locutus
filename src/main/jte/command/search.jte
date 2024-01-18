@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.web.commands.search.SearchResult
@import link.locutus.discord.web.commands.search.SearchType
@import java.util.List
@import org.apache.commons.lang3.text.WordUtils

@param WebStore ws
@param String term
@param List<SearchResult> results

@template.main(ws = ws, title = "Search Results", content = @`
@if(results.isEmpty())
    <div class="alert alert-danger">No results found for <kbd>${term}</kbd></div>
@else
    <div id="results">
@for (SearchResult result : results)
    <div class="bg-lightcontainer mt-1 rounded shadow">
        <h3>${WordUtils.capitalizeFully(result.type.name())}: <a href="${result.url}">${result.title}</a></h3>
        <hr>
        $unsafe{result.body}
    </div>
@endfor
    </div>

    <script>
var matchText = function(node, regex, callback, excludeElements) {

    excludeElements || (excludeElements = ['script', 'style', 'iframe', 'canvas']);
    var child = node.firstChild;

    while (child) {
        switch (child.nodeType) {
        case 1:
            if (excludeElements.indexOf(child.tagName.toLowerCase()) > -1)
                break;
            matchText(child, regex, callback, excludeElements);
            break;
        case 3:
            var bk = 0;
            child.data.replace(regex, function(all) {
                var args = [].slice.call(arguments),
                    offset = args[args.length - 2],
                    newTextNode = child.splitText(offset+bk), tag;
                bk -= child.data.length + all.length;

                newTextNode.data = newTextNode.data.substr(all.length);
                tag = callback.apply(window, [child].concat(args));
                child.parentNode.insertBefore(tag, newTextNode);
                child = newTextNode;
            });
            regex.lastIndex = 0;
            break;
        }

        child = child.nextSibling;
    }

    return node;
};

var term = "${term}";
var root = document.getElementById("results");

$(document).ready(function() {
    matchText(root, new RegExp("" + term + "", "gi"), function(node, match, offset) {
        var wrap = document.createElement("span");
        wrap.style = "background-color: #FFFF00";
        wrap.textContent = match;
        return wrap;
    });
});
    </script>
@endif
`, navbar = null)