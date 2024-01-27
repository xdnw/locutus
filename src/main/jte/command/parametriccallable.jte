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
function initForm2() {
    let params = new URLSearchParams(window.location.search);
    let form = document.querySelector('#command-form');
    let inputs = form.elements;
    let inputMap = {};
    for(let i = 0; i < inputs.length; i++) {
        inputMap[inputs[i].name.toLowerCase()] = inputs[i];
    }
    for(let pair of params.entries()) {
        let key = pair[0].toLowerCase();
        let value = pair[1];
        let input = inputMap[key];
        if(input) {
            // Check the type of the input element
            if(input.type === 'checkbox' || input.type === 'radio') {
                let val = (value.toLowerCase() === 'true');
                input.checked = val;
                input.previousSibling.checked=val;
                input.previousSibling.value=val?1:0;
            } else if(input.tagName.toLowerCase() === 'select') {
                if (input.parentElement.classList.contains('bootstrap-select')) {
                    $(input).selectpicker('val', input.multiple ? value.split(',') : value);
                }
                if(input.multiple) {
                    let values = value.split(',');
                    for(let option of input.options) {
                        option.selected = values.includes(option.value) || values.includes(option.innerHTML);
                    }
                }
                input.value = value;
            } else if(['number', 'color', 'date', 'textarea', 'text'].includes(input.type)) {
                input.value = value;
            } else {
                console.log(`Input type ${input.type} is not supported`);
            }
        } else {
            console.log(`No matching input for key: ${key}, value: ${value}`);
        }
    }
}
$( document ).ready(function() {
    initForm2();
});
</script>
@endraw
`, navbar = null)