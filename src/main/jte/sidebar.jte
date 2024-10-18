@import link.locutus.discord.commands.manager.v2.binding.WebStore
@param WebStore ws
<nav id="sidebar" class="sidebar offcanvas-start offcanvas-md" tabindex="-1" tabindex="-1" role="dialog">
    <div class="offcanvas-header position-relative justify-content-start flex-shrink-0 py-0 mt-2">
        <a href="#top" class="visually-hidden-focusable btn btn-link fw-bold antialiased bg-body-secondary position-absolute top-50 end-0 translate-middle-y me-3 z-1">Skip to main content</a>
        <div class="col-auto ms-auto">
            <button type="button" class="btn-close d-md-none ms-1" data-bs-dismiss="offcanvas" aria-label="Close" data-bs-target="#sidebar"></button>
        </div>
    </div>
    <div class="offcanvas-body position-relative p-0" id="sidebarlist">
        <div class="filter-docs sticky-top p-3">
            <input type="text" id="sb-s-i" class="form-control search" placeholder="Filter pages" aria-label="Filter pages" onkeyup="filtersearch()">
            <div class="mt-1">
                <small>Press <kbd class="text-body" style="font-size: 10px;">/</kbd> to focus</small>
            </div>
        </div>
    </div>
</nav>
<script type="text/javascript">
// Function to read cookie
function readCookie(name) {
    var nameEQ = name + "=";
    var ca = document.cookie.split(";");
    for (var i=0; i < ca.length; i++) {
        var c = ca[i];
        while (c.charAt(0) === " ") {
            c = c.substring(1, c.length);
        }
        if (c.indexOf(nameEQ) === 0) {
            return c.substring(nameEQ.length,c.length);
        }
    }
    return null;
}

// Function to set cookie
function setCookie(name, value, days) {
    var expires = "";
    if (days) {
        var date = new Date();
        date.setTime(date.getTime() + (days*24*60*60*1000));
        expires = "; expires=" + date.toUTCString();
    }
    document.cookie = name + "=" + (value || "")  + expires + "; path=/";
}
// Set system preference if cookie (with saved preference) not present
var systemColorMode = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
let cookieVal = readCookie("halfmoonColorMode");
let setColorMode = cookieVal === null ? systemColorMode : cookieVal;
document.documentElement.setAttribute("data-bs-theme", setColorMode);

// Function to toggle dark mode and set cookie
function toggleDarkMode() {
    var rootPreference = document.documentElement.getAttribute("data-bs-theme");
    if (rootPreference === "light" || rootPreference === null) {
        document.documentElement.setAttribute("data-bs-theme", "dark");
        setCookie("halfmoonColorMode", "dark", 365);
    } else {
        document.documentElement.setAttribute("data-bs-theme", "light");
        setCookie("halfmoonColorMode", "light", 365);
    }
}
// Init sidebar docs filtering
var sidebar = document.getElementById("sidebar");
function createNestedList(jsonObject, parentElement, path = '') {
    var ul = document.createElement('ul');
    if (path === '') {
        ul.id = 'list';
    }
    ul.className = 'sidebar-nav p-3 pt-0';
    for (var key in jsonObject) {
        var li = document.createElement('li');
        li.className = 'nav-item';
        if (typeof jsonObject[key] === 'object') {
            var h6 = document.createElement('h6');
            h6.className = 'sidebar-header';
            h6.textContent = key;
            li.appendChild(h6);
            var newPath = (path ? path : '') + '/' + key;
            if (path.startsWith('wiki')) {
                newPath = "https://github.com/xdnw/locutus/wiki";
            }
            var nestedUl = createNestedList(jsonObject[key], li, newPath);
            nestedUl.id = key;
            li.appendChild(nestedUl);
        } else {
            var a = document.createElement('a');
            a.className = 'nav-link';
            a.href = ((path ? path : "") + "/" + key).toLowerCase();
            let name = (typeof jsonObject[key] === 'string' && jsonObject[key] !== '') ? jsonObject[key] : key;
            var span = document.createElement('span');
            span.className = 'sidebar-search-name';
            span.textContent = name;
            a.appendChild(span);
            li.appendChild(a);
        }

        ul.appendChild(li);
    }
    parentElement.appendChild(ul);
    return ul;
}

function setupSidebar() {
    const src = sidebar.getAttribute("data-src");
    if (src) {
        createNestedList(JSON.parse(atob(src)), document.getElementById("sidebarlist"));
        sidebar.removeAttribute("data-src");
    }
}

unhide_parent = (ele) => {
    if(ele.parentNode.parentNode.style.display=="none") {
        ele.parentNode.parentNode.style.display='';
        unhide_parent(ele.parentNode.parentNode)
    }
};
function filtersearch() {
    var input, filter, ul, tr, search, i, txtValue;
    input = document.getElementById("sb-s-i");
    filter = input.value.toUpperCase();
    ul = document.getElementById("list");
    tr = ul.getElementsByTagName("li");
    for (i = 0; i < tr.length; i++) {
        search = tr[i].getElementsByTagName("a")[0];
        txtValue = search.textContent || search.innerText;
        if (txtValue.toUpperCase().indexOf(filter) > -1) {
            tr[i].style.display = "";
            unhide_parent(tr[i])
        } else {
            tr[i].style.display = "none";
        }
    }
}
setupSidebar();
</script>