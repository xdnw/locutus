package link.locutus.discord.db.entities;

import java.util.function.Predicate;

public class MMRMatcher implements Predicate<String> {
    private final String required;

    public MMRMatcher(String required) {
        this.required = required.trim().toLowerCase().replace("x", ".");
        if (!required.matches("[0-9xX\\.]{4}")) throw new IllegalArgumentException("MMR must be 4 numbers or X. Provided value: `" + required + "`");
    }

    @Override
    public String toString() {
        return required;
    }

    public String getRequired() {
        return required.replace('.', 'X');
    }

    @Override
    public boolean test(String mmr) {
        return mmr.matches(required);
    }
}
