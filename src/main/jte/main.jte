@import java.util.List
@import java.util.Map
@import link.locutus.discord.commands.manager.v2.binding.WebStore
@import link.locutus.discord.web.jooby.WebRoot
@import link.locutus.discord.config.Settings
@import gg.jte.Content
@param WebStore ws
@param Content content
@param String title
@param List<Map.Entry<String, String>> navbar
<!DOCTYPE html>
<html lang="en" data-bs-core="modern">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="light">

    <title>${title}</title>
<%--    <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css">--%>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/Halfmoon/2.0.1/css/halfmoon.min.css"/>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/halfmoon@2.0.1/css/cores/halfmoon.modern.css">
    <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css">
<%--    <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta3/dist/css/bootstrap-select.min.css">--%>
<%--    <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/all.css">--%>
<%--    <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/command.css">--%>
<%--    <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/embed.css">--%>
<%--    <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/datatables.css">--%>

    <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="https://leeoniya.github.io/uPlot/dist/uPlot.min.css">

    <%--    <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/2.4.4/umd/popper.js"></script>--%>
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
<%--    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>--%>
    <!-- Latest compiled and minified JavaScript -->
<%--    <script src="https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta3/dist/js/bootstrap-select.min.js"></script>--%>
<%--    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.4.1/highlight.min.js"></script>--%>
<%--    <script src="https://unpkg.com/twemoji@14.0.2/dist/twemoji.min.js"></script>--%>

<%--    <script src="https://cdn.datatables.net/1.11.3/js/jquery.dataTables.min.js"></script>--%>

<%--    <script src="https://leeoniya.github.io/uPlot/dist/uPlot.iife.min.js"></script>--%>

<%--    <script src="/js/default.js"></script>--%>
<%--    <script src="/js/command.js"></script>--%>
<%--    <script src="/js/embed.js"></script>--%>
<%--    <script src="/js/components.js"></script>--%>
<%--    <script src="/js/pnwtable.js"></script>--%>

<%--    <script src="/js/colorutil.js"></script>--%>
<%--    <script src="/js/pnwtimechart.js"></script>--%>

<%--    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>--%>

    <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.rawgit.com/afeld/bootstrap-toc/v0.3.0/dist/bootstrap-toc.min.css">
</head>
<body class="ps-md-sbwidth">
@template.navbar(ws = ws)

