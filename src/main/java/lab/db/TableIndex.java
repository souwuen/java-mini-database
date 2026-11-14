package lab.db;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class TableIndex implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String name;
    public final String column;
    public final Map<Object, Set<Object>> values = new LinkedHashMap<Object, Set<Object>>();


    public TableIndex(String name, String column) {
        this.name = name.toLowerCase();
        this.column = column.toLowerCase();
    }

    public void add(Object value, Object primaryKey) {
        Set<Object> keys = values.get(value);
        if (keys == null) {
            keys = new LinkedHashSet<Object>();
            values.put(value, keys);
        }
        keys.add(primaryKey);
    }

    public void remove(Object value, Object primaryKey) {
        Set<Object> keys = values.get(value);
        if (keys != null) {
            keys.remove(primaryKey);
            if (keys.isEmpty()) values.remove(value);
        }
    }
}
