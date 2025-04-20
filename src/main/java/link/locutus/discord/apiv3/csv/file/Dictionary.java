package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.util.IOUtil;
import link.locutus.discord.util.StringMan;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

import java.io.*;
import java.util.*;

public class Dictionary {
    private final File file;
    private Map<Long, Integer> reverseHash;
    private ICodedStringMap compressed;
    private volatile boolean loaded;
    private boolean saved;

    public Dictionary(File folder) {
        this(folder, new GlobalSubstringDAG());
    }

    public Dictionary(File folder, ICodedStringMap map) {
        this.file = new File(folder, "dict.bin");
        this.compressed = map;
        this.saved = true;
        this.reverseHash = new Long2IntOpenHashMap();
    }

    public Dictionary load() {
        if (loaded) return this;

        synchronized (this) {
            if (loaded) return this;
            loaded = true;
            try (DataInputStream in = new DataInputStream(new LZ4BlockInputStream(
                    new FastBufferedInputStream(new FileInputStream(file), Character.MAX_VALUE)))) {

                int lines = IOUtil.readVarInt(in);
                int i = 0;
                for (int line = 0; line < lines; line++) {
                    String value = in.readUTF();
                    long hash = StringMan.hash(value);
                    if (this.reverseHash.putIfAbsent(hash, i) == null) {
                        this.compressed.insert(value);
                        i++;
                    }
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
        return this.compressed.get(value);
    }

    public synchronized int put(String value) {
        long hash = StringMan.hash(value);
        Integer index = this.reverseHash.get(hash);
        if (index != null) {
            return index;
        }
        int newIndex = this.compressed.insert(value);
        this.reverseHash.put(hash, newIndex);
        this.saved = false;
        return newIndex;
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