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
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="light">

    <title>${title}</title>
     <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css">
     <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css">
     <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta3/dist/css/bootstrap-select.min.css">
     <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/all.css">
     <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/command.css">
     <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/embed.css">
     <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="/css/datatables.css">

     <link rel="stylesheet"  media="print" onload="this.media='all'" type="text/css" href="https://leeoniya.github.io/uPlot/dist/uPlot.min.css">

<%--    <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/2.4.4/umd/popper.js"></script>--%>
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <!-- Latest compiled and minified JavaScript -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta3/dist/js/bootstrap-select.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.4.1/highlight.min.js"></script>
    <script src="https://unpkg.com/twemoji@14.0.2/dist/twemoji.min.js"></script>

    <script src="https://cdn.datatables.net/1.11.3/js/jquery.dataTables.min.js"></script>

    <script src="https://leeoniya.github.io/uPlot/dist/uPlot.iife.min.js"></script>

    <script src="/js/default.js"></script>
    <script src="/js/command.js"></script>
    <script src="/js/embed.js"></script>
    <script src="/js/components.js"></script>
    <script src="/js/pnwtable.js"></script>

    <script src="/js/colorutil.js"></script>
    <script src="/js/pnwtimechart.js"></script>

    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>

     <link rel="stylesheet"  media="print" onload="this.media='all'" href="https://cdn.rawgit.com/afeld/bootstrap-toc/v0.3.0/dist/bootstrap-toc.min.css">

<%--    <link rel="canonical" href="https://getbootstrap.com/docs/5.0/components/offcanvas/">--%>
<%--    <link href="https://getbootstrap.com/docs/5.0/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-EVSTQN3/azprG1Anm3QDgpJLIm9Nao0Yz1ztcQTwFspd3yD65VohhpuuCOmLASjC" crossorigin="anonymous">--%>

    <link href="/css/t3.css" rel="stylesheet">
</head>
<body>
<div class="skippy visually-hidden-focusable overflow-hidden">
    <div class="container-xl">
        <a class="d-inline-flex p-2 m-1" href="#content">Skip to main content</a>
        <a class="d-none d-md-inline-flex p-2 m-1" href="#bd-docs-nav">Skip to docs navigation</a>
    </div>
</div>

<div class="d-block px-3 py-2 text-center text-bold skippy">
    <a href="https://getbootstrap.com/" class="text-white text-decoration-none">There's a newer version of Bootstrap!</a>
</div><nav class="bd-subnavbar py-2" aria-label="Secondary navigation">
    <div class="container-xxl d-flex align-items-md-center">
        <form class="bd-search position-relative me-auto">
            <input type="search" class="form-control" id="search-input" placeholder="Search docs..." aria-label="Search docs for..." autocomplete="off" data-bd-docs-version="5.0">
        </form>

        <div class="dropdown ms-3">
            <button class="btn btn-bd-light dropdown-toggle" id="bd-versions" data-bs-toggle="dropdown" aria-expanded="false" data-bs-display="static">
                <span class="d-none d-lg-inline">Bootstrap</span> v5.0
            </button>
            <ul class="dropdown-menu dropdown-menu-end" aria-labelledby="bd-versions">
                <li><h6 class="dropdown-header">v5 releases</h6></li>
                <li>
                    <a class="dropdown-item" href="https://getbootstrap.com/docs/5.2/components/offcanvas/">
                        Latest (5.2.x)
                    </a>
                </li>
                <li>
                    <a class="dropdown-item" href="https://getbootstrap.com/docs/5.1/components/offcanvas/">v5.1.3</a>
                </li>
                <li>
                    <a class="dropdown-item current" aria-current="true" href="/docs/5.0/components/offcanvas/">v5.0.2</a>
                </li>
                <li><hr class="dropdown-divider"></li>
                <li><a class="dropdown-item" href="https://getbootstrap.com/docs/4.6/">v4.6.x</a></li>
                <li><a class="dropdown-item" href="https://getbootstrap.com/docs/3.4/">v3.4.1</a></li>
                <li><a class="dropdown-item" href="https://getbootstrap.com/2.3.2/">v2.3.2</a></li>
                <li><hr class="dropdown-divider"></li>
                <li><a class="dropdown-item" href="/docs/versions/">All versions</a></li>
            </ul>
        </div>


        <button class="btn bd-sidebar-toggle d-md-none py-0 px-1 ms-3 order-3 collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#bd-docs-nav" aria-controls="bd-docs-nav" aria-expanded="false" aria-label="Toggle docs navigation">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" class="bi bi-expand" fill="currentColor" viewBox="0 0 16 16">
                <title>Expand</title>
                <path fill-rule="evenodd" d="M1 8a.5.5 0 0 1 .5-.5h13a.5.5 0 0 1 0 1h-13A.5.5 0 0 1 1 8zM7.646.146a.5.5 0 0 1 .708 0l2 2a.5.5 0 0 1-.708.708L8.5 1.707V5.5a.5.5 0 0 1-1 0V1.707L6.354 2.854a.5.5 0 1 1-.708-.708l2-2zM8 10a.5.5 0 0 1 .5.5v3.793l1.146-1.147a.5.5 0 0 1 .708.708l-2 2a.5.5 0 0 1-.708 0l-2-2a.5.5 0 0 1 .708-.708L7.5 14.293V10.5A.5.5 0 0 1 8 10z"/>
            </svg>

            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" class="bi bi-collapse" fill="currentColor" viewBox="0 0 16 16">
                <title>Collapse</title>
                <path fill-rule="evenodd" d="M1 8a.5.5 0 0 1 .5-.5h13a.5.5 0 0 1 0 1h-13A.5.5 0 0 1 1 8zm7-8a.5.5 0 0 1 .5.5v3.793l1.146-1.147a.5.5 0 0 1 .708.708l-2 2a.5.5 0 0 1-.708 0l-2-2a.5.5 0 1 1 .708-.708L7.5 4.293V.5A.5.5 0 0 1 8 0zm-.5 11.707l-1.146 1.147a.5.5 0 0 1-.708-.708l2-2a.5.5 0 0 1 .708 0l2 2a.5.5 0 0 1-.708.708L8.5 11.707V15.5a.5.5 0 0 1-1 0v-3.793z"/>
            </svg>

        </button>
    </div>
</nav>


