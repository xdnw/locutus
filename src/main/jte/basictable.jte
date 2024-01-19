@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import java.util.*

@param WebStore ws
@param String title
@param List<String> header
@param List<List<gg.jte.Content>> rows

@template.main(ws = ws, content = @`
<table class="table">
    <thead>
    <tr>
        @for(String item : header)
        <th scope="col">${item}</th>
        @endfor
    </tr>
    </thead>
    <tbody>
    @for (List<gg.jte.Content> row : rows)
        <tr>
            @for(gg.jte.Content item : row)
            <th>${item}</th>
            @endfor
        </tr>
        @endfor
    </tbody>
</table>
`, title = title, navbar = null)