package com.boydti.discord.commands.manager.v2.impl.pw;

import com.boydti.discord.pnw.DBNation;

import java.lang.reflect.Type;
import java.util.function.Function;
import java.util.function.Predicate;

public interface NationPlaceholder<T> extends Function<DBNation,T> {
    String getName();

    Class<T> getType();
}