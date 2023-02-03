// String.format function
if (!String.prototype.format) {
    String.prototype.format = function() {
        var args = arguments;
        return this.replace(/{(\d+)}/g, function(match, number) {
            return typeof args[number] != 'undefined' ? args[number] : match;
        });
    };
};

const SI_PREFIXES_CENTER_INDEX = 8;

const siPrefixes = [
    'y', 'z', 'a', 'f', 'p', 'n', 'μ', 'm', '', 'k', 'M', 'B', 'T', 'P', 'E', 'Z', 'Y'
];

function formatN(number) {
    if (number === 0) return number.toString();
    const EXP_STEP_SIZE = 3;
    const base = Math.floor(Math.log10(Math.abs(number)));
    const siBase = (base < 0 ? Math.ceil : Math.floor)(base / EXP_STEP_SIZE);
    const prefix = siPrefixes[siBase + SI_PREFIXES_CENTER_INDEX];

    // return number as-is if no prefix is available
    if (siBase === 0) return number.toString();

    // We're left with a number which needs to be devided by the power of 10e[base]
    // This outcome is then rounded two decimals and parsed as float to make sure those
    // decimals only appear when they're actually requird (10.0 -> 10, 10.90 -> 19.9, 10.01 -> 10.01)
    const baseNumber = parseFloat((number / Math.pow(10, siBase * EXP_STEP_SIZE)).toFixed(2));
    return `${baseNumber}${prefix}`;
};
function htmlDecode(input) {
  var doc = new DOMParser().parseFromString(input, "text/html");
  return doc.documentElement.textContent;
}
function uuidv4() {
  return ([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g, c =>
    (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
  );
}

function htmlToElement(html) {
    var template = document.createElement('template');
    html = html.trim(); // Never return a text node of whitespace as the result
    template.innerHTML = html;
    return template.content.firstChild;
}
function modalWithCloseButton(title, body) {
    modal(title, body, `<button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>`);
}

function modal(title, body, footer) {
    var myModal = document.getElementById("exampleModal");

    var html = `<div class="modal fade" id="exampleModal" tabindex="-1" role="dialog" aria-labelledby="exampleModalLabel" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title" id="exampleModalLabel">` + title +
                    `</h5><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close">
                        <span aria-hidden="true">&times;</span>
                    </button>
                </div>
                <div class="modal-body">` + body + `</div>
                <div class="modal-footer">` + footer + `</div>
            </div>
        </div>
    </div>`

    if (myModal == null) {
        myModal = htmlToElement(html);
        document.body.appendChild(myModal);
    } else {
        myModal.innerHTML = htmlToElement(html).innerHTML;
    }
    bootstrap.Modal.getOrCreateInstance(myModal).show();
}