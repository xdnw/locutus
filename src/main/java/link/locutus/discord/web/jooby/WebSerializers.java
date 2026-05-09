package link.locutus.discord.web.jooby;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

final class WebSerializers {
    static final ObjectMapper MSGPACK = new ObjectMapper(new MessagePackFactory());

    private WebSerializers() {
    }
}