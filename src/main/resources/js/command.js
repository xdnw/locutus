const AVATAR = "https://cdn.discordapp.com/avatars/672237266940198960/0d78b819d401a8f983ab16242de195da.webp?size=256";
const AUTHOR_NAME = "Locutus";
const LOADING_CIRCLE_BOILER = "<div style=\"margin: 0;position: absolute;top: 50%;left: 50%;-ms-transform: translate(-50%, -50%);transform: translate(-50%, -50%);\">%content%</div>";
const LOADING_CIRCLE_SM = LOADING_CIRCLE_BOILER.replace("%content%", "<div class=\"spinner-border spinner-border-sm\" style=\"color: lightblue\" role=\"status\"></div>");
const LOADING_CIRCLE = LOADING_CIRCLE_BOILER.replace("%content%", "<div class=\"spinner-border text-light\" role=\"status\"></div>");

function getGuildId() {
    var path = location.pathname;
    if (path.length > 0) {
        if (path.charAt(0) == '/') path = path.substring(1);
        var split = path.split("/");
        if (split.length != 0 && !isNaN(split[0])) {
            var result = BigInt(split[0]);
            return result;
        }
    }
}

function loadInlineData()
{
    var elems = document.getElementsByClassName("select-inline-data");
    for (var elem of elems) {
        if (elem.hasAttribute("data-json")) {
            loadInlineData2(elem, JSON.parse(elem.getAttribute("data-json")));
        } else if (elem.hasAttribute("data-json-src")) {
            var elemFinal = elem;
            $.get(elemFinal.getAttribute("data-json-src"), function( data ) {
                loadInlineData2(elemFinal, data);
            });
        }
    }
}

function loadInlineData2(elem, data) {
    elem.removeAttribute("data-json");
    elem.removeAttribute("data-json-src");
    if ("subtext" in data) {
        elem.setAttribute("data-show-subtext", "true");
    }
    elem.setAttribute("data-live-search", "true");


    var names = data["names"];
    var values = data["values"];
    var subtexts = data["subtext"];

    var inner = '<option style="display:none" disabled selected value> -- select an option -- </option>';
    for (var i = 0; i < names.length; i++) {
        var name = names[i];
        var value = values == null ? null : values[i];
        var subtext = subtexts == null ? null : subtexts[i];
        var opt = document.createElement('option');
        if (subtext != null) {
            if (value == null || value === name) {
                inner += "<option data-subtext=\"" + subtext + "\">" + name + "</option>"
            } else {
                inner += "<option data-subtext=\"" + subtext + "\" value='" + value + "'>" + name + "</option>"
            }
        } else if (value == null || value === name) {
            inner += "<option>" + name + "</option>"
        } else {
            inner += "<option value='" + value + "'>" + name + "</option>"
        }
    }
    elem.innerHTML = inner;
    $(elem).selectpicker();
}

function loadButtons() {
    var click = function(event){
        var cmd = this.getAttribute("cmd");
        if (cmd != null) {
            console.log("click");
            var output = this.getAttribute("for");
            var outDiv = output != null ? document.getElementById(output) : null;

            var query = "cmd=" + encodeURIComponent(cmd);
            domain = [location.protocol, '//', location.host].join('') + "/sse_cmd_str?" + query;
            if (this.hasAttribute("replace") && outDiv != null) {
                outDiv.innerHTML = "";
            }
            if (outDiv != null && "none" === outDiv.style.display) {
                outDiv.style.display = "";
            }
            createEventSourceWithLoading(domain, outDiv, this, LOADING_CIRCLE_SM);
        }
    };
    $(document).on("click", "button", click);
    $(document).on("click", "a", click);
}

$( document ).ready(function() {
    loadInlineData();
    loadButtons();
});

function onMessageReact(emoji, id, json, outDiv, button) {
    console.log("Message react: " + emoji + " | " + id);

    var query = "emoji=" + emoji + "&msg=" + encodeURIComponent(JSON.stringify(json));
    domain = [location.protocol, '//', location.host].join('') + "/sse_reaction?" + query;

    console.log("DOMAIN: " + domain)

    createEventSource(domain, outDiv, button, LOADING_CIRCLE_SM);
}

function executeCommandFromArgMap(form) {
    var outDiv = document.getElementById("output");
    return executeCommandFromArgMapWithOutput(form, outDiv);
}

function executeCommandFromArgMapWithOutput(form, outDiv, args) {
//    var domain = window.location.href.replace("/command", "").replace("web", "sse");
    var domain;
    if (form.hasAttribute("endpoint")) {
        domain = form.getAttribute("endpoint") + window.location.pathname;
    } else {
        var domain = window.location.href.replace("command", "sse");
    }
    return executeCommandFromArgMapWithDomain(domain, form, outDiv);
}

function createEventSource(domain, outDiv) {
    createEventSourceWithLoading(domain, outDiv, null, null);
}

