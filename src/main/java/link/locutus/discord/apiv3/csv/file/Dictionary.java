package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import link.locutus.discord.util.IOUtil;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

import java.io.*;

public class Dictionary {
    private final File file;
    private ICodedStringMap compressed;
    private volatile boolean loaded;
    private boolean saved;

    public Dictionary(File folder) {
        this(folder, new FlatCodedStringMap());
    }

    public Dictionary(File folder, ICodedStringMap map) {
        this.file = new File(folder, "dict.bin");
        this.compressed = map;
        this.saved = true;
    }

    public ICodedStringMap getMap() {
        return this.compressed;
    }

    public Dictionary load() {
        if (loaded) return this;
        if (!file.exists()) {
            loaded = true;
            return this;
        }
        synchronized (this) {
            if (loaded) return this;
            loaded = true;
            try (DataInputStream in = new DataInputStream(new LZ4BlockInputStream(
                    new FastBufferedInputStream(new FileInputStream(file), Character.MAX_VALUE)))) {
                int lines = IOUtil.readVarInt(in);
                for (int line = 0; line < lines; line++) {
                    String value = in.readUTF();
                    if (value.isEmpty()) continue;
                    this.compressed.insert(value);
                }
                this.compressed.finishLoad();
            } catch (EOFException e) {
                // ignore
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this;
    }

    public String get(int value) {
        if (value == -1) return "";
        return this.compressed.get(value);
    }

    public synchronized int put(String value) {
        if (value.isEmpty()) return -1;
        int oldSize = this.compressed.size();
        int index = this.compressed.insert(value);
        int newSize = this.compressed.size();
        if (oldSize != newSize) {
            this.saved = false;
        }
        return index;
    }

    public synchronized void save() {
        if (saved) return;

        File parent = file.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        this.compressed.finishLoad();

        try (DataOutputStream out = new DataOutputStream(new LZ4BlockOutputStream(
                new BufferedOutputStream(new FileOutputStream(file), Character.MAX_VALUE)))) {

            IOUtil.writeVarInt(out, compressed.size());
            for (int i = 0; i < compressed.size(); i++) {
                String value = compressed.get(i);
                if (value != null) {
                    out.writeUTF(value);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        saved = true;
    }
}