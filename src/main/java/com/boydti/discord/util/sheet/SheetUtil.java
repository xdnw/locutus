package com.boydti.discord.util.sheet;

import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.RowData;

import java.util.ArrayList;
import java.util.List;

public class SheetUtil {
    public static String getLetter(int x) {
        x++;
        String letter = "";
        while (x > 0) {
            int r = (x - 1) % 26;
            int n = (x - 1) / 26;
            letter = ((char) ('A' + r)) + letter;
            x = n;
        }
        return letter;
    }

    public static int getIndex(String column) {
        column = column.toUpperCase();
        int out = 0, len = column.length();
        for (int pos = 0; pos < len; pos++) {
            out += (column.charAt(pos) - 64) * Math.pow(26, len - pos - 1);
        }
        return out;
    }

    public static String getRange(int x, int y) {
        return getLetter(x) + "" + (y + 1);
    }

    public static String getRange(int x1, int y1, int x2, int y2) {
        return getRange(x1, y1) + ":" + getRange(x2, y2);
    }

    public static RowData toRowData(List myList) {
        RowData row = new RowData();
        ArrayList<CellData> cellData = new ArrayList<CellData>();
        for (int i = 0; i < myList.size(); i++) {
            Object obj = myList.get(i);
            if (obj == null) cellData.add(null);

            CellData cell = new CellData();
            String str = obj.toString();
            if (str.startsWith("=")) {
                cell.setUserEnteredValue(new ExtendedValue().setFormulaValue(str));
            } else {
                cell.setUserEnteredValue(new ExtendedValue().setStringValue(str));
            }
            cellData.add(cell);

        }
        row.setValues(cellData);
        return row;
    }
}
