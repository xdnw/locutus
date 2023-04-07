package link.locutus.discord.commands.manager.v2.binding;

import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.util.StringMan;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Consumer;

public class BindingHelper {

    private final Queue<Consumer<ValueStore<?>>> tasks;

    public BindingHelper() {
        this.tasks = new ArrayDeque<>();
    }

    public void addBinding(Consumer<ValueStore<?>> task) {
        this.tasks.add(task);
    }

    public void register(ValueStore<Object> store) {
        for (Method method : getClass().getDeclaredMethods()) {
            register(method, store);
        }
        while (!tasks.isEmpty()) {
            tasks.poll().accept(store);
        }
    }

    private boolean register(Method method, ValueStore<Object> store) {
        // Check that it has the binding
        Binding binding = method.getAnnotation(Binding.class);
        if (binding == null) {
            return false;
        }
        String desc = binding.value();
        if (desc == null) desc = method.getName();

        Set<Type> types = new LinkedHashSet<>(Arrays.asList(binding.types()));
        types.add(method.getGenericReturnType());

        for (Type ret : types) {
            MethodParser parser = new MethodParser(this, method, desc, binding, ret);
            Key key = parser.getKey();
            store.addParser(key, parser);
        }
        return true;
    }

    public static <T extends Enum> Set<T> emumSet(Class<T> emum, String input) {
        return new HashSet<>(emumList(emum, input));
    }

    public static <T extends Enum> List<T> emumList(Class<T> emum, String input) {
        List<T> result = new ArrayList<>();
        for (String s : StringMan.split(input, ',')) {
            result.add(emum(emum, s));
        }
        return result;
    }
    public static <T extends Enum> T emum(Class<T> emum, String input) {
        input = input.replaceAll("_", " ").toLowerCase();
        Enum[] constants = emum.getEnumConstants();
        for (Enum constant : constants) {
            String name = constant.name().replaceAll("_", " ").toLowerCase();
            if (name.equals(input)) return (T) constant;
        }
        throw new IllegalArgumentException("Invalid category: `" + input + "`. Options: " + StringMan.getString(constants));
    }
}
