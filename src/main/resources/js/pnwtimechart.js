function initCharts() {
     for (let container of document.querySelectorAll('.locutus-chart:not([initialized])')) {
        container.setAttribute("initialized", true);
        let src = container.getAttribute("src");
        let isTime = container.getAttribute("time") + "" == "true";
        if (src != null) {
            (function(container) {
                $.post(src, function(data) {
                    setupTimeChart(container, data, isTime);
                });
            })(container);
        }else {
            let dataSrc = container.getAttribute("data-src");
            if (dataSrc != null) {
                setupTimeChart(container, JSON.parse(dataSrc), isTime);
            }
        }
    }

    for (let container of document.querySelectorAll('.locutus-barchart:not([initialized])')) {
        container.setAttribute("initialized", true);
        let stackedAttribute = container.getAttribute("stacked");
        let isStacked = stackedAttribute !== null && stackedAttribute.toLowerCase()

        let dataSrc = container.getAttribute("data-src");
        if (dataSrc != null) {
            let json = JSON.parse(dataSrc);

            let labels = json["labels"];

            let jsonData = json["data"];
            let dataSets = [];
            let colors = hexColors(jsonData.length - 1);
            for (let i = 1; i < jsonData.length; i++) {
                dataSets.push({
                    label: labels[i - 1],
                    data: jsonData[i],
                    backgroundColor: colors[i - 1],
                });
            }

            let data = {
            labels: jsonData[0],
            datasets: dataSets
            };
            let config = {
              type: 'bar',
              data: data,
              options: {

                plugins: {
                    legend: {
                      display: jsonData.length > 2
                    },
                    title: {
                        display: json["title"],
                        text: json["title"],
                    }
                },
                scales: {
                    x: {
                      stacked: isStacked,
                      title: {
                        display: json["x"],
                        text: json["x"]
                      }
                    },
                    y: {
                      stacked: isStacked,
                      title: {
                        display: json["y"],
                        text: json["y"]
                      }
                    }
                }
              }
            };
            let myChart = new Chart(
                container,
                config
              );
        }
    }
}

function setupTimeChart(elem, jsonData, isTime) {
    let title = elem.getAttribute("title");
    let width = elem.getBoundingClientRect().width;
    let height = elem.getBoundingClientRect().height;
    if (width == 0) {
        let parent = elem.parentElement;
        while (parent != null && width == 0) {
            width = parent.offsetWidth;
            parent = parent.parentElement;
        }

        console.log("Width " + width)
    }
    if (height < 100) height = 600;
    height = Math.min((width * 2) / 3, height)
    let data = jsonData["data"];
    let labels = jsonData["labels"];
    let xAxis = data[0];
    let series = [{}];
    let axis = [{}];
    axis.push({
        side: 3,
//        scale: data["x"],
//        size: 80,
        scale: data["x"],
//        scale: "left",
        values: (self, ticks) => ticks.map(rawValue => formatN(rawValue))
    });
    if (labels.length == 1) {
        axis[1]["label"] = labels[0];
    }
//    axis.push({
//            side: 1,
//            scale: data["y"],
//            size: 80,
//            values: (self, ticks) => ticks.map(rawValue => formatN(rawValue)),
//        });
    let colors = rgbColors(data.length - 1);
    for (let i = 1; i < data.length; i++) {
        let color = colors[i - 1];
        let colorHex = rgbToHex(color[0], color[1], color[2]);
        series.push({
            scale: data["x"],
            label: labels[i - 1],
            stroke: colorHex,
            width: 1 / devicePixelRatio,
            value: (self, rawValue) => formatN(rawValue),
        })
    }
    let opts = {
        scales: {
            x: {
                    time: isTime
            }
        },
        title: title,
        width: width,
        height: height,
        series: series,
        axes: axis,
    };
    elem.uplot = new uPlot(opts, data, elem);
    let resize = function () {
        let width = this.getBoundingClientRect().width;
        let height = this.uplot.height;
        height = Math.min((width * 2) / 3, height)
        if (width > 0) {
            this.uplot.setSize({width, height});
        }
    };
    $(window).on('resize', resize.bind(elem));
}

$(document).ready(function() {
    initCharts();
});