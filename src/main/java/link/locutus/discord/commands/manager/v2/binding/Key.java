package link.locutus.discord.commands.manager.v2.binding;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ReflectionUtil;
import org.apache.commons.lang3.reflect.TypeUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class Key<T> {
    private final Type type;
    private final Set<Class<?>> annotationTypes;
    private final Annotation[] annotations;

    private boolean isDefault;
    private final int hash;

    private Key(Type type, Class<?> annotationClass) {
        this(type, Collections.singletonList(annotationClass));
    }

    private Key(Type type, List<Class<?>> annotationClasses) {
        this.type = type;
        this.annotations = null; // This constructor variant does not carry full Annotation instances
        this.hash = TypeUtils.toString(type).hashCode();

        if (annotationClasses == null || annotationClasses.isEmpty()) {
            this.annotationTypes = Collections.emptySet();
            return;
        }

        // Fast path: expect tiny lists
        Class<?>[] tmp = new Class<?>[annotationClasses.size()];
        int count = 0;
        boolean localDefault = false;

        outer:
        for (int i = 0, n = annotationClasses.size(); i < n; i++) {
            Class<?> c = annotationClasses.get(i);
            if (c == null) continue;
            if (c == Binding.class) continue;
            if (c == Default.class) {
                localDefault = true;
                continue;
            }
            // dedupe (tiny N => O(n^2) is fine and allocation-free)
            for (int j = 0; j < count; j++) {
                if (tmp[j] == c) continue outer;
            }
            tmp[count++] = c;
        }

        this.isDefault = localDefault;

        if (count == 0) {
            this.annotationTypes = Collections.emptySet();
        } else if (count == 1) {
            this.annotationTypes = Collections.singleton(tmp[0]);
        } else {
            // Use insertion-order preserving set only when truly needed
            ObjectLinkedOpenHashSet<Class<?>> set = new ObjectLinkedOpenHashSet<>(count);
            for (int i = 0; i < count; i++) {
                set.add(tmp[i]);
            }
            this.annotationTypes = set;
        }
    }

    private static final Annotation[] EMPTY_ANNOTATIONS = new Annotation[0];

    private Key(Type type, Annotation... annotations) {
        this.type = type;
        this.hash = TypeUtils.toString(type).hashCode();

        if (annotations == null || annotations.length == 0) {
            this.annotations = EMPTY_ANNOTATIONS;
            this.annotationTypes = Collections.emptySet();
            return;
        }

        // Temporary storage for unique, non-filtered annotation types
        Class<?>[] tmp = new Class<?>[annotations.length];
        int count = 0;

        for (Annotation annotation : annotations) {
            if (annotation == null) continue;
            Class<? extends Annotation> annType = annotation.annotationType();
            if (annType == Binding.class) {
                continue;
            }
            if (annType == Default.class) {
                isDefault = true;
                continue;
            }
            // Deduplicate manually (expected tiny N)
            boolean exists = false;
            for (int i = 0; i < count; i++) {
                if (tmp[i] == annType) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                tmp[count++] = annType;
            }
        }

        // Assign original annotations array (retain previous behavior)
        this.annotations = annotations;

        if (count == 0) {
            this.annotationTypes = Collections.emptySet();
        } else if (count == 1) {
            this.annotationTypes = Collections.singleton(tmp[0]);
        } else {
            // Use fastutil set only when truly needed
            ObjectLinkedOpenHashSet<Class<?>> set = new ObjectLinkedOpenHashSet<>(count);
            for (int i = 0; i < count; i++) {
                set.add((Class<?>) tmp[i]);
            }
            this.annotationTypes = set;
        }
    }

    public String keyNameMarkdown() {
        String keyStr = toSimpleString();
        return keyStr.replace("[", "\\[")
                .replace("]", "\\]")
                .replaceAll("([<|, ])([a-zA-Z_0-9]+)([>|, ])", "$1[$2](#$2)$3").replace("<", "\\<").replace(">", "\\>").replace(", ", ",");
    }

    public static String keyNameMarkdown(String input) {
        return StringMan.classNameToSimple(input.replace("[", "\\[").replace("]", "\\]")
                        .replaceAll("([<|, ])([a-zA-Z_0-9]+)([>|, ])", "$1[$2](#$2)$3"))
                .replace("<", "\\<").replace(">", "\\>").replace(", ", ",");
    }


    public boolean isDefault() {
        return isDefault;
    }

    private Key(Type type, Class<?>... annotationClasses) {
        this(type, Arrays.asList(annotationClasses));
    }

    public static <T, A extends Annotation> Key<T> of(Type type, Class<A> annotationClass) {
        return new Key<>(type, annotationClass);
    }

    public static <T> Key<T> of(Type type, Class<? extends Annotation>... annotationClasses) {
        return new Key<>(type, annotationClasses);
    }

    public static <T> Key<T> nested(Class<?>... clazzes) {
        return of(ReflectionUtil.buildNestedType(clazzes));
    }

    public static <T> Key<T> of(Type clazz) {
        return of(clazz, new Annotation[0]);
    }

    public static <T> Key<T> of(Type clazz, Annotation... annotations) {
        return new Key<>(clazz, annotations);
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
        if (hash != key.hash) return false;
        if (!TypeUtils.equals(type, key.type)) return false;
        if (key.annotationTypes == annotationTypes) return true;
        if (annotationTypes.size() != key.annotationTypes.size()) return false;
        for (Class<?> clazz : annotationTypes) {
            if (!key.annotationTypes.contains(clazz)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return this.hash;
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
        name.append(type.getTypeName().replace(", ", ","));
        if (!annotationTypes.isEmpty()) {
            name.append("[" + annotationTypes.stream().map(Class::getSimpleName).collect(Collectors.joining(",")) + "]");
        }
        return StringMan.classNameToSimple(name.toString());
    }
}