<div class="container-xxl my-md-4 bd-layout">
    <aside class="bd-sidebar">
        <nav class="collapse bd-links" id="bd-docs-nav" aria-label="Docs navigation"><ul class="list-unstyled mb-0 py-3 pt-md-1">
                <li class="mb-1">
                    <button class="btn d-inline-flex align-items-center rounded collapsed" data-bs-toggle="collapse" data-bs-target="#getting-started-collapse" aria-expanded="false">
                        Getting started
                    </button>

                    <div class="collapse" id="getting-started-collapse">
                        <ul class="list-unstyled fw-normal pb-1 small">
                            <li><a href="/docs/5.0/getting-started/introduction/" class="d-inline-flex align-items-center rounded">Introduction</a></li>
                            <li><a href="/docs/5.0/getting-started/download/" class="d-inline-flex align-items-center rounded">Download</a></li>
                            <li><a href="/docs/5.0/getting-started/contents/" class="d-inline-flex align-items-center rounded">Contents</a></li>
                            <li><a href="/docs/5.0/getting-started/browsers-devices/" class="d-inline-flex align-items-center rounded">Browsers &amp; devices</a></li>
                            <li><a href="/docs/5.0/getting-started/javascript/" class="d-inline-flex align-items-center rounded">JavaScript</a></li>
                            <li><a href="/docs/5.0/getting-started/build-tools/" class="d-inline-flex align-items-center rounded">Build tools</a></li>
                            <li><a href="/docs/5.0/getting-started/webpack/" class="d-inline-flex align-items-center rounded">Webpack</a></li>
                            <li><a href="/docs/5.0/getting-started/parcel/" class="d-inline-flex align-items-center rounded">Parcel</a></li>
                            <li><a href="/docs/5.0/getting-started/accessibility/" class="d-inline-flex align-items-center rounded">Accessibility</a></li>
                            <li><a href="/docs/5.0/getting-started/rfs/" class="d-inline-flex align-items-center rounded">RFS</a></li>
                            <li><a href="/docs/5.0/getting-started/rtl/" class="d-inline-flex align-items-center rounded">RTL</a></li>
                        </ul>
                    </div>
                </li>
                <li class="mb-1">
                    <button class="btn d-inline-flex align-items-center rounded collapsed" data-bs-toggle="collapse" data-bs-target="#customize-collapse" aria-expanded="false">
                        Customize
                    </button>

                    <div class="collapse" id="customize-collapse">
                        <ul class="list-unstyled fw-normal pb-1 small">
                            <li><a href="/docs/5.0/customize/overview/" class="d-inline-flex align-items-center rounded">Overview</a></li>
                            <li><a href="/docs/5.0/customize/sass/" class="d-inline-flex align-items-center rounded">Sass</a></li>
                            <li><a href="/docs/5.0/customize/options/" class="d-inline-flex align-items-center rounded">Options</a></li>
                            <li><a href="/docs/5.0/customize/color/" class="d-inline-flex align-items-center rounded">Color</a></li>
                            <li><a href="/docs/5.0/customize/components/" class="d-inline-flex align-items-center rounded">Components</a></li>
                            <li><a href="/docs/5.0/customize/css-variables/" class="d-inline-flex align-items-center rounded">CSS variables</a></li>
                            <li><a href="/docs/5.0/customize/optimize/" class="d-inline-flex align-items-center rounded">Optimize</a></li>
                        </ul>
                    </div>
                </li>
                <li class="mb-1">
                    <button class="btn d-inline-flex align-items-center rounded collapsed" data-bs-toggle="collapse" data-bs-target="#layout-collapse" aria-expanded="false">
                        Layout
                    </button>

                    <div class="collapse" id="layout-collapse">
                        <ul class="list-unstyled fw-normal pb-1 small">
                            <li><a href="/docs/5.0/layout/breakpoints/" class="d-inline-flex align-items-center rounded">Breakpoints</a></li>
                            <li><a href="/docs/5.0/layout/containers/" class="d-inline-flex align-items-center rounded">Containers</a></li>
                            <li><a href="/docs/5.0/layout/grid/" class="d-inline-flex align-items-center rounded">Grid</a></li>
                            <li><a href="/docs/5.0/layout/columns/" class="d-inline-flex align-items-center rounded">Columns</a></li>
                            <li><a href="/docs/5.0/layout/gutters/" class="d-inline-flex align-items-center rounded">Gutters</a></li>
                            <li><a href="/docs/5.0/layout/utilities/" class="d-inline-flex align-items-center rounded">Utilities</a></li>
                            <li><a href="/docs/5.0/layout/z-index/" class="d-inline-flex align-items-center rounded">Z-index</a></li>
                        </ul>
                    </div>
                </li>
                <li class="mb-1">
                    <button class="btn d-inline-flex align-items-center rounded collapsed" data-bs-toggle="collapse" data-bs-target="#content-collapse" aria-expanded="false">
                        Content
                    </button>

                    <div class="collapse" id="content-collapse">
                        <ul class="list-unstyled fw-normal pb-1 small">
                            <li><a href="/docs/5.0/content/reboot/" class="d-inline-flex align-items-center rounded">Reboot</a></li>
                            <li><a href="/docs/5.0/content/typography/" class="d-inline-flex align-items-center rounded">Typography</a></li>
                            <li><a href="/docs/5.0/content/images/" class="d-inline-flex align-items-center rounded">Images</a></li>
                            <li><a href="/docs/5.0/content/tables/" class="d-inline-flex align-items-center rounded">Tables</a></li>
                            <li><a href="/docs/5.0/content/figures/" class="d-inline-flex align-items-center rounded">Figures</a></li>
                        </ul>
                    </div>
                </li>
                <li class="mb-1">
                    <button class="btn d-inline-flex align-items-center rounded collapsed" data-bs-toggle="collapse" data-bs-target="#forms-collapse" aria-expanded="false">
                        Forms
                    </button>

                    <div class="collapse" id="forms-collapse">
                        <ul class="list-unstyled fw-normal pb-1 small">
                            <li><a href="/docs/5.0/forms/overview/" class="d-inline-flex align-items-center rounded">Overview</a></li>
                            <li><a href="/docs/5.0/forms/form-control/" class="d-inline-flex align-items-center rounded">Form control</a></li>
                            <li><a href="/docs/5.0/forms/select/" class="d-inline-flex align-items-center rounded">Select</a></li>
                            <li><a href="/docs/5.0/forms/checks-radios/" class="d-inline-flex align-items-center rounded">Checks &amp; radios</a></li>
                            <li><a href="/docs/5.0/forms/range/" class="d-inline-flex align-items-center rounded">Range</a></li>
                            <li><a href="/docs/5.0/forms/input-group/" class="d-inline-flex align-items-center rounded">Input group</a></li>
                            <li><a href="/docs/5.0/forms/floating-labels/" class="d-inline-flex align-items-center rounded">Floating labels</a></li>
                            <li><a href="/docs/5.0/forms/layout/" class="d-inline-flex align-items-center rounded">Layout</a></li>
                            <li><a href="/docs/5.0/forms/validation/" class="d-inline-flex align-items-center rounded">Validation</a></li>
                        </ul>
                    </div>
                </li>
                <li class="mb-1">
                    <button class="btn d-inline-flex align-items-center rounded" data-bs-toggle="collapse" data-bs-target="#components-collapse" aria-expanded="true" aria-current="true">
                        Components
                    </button>

                    <div class="collapse show" id="components-collapse">
                        <ul class="list-unstyled fw-normal pb-1 small">
                            <li><a href="/docs/5.0/components/accordion/" class="d-inline-flex align-items-center rounded">Accordion</a></li>
                            <li><a href="/docs/5.0/components/alerts/" class="d-inline-flex align-items-center rounded">Alerts</a></li>
                            <li><a href="/docs/5.0/components/badge/" class="d-inline-flex align-items-center rounded">Badge</a></li>
                            <li><a href="/docs/5.0/components/breadcrumb/" class="d-inline-flex align-items-center rounded">Breadcrumb</a></li>
                            <li><a href="/docs/5.0/components/buttons/" class="d-inline-flex align-items-center rounded">Buttons</a></li>
                            <li><a href="/docs/5.0/components/button-group/" class="d-inline-flex align-items-center rounded">Button group</a></li>
                            <li><a href="/docs/5.0/components/card/" class="d-inline-flex align-items-center rounded">Card</a></li>
                            <li><a href="/docs/5.0/components/carousel/" class="d-inline-flex align-items-center rounded">Carousel</a></li>
                            <li><a href="/docs/5.0/components/close-button/" class="d-inline-flex align-items-center rounded">Close button</a></li>
                            <li><a href="/docs/5.0/components/collapse/" class="d-inline-flex align-items-center rounded">Collapse</a></li>
                            <li><a href="/docs/5.0/components/dropdowns/" class="d-inline-flex align-items-center rounded">Dropdowns</a></li>
                            <li><a href="/docs/5.0/components/list-group/" class="d-inline-flex align-items-center rounded">List group</a></li>
                            <li><a href="/docs/5.0/components/modal/" class="d-inline-flex align-items-center rounded">Modal</a></li>
                            <li><a href="/docs/5.0/components/navs-tabs/" class="d-inline-flex align-items-center rounded">Navs &amp; tabs</a></li>
                            <li><a href="/docs/5.0/components/navbar/" class="d-inline-flex align-items-center rounded">Navbar</a></li>
                            <li><a href="/docs/5.0/components/offcanvas/" class="d-inline-flex align-items-center rounded active" aria-current="page">Offcanvas</a></li>
                            <li><a href="/docs/5.0/components/pagination/" class="d-inline-flex align-items-center rounded">Pagination</a></li>
                            <li><a href="/docs/5.0/components/popovers/" class="d-inline-flex align-items-center rounded">Popovers</a></li>
                            <li><a href="/docs/5.0/components/progress/" class="d-inline-flex align-items-center rounded">Progress</a></li>
                            <li><a href="/docs/5.0/components/scrollspy/" class="d-inline-flex align-items-center rounded">Scrollspy</a></li>
                            <li><a href="/docs/5.0/components/spinners/" class="d-inline-flex align-items-center rounded">Spinners</a></li>
                            <li><a href="/docs/5.0/components/toasts/" class="d-inline-flex align-items-center rounded">Toasts</a></li>
                            <li><a href="/docs/5.0/components/tooltips/" class="d-inline-flex align-items-center rounded">Tooltips</a></li>
                        </ul>
                    </div>
                </li>
                <li class="mb-1">
                    <button class="btn d-inline-flex align-items-center rounded collapsed" data-bs-toggle="collapse" data-bs-target="#helpers-collapse" aria-expanded="false">
                        Helpers
                    </button>

                    <div class="collapse" id="helpers-collapse">
                        <ul class="list-unstyled fw-normal pb-1 small">
                            <li><a href="/docs/5.0/helpers/clearfix/" class="d-inline-flex align-items-center rounded">Clearfix</a></li>
                            <li><a href="/docs/5.0/helpers/colored-links/" class="d-inline-flex align-items-center rounded">Colored links</a></li>
                            <li><a href="/docs/5.0/helpers/ratio/" class="d-inline-flex align-items-center rounded">Ratio</a></li>
                            <li><a href="/docs/5.0/helpers/position/" class="d-inline-flex align-items-center rounded">Position</a></li>
                            <li><a href="/docs/5.0/helpers/visually-hidden/" class="d-inline-flex align-items-center rounded">Visually hidden</a></li>
                            <li><a href="/docs/5.0/helpers/stretched-link/" class="d-inline-flex align-items-center rounded">Stretched link</a></li>
                            <li><a href="/docs/5.0/helpers/text-truncation/" class="d-inline-flex align-items-center rounded">Text truncation</a></li>
                        </ul>
                    </div>
                </li>
                <li class="mb-1">
                    <button class="btn d-inline-flex align-items-center rounded collapsed" data-bs-toggle="collapse" data-bs-target="#utilities-collapse" aria-expanded="false">
                        Utilities
                    </button>

                    <div class="collapse" id="utilities-collapse">
                        <ul class="list-unstyled fw-normal pb-1 small">
                            <li><a href="/docs/5.0/utilities/api/" class="d-inline-flex align-items-center rounded">API</a></li>
                            <li><a href="/docs/5.0/utilities/background/" class="d-inline-flex align-items-center rounded">Background</a></li>
                            <li><a href="/docs/5.0/utilities/borders/" class="d-inline-flex align-items-center rounded">Borders</a></li>
                            <li><a href="/docs/5.0/utilities/colors/" class="d-inline-flex align-items-center rounded">Colors</a></li>
                            <li><a href="/docs/5.0/utilities/display/" class="d-inline-flex align-items-center rounded">Display</a></li>
                            <li><a href="/docs/5.0/utilities/flex/" class="d-inline-flex align-items-center rounded">Flex</a></li>
                            <li><a href="/docs/5.0/utilities/float/" class="d-inline-flex align-items-center rounded">Float</a></li>
                            <li><a href="/docs/5.0/utilities/interactions/" class="d-inline-flex align-items-center rounded">Interactions</a></li>
                            <li><a href="/docs/5.0/utilities/overflow/" class="d-inline-flex align-items-center rounded">Overflow</a></li>
                            <li><a href="/docs/5.0/utilities/position/" class="d-inline-flex align-items-center rounded">Position</a></li>
                            <li><a href="/docs/5.0/utilities/shadows/" class="d-inline-flex align-items-center rounded">Shadows</a></li>
                            <li><a href="/docs/5.0/utilities/sizing/" class="d-inline-flex align-items-center rounded">Sizing</a></li>
                            <li><a href="/docs/5.0/utilities/spacing/" class="d-inline-flex align-items-center rounded">Spacing</a></li>
                            <li><a href="/docs/5.0/utilities/text/" class="d-inline-flex align-items-center rounded">Text</a></li>
                            <li><a href="/docs/5.0/utilities/vertical-align/" class="d-inline-flex align-items-center rounded">Vertical align</a></li>
                            <li><a href="/docs/5.0/utilities/visibility/" class="d-inline-flex align-items-center rounded">Visibility</a></li>
                        </ul>
                    </div>
                </li>
                <li class="mb-1">
                    <button class="btn d-inline-flex align-items-center rounded collapsed" data-bs-toggle="collapse" data-bs-target="#extend-collapse" aria-expanded="false">
                        Extend
                    </button>

                    <div class="collapse" id="extend-collapse">
                        <ul class="list-unstyled fw-normal pb-1 small">
                            <li><a href="/docs/5.0/extend/approach/" class="d-inline-flex align-items-center rounded">Approach</a></li>
                            <li><a href="/docs/5.0/extend/icons/" class="d-inline-flex align-items-center rounded">Icons</a></li>
                        </ul>
                    </div>
                </li>
                <li class="mb-1">
                    <button class="btn d-inline-flex align-items-center rounded collapsed" data-bs-toggle="collapse" data-bs-target="#about-collapse" aria-expanded="false">
                        About
                    </button>

                    <div class="collapse" id="about-collapse">
                        <ul class="list-unstyled fw-normal pb-1 small">
                            <li><a href="/docs/5.0/about/overview/" class="d-inline-flex align-items-center rounded">Overview</a></li>
                            <li><a href="/docs/5.0/about/team/" class="d-inline-flex align-items-center rounded">Team</a></li>
                            <li><a href="/docs/5.0/about/brand/" class="d-inline-flex align-items-center rounded">Brand</a></li>
                            <li><a href="/docs/5.0/about/license/" class="d-inline-flex align-items-center rounded">License</a></li>
                            <li><a href="/docs/5.0/about/translations/" class="d-inline-flex align-items-center rounded">Translations</a></li>
                        </ul>
                    </div>
                </li>
                <li class="my-3 mx-4 border-top"></li>
                <li>
                    <a href="/docs/5.0/migration/" class="d-inline-flex align-items-center rounded">
                        Migration
                    </a>
                </li>
            </ul>
        </nav>

    </aside>

    <main class="bd-main order-1">
        <div class="bd-intro ps-lg-4">
            <div class="d-md-flex flex-md-row-reverse align-items-center justify-content-between">
                <a class="btn btn-sm btn-bd-light mb-2 mb-md-0" href="https://github.com/twbs/bootstrap/blob/v5.0.2/site/content/docs/5.0/components/offcanvas.md" title="View and edit this file on GitHub" target="_blank" rel="noopener">View on GitHub</a>
                <h1 class="bd-title" id="content">Offcanvas</h1>
            </div>
            <p class="bd-lead">Build hidden sidebars into your project for navigation, shopping carts, and more with a few classes and our JavaScript plugin.</p>
            <script async src="https://cdn.carbonads.com/carbon.js?serve=CKYIKKJL&placement=getbootstrapcom" id="_carbonads_js"></script>

        </div>


        <div class="bd-toc mt-4 mb-5 my-md-0 ps-xl-3 mb-lg-5 text-muted">
            <strong class="d-block h6 my-2 pb-2 border-bottom">On this page</strong>
            <nav id="TableOfContents">
                <ul>
                    <li><a href="#how-it-works">How it works</a></li>
                    <li><a href="#examples">Examples</a>
                        <ul>
                            <li><a href="#offcanvas-components">Offcanvas components</a></li>
                            <li><a href="#live-demo">Live demo</a></li>
                        </ul>
                    </li>
                    <li><a href="#placement">Placement</a></li>
                    <li><a href="#backdrop">Backdrop</a></li>
                    <li><a href="#accessibility">Accessibility</a></li>
                    <li><a href="#sass">Sass</a>
                        <ul>
                            <li><a href="#variables">Variables</a></li>
                        </ul>
                    </li>
                    <li><a href="#usage">Usage</a>
                        <ul>
                            <li><a href="#via-data-attributes">Via data attributes</a></li>
                            <li><a href="#via-javascript">Via JavaScript</a></li>
                            <li><a href="#options">Options</a></li>
                            <li><a href="#methods">Methods</a></li>
                            <li><a href="#events">Events</a></li>
                        </ul>
                    </li>
                </ul>
            </nav>
        </div>


        <div class="bd-content ps-lg-4">


            <h2 id="how-it-works">How it works</h2>
            <p>Offcanvas is a sidebar component that can be toggled via JavaScript to appear from the left, right, or bottom edge of the viewport. Buttons or anchors are used as triggers that are attached to specific elements you toggle, and <code>data</code> attributes are used to invoke our JavaScript.</p>
            <ul>
                <li>Offcanvas shares some of the same JavaScript code as modals. Conceptually, they are quite similar, but they are separate plugins.</li>
                <li>Similarly, some <a href="#sass">source Sass</a> variables for offcanvas&rsquo;s styles and dimensions are inherited from the modal&rsquo;s variables.</li>
                <li>When shown, offcanvas includes a default backdrop that can be clicked to hide the offcanvas.</li>
                <li>Similar to modals, only one offcanvas can be shown at a time.</li>
            </ul>
            <p><strong>Heads up!</strong> Given how CSS handles animations, you cannot use <code>margin</code> or <code>translate</code> on an <code>.offcanvas</code> element. Instead, use the class as an independent wrapping element.</p>
            <div class="bd-callout bd-callout-info">
                The animation effect of this component is dependent on the <code>prefers-reduced-motion</code> media query. See the <a href="/docs/5.0/getting-started/accessibility/#reduced-motion">reduced motion section of our accessibility documentation</a>.
            </div>

            <h2 id="examples">Examples</h2>
            <h3 id="offcanvas-components">Offcanvas components</h3>
            <p>Below is an offcanvas example that is shown by default (via <code>.show</code> on <code>.offcanvas</code>). Offcanvas includes support for a header with a close button and an optional body class for some initial <code>padding</code>. We suggest that you include offcanvas headers with dismiss actions whenever possible, or provide an explicit dismiss action.</p>
            <div class="bd-example bd-example-offcanvas p-0 bg-light overflow-hidden">
                <div class="offcanvas offcanvas-start" tabindex="-1" id="offcanvas" aria-labelledby="offcanvasLabel">
                    <div class="offcanvas-header">
                        <h5 class="offcanvas-title" id="offcanvasLabel">Offcanvas</h5>
                        <button type="button" class="btn-close text-reset" data-bs-dismiss="offcanvas" aria-label="Close"></button>
                    </div>
                    <div class="offcanvas-body">
                        Content for the offcanvas goes here. You can place just about any Bootstrap component or custom elements here.
                    </div>
                </div>
            </div><div class="highlight"><pre class="chroma"><code class="language-html" data-lang="html"><span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas offcanvas-start&#34;</span> <span class="na">tabindex</span><span class="o">=</span><span class="s">&#34;-1&#34;</span> <span class="na">id</span><span class="o">=</span><span class="s">&#34;offcanvas&#34;</span> <span class="na">aria-labelledby</span><span class="o">=</span><span class="s">&#34;offcanvasLabel&#34;</span><span class="p">&gt;</span>
  <span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-header&#34;</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">h5</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-title&#34;</span> <span class="na">id</span><span class="o">=</span><span class="s">&#34;offcanvasLabel&#34;</span><span class="p">&gt;</span>Offcanvas<span class="p">&lt;/</span><span class="nt">h5</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">button</span> <span class="na">type</span><span class="o">=</span><span class="s">&#34;button&#34;</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;btn-close text-reset&#34;</span> <span class="na">data-bs-dismiss</span><span class="o">=</span><span class="s">&#34;offcanvas&#34;</span> <span class="na">aria-label</span><span class="o">=</span><span class="s">&#34;Close&#34;</span><span class="p">&gt;&lt;/</span><span class="nt">button</span><span class="p">&gt;</span>
  <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
  <span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-body&#34;</span><span class="p">&gt;</span>
    Content for the offcanvas goes here. You can place just about any Bootstrap component or custom elements here.
  <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
