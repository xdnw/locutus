package link.locutus.discord.web.commands.api;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.treatyvis.runtime.TreatyHistoryService;
import link.locutus.discord.treatyvis.runtime.TreatyVisRuntimePayload;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.jooby.BinaryResponse;

import java.io.IOException;

public class TreatyVisRuntimeEndpoints {
    private final TreatyHistoryService treatyHistoryService;

    public TreatyVisRuntimeEndpoints() {
        this(new TreatyHistoryService());
    }

    TreatyVisRuntimeEndpoints(TreatyHistoryService treatyHistoryService) {
        this.treatyHistoryService = treatyHistoryService;
    }

    @Command(desc = "Build the treaty history runtime payload from current locutus3 state", viewable = true)
    @ReturnType(TreatyVisRuntimePayload.class)
    public TreatyVisRuntimePayload treaty_history() throws IOException {
        return treatyHistoryService.buildPayload();
    }

    @Command(desc = "Build the treaty atlas WebP from current locutus3 state", viewable = true)
    @ReturnType(byte[].class)
    public BinaryResponse treaty_atlas() throws IOException {
        return new BinaryResponse(treatyHistoryService.buildAtlasWebp(), "image/webp");
    }
}
