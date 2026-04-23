package link.locutus.discord.web.commands.api;

import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.adapter.TsEndpointGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SimEndpointsContractTest {
    @Test
    void simEndpointsAreRegisteredInStandalonePageHandler() {
        PageHandler handler = TsEndpointGenerator.createStandalonePageHandler();
        CommandGroup api = (CommandGroup) handler.getCommands().get("api");

        CommandCallable simBlitz = api.get("simBlitz");
        CommandCallable simAdhoc = api.get("simAdhoc");
        CommandCallable simSchedule = api.get("simSchedule");

        assertNotNull(simBlitz);
        assertNotNull(simAdhoc);
        assertNotNull(simSchedule);
    }
}