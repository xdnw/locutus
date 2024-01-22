@import gg.jte.Content
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.commands.manager.v2.command.CommandGroup
@import java.util.*
@import link.locutus.discord.commands.manager.v2.binding.ValueStore
@import link.locutus.discord.commands.manager.v2.command.ParametricCallable
@import link.locutus.discord.util.MarkupUtil
@param WebStore ws
@param ParametricCallable command
@param Content form
@template.main(ws = ws, title = "Command: " + command.getPrimaryCommandId(), content = @`
<div class="container-fluid rounded shadow bg-lightp-1">
@if(command.help(ws.store()) != null)
<h2>Help</h2>
<p>$unsafe{MarkupUtil.markdownToHTML(command.help(ws.store()))}</p>
@endif
@if(command.simpleDesc() != null && !command.simpleDesc().isEmpty())
<h2>Description</h2>
<p>$unsafe{MarkupUtil.markdownToHTML(command.simpleDesc())}</p>
@endif
<h2>Execute</h2>
${form}
<div style="width:100%;height:80%;margin-top:10px" id="output" class=""></div>
</div>
@raw
<script>
        // Parse the query string
let params = new URLSearchParams(window.location.search);

// Iterate over each key-value pair
for(let pair of params.entries()) {
    let key = pair[0];
    let value = pair[1];

    // Find the corresponding input element in the form
    let input = document.querySelector(`#command-form [name="${key}"]`);

    // Check if the input element exists
    if(input) {
        // Check the type of the input element
        if(input.type === 'checkbox') {
            // Set the checked property
            input.checked = (value.toLowerCase() === 'true');
        } else if(input.type === 'select-multiple') {
            // Split the value and set the selected options
            let values = value.split(',');
            for(let option of input.options) {
                option.selected = values.includes(option.value);
            }
        } else if(input.type === 'select-one') {
            // Set the value
            input.value = value;
        } else if(input.type === 'text') {
            // Set the value
            input.value = value;
        }
    } else {
        // Print the key-value pair if no matching input element is found
        console.log(`No matching input for key: ${key}, value: ${value}`);
    }
}
</script>
@endraw
`, navbar = null)