<!-- Sidebar -->
<nav id="sidebar" class="sidebar offcanvas-start offcanvas-md" tabindex="-1" tabindex="-1" role="dialog">
    <div class="offcanvas-header position-relative justify-content-start flex-shrink-0 py-0 mt-2">
        <a href="#top" class="visually-hidden-focusable btn btn-link fw-bold antialiased bg-body-secondary position-absolute top-50 end-0 translate-middle-y me-3 z-1">Skip to main content</a>
        <div class="col-auto ms-auto">
            <button type="button" class="btn-close d-md-none ms-1" data-bs-dismiss="offcanvas" aria-label="Close" data-bs-target="#sidebar"></button>
        </div>
    </div>
    <div class="offcanvas-body position-relative p-0">
        <div class="filter-docs sticky-top p-3">
            <input type="text" class="form-control search" placeholder="Filter docs" aria-label="Filter docs">
            <div class="mt-1">
                <small>Press <kbd class="text-body" style="font-size: 10px; background-color: hsla(var(--bs-emphasis-color-hsl), 0.1)">/</kbd> to focus</small>
            </div>
        </div>
        <ul class="sidebar-nav list p-3 pt-0"><li class="position-relative mt-2">
                <h6 class="sidebar-header">
                    Getting started
                </h6>
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Introduction | Download | Customize and theme | Browsers and devices | Accessibility | RTL | Color modes | </span>
            </li><li class="position-relative">
                <hr class="sidebar-divider">
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Introduction | Download | Customize and theme | Browsers and devices | Accessibility | RTL | Color modes | </span>
            </li><li id="sidebar-introduction" class="nav-item">
                <a href="/docs/introduction/" class="nav-link ">
                    <span class="sidebar-search-name">Introduction</span>
                </a>
            </li><li id="sidebar-download" class="nav-item">
                <a href="/docs/download/" class="nav-link ">
                    <span class="sidebar-search-name">Download</span>
                </a>
            </li><li id="sidebar-customize-and-theme" class="nav-item">
                <a href="/docs/customize-and-theme/" class="nav-link ">
                    <span class="sidebar-search-name">Customize and theme</span>
                </a>
            </li><li id="sidebar-browsers-and-devices" class="nav-item">
                <a href="/docs/browsers-and-devices/" class="nav-link ">
                    <span class="sidebar-search-name">Browsers and devices</span>
                </a>
            </li><li id="sidebar-accessibility" class="nav-item">
                <a href="/docs/accessibility/" class="nav-link ">
                    <span class="sidebar-search-name">Accessibility</span>
                </a>
            </li><li id="sidebar-rtl" class="nav-item">
                <a href="/docs/rtl/" class="nav-link ">
                    <span class="sidebar-search-name">RTL</span>
                </a>
            </li><li id="sidebar-color-modes" class="nav-item">
                <a href="/docs/color-modes/" class="nav-link ">
                    <span class="sidebar-search-name">Color modes</span>
                </a>
            </li><li class="position-relative mt-4">
                <h6 class="sidebar-header">
                    Layout
                </h6>
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Breakpoints | Containers | Grid system | Columns | Gutters | Z-index | </span>
            </li><li class="position-relative">
                <hr class="sidebar-divider">
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Breakpoints | Containers | Grid system | Columns | Gutters | Z-index | </span>
            </li><li id="sidebar-breakpoints" class="nav-item">
                <a href="/docs/breakpoints/" class="nav-link ">
                    <span class="sidebar-search-name">Breakpoints</span>
                </a>
            </li><li id="sidebar-containers" class="nav-item">
                <a href="/docs/containers/" class="nav-link ">
                    <span class="sidebar-search-name">Containers</span>
                </a>
            </li><li id="sidebar-grid" class="nav-item">
                <a href="/docs/grid/" class="nav-link ">
                    <span class="sidebar-search-name">Grid system</span>
                </a>
            </li><li id="sidebar-columns" class="nav-item">
                <a href="/docs/columns/" class="nav-link ">
                    <span class="sidebar-search-name">Columns</span>
                </a>
            </li><li id="sidebar-gutters" class="nav-item">
                <a href="/docs/gutters/" class="nav-link ">
                    <span class="sidebar-search-name">Gutters</span>
                </a>
            </li><li id="sidebar-z-index" class="nav-item">
                <a href="/docs/z-index/" class="nav-link ">
                    <span class="sidebar-search-name">Z-index</span>
                </a>
            </li><li class="position-relative mt-4">
                <h6 class="sidebar-header">
                    Content
                </h6>
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Typography | Images | Tables | Figures | </span>
            </li><li class="position-relative">
                <hr class="sidebar-divider">
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Typography | Images | Tables | Figures | </span>
            </li><li id="sidebar-typography" class="nav-item">
                <a href="/docs/typography/" class="nav-link ">
                    <span class="sidebar-search-name">Typography</span>
                </a>
            </li><li id="sidebar-images" class="nav-item">
                <a href="/docs/images/" class="nav-link ">
                    <span class="sidebar-search-name">Images</span>
                </a>
            </li><li id="sidebar-tables" class="nav-item">
                <a href="/docs/tables/" class="nav-link ">
                    <span class="sidebar-search-name">Tables</span>
                </a>
            </li><li id="sidebar-figures" class="nav-item">
                <a href="/docs/figures/" class="nav-link ">
                    <span class="sidebar-search-name">Figures</span>
                </a>
            </li><li class="position-relative mt-4">
                <h6 class="sidebar-header">
                    Forms
                </h6>
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Form overview | Form controls | Select | Checkbox and radio | Range | Input group | Floating labels | Form layout | Form validation | </span>
            </li><li class="position-relative">
                <hr class="sidebar-divider">
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Form overview | Form controls | Select | Checkbox and radio | Range | Input group | Floating labels | Form layout | Form validation | </span>
            </li><li id="sidebar-form-overview" class="nav-item">
                <a href="/docs/form-overview/" class="nav-link ">
                    <span class="sidebar-search-name">Form overview</span>
                </a>
            </li><li id="sidebar-form-controls" class="nav-item">
                <a href="/docs/form-controls/" class="nav-link ">
                    <span class="sidebar-search-name">Form controls</span>
                </a>
            </li><li id="sidebar-select" class="nav-item">
                <a href="/docs/select/" class="nav-link ">
                    <span class="sidebar-search-name">Select</span>
                </a>
            </li><li id="sidebar-checkbox-and-radio" class="nav-item">
                <a href="/docs/checkbox-and-radio/" class="nav-link ">
                    <span class="sidebar-search-name">Checkbox and radio</span>
                </a>
            </li><li id="sidebar-range" class="nav-item">
                <a href="/docs/range/" class="nav-link ">
                    <span class="sidebar-search-name">Range</span>
                </a>
            </li><li id="sidebar-input-group" class="nav-item">
                <a href="/docs/input-group/" class="nav-link ">
                    <span class="sidebar-search-name">Input group</span>
                </a>
            </li><li id="sidebar-floating-labels" class="nav-item">
                <a href="/docs/floating-labels/" class="nav-link ">
                    <span class="sidebar-search-name">Floating labels</span>
                </a>
            </li><li id="sidebar-form-layout" class="nav-item">
                <a href="/docs/form-layout/" class="nav-link ">
                    <span class="sidebar-search-name">Form layout</span>
                </a>
            </li><li id="sidebar-form-validation" class="nav-item">
                <a href="/docs/form-validation/" class="nav-link ">
                    <span class="sidebar-search-name">Form validation</span>
                </a>
            </li><li class="position-relative mt-4">
                <h6 class="sidebar-header">
                    Components
                </h6>
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Accordion | Alerts | Badges | Breadcrumb | Buttons | Button group | Cards | Carousel | Collapse | Close button | Dropdowns | List group | Modal | Navbar | Navs and tabs | Offcanvas | Pagination | Placeholders | Popovers | Progress | Scrollspy | Sidebar | Spinners | Toasts | Tooltips | </span>
            </li><li class="position-relative">
                <hr class="sidebar-divider">
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Accordion | Alerts | Badges | Breadcrumb | Buttons | Button group | Cards | Carousel | Collapse | Close button | Dropdowns | List group | Modal | Navbar | Navs and tabs | Offcanvas | Pagination | Placeholders | Popovers | Progress | Scrollspy | Sidebar | Spinners | Toasts | Tooltips | </span>
            </li><li id="sidebar-accordion" class="nav-item">
                <a href="/docs/accordion/" class="nav-link ">
                    <span class="sidebar-search-name">Accordion</span>
                </a>
            </li><li id="sidebar-alerts" class="nav-item">
                <a href="/docs/alerts/" class="nav-link ">
                    <span class="sidebar-search-name">Alerts</span>
                </a>
            </li><li id="sidebar-badges" class="nav-item">
                <a href="/docs/badges/" class="nav-link ">
                    <span class="sidebar-search-name">Badges</span>
                </a>
            </li><li id="sidebar-breadcrumb" class="nav-item">
                <a href="/docs/breadcrumb/" class="nav-link ">
                    <span class="sidebar-search-name">Breadcrumb</span>
                </a>
            </li><li id="sidebar-buttons" class="nav-item">
                <a href="/docs/buttons/" class="nav-link ">
                    <span class="sidebar-search-name">Buttons</span>
                </a>
            </li><li id="sidebar-button-group" class="nav-item">
                <a href="/docs/button-group/" class="nav-link ">
                    <span class="sidebar-search-name">Button group</span>
                </a>
            </li><li id="sidebar-cards" class="nav-item">
                <a href="/docs/cards/" class="nav-link ">
                    <span class="sidebar-search-name">Cards</span>
                </a>
            </li><li id="sidebar-carousel" class="nav-item">
                <a href="/docs/carousel/" class="nav-link ">
                    <span class="sidebar-search-name">Carousel</span>
                </a>
            </li><li id="sidebar-collapse" class="nav-item">
                <a href="/docs/collapse/" class="nav-link ">
                    <span class="sidebar-search-name">Collapse</span>
                </a>
            </li><li id="sidebar-close-button" class="nav-item">
                <a href="/docs/close-button/" class="nav-link ">
                    <span class="sidebar-search-name">Close button</span>
                </a>
            </li><li id="sidebar-dropdowns" class="nav-item">
                <a href="/docs/dropdowns/" class="nav-link ">
                    <span class="sidebar-search-name">Dropdowns</span>
                </a>
            </li><li id="sidebar-list-group" class="nav-item">
                <a href="/docs/list-group/" class="nav-link ">
                    <span class="sidebar-search-name">List group</span>
                </a>
            </li><li id="sidebar-modal" class="nav-item">
                <a href="/docs/modal/" class="nav-link ">
                    <span class="sidebar-search-name">Modal</span>
                </a>
            </li><li id="sidebar-navbar" class="nav-item">
                <a href="/docs/navbar/" class="nav-link ">
                    <span class="sidebar-search-name">Navbar</span>
                </a>
            </li><li id="sidebar-navs-and-tabs" class="nav-item">
                <a href="/docs/navs-and-tabs/" class="nav-link ">
                    <span class="sidebar-search-name">Navs and tabs</span>
                </a>
            </li><li id="sidebar-offcanvas" class="nav-item">
                <a href="/docs/offcanvas/" class="nav-link active" aria-current="page">
                    <span class="sidebar-search-name">Offcanvas</span>
                </a>
            </li><li id="sidebar-pagination" class="nav-item">
                <a href="/docs/pagination/" class="nav-link ">
                    <span class="sidebar-search-name">Pagination</span>
                </a>
            </li><li id="sidebar-placeholders" class="nav-item">
                <a href="/docs/placeholders/" class="nav-link ">
                    <span class="sidebar-search-name">Placeholders</span>
                </a>
            </li><li id="sidebar-popovers" class="nav-item">
                <a href="/docs/popovers/" class="nav-link ">
                    <span class="sidebar-search-name">Popovers</span>
                </a>
            </li><li id="sidebar-progress" class="nav-item">
                <a href="/docs/progress/" class="nav-link ">
                    <span class="sidebar-search-name">Progress</span>
                </a>
            </li><li id="sidebar-scrollspy" class="nav-item">
                <a href="/docs/scrollspy/" class="nav-link ">
                    <span class="sidebar-search-name">Scrollspy</span>
                </a>
            </li><li id="sidebar-sidebar" class="nav-item">
                <a href="/docs/sidebar/" class="nav-link ">
                    <span class="sidebar-search-name">Sidebar</span>
                </a>
            </li><li id="sidebar-spinners" class="nav-item">
                <a href="/docs/spinners/" class="nav-link ">
                    <span class="sidebar-search-name">Spinners</span>
                </a>
            </li><li id="sidebar-toasts" class="nav-item">
                <a href="/docs/toasts/" class="nav-link ">
                    <span class="sidebar-search-name">Toasts</span>
                </a>
            </li><li id="sidebar-tooltips" class="nav-item">
                <a href="/docs/tooltips/" class="nav-link ">
                    <span class="sidebar-search-name">Tooltips</span>
                </a>
            </li><li class="position-relative mt-4">
                <h6 class="sidebar-header">
                    Helpers
                </h6>
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Clearfix | Color and background | Colored links | Focus ring | Icon link | Position | Ratios | Stacks | Stretched link | Text truncation | Vertical rule | Visually hidden | </span>
            </li><li class="position-relative">
                <hr class="sidebar-divider">
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Clearfix | Color and background | Colored links | Focus ring | Icon link | Position | Ratios | Stacks | Stretched link | Text truncation | Vertical rule | Visually hidden | </span>
            </li><li id="sidebar-clearfix" class="nav-item">
                <a href="/docs/clearfix/" class="nav-link ">
                    <span class="sidebar-search-name">Clearfix</span>
                </a>
            </li><li id="sidebar-color-and-background" class="nav-item">
                <a href="/docs/color-and-background/" class="nav-link ">
                    <span class="sidebar-search-name">Color and background</span>
                </a>
            </li><li id="sidebar-colored-links" class="nav-item">
                <a href="/docs/colored-links/" class="nav-link ">
                    <span class="sidebar-search-name">Colored links</span>
                </a>
            </li><li id="sidebar-focus-ring" class="nav-item">
                <a href="/docs/focus-ring/" class="nav-link ">
                    <span class="sidebar-search-name">Focus ring</span>
                </a>
            </li><li id="sidebar-icon-link" class="nav-item">
                <a href="/docs/icon-link/" class="nav-link ">
                    <span class="sidebar-search-name">Icon link</span>
                </a>
            </li><li id="sidebar-position" class="nav-item">
                <a href="/docs/position/" class="nav-link ">
                    <span class="sidebar-search-name">Position</span>
                </a>
            </li><li id="sidebar-ratios" class="nav-item">
                <a href="/docs/ratios/" class="nav-link ">
                    <span class="sidebar-search-name">Ratios</span>
                </a>
            </li><li id="sidebar-stacks" class="nav-item">
                <a href="/docs/stacks/" class="nav-link ">
                    <span class="sidebar-search-name">Stacks</span>
                </a>
            </li><li id="sidebar-stretched-link" class="nav-item">
                <a href="/docs/stretched-link/" class="nav-link ">
                    <span class="sidebar-search-name">Stretched link</span>
                </a>
            </li><li id="sidebar-text-truncation" class="nav-item">
                <a href="/docs/text-truncation/" class="nav-link ">
                    <span class="sidebar-search-name">Text truncation</span>
                </a>
            </li><li id="sidebar-vertical-rule" class="nav-item">
                <a href="/docs/vertical-rule/" class="nav-link ">
                    <span class="sidebar-search-name">Vertical rule</span>
                </a>
            </li><li id="sidebar-visually-hidden" class="nav-item">
                <a href="/docs/visually-hidden/" class="nav-link ">
                    <span class="sidebar-search-name">Visually hidden</span>
                </a>
            </li><li class="position-relative mt-4">
                <h6 class="sidebar-header">
                    Utilities
                </h6>
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Background utilities | Border utilities | Box shadow utilities | Color utilities | Display utilities | Flex utilities | Float utilities | Interaction utilities | Link utilities | Object fit utilities | Opacity utilities | Overflow utilities | Position utilities | Sizing utilities | Spacing utilities | Text utilities | Vertical align utilities | Visibility utilities | Z-index utilities | </span>
            </li><li class="position-relative">
                <hr class="sidebar-divider">
                <span class="sidebar-search-name position-absolute top-0 start-0 z-n1 invisible">Background utilities | Border utilities | Box shadow utilities | Color utilities | Display utilities | Flex utilities | Float utilities | Interaction utilities | Link utilities | Object fit utilities | Opacity utilities | Overflow utilities | Position utilities | Sizing utilities | Spacing utilities | Text utilities | Vertical align utilities | Visibility utilities | Z-index utilities | </span>
            </li><li id="sidebar-background-utilities" class="nav-item">
                <a href="/docs/background-utilities/" class="nav-link ">
                    <span class="sidebar-search-name">Background utilities</span>
                </a>
            </li><li id="sidebar-border-utilities" class="nav-item">
                <a href="/docs/border-utilities/" class="nav-link ">
                    <span class="sidebar-search-name">Border utilities</span>
                </a>
            </li><li id="sidebar-box-shadow-utilities" class="nav-item">
                <a href="/docs/box-shadow-utilities/" class="nav-link ">
                    <span class="sidebar-search-name">Box shadow utilities</span>
                </a>
            </li><li id="sidebar-color-utilities" class="nav-item">
                <a href="/docs/color-utilities/" class="nav-link ">
                    <span class="sidebar-search-name">Color utilities</span>
                </a>
            </li><li id="sidebar-z-index-utilities" class="nav-item">
                <a href="/docs/z-index-utilities/" class="nav-link ">
                    <span class="sidebar-search-name">Z-index utilities</span>
                </a>
            </li></ul>
    </div>
</nav>
<div class="container">
    ${content}
</div>
<footer class="bd-footer py-5 mt-5 bg-light">
    <div class="container py-5">
        <div class="row">
            <div class="col-12">
                Hello World
            </div>
        </div>
    </div>
</footer>
<%--  Add navbar  --%>
<%--@template.navbar(ws = ws)--%>
<%--add sidebar jte --%>
<%--@template.sidebar(ws = ws)--%>
<%--<script>--%>
<%--var pathname = window.location.pathname;--%>
<%--if (pathname.startsWith("/")) pathname = pathname.substring(1);--%>
<%--var pathSplit = pathname.split("/");--%>

<%--var guild_id = "";--%>
<%--if (/^[0-9]+$/.test(pathSplit[0])) {--%>
<%--    guild_id = (pathSplit[0]);--%>
<%--}--%>
<%--if (guild_id == "") document.getElementById("navbar-search-form").remove();--%>

<%--function search() {--%>
<%--    var value = document.getElementById("navbar-search").value;--%>
<%--    var url = "${WebRoot.REDIRECT}/" + guild_id + "/search/" + value;--%>
<%--    window.location.href = url;--%>
<%--    return false;--%>
<%--}--%>
<%--</script>--%>
</body>
</html>