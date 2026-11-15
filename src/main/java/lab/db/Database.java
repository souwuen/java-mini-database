package lab.db;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Database implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, TableData> tables = new LinkedHashMap<String, TableData>();
    private transient FileStorage storage;


    public static Database open(File file) throws Exception {
        FileStorage storage = new FileStorage(file);
        Database database = storage.load();
        if (database == null) {
            database = new Database();
            database.createDefaults();
        }
        database.storage = storage;
        database.rebuildIndexes();
        database.save();
        return database;
    }

    private void createDefaults() {
        TableData owners = new TableData("owners");
        owners.columns.add(new ColumnDef("id", ColumnType.INT, true, null));
        owners.columns.add(new ColumnDef("name", ColumnType.STRING, false, null));
        owners.columns.add(new ColumnDef("age", ColumnType.INT, false, null));
        owners.columns.add(new ColumnDef("rating", ColumnType.DOUBLE, false, null));
        owners.columns.add(new ColumnDef("registered", ColumnType.DATE, false, null));
        owners.columns.add(new ColumnDef("active", ColumnType.BOOLEAN, false, null));
        tables.put(owners.name, owners);

        TableData cars = new TableData("cars");
        cars.columns.add(new ColumnDef("id", ColumnType.INT, true, null));
        cars.columns.add(new ColumnDef("owner_id", ColumnType.INT, false, "owners"));
        cars.columns.add(new ColumnDef("brand", ColumnType.STRING, false, null));
        cars.columns.add(new ColumnDef("price", ColumnType.DOUBLE, false, null));
        cars.columns.add(new ColumnDef("year", ColumnType.INT, false, null));
        cars.columns.add(new ColumnDef("available", ColumnType.BOOLEAN, false, null));
        tables.put(cars.name, cars);
    }


    public TableData table(String name) {
        TableData table = tables.get(name.toLowerCase());
        if (table == null) throw new IllegalArgumentException("Таблица не найдена: " + name);
        return table;
    }

    public List<String> tableNames() { return new ArrayList<String>(tables.keySet()); }

    public void createTable(String name, List<ColumnDef> columns) throws Exception {
        String key = name.toLowerCase();
        if (tables.containsKey(key)) throw new IllegalArgumentException("Таблица уже существует: " + name);
        int primaryKeys = 0;
        for (ColumnDef column : columns) {
            if (column.primaryKey) primaryKeys++;
            for (ColumnDef prior : columns) if (prior != column && prior.name.equals(column.name))
                throw new IllegalArgumentException("Повтор столбца: " + column.name);
        }
        if (columns.isEmpty() || primaryKeys != 1) throw new IllegalArgumentException("Таблица должна иметь ровно один PRIMARY KEY");
        for (ColumnDef column : columns) if (column.referencesTable != null) table(column.referencesTable);
        TableData table = new TableData(key);
        table.columns.addAll(columns);
        tables.put(key, table);
        save();
    }

    public void dropTable(String name) throws Exception {
        TableData target = table(name);
        for (TableData candidate : tables.values()) for (ColumnDef column : candidate.columns)
            if (column.referencesTable != null && column.referencesTable.equals(target.name))
                throw new IllegalArgumentException("Таблица используется внешним ключом: " + candidate.name + "." + column.name);
        tables.remove(target.name);
        save();
    }

    public void insert(String tableName, Map<String, Object> input) throws Exception {
        TableData table = table(tableName);
        for (String supplied : input.keySet()) requireColumn(table, supplied);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (ColumnDef column : table.columns) {
            Object value = input.get(column.name);
            if (value != null && !matchesType(value, column.type)) throw new IllegalArgumentException("Неверный тип столбца " + column.name + "; ожидается " + column.type);
            if (column.referencesTable != null && value != null && !table(column.referencesTable).rows.containsKey(value))
                throw new IllegalArgumentException("Нет записи " + value + " в таблице " + column.referencesTable);
            row.put(column.name, value);
        }
        Object key = row.get(table.primaryKey().name);
        if (key == null) throw new IllegalArgumentException("Первичный ключ не может быть пустым");
        if (table.rows.containsKey(key)) throw new IllegalArgumentException("Первичный ключ уже существует: " + key);
        table.rows.put(key, row);
        for (TableIndex index : table.indexes.values()) index.add(row.get(index.column), key);
        save();
    }

    public void update(String tableName, List<Condition> conditions, Map<String, Object> changes) throws Exception {
        TableData table = table(tableName);
        List<Map<String, Object>> matched = find(tableName, conditions, true);
        Map<Object, Map<String, Object>> replacements = new LinkedHashMap<Object, Map<String, Object>>();
        for (Map<String, Object> old : matched) {
            Map<String, Object> row = new LinkedHashMap<String, Object>(old);
            for (Map.Entry<String, Object> change : changes.entrySet()) {
                ColumnDef column = requireColumn(table, change.getKey());
                if (!matchesType(change.getValue(), column.type)) throw new IllegalArgumentException("Неверный тип столбца " + column.name);
                row.put(column.name, change.getValue());
            }
            Object key = row.get(table.primaryKey().name);
            if (!key.equals(old.get(table.primaryKey().name)) && table.rows.containsKey(key))
                throw new IllegalArgumentException("Первичный ключ уже существует: " + key);
            for (ColumnDef column : table.columns) if (column.referencesTable != null && row.get(column.name) != null
                    && !table(column.referencesTable).rows.containsKey(row.get(column.name)))
                throw new IllegalArgumentException("Нет связанной записи в таблице " + column.referencesTable);
            replacements.put(old.get(table.primaryKey().name), row);
        }
        for (Map.Entry<Object, Map<String, Object>> entry : replacements.entrySet()) {
            Object oldKey = entry.getKey();
            Map<String, Object> row = entry.getValue();
            Object newKey = row.get(table.primaryKey().name);
            for (TableIndex index : table.indexes.values()) index.remove(table.rows.get(oldKey).get(index.column), oldKey);
            table.rows.remove(oldKey);
            table.rows.put(newKey, row);
            for (TableIndex index : table.indexes.values()) index.add(row.get(index.column), newKey);
        }
        if (!matched.isEmpty()) save();
    }

    public void delete(String tableName, List<Condition> conditions) throws Exception {
        TableData table = table(tableName);
        List<Map<String, Object>> matched = find(tableName, conditions, true);
        for (Map<String, Object> row : matched) {
            Object key = row.get(table.primaryKey().name);
            for (TableData candidate : tables.values()) for (ColumnDef column : candidate.columns)
                if (table.name.equals(column.referencesTable))
                    for (Map<String, Object> child : candidate.rows.values())
                        if (row.get(table.primaryKey().name).equals(child.get(column.name)))
                            throw new IllegalArgumentException("Удаление запрещено: запись используется таблицей " + candidate.name);
            for (TableIndex index : table.indexes.values()) index.remove(row.get(index.column), key);
            table.rows.remove(key);
        }
        if (!matched.isEmpty()) save();
    }

    public List<Map<String, Object>> find(String tableName, List<Condition> conditions, boolean useIndex) {
        TableData table = table(tableName);
        Set<Object> candidates = null;
        if (useIndex) for (Condition condition : conditions) {
            TableIndex index = table.indexForColumn(condition.column);
            if (index != null) {
                candidates = new LinkedHashSet<Object>();
                for (Map.Entry<Object, Set<Object>> entry : index.values.entrySet())
                    if (Condition.compare(entry.getKey(), condition.value, condition.operator)) candidates.addAll(entry.getValue());
                break;
            }
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        Iterable<Map<String, Object>> source;
        if (candidates == null) source = table.rows.values();
        else {
            List<Map<String, Object>> selected = new ArrayList<Map<String, Object>>();
            for (Object key : candidates) if (table.rows.containsKey(key)) selected.add(table.rows.get(key));
            source = selected;
        }
        for (Map<String, Object> row : source) {
            boolean matches = true;
            for (Condition condition : conditions) {
                requireColumn(table, condition.column);
                if (!Condition.compare(row.get(condition.column), condition.value, condition.operator)) { matches = false; break; }
            }
            if (matches) result.add(row);
        }
        return result;
    }

    public void createIndex(String indexName, String tableName, String columnName) throws Exception {
        TableData table = table(tableName);
        ColumnDef column = requireColumn(table, columnName);
        if (table.indexes.containsKey(indexName.toLowerCase())) throw new IllegalArgumentException("Индекс уже существует");
        if (table.indexForColumn(column.name) != null) throw new IllegalArgumentException("Для столбца уже создан индекс");
        TableIndex index = new TableIndex(indexName, column.name);
        for (Map.Entry<Object, Map<String, Object>> row : table.rows.entrySet()) index.add(row.getValue().get(column.name), row.getKey());
        table.indexes.put(index.name, index);
        save();
    }


    public long benchmark(String table, String column, Object value, int repetitions, boolean indexed) {
        if (repetitions < 1 || repetitions > 10000000) throw new IllegalArgumentException("Число повторов должно быть от 1 до 10000000");
        TableData data = table(table);
        List<Condition> conditions = Collections.singletonList(new Condition(column, "=", value));
        long start = System.nanoTime();
        int matches = 0;
        for (int i = 0; i < repetitions; i++) matches += find(data.name, conditions, indexed).size();
        long elapsed = System.nanoTime() - start;
        if (matches < 0) throw new IllegalStateException();
        return elapsed;
    }

    private void rebuildIndexes() {
        for (TableData table : tables.values()) for (TableIndex index : table.indexes.values()) {
            index.values.clear();
            for (Map.Entry<Object, Map<String, Object>> row : table.rows.entrySet()) index.add(row.getValue().get(index.column), row.getKey());
        }
    }

    private void save() throws Exception {
        if (storage != null) storage.save(this);
    }

    public static Object convert(String raw, ColumnType type) {
        try {
            if (raw.startsWith("'") && raw.length() >= 1) raw = raw.substring(1);
            switch (type) {
                case INT: return Integer.valueOf(raw);
                case DOUBLE: return Double.valueOf(raw);
                case STRING: return raw;
                case DATE: return LocalDate.parse(raw);
                case BOOLEAN:
                    if (raw.equalsIgnoreCase("true")) return Boolean.TRUE;
                    if (raw.equalsIgnoreCase("false")) return Boolean.FALSE;
                    throw new IllegalArgumentException("Ожидается TRUE или FALSE");
                default: throw new IllegalArgumentException("Неизвестный тип");
            }
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Не удалось прочитать значение '" + raw + "' как " + type); }
    }

    private static boolean matchesType(Object value, ColumnType type) {
        switch (type) {
            case INT: return value instanceof Integer;
            case DOUBLE: return value instanceof Double;
            case STRING: return value instanceof String;
            case DATE: return value instanceof LocalDate;
            case BOOLEAN: return value instanceof Boolean;
            default: return false;
        }
    }

    static ColumnDef requireColumn(TableData table, String name) {
        ColumnDef column = table.column(name);
        if (column == null) throw new IllegalArgumentException("Неизвестный столбец: " + name);
        return column;
    }
}

class Condition implements Serializable {
    private static final long serialVersionUID = 1L;
    final String column;
    final String operator;
    final Object value;
    Condition(String column, String operator, Object value) { this.column = column.toLowerCase(); this.operator = operator; this.value = value; }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean compare(Object left, Object right, String operator) {
        int comparison;
        if (left == null || right == null) comparison = left == right ? 0 : Integer.MIN_VALUE;
        else if (left instanceof Number && right instanceof Number) comparison = Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        else if (left instanceof Comparable && left.getClass().isInstance(right)) comparison = ((Comparable) left).compareTo(right);
        else comparison = left.equals(right) ? 0 : Integer.MIN_VALUE;
        if ("=".equals(operator)) return comparison == 0;
        if ("!=".equals(operator) || "<>".equals(operator)) return comparison != 0;
        if (">".equals(operator)) return comparison > 0;
        if (">=".equals(operator)) return comparison >= 0;
        if ("<".equals(operator)) return comparison < 0;
        if ("<=".equals(operator)) return comparison <= 0;
        throw new IllegalArgumentException("Неизвестный оператор: " + operator);
    }
}
