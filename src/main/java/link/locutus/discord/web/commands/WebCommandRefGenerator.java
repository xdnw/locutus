package link.locutus.discord.web.commands;

import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.adapter.TsEndpointGenerator;

import java.io.IOException;

public final class WebCommandRefGenerator {
    private WebCommandRefGenerator() {
    }

    public static void main(String[] args) throws IOException {
        PageHandler handler = TsEndpointGenerator.createStandalonePageHandler();
        handler.getCommands().savePojo(null, WM.class, "WM");
    }
}
