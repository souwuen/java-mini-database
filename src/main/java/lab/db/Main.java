package lab.db;

import java.io.File;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try {
            Database database = Database.open(new File("data/database.bin"));
            SqlEngine engine = new SqlEngine(database);
            System.out.println("Упрощённая СУБД. Введите HELP для справки, EXIT для выхода.");
            Scanner scanner = new Scanner(System.in, "UTF-8");
            while (true) {
                System.out.print("db> ");
                if (!scanner.hasNextLine()) break;
                String command = scanner.nextLine().trim();
                if (command.equalsIgnoreCase("EXIT") || command.equalsIgnoreCase("EXIT;")) break;
                if (command.isEmpty()) continue;
                try { System.out.println(engine.execute(command)); }
                catch (Exception ex) { System.out.println("Ошибка: " + ex.getMessage()); }
            }
            scanner.close();
            System.out.println("Работа завершена.");
        } catch (Exception ex) {
            System.err.println("Не удалось открыть базу: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
