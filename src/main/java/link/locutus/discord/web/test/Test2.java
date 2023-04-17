package link.locutus.discord.web.test;

import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.commands.binding.WebPWBindings;

public class Test2 {
    private final CommandGroup commands;
    private final ValueStore<Object> store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;

    public Test2() throws NoSuchFieldException {
        this.store = new SimpleValueStore<>();
        new WebPWBindings().register(store);
        new PrimitiveBindings().register(store);

        new AuthBindings().register(store);

        this.validators = new ValidatorStore();
        new PrimitiveValidators().register(validators);

        this.permisser = new PermissionHandler();
        new PermissionBinding().register(permisser);

        this.commands = CommandGroup.createRoot(store, validators);

        this.commands.registerCommands(new TestCommands());
    }
}
