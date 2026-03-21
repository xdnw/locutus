package link.locutus.discord.commands.manager.v2.binding.bindings;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeCommandContext;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeLookupContext;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeLookupService;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationSnapshotService;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeServices;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeStoreBindings;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class PlaceholderEngine implements PlaceholderRegistry {
    private final ValueStore store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final CommandRuntimeServices services;
    private final Map<Class<?>, Placeholders<?, ?>> placeholders = new LinkedHashMap<>();

    public PlaceholderEngine(ValueStore store, ValidatorStore validators, PermissionHandler permisser,
            CommandRuntimeServices services) {
        this.store = store;
        this.validators = validators;
        this.permisser = permisser;
        this.services = services;
        CommandRuntimeStoreBindings.register(store, services);
    }

    public ValueStore getStore() {
        return store;
    }

    public ValidatorStore getValidators() {
        return validators;
    }

    public PermissionHandler getPermisser() {
        return permisser;
    }

    public CommandRuntimeServices getRuntimeServices() {
        return services;
    }

    public PlaceholderEngine add(Placeholders<?, ?> placeholders) {
        this.placeholders.put(placeholders.getType(), placeholders);
        return this;
    }

    public PlaceholderEngine initParsing() {
        for (Placeholders<?, ?> placeholders : this.placeholders.values()) {
            placeholders.register(store);
        }
        store.addLazyProvider(Key.of(PlaceholderRegistry.class), () -> this);
        return this;
    }

    public PlaceholderEngine initCommands() {
        initParsing();
        return this;
    }

    public LocalValueStore createLocals() {
        LocalValueStore locals = new LocalValueStore(store);
        locals.addProvider(Key.of(PermissionHandler.class), permisser);
        locals.addProvider(Key.of(ValidatorStore.class), validators);
        return locals;
    }

    protected Collection<Placeholders<?, ?>> getAllPlaceholders() {
        return Collections.unmodifiableCollection(placeholders.values());
    }

    @Override
    public Set<Class<?>> getTypes() {
        return placeholders.keySet();
    }

    @Override
    public <T, M> Placeholders<T, M> get(Class<T> type) {
        return (Placeholders<T, M>) placeholders.get(type);
    }
}