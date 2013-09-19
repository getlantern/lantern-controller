package org.lantern.charts;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a DataTable as used by Google Charts.
 */
public class DataTable extends HashMap<String, List> {
    private static final long serialVersionUID = 1L;

    private List<Map<String, Object>> cols = new ArrayList<Map<String, Object>>();
    private List<Map<String, List<Map<String, Object>>>> rows = new ArrayList<Map<String, List<Map<String, Object>>>>();

    public DataTable() {
        put("cols", cols);
        put("rows", rows);
    }

    public void addColumn(String id, String label, ColumnType type) {
        Map<String, Object> colSpec = new HashMap<String, Object>();
        colSpec.put("id", id);
        colSpec.put("label", label);
        colSpec.put("type", type.toString());
        cols.add(colSpec);
    }

    public Row addRow() {
        Row row = new Row();
        rows.add(row);
        return row;
    }

    public static class Row extends HashMap<String, List<Map<String, Object>>> {
        private static final long serialVersionUID = 1L;

        private List<Map<String, Object>> cells = new ArrayList<Map<String, Object>>();

        public Row() {
            put("c", cells);
        }

        public Row addCell(Object value) {
            if (value instanceof Date) {
                value = formatDate((Date) value);
            }
            return addCell(value, null);
        }

        public Row addCell(Object value, String formattedValue) {
            Map<String, Object> cell = new HashMap<String, Object>();
            cell.put("v", value);
            if (formattedValue != null) {
                cell.put("f", formattedValue);
            }
            cells.add(cell);
            return this;
        }

        private String formatDate(Date date) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            return String.format("Date(%1$s,%2$s,%3$s,%4$s,%5$s,%6$s)",
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
                    cal.get(Calendar.HOUR),
                    cal.get(Calendar.MINUTE),
                    cal.get(Calendar.SECOND));
        }
    }

    public static enum ColumnType {
        string,
        date,
        number
    }
}
