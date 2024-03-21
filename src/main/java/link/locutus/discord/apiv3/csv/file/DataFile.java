package link.locutus.discord.apiv3.csv.file;

import de.siegmar.fastcsv.reader.CloseableIterator;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import net.jpountz.util.SafeUtils;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class DataFile<T, H extends DataHeader<T>> {
    private final File csvFile;
    private final File binFile;
    private boolean csvExists = false;
    private boolean binExists = false;
    private final H header;
    private final Map<String, ColumnInfo<T, Object>> headers;
    private final Map<String, String> aliases;
    private final long date;
    private final long day;
    private final String filePart;
    private SoftReference<byte[]> cachedBytes = new SoftReference<>(null);

    public static boolean isValidName(File file, String requirePrefix) {
        String[] split = file.getName().split("\\.", 2);
        if (split.length != 2) return false;
        String ext = split[1];
        if (!ext.equals("csv") && !ext.equals("bin")) return false;
        String[] split2 = split[0].split("-", 4);
        if (split2.length != 4) return false;
        if (!split2[0].equals(requirePrefix)) return false;
        return MathMan.isInteger(split2[1]) && MathMan.isInteger(split2[2]) && MathMan.isInteger(split2[3]);
    }

    public String getFilePart() {
        return filePart;
    }

    public DataFile(File file, H unloaded) {
        this.filePart = file.getName().split("\\.")[0];
        this.csvFile = new File(file.getParent(), filePart + ".csv");
        this.binFile = new File(file.getParent(), filePart + ".bin");
        this.csvExists = csvFile.exists();
        this.binExists = binFile.exists();
        this.header = unloaded;
        this.headers = unloaded.getHeaders();
        this.aliases = new Object2ObjectLinkedOpenHashMap<>(1);
        for (Map.Entry<String, ColumnInfo<T, Object>> entry : this.headers.entrySet()) {
            ColumnInfo<T, Object> col = entry.getValue();
            String[] aliases = col.getAliases();
            if (aliases != null) {
                for (String alias : aliases) {
                    this.aliases.put(alias, entry.getKey());
                }
            }
        }

        this.date = unloaded.getDate();
        this.day = TimeUtil.getDay(date);
    }

    public static long parseDateFromFile(String fileName) {
        String dateStr = fileName.replace("nations-", "").replace("cities-", "").replace(".csv", "").replace(".bin", "");
        try {
            return TimeUtil.YYYY_MM_DD_FORMAT.parse(dateStr).toInstant().toEpochMilli();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public long getDate() throws ParseException {
        return date;
    }

    public long getDay() {
        return day;
    }

    public H getHeader() {
        return header;
    }

    private void readAllCsv(File file, ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>> onEach) throws IOException {
        try (CsvReader reader = CsvReader.builder().fieldSeparator(',').quoteCharacter('"').build(file.toPath())) {
            try (CloseableIterator<CsvRow> iter = reader.iterator()) {
                CsvRow header = iter.next();
                List<String> fields = header.getFields().stream().map(s -> s.replaceAll("[^a-z_]", "")).toList();
                onEach.accept(fields, iter);
            }
        } catch (PoliticsAndWarV3.PageError e) {
            // allowed exit
        }
    }

    public File getCompressedFile(boolean create, boolean deleteCsv) throws IOException {
        if (binExists) {
            return binFile;
        }
        if (binFile.exists()) {
            binExists = true;
            if (deleteCsv) {
                csvFile.deleteOnExit();
            }
            return binFile;
        }
        if (!create) return null;
        if (!csvExists) {
            throw new IllegalArgumentException("CSV file does not exist: " + csvFile);
        }
        readAllCsv(csvFile, (csvHeader, row) -> {
            List<ColumnInfo<T, Object>> columns = new ArrayList<>();
            for (int i = 0; i < csvHeader.size(); i++) {
                String header = csvHeader.get(i);
                ColumnInfo<T, Object> column = headers.get(header);
                if (column == null) {
                    String alias = aliases.get(header);
                    if (alias != null) {
                        column = headers.get(alias);
                    }
                } else if (column.getIndex() != -1) {
                    continue;
                }
                if (column != null) {
                    column.setIndex(i, 0);
                    columns.add(column);
                } else {
                    throw new IllegalArgumentException("Unknown header: `" + header + "` in " + csvFile);
                }
            }
            FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
            try (DataOutputStream dos = new DataOutputStream(baos)) {
                dos.writeInt(headers.size());
                for (ColumnInfo<T, ?> header : headers.values()) {
                    dos.writeBoolean(header.getIndex() != -1);
                }
                List<List<Object>> all = new ObjectArrayList<>();
                while (row.hasNext()) {
                    CsvRow csvRow = row.next();
                    ObjectArrayList<Object> rowData = new ObjectArrayList<>(columns.size());
                    for (ColumnInfo<T, Object> column : columns) {
                        String cell = csvRow.getField(column.getIndex());
                        Object value = column.read(cell);
                        rowData.add(value);
                    }
                    all.add(rowData);
                }
                dos.writeInt(all.size());
                for (List<Object> rowData : all) {
                    for (ColumnInfo<T, Object> column : columns) {
                        column.write(dos, rowData.get(column.getIndex()));
                    }
                }
            }

            baos.trim();
            byte[] compressed = ArrayUtil.compressLZ4(baos.array);
            try (FileOutputStream fos = new FileOutputStream(binFile)) {
                fos.write(compressed);
            }
        });
        header.getDictionary().save();
        binExists = true;
        if (deleteCsv) {
            csvFile.deleteOnExit();
        }
        return binFile;
    }

//    public void testCsv() throws IOException {
//        readAllCsv(csvFile, (csvHeader, row) -> {
//            while (row.hasNext()) {
//                CsvRow csvRow = row.next();
//            }
//        });
//    }
//
//    public void testRead() throws IOException {
//        File file = getCompressedFile(true, false);
//        byte[] decompressed = ArrayUtil.decompressLZ4(Files.readAllBytes(file.toPath()));
//
//        // read with gzip, ignore all data
//        int fileSize = 524288;
//        try (DataInputStream dis = new DataInputStream(new LZ4BlockInputStream(new FastBufferedInputStream(new FileInputStream(file), fileSize)))) {
//            // skip all
//            ObjectArrayList<ColumnInfo<T, Object>> allColumns = new ObjectArrayList<>(headers.values());
//            int rowBytes = 0;
//            int size = dis.readInt();
//            for (int i = 0; i < size; i++) {
//                boolean hasIndex = dis.readBoolean();
//                if (hasIndex) {
//                    ColumnInfo<T, Object> column = allColumns.get(i);
//                    rowBytes += column.getBytes();
//                }
//            }
//            int numLines = IOUtil.readVarInt(dis);
//            for (int i = 0; i < numLines; i++) {
//                dis.skipBytes(rowBytes);
//            }
//        }
//    }

    public class Builder {
        List<ColumnInfo<T, Object>> requiredColumns = new ObjectArrayList<>();
        List<ColumnInfo<T, Object>> optionalColumns = new ObjectArrayList<>();

        public Builder all(boolean includeNonData) {
            optionalColumns.addAll(headers.values().stream().filter(f -> includeNonData || !f.isAlwaysSkip()).toList());
            return this;
        }

        public Builder required(Function<H, List<ColumnInfo>> columns) {
            columns.apply(header).forEach(requiredColumns::add);
            return this;
        }

        public Builder optional(Function<H, List<ColumnInfo>> columns) {
            columns.apply(header).forEach(optionalColumns::add);
            return this;
        }

        public Builder required(ColumnInfo... columns) {
            for (ColumnInfo<T, Object> column : columns) {
                requiredColumns.add(column);
            }
            return this;
        }

        public Builder required(String... names) {
            for (String name : names) {
                ColumnInfo<T, Object> column = headers.get(name);
                if (column == null) {
                    throw new IllegalArgumentException("Unknown header: `" + name + "` in " + filePart);
                }
                requiredColumns.add(column);
            }
            return this;
        }

        public Builder optional(ColumnInfo... columns) {
            for (ColumnInfo<T, Object> column : columns) {
                optionalColumns.add(column);
            }
            return this;
        }

        public Builder optional(String... names) {
            for (String name : names) {
                ColumnInfo<T, Object> column = headers.get(name);
                if (column == null) {
                    throw new IllegalArgumentException("Unknown header: `" + name + "` in " + filePart);
                }
                optionalColumns.add(column);
            }
            return this;
        }

        public void read(Consumer<H> onEachRow) throws IOException {
            List<ColumnInfo<T, Object>> presentColumns = new ObjectArrayList<>();
            List<ColumnInfo<T, Object>> allColumns = new ObjectArrayList<>(headers.values());
            for (ColumnInfo<T, Object> col : allColumns) {
                col.setIndex(-1, -1);
                col.setCachedValue(null);
            }
            header.clear();
            byte[] decompressed = cachedBytes.get();
            if (decompressed == null) {
                File binFile = getCompressedFile(true, false);
                decompressed = ArrayUtil.decompressLZ4(Files.readAllBytes(binFile.toPath()));
                cachedBytes = new SoftReference<>(decompressed);
            }
            int index = 0;
            int size = SafeUtils.readIntBE(decompressed, index);
            index += 4;
            int rowBytes = 0;
            for (int i = 0; i < size; i++) {
                boolean hasIndex = decompressed[index++] != 0;
                if (hasIndex) {
                    ColumnInfo<T, Object> column = allColumns.get(i);
                    column.setIndex(i, rowBytes);
                    presentColumns.add(column);
                    rowBytes += column.getBytes();
                }
            }

            Set<ColumnInfo<T, Object>> presetAndSpecified = new ObjectLinkedOpenHashSet<>();
            for (ColumnInfo<T, Object> column : requiredColumns) {
                if (column.getIndex() == -1) {
                    throw new IllegalArgumentException("Required column `" + column + "` is missing in " + filePart);
                }
                presetAndSpecified.add(column);
            }
            for (ColumnInfo<T, Object> column : optionalColumns) {
                if (column.getIndex() != -1) {
                    presetAndSpecified.add(column);
                }
            }
            ColumnInfo<T, Object>[] shouldRead = presetAndSpecified.toArray(new ColumnInfo[0]);
            int numLines = SafeUtils.readIntBE(decompressed, index);
            index += 4;

            for (int i = 0; i < numLines; i++) {
                header.setOffset(index);
                for (ColumnInfo<T, Object> column : shouldRead) {
                    column.setCachedValue(column.read(decompressed, index + column.getOffset()));
                }
                onEachRow.accept(header);
                index += rowBytes;
            }
        }
    }

    public Builder reader() {
        return new Builder();
    }
}
