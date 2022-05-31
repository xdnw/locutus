package com.boydti.discord.commands.manager.v2.binding;

import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.util.math.ReflectionUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Key<T> {
    private final Type type;
    private final Set<Class<?>> annotationTypes;
    private final Binding binding;
    private final Annotation[] annotations;

    private Key(Type type, Class annotationClass) {
        this.type = type;
        this.annotationTypes = Collections.singleton(annotationClass);
        this.binding = null;
        this.annotations = null;
    }

    public Key<T> append(Class<? extends Annotation> annotation) {
        List<Class<?>> keyAnnotations = new ArrayList<>(getAnnotationTypes());
        keyAnnotations.add(annotation);
        return Key.of(getType(), keyAnnotations.toArray(new Class[0]));
    }

    private Key(Type type, Class<?>... annotationClasses) {
        this.type = type;
        this.annotationTypes = new HashSet<>(Arrays.asList(annotationClasses));
        if (!this.annotationTypes.isEmpty()) this.annotationTypes.removeIf(f -> f == Binding.class);
        this.binding = null;
        this.annotations = null;
    }

    public static <T> Key<T> of(Type type, Class annotationClass) {
        return new Key(type, annotationClass);
    }

    public static <T> Key<T> of(Type type, Class... annotationClasses) {
        return new Key(type, annotationClasses);
    }

    private Key(Binding binding, Type type, Annotation... annotations) {
        this.binding = binding;
        this.type = type;
        this.annotationTypes = new HashSet<>();
        this.annotations = annotations;
        for (Annotation annotation : annotations) {
            annotationTypes.add(annotation.annotationType());
        }
        if (!this.annotationTypes.isEmpty()) this.annotationTypes.removeIf(f -> f == Binding.class);
    }

    public Class<T> getClazz() {
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

    public static <T> Key<T> of(Type clazz) {
        return of(clazz, new Annotation[0]);
    }

    public static <T> Key<T>  of(Type clazz, Annotation... annotations) {
        return of(null, clazz, annotations);
    }

    public static <T> Key<T>  of(Binding binding, Type clazz) {
        return of(binding, clazz, new Annotation[0]);
    }

    public static <T> Key<T>  of(Binding binding, Type clazz, Annotation... annotations) {
        return new Key(binding, clazz, annotations);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Key key = (Key) o;

        if (!Objects.equals(type, key.type)) return false;
        return annotationTypes.equals(key.annotationTypes);
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (annotationTypes != null ? annotationTypes.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Key{" +
                "type=" + type +
                ", annotationTypes=" + annotationTypes +
                '}';
    }
}
