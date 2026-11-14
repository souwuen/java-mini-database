package lab.db;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TableData implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String name;
    public final List<ColumnDef> columns = new ArrayList<ColumnDef>();
    public final Map<Object, Map<String, Object>> rows = new LinkedHashMap<Object, Map<String, Object>>();
    public final Map<String, TableIndex> indexes = new LinkedHashMap<String, TableIndex>();

    public TableData(String name) { this.name = name.toLowerCase(); }

    public ColumnDef column(String name) {
        String wanted = name.toLowerCase();
        for (ColumnDef column : columns) if (column.name.equals(wanted)) return column;
        return null;
    }

    public ColumnDef primaryKey() {
        for (ColumnDef column : columns) if (column.primaryKey) return column;
        return null;
    }

    public TableIndex indexForColumn(String column) {
        for (TableIndex index : indexes.values()) if (index.column.equalsIgnoreCase(column)) return index;
        return null;
    }
}
