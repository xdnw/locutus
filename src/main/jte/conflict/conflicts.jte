@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.web.jooby.WebRoot
@param WebStore ws
@param String title
@param String data
@template.main(ws = ws, title = title, content = @`
@template.data.table_data(ws = ws, title = title, data = data)
`, navbar = null);
<script>
function createAnchor(href, text) {
    var a = document.createElement('a');
    a.href = href;
    a.innerText = text;
    return a.outerHTML;
}
function renderUrl(data, type, row, meta) {
    return createAnchor("${WebRoot.REDIRECT}/page/conflict/?conflict=" + data[0], data[1]);
}
function renderTime(data, type, row, meta) {
    if (data == -1) return "N/A";
    let date = new Date(data);
    return date.toISOString().split('T')[0];
}
function renderMoney(data, type, row, meta) {
    return "$" + data.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}
</script>
