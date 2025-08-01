package link.locutus.discord.commands.manager.v2.binding.bindings;

import java.util.Arrays;
import java.util.Objects;

public class MethodIdentity {
    private final MethodEnum method;
    private final Object[] args;

    private MethodIdentity(MethodEnum method, Object[] args) {
        this.method = method;
        this.args = args;
    }

    private static final Object[] EMPTY_ARGS = new Object[0];

    public static MethodIdentity of(MethodEnum method, Object... objects) {
        if (objects.length == 0) {
            return new MethodIdentity(method, EMPTY_ARGS);
        }
        Object[] processedArgs = new Object[objects.length];
        for (int i = 0; i < objects.length; i++) {
            Object obj = objects[i];
            if (obj == null ||
                    obj instanceof String ||
                    obj instanceof Number ||
                    obj instanceof Boolean ||
                    obj.getClass().isPrimitive() ||
                    obj.getClass().isEnum()) {
                processedArgs[i] = obj;
            } else {
                processedArgs[i] = obj.hashCode();
            }
        }
        return new MethodIdentity(method, processedArgs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodIdentity that)) return false;
        return Objects.equals(method, that.method) &&
                Arrays.equals(args, that.args);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, args);
    }
}
