package link.locutus.discord.db.entities;

import link.locutus.discord.util.MathMan;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class LoginFactor {
    private final Function<DBNation, Double> function;
    public final String name;
    private final Map<DBNation, Double> functionCache;

    public LoginFactor(String name, Function<DBNation, Double> function) {
        this.name = name;
        this.function = function;
        this.functionCache = new HashMap<>();
    }

    public double get(DBNation nation) {
        return functionCache.computeIfAbsent(nation, function);
    }

    public boolean matches(double candidate, double target) {
        return candidate == target;
    }

    public String toString(double value) {
        return MathMan.format(value);
    }
}
