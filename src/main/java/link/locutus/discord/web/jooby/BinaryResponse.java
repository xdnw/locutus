package link.locutus.discord.web.jooby;

import java.util.Arrays;

public record BinaryResponse(byte[] bytes, String contentType) {
    public BinaryResponse {
        bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }
}