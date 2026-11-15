package lab.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SqlEngine {
    private final Database database;
    private List<String> tokens;
    private int position;

    public SqlEngine(Database database) { this.database = database; }

    public String execute(String sql) throws Exception {
        tokens = SqlTokenizer.tokenize(sql);
        position = 0;
        if (tokens.isEmpty()) return "";
        String command = take().toUpperCase();
        String result;
        if ("INSERT".equals(command)) result = insert();
        else if ("SELECT".equals(command)) result = select();
        else if ("UPDATE".equals(command)) result = update();
        else if ("DELETE".equals(command)) result = delete();
        else if ("CREATE".equals(command)) result = create();
        else if ("DROP".equals(command)) result = drop();
        else if ("HELP".equals(command)) result = help();
        else if ("BENCHMARK".equals(command)) result = benchmark();
        else throw new IllegalArgumentException("Команда не поддерживается: " + command);
        accept(";");
        if (position < tokens.size()) throw new IllegalArgumentException("Лишний текст после команды: " + peek());
        return result;
    }

    private String insert() throws Exception {
        expect("INTO");
        String tableName = take();
        TableData table = database.table(tableName);
        List<String> columns = new ArrayList<String>();
        if (accept("(")) { do { columns.add(take().toLowerCase()); } while (accept(",")); expect(")"); }
        else for (ColumnDef column : table.columns) columns.add(column.name);
        expect("VALUES");
        int count = 0;
        do {
            expect("(");
            List<Object> values = new ArrayList<Object>();
            do { values.add(rawValue()); } while (accept(","));
            expect(")");
            if (values.size() != columns.size()) throw new IllegalArgumentException("Число значений не совпадает с числом столбцов");
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (int i = 0; i < columns.size(); i++) {
                ColumnDef column = Database.requireColumn(table, columns.get(i));
                row.put(column.name, Database.convert(values.get(i).toString(), column.type));
            }
            database.insert(tableName, row);
            count++;
        } while (accept(","));
        return "Добавлено записей: " + count;
    }

    private String select() {
        List<String> projection = new ArrayList<String>();
        if (accept("*")) projection.add("*");
        else do { projection.add(take().toLowerCase()); } while (accept(","));
        expect("FROM");
        String tableName = take();
        TableData table = database.table(tableName);
        List<Condition> conditions = parseWhere(table);
        List<Map<String, Object>> rows = database.find(tableName, conditions, true);
        if (projection.size() == 1 && "*".equals(projection.get(0))) {
            projection.clear();
            for (ColumnDef column : table.columns) projection.add(column.name);
        }
        for (String col : projection) Database.requireColumn(table, col);
        return render(projection, rows);
    }

    private String update() throws Exception {
        String tableName = take();
        TableData table = database.table(tableName);
        expect("SET");
        Map<String, Object> changes = new LinkedHashMap<String, Object>();
        do {
            String columnName = take().toLowerCase();
            expect("=");
            ColumnDef column = Database.requireColumn(table, columnName);
            changes.put(column.name, Database.convert(rawValue().toString(), column.type));
        } while (accept(","));
        List<Condition> conditions = parseWhere(table);
        int count = database.find(tableName, conditions, true).size();
        database.update(tableName, conditions, changes);
        return "Обновлено записей: " + count;
    }

    private String delete() throws Exception {
        expect("FROM");
        String tableName = take();
        TableData table = database.table(tableName);
        List<Condition> conditions = parseWhere(table);
        int count = database.find(tableName, conditions, true).size();
        database.delete(tableName, conditions);
        return "Удалено записей: " + count;
    }

    private String create() throws Exception {
        String kind = take().toUpperCase();
        if ("INDEX".equals(kind)) {
            String indexName = take(); expect("ON"); String table = take();
            expect("("); String column = take(); expect(")");
            database.createIndex(indexName, table, column);
            return "Индекс создан: " + indexName;
        }
        if ("TABLE".equals(kind)) {
            String name = take(); expect("(");
            List<ColumnDef> columns = new ArrayList<ColumnDef>();
            do {
                String col = take();
                ColumnType type;
                try { type = ColumnType.valueOf(take().toUpperCase()); }
                catch (RuntimeException ex) { throw new IllegalArgumentException("Тип должен быть INT, DOUBLE, STRING, DATE или BOOLEAN"); }
                boolean pk = false; String references = null;
                if (accept("PRIMARY")) { expect("KEY"); pk = true; }
                if (accept("REFERENCES")) { references = take(); if (accept("(")) { take(); expect(")"); } }
                columns.add(new ColumnDef(col, type, pk, references));
            } while (accept(","));
            expect(")");
            database.createTable(name, columns);
            return "Таблица создана: " + name;
        }
        throw new IllegalArgumentException("Ожидалось CREATE INDEX или CREATE TABLE");
    }

    private String drop() throws Exception {
        expect("TABLE"); String name = take(); database.dropTable(name); return "Таблица удалена: " + name;
    }

    private List<Condition> parseWhere(TableData table) {
        List<Condition> conditions = new ArrayList<Condition>();
        if (accept("WHERE")) {
            do {
                String columnName = take().toLowerCase();
                ColumnDef column = Database.requireColumn(table, columnName);
                String operator = take();
                if (!"=".equals(operator) && !"!=".equals(operator) && !"<>".equals(operator)
                        && !">".equals(operator) && !">=".equals(operator) && !"<".equals(operator) && !"<=".equals(operator))
                    throw new IllegalArgumentException("Ожидался оператор сравнения");
                Object value = Database.convert(rawValue().toString(), column.type);
                conditions.add(new Condition(column.name, operator, value));
            } while (accept("AND"));
        }
        return conditions;
    }

    private Object rawValue() {
        String value = take();
        if (value.startsWith("'")) return value;
        return value;
    }

    private String benchmark() {
        String table = take(); String column = take(); Object value = rawValue(); int repetitions = Integer.parseInt(take());
        TableData data = database.table(table);
        ColumnDef def = Database.requireColumn(data, column);
        Object typedValue = Database.convert(value.toString(), def.type);
        long scan = database.benchmark(table, column, typedValue, repetitions, false);
        long indexed = database.benchmark(table, column, typedValue, repetitions, true);
        return "Повторы: " + repetitions + ", строк: " + data.rows.size() + ", индекс: "
                + (data.indexForColumn(column) == null ? "нет" : "есть")
                + "\nПолный перебор, нс: " + scan + "\nПоиск с индексом, нс: " + indexed;
    }

    private String render(List<String> columns, List<Map<String, Object>> rows) {
        List<Integer> widths = new ArrayList<Integer>();
        for (String col : columns) widths.add(col.length());
        for (Map<String, Object> row : rows) for (int i = 0; i < columns.size(); i++)
            widths.set(i, Math.max(widths.get(i), String.valueOf(row.get(columns.get(i))).length()));
        StringBuilder out = new StringBuilder();
        appendLine(out, columns, widths);
        for (int i = 0; i < widths.size(); i++) { if (i > 0) out.append("-+-"); for (int j = 0; j < widths.get(i); j++) out.append('-'); }
        out.append('\n');
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<String>();
            for (String col : columns) values.add(String.valueOf(row.get(col)));
            appendLine(out, values, widths);
        }
        out.append("Строк: ").append(rows.size());
        return out.toString();
    }

    private void appendLine(StringBuilder out, List<String> values, List<Integer> widths) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(" | ");
            out.append(values.get(i));
            for (int pad = values.get(i).length(); pad < widths.get(i); pad++) out.append(' ');
        }
        out.append('\n');
    }

    private String help() {
        return "Команды: INSERT INTO, SELECT, UPDATE, DELETE, CREATE INDEX, CREATE TABLE, DROP TABLE, BENCHMARK, HELP, EXIT.\n"
                + "Условие: WHERE column = value [AND column > value]. Дата: 'YYYY-MM-DD'.";
    }

    private boolean accept(String expected) {
        if (position < tokens.size() && tokens.get(position).equalsIgnoreCase(expected)) { position++; return true; }
        return false;
    }
    private void expect(String expected) {
        if (!accept(expected)) throw new IllegalArgumentException("Ожидалось '" + expected + "', получено '" + peek() + "'");
    }
    private String take() {
        if (position >= tokens.size()) throw new IllegalArgumentException("Команда неожиданно закончилась");
        return tokens.get(position++);
    }
    private String peek() { return position < tokens.size() ? tokens.get(position) : "конец команды"; }
}
