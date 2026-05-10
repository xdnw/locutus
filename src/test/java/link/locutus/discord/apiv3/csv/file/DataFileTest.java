package link.locutus.discord.apiv3.csv.file;

import link.locutus.discord.apiv3.csv.column.IntColumn;
import link.locutus.discord.apiv3.csv.column.StringColumn;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.apiv3.csv.header.DataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataFileTest {
    @TempDir
    Path tempDir;

    @Test
    void ignoredCsvColumnsDoNotBreakDenseBinaryWriteOrder() throws IOException {
        Path csvPath = tempDir.resolve("test-2026-05-10.csv");
        Files.writeString(csvPath, "id,ignored,name\n1,skip,Alpha\n2,skip,Bravo\n");

        Dictionary dictionary = new Dictionary(tempDir.toFile());
        TestDataFile dataFile = new TestDataFile(csvPath.toFile(), dictionary);
        List<String> rows = new ArrayList<>();

        dataFile.reader()
                .required(header -> List.of(header.id, header.name))
                .read(reader -> rows.add(reader.readRow()));

        assertEquals(List.of("1:Alpha", "2:Bravo"), rows);
    }

    public static final class TestRow {
        private int id;
        private String name;
    }

    public static final class TestHeader extends DataHeader<TestRow> {
        public final IntColumn<TestRow> id = new IntColumn<>(this, (row, value) -> row.id = value);
        public final StringColumn<TestRow> name = new StringColumn<>(this, (row, value) -> row.name = value);

        private TestHeader(Dictionary dictionary) {
            super(dictionary);
        }

        @Override
        public boolean isIgnoredColumn(String columnName) {
            return "ignored".equals(columnName);
        }
    }

    public static final class TestReader extends DataReader<TestHeader> {
        private TestReader(TestHeader header, long date) {
            super(header, date);
        }

        private String readRow() {
            return header.id.get() + ":" + header.name.get();
        }

        @Override
        public void clear() {
        }
    }

    public static final class TestDataFile extends DataFile<TestRow, TestHeader, TestReader> {
        private TestDataFile(java.io.File file, Dictionary dictionary) {
            super(file, 0L, () -> new TestHeader(dictionary), TestReader::new);
        }
    }
}