<span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span></code></pre></div>
            <h3 id="live-demo">Live demo</h3>
            <p>Use the buttons below to show and hide an offcanvas element via JavaScript that toggles the <code>.show</code> class on an element with the <code>.offcanvas</code> class.</p>
            <ul>
                <li><code>.offcanvas</code> hides content (default)</li>
                <li><code>.offcanvas.show</code> shows content</li>
            </ul>
            <p>You can use a link with the <code>href</code> attribute, or a button with the <code>data-bs-target</code> attribute. In both cases, the <code>data-bs-toggle=&quot;offcanvas&quot;</code> is required.</p>
            <div class="bd-example">
                <a class="btn btn-primary" data-bs-toggle="offcanvas" href="#offcanvasExample" role="button" aria-controls="offcanvasExample">
                    Link with href
                </a>
                <button class="btn btn-primary" type="button" data-bs-toggle="offcanvas" data-bs-target="#offcanvasExample" aria-controls="offcanvasExample">
                    Button with data-bs-target
                </button>

                <div class="offcanvas offcanvas-start" tabindex="-1" id="offcanvasExample" aria-labelledby="offcanvasExampleLabel">
                    <div class="offcanvas-header">
                        <h5 class="offcanvas-title" id="offcanvasExampleLabel">Offcanvas</h5>
                        <button type="button" class="btn-close text-reset" data-bs-dismiss="offcanvas" aria-label="Close"></button>
                    </div>
                    <div class="offcanvas-body">
                        <div class="">
                            Some text as placeholder. In real life you can have the elements you have chosen. Like, text, images, lists, etc.
                        </div>
                        <div class="dropdown mt-3">
                            <button class="btn btn-secondary dropdown-toggle" type="button" id="dropdownMenuButton" data-bs-toggle="dropdown">
                                Dropdown button
                            </button>
                            <ul class="dropdown-menu" aria-labelledby="dropdownMenuButton">
                                <li><a class="dropdown-item" href="#">Action</a></li>
                                <li><a class="dropdown-item" href="#">Another action</a></li>
                                <li><a class="dropdown-item" href="#">Something else here</a></li>
                            </ul>
                        </div>
                    </div>
                </div>
            </div><div class="highlight"><pre class="chroma"><code class="language-html" data-lang="html"><span class="p">&lt;</span><span class="nt">a</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;btn btn-primary&#34;</span> <span class="na">data-bs-toggle</span><span class="o">=</span><span class="s">&#34;offcanvas&#34;</span> <span class="na">href</span><span class="o">=</span><span class="s">&#34;#offcanvasExample&#34;</span> <span class="na">role</span><span class="o">=</span><span class="s">&#34;button&#34;</span> <span class="na">aria-controls</span><span class="o">=</span><span class="s">&#34;offcanvasExample&#34;</span><span class="p">&gt;</span>
  Link with href
