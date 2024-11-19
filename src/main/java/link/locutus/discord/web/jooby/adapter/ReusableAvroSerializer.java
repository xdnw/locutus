package link.locutus.discord.web.jooby.adapter;

import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ReusableAvroSerializer {

    private final Schema schema;
    private final DatumWriter<GenericRecord> datumWriter;
    private final ThreadLocal<FastByteArrayOutputStream> outputStream;
    private final ThreadLocal<BinaryEncoder> encoder;

    public ReusableAvroSerializer(Schema schema) {
        this.schema = schema;
        this.datumWriter = new GenericDatumWriter<>(schema);
        this.outputStream = ThreadLocal.withInitial(FastByteArrayOutputStream::new);
        this.encoder = ThreadLocal.withInitial(() -> EncoderFactory.get().binaryEncoder(outputStream.get(), null));
    }

    public byte[] serialize(GenericRecord record) throws IOException {
        FastByteArrayOutputStream outStream = outputStream.get();
        BinaryEncoder enc = encoder.get();
        datumWriter.write(record, enc);
        enc.flush();
        outStream.trim();
        byte[] serializedData = outStream.array;
        outStream.reset();
        return serializedData;
    }
}