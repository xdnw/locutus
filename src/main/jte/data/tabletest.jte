@import java.util.*

@param String title
@param String data
@template.main(title = "Hello World", content = @`
@template.data.table_data(title = title, data = data)
`, navbar = null)