<span class="p">&lt;/</span><span class="nt">a</span><span class="p">&gt;</span>
<span class="p">&lt;</span><span class="nt">button</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;btn btn-primary&#34;</span> <span class="na">type</span><span class="o">=</span><span class="s">&#34;button&#34;</span> <span class="na">data-bs-toggle</span><span class="o">=</span><span class="s">&#34;offcanvas&#34;</span> <span class="na">data-bs-target</span><span class="o">=</span><span class="s">&#34;#offcanvasExample&#34;</span> <span class="na">aria-controls</span><span class="o">=</span><span class="s">&#34;offcanvasExample&#34;</span><span class="p">&gt;</span>
  Button with data-bs-target
<span class="p">&lt;/</span><span class="nt">button</span><span class="p">&gt;</span>

<span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas offcanvas-start&#34;</span> <span class="na">tabindex</span><span class="o">=</span><span class="s">&#34;-1&#34;</span> <span class="na">id</span><span class="o">=</span><span class="s">&#34;offcanvasExample&#34;</span> <span class="na">aria-labelledby</span><span class="o">=</span><span class="s">&#34;offcanvasExampleLabel&#34;</span><span class="p">&gt;</span>
  <span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-header&#34;</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">h5</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-title&#34;</span> <span class="na">id</span><span class="o">=</span><span class="s">&#34;offcanvasExampleLabel&#34;</span><span class="p">&gt;</span>Offcanvas<span class="p">&lt;/</span><span class="nt">h5</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">button</span> <span class="na">type</span><span class="o">=</span><span class="s">&#34;button&#34;</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;btn-close text-reset&#34;</span> <span class="na">data-bs-dismiss</span><span class="o">=</span><span class="s">&#34;offcanvas&#34;</span> <span class="na">aria-label</span><span class="o">=</span><span class="s">&#34;Close&#34;</span><span class="p">&gt;&lt;/</span><span class="nt">button</span><span class="p">&gt;</span>
  <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
  <span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-body&#34;</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">div</span><span class="p">&gt;</span>
      Some text as placeholder. In real life you can have the elements you have chosen. Like, text, images, lists, etc.
    <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;dropdown mt-3&#34;</span><span class="p">&gt;</span>
      <span class="p">&lt;</span><span class="nt">button</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;btn btn-secondary dropdown-toggle&#34;</span> <span class="na">type</span><span class="o">=</span><span class="s">&#34;button&#34;</span> <span class="na">id</span><span class="o">=</span><span class="s">&#34;dropdownMenuButton&#34;</span> <span class="na">data-bs-toggle</span><span class="o">=</span><span class="s">&#34;dropdown&#34;</span><span class="p">&gt;</span>
        Dropdown button
      <span class="p">&lt;/</span><span class="nt">button</span><span class="p">&gt;</span>
      <span class="p">&lt;</span><span class="nt">ul</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;dropdown-menu&#34;</span> <span class="na">aria-labelledby</span><span class="o">=</span><span class="s">&#34;dropdownMenuButton&#34;</span><span class="p">&gt;</span>
        <span class="p">&lt;</span><span class="nt">li</span><span class="p">&gt;&lt;</span><span class="nt">a</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;dropdown-item&#34;</span> <span class="na">href</span><span class="o">=</span><span class="s">&#34;#&#34;</span><span class="p">&gt;</span>Action<span class="p">&lt;/</span><span class="nt">a</span><span class="p">&gt;&lt;/</span><span class="nt">li</span><span class="p">&gt;</span>
        <span class="p">&lt;</span><span class="nt">li</span><span class="p">&gt;&lt;</span><span class="nt">a</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;dropdown-item&#34;</span> <span class="na">href</span><span class="o">=</span><span class="s">&#34;#&#34;</span><span class="p">&gt;</span>Another action<span class="p">&lt;/</span><span class="nt">a</span><span class="p">&gt;&lt;/</span><span class="nt">li</span><span class="p">&gt;</span>
        <span class="p">&lt;</span><span class="nt">li</span><span class="p">&gt;&lt;</span><span class="nt">a</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;dropdown-item&#34;</span> <span class="na">href</span><span class="o">=</span><span class="s">&#34;#&#34;</span><span class="p">&gt;</span>Something else here<span class="p">&lt;/</span><span class="nt">a</span><span class="p">&gt;&lt;/</span><span class="nt">li</span><span class="p">&gt;</span>
      <span class="p">&lt;/</span><span class="nt">ul</span><span class="p">&gt;</span>
    <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
  <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
