package link.locutus.discord.commands.manager.v2.binding.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Binding {
    String value() default "";

    String[] examples() default {};

    Class<?>[] types() default {};

    boolean multiple() default false;

    /**
     * Defer to specific component classes making up this type e.g. Set&lt;MyType&gt; would have MyType as a component
     * Leave this blank unless the component type is NOT correctly inferred
     * e.g. NationList should defer -> DBNation
     * @return
     */
    Class[] components() default {};
}