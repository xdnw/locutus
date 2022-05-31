package com.boydti.discord.commands.manager.v2.impl.pw;

import com.boydti.discord.commands.manager.v2.binding.bindings.Operation;
import com.boydti.discord.pnw.DBNation;

import java.util.function.Predicate;

public interface NationFilter extends Predicate<DBNation> {
}
