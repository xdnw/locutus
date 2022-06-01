package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArgumentStack {
    private final List<String> args;
    private final ValueStore store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private int index;

    public ArgumentStack(List<String> args, ValueStore<?> store, ValidatorStore validators, PermissionHandler permisser) {
        this.args = args;
        this.store = store;
        this.validators = validators;
        this.permisser = permisser;
    }

    public List<String> getRemainingArgs() {
        return args.subList(index, args.size());
    }

    public List<String> getArgs() {
        return args;
    }

    public void add(String... args) {
        this.args.addAll(Arrays.asList(args));
    }

    public ValidatorStore getValidators() {
        return validators;
    }

    public int getSize() {
        return args.size();
    }

    public int remaining() {
        return getSize() - index;
    }

    public int index() {
        return index;
    }

    public String consumeNext() {
        return args.get(index++);
    }

    public String getCurrent() {
        return args.get(index);
    }

    public String getLast() {
        return index > 0 ? args.get(index - 1) : null;
    }

    public ArgumentStack createScoped() {
        return createScoped(index, args.size());
    }

    public ArgumentStack createScoped(int start, int end) {
        return new ArgumentStack(args.subList(start, end), store, validators, permisser);
    }

    public <T> T consume(Key key) {
        if (key == null || (key.getType() == String.class && key.getAnnotations().length == 0)) return (T) consumeNext();
        Parser parser = store.get(key);
        if (parser == null) {
            throw new IllegalStateException("No binding found for: " + key.toString());
        }
        return (T) parser.apply(this);
    }

    public ValueStore getStore() {
        return store;
    }

    public Map<Character, String> consumeFlags(Set<Character> booleanFlags, Set<Character> valueFlags) {
        Map<Character, String> map = new LinkedHashMap<>();
        for (int i = args.size() - 1; i >= 0; i--) {
            String arg = args.get(i);
            if (arg.matches("^-[a-zA-Z?]+$")) {
                char flagChar = arg.charAt(1);
                if (booleanFlags.contains(flagChar)) {
                    args.remove(i);
                    map.put(flagChar, "true");
                } else if (valueFlags.contains(flagChar)) {
                    args.remove(i);
                    String value = args.remove(i);
                    map.put(flagChar, value);
                }
            } else if (arg.matches("^-[a-zA-Z?][ ].*$")) {
                char flagChar = arg.charAt(1);
                String[] split = arg.split(" ", 2);
                if (split.length == 2) {
                    String value = split[1];
                    if (valueFlags.contains(flagChar) || booleanFlags.contains(flagChar)) {
                        map.put(flagChar, value);
                        args.remove(i);
                    }
                }
            }
        }
        return map;
    }

    public boolean hasNext() {
        return remaining() > 0;
    }

    public PermissionHandler getPermissionHandler() {
        return permisser;
    }
}
