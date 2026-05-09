package link.locutus.discord.web.commands.api;

import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.treatyvis.runtime.TreatyVisRuntimePayload;
import link.locutus.discord.web.jooby.BinaryResponse;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.adapter.TsEndpointGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TreatyVisRuntimeEndpointsContractTest {
    private PageHandler handler;
    private Method wrapMethod;

    @BeforeEach
    void setUp() throws Exception {
        handler = TsEndpointGenerator.createStandalonePageHandler();
        wrapMethod = PageHandler.class.getDeclaredMethod("wrap", WebStore.class, Object.class, io.javalin.http.Context.class, boolean.class);
        wrapMethod.setAccessible(true);
    }

    @Test
    void treatyVisRuntimeEndpointIsRegisteredInStandalonePageHandler() {
        CommandGroup api = (CommandGroup) handler.getCommands().get("api");

        CommandCallable runtimePayload = api.get("treaty_history");
        CommandCallable runtimeAtlas = api.get("treaty_atlas");

        assertNotNull(runtimePayload);
        assertNotNull(runtimeAtlas);
    }

    @Test
    void standalonePageHandlerSerializesTreatyRuntimePayloadAsMessagePack() throws Exception {
        TreatyVisRuntimePayload payload = new TreatyVisRuntimePayload(
                1,
                20_089,
                100,
                new TreatyVisRuntimePayload.Alliances(List.of(881), List.of("Guardian")),
                List.of("mdp"),
                new TreatyVisRuntimePayload.Edges(List.of(0), List.of(0), List.of(0)),
                new TreatyVisRuntimePayload.InitialState(List.of(0), List.of(), List.of(), List.of(0), List.of(182_340)),
                new TreatyVisRuntimePayload.TreatyChanges(List.of(), List.of(0), List.of(), List.of()),
                new TreatyVisRuntimePayload.FlagChanges(List.of(), List.of(0), List.of(), List.of()),
                new TreatyVisRuntimePayload.ScoreSnapshots(List.of(), List.of(0), List.of(), List.of())
        );

        byte[] serialized = (byte[]) wrapMethod.invoke(handler, null, payload, null, true);
        TreatyVisRuntimePayload decoded = handler.getSerializer().readValue(serialized, TreatyVisRuntimePayload.class);

        assertEquals(payload, decoded);
    }

    @Test
    void standalonePageHandlerPreservesBinaryResponseContent() throws Exception {
        byte[] atlasBytes = new byte[] {1, 2, 3, 4};
        BinaryResponse response = new BinaryResponse(atlasBytes, "image/webp");

        BinaryResponse wrapped = (BinaryResponse) wrapMethod.invoke(handler, null, response, null, true);

        assertEquals("image/webp", wrapped.contentType());
        assertArrayEquals(atlasBytes, wrapped.bytes());
    }
}
