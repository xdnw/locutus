package link.locutus.discord.network;

import org.jsoup.Connection;

import java.io.IOException;

public interface IProxy {
    public Connection connect(String url) throws IOException;

    default boolean isPassthrough() {
        return false;
    }
}
