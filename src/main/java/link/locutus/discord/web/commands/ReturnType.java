package link.locutus.discord.web.commands;

import link.locutus.discord.web.commands.binding.value_types.CacheType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the TypeScript return type for an API endpoint.
 * <p>
 * The {@code value} array encodes the full type:
 * <ul>
 *   <li>{@code @ReturnType(Foo.class)}                          → {@code Foo}</li>
 *   <li>{@code @ReturnType({List.class, Foo.class})}            → {@code Foo[]}</li>
 *   <li>{@code @ReturnType({Map.class, String.class, Foo.class})} → {@code Record<string, Foo>}</li>
 * </ul>
 * The first element is the raw type; remaining elements are type arguments.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface ReturnType {
    Class[] value();
    CacheType cache() default CacheType.None;
    long duration() default 2592000;
}
