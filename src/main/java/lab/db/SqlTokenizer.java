package lab.db;

import java.util.ArrayList;
import java.util.List;

public final class SqlTokenizer {
    private SqlTokenizer() { }


    public static List<String> tokenize(String sql) {
        List<String> tokens = new ArrayList<String>();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '\'') {
                StringBuilder value = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < sql.length()) {
                    char q = sql.charAt(i++);
                    if (q == '\'') {
                        if (i < sql.length() && sql.charAt(i) == '\'') { value.append('\''); i++; }
                        else { closed = true; break; }
                    } else value.append(q);
                }
                if (!closed) throw new IllegalArgumentException("Строка не закрыта одинарной кавычкой");
                tokens.add("'" + value.toString());
                continue;
            }
            if (c == '(' || c == ')' || c == ',' || c == ';' || c == '*') {
                tokens.add(String.valueOf(c)); i++; continue;
            }
            if (c == '=' || c == '!' || c == '<' || c == '>') {
                String op = String.valueOf(c); i++;
                if (i < sql.length() && (sql.charAt(i) == '=' || (c == '<' && sql.charAt(i) == '>'))) op += sql.charAt(i++);
                tokens.add(op); continue;
            }
            int start = i;
            while (i < sql.length() && !Character.isWhitespace(sql.charAt(i))
                    && "(),;*='!<>".indexOf(sql.charAt(i)) < 0) i++;
            if (start == i) throw new IllegalArgumentException("Неожиданный символ: " + c);
            tokens.add(sql.substring(start, i));
        }
        return tokens;
    }
}
