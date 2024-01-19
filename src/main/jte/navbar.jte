@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.config.Settings
@import link.locutus.discord.web.jooby.WebRoot
@import java.util.Map
@param WebStore ws
<nav class="navbar navbar-expand-md border-bottom">
    <div class="container-fluid">
        <div class="row d-flex w-100">
            <div class="col-auto">
                <div class="btn-group">
                    <button type="button" data-bs-toggle="offcanvas" data-bs-target="#sidebar" class="btn btn btn-secondary d-md-none">
                        <i class="bi bi-layout-sidebar"></i>
                    </button>
                    <a href="/" class="d-flex align-items-center d-block btn btn-sm btn-secondary">
                        <img src="https://cdn.discordapp.com/avatars/672237266940198960/0d78b819d401a8f983ab16242de195da.webp" alt="Logo" width="18" height="18">
                        <span class="d-none d-md-block d-lg-block inline me-2">${Settings.INSTANCE.WEB.INTERFACE_NAME}</span>
                    </a>
                    <a type="button" class="btn btn btn-secondary d-md-block" aria-label="Toggle dark mode" onclick="toggleDarkMode()">
                        <i class="bi bi-moon-stars-fill" style="color:gray"></i>
                    </a>
                    <div class="d-inline text-truncate mt-1">
                        @for (Map.Entry<String, String> entry : ws.getPathLinks().entrySet())
                            <span class="mx-1">/</span>
                            <a href="${entry.getValue()}/" class="simple-link fw-bold">${entry.getKey()}</a>
                        @endfor
                    </div>
                </div>
            </div>
            <div class="col">
                <form class="d-flex" role="search" id="navbar-search-form" onsubmit="return search()">
                    <input class="form-control rounded-end-0" type="search" placeholder="Search pages..." aria-label="Search">
                    <button type="submit" class="btn btn-light text-nowrap rounded-start-0"><i class="bi bi-search"></i></button>
                </form>
            </div>
            <div class="col-auto">
                <a class="btn btn btn-danger fw-bold" href="${WebRoot.REDIRECT}/logout">Logout</a>
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
    $(document).ready(function(){
      $("#myInput").on("keyup", function() {
        var value = $(this).val().toLowerCase();
        $("#myTable tr").filter(function() {
          $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)
        });
      });
    });

    new List("sidebar", {valueNames: ["sidebar-search-name",]});
    document.addEventListener("keydown", function (event) {
        if (!document.querySelector(".form-control:focus")) {
            event = event || window.event;
            if (event.which == 191) {
                document.querySelector(".filter-docs .form-control").focus();
                event.preventDefault();
            }
        }
    });
</script>