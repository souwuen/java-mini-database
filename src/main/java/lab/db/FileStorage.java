package lab.db;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FileStorage {
    private final File file;
    private final File temp;

    public FileStorage(File file) {
        this.file = file;
        this.temp = new File(file.getPath() + ".tmp");
    }

    public Database load() throws Exception {
        File source = file.exists() ? file : temp.exists() ? temp : null;
        if (source == null) return null;
        ObjectInputStream input = new ObjectInputStream(new FileInputStream(source));
        try { return (Database) input.readObject(); }
        finally { input.close(); }
    }

    public void save(Database database) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new Exception("Не удалось создать папку " + parent);
        ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(temp));
        try { output.writeObject(database); output.flush(); }
        finally { output.close(); }
        try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (Exception atomicMoveUnavailable) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }
}
