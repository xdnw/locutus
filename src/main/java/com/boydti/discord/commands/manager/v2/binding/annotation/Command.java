package com.boydti.discord.commands.manager.v2.binding.annotation;

import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.user.Roles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Command {
    String[] aliases() default {};

    String help() default "";
    String desc() default "";

    CommandCategory category() default CommandCategory.UNCATEGORIZED;
}