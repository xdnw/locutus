package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
import link.locutus.discord.commands.manager.v2.binding.bindings.StaticPlaceholders;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.GPTBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NewsletterBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.SheetBindings;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.scheduler.ThrowingTriFunction;
import link.locutus.discord.util.sheet.SpreadSheet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static link.locutus.discord.commands.manager.v2.binding.BindingHelper.emumSet;

public class Test {

    private final ValidatorStore validators;
    private final PermissionHandler permisser;

    public static void main(String[] args) {
        Test test = new Test();
        StaticPlaceholders<Continent> ph = test.create();
        ph.register(test.store);
        ph.init();


        Set<Continent> filter = ph.parseSet(test.store, "*,{ordinal}={ordinal}");
        System.out.println(filter);
    }

    private final SimpleValueStore<Object> store;


    public Test() {
        this.store = new SimpleValueStore<>();
        new PrimitiveBindings().register(store);
        new DiscordBindings().register(store);
        new PWBindings().register(store);
        new GPTBindings().register(store);
        new SheetBindings().register(store);
//        new StockBinding().register(store);
        new NewsletterBindings().register(store);

        this.validators = new ValidatorStore();
        new PrimitiveValidators().register(validators);

        this.permisser = new PermissionHandler();
        new PermissionBinding().register(permisser);
    }

    public StaticPlaceholders<Continent> create() {
        return new StaticPlaceholders<Continent>(Continent.class, store, validators, permisser,
                "TODO CM REF",
                (ThrowingTriFunction<Placeholders<Continent>, ValueStore, String, Set<Continent>>) (inst, store, input) -> {
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(Continent.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("continent"), true, (type, str) -> PWBindings.continent(str));
                    }
                    return emumSet(Continent.class, input);
                });
    }
}
