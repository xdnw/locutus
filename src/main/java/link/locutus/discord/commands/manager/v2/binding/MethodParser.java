package link.locutus.discord.commands.manager.v2.binding;

import com.google.gson.internal.Primitives;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ReflectionUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MethodParser<T> implements Parser<T> {
    private final String desc;
    private final Key<T> key;
    private final Method method;
    private final List<Key> params;
    private final List<Class> primaryClass;
    private final Object object;

    private boolean isConsumerInit;
    private boolean isConsumer;

    public MethodParser(Object object, Method method, String desc) {
        this(object, method, desc, null, null);
    }

    public MethodParser(Object object, Method method, String desc, Binding binding, Type ret) {
        this.object = object;
        this.method = method;
        Annotation[] annotations = method.getAnnotations();
        if (ret == null) ret = method.getGenericReturnType();

        if (annotations.length == 1) {
            key = Key.of(binding, ret);
        } else if (annotations.length == 2) {
            Annotation annotation = annotations[0] == binding ? annotations[1] : annotations[0];
            key = Key.of(binding, ret, annotation);
        } else {
            key = Key.of(binding, ret, annotations);
//            throw new IllegalArgumentException("Cannot annotate " + method + " with " + StringMan.getString(annotations));
        }

        // Get the provided parameters
        Type[] paramTypes = method.getGenericParameterTypes();
        Annotation[][] paramAnns = method.getParameterAnnotations();

        this.params = new ArrayList<>();

        this.isConsumer = false;
        for (int i = 0; i < paramTypes.length; i++) {
            Type param = paramTypes[i];
            if (param != String.class) {
                Annotation[] paramAnn = paramAnns[i];
                Key paramKey;
                if (paramAnn.length == 1) {
                    paramKey = Key.of(binding, param, paramAnn[0]);
                } else if (paramAnn.length == 0) {
                    paramKey = Key.of(binding, param);
                } else {
                    paramKey = Key.of(binding, param, paramAnn);
                }
                this.params.add(paramKey);
            } else {
                params.add(null);
                isConsumer = true;
            }
        }

        this.primaryClass = params.stream().map(f -> f == null ? null : ReflectionUtil.getClassType(f.getType())).collect(Collectors.toList());

        this.isConsumerInit = isConsumer;
        this.desc = desc == null && binding != null ? binding.value() : desc;
    }

    @Override
    public String toString() {
        return "MethodParser{" +
                "desc='" + desc + '\'' +
                ", key=" + key +
                ", method=" + method +
                ", params=" + params +
                ", object=" + object +
                ", isConsumerInit=" + isConsumerInit +
                ", isConsumer=" + isConsumer +
                '}';
    }

    public Method getMethod() {
        return method;
    }

    public T apply(ValueStore store, Object t) {
        try {

            Object[] args = new Object[params.size()];
            for (int i = 0; i < params.size(); i++) {
                Key paramKey = params.get(i);
                Class expectedType = primaryClass.get(i);
                Object arg;
                try {
                    if (t != null && paramKey != null && paramKey.getType() != String.class && expectedType != null && (expectedType == t.getClass() || expectedType.isAssignableFrom(t.getClass()))) {
                        arg = t;
                    } else if (paramKey == null || (paramKey.getType() == String.class && paramKey.getAnnotations().length == 0)) {
                        arg = t;
                    } else if (paramKey.getAnnotations().length == 0 && paramKey.getType() == Method.class) {
                        arg = method;
                    } else if (paramKey.getAnnotations().length == 0 && key.getAnnotationTypes().contains(paramKey.getType())) {
                        Parser parser = store.get(paramKey);
                        if (parser != null) {
                            arg = parser.apply(store, t);
                        } else {
                            arg = key.getAnnotation(paramKey.getType());
                        }
                    } else {
                        Type type = paramKey.getType();
                        Class clazz = ReflectionUtil.getClassType(type);
                        if (clazz.isPrimitive()) clazz = Primitives.wrap(clazz);
                        if (t != null && clazz == t.getClass() && paramKey.getAnnotations().length == 0) {
                            arg = t;
                        } else {
                            Parser parser = store.get(paramKey);
                            if (parser == null) {
                                if (paramKey.isDefault()) {
                                    continue;
                                }
                                throw new IllegalArgumentException("No parser found for " + paramKey);
                            }
                            arg = parser.apply(store, t);
                        }
                    }
                } catch (IllegalStateException e) {
                    if (!paramKey.isDefault()) {
                        throw e;
                    }
                    arg = null;
                }
                args[i] = arg;
            }
            return (T) method.invoke(object, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            if (e.getCause() != null && e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public T apply(ArgumentStack stack) {
        try {
            Object[] args = new Object[params.size()];
            for (int i = 0; i < params.size(); i++) {
                Key paramKey = params.get(i);
                Object arg;
                try {
                    if (paramKey == null || paramKey.getType() == String.class && paramKey.getAnnotations().length == 0) {
                        arg = stack.consumeNext();
                    } else if (paramKey.getAnnotations().length == 0 && paramKey.getType() == Method.class) {
                        arg = method;
                    } else if (paramKey.getAnnotations().length == 0 && key.getAnnotationTypes().contains(paramKey.getType())) {
                        Parser parser = stack.getStore().get(paramKey);
                        if (parser != null) {
                            arg = parser.apply(stack);
                        } else {
                            arg = key.getAnnotation(paramKey.getType());
                        }
                    } else {
                        arg = stack.consume(paramKey);
                    }
                } catch (IllegalStateException e) {
                    if (paramKey == null || !paramKey.isDefault()) {
                        throw e;
                    }
                    arg = null;
                }
                args[i] = arg;
            }
            return (T) method.invoke(object, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            if (e.getCause() != null && e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isConsumer(ValueStore store) {
        if (!isConsumerInit) {
            isConsumerInit = true;
            for (Key dependency : params) {
                if (dependency == null) {
                    isConsumer = true;
                    break;
                }
                Parser child = store.get(dependency);
                if (child == null) {
                    System.out.println("Check consumer " + StringMan.getString(getKey()) + " | " + dependency);
                }
                if (child.isConsumer(store)) {
                    isConsumer = true;
                    break;
                }
            }
        }
        return isConsumer;
    }

    @Override
    public Key getKey() {
        return key;
    }

    @Override
    public String getDescription() {
        return desc;
    }
}
