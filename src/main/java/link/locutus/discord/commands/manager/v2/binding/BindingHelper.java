package link.locutus.discord.commands.manager.v2.binding;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ReflectionUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Consumer;

public class BindingHelper {

    private final Queue<Consumer<ValueStore<?>>> tasks;

    public BindingHelper() {
        this.tasks = new ArrayDeque<>();
    }

    public static <T extends Enum> Set<T> emumSet(Class<T> emum, String input) {
        return new ObjectLinkedOpenHashSet<>(emumList(emum, input));
    }

    public static <T extends Enum> List<T> emumList(Class<T> emum, String input) {
        if (input.equalsIgnoreCase("*")) {
            return new ArrayList<>(Arrays.asList(emum.getEnumConstants()));
        }
        List<T> result = new ObjectArrayList<>();
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

    public void addBinding(Consumer<ValueStore<?>> task) {
        this.tasks.add(task);
    }

    public void register(ValueStore<Object> store) {
        Class<? extends BindingHelper> thisClass = getClass();
        Method[] methods = thisClass.getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() != thisClass) continue;
            register(method, store);
        }
        while (!tasks.isEmpty()) {
            tasks.poll().accept(store);
        }
    }

    private boolean register(Method method, ValueStore<Object> store) {
        Binding binding = method.getAnnotation(Binding.class);
        if (binding == null) {
            return false;
        }
        String[] examples = binding.examples();
        String desc = binding.value();
        if (desc == null) desc = method.getName();

        if (binding.multiple()) {
            try {
                Type type = ReflectionUtil.buildNestedType(binding.types());
                MethodParser parser = new MethodParser(this, method, desc, examples, type, binding.webType());
                Key key = parser.getKey();
                store.addParser(key, parser);
            } catch (IllegalArgumentException e) {
                System.err.println("Failed to register multiple binding for method " + method.getDeclaringClass().getSimpleName() +"#" + method.getName() + ": " + e.getMessage());
                throw e;
            }
        } else {
            Class<?>[] typesArr = binding.types();
            Set<Type> types = new ObjectLinkedOpenHashSet<>(Math.max(1, typesArr.length));
            for (Class<?> type : typesArr) types.add(type);
            if (types.isEmpty()) {
                types.add(method.getGenericReturnType());
            }
            for (Type ret : types) {
                MethodParser parser = new MethodParser(this, method, desc, examples, ret, binding.webType());
                Key key = parser.getKey();
                store.addParser(key, parser);
            }
        }
        return true;
    }
}
