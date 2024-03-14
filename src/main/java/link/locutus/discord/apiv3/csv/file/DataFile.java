package link.locutus.discord.apiv3.csv.file;

import de.siegmar.fastcsv.reader.CloseableIterator;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import org.anarres.parallelgzip.ParallelGZIPInputStream;
import org.anarres.parallelgzip.ParallelGZIPOutputStream;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class DataFile<T, H extends DataHeader<T>> {
    private final File csvFile;
    private final File binFile;
    private boolean csvExists = false;
    private boolean binExists = false;
    private final H header;
    private final Map<String, ColumnInfo<T, Object>> headers;
    private final long date;
    private final long day;
    private final String filePart;

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

    public DataFile(File file, H unloaded) {
        this.filePart = file.getName().split("\\.")[0];
        this.csvFile = new File(file.getParent(), filePart + ".csv");
        this.binFile = new File(file.getParent(), filePart + ".bin");
        this.csvExists = csvFile.exists();
        this.binExists = binFile.exists();
        this.header = unloaded;
        this.headers = unloaded.getHeaders();
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
                if (column != null) {
                    column.setIndex(i);
                    columns.add(column);
                } else {
                    throw new IllegalArgumentException("Unknown header: `" + header + "` in " + csvFile);
                }
            }
            try (DataOutputStream dos = new DataOutputStream(new ParallelGZIPOutputStream(new FileOutputStream(binFile)))) {
                dos.writeInt(headers.size());
                for (ColumnInfo<T, ?> header : headers.values()) {
                    dos.writeBoolean(header.getIndex() != -1);
                }
                while (row.hasNext()) {
                    CsvRow csvRow = row.next();
                    for (ColumnInfo<T, Object> column : columns) {
                        String cell = csvRow.getField(column.getIndex());
                        Object value = column.read(cell);
                        column.write(dos, value);
                    }
                }
            }
        });
        binExists = true;
        if (deleteCsv) {
            csvFile.deleteOnExit();
        }
        return binFile;
    }

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
            if (requiredColumns.isEmpty() && optionalColumns.isEmpty()) {
                throw new IllegalArgumentException("No columns specified");
            }
            File binFile = getCompressedFile(true, false);
            List<ColumnInfo<T, Object>> presentColumns = new ObjectArrayList<>();
            List<ColumnInfo<T, Object>> allColumns = new ObjectArrayList<>(headers.values());
            for (ColumnInfo<T, Object> col : allColumns) {
                col.setIndex(-1);
                col.setCachedValue(null);
            }
            header.clear();
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new ParallelGZIPInputStream(new FileInputStream(binFile), 8192), 8192))) {
                int size = dis.readInt();
                for (int i = 0; i < size; i++) {
                    boolean hasIndex = dis.readBoolean();
                    if (hasIndex) {
                        ColumnInfo<T, Object> column = allColumns.get(i);
                        column.setIndex(i);
                        presentColumns.add(column);
                    }
                }
                for (ColumnInfo<T, Object> column : requiredColumns) {
                    if (column.getIndex() == -1) {
                        throw new IllegalArgumentException("Required column `" + column + "` is missing in " + filePart);
                    }
                }
                Consumer<DataInputStream> readRow = getDataInputStreamConsumer(presentColumns);
//                int linesRead = 0;
                try {
                    while (dis.available() > 0) {
                        readRow.accept(dis);
                        onEachRow.accept(header);
//                        linesRead++;
                    }
                } catch (RuntimeException e) {
                    Throwable root = e;
                    while (e.getCause() != null && e.getCause() != e) {
                        root = e.getCause();
                    }
                    if (root instanceof EOFException) {
//                        System.out.println("Read file " + binFile.getName() + " with " + presentColumns.size() + " columns" + " and " + linesRead + " lines");
                    } else {
                        throw e;
                    }
                }
            }
        }

        @Nullable
        private Consumer<DataInputStream> getDataInputStreamConsumer(List<ColumnInfo<T, Object>> presentColumns) {
            Consumer<DataInputStream> onEach = null;
            for (ColumnInfo<T, Object> column : presentColumns) {
                boolean shouldRead = requiredColumns.contains(column) || optionalColumns.contains(column);
                Consumer<DataInputStream> reader;
                if (shouldRead) {
                    reader = (ThrowingConsumer<DataInputStream>) dis1 -> {
                        Object value = column.read(dis1);
                        column.setCachedValue(value);
                    };
                } else {
                    reader = (ThrowingConsumer<DataInputStream>) column::skip;
                }
                if (onEach == null) {
                    onEach = reader;
                } else {
                    onEach = onEach.andThen(reader);
                }
            }
            return onEach;
        }
    }

    public Builder reader() {
        return new Builder();
    }
}