<span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span></code></pre></div>
            <h2 id="placement">Placement</h2>
            <p>There&rsquo;s no default placement for offcanvas components, so you must add one of the modifier classes below;</p>
            <ul>
                <li><code>.offcanvas-start</code> places offcanvas on the left of the viewport (shown above)</li>
                <li><code>.offcanvas-end</code> places offcanvas on the right of the viewport</li>
                <li><code>.offcanvas-top</code> places offcanvas on the top of the viewport</li>
                <li><code>.offcanvas-bottom</code> places offcanvas on the bottom of the viewport</li>
            </ul>
            <p>Try the top, right, and bottom examples out below.</p>
            <div class="bd-example">
                <button class="btn btn-primary" type="button" data-bs-toggle="offcanvas" data-bs-target="#offcanvasTop" aria-controls="offcanvasTop">Toggle top offcanvas</button>

                <div class="offcanvas offcanvas-top" tabindex="-1" id="offcanvasTop" aria-labelledby="offcanvasTopLabel">
                    <div class="offcanvas-header">
                        <h5 id="offcanvasTopLabel">Offcanvas top</h5>
                        <button type="button" class="btn-close text-reset" data-bs-dismiss="offcanvas" aria-label="Close"></button>
                    </div>
                    <div class="offcanvas-body">
                        ...
                    </div>
                </div>
            </div><div class="highlight"><pre class="chroma"><code class="language-html" data-lang="html"><span class="p">&lt;</span><span class="nt">button</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;btn btn-primary&#34;</span> <span class="na">type</span><span class="o">=</span><span class="s">&#34;button&#34;</span> <span class="na">data-bs-toggle</span><span class="o">=</span><span class="s">&#34;offcanvas&#34;</span> <span class="na">data-bs-target</span><span class="o">=</span><span class="s">&#34;#offcanvasTop&#34;</span> <span class="na">aria-controls</span><span class="o">=</span><span class="s">&#34;offcanvasTop&#34;</span><span class="p">&gt;</span>Toggle top offcanvas<span class="p">&lt;/</span><span class="nt">button</span><span class="p">&gt;</span>

<span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas offcanvas-top&#34;</span> <span class="na">tabindex</span><span class="o">=</span><span class="s">&#34;-1&#34;</span> <span class="na">id</span><span class="o">=</span><span class="s">&#34;offcanvasTop&#34;</span> <span class="na">aria-labelledby</span><span class="o">=</span><span class="s">&#34;offcanvasTopLabel&#34;</span><span class="p">&gt;</span>
  <span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-header&#34;</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">h5</span> <span class="na">id</span><span class="o">=</span><span class="s">&#34;offcanvasTopLabel&#34;</span><span class="p">&gt;</span>Offcanvas top<span class="p">&lt;/</span><span class="nt">h5</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">button</span> <span class="na">type</span><span class="o">=</span><span class="s">&#34;button&#34;</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;btn-close text-reset&#34;</span> <span class="na">data-bs-dismiss</span><span class="o">=</span><span class="s">&#34;offcanvas&#34;</span> <span class="na">aria-label</span><span class="o">=</span><span class="s">&#34;Close&#34;</span><span class="p">&gt;&lt;/</span><span class="nt">button</span><span class="p">&gt;</span>
  <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
  <span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-body&#34;</span><span class="p">&gt;</span>
    ...
  <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
