package com.boydti.discord.commands.manager.v2.impl.discord.permission;

import com.boydti.discord.db.entities.Coalition;
import com.boydti.discord.user.Roles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface CoalitionPermission {
    Coalition value();
}
