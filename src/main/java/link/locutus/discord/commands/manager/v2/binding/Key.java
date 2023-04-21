package link.locutus.discord.commands.manager.v2.binding;

import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ReflectionUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class Key<T> {
    private final Type type;
    private final Set<Class<?>> annotationTypes;
    private final Binding binding;
    private final Annotation[] annotations;

    private boolean isDefault;

    private Key(Type type, Class annotationClass) {
        this(type, Collections.singletonList(annotationClass));
    }
    private Key(Type type, List<Class<?>> annotationClasses) {
        this.type = type;
        this.annotationTypes = new HashSet<>(annotationClasses);
        if (!this.annotationTypes.isEmpty()) {
            Iterator<Class<?>> iter = this.annotationTypes.iterator();
            while (iter.hasNext()) {
                Class<?> f = iter.next();
                if (Binding.class.isAssignableFrom(f)) {
                    iter.remove();
                } else if (Default.class.isAssignableFrom(f)) {
                    iter.remove();
                    isDefault = true;
                }
            }
        }
        this.binding = null;
        this.annotations = null;
    }

    public boolean isDefault() {
        return isDefault;
    }

    private Key(Type type, Class<?>... annotationClasses) {
        this(type, Arrays.asList(annotationClasses));
    }

    private Key(Binding binding, Type type, Annotation... annotations) {
        this.binding = binding;
        this.type = type;
        this.annotationTypes = new HashSet<>();
        this.annotations = annotations;
        for (Annotation annotation : annotations) {
            annotationTypes.add(annotation.annotationType());
        }
        if (!this.annotationTypes.isEmpty()) {
            Iterator<Class<?>> iter = this.annotationTypes.iterator();
            while (iter.hasNext()) {
                Class<?> f = iter.next();
                if (Binding.class.isAssignableFrom(f)) {
                    iter.remove();
                } else if (Default.class.isAssignableFrom(f)) {
                    iter.remove();
                    isDefault = true;
                }
            }
        }

    }

    public static <T> Key<T> of(Type type, Class annotationClass) {
        return new Key<>(type, annotationClass);
    }

    public static <T> Key<T> of(Type type, Class... annotationClasses) {
        return new Key<>(type, annotationClasses);
    }

    public static <T> Key<T> of(Type clazz) {
        return of(clazz, new Annotation[0]);
    }

    public static <T> Key<T> of(Type clazz, Annotation... annotations) {
        return of(null, clazz, annotations);
    }

    public static <T> Key<T> of(Binding binding, Type clazz) {
        return of(binding, clazz, new Annotation[0]);
    }

    public static <T> Key<T> of(Binding binding, Type clazz, Annotation... annotations) {
        return new Key<>(binding, clazz, annotations);
    }

    public Key<T> append(Class<? extends Annotation> annotation) {
        List<Class<?>> keyAnnotations = new ArrayList<>(getAnnotationTypes());
        keyAnnotations.add(annotation);
        return Key.of(getType(), keyAnnotations.toArray(new Class[0]));
    }

    public Class getClazz() {
        return ReflectionUtil.getClassType(type);
    }

    public Type getType() {
        return type;
    }

    public Annotation getAnnotation(Type type) {
        if (annotations == null) return null;
        for (Annotation annotation : annotations) {
            if (ReflectionUtil.getClassType(type) == annotation.annotationType()) {
                return annotation;
            }
        }
        return null;
    }

    public Binding getBinding() {
        return binding;
    }

    public Annotation[] getAnnotations() {
        if (annotations == null) return new Annotation[0];
        return annotations;
    }

    public Set<Class<?>> getAnnotationTypes() {
        return annotationTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Key key = (Key) o;

        if (!Objects.equals(type, key.type)) return false;
        if (key.annotationTypes == annotationTypes) return true;
        if (annotationTypes.size() != key.annotationTypes.size()) return false;
        for (Class<?> clazz : annotationTypes) {
            if (!key.annotationTypes.contains(clazz)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        return result;
    }

    @Override
    public String toString() {
        return "Key{" +
                "type=" + type +
                ", annotationTypes=" + annotationTypes +
                '}';
    }

    public String toSimpleString() {
        StringBuilder name = new StringBuilder();
        name.append(type.getTypeName());
        if (!annotationTypes.isEmpty()) {
            name.append("[" + annotationTypes.stream().map(Class::getSimpleName).collect(Collectors.joining(",")) + "]");
        }
        return name.toString().replaceAll("[a-z_A-Z0-9.]+\\.([a-z_A-Z0-9]+)", "$1").replaceAll("[a-z_A-Z0-9]+\\$([a-z_A-Z0-9]+)", "$1");
    }
}