function createEventSourceWithLoading(domain, outDiv, inputDiv, inputReplacement) {
    var innerContentCopy = null;
    var disabledPrevious = false;
    if (inputDiv != null) {
        disabledPrevious = inputDiv.disabled;
        innerContentCopy = inputDiv.innerHTML;
        inputDiv.disabled = true;
        var loadingHtml = "<div style=\"position:relative\"><div style=\"opacity:0\">" + innerContentCopy + "</div>" + inputReplacement + "</div>"
        inputDiv.innerHTML = loadingHtml;
    }
    const sse = new EventSource(domain);
    try {
        sse.onerror = function(err) {
            if (innerContentCopy != null) {
                console.log("RESET LOADING BAR");
                inputDiv.innerHTML = innerContentCopy;
                inputDiv.disabled = disabledPrevious;
            }
            console.log("SSE ERROR CLOSING");
            console.error("EventSource failed 2:", err);
            sse.close();
        };
        sse.onmessage = function(event) {
        try {
            console.log("ON MESSAGE ");
            console.log("DATA: " + event.data);
            var json = JSON.parse(event.data, (key, value) => {
                if (key === "id" && typeof value === "string" && value.match(/^\d+$/)) {
                    return BigInt(value);
                }
                return value;
            });
            if (json["action"]) {
                switch (json["action"]) {
                    case "redirect":
                        window.location.href = json["value"];
                        return;
                    case "deleteByIds":
                        for (var id of json["value"]) {
                            var item = document.getElementById(id + "");
                            console.log("DELETE " + item + " | " + id)
                            if (item != null) {
                                item.parentNode.removeChild(item);
                            }
                        }
                        break;
                    default:
                        alert("Unknown action: " + json["action"]);
                        break;
                }

                return;
            }

            console.log(json);
            console.log(json["content"]);
            console.log($.parseHTML(json["content"]));
//            var output = "<div class='alert alert-success'><b>[" + new Date().toLocaleTimeString() + "]: " + JSON.stringify(args) +":</b><br>" + json["content"] + "</div>";

            var embed = null;
            if (json.id) {
                var existing = document.getElementById(json.id);
                if (existing) {
                    embed = existing;
                }
            }
            if (embed == null) {
                embed = document.createElement("div");
                if (json.id) {
                    embed.id = json.id;
                }
                if (outDiv != null) {
                    outDiv.prepend(embed);
                }
            }
            createEmbed(embed, json, AUTHOR_NAME, AVATAR, outDiv);
            if (outDiv == null) {
                console.log(embed + " | " + embed.innerHTML + " | " + embed.outerHTML);
                modalWithCloseButton("Command result", embed.outerHTML);
            }
            console.log("Create embed " + (outDiv == null))
            } catch (exception_var) {
                console.log(exception_var.stack);
              console.log("Error: " + exception_var)
            }
//            outDiv.innerHTML = output + outDiv.innerHTML;
         }
    } finally {
//        evtSource.close();
    }
}

serialize = function(obj) {
  var str = [];
  for (var p in obj)
    if (obj.hasOwnProperty(p)) {
      str.push(encodeURIComponent(p) + "=" + encodeURIComponent(obj[p]));
    }
  return str.join("&");
}

function processInputs(form, args) {
    var selectElems = form.querySelectorAll("select[multiple]");
    selectElems.forEach(elem => {
        var name = elem.getAttribute("name");
    	var listValue = $(elem).val().join(',');

    	console.log("name " + name + " | " + listValue + " | " + args[name]);

        args[name] = listValue;
    });
}

function executeCommandFromArgMapWithDomain(domain, form, outDiv) {
//    for ( elem of form.querySelectorAll("input[type=\"checkbox\"]")) {
//                   if (elem.checked) {
//                       elem.value = "true";
//                   } else {
//                       elem.value = "false";
//                   }
//               }


    var serialized = $(form).serializeArray();
    var args = {};

    $.map(serialized, function(n, i){
        args[n['name']] = n['value'];
    });

    var previousValues = processInputs(form, args);

    console.log("Serialized " + serialized + " | " + args + " | " + serialize(args));

    domain = domain + "?" + serialize(args);

    var submitButtons = form.querySelectorAll("button[type=submit]");
    if (submitButtons.length == 1) {
        createEventSourceWithLoading(domain, outDiv, submitButtons[0], LOADING_CIRCLE);
    } else {
        console.log(submitButtons.length + " <> buttons")
        createEventSource(domain, outDiv);
    }
    return false;
}
function addFilter(elem) {
    var outputId = elem.getAttribute("for");
    console.log("[name=\"filter-type\"][for=\"" + outputId + "\"]")
    var type = document.querySelectorAll("[name=\"filter-type\"][for=\"" + outputId + "\"]")[0].value;
    var operator = document.querySelectorAll("[name=\"filter-operator\"][for=\"" + outputId + "\"]")[0].value;
    var value = document.querySelectorAll("[name=\"filter-value\"][for=\"" + outputId + "\"]")[0].value;

    var form = document.querySelectorAll("form[for=\"" + outputId + "\"]")[0];
    var serialized = $(form).serializeArray();
    var args = {};
    $.map(serialized, function(n, i){
        args[n['name']] = n['value'];
    });
//    console.log(serialized);
//    console.log(serialized + " <-- serialized");
//    var args = JSON.parse(serialized);
    delete args[outputId];
    delete args['filter-type'];
    delete args['filter-operator'];
    delete args['filter-value'];
    var result;
    if (Object.keys(args).length == 0) {
        result = "#" + type + "" + operator + "" + value;
    } else {
        var mylist = [];
        for (const [key, value] of Object.entries(args)) {
            console.log(key + " : " + value.toString());
            mylist.push(value);
        }
        result = "#" + type + "(" + mylist.join(",") + ")" + operator + "" + value;
    }

    var output = document.getElementById(outputId);
    var outputVal = output.value;
    var newValue = outputVal == null || outputVal === "" ? result : outputVal + "," + result;
    document.getElementById(outputId).value = newValue;
    return false;
}
function updateFilter(elem) {
    var outputId = elem.getAttribute("for");
    var filterForm = document.querySelectorAll("form[for=\"" + outputId + "\"]")[0];
    var outputId = elem.getAttribute("for");
    $.get("http://localhost:8000/filter/" + elem.value.toLowerCase() + "/" + outputId, function( data ) {
        filterForm.innerHTML = data;
        loadInlineData();
    })
}