package com.boydti.discord.web.test;

import com.boydti.discord.commands.manager.v2.binding.SimpleValueStore;
import com.boydti.discord.commands.manager.v2.binding.ValueStore;
import com.boydti.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import com.boydti.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
import com.boydti.discord.commands.manager.v2.binding.validator.ValidatorStore;
import com.boydti.discord.commands.manager.v2.command.CommandGroup;
import com.boydti.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import com.boydti.discord.commands.manager.v2.perm.PermissionHandler;
import com.boydti.discord.web.commands.binding.WebPrimitiveBinding;

public class Test2 {
    private final CommandGroup commands;
    private final ValueStore<Object> store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;

    public Test2() throws NoSuchFieldException {
        this.store = new SimpleValueStore<>();
        new WebPrimitiveBinding().register(store);
        new PrimitiveBindings().register(store);

        this.validators = new ValidatorStore();
        new PrimitiveValidators().register(validators);

        this.permisser = new PermissionHandler();
        new PermissionBinding().register(permisser);

        this.commands = CommandGroup.createRoot(store, validators);

        this.commands.registerCommands(new TestCommands());
    }
}
