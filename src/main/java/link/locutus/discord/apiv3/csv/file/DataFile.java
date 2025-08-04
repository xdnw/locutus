package link.locutus.discord.apiv3.csv.file;

import de.siegmar.fastcsv.reader.CloseableIterator;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.apiv3.csv.header.DataReader;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DataFile<T, H extends DataHeader<T>, R extends DataReader<H>> {
    private final File csvFile;
    private final File binFile;
    private final BiFunction<H, Long, R> createReader;
    private boolean csvExists = false;
    private boolean binExists = false;
    private final long date;
    private final long day;
    private final String filePart;
    private SoftReference<byte[]> cachedBytes = new SoftReference<>(null);

    private final Supplier<H> createHeader;
    private volatile H globalHeader;

    public File getCsvFile() {
        return csvFile;
    }

    public File getBinFile() {
        return binFile;
    }

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

    public synchronized byte[] getBytes() throws IOException {
        byte[] decompressed = cachedBytes.get();
        if (decompressed == null) {
            File binFile = getCompressedFile(true, false);
            decompressed = ArrayUtil.decompressLZ4(Files.readAllBytes(binFile.toPath()));
            cachedBytes = new SoftReference<>(decompressed);
        }
        return decompressed;
    }

    public static class Header<T> {
        public int numLines;
        public ColumnInfo<T, Object>[] headers;
        public int bytesPerRow;
        public int initialOffset;
    }

    public String getFilePart() {
        return filePart;
    }

    public DataFile(File file, long date, Supplier<H> createHeader, BiFunction<H, Long, R> createReader) {
        this.filePart = file.getName().split("\\.")[0];
        this.csvFile = new File(file.getParent(), filePart + ".csv");
        this.binFile = new File(file.getParent(), filePart + ".bin");
        this.csvExists = csvFile.exists();
        this.binExists = binFile.exists();
        this.createHeader = createHeader;
        this.date = date;
        this.day = TimeUtil.getDay(date);
        this.createReader = createReader;
    }

    public static long parseDateFromFile(String fileName) {
        String dateStr = fileName.replace("nations-", "").replace("cities-", "").replace(".csv", "").replace(".bin", "");
        return TimeUtil.parseDate(TimeUtil.YYYY_MM_DD_FORMAT, dateStr);
    }

    public long getDate() {
        return date;
    }

    public long getDay() {
        return day;
    }

    public H getGlobalHeader() {
        if (globalHeader == null) {
            synchronized (this) {
                if (globalHeader == null) {
                    globalHeader = createHeader.get();
                }
            }
        }
        return globalHeader;
    }

    private static void readAllCsv(File file, ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>> onEach) throws IOException {
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

        synchronized (this) {
            H parent = createHeader.get();
            Map<String, String> aliases = parent.getAliases();
            Map<String, ColumnInfo<T, Object>> headers = parent.createHeaders();

            readAllCsv(csvFile, (csvHeader, rows) -> {
                List<ColumnInfo<T, Object>> columnsInCsv = new ObjectArrayList<>();

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
                        columnsInCsv.add(column);
                    } else {
                        throw new IllegalArgumentException("Unknown header: `" + header + "` in " + csvFile);
                    }
                }

                List<ColumnInfo<T, Object>> writeOrder = new ArrayList<>(headers.values());
                {
                    Set<ColumnInfo<T, Object>> present = new ObjectLinkedOpenHashSet<>(columnsInCsv);
                    writeOrder.removeIf(f -> !present.contains(f));
                }

                FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
                try (DataOutputStream dos = new DataOutputStream(baos)) {
                    dos.writeInt(headers.size());
                    for (ColumnInfo<T, ?> header : headers.values()) {
                        dos.writeBoolean(header.getIndex() != -1);
                    }
                    List<List<Object>> all = new ObjectArrayList<>();
                    while (rows.hasNext()) {
                        CsvRow csvRow = rows.next();
                        ObjectArrayList<Object> rowData = new ObjectArrayList<>(columnsInCsv.size());
                        for (ColumnInfo<T, Object> column : columnsInCsv) {
                            String cell = csvRow.getField(column.getIndex());
                            Object value;
                            try {
                                value = column.read(cell);
                            } catch (RuntimeException e) {
                                System.err.println("Error reading column `" + column.getName() + "/" + column.getIndex() + "` in row " + (all.size() + 1) + ": " + cell + " in file: " + csvFile.getName());
                                throw e;
                            }
                            rowData.add(value);
                        }
                        all.add(rowData);
                    }
                    dos.writeInt(all.size());
                    for (List<Object> rowData : all) {
                        for (ColumnInfo<T, Object> column : writeOrder) {
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
            parent.getDictionary().save();
        }
        binExists = true;
        if (deleteCsv) {
            csvFile.deleteOnExit();
        }
        return binFile;
    }

    public class Builder {
        private final H header;
        private final Map<String, ColumnInfo<T, Object>> headers;

        public Builder() {
            this.header = createHeader.get();
            this.headers = header.createHeaders();
        }

        public H getHeader() {
            return header;
        }

        public Map<String, ColumnInfo<T, Object>> getHeaders() {
            return headers;
        }

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

        public void read(Consumer<R> onEachRow) throws IOException {
            synchronized (DataFile.this) {
                R reader = createReader.apply(header, date);
                byte[] decompressed = getBytes();
                Header<T> colInfo = header.readIndexes(decompressed);

                Set<ColumnInfo<T, Object>> presetAndSpecified = new ObjectLinkedOpenHashSet<>();
                for (ColumnInfo<T, Object> column : requiredColumns) {
                    if (column.getIndex() == -1) {
                        throw new IllegalArgumentException("Required column `" + column.getName() + "` is missing in " + filePart);
                    }
                    presetAndSpecified.add(column);
                }
                for (ColumnInfo<T, Object> column : optionalColumns) {
                    if (column.getIndex() != -1) {
                        presetAndSpecified.add(column);
                    }
                }
                ColumnInfo<T, Object>[] shouldRead = presetAndSpecified.toArray(new ColumnInfo[0]);
                Arrays.sort(shouldRead, Comparator.comparingInt(ColumnInfo::getIndex));


                int index = colInfo.initialOffset;
                int rowBytes = colInfo.bytesPerRow;
                int numLines = colInfo.numLines;

                for (int i = 0; i < numLines; i++) {
                    header.setOffset(index);
                    for (ColumnInfo<T, Object> column : shouldRead) {
                        column.setCachedValue(column.read(decompressed, index + column.getOffset()));
                    }
                    onEachRow.accept(reader);
                    index += rowBytes;
                }
            }
        }
    }

    public Builder reader() {
        return new Builder();
    }
}
