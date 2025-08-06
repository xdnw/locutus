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
import org.jgrapht.alg.util.Pair;

import java.io.DataOutputStream;
import java.io.File;
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

    public synchronized byte[] getBytes() throws IOException {
        byte[] decompressed = cachedBytes.get();
        if (decompressed == null) {
            Pair<File, byte[]> binFilePair = getCompressedFile(true, false);
            decompressed = binFilePair.getSecond();
            if (decompressed == null) {
                decompressed = ArrayUtil.decompressLZ4(Files.readAllBytes(binFile.toPath()));
            }
            cachedBytes = new SoftReference<>(decompressed);
        }
        return decompressed;
    }

    public Pair<File, byte[]> getCompressedFile(boolean create, boolean deleteCsv) throws IOException {
        if (binExists) {
            return Pair.of(binFile, null);
        }
        if (binFile.exists()) {
            binExists = true;
            if (deleteCsv) {
                csvFile.deleteOnExit();
            }
            return Pair.of(binFile, null);
        }
        if (!create) return null;
        if (!csvExists) {
            throw new IllegalArgumentException("CSV file does not exist: " + csvFile);
        }

        synchronized (this) {
            if (binExists) {
                return Pair.of(binFile, null);
            }
            H parent = createHeader.get();
            Map<String, String> aliases = parent.getAliases();
            Map<String, ColumnInfo<T, Object>> headers = parent.createHeaders();

            byte[][] output = new byte[1][];
            readAllCsv(csvFile, (csvHeader, rows) -> {
                List<ColumnInfo<T, Object>> columnsInCsv = new ObjectArrayList<>();

                int numWithIndex = 0;
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
                        numWithIndex++;
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

                {
                    int expected = columnsInCsv.size();
                    if (numWithIndex != expected) {
                        throw new IllegalStateException("Expected " + expected + " columns with index, but found " + numWithIndex + " in file: " + csvFile.getName());
                    }
                }

                int numRows = 0;
                FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
                try (DataOutputStream dos = new DataOutputStream(baos)) {
                    dos.writeInt(headers.size());
                    for (ColumnInfo<T, ?> header : headers.values()) {
                        dos.writeBoolean(header.getIndex() != -1);
                    }
                    List<List<Object>> all = new ObjectArrayList<>();
                    while (rows.hasNext()) {
                        numRows++;
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
                baos.close(); // Unnecessary since it doesn't implement any close logic, but good practice

                baos.trim();
                {
                    if (baos.array.length != baos.length) {
                        throw new IllegalStateException("Byte array length mismatch in file: " + csvFile.getName() + " (expected: " + baos.length + ", actual: " + baos.array.length + ")");
                    }
                    int headerSize = 4 + headers.size() + 4;
                    int rowSize = columnsInCsv.stream().map(ColumnInfo::getBytes).reduce(0, Integer::sum);
                    int expectedSize = headerSize + (numRows * rowSize);
                    int remainder = (baos.length - expectedSize) % rowSize;
                    if (baos.length != expectedSize) {
                        throw new IllegalStateException("Expected " + expectedSize + " bytes, but got " + baos.length + " in file: " + csvFile.getName() + " (diff: " + (expectedSize - baos.length) + ")");
                    }
                    {
                        System.out.println("Created binary file: " + binFile.getName() + " remainder: " + remainder + " bytes per row: " +  + rowSize + " | columns: " + columnsInCsv.size() + "/" + headers.size() + " | uncompressed size: " + baos.array.length);
                    }
                }
                byte[] compressed = ArrayUtil.compressLZ4(baos.array, baos.length);
                Files.write(binFile.toPath(), compressed);
                output[0] = baos.array;
            });
            parent.getDictionary().save();
            binExists = true;
            if (deleteCsv) {
                csvFile.deleteOnExit();
            }
            return Pair.of(binFile, output[0]);
        }
    }

    public class Builder {
        private final H header;
        private final Map<String, ColumnInfo<T, Object>> headers;
        private final List<ColumnInfo<T, Object>> requiredColumns = new ObjectArrayList<>();
        private final List<ColumnInfo<T, Object>> optionalColumns = new ObjectArrayList<>();

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

        public Builder all(boolean includeNonData) {
            synchronized (headers) {
                optionalColumns.addAll(headers.values().stream().filter(f -> includeNonData || !f.isAlwaysSkip()).toList());
            }
            return this;
        }

        public Builder required(Function<H, List<ColumnInfo>> columns) {
            synchronized (headers) {
                columns.apply(header).forEach(requiredColumns::add);
            }
            return this;
        }

        public Builder optional(Function<H, List<ColumnInfo>> columns) {
            synchronized (headers) {
                columns.apply(header).forEach(optionalColumns::add);
            }
            return this;
        }

        public Builder required(ColumnInfo... columns) {
            for (ColumnInfo<T, Object> column : columns) {
                requiredColumns.add(column);
            }
            return this;
        }

        public Builder required(String... names) {
            synchronized (headers) {
                for (String name : names) {
                    ColumnInfo<T, Object> column = headers.get(name);
                    if (column == null) {
                        throw new IllegalArgumentException("Unknown header: `" + name + "` in " + filePart);
                    }
                    requiredColumns.add(column);
                }
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
            try {
                synchronized (DataFile.this) {
                    R reader = createReader.apply(header, date);
                    byte[] decompressed = getBytes();
                    Header<T> colInfo = header.readIndexes(decompressed);
                    int bytesOffset = colInfo.initialOffset; // initial offset + header size + number of rows
                    int remainder = (decompressed.length - bytesOffset) % colInfo.bytesPerRow;
                    if (remainder != 0) {
                        throw new IllegalStateException("Data file " + filePart + " has a remainder of " + remainder + " bytes, expected multiple of " + colInfo.bytesPerRow + " | Num columns " + colInfo.headers.length + " | Bytes per row " + colInfo.bytesPerRow + " | Total bytes " + decompressed.length);
                    } else {
                        // print same info but no error
//                        System.out.println("Success: Data file " + filePart + " has no remainder, expected multiple of " + colInfo.bytesPerRow + " | Num columns " + colInfo.headers.length + " | Bytes per row " + colInfo.bytesPerRow + " | Total bytes " + decompressed.length);
                    }

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
            } catch (IllegalStateException e) {
                throw new RuntimeException(filePart + ": " + e.getMessage(), e);
            }
        }
    }

    public Builder reader() {
        return new Builder();
    }
}
