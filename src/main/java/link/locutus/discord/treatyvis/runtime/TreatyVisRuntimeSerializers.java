package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

final class TreatyVisRuntimeSerializers {
    static final ObjectMapper JSON = new ObjectMapper();
    static final ObjectMapper MSGPACK = new ObjectMapper(new MessagePackFactory());

    private TreatyVisRuntimeSerializers() {
    }
}