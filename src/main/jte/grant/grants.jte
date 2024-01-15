@param String title
@param gg.jte.Content content

@template.main(title = title + " Grants", content = @`
<script>
    function promptGrant(title, body, uuid, isOverride) {
        var body = '<div class="container">' + body + '</div>';
    var cmd = "$approveGrant " + uuid + " -f";
    var footer = '<button type="button" class="btn btn-primary" cmd="' + cmd + '" >Confirm Grant</button>'
    footer += '<button type="button" class="btn btn-secondary" data-dismiss="modal">Close</button>';
        modal(title, body, footer);
    }
    $(document).ready(function(){
      $("#myInput").on("keyup", function() {
    var value = $(this).val().toLowerCase();
        $(".guild-entry").filter(function() {
    $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)
    });
    });
    });
</script>
${content}
`, navbar = null)