package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class WebCurrentTreaties {
    public final List<WebCurrentTreaty> current_treaties;

    public WebCurrentTreaties(List<WebCurrentTreaty> current_treaties) {
        this.current_treaties = current_treaties;
    }

    public static class WebCurrentTreaty {
        public final String treaty_type;
        public final int from_alliance_id;
        public final int to_alliance_id;
        public final int turns_remaining;

        public WebCurrentTreaty(String treaty_type, int from_alliance_id, int to_alliance_id, int turns_remaining) {
            this.treaty_type = treaty_type;
            this.from_alliance_id = from_alliance_id;
            this.to_alliance_id = to_alliance_id;
            this.turns_remaining = turns_remaining;
        }
    }
}

