package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.db.AllianceLookup;
import link.locutus.discord.db.bank.TaxBracketLookup;

/**
 * Shared composition-root wiring for runtime services exposed through a value store.
 */
public final class CommandRuntimeStoreBindings {
    private CommandRuntimeStoreBindings() {
    }

    public static void register(ValueStore store, CommandRuntimeLookupContext lookupContext) {
        CommandRuntimeCommandContext commandContext = lookupContext instanceof CommandRuntimeCommandContext typed
                ? typed
                : null;
        register(store, lookupContext, commandContext);
    }

    public static void register(ValueStore store, CommandRuntimeLookupContext lookupContext,
            CommandRuntimeCommandContext commandContext) {
        if (lookupContext instanceof CommandRuntimeServices services) {
            store.addLazyProvider(Key.of(CommandRuntimeServices.class), () -> services);
            store.addLazyProvider(Key.of(AllianceLookup.class), services::allianceLookup);
        }
        store.addLazyProvider(Key.of(TaxBracketLookup.class), lookupContext::taxBracketLookup);
        store.addLazyProvider(Key.of(CommandRuntimeLookupContext.class), () -> lookupContext);
        store.addLazyProvider(Key.of(CommandRuntimeLookupService.class), lookupContext::lookup);
        store.addLazyProvider(Key.of(NationSnapshotService.class), lookupContext::nationSnapshots);
        if (commandContext != null) {
            store.addLazyProvider(Key.of(CommandRuntimeCommandContext.class), () -> commandContext);
        }
    }
}