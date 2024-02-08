function setupTable(containerElem, tableElem, dataSetRoot) {
    let jqContainer = $(containerElem);
    let jqTable = $(tableElem);

    let dataColumns = dataSetRoot["columns"]; // is a list
    let dataList = dataSetRoot["data"]; // is a list of lists
    let searchableColumns = dataSetRoot["searchable"]; // is a list of column indexes
    let visibleColumns = dataSetRoot["visible"]; // is a list of column indexes
    let cell_format = dataSetRoot["cell_format"]; // is a object of function -> list of column indexes - This variable may be null
    let row_format = dataSetRoot["row_format"]; // is a string (or null)
    let sort = dataSetRoot["sort"]; // [index, asc or desc]
    if (sort == null) sort = [0, 'asc'];

    let dataObj = [];
    dataList.forEach(function (row, index) {
        let obj = {};
        for (let i = 0; i < dataColumns.length; i++) {
            obj[dataColumns[i]] = row[i];
        }
        dataObj.push(obj);
    });
    let cellFormatByCol = {};
    if (cell_format != null) {
        for (let func in cell_format) {
            let cols = cell_format[func];
            for (let i = 0; i < cols.length; i++) {
                cellFormatByCol[cols[i]] = window[func];
            }
        }
    }

    let columnsInfo = [];
    if (dataColumns.length > 0) {
        for (let i = 0; i < dataColumns.length; i++) {
            let columnInfo = {"data": dataColumns[i], "className": 'details-control'};
            let renderFunc = cellFormatByCol[i];
            if (renderFunc != null) {
                columnInfo["render"] = renderFunc;
            }
            columnsInfo.push(columnInfo);
        }
    }

    // header and footer toggles + search
    for(let i = 0; i < columnsInfo.length; i++) {
        let columnInfo = columnsInfo[i];
        let title = columnInfo["data"];
        if (visibleColumns != null) {
            columnInfo["visible"] = visibleColumns.includes(i);
        }
        let th,tf;
        if (title == null) {
            th = '';
            tf = '';
        } else {
            if (searchableColumns == null || searchableColumns.includes(i)) {
                th = '<input type="text" placeholder="'+ title +'" style="width: 100%;" />';
            } else {
                th = title;
            }
            tf = "<button class='toggle-vis btn-danger' data-column='" + i + "'>-" + title + "</button>";
        }
        jqTable.find("thead tr").append("<th>" + th + "</th>");
        let rows = jqTable.find("tfoot tr").append("<th>" + tf + "</th>");
        if (typeof columnInfo["visible"] === 'boolean' && columnInfo["visible"] === false) {
            let row = rows.children().last();
            let toggle = row.children().first();
            toggle[0].oldParent = row[0];
            toggle = jqContainer.find(".table-toggles").append(toggle);
        }
    }

    // table initialization
    let table = jqTable.DataTable( {
        data: dataObj,
        "columns": columnsInfo,
        "order": [sort],
        lengthMenu: [ [10, 25, 50, 100, -1], [10, 25, 50, 100, "All"] ],
        initComplete: function () {
            this.api().columns().every( function (index) {
                let column = this;
                let title = columnsInfo[index]["data"];
                if (title != null) {
                    let data = column.data();
                    let unique = data.unique();
                    let uniqueCount = unique.count();
                    if (uniqueCount > 1 && uniqueCount < 24 && uniqueCount < data.count() / 2 && (searchableColumns == null || searchableColumns.includes(title))) {
                        let select = $('<select><option value=""></option></select>')
                            .appendTo($(column.header()).empty() )
                            .on( 'change', function () {
                                let val = $.fn.dataTable.util.escapeRegex(
                                    $(this).val()
                                );

                                column
                                    .search( val ? '^'+val+'$' : '', true, false )
                                    .draw();
                            });

                        unique.sort().each( function ( d, j ) {
                            select.append('<option value="'+d+'">'+d+'</option>' );
                        });

                        select.before(title + ": ");
                    }

                }
            } );
        }
    });

    // Apply the search
    table.columns().every( function () {
        let that = this;
        $( 'input', this.header() ).on( 'keyup change clear', function () {
            if ( that.search() !== this.value ) {
                that
                    .search( this.value )
                    .draw();
            }
        } );
    } );

    // apply the toggles
    jqContainer.find('.toggle-vis').click(function (e) {
        e.preventDefault();
        // Get the column API object
        let column = table.column( $(this).attr('data-column') );

        // Toggle the visibility
        column.visible( ! column.visible() );
//
        // move elem
        if (event.target.parentElement.tagName == "TH") {
            event.target.oldParent = event.target.parentElement;
            jqContainer.find(".dataTables_length").after(event.target);
        } else {
            event.target.oldParent.append(event.target);
        }
    });

    /* Formatting function for row details - modify as you need */
    function format ( d ) {
        let rows = "";
        table.columns().every( function (index) {
            let columnInfo = columnsInfo[index];
            let title = columnInfo["data"];
            if (title != null) {
                if (!table.column(index).visible()) {
                    rows += '<tr>'+
                        '<td>' + title + '</td>'+
                        '<td>'+d[title]+'</td>'+
                        '</tr>';
                }
            }
        });
        if (rows == "") rows = "No extra info";
        return '<table class="table table-striped table-bordered table-sm" cellspacing="0" border="0">'+rows+'</table>';
    }

    // Add event listener for opening and closing details
    jqTable.find('tbody').on('click', 'td.details-control', function () {
        let tr = $(this).closest('tr');
        let row = table.row( tr );

        if ( row.child.isShown() ) {
            // This row is already open - close it
            row.child.hide();
            tr.removeClass('shown');
        }
        else {
            // Open this row
            row.child( format(row.data()) ).show();
            tr.addClass('shown');
        }
    } );
}

$(document).ready(function() {
    let containers = document.getElementsByClassName("locutus-table-container");
    for (let i = 0; i < containers.length; i++) {
        let container = containers[i];

        var id = "t" + uuidv4();
        container.innerHTML = "<div class=\"table-toggles\"></div>" +
            "<table id=\"" + id + "\" class=\"locutus-table table compact table-striped table-bordered table-sm\" style=\"width:100%\">" +
            "<thead class=\"table-danger\"><tr></tr></thead>" +
            "<tbody></tbody>" +
            "<tfoot><tr></tr></tfoot>" +
            "</table>";
        let table = container.getElementsByTagName("table")[0];
        let src = container.getAttribute("src");

        if (src != null) {
            $.getJSON( src, function( data ) {
                setupTable(container, table, data);
            });
        }else {
            let dataSrc = container.getAttribute("data-src");
            if (dataSrc != null) {
                container.removeAttribute("data-src");
                setupTable(container, table, JSON.parse(atob(dataSrc)));
            }
        }
    }
    $("input").click(function(e) {
       e.stopPropagation();
    });
});