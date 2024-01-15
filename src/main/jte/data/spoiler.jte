@import gg.jte.Content

@param String title
@param Content content
@param String id
<br>
<button class="btn btn-primary" type="button" data-bs-toggle="collapse" data-bs-target="#${id}" aria-expanded="false" aria-controls="${id}">
    ${title}
</button>
<div class="collapse" id="${id}">
${content}
</div>
<br>
<hr/>