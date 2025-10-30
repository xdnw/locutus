package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import link.locutus.discord.util.IOUtil;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.function.Supplier;

public class Dictionary {

    private final File file;
    private final Supplier<? extends ICodedStringMap> mapSupplier;

    private final Object loadLock = new Object();

    private volatile ICodedStringMap mapReference = null;
    private volatile ICodedStringMap dirtyStrongRef;

    private volatile boolean loaded;
    private volatile boolean saved = true;

    public Dictionary(File folder) {
        this(folder, FrontCodedStringMap::new);
    }

    public Dictionary(File folder, Supplier<? extends ICodedStringMap> mapSupplier) {
        this.file = new File(folder, "dict.bin");
        this.mapSupplier = Objects.requireNonNull(mapSupplier, "mapSupplier");
    }

    public ICodedStringMap getMap() {
        return currentMap();
    }

    public Dictionary load() {
        currentMap();
        return this;
    }

    public String get(int value) {
        if (value == -1) {
            return "";
        }
        return currentMap().get(value);
    }

    public synchronized int put(String value) {
        if (value.isEmpty()) {
            return -1;
        }
        ICodedStringMap map = currentMap();
        int oldSize = map.size();
        int index = map.insert(value);
        if (map.size() != oldSize) {
            saved = false;
            updateMapRefs(map, true); // keep a strong ref while dirty
        }
        return index;
    }

    public synchronized void save() {
        if (saved) {
            return;
        }

        ICodedStringMap map = currentMap();

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IllegalStateException("Unable to create directory " + parent);
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        map.finishLoad();

        try (DataOutputStream out = new DataOutputStream(new LZ4BlockOutputStream(
                new BufferedOutputStream(new FileOutputStream(file), Character.MAX_VALUE)))) {

            IOUtil.writeVarInt(out, map.size());
            for (int i = 0; i < map.size(); i++) {
                String entry = map.get(i);
                if (entry != null) {
                    out.writeUTF(entry);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        saved = true;
        updateMapRefs(map, false); // release strong ref, keep only soft ref
    }

    private ICodedStringMap currentMap() {
        ICodedStringMap map = dirtyStrongRef;
        if (map != null) {
            return map;
        }

        map = mapReference;
        if (map != null && loaded) {
            return map;
        }

        return ensureMapLoaded();
    }

    private ICodedStringMap ensureMapLoaded() {
        ICodedStringMap map = dirtyStrongRef;
        if (map != null && loaded) {
            return map;
        }

        map = mapReference;
        if (map != null && loaded) {
            return map;
        }

        synchronized (loadLock) {
            map = dirtyStrongRef;
            if (map != null && loaded) {
                return map;
            }

            map = mapReference;
            if (map != null && loaded) {
                return map;
            }

            map = loadInternal();
            loaded = true;
            updateMapRefs(map, false);
            return map;
        }
    }

    private ICodedStringMap loadInternal() {
        ICodedStringMap map = (!loaded) ? mapReference : null;
        if (map == null) {
            map = Objects.requireNonNull(mapSupplier.get(), "mapSupplier returned null");
        }

        if (!file.exists()) {
            map.finishLoad();
            saved = true;
            return map;
        }

        try (DataInputStream in = new DataInputStream(new LZ4BlockInputStream(
                new FastBufferedInputStream(new FileInputStream(file), Character.MAX_VALUE)))) {

            int lines = IOUtil.readVarInt(in);
            for (int line = 0; line < lines; line++) {
                String value = in.readUTF();
                if (!value.isEmpty()) {
                    map.insert(value);
                }
            }
        } catch (EOFException ignored) {
            // treat truncated dictionary as empty
        } catch (IOException e) {
            throw new RuntimeException("Failed to load dictionary from " + file, e);
        }

        map.finishLoad();
        saved = true;
        return map;
    }

    private void updateMapRefs(ICodedStringMap map, boolean keepStrong) {
        mapReference = map;
        dirtyStrongRef = keepStrong ? map : null;
    }

    private static Supplier<? extends ICodedStringMap> supplierFor(ICodedStringMap prototype) {
        Objects.requireNonNull(prototype, "prototype");
        final Constructor<? extends ICodedStringMap> ctor;
        try {
            ctor = prototype.getClass().getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Provided map type " + prototype.getClass().getName()
                    + " must expose a no-arg constructor or supply a Supplier instead.", e);
        }
        return () -> {
            try {
                return ctor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to instantiate " + ctor.getDeclaringClass().getName(), e);
            }
        };
    }
}