<span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span></code></pre></div>
            <div class="bd-example">
                <button class="btn btn-primary" type="button" data-bs-toggle="offcanvas" data-bs-target="#offcanvasRight" aria-controls="offcanvasRight">Toggle right offcanvas</button>

                <div class="offcanvas offcanvas-end" tabindex="-1" id="offcanvasRight" aria-labelledby="offcanvasRightLabel">
                    <div class="offcanvas-header">
                        <h5 id="offcanvasRightLabel">Offcanvas right</h5>
                        <button type="button" class="btn-close text-reset" data-bs-dismiss="offcanvas" aria-label="Close"></button>
                    </div>
                    <div class="offcanvas-body">
                        ...
                    </div>
                </div>
            </div><div class="highlight"><pre class="chroma"><code class="language-html" data-lang="html"><span class="p">&lt;</span><span class="nt">button</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;btn btn-primary&#34;</span> <span class="na">type</span><span class="o">=</span><span class="s">&#34;button&#34;</span> <span class="na">data-bs-toggle</span><span class="o">=</span><span class="s">&#34;offcanvas&#34;</span> <span class="na">data-bs-target</span><span class="o">=</span><span class="s">&#34;#offcanvasRight&#34;</span> <span class="na">aria-controls</span><span class="o">=</span><span class="s">&#34;offcanvasRight&#34;</span><span class="p">&gt;</span>Toggle right offcanvas<span class="p">&lt;/</span><span class="nt">button</span><span class="p">&gt;</span>

<span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas offcanvas-end&#34;</span> <span class="na">tabindex</span><span class="o">=</span><span class="s">&#34;-1&#34;</span> <span class="na">id</span><span class="o">=</span><span class="s">&#34;offcanvasRight&#34;</span> <span class="na">aria-labelledby</span><span class="o">=</span><span class="s">&#34;offcanvasRightLabel&#34;</span><span class="p">&gt;</span>
  <span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-header&#34;</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">h5</span> <span class="na">id</span><span class="o">=</span><span class="s">&#34;offcanvasRightLabel&#34;</span><span class="p">&gt;</span>Offcanvas right<span class="p">&lt;/</span><span class="nt">h5</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">button</span> <span class="na">type</span><span class="o">=</span><span class="s">&#34;button&#34;</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;btn-close text-reset&#34;</span> <span class="na">data-bs-dismiss</span><span class="o">=</span><span class="s">&#34;offcanvas&#34;</span> <span class="na">aria-label</span><span class="o">=</span><span class="s">&#34;Close&#34;</span><span class="p">&gt;&lt;/</span><span class="nt">button</span><span class="p">&gt;</span>
  <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
  <span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-body&#34;</span><span class="p">&gt;</span>
    ...
  <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
<span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span></code></pre></div>
            <div class="bd-example">
                <button class="btn btn-primary" type="button" data-bs-toggle="offcanvas" data-bs-target="#offcanvasBottom" aria-controls="offcanvasBottom">Toggle bottom offcanvas</button>

                <div class="offcanvas offcanvas-bottom" tabindex="-1" id="offcanvasBottom" aria-labelledby="offcanvasBottomLabel">
                    <div class="offcanvas-header">
                        <h5 class="offcanvas-title" id="offcanvasBottomLabel">Offcanvas bottom</h5>
                        <button type="button" class="btn-close text-reset" data-bs-dismiss="offcanvas" aria-label="Close"></button>
                    </div>
                    <div class="offcanvas-body small">
                        ...
                    </div>
                </div>
            </div><div class="highlight"><pre class="chroma"><code class="language-html" data-lang="html"><span class="p">&lt;</span><span class="nt">button</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;btn btn-primary&#34;</span> <span class="na">type</span><span class="o">=</span><span class="s">&#34;button&#34;</span> <span class="na">data-bs-toggle</span><span class="o">=</span><span class="s">&#34;offcanvas&#34;</span> <span class="na">data-bs-target</span><span class="o">=</span><span class="s">&#34;#offcanvasBottom&#34;</span> <span class="na">aria-controls</span><span class="o">=</span><span class="s">&#34;offcanvasBottom&#34;</span><span class="p">&gt;</span>Toggle bottom offcanvas<span class="p">&lt;/</span><span class="nt">button</span><span class="p">&gt;</span>

<span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas offcanvas-bottom&#34;</span> <span class="na">tabindex</span><span class="o">=</span><span class="s">&#34;-1&#34;</span> <span class="na">id</span><span class="o">=</span><span class="s">&#34;offcanvasBottom&#34;</span> <span class="na">aria-labelledby</span><span class="o">=</span><span class="s">&#34;offcanvasBottomLabel&#34;</span><span class="p">&gt;</span>
  <span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-header&#34;</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">h5</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-title&#34;</span> <span class="na">id</span><span class="o">=</span><span class="s">&#34;offcanvasBottomLabel&#34;</span><span class="p">&gt;</span>Offcanvas bottom<span class="p">&lt;/</span><span class="nt">h5</span><span class="p">&gt;</span>
    <span class="p">&lt;</span><span class="nt">button</span> <span class="na">type</span><span class="o">=</span><span class="s">&#34;button&#34;</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;btn-close text-reset&#34;</span> <span class="na">data-bs-dismiss</span><span class="o">=</span><span class="s">&#34;offcanvas&#34;</span> <span class="na">aria-label</span><span class="o">=</span><span class="s">&#34;Close&#34;</span><span class="p">&gt;&lt;/</span><span class="nt">button</span><span class="p">&gt;</span>
  <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
  <span class="p">&lt;</span><span class="nt">div</span> <span class="na">class</span><span class="o">=</span><span class="s">&#34;offcanvas-body small&#34;</span><span class="p">&gt;</span>
    ...
  <span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span>
<span class="p">&lt;/</span><span class="nt">div</span><span class="p">&gt;</span></code></pre></div>
            <h2 id="backdrop">Backdrop</h2>
            <p>Scrolling the <code>&lt;body&gt;</code> element is disabled when an offcanvas and its backdrop are visible. Use the <code>data-bs-scroll</code> attribute to toggle <code>&lt;body&gt;</code> scrolling and <code>data-bs-backdrop</code> to toggle the backdrop.</p>
            <div class="bd-example">
                <button class="btn btn-primary" type="button" data-bs-toggle="offcanvas" data-bs-target="#offcanvasScrolling" aria-controls="offcanvasScrolling">Enable body scrolling</button>
                <button class="btn btn-primary" type="button" data-bs-toggle="offcanvas" data-bs-target="#offcanvasWithBackdrop" aria-controls="offcanvasWithBackdrop">Enable backdrop (default)</button>
                <button class="btn btn-primary" type="button" data-bs-toggle="offcanvas" data-bs-target="#offcanvasWithBothOptions" aria-controls="offcanvasWithBothOptions">Enable both scrolling & backdrop</button>

                <div class="offcanvas offcanvas-start" data-bs-scroll="true" data-bs-backdrop="false" tabindex="-1" id="offcanvasScrolling" aria-labelledby="offcanvasScrollingLabel">
                    <div class="offcanvas-header">
                        <h5 class="offcanvas-title" id="offcanvasScrollingLabel">Colored with scrolling</h5>
                        <button type="button" class="btn-close text-reset" data-bs-dismiss="offcanvas" aria-label="Close"></button>
                    </div>
                    <div class="offcanvas-body">
                        <p>Try scrolling the rest of the page to see this option in action.</p>
                    </div>
                </div>
                <div class="offcanvas offcanvas-start" tabindex="-1" id="offcanvasWithBackdrop" aria-labelledby="offcanvasWithBackdropLabel">
                    <div class="offcanvas-header">
                        <h5 class="offcanvas-title" id="offcanvasWithBackdropLabel">Offcanvas with backdrop</h5>
                        <button type="button" class="btn-close text-reset" data-bs-dismiss="offcanvas" aria-label="Close"></button>
                    </div>
                    <div class="offcanvas-body">
                        <p>.....</p>
                    </div>
                </div>
                <div class="offcanvas offcanvas-start" data-bs-scroll="true" tabindex="-1" id="offcanvasWithBothOptions" aria-labelledby="offcanvasWithBothOptionsLabel">
                    <div class="offcanvas-header">
                        <h5 class="offcanvas-title" id="offcanvasWithBothOptionsLabel">Backdroped with scrolling</h5>
                        <button type="button" class="btn-close text-reset" data-bs-dismiss="offcanvas" aria-label="Close"></button>
                    </div>
                    <div class="offcanvas-body">
                        <p>Try scrolling the rest of the page to see this option in action.</p>
                    </div>
                </div>
            </div>
            <h2 id="accessibility">Accessibility</h2>
            <p>Since the offcanvas panel is conceptually a modal dialog, be sure to add <code>aria-labelledby=&quot;...&quot;</code>—referencing the offcanvas title—to <code>.offcanvas</code>. Note that you don’t need to add <code>role=&quot;dialog&quot;</code> since we already add it via JavaScript.</p>
            <h2 id="sass">Sass</h2>
            <h3 id="variables">Variables</h3>
            <div class="highlight"><pre class="chroma"><code class="language-scss" data-lang="scss"><span class="nv">$offcanvas-padding-y</span><span class="o">:</span>               <span class="nv">$modal-inner-padding</span><span class="p">;</span>
