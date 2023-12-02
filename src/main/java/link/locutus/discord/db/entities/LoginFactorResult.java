package link.locutus.discord.db.entities;

import java.util.LinkedHashMap;
import java.util.Map;

public class LoginFactorResult {
    public final Map<LoginFactor, Double> result = new LinkedHashMap<>();

    public LoginFactorResult() {
    }

    // add
    public LoginFactorResult put(LoginFactor factor, double value) {
        result.put(factor, value);
        return this;
    }

    public Map<LoginFactor, Double> getResult() {
        return result;
    }
}
