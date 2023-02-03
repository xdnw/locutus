/**
* Generate distinct RGB colors
*
* t is the total number of colors
* you want to generate.
*/
function rgbColors(t) {
    t = parseInt(t);
    if (t == 0) return [];
    if (t == 1) return [[255, 100, 100]];
    if (t < 1)
        throw new Error("'t' must be greater than 1.");

    // distribute the colors evenly on
    // the hue range (the 'H' in HSV)
    var i = 360 / (t - 1);

    // hold the generated colors
    var r = [];

    var s = 100;
    var v = 100;

    for (var x = 0; x < t; x++) {
        // alternate the s, v for more
        // contrast between the colors.
        r.push(hsvToRgb(i * x, s, v));
        if (t > 6) {
            v += 23;
            if (v > 100) {
                v -= 80;
                s += 23;
                if (s > 100) s -= 75;
            }
        }
    }
    return r;
};

function hexColors(t) {
    var result = []
    for (var color of rgbColors(t)) {
        let colorHex = rgbToHex(color[0], color[1], color[2]);
        result.push(colorHex);
    }
    return result;
}

function componentToHex(c) {
    var hex = c.toString(16);
    return hex.length == 1 ? "0" + hex : hex;
}
function rgbToHex(r, g, b) {
    return "#" + componentToHex(r) + componentToHex(g) + componentToHex(b);
}

/**
 * HSV to RGB color conversion
 *
 * H runs from 0 to 360 degrees
 * S and V run from 0 to 100
 *
 * Ported from the excellent java algorithm by Eugene Vishnevsky at:
 * http://www.cs.rit.edu/~ncs/color/t_convert.html
 */
var hsvToRgb = function(h, s, v) {
    var r, g, b;
    var i;
    var f, p, q, t;

    // Make sure our arguments stay in-range
    h = Math.max(0, Math.min(360, h));
    s = Math.max(0, Math.min(100, s));
    v = Math.max(0, Math.min(100, v));

    // We accept saturation and value arguments from 0 to 100 because that's
    // how Photoshop represents those values. Internally, however, the
    // saturation and value are calculated from a range of 0 to 1. We make
    // That conversion here.
    s /= 100;
    v /= 100;

    if (s == 0) {
        // Achromatic (grey)
        r = g = b = v;
        return [Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)];
    }

    h /= 60; // sector 0 to 5
    i = Math.floor(h);
    f = h - i; // factorial part of h
    p = v * (1 - s);
    q = v * (1 - s * f);
    t = v * (1 - s * (1 - f));

    switch (i) {
        case 0:
            r = v;
            g = t;
            b = p;
            break;
        case 1:
            r = q;
            g = v;
            b = p;
            break;
        case 2:
            r = p;
            g = v;
            b = t;
            break;
        case 3:
            r = p;
            g = q;
            b = v;
            break;
        case 4:
            r = t;
            g = p;
            b = v;
            break;
        default: // case 5:
            r = v;
            g = p;
            b = q;
    }

    return [Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)];
};