<span class="nv">$offcanvas-padding-x</span><span class="o">:</span>               <span class="nv">$modal-inner-padding</span><span class="p">;</span>
<span class="nv">$offcanvas-horizontal-width</span><span class="o">:</span>        <span class="mi">400</span><span class="kt">px</span><span class="p">;</span>
<span class="nv">$offcanvas-vertical-height</span><span class="o">:</span>         <span class="mi">30</span><span class="kt">vh</span><span class="p">;</span>
<span class="nv">$offcanvas-transition-duration</span><span class="o">:</span>     <span class="mf">.3</span><span class="kt">s</span><span class="p">;</span>
<span class="nv">$offcanvas-border-color</span><span class="o">:</span>            <span class="nv">$modal-content-border-color</span><span class="p">;</span>
<span class="nv">$offcanvas-border-width</span><span class="o">:</span>            <span class="nv">$modal-content-border-width</span><span class="p">;</span>
<span class="nv">$offcanvas-title-line-height</span><span class="o">:</span>       <span class="nv">$modal-title-line-height</span><span class="p">;</span>
<span class="nv">$offcanvas-bg-color</span><span class="o">:</span>                <span class="nv">$modal-content-bg</span><span class="p">;</span>
<span class="nv">$offcanvas-color</span><span class="o">:</span>                   <span class="nv">$modal-content-color</span><span class="p">;</span>
<span class="nv">$offcanvas-box-shadow</span><span class="o">:</span>              <span class="nv">$modal-content-box-shadow-xs</span><span class="p">;</span>
</code></pre></div>
            <h2 id="usage">Usage</h2>
            <p>The offcanvas plugin utilizes a few classes and attributes to handle the heavy lifting:</p>
            <ul>
                <li><code>.offcanvas</code> hides the content</li>
                <li><code>.offcanvas.show</code> shows the content</li>
                <li><code>.offcanvas-start</code> hides the offcanvas on the left</li>
                <li><code>.offcanvas-end</code> hides the offcanvas on the right</li>
                <li><code>.offcanvas-bottom</code> hides the offcanvas on the bottom</li>
            </ul>
            <p>Add a dismiss button with the <code>data-bs-dismiss=&quot;offcanvas&quot;</code> attribute, which triggers the JavaScript functionality. Be sure to use the <code>&lt;button&gt;</code> element with it for proper behavior across all devices.</p>
            <h3 id="via-data-attributes">Via data attributes</h3>
            <p>Add <code>data-bs-toggle=&quot;offcanvas&quot;</code> and a <code>data-bs-target</code> or <code>href</code> to the element to automatically assign control of one offcanvas element. The <code>data-bs-target</code> attribute accepts a CSS selector to apply the offcanvas to. Be sure to add the class <code>offcanvas</code> to the offcanvas element. If you&rsquo;d like it to default open, add the additional class <code>show</code>.</p>
            <h3 id="via-javascript">Via JavaScript</h3>
            <p>Enable manually with:</p>
            <div class="highlight"><pre class="chroma"><code class="language-js" data-lang="js"><span class="kd">var</span> <span class="nx">offcanvasElementList</span> <span class="o">=</span> <span class="p">[].</span><span class="nx">slice</span><span class="p">.</span><span class="nx">call</span><span class="p">(</span><span class="nb">document</span><span class="p">.</span><span class="nx">querySelectorAll</span><span class="p">(</span><span class="s1">&#39;.offcanvas&#39;</span><span class="p">))</span>
<span class="kd">var</span> <span class="nx">offcanvasList</span> <span class="o">=</span> <span class="nx">offcanvasElementList</span><span class="p">.</span><span class="nx">map</span><span class="p">(</span><span class="kd">function</span> <span class="p">(</span><span class="nx">offcanvasEl</span><span class="p">)</span> <span class="p">{</span>
  <span class="k">return</span> <span class="k">new</span> <span class="nx">bootstrap</span><span class="p">.</span><span class="nx">Offcanvas</span><span class="p">(</span><span class="nx">offcanvasEl</span><span class="p">)</span>
<span class="p">})</span>
</code></pre></div><h3 id="options">Options</h3>
            <p>Options can be passed via data attributes or JavaScript. For data attributes, append the option name to <code>data-bs-</code>, as in <code>data-bs-backdrop=&quot;&quot;</code>.</p>
            <table class="table">
                <thead>
                <tr>
                    <th>Name</th>
                    <th>Type</th>
                    <th>Default</th>
                    <th>Description</th>
                </tr>
                </thead>
                <tbody>
                <tr>
                    <td><code>backdrop</code></td>
                    <td>boolean</td>
                    <td><code>true</code></td>
                    <td>Apply a backdrop on body while offcanvas is open</td>
                </tr>
                <tr>
                    <td><code>keyboard</code></td>
                    <td>boolean</td>
                    <td><code>true</code></td>
                    <td>Closes the offcanvas when escape key is pressed</td>
                </tr>
                <tr>
                    <td><code>scroll</code></td>
                    <td>boolean</td>
                    <td><code>false</code></td>
                    <td>Allow body scrolling while offcanvas is open</td>
                </tr>
                </tbody>
            </table>

            <h3 id="methods">Methods</h3>
            <div class="bd-callout bd-callout-danger">
                <h4 id="asynchronous-methods-and-transitions">Asynchronous methods and transitions</h4>
                <p>All API methods are <strong>asynchronous</strong> and start a <strong>transition</strong>. They return to the caller as soon as the transition is started but <strong>before it ends</strong>. In addition, a method call on a <strong>transitioning component will be ignored</strong>.</p>
                <p><a href="/docs/5.0/getting-started/javascript/#asynchronous-functions-and-transitions">See our JavaScript documentation for more information</a>.</p>

            </div>

            <p>Activates your content as an offcanvas element. Accepts an optional options <code>object</code>.</p>
            <p>You can create an offcanvas instance with the constructor, for example:</p>
            <div class="highlight"><pre class="chroma"><code class="language-js" data-lang="js"><span class="kd">var</span> <span class="nx">myOffcanvas</span> <span class="o">=</span> <span class="nb">document</span><span class="p">.</span><span class="nx">getElementById</span><span class="p">(</span><span class="s1">&#39;myOffcanvas&#39;</span><span class="p">)</span>
<span class="kd">var</span> <span class="nx">bsOffcanvas</span> <span class="o">=</span> <span class="k">new</span> <span class="nx">bootstrap</span><span class="p">.</span><span class="nx">Offcanvas</span><span class="p">(</span><span class="nx">myOffcanvas</span><span class="p">)</span>
</code></pre></div><table class="table">
                <thead>
                <tr>
                    <th>Method</th>
                    <th>Description</th>
                </tr>
                </thead>
                <tbody>
                <tr>
                    <td><code>toggle</code></td>
                    <td>Toggles an offcanvas element to shown or hidden. <strong>Returns to the caller before the offcanvas element has actually been shown or hidden</strong> (i.e. before the <code>shown.bs.offcanvas</code> or <code>hidden.bs.offcanvas</code> event occurs).</td>
                </tr>
                <tr>
                    <td><code>show</code></td>
                    <td>Shows an offcanvas element. <strong>Returns to the caller before the offcanvas element has actually been shown</strong> (i.e. before the <code>shown.bs.offcanvas</code> event occurs).</td>
                </tr>
                <tr>
                    <td><code>hide</code></td>
                    <td>Hides an offcanvas element. <strong>Returns to the caller before the offcanvas element has actually been hidden</strong> (i.e. before the <code>hidden.bs.offcanvas</code> event occurs).</td>
                </tr>
                <tr>
                    <td><code>getInstance</code></td>
                    <td><em>Static</em> method which allows you to get the offcanvas instance associated with a DOM element</td>
                </tr>
                <tr>
                    <td><code>getOrCreateInstance</code></td>
                    <td><em>Static</em> method which allows you to get the offcanvas instance associated with a DOM element, or create a new one in case it wasn&rsquo;t initialised</td>
                </tr>
                </tbody>
            </table>

            <h3 id="events">Events</h3>
            <p>Bootstrap&rsquo;s offcanvas class exposes a few events for hooking into offcanvas functionality.</p>
            <table class="table">
                <thead>
                <tr>
                    <th>Event type</th>
                    <th>Description</th>
                </tr>
                </thead>
                <tbody>
                <tr>
                    <td><code>show.bs.offcanvas</code></td>
                    <td>This event fires immediately when the <code>show</code> instance method is called.</td>
                </tr>
                <tr>
                    <td><code>shown.bs.offcanvas</code></td>
                    <td>This event is fired when an offcanvas element has been made visible to the user (will wait for CSS transitions to complete).</td>
                </tr>
                <tr>
                    <td><code>hide.bs.offcanvas</code></td>
                    <td>This event is fired immediately when the <code>hide</code> method has been called.</td>
                </tr>
                <tr>
                    <td><code>hidden.bs.offcanvas</code></td>
                    <td>This event is fired when an offcanvas element has been hidden from the user (will wait for CSS transitions to complete).</td>
                </tr>
                </tbody>
            </table>

            <div class="highlight"><pre class="chroma"><code class="language-js" data-lang="js"><span class="kd">var</span> <span class="nx">myOffcanvas</span> <span class="o">=</span> <span class="nb">document</span><span class="p">.</span><span class="nx">getElementById</span><span class="p">(</span><span class="s1">&#39;myOffcanvas&#39;</span><span class="p">)</span>
<span class="nx">myOffcanvas</span><span class="p">.</span><span class="nx">addEventListener</span><span class="p">(</span><span class="s1">&#39;hidden.bs.offcanvas&#39;</span><span class="p">,</span> <span class="kd">function</span> <span class="p">()</span> <span class="p">{</span>
  <span class="c1">// do something...
</span><span class="c1"></span><span class="p">})</span>
</code></pre></div>
        </div>
    </main>
</div>


<footer class="bd-footer py-5 mt-5 bg-light">
    <div class="container py-5">
        <div class="row">
            <div class="col-lg-3 mb-3">
                <a class="d-inline-flex align-items-center mb-2 link-dark text-decoration-none" href="/" aria-label="Bootstrap">
                    <span class="fs-5">Bootstrap</span>
                </a>
                <ul class="list-unstyled small text-muted">
                    <li class="mb-2">Designed and built with all the love in the world by the <a href="/docs/5.0/about/team/">Bootstrap team</a> with the help of <a href="https://github.com/twbs/bootstrap/graphs/contributors">our contributors</a>.</li>
                    <li class="mb-2">Code licensed <a href="https://github.com/twbs/bootstrap/blob/main/LICENSE" target="_blank" rel="license noopener">MIT</a>, docs <a href="https://creativecommons.org/licenses/by/3.0/" target="_blank" rel="license noopener">CC BY 3.0</a>.</li>
                    <li class="mb-2">Currently v5.0.2.</li>
                    <li class="mb-2">Analytics by <a href="https://usefathom.com/ref/ADZSBE" target="_blank" rel="noopener">Fathom</a>.</li>
                </ul>
            </div>
            <div class="col-6 col-lg-2 offset-lg-1 mb-3">
                <h5>Links</h5>
                <ul class="list-unstyled">
                    <li class="mb-2"><a href="/">Home</a></li>
                    <li class="mb-2"><a href="/docs/5.0/">Docs</a></li>
                    <li class="mb-2"><a href="/docs/5.0/examples/">Examples</a></li>
                    <li class="mb-2"><a href="https://themes.getbootstrap.com/">Themes</a></li>
                    <li class="mb-2"><a href="https://blog.getbootstrap.com/">Blog</a></li>
                    <li class="mb-2"><a href="https://cottonbureau.com/people/bootstrap">Swag Store</a></li>
                </ul>
            </div>
            <div class="col-6 col-lg-2 mb-3">
                <h5>Guides</h5>
                <ul class="list-unstyled">
                    <li class="mb-2"><a href="/docs/5.0/getting-started/">Getting started</a></li>
                    <li class="mb-2"><a href="/docs/5.0/examples/starter-template/">Starter template</a></li>
                    <li class="mb-2"><a href="/docs/5.0/getting-started/webpack/">Webpack</a></li>
                    <li class="mb-2"><a href="/docs/5.0/getting-started/parcel/">Parcel</a></li>
                </ul>
            </div>
            <div class="col-6 col-lg-2 mb-3">
                <h5>Projects</h5>
                <ul class="list-unstyled">
                    <li class="mb-2"><a href="https://github.com/twbs/bootstrap">Bootstrap 5</a></li>
                    <li class="mb-2"><a href="https://github.com/twbs/bootstrap/tree/v4-dev">Bootstrap 4</a></li>
                    <li class="mb-2"><a href="https://github.com/twbs/icons">Icons</a></li>
                    <li class="mb-2"><a href="https://github.com/twbs/rfs">RFS</a></li>
                    <li class="mb-2"><a href="https://github.com/twbs/bootstrap-npm-starter">npm starter</a></li>
                </ul>
            </div>
            <div class="col-6 col-lg-2 mb-3">
                <h5>Community</h5>
                <ul class="list-unstyled">
                    <li class="mb-2"><a href="https://github.com/twbs/bootstrap/issues">Issues</a></li>
                    <li class="mb-2"><a href="https://github.com/twbs/bootstrap/discussions">Discussions</a></li>
                    <li class="mb-2"><a href="https://github.com/sponsors/twbs">Corporate sponsors</a></li>
                    <li class="mb-2"><a href="https://opencollective.com/bootstrap">Open Collective</a></li>
                    <li class="mb-2"><a href="https://stackoverflow.com/questions/tagged/bootstrap-5">Stack Overflow</a></li>
                </ul>
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