var template = '<div class=\'tag-input-tl\'>' +
    '<div class="box_tag"></div>' +
    '<input class="tag_input" type=\'text\' placeholder="Search..." />' +
    '<div class="list_autocomplete">' +
    '</div>' +
    '</div>';

let autoElems = document.getElementsByClassName("autocomplete");

for (let i =0; i < autoElems.length; i++) {
    let elem = autoElems[i];
    elem.inputTags = [];
    elem.inputTagsLower = [];
    if (elem.hasAttribute("data-options")) {
        elem.inputTags = JSON.parse(elem.getAttribute("data-options"));
        elem.inputTagsLower = elem.inputTags.map(v => v.toLowerCase());
    } else if (elem.hasAttribute("src")) {
        $.getJSON( elem.getAttribute("src"), function( data ) {
            elem.inputTags = data;
            elem.inputTagsLower = elem.inputTags.map(v => v.toLowerCase());
        });
    }

    elem.innerHTML = template;
    let tagDiv = elem.getElementsByClassName("box_tag")[0];
    let tagInput = elem.getElementsByClassName("tag_input")[0];
    let listDiv = elem.getElementsByClassName("list_autocomplete")[0];

    if (elem.hasAttribute("input-id")) {
        tagInput.id = elem.getAttribute("input-id");
    }
    if (elem.hasAttribute("required")) {
        tagInput.setAttribute("required", "");
    }

    elem.apply = function() {
        let tagHtml = "";
        let tags = elem.tagArrayListSetView();
        for (let j = 0; j < tags.length; j++) {
            let tag = tags[j];
            tagHtml += "<div class='input-tag'><span>" + tag + "</span><div class='delete-tag' onclick='this.parentNode.parentNode.parentNode.parentNode.deleteTag(" + j + ")'>&times;</div></div>";
        }
        let listHTML = "";
        if (elem.searchFilter.length > 1) {
            let filterLower = elem.searchFilter.toLowerCase();
            for (let j = 0; j < elem.inputTagsLower.length; j++) {
                let tagLower = elem.inputTagsLower[j];
                let tag = elem.inputTags[j];
                if (tagLower.startsWith(filterLower)) {
                    listHTML += '<div class="list_autocomplete_child';
                    if (j == elem.selectedIndex) value += " active_autocomplete";
                    listHTML += '"';
                    listHTML += ' onclick=\'this.parentNode.parentNode.parentNode.setTagToView("' + tag + '")\'>' + tag + '</div>';
                }
            }
        }
        tagDiv.innerHTML = tagHtml;
        listDiv.innerHTML = listHTML;
    };

    elem.addEventListener("input", function(e) {
        let value = tagInput.value;
        elem.inputText = value;
        elem.searchFilter = value;
        elem.apply();
    });

    elem.addEventListener("keydown", function(e) {
        switch (e.key + "") {
            case "ArrowUp": // arrow up
                if (elem.selectedIndex == -1) elem.selectedIndex = 0;
                else
                    elem.selectedIndex--;
                elem.apply();
                break;
            case "ArrowDown": // arrow down
                if (elem.selectedIndex >= l) elem.selectedIndex = 0;
                else
                    elem.selectedIndex++;
                elem.apply();
                break;
            case "Enter": // enter
                if (elem.selectedIndex == -1 || elem.selectedValue == '') break;
                else
                    elem.setTagToView(elem.selectedValue);
                elem.selectedIndex = -1;
                elem.selectedValue = '';
                elem.apply();
                break;
            case "Backspace": // backspace
                elem.removeEachTag();
                elem.apply();
                break;
        }
    });

    elem.searchFilter = '';
    elem.selectedIndex = -1;

    elem.tagArrayListSetView = function () {
        if (elem.listSetToView === undefined) {
            return [];
        }
        return elem.listSetToView.split(',').filter(function (tag) {
            return tag !== '';
        });
    };

    elem.deleteTag = function (key) {
        var tagArray;
        tagArray = elem.tagArrayListSetView();
        if (tagArray.length > 0 && elem.searchFilter.length === 0 && key === undefined) {
            tagArray.pop();
        } else {
            if (key !== undefined) {
                tagArray.splice(key, 1);
            }
        }
        let result =elem.listSetToView = tagArray.join(',');
        elem.apply();
        return result;
    };

    elem.removeEachTag = function () {
        var tagArray;
        tagArray = elem.tagArrayListSetView();
        if (tagArray.length > 0 && elem.searchFilter.length === 0) {
            tagArray.splice(tagArray.length -1, 1);
        }
        let result = elem.listSetToView = tagArray.join(',');
        elem.apply();
        return result;
    };

    elem.setTagToView = function (data) {
        var tagArray;
        if (data.length === 0) return;

        tagArray = elem.tagArrayListSetView();
        if (elem.limitSetTag === undefined) {
            if (tagArray.indexOf(data) == -1) {
                tagArray.push(data);
                elem.listSetToView = tagArray.join(',');
                elem.searchFilter = '';
                tagInput.value = '';
            }
            else {
                elem.searchFilter = '';
                tagInput.value = '';
            }
        }
        else if (elem.limitSetTag >= 1) {
            if (tagArray.indexOf(data) == -1 && tagArray.length < elem.limitSetTag) {
                tagArray.push(data);
                elem.listSetToView = tagArray.join(',');
                elem.searchFilter = '';
                tagInput.value = '';
            }
            else {
                elem.searchFilter = '';
                tagInput.value = '';
            }
        }
        elem.apply();
    };

    elem.setSeletedValue = function (index, data) {
        if (index == elem.selectedIndex) elem.selectedValue = data;
        return true;
    };

    elem.apply();
}