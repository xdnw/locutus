package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.util.IOUtil;
import link.locutus.discord.util.math.ArrayUtil;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.anarres.parallelgzip.ParallelGZIPInputStream;
import org.anarres.parallelgzip.ParallelGZIPOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Dictionary {
    private final File file;
    private Map<String, Integer> reverse;
    private ObjectArrayList<String> map;
    private boolean saved;

    public Dictionary(File folder) {
        this.file = new File(folder, "dict.bin");
        this.map = new ObjectArrayList<>();
        this.saved = true;
        this.reverse = new Object2IntOpenHashMap<>();
    }

    public Dictionary load() {
        if (!map.isEmpty() || !file.exists()) {
            return this;
        }
        try (DataInputStream in = new DataInputStream(new LZ4BlockInputStream(new FastBufferedInputStream(new FileInputStream(file), Character.MAX_VALUE)))) {
            int lines = IOUtil.readVarInt(in);
            int i = 0;
            for (int line = 0; line < lines; line++) {
                String value = in.readUTF();
                this.map.add(value);
                this.reverse.put(value, i);
                i++;
            }
        } catch (EOFException e) {
            // ignore
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public String get(int value) {
        return this.map.get(value);
    }

    public synchronized int put(String value) {
        Integer index = this.reverse.get(value);
        if (index != null) {
            return index;
        }
        int next = this.map.size();
        this.map.add(value);
        this.reverse.put(value, next);
        this.saved = false;
        return next;
    }

    public synchronized void save() {
        if (saved) {
            return;
        }
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
        try (DataOutputStream out = new DataOutputStream(new LZ4BlockOutputStream(new BufferedOutputStream(new FileOutputStream(file), Character.MAX_VALUE)))) {
            IOUtil.writeVarInt(out, map.size());
            for (int i = 0; i < map.size(); i++) {
                String value = map.get(i);
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
