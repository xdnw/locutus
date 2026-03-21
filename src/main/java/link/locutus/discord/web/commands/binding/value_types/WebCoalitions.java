package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class WebCoalitions {
    public final List<WebCoalition> coalitions;

    public WebCoalitions(List<WebCoalition> coalitions) {
        this.coalitions = coalitions;
    }

    public static class WebCoalition {
        public final String name;
        public final List<WebCoalitionMember> members;

        public WebCoalition(String name, List<WebCoalitionMember> members) {
            this.name = name;
            this.members = members;
        }
    }

    public static class WebCoalitionMember {
        public final long id;
        public final String name;
        public final boolean deleted;

        public WebCoalitionMember(long id, String name, boolean deleted) {
            this.id = id;
            this.name = name;
            this.deleted = deleted;
        }
    }
}
