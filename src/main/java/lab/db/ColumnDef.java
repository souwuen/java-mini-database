package lab.db;

import java.io.Serializable;

public class ColumnDef implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String name;
    public final ColumnType type;
    public final boolean primaryKey;
    public final String referencesTable;


    public ColumnDef(String name, ColumnType type, boolean primaryKey, String referencesTable) {
        this.name = name.toLowerCase();
        this.type = type;
        this.primaryKey = primaryKey;
        this.referencesTable = referencesTable == null ? null : referencesTable.toLowerCase();
    }
}
